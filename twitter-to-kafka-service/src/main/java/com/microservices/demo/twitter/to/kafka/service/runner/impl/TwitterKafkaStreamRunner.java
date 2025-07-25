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
    public TwitterKafkaStreamRunner(TwitterToKafkaServiceConfigData configData,
                                    TwitterKafkaStatusListener statusListener) {
        this.twitterToKafkaServiceConfigData = configData;
        this.twitterKafkaStatusListener = statusListener;
    }

    /**
     * This method is used to start the Twitter stream and listen for tweets
     * It reads the keywords from the configuration file and adds a filter to the Twitter stream
     * to check for tweets containing those keywords
     *
     * @throws TwitterException if there is an error starting the Twitter stream
     */
    @Override
    public void start() throws TwitterException {
        //Print the filter data to the log , reads the keywords from the configuration file
        log.info(twitterToKafkaServiceConfigData.getTwitterKeywords().toArray(new String[0])[0]);
        twitterStream = new TwitterStreamFactory().getInstance();
        twitterStream.addListener(twitterKafkaStatusListener);
        addFilter();
    }

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
        FilterQuery filterQuery = new FilterQuery(keywords);
        // Set the filter query to track the keywords
        twitterStream.filter(filterQuery);
        //  LOG.info("Started filtering twitter stream for keywords {}", Arrays.toString(keywords));
        log.info("Started filtering twitter stream for keywords {}", Arrays.toString(keywords));
    }
}
