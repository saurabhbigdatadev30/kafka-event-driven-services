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
    private String welcomeMessage;
    private Boolean enableMockTweets;
    private Long mockSleepMs;
    private Integer mockMinTweetLength;
    private Integer mockMaxTweetLength;
    private String twitterV2BaseUrl;
    private String twitterV2RulesBaseUrl;
    // Reads from Environment Variable
    private String twitterV2BearerToken;
}
