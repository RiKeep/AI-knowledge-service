package com.ri.artificial.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author Ri
 * @date 2026-10-03 21:25
 */
@Data
public class ChatHistoryVO {
    private Long historyId;
    private String historyUuid;
    private String title;
    private LocalDateTime createTime;
}
