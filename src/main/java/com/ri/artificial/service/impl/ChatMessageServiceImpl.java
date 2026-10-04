package com.ri.artificial.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.HttpStatus;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ri.artificial.domain.po.ChatMessage;
import com.ri.artificial.exception.BadRequestException;
import com.ri.artificial.mapper.ChatMessageMapper;
import com.ri.artificial.service.IChatMessageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @author Ri
 * @date 2026-10-01 11:14
 */
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements IChatMessageService {

    @Override
    public void saveMessage(Long userId, Long historyId, String role, String content, String reasoning) {
        save(new ChatMessage()
                .setUserId(userId)
                .setHistoryId(historyId)
                .setRole(role)
                .setContent(content)
                .setReasoning(reasoning));
    }

    @Override
    public List<ChatMessage> listMessages(Long userId, Long historyId) {
        // 查询对应用户的聊天记录，最多返回 100 条
        return lambdaQuery()
                .eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getHistoryId, historyId)
                .orderByAsc(ChatMessage::getMessageId)
                .last("limit 100")
                .list();
    }

    @Override
    public void deleteByHistoryId(Long userId, Long historyId) {
        lambdaUpdate().eq(ChatMessage::getUserId, userId)
                .eq(ChatMessage::getHistoryId, historyId)
                .remove();
    }

    @Override
    @Transactional
        public void deleteMessagesAfterTime(Long userId, Long messageId, Long historyId) {
        if(ObjectUtil.isNull(messageId) || ObjectUtil.isNull(historyId)) {
            throw new BadRequestException("消息ID和会话记录ID不能为空", HttpStatus.HTTP_BAD_REQUEST);
        }

        // 查到当前要删除的聊天记录
        ChatMessage msg = lambdaQuery().eq(ChatMessage::getMessageId, messageId)
                .eq(ChatMessage::getHistoryId, historyId)
                .eq(ChatMessage::getUserId, userId)
                .one();

        if (ObjectUtil.isNull(msg)) {
            throw new BadRequestException("消息不存在", HttpStatus.HTTP_BAD_REQUEST);
        }

        // 删除比自己创建日期大的所有聊天记录(注意：不要带上MessageId，否则删除不到其它消息)
        lambdaUpdate().eq(ChatMessage::getHistoryId, historyId)
                .eq(ChatMessage::getUserId, userId)
                .ge(ChatMessage::getCreateTime, msg.getCreateTime())
                .remove();
    }

    @Override
    @Transactional
    public void reAnswerUserMessage(Long userId, Long messageId, Long historyId, String message) {
        this.deleteMessagesAfterTime(userId, messageId, historyId);
    }
}
