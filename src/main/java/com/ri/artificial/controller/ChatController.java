package com.ri.artificial.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ri.artificial.domain.Result;
import com.ri.artificial.domain.dto.ChatRequest;
import com.ri.artificial.domain.vo.ChatAnswerVO;
import com.ri.artificial.service.IChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * @author Ri
 * @date 2026-10-01 14:39
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/ai/chat")
public class ChatController {
    private final IChatService chatService;

    @PostMapping
    public Result<ChatAnswerVO> chat(@RequestBody ChatRequest chatRequest) {
        return chatService.chat(chatRequest, StpUtil.getLoginIdAsLong());
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestBody ChatRequest chatRequest) {
        return chatService.stream(chatRequest, StpUtil.getLoginIdAsLong());
    }
}
