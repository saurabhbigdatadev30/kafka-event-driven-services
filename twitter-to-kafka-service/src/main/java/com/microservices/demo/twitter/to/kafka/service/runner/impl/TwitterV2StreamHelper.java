package com.microservices.demo.twitter.to.kafka.service.runner.impl;

import com.microservices.demo.config.TwitterToKafkaServiceConfigData;
import com.microservices.demo.twitter.to.kafka.service.config.TwitterHttpClientConfig;
import com.microservices.demo.twitter.to.kafka.service.exception.TwitterToKafkaServiceException;
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
import org.apache.http.impl.client.CloseableHttpClient;
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
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Component

/**
 This class is responsible for connecting to the Twitter V2 API and streaming tweets that match the rules we set up.

  https://github.com/xdevplatform/samples/blob/main/java/streams/FilteredStreamDemo.java

 StreamRunner Interface has 3 different implementations . Based on @ConditionalOnExpression annotation, the respective
 implementation will be loaded at runtime based on the configuration properties defined in
 config-client-twitter_to_kafka.yml file.

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
   // @Bean [CloseableHttpClient] is created in @Confifuration class TwitterHttpClientConfig
    private final CloseableHttpClient twitterHttpClient;

    // directly from the JSON string to a JsonNode tree structure
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
      This is the JSON representation of tweet, compatible with Twitter4J's Status object, this will match the fields required by
      Twitter4J's Status object.
      The placeholders {0}, {1}, {2}, and {3} will be replaced with actual tweet data such as [created_at, id , text, and user id] respectively.
      This format is compatible with Twitter4J's Status object.
     */
    private static final String tweetAsRawJson = "{" +
            "\"created_at\":\"{0}\"," +
            "\"id\":\"{1}\"," +
            "\"text\":\"{2}\"," +
            "\"user\":{\"id\":\"{3}\"}" +
            "}";

    // We will format created_at field to match expected date format [day of week, month, day, time, timezone, year]
    private static final String TWITTER_STATUS_DATE_FORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";

    public TwitterV2StreamHelper(TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData,
                                 TwitterKafkaStatusListener twitterKafkaStatusListener, CloseableHttpClient twitterHttpClient) {
        this.twitterToKafkaServiceConfigData = twitterToKafkaServiceConfigData;
        this.twitterKafkaStatusListener = twitterKafkaStatusListener;
        this.twitterHttpClient = twitterHttpClient;
    }

    void connectStream(String bearerToken) throws IOException, URISyntaxException, JSONException
      {
        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2BaseUrl());
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        HttpResponse response = twitterHttpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();

        if (null != entity) {
            /**
               1. We use BufferedReader to read the stream of tweets from the Twitter V2 API from (HttpEntity.getEntity()).
               2. Each line in the stream represents a tweet in JSON format.
               3. This will be continues stream of tweets , which we will be parsing (JSON) & will send to Kafka.
             */
            BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8));
            String line = reader.readLine();
          // Continues Streaming tweets until the stream is closed or an error occurs. Each line represents a tweet in JSON format.
            while (line != null) {
                line = reader.readLine();
                if (line != null && !line.isEmpty()) {
                    try {
                 /**
                   The getFormattedTweet(...) is invoked in the continues while loop . If :-
                   1.The getFormattedTweet(...) method throws exceptions if the  tweet data is malformed , missing
                     expected fields , parsing error.
                   2.We catch those exceptions here and log them, the record is skipped .
                   3.So that the stream continues to process subsequent tweets in getFormattedTweet(...) method .
                  */
                        String tweet = getFormattedTweet(line);                    // step 1
                        if (tweet == null) continue; //  skips step 2 and step 3
                        Status status = TwitterObjectFactory.createStatus(tweet);  // step 2
                        twitterKafkaStatusListener.onStatus(status);               // step 3
                    } catch (TwitterToKafkaServiceException e) {
                        LOG.error("Skipping tweet — malformed data: {}", e.getMessage());
                    } catch (TwitterException e) {
                        LOG.error("Skipping tweet — could not create Twitter status: {}", e.getMessage());
                    } catch (Exception e) {
                        LOG.error("Skipping tweet — unexpected error: {}", e.getMessage(), e);
                    }
                    //  All exceptions caught above — stream CONTINUES to next tweet, loop does not breaks
                }
            }
        }
    }


    /*
     * Helper method to setup rules before streaming data
     * */
    void setupRules(String bearerToken, Map<String, String> rules) throws IOException, URISyntaxException {
        // List<String> existingRules = getRules(bearerToken);
        List<String> existingRules =  getRulesDynamicJson(bearerToken);
        if (existingRules.size() > 0) {
            deleteRules(bearerToken, existingRules);
        }
        // Create rules by passing the rules from the config file to the rules endpoint
        createRules(bearerToken, rules);
        LOG.info("Created rules for twitter stream {}", rules.keySet().toArray());
    }


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
        // Set the Authorization header --> Authorization:Bearer <token>
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


    private List<String> getRules(String bearerToken) throws URISyntaxException, IOException {
        List<String> rules = new ArrayList<>();

        // [1] Build HttpClient Object . This HttpClient will be used to send HttpGet request to the Twitter V2 API
       /* HttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                .setCookieSpec(CookieSpecs.STANDARD).build())
                .build();*/

        // [2] Build URIBuilder Object, reading the twitter-v2-rules-base-url from configuration twitter-v2-rules-base-url
        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl());

        // [3] Build a HttpGet request [HttpGet] Object , passing the uriBuilder.  Set Bearer Token in the Header[Authorization] of HttpGet
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpGet.setHeader("content-type", "application/json");

        // [4] Using the HttpClient, send the HttpGet request to the Twitter V2 API Rules endpoint
        HttpResponse response = twitterHttpClient.execute(httpGet);

        // [5] Get the response entity from the HttpResponse . We get the rules of Twitter -V2 API in the response entity
        HttpEntity entity = response.getEntity();
        if (null != entity) {
            // [6] Get the entity content as a String . Build JSONObject from HttpEntity
            JSONObject json = new JSONObject(EntityUtils.toString(entity, "UTF-8"));
            // We will then check if the JSON object has "data" key
            if (json.length() > 1 && json.has("data")) {
                /**
                 [8] If the JSONObject has "data" key, then extract the rules from the JSONObject in JSONArray .
                    The "data" key contains an array of rules, we will extract the "id" from each rule and add it to the list of rules.
                 */
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

    public List<String> getRules1(String bearerToken) throws URISyntaxException, IOException {
        List<String> rulesLst = new ArrayList<>();
        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl()) ; // twitter-v2-base-url=<>
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpGet.setHeader("content-type", "application/json");
        HttpResponse httpResponse = twitterHttpClient.execute(httpGet);
        HttpEntity httpEntity = httpResponse.getEntity();
        // Read the contents of an entity and return it as a String
        JSONObject json = new JSONObject(EntityUtils.toString(httpEntity),"UTF-8");
        if(json.has("data")){
            JSONArray array = (JSONArray) json.get("data");
            for(int i =0; i <array.length(); i++){
                JSONObject jsonElement = (JSONObject)array.get(i);
                //rulesLst.add(jsonElement.getString("id"));
                rulesLst.add(jsonElement.get("id").toString());
            }
        };
        return rulesLst;
    }

    public List<String> getRulesDynamicJson(String bearerToken) throws URISyntaxException, IOException {
        List<String> rulesLst = new ArrayList<>();

        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl());
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpGet.setHeader("content-type", "application/json");

        HttpResponse httpResponse = twitterHttpClient.execute(httpGet);
        HttpEntity httpEntity = httpResponse.getEntity();

        // [1] Null check on httpEntity before processing
        if (httpEntity != null) {

     /*
        [2]  Store the entire JSON in JsonNode tree structure.
             This allows us to navigate the JSON response safely without worrying about missing keys or
             type mismatches.
      */
      JsonNode root = objectMapper.readTree(EntityUtils.toString(httpEntity, StandardCharsets.UTF_8.name()));

       // [3] Extract the "data" node from the JSON tree. The "data" node contains the array of rules.
            JsonNode dataNode = root.get("data");

        // [4] Check if the JsonNode  exists AND is an array before iterating
            if (dataNode != null && dataNode.isArray()) {

                // [5] Clean for-each iteration — no index, no casting
                for (JsonNode rule : dataNode) {
                    rulesLst.add(rule.get("id").asText());   // [6] type-safe asText() ✅
                }
            }
        }
        return rulesLst;
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

// Called in endless while() loop context
   private String getFormattedTweet(String tweetData) {

       // [1] Null / Empty check on raw input
       if (tweetData == null || tweetData.isEmpty()) {
           throw new TwitterToKafkaServiceException("Cannot format tweet — raw tweet data is null or empty");
       }

       // ToDo - Add validation logic to check if the raw tweet data contains expected fields (created_at, id, text, author_id) before parsing

       // [2] Build JSONObject from the raw tweets [tweetData] string.
       JSONObject tweetJsonObject = new JSONObject(tweetData);

       // [3] Check — "data" key missing → throw domain exception
       if (!tweetJsonObject.has("data")) {
           throw new TwitterToKafkaServiceException(String.format("Tweet %s is not valid", tweetData));
       }

       // [4] Safe extraction — only reached if "data" key exists
       JSONObject jsonData = (JSONObject) tweetJsonObject.get("data");

       // Build the tweet params as array in the expected order [created_at, id, text, author_id] for formatting the tweet JSON compatible with Twitter4J's Status object
       String[] tweetParams = new String[]{
               ZonedDateTime.parse(jsonData.get("created_at").toString())
                       .withZoneSameInstant(ZoneId.of("UTC"))
                       .format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH)),
               jsonData.get("id").toString(),
               jsonData.get("text").toString().replaceAll("\"", "\\\\\""),
               jsonData.get("author_id").toString(),
       };
       return formatTweetAsJsonWithParams(tweetParams);
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
    private String getFormattedTweetJSONParser(String rawTweetV2API) {
        try {

           // Tweet details contains (created_at, id, text, and author_id)
           JsonNode rootNode = objectMapper.readTree(rawTweetV2API);

           if  (!rootNode.has("data")) {
               throw new TwitterToKafkaServiceException(String.format("Tweet %s is not valid", rawTweetV2API));
           }
            JsonNode root = objectMapper.readTree(rawTweetV2API).get("data");

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

    /**
    This method is Higher Order Function , it takes Supplier<> as input argument which is a functional interface
     that represents a supplier of results. It has a single abstract method get() which returns a result of type T.
     */
      void setupRulesModified(String bearerToken, Supplier<Map<String, String>> rulesSupplier)
            throws IOException, URISyntaxException {

        Map<String, String> rules = rulesSupplier.get();
        // Fetch existing rules from Twitter V2 API & delete the existing rules if any
        List<String> existingRules = getRulesDynamicJson(bearerToken);
        if (existingRules.size() > 0) {
            deleteRules(bearerToken, existingRules);
        }
        // Create rules by passing the rules from the config file to the rules endpoint
        createRules(bearerToken, rules);
    }


}
