package com.microservices.demo.twitter.to.kafka.service.listener;

import lombok.extern.slf4j.Slf4j;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
@Slf4j
public class TwitterListenerAPITest
{
   public void publishTweetsToKafkaCluster(Status status)
   {
      /**
          1. We implement the TwitterListenerAPI [StatusListener] to listen to the tweets , based on the rules configured
          2. We then send the tweets to Kafka topic using the KafkaProducer API.
         */
      StatusListener listener =  new StatusListener() {
           @Override
           public void onStatus(Status status)
           {
               log.info("Twitter messages {}", status.getText());
              // send the tweets to Kafka
           }

           @Override
           public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
           }

           @Override
           public void onTrackLimitationNotice(int i) {
           }

           @Override
           public void onScrubGeo(long l, long l1) {
           }

           @Override
           public void onStallWarning(StallWarning stallWarning) {

           }

           @Override
           public void onException(Exception e) {

           }
       };

   }
}
