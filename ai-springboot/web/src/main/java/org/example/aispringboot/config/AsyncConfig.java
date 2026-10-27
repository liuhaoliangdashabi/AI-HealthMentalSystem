package org.example.aispringboot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Async 线程池。
 * 默认配置是 core=8 / max=MAX / queue=MAX，队列永远装不满，于是永远不扩容、不拒绝，任务会一直堆积到 OOM。
 * 这里改成有界队列，满了让提交任务的线程自己跑，宁可慢也不丢。
 */
@Slf4j
@Configuration
public class AsyncConfig {

    //bean 名必须同时叫 applicationTaskExecutor 和 taskExecutor，@Async 找不到后者会退化成每次新建线程
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(200);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix("async-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        //停机时把队列里的任务跑完，否则文件补偿删除会被掐断，OSS 上留孤儿文件
        exec.setWaitForTasksToCompleteOnShutdown(true);
        exec.setAwaitTerminationSeconds(30);
        return exec;
    }
}
