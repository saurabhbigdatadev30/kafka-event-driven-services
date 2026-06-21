package com.microservices.demo.kafka.consumer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ExecutorConfig
{
    @Bean
    public ExecutorService kafkaWorkerPool()
    {
        return new ThreadPoolExecutor(
                5, 10,
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(100),
                // 🔥 slows down consumer, since now the calling thread also starts processing the tasks.
                new ThreadPoolExecutor.CallerRunsPolicy());
    }


    @Bean
    public ExecutorService getAsynchronousThreadPool()
    {
        return new ThreadPoolExecutor(8,10, 0L ,TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(100),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
