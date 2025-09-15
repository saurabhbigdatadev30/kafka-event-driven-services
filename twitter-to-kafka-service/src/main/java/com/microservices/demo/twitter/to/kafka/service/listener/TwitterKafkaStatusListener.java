package com.microservices.demo.twitter.to.kafka.service.listener;

import com.microservices.demo.config.KafkaConfigData;
import com.microservices.demo.kafka.avro.model.TwitterAvroModel;
import com.microservices.demo.kafka.producer.config.service.KafkaProducer;
import com.microservices.demo.twitter.to.kafka.service.transformer.TwitterStatusToAvroTransformer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import twitter4j.Status;
import twitter4j.StatusAdapter;
@Slf4j
@Component


/*
     (1) public class TwitterKafkaStatusListener extends StatusAdapter & overrides the onStatus() method.

     (2) onStatus method --> When a new status/tweet is received, it transforms the tweet object into an Avro model
         and publishes it to a Kafka topic. The tweet object  is transformed into TwitterAvroModel (value) & the key is userId of the tweet ,
         we send  KafkaProducer<Long,TwitterAvroModel>

     Has dependencies to KafkaProducer  , app-config-data  and KafkaModel modules
 */
public class TwitterKafkaStatusListener extends StatusAdapter {
    // Dependencies: app-config-data module    ->  To read the topic name where the message to be published.
    private final KafkaConfigData kafkaConfigData;
   // Dependencies:  kafka-producer module     ->  To send the message to Kafka topic ,  KafkaProducer<Long,TwitterAvroModel>.
    private final KafkaProducer<Long, TwitterAvroModel> kafkaProducer;

    private final TwitterStatusToAvroTransformer twitterStatusToAvroTransformer;

    public TwitterKafkaStatusListener(KafkaConfigData configData,
                                      KafkaProducer<Long, TwitterAvroModel> producer,
                                      TwitterStatusToAvroTransformer transformer) {
        this.kafkaConfigData = configData;
        this.kafkaProducer = producer;
        this.twitterStatusToAvroTransformer = transformer;
    }

    /**
     * This method is called when a new status is received from Twitter.
     * It transforms the status into an Avro model and publishes it to a Kafka topic.
     * So this module has dependency to KafkaProducer and KafkaModel modules
     * @param status The status received from Twitter.
     */
    @Override
    public void onStatus(Status status) {

        // status will contain the filtered tweet messages
        //LOG.info("Received status text {} publishing  to kafka topic {}", status.getText(), kafkaConfigData.getTopicName());
        log.info("Received status text {} publishing  to kafka topic {}", status.getText(), kafkaConfigData.getTopicName());
        // Construct the AvroModel from the status object...The AvroModel is generated using the Avro schema
        // The AvroModel is a representation of the tweet in a format suitable for Kafka ,
        // Dependencies: kafka-model module
        TwitterAvroModel twitterAvroModel = twitterStatusToAvroTransformer.getTwitterAvroModelFromStatus(status);

        // publish to Kafka topic (key,value), where key = userID & the value = TwitterAvroModel
          kafkaProducer.send(kafkaConfigData.getTopicName(), twitterAvroModel.getUserId(), twitterAvroModel);
    }
}
