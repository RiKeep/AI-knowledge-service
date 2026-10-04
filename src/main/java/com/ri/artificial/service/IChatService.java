package com.ri.artificial.service;

import com.ri.artificial.domain.Result;
import com.ri.artificial.domain.dto.ChatRequest;
import com.ri.artificial.domain.vo.ChatAnswerVO;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * @author Ri
 * @date 2026-10-01 11:12
 */
public interface IChatService {

    /** 非流式输出 */
    Result<ChatAnswerVO> chat(ChatRequest chatRequest, Long userId);

    /** 流式输出 */
    Flux<ServerSentEvent<String>> stream(ChatRequest chatRequest, Long userId);
}

