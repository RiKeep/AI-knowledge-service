package com.ri.artificial.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ri.artificial.domain.po.ChatHistory;
import com.ri.artificial.domain.po.ChatMessage;

import java.util.List;

/**
 * @author Ri
 * @date 2026-10-01 11:12
 */
public interface IChatHistoryService extends IService<ChatHistory> {
    ChatHistory getOrCreateChat(Long userId, String sessionId);

    ChatHistory getChatById(Long userId, String sessionId);

    List<ChatHistory> queryChatHistory(Long userId);

    void deleteChatHistory(Long userId, Long historyId);

    void renameChatHistory(Long userId, Long historyId, String title);

    void removeAllHistory(Long userId);
}
