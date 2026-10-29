package com.offerget.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 模拟面试的评分任务线程池。
 * 评分在题目完成后异步执行，不阻塞面试对话；结束报告时再兜底补评。
 */
@Configuration
public class InterviewExecutorConfig {

    @Bean("interviewExecutor")
    public Executor interviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mock-interview-");
        executor.initialize();
        return executor;
    }

}
