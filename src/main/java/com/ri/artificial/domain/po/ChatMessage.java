package com.ri.artificial.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * @author Ri
 * @date 2026-10-01 11:19
 */
@Data
@Accessors(chain = true)
@TableName("chat_message")
public class ChatMessage {
    // 消息ID
    // 雪花 ID 为 64 位，必须用 Long 承接；用 Integer 会被截断（含符号位），既不唯一也可能为负
    @TableId(type = IdType.ASSIGN_ID)
    private Long messageId;

    // 会话ID（关联 chat_history.history_id）
    private Long historyId;

    // 用户ID
    private Long userId;

    // 角色：user / assistant / system
    private String role;

    // 思考过程
    private String reasoning;

    // 消息正文
    private String content;

    // 创建时间
    private LocalDateTime createTime;

    // 更新时间
    private LocalDateTime updateTime;
}
