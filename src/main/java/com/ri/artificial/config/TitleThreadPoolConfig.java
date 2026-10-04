package com.ri.artificial.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Ri
 * @date 2026-10-04 13:23
 */
@Configuration
public class TitleThreadPoolConfig {

    @Bean
    public ThreadPoolTaskExecutor titleThreadPool() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(2);
        // 最大线程数
        executor.setMaxPoolSize(10);
        // 队列数量
        executor.setQueueCapacity(20);
        // 线程前缀
        executor.setThreadNamePrefix("chat-title-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 关闭时等任务完成再退
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 最多等待30秒
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
