package com.ri.artificial.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.ri.artificial.common.SseStreamSupport;
import com.ri.artificial.config.TitleThreadPoolConfig;
import com.ri.artificial.constant.MessageRole;
import com.ri.artificial.domain.Result;
import com.ri.artificial.domain.dto.ChatRequest;
import com.ri.artificial.domain.po.ChatHistory;
import com.ri.artificial.domain.vo.ChatAnswerVO;
import com.ri.artificial.domain.vo.SseMessage;
import com.ri.artificial.service.IChatHistoryService;
import com.ri.artificial.service.IChatMessageService;
import com.ri.artificial.service.IChatService;
import com.ri.artificial.service.IKnowledgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * @author Ri
 * @date 2026-10-01 11:14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements IChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final IChatHistoryService chatHistoryService;
    private final IChatMessageService chatMessageService;
    private final SseStreamSupport sseStreamSupport;
    private final IKnowledgeService knowledgeService;
    private final ThreadPoolTaskExecutor titleThreadPool;

    private static final String DEFAULT_TITLE = "新对话";

    @Override
    public Result<ChatAnswerVO> chat(ChatRequest chatRequest, Long userId) {
        Prepared prepared = prepare(chatRequest, userId);

        String content = "";
        String reasoning = null;
        ChatResponse response = prepared.spec().call().chatResponse();

        if (response != null && response.getResult() != null) {
            // 获取AI返回的内容
            AssistantMessage output = response.getResult().getOutput();
            content = StrUtil.nullToEmpty(output.getText());
            // 未开启深度思考时不读取 reasoning：模型可能仍返回该字段，但不应透出与落库
            reasoning = chatRequest.isDeepThink() ? reasoningOf(output) : null;
        }

        // 非流式没有中断/取消路径，落库只此一处，不会重复保存
        saveAssistant(prepared, content, reasoning);
        ChatAnswerVO data = new ChatAnswerVO()
                .setSessionId(prepared.sessionId())
                .setHistoryId(prepared.historyId())
                .setContent(content)
                .setReasoning(reasoning);

        return Result.success(data);
    }

    @Override
    public Flux<ServerSentEvent<String>> stream(ChatRequest chatRequest, Long userId) {
        Prepared prepared = prepare(chatRequest, userId);

        // 是否透出思考过程：关闭时既不累积也不下发 reasoning 帧
        boolean deepThink = chatRequest.isDeepThink();

        // 累积正文与思考过程，供终止时落库
        StringBuilder aiContent = new StringBuilder();
        StringBuilder aiReasoning = new StringBuilder();

        Flux<ServerSentEvent<String>> stream = prepared.spec()
                .stream()
                .chatResponse()
                .flatMap(resp -> {
                    if (resp.getResult() == null) {
                        return Flux.empty();
                    }
                    Flux<ServerSentEvent<String>> eventFlux = Flux.empty();
                    AssistantMessage output = resp.getResult().getOutput();

                    String content = output.getText();
                    if (StrUtil.isNotEmpty(content)) {
                        aiContent.append(content);
                        eventFlux = eventFlux.concatWithValues(
                                sseStreamSupport.event(SseMessage.CONTENT, content));
                    }

                    // 未开启深度思考时完全跳过 reasoning：模型仍可能返回该字段，
                    // 但不应下发、也不应累积落库（否则刷新后会重放出思考块）
                    String reasoning = deepThink ? reasoningOf(output) : null;
                    if (reasoning != null) {
                        // 累积用原文，下发才过滤纯空白，避免思考过程里的换行丢失
                        aiReasoning.append(reasoning);
                        if (StrUtil.isNotBlank(reasoning)) {
                            eventFlux = eventFlux.concatWithValues(
                                    sseStreamSupport.event(SseMessage.REASONING, reasoning));
                        }
                    }
                    return eventFlux;
                });

        return sseStreamSupport.wrap(stream,
                () -> saveAssistant(prepared, aiContent.toString(), aiReasoning.toString()),
                "ChatService(stream)");
    }



    /**
     * 一次对话的公共前置。流式与非流式都走这里，保证两条路的行为一致：
     * 会话定位、历史装载、用户消息落库、RAG 挂载。
     */
    private Prepared prepare(ChatRequest chatRequest, Long userId) {
        // 前端未带 sessionId 时生成一个，随返回值交给前端续接，否则每条消息都会新开会话
        String sessionId = StrUtil.isBlank(chatRequest.getSessionId())
                ? UUID.randomUUID().toString()
                : chatRequest.getSessionId();

        // 获取会话记录，没有则新建
        ChatHistory history = chatHistoryService.getOrCreateChat(userId, sessionId);

        // 如果是新建的会话，它是没有标题的，所以新会话使用AI生成标题
        if(StrUtil.isBlank(history.getTitle())){
            generateAsync(userId, history.getHistoryId(), chatRequest.getMessage());
        }

        // 获取当前会话历史上下文（从 MySQL 查询），必须在落库本条消息之前查询，否则本条会重复进入上下文
        List<Message> histories = chatMessageService.listMessages(userId, history.getHistoryId()).stream()
                .map(msg -> MessageRole.ASSISTANT.equals(msg.getRole())
                        ? new AssistantMessage(msg.getContent())
                        : new UserMessage(msg.getContent()))
                .collect(Collectors.toList());

        // 把用户的新消息加入到数据库中
        chatMessageService.saveMessage(userId, history.getHistoryId(), MessageRole.USER,
                chatRequest.getMessage(), null);

        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .messages(histories)
                .user(chatRequest.getMessage());

        // RAG 就是一个 advisor 的有无：挂上则注入检索到的片段，不挂就是普通对话
        if (chatRequest.isRag()) {
            spec = spec.advisors(ragAdvisor(userId, chatRequest.getMessage(), chatRequest.getDocIds()));
        }

        return new Prepared(userId, sessionId, history.getHistoryId(), spec);
    }

    /**
     * 检索增强：只在当前用户、且（用户选中文件时）指定文档的向量里检索。
     * user_id 过滤必须始终存在，否则会检索到其他用户上传的知识库。
     */
    private Advisor ragAdvisor(Long userId, String message, List<Long> docIds) {
        // 未选中文件时不查文档 id：listVectorIds 未对空入参做保护，空场景下不应进入
        List<String> vectorIds = CollUtil.isEmpty(docIds)
                ? List.of()
                : knowledgeService.listVectorIds(docIds);

        FilterExpressionBuilder fb = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op userOp = fb.eq("user_id", userId);
        // 未选文件则仅按用户隔离；选了则再收窄到这些文档
        Filter.Expression filter = CollUtil.isEmpty(vectorIds)
                ? userOp.build()
                : fb.and(userOp, fb.in("document_id", vectorIds.toArray())).build();

        SearchRequest searchRequest = SearchRequest.builder()
                .query(message)
                .filterExpression(filter)
                .topK(10)
                .similarityThreshold(0.6)
                .build();
        return QuestionAnswerAdvisor.builder(vectorStore).searchRequest(searchRequest).build();
    }

    /**
     * 取输出里的思考内容；模型未返回该字段时 metadata.get 为 null。
     * 此处不能直接 String.valueOf，否则 null 会变成字面量 "null" 被当成思考内容下发。
     */
    private String reasoningOf(AssistantMessage output) {
        Object reasoning = output.getMetadata().get("reasoningContent");
        return ObjectUtil.isNull(reasoning) ? null : reasoning.toString();
    }

    /**
     * 保存 AI 回复：非流式一次落库，流式在正常结束、客户端取消、异常中断三条终止路径共用。
     */
    private void saveAssistant(Prepared prepared, String content, String reasoning) {
        // 两段都为空说明尚未产生任何输出，不落库空消息
        if (StrUtil.isBlank(content) && StrUtil.isBlank(reasoning)) {
            return;
        }
        // 保存消息
        chatMessageService.saveMessage(prepared.userId(), prepared.historyId(), MessageRole.ASSISTANT,
                content, StrUtil.isBlank(reasoning) ? null : reasoning);
    }

    /** 异步生成标题并回写，失败降级为默认标题，绝不抛出异常影响主流程 */
    private void generateAsync(Long userId, Long historyId, String message) {
        if (historyId == null || StrUtil.isBlank(message)) {
            return;
        }
        try {
            titleThreadPool.execute(() -> {
                String title = resolveTitle(message);
                try {
                    chatHistoryService.renameChatHistory(userId, historyId, title);
                } catch (Exception e) {
                    log.warn("回写会话标题失败, historyId={}, title={}", historyId, title, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("标题生成任务被拒绝, historyId={}", historyId, e);
        }
    }

    /** 生成标题；模型失败或返回空，统一落回默认标题 */
    private String resolveTitle(String message) {
        String systemPrompt = String.format("把下面这句话的核心意思作为会话标题，不超过20字，不要多余内容：%s", message);
        String title = chatClient.prompt().system(systemPrompt).call().content();
        return StrUtil.isBlank(title) ? DEFAULT_TITLE : title;
    }

    /** 一次对话的公共前置产物：会话信息 + 已装配（含可选 RAG）的请求 */
    private record Prepared(Long userId, String sessionId, Long historyId,
                            ChatClient.ChatClientRequestSpec spec) {
    }
}
