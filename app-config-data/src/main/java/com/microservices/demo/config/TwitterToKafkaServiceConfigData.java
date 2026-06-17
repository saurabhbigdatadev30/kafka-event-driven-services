package com.microservices.demo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/*
(1) Reads the configuration fromfile  = config-client-twitter_to_kafka.yml.
    Usage of the @Configuration class  & @ConfigurationProperties

(2) We will create a @Configuration class  with @ConfigurationProperties to read "twitter-to-kafka-service"
   section from the YAML file.

twitter-to-kafka-service:
 # get the tweets from Twitter related with these keywords
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
  Read from Environment Variable , set the environment variable = TWITTER_BEARER_TOKEN
  twitter-v2-bearer-token: ${TWITTER_BEARER_TOKEN}
  mock-min-tweet-length: 5
  mock-max-tweet-length: 15
  mock-sleep-ms: 10000
 */

// Understand the mapping :-   "-" in the .yml file is replaced by the camelCase in the java class .
@Data
@Configuration
@ConfigurationProperties(prefix = "twitter-to-kafka-service")
public class TwitterToKafkaServiceConfigData {
    private List<String> twitterKeywords;        // twitter-keywords   =     [twitterKeywords]
    private String welcomeMessage;               // welcome-message    =     [welcomeMessage]
    private Boolean enableMockTweets;            // enable-mock-tweets =     [enableMockTweets]
    private Long mockSleepMs;                    // mock-sleep-ms      =     [mockSleepMs]
    private Integer mockMinTweetLength;          // mock-min-tweet-length  = [mockMinTweetLength]
    private Integer mockMaxTweetLength;          // mock-max-tweet-length  = [mockMaxTweetLength]
    private String twitterV2BaseUrl;             // twitter-v2-base-url    = [twitterV2BaseUrl]
    private String twitterV2RulesBaseUrl;        // twitter-v2-rules-base-url = [twitterV2RulesBaseUrl]
    private String twitterV2RulesEndPoint;       // twitter-v2-rules-endpoint  = Contains the old url
    // Reads from Environment Variable
    private String twitterV2BearerToken;         //  twitter-v2-bearer-token =  [twitterV2BearerToken]

}

// twitter-v2-rules-base-url: https://api.twitter.com/2/tweets/search/stream/rules