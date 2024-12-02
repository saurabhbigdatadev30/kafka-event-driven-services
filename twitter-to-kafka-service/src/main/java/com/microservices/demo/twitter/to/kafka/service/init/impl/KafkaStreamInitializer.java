package com.microservices.demo.twitter.to.kafka.service.init.impl;

import com.microservices.demo.config.KafkaConfigData;
import com.microservices.demo.kafka.admin.client.KafkaAdminClient;
import com.microservices.demo.twitter.to.kafka.service.init.StreamInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KafkaStreamInitializer implements StreamInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaStreamInitializer.class);

    /*
     In the twitter-to-kafka-service Module we add the modules
      Add module = app-config-data
      Add module = kafka-admin . The kafka-admin module adds the common-config module
       To read Topic Name , Partitions ... We add the app-config-data module here , since this will contain all the classes i.e @Configurations = KafkaConfigData
       that is responsible to read the kafka-config properties

     */
    private final KafkaConfigData kafkaConfigData;

    // Create the Topic at start up we use KafkaAdminClient . Include the module = kafka-admin
    private final KafkaAdminClient kafkaAdminClient;

    public KafkaStreamInitializer(KafkaConfigData configData, KafkaAdminClient adminClient) {
        this.kafkaConfigData = configData;
        this.kafkaAdminClient = adminClient;
    }

    @Override
    public void init() {
        // We create Kafka topic using KafkaAdmin
        kafkaAdminClient.createTopics();
        kafkaAdminClient.checkSchemaRegistry();
        LOG.info("Topics with name {} is ready for operations!", kafkaConfigData.getTopicNamesToCreate().toArray());
    }
}
