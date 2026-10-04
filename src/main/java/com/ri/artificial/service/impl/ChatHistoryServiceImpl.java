package com.ri.artificial.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ri.artificial.domain.po.ChatHistory;
import com.ri.artificial.domain.po.ChatMessage;
import com.ri.artificial.mapper.ChatHistoryMapper;
import com.ri.artificial.service.IChatHistoryService;
import com.ri.artificial.service.IChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Ri
 * @date 2026-10-01 11:14
 */
@Service
@RequiredArgsConstructor
public class ChatHistoryServiceImpl extends ServiceImpl<ChatHistoryMapper, ChatHistory> implements IChatHistoryService {
    private final IChatMessageService chatMessageService;

    @Override
    public ChatHistory getOrCreateChat(Long userId, String sessionId) {
        // 按 (user_id, history_uuid) 查询，已存在则直接返回，避免同会话重复建记录
        ChatHistory history = getChatById(userId, sessionId);
        if (ObjectUtil.isNotNull(history)) {
            return history;
        }
        // 不存在则新建，标题留空，由调用方根据首条消息生成
        ChatHistory newHistory = new ChatHistory()
                .setUserId(userId)
                .setHistoryUuid(sessionId);
        save(newHistory);
        return newHistory;
    }

    @Override
    public ChatHistory getChatById(Long userId, String sessionId) {
        return lambdaQuery().eq(ChatHistory::getUserId, userId)
                .eq(ChatHistory::getHistoryUuid, sessionId)
                .one();
    }

    @Override
    public List<ChatHistory> queryChatHistory(Long userId) {
        // 查询当前用户的会话历史, 按更新时间倒序排列
        return lambdaQuery().eq(ChatHistory::getUserId, userId)
                .orderByDesc(ChatHistory::getUpdateTime)
                .list();
    }

    @Override
    public void deleteChatHistory(Long userId, Long historyId) {
        // 只删属于当前用户的会话（防止越权）
        if (!lambdaQuery().eq(ChatHistory::getUserId, userId)
                .eq(ChatHistory::getHistoryId, historyId)
                .exists()) {
            return;
        }
        // 级联删除会话消息
        chatMessageService.deleteByHistoryId(userId, historyId);
        // 删除会话记录
        lambdaUpdate().eq(ChatHistory::getUserId, userId)
                .eq(ChatHistory::getHistoryId, historyId)
                .remove();
    }

    @Override
    public void renameChatHistory(Long userId, Long historyId, String title) {
        lambdaUpdate().eq(ChatHistory::getHistoryId, historyId)
                .eq(ChatHistory::getUserId, userId)
                .set(ChatHistory::getTitle, title)
                .update();
    }

    @Override
    public void removeAllHistory(Long userId) {
        // 删除所有聊天记录的消息
        queryChatHistory(userId).forEach(h -> chatMessageService.deleteByHistoryId(userId, h.getHistoryId()));
        // 删除所有聊天记录历史
        lambdaUpdate().eq(ChatHistory::getUserId, userId)
                .remove();
    }
}
