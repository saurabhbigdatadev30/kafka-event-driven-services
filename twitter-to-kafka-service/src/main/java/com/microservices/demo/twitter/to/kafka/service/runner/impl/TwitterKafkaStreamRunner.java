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

/*
  The TwitterKafkaStreamRunner class  implements the StreamRunner interface. This Bean is responsible for connecting to the Twitter API and
  listening for tweets that match certain keywords.
  This Bean will be loaded when enable-mock-tweets = false & enable-v2-tweets =false.
  This class uses the Twitter4J library to create a Twitter stream and filter tweets based on keywords specified in the configuration.
  It's a free service provided by Twitter to access tweets in real-time.
 */
@Slf4j
@Component
@ConditionalOnExpression("not ${twitter-to-kafka-service.enable-mock-tweets} && not ${twitter-to-kafka-service.enable-v2-tweets}")
public class TwitterKafkaStreamRunner implements StreamRunner {

   // private static final Logger LOG = LoggerFactory.getLogger(TwitterKafkaStreamRunner.class);

    // The TwitterToKafkaServiceConfigData is a configuration class that holds the configuration data for the Twitter to Kafka service
    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;

    // The TwitterKafkaStatusListener is a custom listener that handles the status updates from the Twitter stream
    private final TwitterKafkaStatusListener twitterKafkaStatusListener;

    // The TwitterStream is a Twitter 4J library class instance used to connect to the Twitter API and listen for tweets
    private TwitterStream twitterStream;

    /**
     * Constructor for TwitterKafkaStreamRunner
     * It initializes the Twitter stream and sets the status listener
     *
     * @param configData        Configuration data for the Twitter to Kafka service
     * @param statusListener    Listener for Twitter stream status updates
     */
    /*
       We are not using the @AllArgsConstructor here because we want to explicitly define the constructor
       and inject the dependencies via constructor injection. This makes the code more readable and easy to understand.
       Also, it allows us to add additional logic in the constructor if needed in the future.
       We don't inject the TwitterStream here because we want to create a new instance of TwitterStream
       each time the start() method is called. This ensures that we have a fresh connection to the Twitter API
     */
    public TwitterKafkaStreamRunner(TwitterToKafkaServiceConfigData configData,
                                    TwitterKafkaStatusListener statusListener) {
        this.twitterToKafkaServiceConfigData = configData;
        this.twitterKafkaStatusListener = statusListener;
    }

    /**
     --------------------------------------------------------------------------------------------------------------------------------------
     [1] Instantiate the TwitterStream using TwitterStreamFactory.
                    twitterStream = new TwitterStreamFactory().getInstance();

     Note:
     Ideally TwitterStreamFactory should be created as a @Bean in a Configuration class and should be injected here.

     // [1] TwitterStreamConfig.java -- We create @Bean TwitterStream in a @Configuration class [TwitterStreamConfig] in app-config-data module
          This will make the TwitterStream a singleton bean managed by application context and can be injected wherever needed.

          @Configuration
          public class TwitterStreamConfig {
             @Bean
             public TwitterStream twitterStream() {
             return new TwitterStreamFactory().getInstance();
             }
          }

          // [2] In the class TwitterKafkaStreamRunner.java -- We will inject the TwitterStream Bean in the constructor injection
                 instead of creating a new instance and use it in the start() method

          public class TwitterKafkaStreamRunner
          {
             private final TwitterStream twitterStream;  // Created as a @Bean in TwitterStreamConfig class

             public TwitterKafkaStreamRunner(TwitterToKafkaServiceConfigData configData, TwitterKafkaStatusListener statusListener,
                                             TwitterStream twitterStream)
               {
 -----------------------------------------------------------------------------------------------------------------------------------------------

     [2] Add  listener to the TwitterStream. This listener will handle the incoming tweets and other events.
                  twitterStream.addListener(twitterKafkaStatusListener);

     [3] Configure the filter with the keywords. This will start the stream and filter tweets based on the specified keywords.
                 FilterQuery filterQuery = new FilterQuery(keywords);
                 twitterStream.filter(filterQuery);
     --------------------------------------------------------------------------------------------------------------------------------------
     So, the start() method defined in th TwitterKAfkaStreamRunner (Implemetation-1] does the following:
        1. Instantiates the TwitterStream using TwitterStreamFactory, which is used to connect to the Twitter API.
        2. Adds the listener to the TwitterStream, which will handle the incoming tweets and other events.
        3. Configures the filter with the keywords , add to the TwitterStream. to starts filtering the Twitter stream.
     */
     @Override
     public void start() throws TwitterException {
        // Print the filter data to the log , reads the keywords from the configuration file
        log.info(twitterToKafkaServiceConfigData.getTwitterKeywords().toArray(new String[0])[0]);
        // [1] Instantiate the TwitterStream using TwitterStreamFactory , should ideally be a @Bean
         // in a Configuration class and should be injected here.
        twitterStream = new TwitterStreamFactory().getInstance();
        /*
           [2] Add the listener to the TwitterStream ,this invokes the onStatus(Status status) method of the listener when
               a new tweet is received from the Twitter stream & pass the tweet to the listener.
           */
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
     * This method adds a filter to the Twitter stream to check for tweets containing the keywords
     * specified in the configuration file.
     * It creates a FilterQuery with the keywords and applies it to the Twitter stream.
     */
    private void addFilter() {
        String[] keywords = twitterToKafkaServiceConfigData.getTwitterKeywords().toArray(new String[0]);
         // Create a FilterQuery with the keywords
         // The FilterQuery is used to filter tweets based on the specified keywords
         FilterQuery filterQuery = new FilterQuery(keywords);
         /*[3] Configure the filter with the keywords and start filtering the Twitter stream
              This will start the stream and filter tweets based on the specified keywords
              */
        twitterStream.filter(filterQuery);
        //  LOG.info("Started filtering twitter stream for keywords {}", Arrays.toString(keywords));
        log.info("Started filtering twitter stream for keywords {}", Arrays.toString(keywords));
    }
}
