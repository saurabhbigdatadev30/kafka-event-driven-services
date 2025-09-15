package com.microservices.demo.twitter.to.kafka.service.runner.impl;

import com.microservices.demo.config.TwitterToKafkaServiceConfigData;
import com.microservices.demo.twitter.to.kafka.service.listener.TwitterKafkaStatusListener;
import com.microservices.demo.twitter.to.kafka.service.response.TwitterRulesResponse;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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

@Component

/*
https://github.com/xdevplatform/Twitter-API-v2-sample-code/blob/main/Filtered-Stream/FilteredStreamDemo.java

StreamRunner Interface has 3 different implementations . We load the V2 Implementation .
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
    // dependency : app-config-data module
    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;
    // dependency : twitter-to-kafka-service module
    private final TwitterKafkaStatusListener twitterKafkaStatusListener;

    // directly from the JSON string to a JsonNode tree structure
    private final ObjectMapper objectMapper = new ObjectMapper();

    /*
      This is the JSON representation of tweet, compatible with Twitter4J's Status object, this will match the fields required by Twitter4J's Status object.
      The placeholders {0}, {1}, {2}, and {3} will be replaced with actual tweet data such as [created_at, id, text, and user id] respectively.
      This format is compatible with Twitter4J's Status object.
     */
    private static final String tweetAsRawJson = "{" +
            "\"created_at\":\"{0}\"," +
            "\"id\":\"{1}\"," +
            "\"text\":\"{2}\"," +
            "\"user\":{\"id\":\"{3}\"}" +
            "}";

    // We will format the created_at field to match Twitter4J's expected date format [day of week, month, day, time, timezone, year]
    private static final String TWITTER_STATUS_DATE_FORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";

    public TwitterV2StreamHelper(TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData,
                                 TwitterKafkaStatusListener twitterKafkaStatusListener) {
        this.twitterToKafkaServiceConfigData = twitterToKafkaServiceConfigData;
        this.twitterKafkaStatusListener = twitterKafkaStatusListener;
    }


 /*
   # Steps [Summary] : We connect to Twitter V2 API endpoint & read the stream of tweets based on the rules we have set continuously

          [1] Get the HttpResponse from Twitter V2 API endpoint.
                 HttpResponse response = httpClient.execute(httpGet);
                 HttpEntity httpEntity = response.getEntity();

          [2] Store the stream of tweets from the HttpEntity using BufferedReader
                  BufferedReader reader  = new BufferedReader(new InputStreamReader(httpEntity.getContent());

          [3] We read the stream of tweets using an endless while loop , since this is a streaming API the connection will be kept alive until we
              close it or Twitter disconnects it.

                    while(reader.readLine()!=null)
                    {
                           String tweetData = reader.readLine();

           [4]  We parse the tweetData JSON & format it to match Twitter4J's Status object
                           String tweet = getFormattedTweetJSONParser(tweetData);
                           status = TwitterObjectFactory.createStatus(tweet);
                           twitterKafkaStatusListener.onStatus(status);
                     }
   ------------------------------------------------------------------------------------------------------------------------------------------------------------
  */

    void connectStream(String bearerToken) throws IOException, URISyntaxException, TwitterException, JSONException {
        HttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                .build();

        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2BaseUrl());
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        HttpResponse response = httpClient.execute(httpGet);
        LOG.info("Response Status: {}", response.getStatusLine());
        HttpEntity entity = response.getEntity();
        if (null != entity) {
            // Read the stream of tweets from the HttpEntity using BufferedReader
            BufferedReader reader = new BufferedReader(new InputStreamReader((entity.getContent())));
            String tweetData = reader.readLine();
            LOG.info("Twitter stream has started streaming tweets");
            // This loop never ends until we close the connection from our side or Twitter disconnects the connection.
            while (tweetData != null) {
                tweetData = reader.readLine();
                if (!tweetData.isEmpty()) {
                    LOG.debug("Received tweet data: {}", tweetData);
                    //  String tweet = getFormattedTweet(tweetData);
                    // Parsing the tweetData JSON & format it to match Twitter4J's Status object
                    String tweet = getFormattedTweetJSONParser(tweetData);
                    Status status = null;

                    // [7]  Create the status from the tweet
                    try {
                        status = TwitterObjectFactory.createStatus(tweet);
                    } catch (TwitterException e) {
                        LOG.error("Could not create status for text: {}", tweet, e);
                    }
                    if (status != null) {
                        // [8] This will publish the tweet to the Kafka Topic
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
        // List<String> existingRules = getRules(bearerToken);
        List<String> existingRules =  getRulesJSON(bearerToken);
        if (existingRules.size() > 0) {
            deleteRules(bearerToken, existingRules);
        }
        // Create rules by passing the rules from the config file to the rules endpoint
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
        // [1] Build the HttpClient Object using Builder pattern. The HttpClient will be used to send the HttpPost request to the Twitter V2 API
        HttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                .build();

        // [2] Build URIBuilder Object, reading twitter-v2-rules-base-url url path from configuration.
        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl());

        // [3] Build a HttpPost request [HttpPost] Object , passing the uriBuilder. Set Bearer Token in the Header[Authorization] of HttpPost
        HttpPost httpPost = new HttpPost(uriBuilder.build());
        // Set the Authorization header with Bearer Token for oAuth
        httpPost.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpPost.setHeader("content-type", "application/json");

        // [4] Set the rules in the HttpPost body using the setEntity() method of HttpPost
        StringEntity body = new StringEntity(getFormattedString("{\"add\": [%s]}", rules));
        // [5] Configuring the post request body, we pass the body to the HttpPost object which contains the rules from the config file
        httpPost.setEntity(body);
        // [6] Using the HttpClient , send the HttpPost request to the Twitter V2 API Rules endpoint
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
       [6] Create [JSONObject] from the HttpEntity. Reads the response body as a UTF-8 string , to parse the JSON string and extract the
           rules from the JSON structure.
       [7] If the JSONObject has "data" key, then extract the rules from the JSON object.
       [8] The "data" key contains an array of rules, we will extract the "id".
       [9] Return the list of rule ids.
       This method retrieves the existing rules from the Twitter V2 API Rules endpoint.
     */
    private List<String> getRules(String bearerToken) throws URISyntaxException, IOException {
        List<String> rules = new ArrayList<>();

        // [1] Build HttpClient Object . This HttpClient will be used to send HttpGet request to the Twitter V2 API
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

        // [5] Get the response entity from the HttpResponse . We get the rules of Twitter -V2 API in the response entity
        HttpEntity entity = response.getEntity();
        if (null != entity) {
            // [6] Reads the HTTP ResponseBody from the Response HttpEntity as a UTF-8 string
            // [7] Build JSONObject from  HttpEntity to parse the JSON string and extract the rules ,from the JSON structure
            JSONObject json = new JSONObject(EntityUtils.toString(entity, "UTF-8"));
            // We will then check if the JSON object has "data" key
            if (json.length() > 1 && json.has("data")) {
                // [8] If the JSON object has "data" key, then extract the rules from the JSON object
                JSONArray array = (JSONArray) json.get("data");
                for (int i = 0; i < array.length(); i++) {
                    // [9]  The "data" key contains an array of rules, we will extract the "id
                    JSONObject jsonObject = (JSONObject) array.get(i);
                    // [10] We fetch the "id" from each rule and add it to the list of rules
                    rules.add(jsonObject.getString("id"));
                }
            }
        }
        return rules;
    }

    // Make a GET request to the Twitter V2 API Rules endpoint to retrieve existing rules & store the rule IDs in a List<String>
    private List<String> getRulesJSON(String bearerToken) throws URISyntaxException, IOException {
        List<String> rules = new ArrayList<>();

        // [1] Build HttpClient Object . This HttpClient will be used to send HttpGet request to the Twitter V2 API
        HttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setCookieSpec(CookieSpecs.STANDARD).build())
                .build();

        //[2]  Build URIBuilder Object, reading the twitter-v2-rules-base-url from configuration.
        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl());
        // Create a HttpGet request [HttpGet] Object from URIBuilder object. Set Bearer Token in the Header[Authorization] of HttpGet
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpGet.setHeader("content-type", "application/json");

        // [4] Using the HttpClient, send the HttpGet request to the Twitter V2 API Rules endpoint
        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
           /*
            The ObjectMapper is part of Jackson library that reads the HTTP ResponseBody from the Response HttpEntity as a UTF-8 string
            and maps it to the TwitterRulesResponse class, which contains a list of TwitterRule objects
            representing the existing rules.
            */
            TwitterRulesResponse rulesResponse = objectMapper.readValue(EntityUtils.toString(entity, "UTF-8"), TwitterRulesResponse.class);
            // From the rulesResponse object, extract the rule IDs and add them to the list.
            if (rulesResponse.getData() != null) {
                for (TwitterRule rule : rulesResponse.getData()) {
                    rules.add(rule.getId());
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
---------------------------------------------------------------------------------------------------------------------------------------------------------
      created_at : Formatted as EEE MMM dd HH:mm:ss zzz yyyy
                      EEE : Day of the week in short form
                      MMM : Month in short form
                      dd  : Day of the month
                      HH  : Hour in 24 hour format
                      mm  : Minutes
                      ss  : Seconds
                      zzz : Timezone
                      yyyy: Year in 4 digit format
        Formatted tweet returned from the V2 API  will be in the form of a JSON string with the following structure.
------------------------------------------------------------------------------------------------------------------------------------------------------
               String tweetData :
                                   {
                                       "created_at": "Mon Apr 08 12:34:56 UTC 2024",
                                       "id": "1234567890",  // Tweet_ID
                                       "text": "This is a sample tweet",
                                       "author": {
                                           "id": "9876543210"  // Author_ID
                                     }
        The getFormattedTweet() method will be called for each incoming tweet within the endless while() loop.
        The loop only ends if the connection is closed or interrupted.
        The getFormattedTweet() method extracts the tweetData from the JSON data and formats it into a structure compatible with
        Twitter4J's Status object.


  # Understanding the date formatting logic in getFormattedTweet() method

  Instant is simpler and always represents a UTC timestamp, which avoids timezone issues.
  If you only need the UTC moment (not local time or timezone info), prefer Instant.
  Use ZonedDateTime if you need to handle or display timezones.

   ZonedDateTime.parse(jsonData.get(created_at).toString())       : Converts the extracted date-time in [String] format to ZonedDateTime format.
                .withZoneSameInstant(ZoneId.of("UTC"))            : Converts the time extracted [ZonedDateTime]  to UTC format.
                .format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH)) : Formats the UTC ZonedDateTime to a specific
                                                                                                   pattern defined in TWITTER_STATUS_DATE_FORMAT.

   This statement parses the "created_at" Date String from the tweet JSON, converts it to a ZonedDateTime,
   converts  it to UTC time zone, and formats it as a string using the pattern defined in TWITTER_STATUS_DATE_FORMAT
   (e.g., EEE MMM dd HH:mm:ss zzz yyyy) with English locale. This ensures the tweet's timestamp is standardized to UTC
   and formatted for Twitter4J compatibility.
-----------------------------------------------------------------------------------------------------------------------------------------------------------
     */

    private String getFormattedTweet(String tweetData) {
          /*
               Create JSONObject from tweetData to parse the JSON string and extract the tweet data fields
                   (created_at, id, text, and author_id) from the tweet's JSON structure.
         */
        JSONObject jsonData = (JSONObject) new JSONObject(tweetData).get("data");
        LOG.debug("Received tweet data: {}", jsonData); // [created_at] , [tweet_id] , [tweet_data] , [author_id]
        // Extract the tweet details from the JSON object and format them into a structure compatible with Twitter4J's Status object
        // We extract the [created_at]  , [id] (tweetID), [text] (tweetData), [author_id] from the JSON object and
        String[] params = new String[]{
                ZonedDateTime.parse(jsonData.get("created_at").toString()).withZoneSameInstant(ZoneId.of("UTC"))
                        .format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH)),
                jsonData.get("id").toString(),         // Tweet ID
                jsonData.get("text").toString().replaceAll("\"","\\\\\""), // Tweet text
                jsonData.get("author_id").toString(),  // Author ID
        };
        return formatTweetAsJsonWithParams(params);
    }

    /*
    String tweetData :
     {
         "created_at": "Mon Apr 08 12:34:56 UTC 2024",
         "id": "1234567890",  // Tweet_ID
         "text": "This is a sample tweet",
         "author": {
             "id": "9876543210"  // Author_ID
       }
     */
    private String getFormattedTweetJSONParser(String tweetData) {
        try {
            /*

              Prefer using the  JsonNode in modern Spring Boot projects. It is useful to dynamic access to any part of the JSON.

                Using Jackson's ObjectMapper to parse the tweetData JSON string.
                The readTree() method reads the JSON string and converts it into a JsonNode tree structure.
                We then navigate to the "data" node using get("data") to access the tweet details.

                Why we used JsonNode instead of mapping directly to a POJO?
                *** When the JSON structure is complex, dynamic, or not fully known at compile time.

                1. Dynamic Structure: JsonNode allows us to work with JSON data that may have a
                   dynamic or unknown structure.  We can access fields without needing a predefined class.

                2. Partial Data: If we only need to work with a subset of the JSON data, JsonNode allows us to
                     access just the parts we care about without creating a full class for the entire structure.

                3. Flexibility: JsonNode provides flexibility in handling JSON data, especially when the
                                structure can vary between different responses.
             */
           // Tweet details contains (created_at, id, text, and author_id)
            JsonNode root = objectMapper.readTree(tweetData).get("data");
            String createdAt = ZonedDateTime.parse(root.get("created_at").asText())
                               .withZoneSameInstant(ZoneId.of("UTC"))
                               .format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH));
            String id = root.get("id").asText();
            String text = root.get("text").asText().replaceAll("\"", "\\\\\"");
            String authorId = root.get("author_id").asText();
            String[] params = new String[]{createdAt, id, text, authorId};
            return formatTweetAsJsonWithParams(params);
        } catch (Exception e) {
            LOG.error("Error parsing tweet JSON", e);
            return null;
        }
    }

    private String formatTweetAsJsonWithParams(String[] params) {
        String tweet = tweetAsRawJson;

        for (int i = 0; i < params.length; i++) {
            tweet = tweet.replace("{" + i + "}", params[i]);
        }
        return tweet;
    }


    /*
      Higher Order Function : The setupRulesModified() method is a higher-order function because it takes another function (rulesSupplier)
      as an argument. There can be multiple implementations of the Supplier functional interface,
      allowing different ways to supply the rules.
     */
    void setupRulesModified(String bearerToken, Supplier<Map<String, String>> rulesSupplier)
            throws IOException, URISyntaxException {

        Map<String, String> rules = rulesSupplier.get();
        // Fetch existing rules from Twitter V2 API & delete the existing rules if any
        List<String> existingRules = getRulesJSON(bearerToken);
        if (existingRules.size() > 0) {
            deleteRules(bearerToken, existingRules);
        }
        // Create rules by passing the rules from the config file to the rules endpoint
        createRules(bearerToken, rules);
    }


}
