package com.microservices.demo.twitter.to.kafka.service.runner.impl;

import com.microservices.demo.config.TwitterToKafkaServiceConfigData;
import com.microservices.demo.twitter.to.kafka.service.runner.StreamRunner;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import twitter4j.TwitterException;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;


// https://github.com/twitterdev/Twitter-API-v2-sample-code/blob/main/Filtered-Stream/FilteredStreamDemo.java

/*
   This class implements the StreamRunner interface and is responsible for starting the Twitter V2 stream & will be
   getting loaded only if the enable-v2-tweets = true & enable-mock-tweets = false
 */
@Component
@ConditionalOnExpression("${twitter-to-kafka-service.enable-v2-tweets} && not ${twitter-to-kafka-service.enable-mock-tweets}")

public class TwitterV2KafkaStreamRunner implements StreamRunner {

    private static final Logger LOG = LoggerFactory.getLogger(TwitterV2KafkaStreamRunner.class);

    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;

    // The TwitterV2StreamHelper is a helper class that handles the connection to the Twitter V2 API and manages the stream
    private final TwitterV2StreamHelper twitterV2StreamHelper;

    public TwitterV2KafkaStreamRunner(TwitterToKafkaServiceConfigData configData,
                                      TwitterV2StreamHelper twitterV2StreamHelper) {
        this.twitterToKafkaServiceConfigData = configData;
        this.twitterV2StreamHelper = twitterV2StreamHelper;
    }

    @Override
    public void start() {
        // Set the Bearer Token in Environment variable.
        String bearerToken = twitterToKafkaServiceConfigData.getTwitterV2BearerToken();
        if (null != bearerToken) {
            try {
                 /**
                     The method setupRulesModified1 method is responsible for setting up the rules for filtering the tweets based
                     on the keywords specified in the configuration.
                     👉  setupRulesModified1 is HOF , it takes lambda expression as input argument () -> rulesToBeCreated()
                     👉 *** This lambda expression implements the @Functional interface <Supplier></Supplier> ***
                  */
                twitterV2StreamHelper.setupRulesModified1(bearerToken, () -> rulesToBeCreated());
                // THe setupRulesModified1RefactoredHOF is same as setupRulesModified1
                twitterV2StreamHelper.setupRulesModified1RefactoredHOF(bearerToken, () -> rulesToBeCreated());
                twitterV2StreamHelper.connectStream(bearerToken);
            } catch (IOException | URISyntaxException | JSONException e) {
                LOG.error("Error streaming tweets in V2 API!", e);
                throw new RuntimeException("Error streaming tweets!", e);
            }
        } else {
            LOG.error("There was a problem getting your bearer token. " +
                    "Please make sure you set the TWITTER_BEARER_TOKEN environment variable");
            throw new RuntimeException("There was a problem getting your bearer token. +" +
                    "Please make sure you set the TWITTER_BEARER_TOKEN environment variable");
        }
    }

    // This method creates a map of rules for filtering tweets based on keywords which we read from the config file .
    private Map<String,String> getRules() {
        List<String> keywords = twitterToKafkaServiceConfigData.getTwitterKeywords();
        // Convert List to Map , Map<"Java", "Keyword: Java">
        var rulesMap =   keywords.stream().collect(Collectors.toMap(
                keyword -> keyword,
                keyword -> "Keyword: " + keyword
        ));
        LOG.info("Created filter for twitter stream for keywords {}", keywords.toArray());
        return rulesMap;
    }


    private Map<String,String>rulesToBeCreated(){
        List<String> rulesConfig = twitterToKafkaServiceConfigData.getTwitterKeywords();
        return rulesConfig
                .stream()
                .collect(Collectors.toMap(rule -> rule,
                        rule -> "Keyword:: " + rule));

    }


}
