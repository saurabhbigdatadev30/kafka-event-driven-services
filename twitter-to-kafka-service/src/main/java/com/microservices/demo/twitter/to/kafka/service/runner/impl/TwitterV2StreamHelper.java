package com.microservices.demo.twitter.to.kafka.service.runner.impl;

import com.microservices.demo.config.TwitterToKafkaServiceConfigData;
import com.microservices.demo.twitter.to.kafka.service.listener.TwitterKafkaStatusListener;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component

/*
https://github.com/xdevplatform/Twitter-API-v2-sample-code/blob/main/Filtered-Stream/FilteredStreamDemo.java

StreamRunner Interface has 3 different implementations .
We load the V2 Implementation .
TwitterV2StreamHelper is a helper class to connect to Twitter V2 API and stream tweets.
ConditionalOnExpression annotation allows  to load a spring bean at runtime using a configuration
value. The config-client-twitter_to_kafka.yml will have the below properties, to load particular implementation at runtime

twitter-to-kafka-service:
 enable-v2-tweets: true
 enable-mock-tweets: false

 So based on the above properties, the V2 implementation & the respective helper class  will be loaded at runtime.
 */

@ConditionalOnExpression("${twitter-to-kafka-service.enable-v2-tweets} && not ${twitter-to-kafka-service.enable-mock-tweets}")
//@ConditionalOnProperty(name = "twitter-to-kafka-service.enable-v2-tweets", havingValue = "true", matchIfMissing = true)
public class TwitterV2StreamHelper {

    private static final Logger LOG = LoggerFactory.getLogger(TwitterV2StreamHelper.class);

    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;

    private final TwitterKafkaStatusListener twitterKafkaStatusListener;

    private static final String tweetAsRawJson = "{" +
            "\"created_at\":\"{0}\"," +
            "\"id\":\"{1}\"," +
            "\"text\":\"{2}\"," +
            "\"user\":{\"id\":\"{3}\"}" +
            "}";

    private static final String TWITTER_STATUS_DATE_FORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";

    public TwitterV2StreamHelper(TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData,
                                 TwitterKafkaStatusListener twitterKafkaStatusListener) {
        this.twitterToKafkaServiceConfigData = twitterToKafkaServiceConfigData;
        this.twitterKafkaStatusListener = twitterKafkaStatusListener;
    }

    /*
     [1] Build the HttpClient object using Builder pattern.
     [2] Build URIBuilder, reading the twitter-v2-base-url  from the Configuration .yml file.
     [3] Using the above created URIBuilder object , Build HttpGet Object.
     [4] Add the Bearer Token in the HttpGet Header for oAuth Authorization.
     [5] Send HttpRequest [HttpGet] to the Twitter V2 Base url , using the HttpClient. We get the HttpResponse object
     [6] Build the HttpEntity from the HttpResponse object.
     [7] From the HttpEntity build the  BufferedReader object, to read the stream of tweets from the Twitter V2 API.
     [8] THe while loop never ends until we close the connection from our side or Twitter disconnects the connection.
         We get continuous stream of tweets endlessly from Twitter V2 API endpoint until we disconnect in the while loop.
         We read each line from the BufferedReader object, and for each line we create a Twitter4J Status object.
         and pass it to TwitterKafkaStatusListener's onStatus method.
     */


    void connectStream(String bearerToken) throws IOException, URISyntaxException, TwitterException, JSONException {

        // [1] Build the HttpClient object using Builder pattern
        HttpClient httpClient = HttpClients.custom()
                                .setDefaultRequestConfig(RequestConfig.custom()
                                .setCookieSpec(CookieSpecs.STANDARD).build())
                                .build();

        // [2] Build URIBuilder, reading the twitter-v2-base-url  from the Configuration .yml file
        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2BaseUrl());

        // [3] Using the above created URIBuilder object , Build HttpGet Object.
        HttpGet httpGet = new HttpGet(uriBuilder.build());

