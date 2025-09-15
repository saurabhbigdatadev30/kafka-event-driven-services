package com.microservices.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
/*

The below @Configuration class = KafkaConfigData .
This class reads the  configuration from the  file  = config-client-reactive_elastic_query.yml

kafka-config:
  bootstrap-servers: localhost:19092, localhost:29092, localhost:39092
  schema-registry-url-key: schema.registry.url                     => schemaRegistryUrlKey (schema-registry-url-key:)
  schema-registry-url: http://localhost:8081                       => schemaRegistryUrl    (schema-registry-url:)
  topic-name: twitter-topic                                        => topicName
  topic-names-to-create:                                           => topicNamesToCreate   (topic-names-to-create:)
    - twitter-topic
  number-of-partitions: 3                                           => numOfPartitions
  replication-factor: 3                                             => replicationFactor
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
