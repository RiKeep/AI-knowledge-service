package com.ri.artificial.interceptors;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import org.springframework.ai.tool.ToolCallback;

/**
 * @author Ri
 * @date 2026-10-05 16:38
 */
public class LoggingInterceptors extends ToolInterceptor {

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        System.out.println("在调用工具之前打印：");
        System.out.println(request.getToolCallId());
        return handler.call(request);
    }

    @Override
    public String getName() {
        return "logging interceptors";
    }
}
