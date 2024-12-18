package com.microservices.demo.common.config;

import com.microservices.demo.config.RetryConfigData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Configuration
public class RetryConfig {


    // We add module = app-config-data, which contains RetryConfigData . This is responsible to read the retry configurations
    private RetryConfigData retryConfigData;

    public RetryConfig(RetryConfigData configData) {
        this.retryConfigData = configData;
    }

    /*

    We need Retry logic, because when we start everything together including Kafka cluster and the services , using the docker-compose we  might
    need to wait until Kafka cluster healthy and ready to create topics and returning list of topics.

    For this we use spring RetryTemplate Creation of topics and checking existing of topics. Here we need retry logic,
    because when we start everything together including Kafka cluster and your services, we  need to wait until Kafka cluster healthy
    and ready to create topics and returning list of topics.

    The RetryTemplate we created here will be used in the module kafka-admin , to create topic
     (1) Create a @Bean = RetryTemplate.
     (2) Set the  ExponentialBackOffPolicy .
     (3) Set the  SimpleRetryPolicy
     */


    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        //[1] Configure the ExponentialBackOffPolicy
        ExponentialBackOffPolicy exponentialBackOffPolicy = new ExponentialBackOffPolicy();
        exponentialBackOffPolicy.setInitialInterval(retryConfigData.getInitialIntervalMs());
        exponentialBackOffPolicy.setMaxInterval(retryConfigData.getMaxIntervalMs());
        exponentialBackOffPolicy.setMultiplier(retryConfigData.getMultiplier());

        //[2] Set retryTemplate for the ExponentialBackOffPolicy
        retryTemplate.setBackOffPolicy(exponentialBackOffPolicy);

        SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();
        simpleRetryPolicy.setMaxAttempts(retryConfigData.getMaxAttempts());

        // Set retryTemplate for RetryPolicy
        retryTemplate.setRetryPolicy(simpleRetryPolicy);

        return retryTemplate;
    }
}
