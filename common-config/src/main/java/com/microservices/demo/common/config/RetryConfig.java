package com.microservices.demo.common.config;

import com.microservices.demo.config.RetryConfigData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfig {


    // We add module app-config-data, which contains RetryConfigData . This is responsible to read the retry configurations
    private RetryConfigData retryConfigData;

    public RetryConfig(RetryConfigData configData) {
        this.retryConfigData = configData;
    }

    /*

    We need retry logic, because when we start everything together including Kafka cluster and the services , using the docker-compose we  might
    need to wait until Kafka cluster healthy and ready to create topics and returning list of topics.

    For this we use spring RetryTemplate Creation of topics and checking existing of topics. Here we need retry logic,
    because when we start everything together including Kafka cluster and your services, we might need to wait until Kafka cluster healthy
    and ready to create topics and returning list of topics.

     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy exponentialBackOffPolicy = new ExponentialBackOffPolicy();
        exponentialBackOffPolicy.setInitialInterval(retryConfigData.getInitialIntervalMs());
        exponentialBackOffPolicy.setMaxInterval(retryConfigData.getMaxIntervalMs());
        exponentialBackOffPolicy.setMultiplier(retryConfigData.getMultiplier());

        // Set retryTemplate
        retryTemplate.setBackOffPolicy(exponentialBackOffPolicy);

        SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();
        simpleRetryPolicy.setMaxAttempts(retryConfigData.getMaxAttempts());

        // Set retryTemplate
        retryTemplate.setRetryPolicy(simpleRetryPolicy);

        return retryTemplate;
    }
}
