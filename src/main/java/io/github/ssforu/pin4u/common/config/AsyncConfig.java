package io.github.ssforu.pin4u.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync // 비동기 처리 활성화
public class AsyncConfig {

    @Bean(name = "aiTaskExecutor") // AI 작업 전용 스레드 풀
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10); // 기본 대기 스레드 수
        executor.setMaxPoolSize(50);  // 최대 스레드 수 (동시 요청 폭주 시)
        executor.setQueueCapacity(500); // 대기열 크기 (모든 스레드가 바쁠 때 대기)
        executor.setThreadNamePrefix("AI-Async-");
        executor.initialize();
        return executor;
    }
}