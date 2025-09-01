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
import java.util.stream.Collectors;


// https://github.com/twitterdev/Twitter-API-v2-sample-code/blob/main/Filtered-Stream/FilteredStreamDemo.java

/*
  This class implements the StreamRunner interface and is responsible for starting the Twitter V2 stream & will be getting
  loaded only if the enable-v2-tweets = true & enable-mock-tweets = false
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
                // twitterV2StreamHelper.setupRules(bearerToken, getRules());

                /*
                   ## Lambda Expression : Java 8 Feature Key Points
                   The advantage of using a lambda expression here is that it allows for more flexibility and reusability.
                   By passing a Supplier<Map<String, String>> as argument to the setupRulesModified method, we can easily change the way
                   rules are generated without modifying the method itself.

                    The signature of the method [setupRulesModified] remains the same, but the logic for generating the rules
                    can be changed by passing different lambda expressions.
                    This allows for better separation of concerns, as the method is only responsible for
                    setting up the rules . This makes the code more modular and easier to maintain.
                    Additionally, using a lambda expression can make the code more concise and easier to read, especially
                    when the logic for generating the rules is simple and can be expressed in a single line.
                 */
                twitterV2StreamHelper.setupRulesModified(bearerToken, () -> {
                    Map<String, String> rules = new HashMap<>();
                    rules.put("cats has:images", "cat images");
                    rules.put("dogs has:images", "dog images");
                    return rules;
                });

                // Connect to the Twitter V2 stream API , start streaming tweets based on defined rules & send this to Kafka
                twitterV2StreamHelper.connectStream(bearerToken);
            } catch (IOException | URISyntaxException | TwitterException | JSONException e) {
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



    // This method creates a map of rules for filtering tweets based on keywords.
    private Map<String, String> getRules() {
        List<String> keywords = twitterToKafkaServiceConfigData.getTwitterKeywords();
      var rulesMap =   keywords.stream().collect(Collectors.toMap(
                keyword -> keyword,
                keyword -> "Keyword: " + keyword
        ));
        LOG.info("Created filter for twitter stream for keywords {}", keywords.toArray());
        return rulesMap;
    }
}
