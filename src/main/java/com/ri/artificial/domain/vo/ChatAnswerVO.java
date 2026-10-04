package com.ri.artificial.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author Ri
 * @date 2026-10-03 19:22
 * 非流式输出返回实体
 */
@Data
@Accessors(chain = true)
public class ChatAnswerVO {
    private String sessionId;
    private Long historyId;
    private String content;
    private String reasoning;
}
