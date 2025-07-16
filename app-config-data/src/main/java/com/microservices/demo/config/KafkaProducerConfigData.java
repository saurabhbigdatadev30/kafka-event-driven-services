package com.microservices.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "kafka-producer-config")
public class KafkaProducerConfigData {
    private String keySerializerClass;
    private String valueSerializerClass;
    private String compressionType;
    private String acks;
    private Integer batchSize;
    private Integer batchSizeBoostFactor;
    private Integer lingerMs;
    private Integer requestTimeoutMs;
    private Integer retryCount;
    private Integer maxInFlightRequestsPerConnection;
    private Integer retryBackoffMs;
    private Boolean enableIdempotence;

}
/*
In the module aap-config-data , we create a configuration class = KafkaProducerConfigData  to read from  config-client-twitter_to_kafka.yml
"kafka-producer-config:"

kafka-producer-config:
  key-serializer-class: org.apache.kafka.common.serialization.LongSerializer
  value-serializer-class: io.confluent.kafka.serializers.KafkaAvroSerializer
  compression-type: snappy
  acks: all
  enable-idempotence: true
  max-in-flight-requests-per-connection: 5
  batch-size: 16384
  batch-size-boost-factor: 100
  linger-ms: 5
  request-timeout-ms: 60000
  retry-count: 5
  retry-backoff-ms: 1000

  To increase throughput we can increase the batched data in request. This can be done by increasing the batch size, adding a compression as batching is done
  after compression, and increase the linger ms to add a delay on producer client to wait more and send more data at once.
 */