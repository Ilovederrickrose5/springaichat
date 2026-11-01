package com.example.springaichat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 聊天相关异步任务使用独立线程池，避免 CompletableFuture 默认占用 ForkJoinPool.commonPool()
 * 后执行阻塞的数据库操作。
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "chatTaskExecutor")
    public ThreadPoolTaskExecutor chatTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("chat-async-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }
}
