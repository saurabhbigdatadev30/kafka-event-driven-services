package com.microservices.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
/*

The below @Configuration class = KafkaConfigData .
This class reads the  configuration from the  file  = config-client-reactive_elastic_query.yml

kafka-config:
  topic-name: twitter-analytics-topic-2
  topic-names-to-create:
    - twitter-analytics-topic-2
  num-partitions: 3
  replication-factor: 3
  bootstrap-servers: localhost:19092, localhost:29092, localhost:39092
  schema-registry-servers: http://localhost:8081
  sleep-time-ms: 1000
  max-interval-ms: 10000
  retry-multiplier: 2.0
  admin-retry-max: 1
 */

@Data
@Configuration
@ConfigurationProperties(prefix = "kafka-config")
public class KafkaConfigData {
    private String bootstrapServers;
    private String schemaRegistryUrlKey;
    private String schemaRegistryUrl;
    private String topicName;
    private List<String> topicNamesToCreate;
    private Integer numOfPartitions;
    private Short replicationFactor;
    //  Here we don't add the property  enable-v2-tweets, since this will not be read through  Java object , it will be used
    //  as @ConditionalOnExpression("${twitter-to-kafka-service.enable-v2-tweets} , to load the TwitterV2Stream dynamically
}
