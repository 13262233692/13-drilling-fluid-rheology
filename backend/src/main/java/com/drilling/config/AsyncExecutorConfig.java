package com.drilling.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.annotation.PostConstruct;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncExecutorConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncExecutorConfig.class);

    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();

    @PostConstruct
    public void logConfiguration() {
        log.info("AsyncExecutorConfig initialized - availableProcessors={}", AVAILABLE_PROCESSORS);
        log.info("wsPushExecutor: core={}, max={}, queueCapacity=32",
                Math.min(4, AVAILABLE_PROCESSORS), Math.min(8, AVAILABLE_PROCESSORS * 2));
        log.info("wsChannelExecutor: core={}, max={}, queueCapacity=64",
                Math.min(2, AVAILABLE_PROCESSORS), Math.min(4, AVAILABLE_PROCESSORS));
        log.info("wsPushScheduler: poolSize=1");
    }

    @Bean("wsPushExecutor")
    public ThreadPoolTaskExecutor wsPushExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.min(4, AVAILABLE_PROCESSORS));
        executor.setMaxPoolSize(Math.min(8, AVAILABLE_PROCESSORS * 2));
        executor.setQueueCapacity(32);
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("ws-push-");
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        return executor;
    }

    @Bean("wsChannelExecutor")
    public ThreadPoolTaskExecutor wsChannelExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.min(2, AVAILABLE_PROCESSORS));
        executor.setMaxPoolSize(Math.min(4, AVAILABLE_PROCESSORS));
        executor.setQueueCapacity(64);
        executor.setRejectedExecutionHandler(wsChannelRejectedHandler());
        executor.setThreadNamePrefix("ws-channel-");
        executor.setDaemon(true);
        executor.initialize();
        return executor;
    }

    private RejectedExecutionHandler wsChannelRejectedHandler() {
        return (r, executor) -> {
            log.error("wsChannelExecutor rejected task - poolSize={}, active={}, queueSize={}, queueRemaining={}",
                    executor.getPoolSize(),
                    executor.getActiveCount(),
                    executor.getQueue().size(),
                    executor.getQueue().remainingCapacity());
            throw new java.util.concurrent.RejectedExecutionException(
                    "wsChannelExecutor is saturated, task rejected");
        };
    }

    @Bean("wsPushScheduler")
    public ThreadPoolTaskScheduler wsPushScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-scheduler-");
        scheduler.initialize();
        return scheduler;
    }
}