        // [4] Add the Bearer Token in the HttpGet Header for oAuth Authorization
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));

        // [5] Send HttpRequest [HttpGet]  to the twitter-v2-base-url value , using the HttpClient.
        // We get continues Stream of tweets from Twitter V2 API endpoint. We get the HttpResponse object
         HttpResponse response = httpClient.execute(httpGet);

        // [6] Build the HttpEntity from the HttpResponse object.
        HttpEntity entity = response.getEntity();
        if (null != entity) {
            // [7] From the HttpEntity build the  BufferedReader object, to read the continues stream of tweets from the Twitter V2 API
            // The entity.getContent() returns an InputStream from the HTTP response body.
            BufferedReader reader = new BufferedReader(new InputStreamReader((entity.getContent())));
            // Read the first line from the stream of tweets.
            String tweetData = reader.readLine();
            LOG.info("Twitter stream has started streaming tweets");

            // The while loop never ends until we close the connection from our side or Twitter disconnects the connection
            // We get continuous stream of tweets endlessly from Twitter V2 API endpoint until we disconnect in the while loop.
            // [8] We read each line from the BufferedReader object, and for each line we create a Twitter4J Status object
            while (tweetData != null) {
                tweetData = reader.readLine();
                if (!tweetData.isEmpty()) {
                    // Format the Tweets as per Twitter4J Status object format
                    String tweet = getFormattedTweet(tweetData);
                    Status status = null;

                    // [7]  Create the status from the tweet
                    try {
                        status = TwitterObjectFactory.createStatus(tweet);
                    } catch (TwitterException e) {
                        LOG.error("Could not create status for text: {}", tweet, e);
                    }
                    if (status != null) {
                        // This will publish the tweet to the Kafka Topic
                        twitterKafkaStatusListener.onStatus(status);
                    }
                }
            }
        }
    }

    /*
     * Helper method to setup rules before streaming data
     * */
    void setupRules(String bearerToken, Map<String, String> rules) throws IOException, URISyntaxException {
        List<String> existingRules = getRules(bearerToken);
        if (existingRules.size() > 0) {
            deleteRules(bearerToken, existingRules);
        }
        createRules(bearerToken, rules);
        LOG.info("Created rules for twitter stream {}", rules.keySet().toArray());
    }



    /*
    In the createRules method, we will be performing the following steps:
     [1]  Build HttpClient Object . This HttpClient will be used to send the HttpPost request to the Twitter V2 API Rules endpoint.
     [2]  Build URIBuilder Object, reading  twitter-v2-rules-base-url from configuration.
     [3]  Build a HttpPost request [HttpPost] Object. Set Bearer Token in the Header[Authorization] of HttpPost.
     [4]  Set the rules in the HttpPost body.
     [5]  Using the HttpClient , we send the HttpPost request to the Twitter V2 API Rules endpoint.
     [6]  From HttpResponse, we  get the response entity from the HttpResponse.
     [7]  Print the response entity as a String.

      */
    private void createRules(String bearerToken, Map<String, String> rules) throws URISyntaxException, IOException {
        // [1] Build HttpClient Object using Builder pattern. This HttpClient will be used to send the HttpPost request to the Twitter V2 API
        HttpClient httpClient = HttpClients.custom()
                                           .setDefaultRequestConfig(RequestConfig.custom()
                                           .setCookieSpec(CookieSpecs.STANDARD).build())
                                           .build();

        // [2] Build URIBuilder Object, reading twitter-v2-rules-base-url from configuration.
        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl());

        // [3] Build a HttpPost request [HttpPost] Object. Set Bearer Token in the Header[Authorization] of HttpPost
        HttpPost httpPost = new HttpPost(uriBuilder.build());
        // Set the Authorization header with Bearer Token for oAuth
        httpPost.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpPost.setHeader("content-type", "application/json");

        // [4] Set the rules in the HttpPost body using the setEntity() method of HttpPost
        StringEntity body = new StringEntity(getFormattedString("{\"add\": [%s]}", rules));
        httpPost.setEntity(body);

        // [5] Using the HttpClient , send the HttpPost
        HttpResponse response = httpClient.execute(httpPost);
        HttpEntity entity = response.getEntity();
        if (null != entity) {
            System.out.println(EntityUtils.toString(entity, "UTF-8"));
        }
    }

    /*
      Helper method to get existing rules
       [1] Build HttpClient Object using Builder pattern. This HttpClient will be used to send the HttpRequest (get) to the Twitter V2 API.
       [2] Build URIBuilder Object, reading the twitter-v2-rules-base-url from configuration.
       [3] Build a HttpGet request [HttpGet] Object from URIBuilder object. Set Bearer Token in the Header[Authorization] of HttpGet.
       [4] Using the HttpClient execute() method, send the HttpGet request object to the Twitter V2 API Rules endpoint.
       [5] Get the response entity from the HttpResponse. From the HTTPResponse  -> HttpEntity
       [6] Create [JSONObject] from the HttpEntity. Reads the response body as a UTF-8 string.
       [7] If the JSONObject has "data" key, then extract the rules from the JSON object.
       [8] The "data" key contains an array of rules, we will extract the "id".
       [9] Return the list of rule ids.
       This method retrieves the existing rules from the Twitter V2 API Rules endpoint.
     */
    private List<String> getRules(String bearerToken) throws URISyntaxException, IOException {
        List<String> rules = new ArrayList<>();

        // [1] Build HttpClient Object using Builder pattern. This HttpClient will be used to send the HttpGet Request to the Twitter V2 API
        HttpClient httpClient = HttpClients.custom()
                        .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                        .build();

        // [2] Build URIBuilder Object, reading the twitter-v2-rules-base-url from configuration
        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl());

        // [3] Build a HttpGet request [HttpGet] Object , passing the uriBuilder.  Set Bearer Token in the Header[Authorization] of HttpGet
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpGet.setHeader("content-type", "application/json");

        // [4] Using the HttpClient, send the HttpGet request to the Twitter V2 API Rules endpoint
        HttpResponse response = httpClient.execute(httpGet);
        // [5] Get the response entity from the HttpResponse
        HttpEntity entity = response.getEntity();
        if (null != entity) {
            // [6] Reads the HTTP ResponseBody from the Response HttpEntity as a UTF-8 string
            // [7] Creates JSONObject from  this String to then fetch the JSON fields.
            JSONObject json = new JSONObject(EntityUtils.toString(entity, "UTF-8"));
            if (json.length() > 1 && json.has("data")) {
                // [8] If the JSON object has "data" key, then extract the rules from the JSON object
                JSONArray array = (JSONArray) json.get("data");
                for (int i = 0; i < array.length(); i++) {
                    // [9]  The "data" key contains an array of rules, we will extract the "id
                    JSONObject jsonObject = (JSONObject) array.get(i);
                    rules.add(jsonObject.getString("id"));
                }
            }
        }
        return rules;
    }

    /*
     * Helper method to delete rules
     * */
    private void deleteRules(String bearerToken, List<String> existingRules) throws URISyntaxException, IOException {
        HttpClient httpClient = HttpClients.custom()
                                      .setDefaultRequestConfig(RequestConfig.custom()
                                      .setCookieSpec(CookieSpecs.STANDARD).build())
                                      .build();

        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl());

        HttpPost httpPost = new HttpPost(uriBuilder.build());
        httpPost.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpPost.setHeader("content-type", "application/json");
        StringEntity body = new StringEntity(getFormattedString("{ \"delete\": { \"ids\": [%s]}}",
                existingRules));
        httpPost.setEntity(body);
        HttpResponse response = httpClient.execute(httpPost);
        HttpEntity entity = response.getEntity();
        if (null != entity) {
            System.out.println(EntityUtils.toString(entity, "UTF-8"));
        }
    }

    private String getFormattedString(String string, List<String> ids) {
        StringBuilder sb = new StringBuilder();
        if (ids.size() == 1) {
            return String.format(string, "\"" + ids.get(0) + "\"");
        } else {
            for (String id : ids) {
                sb.append("\"" + id + "\"" + ",");
            }
            String result = sb.toString();
            return String.format(string, result.substring(0, result.length() - 1));
        }
    }

    private String getFormattedString(String string, Map<String, String> rules) {
        StringBuilder sb = new StringBuilder();
        if (rules.size() == 1) {
            String key = rules.keySet().iterator().next();
            return String.format(string, "{\"value\": \"" + key + "\", \"tag\": \"" + rules.get(key) + "\"}");
        } else {
            for (Map.Entry<String, String> entry : rules.entrySet()) {
                String value = entry.getKey();
                String tag = entry.getValue();
                sb.append("{\"value\": \"" + value + "\", \"tag\": \"" + tag + "\"}" + ",");
            }
            String result = sb.toString();
            return String.format(string, result.substring(0, result.length() - 1));
        }
    }

   /*
---------------------------------------------------------------------------------------------------------------------
     Formatted tweet received from the V2 API  will be in the form of a JSON string with the following structure.

               String tweetData :
                                   {
                                       "created_at": "Mon Apr 08 12:34:56 UTC 2024",
                                       "id": "1234567890",  // Tweet ID
                                       "text": "This is a sample tweet",
                                       "author": {
                                           "id": "9876543210"  // Author ID
                                     }
        The getFormattedTweet() method will be called for each incoming tweet within the endless while() loop.
        The loop only ends if the connection is closed or interrupted.
        The getFormattedTweet() method extracts the tweetData from the JSON data and formats it into a structure compatible with
        Twitter4J's Status object.

         ZonedDateTime.parse(jsonData.get(created_at).toString())       : Converts the extracted date-time in String format to ZonedDateTime format.
                      .withZoneSameInstant(ZoneId.of("UTC"))            : Converts the time extracted [ZonedDateTime]  to UTC format.
                      .format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH)) : Formats the UTC ZonedDateTime to a specific
                                                                                                          pattern defined in TWITTER_STATUS_DATE_FORMAT.

            This statement parses the "created_at" date string from the tweet JSON, converts it to a ZonedDateTime,
            adjusts it to UTC time zone, and formats it as a string using the pattern defined in TWITTER_STATUS_DATE_FORMAT
            (e.g., EEE MMM dd HH:mm:ss zzz yyyy) with English locale. This ensures the tweet's timestamp is standardized to UTC
             and formatted for Twitter4J compatibility.
     */

    private String getFormattedTweet(String tweetData) {
        // Create a JSONObject extracting the "data" key .The "data" key contains the tweet information as value
        // Retrieves the "data" field from the JSON, which contains the tweet details.
        JSONObject jsonData = (JSONObject)new JSONObject(tweetData).get("data");
        LOG.debug("Received tweet data: {}", jsonData);
        String[] params = new String[]{
                ZonedDateTime.parse(jsonData.get("created_at").toString()).withZoneSameInstant(ZoneId.of("UTC"))
                             .format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH)),
                jsonData.get("id").toString(), // Tweet ID
                jsonData.get("text").toString().replaceAll("\"","\\\\\""),
                jsonData.get("author_id").toString(), // Author ID
        };
        return formatTweetAsJsonWithParams(params);
    }

    private String formatTweetAsJsonWithParams(String[] params) {
        String tweet = tweetAsRawJson;

        for (int i = 0; i < params.length; i++) {
            tweet = tweet.replace("{" + i + "}", params[i]);
        }
        return tweet;
    }


    void setupRulesHigherOrder(String bearerToken, Supplier<Map<String, String>> rulesSupplier) throws IOException, URISyntaxException
    {
        Map<String, String> rules = rulesSupplier.get();
        List<String> existingRules = getRules(bearerToken);
        if (existingRules.size() > 0) {
            deleteRules(bearerToken, existingRules);
        }
        createRules(bearerToken, rules);
        LOG.info("Created rules for twitter stream {}", rules.keySet().toArray());
    }

}
