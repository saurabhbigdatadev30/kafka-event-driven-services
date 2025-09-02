package com.microservices.demo.kafka.admin.config;

import com.microservices.demo.config.KafkaConfigData;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import java.util.Map;

/*
    KafkaAdminConfig is a configuration class that sets up an AdminClient bean for managing Kafka topics, consumer groups, etc.
    It reads from the KafkaConfigData to get the bootstrap servers for connecting to the Kafka cluster.

    The @EnableRetry annotation allows retrying operations in case of failures.
 */
@EnableRetry
@Configuration
public class KafkaAdminConfig {

    private final KafkaConfigData kafkaConfigData;

    public KafkaAdminConfig(KafkaConfigData configData) {
        this.kafkaConfigData = configData;
    }

    /*
         Creates AdminClient bean that can be used to manage Kafka topics, consumer groups, etc.
         The AdminClient is configured with the bootstrap servers from KafkaConfigData.

     */
    @Bean
    public AdminClient adminClient() {
        return AdminClient.create(Map.of(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG,
                kafkaConfigData.getBootstrapServers()));
    }
}
