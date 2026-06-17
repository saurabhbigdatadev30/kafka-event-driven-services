package com.microservices.demo.twitter.to.kafka.service.runner.impl;

import com.microservices.demo.config.TwitterToKafkaServiceConfigData;
import com.microservices.demo.twitter.to.kafka.service.listener.TwitterKafkaStatusListener;
import com.microservices.demo.twitter.to.kafka.service.runner.StreamRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import twitter4j.FilterQuery;
import twitter4j.TwitterException;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;

import jakarta.annotation.PreDestroy;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;

/**
  1. The TwitterKafkaStreamRunner class  implements the StreamRunner interface.
  2. This Bean is responsible for connecting to the Twitter API and listening for tweets that match
      certain keywords.For this we define configure FilterQuery
  3. This Bean will be loaded when enable-mock-tweets = false & enable-v2-tweets =false.
  4. This Implementation  uses the Twitter4J library to create a Twitter stream and filter tweets based on
     keywords specified in the configuration.
 */
@Slf4j
@Component
@ConditionalOnExpression("not ${twitter-to-kafka-service.enable-mock-tweets} && not ${twitter-to-kafka-service.enable-v2-tweets}")
public class TwitterKafkaStreamRunner implements StreamRunner {

   // private static final Logger LOG = LoggerFactory.getLogger(TwitterKafkaStreamRunner.class);

    // The TwitterToKafkaServiceConfigData is a configuration class that holds the configuration data for the Twitter to Kafka service
    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;

    // The TwitterKafkaStatusListener is a custom listener that handles the status updates from the Twitter stream.
    private final TwitterKafkaStatusListener twitterKafkaStatusListener;

    // The TwitterStream is a Twitter 4J library class instance used to connect to the Twitter API and listen for tweets.
    private TwitterStream twitterStream;

    /**
        Using @Lombok for Constructor Injection
        @RequiredArgsConstructor    // ← Only injects FINAL fields, excludes the other fields
        private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;  // ✅ included
        private final TwitterKafkaStatusListener twitterKafkaStatusListener;            // ✅ included
        private TwitterStream twitterStream;   // NOT final → ✅ EXCLUDED
        TwitterStream has no Spring @Bean definition, so we will create it in the start() method .
     */

    public TwitterKafkaStreamRunner(TwitterToKafkaServiceConfigData configData,
                                    TwitterKafkaStatusListener statusListener) {
        this.twitterToKafkaServiceConfigData = configData;
        this.twitterKafkaStatusListener = statusListener;
        /*
              1. The TwitterStream is intentionally excluded from constructor injection because it is not a
                 Spring-managed bean and does not have a @Bean definition.

              2. TwitterStream will be created in the start() method to ensure a fresh connection to the Twitter API
                  each time the stream is started.
         */
    }

     @Override
     public void start() throws TwitterException {

   /**
        1. Build  a TwitterStream instance using the TwitterStreamFactory class from the Twitter4J library.
        2. This instance will be used to connect to the Twitter API and listen for tweets.
        3.  Register Listener with the TwitterStream instance to receive status updates from the Twitter stream.
        4.  The listener will handle the incoming tweets and process them accordingly
             (e.g., transform and publish to Kafka).
        5.  We Register the FilterQuery with the TwitterStream instance to filter tweets based on the specified keywords.
            The FilterQuery will ensure that only tweets containing the specified keywords are received by the listener.
        6.  The onStatus(Status staus) is invoked , when matching tweet to configured filer
             So, we get continues stream of tweets from the Twitter API into the ingestion service
      */
          twitterStream = new TwitterStreamFactory().getInstance();
          twitterStream.addListener(twitterKafkaStatusListener);
           addFilter();
    }

    // Will not work with Prototype scope
    @PreDestroy
    public void shutdown() {
        if (twitterStream != null) {
          //  LOG.info("Closing twitter stream!");
            log.info("Closing twitter stream!");
            twitterStream.shutdown();
        }
    }

    /**
         This method adds a filter to the Twitter stream to check for tweets containing the keywords
         specified in the configuration file.
         It creates a FilterQuery with the keywords and applies it to the Twitter stream.
     */
    private void addFilter() {
        String[] keywords = twitterToKafkaServiceConfigData.getTwitterKeywords().toArray(new String[0]);
         // Build a FilterQuery with the keywords , used to filter tweets based on the specified keywords
         FilterQuery filterQuery = new FilterQuery(keywords);
        // Register the FilterQuery with twitterStream
        twitterStream.filter(filterQuery);
        //  LOG.info("Started filtering twitter stream for keywords {}", Arrays.toString(keywords));
        log.info("Started filtering twitter stream for keywords {}", Arrays.toString(keywords));
    }
}
