package com.ri.artificial.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.ri.artificial.domain.Result;
import com.ri.artificial.domain.po.ChatHistory;
import com.ri.artificial.domain.po.ChatMessage;
import com.ri.artificial.domain.vo.ChatHistoryVO;
import com.ri.artificial.domain.vo.ChatMessageVO;
import com.ri.artificial.service.IChatHistoryService;
import com.ri.artificial.service.IChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;


/**
 * @author Ri
 * @date 2026-10-03 21:20
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/history")
public class ChatHistoryController {

    private final IChatHistoryService chatHistoryService;
    private final IChatMessageService chatMessageService;

    @GetMapping("/list")
    public Result<List<ChatHistoryVO>> listHistory() {
        // 获取对应的 histories
        List<ChatHistory> histories = chatHistoryService.queryChatHistory(StpUtil.getLoginIdAsLong());
        // 使用BeanUtil.copyToList方法把它转换成前端所需要的对象
        List<ChatHistoryVO> hisVos = BeanUtil.copyToList(histories, ChatHistoryVO.class);
        return Result.success(hisVos);
    }

    @GetMapping("/messages")
    public Result<List<ChatMessageVO>> listMessages(@RequestParam String sessionId) {
        Long userId = StpUtil.getLoginIdAsLong();
        // 先查询出对应的会话历史，如果没有就返回空
        ChatHistory history = chatHistoryService.getChatById(userId, sessionId);
        if (history == null) {
            return Result.success(Collections.emptyList());
        }
        // 根据用户ID和历史记录ID查询对应的聊天记录
        List<ChatMessage> chatMessages = chatMessageService.listMessages(userId, history.getHistoryId());
        // 使用BeanUtil.copyToList方法把它转换成前端所需要的对象
        List<ChatMessageVO> vos = BeanUtil.copyToList(chatMessages, ChatMessageVO.class);
        return Result.success(vos);
    }

    @PutMapping("/{id}/title")
    public Result<String> renameTitle(@PathVariable Long id, @RequestParam String title) {
        chatHistoryService.renameChatHistory(StpUtil.getLoginIdAsLong(), id, title);
        return Result.success();
    }

    @DeleteMapping("/del/after-time/{messageId}/{historyId}")
    public Result<String> deleteAfterTimeMsg(@PathVariable("messageId") Long messageId,
    @PathVariable("historyId") Long historyId) {
        chatMessageService.deleteMessagesAfterTime(StpUtil.getLoginIdAsLong(), messageId, historyId);
        return Result.success();
    }

    @DeleteMapping("/del/{id}")
    public Result<String> deleteHistory(@PathVariable Long id) {
        chatHistoryService.deleteChatHistory(StpUtil.getLoginIdAsLong(), id);
        return Result.success();
    }

    @DeleteMapping("/del/all")
    public Result<String> deleteAllHistories() {
        chatHistoryService.removeAllHistory(StpUtil.getLoginIdAsLong());
        return Result.success();
    }
}
