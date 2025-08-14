package com.microservices.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;


/*
Reads the  configuration from the  file  = config-client-twitter_to_kafka.yml

twitter-to-kafka-service:
  twitter-keywords:
    - Java
    - Microservices
    - Kafka
    - Elasticsearch
  welcome-message: Hello microservices!
  enable-v2-tweets: true
  enable-mock-tweets: false
  twitter-v2-base-url: https://api.twitter.com/2/tweets/search/stream?tweet.fields=created_at&expansions=author_id
  twitter-v2-rules-base-url: https://api.twitter.com/2/tweets/search/stream/rules
  Read from Environment Variable
  twitter-v2-bearer-token: ${TWITTER_BEARER_TOKEN}
  mock-min-tweet-length: 5
  mock-max-tweet-length: 15
  mock-sleep-ms: 10000
 */


@Data
@Configuration
@ConfigurationProperties(prefix = "twitter-to-kafka-service")
public class TwitterToKafkaServiceConfigData {
    private List<String> twitterKeywords;
    private String welcomeMessage;          //  Reads from config-client-twitter_to_kafka.yml welcome-message
    private Boolean enableMockTweets;       // in the file config-client-twitter_to_kafka.yml enable-mock-tweets
    private Long mockSleepMs;              //  in the file config-client-twitter_to_kafka.yml mock-sleep-ms
    private Integer mockMinTweetLength;    //  in the file config-client-twitter_to_kafka.yml mock-min-tweet-length
    private Integer mockMaxTweetLength;   //   in the file config-client-twitter_to_kafka.yml mock-max-tweet-length
    private String twitterV2BaseUrl;      //   Reads from config-client-twitter_to_kafka.yml twitter-v2-base-url
    private String twitterV2RulesBaseUrl; //   Reads from config-client-twitter_to_kafka.yml twitter-v2-rules-base-url
    // Reads from Environment Variable
    private String twitterV2BearerToken; //   Reads from config-client-twitter_to_kafka.yml twitter-v2-bearer-token
}

// twitter-v2-rules-base-url: https://api.twitter.com/2/tweets/search/stream/rules