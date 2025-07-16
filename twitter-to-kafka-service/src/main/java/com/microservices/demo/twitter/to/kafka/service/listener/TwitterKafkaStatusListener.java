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
* * This class is a listener for Twitter statuses.
 * It extends StatusAdapter to receive status updates from Twitter4J.
 * When a new status is received, it transforms the status into an Avro model
 * and publishes it to a Kafka topic.
 */
public class TwitterKafkaStatusListener extends StatusAdapter {

    //private static final Logger LOG = LoggerFactory.getLogger(TwitterKafkaStatusListener.class);

    private final KafkaConfigData kafkaConfigData;

    private final KafkaProducer<Long, TwitterAvroModel> kafkaProducer;

    private final TwitterStatusToAvroTransformer twitterStatusToAvroTransformer;

    public TwitterKafkaStatusListener(KafkaConfigData configData,
                                      KafkaProducer<Long, TwitterAvroModel> producer,
                                      TwitterStatusToAvroTransformer transformer) {
        this.kafkaConfigData = configData;
        this.kafkaProducer = producer;
        this.twitterStatusToAvroTransformer = transformer;
    }

   // This method will be called by Twitter4J when a new status is available
   //  This is the entry point for processing tweets
    // It will be called for each tweet that matches the filter criteria set in the Twitter stream
    @Override
    public void onStatus(Status status) {

        // status will contain the filtered tweet messages
        //LOG.info("Received status text {} publishing  to kafka topic {}", status.getText(), kafkaConfigData.getTopicName());
        log.info("Received status text {} publishing  to kafka topic {}", status.getText(), kafkaConfigData.getTopicName());
        // Construct the AvroModel from the status object...The AvroModel is generated using the Avro schema
        // The AvroModel is a representation of the tweet in a format suitable for Kafka ,
        TwitterAvroModel twitterAvroModel = twitterStatusToAvroTransformer.getTwitterAvroModelFromStatus(status);

        // publish to Kafka topic (key,value), where key = userID & the value = TwitterAvroModel
          kafkaProducer.send(kafkaConfigData.getTopicName(), twitterAvroModel.getUserId(), twitterAvroModel);
    }
}
