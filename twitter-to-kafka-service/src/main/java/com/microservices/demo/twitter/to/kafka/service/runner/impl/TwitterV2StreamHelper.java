package com.microservices.demo.twitter.to.kafka.service.runner.impl;

import com.microservices.demo.config.TwitterToKafkaServiceConfigData;
import com.microservices.demo.twitter.to.kafka.service.exception.TwitterToKafkaServiceException;
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
import java.util.*;
import java.util.function.Supplier;

@Component

/**
 This class is responsible for connecting to the Twitter V2 API and streaming tweets that match the rules we set up.
  https://github.com/xdevplatform/samples/blob/main/java/streams/FilteredStreamDemo.java

 The StreamRunner Interface has 3 different implementations . Based on @ConditionalOnExpression annotation,
 the respective implementation & the respective Helper class will be loaded at runtime based on the configuration properties
 defined iconfig-client-twitter_to_kafka.yml file.

twitter-to-kafka-service:
 enable-v2-tweets: true
 enable-mock-tweets: false

 So based on the above properties, the V2 implementation & the respective helper class  will be loaded at runtime.
 */

@ConditionalOnExpression("${twitter-to-kafka-service.enable-v2-tweets} && not ${twitter-to-kafka-service.enable-mock-tweets}")
//@ConditionalOnProperty(name = "twitter-to-kafka-service.enable-v2-tweets", havingValue = "true", matchIfMissing = true)
public class TwitterV2StreamHelper
{
    private static final Logger LOG = LoggerFactory.getLogger(TwitterV2StreamHelper.class);

    // dependency : app-config-data module
    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;

    private final TwitterKafkaStatusListener twitterKafkaStatusListener;

   // @Bean [CloseableHttpClient] is created in @Confifuration class TwitterHttpClientConfig
    private final CloseableHttpClient twitterHttpClient;

    // directly from the JSON string to a JsonNode tree structure
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
       1. This is the JSON representation of tweet, compatible with Twitter4J's Status object, this will match the
          fields required by Twitter4J's Status object.
       2. The placeholders {0}, {1}, {2}, and {3} will be replaced with actual tweet data such as
            [created_at, id , text, and user id] respectively.
       3. This format is compatible with Twitter4J's Status object.
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
                                 TwitterKafkaStatusListener twitterKafkaStatusListener,
                                 CloseableHttpClient twitterHttpClient)
    {
        this.twitterToKafkaServiceConfigData = twitterToKafkaServiceConfigData;
        this.twitterKafkaStatusListener = twitterKafkaStatusListener;
        this.twitterHttpClient = twitterHttpClient;
    }

    void connectStream(String bearerToken) throws IOException, URISyntaxException, JSONException
      {
        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2BaseUrl());
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpGet.setHeader("content-type", "application/json");
        /**
       - Send HttpGet request to the Twitter V2 API endpoint to establish a connection & start streaming tweet.
       - The response will be a stream of tweets that match the rules you set up.
       - Each line in the response represents a tweet in JSON format.
       - We will read the stream of tweets from the Twitter V2 API using a BufferedReader, which allows us to read the
         response line by line.
       */
        HttpResponse response = twitterHttpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();

        if (null != entity)
        {
          /**
             - entity.getContent() returns the raw **InputStream** of the HTTP response body — which is a **continuous,
               live stream of tweet data** coming from Twitter's V2 API.
             - We use BufferedReader to read the stream of tweets from the Twitter V2 API from (HttpEntity.getEntity()).
          */
            BufferedReader reader = new BufferedReader(new InputStreamReader(entity.getContent(), StandardCharsets.UTF_8));
            String line ;
          // Continues Streaming tweets until the stream is closed or an error occurs. Each line represents a tweet in JSON format.
            while ((line = reader.readLine()) != null)
            {
                if (line != null && !line.isEmpty()) {
                    try {
                        String tweet = getFormattedTweet(line);                    // step 1
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



    /**
      This is a dummy method for the Understanding of  IfPresentOrElse() signature
       public void ifPresentOrElse(
       @NotNull java.util.function.Consumer<? super @NotNullT> action,
       @NotNull Runnable emptyAction
     )
     */

    private void createRulesUsingOptionalDemo(String bearerToken , Map<String, String> rulesToBeCreated)
                       throws URISyntaxException, IOException
               {
                   fetchRulesJsonHandleNull(bearerToken).ifPresentOrElse(
                           rulesList -> {
                               try {
                                   deleteRules(bearerToken, rulesList);
                               } catch (URISyntaxException e) {
                                   throw new RuntimeException(e);
                               } catch (IOException e) {
                                   throw new RuntimeException(e);
                               }
                           }
                           ,
                           () -> LOG.error("No Rules found to delete")
                   );
                   createRules(bearerToken, rulesToBeCreated);

               }



    private void createRules(String bearerToken, Map<String, String> rules)
            throws URISyntaxException, IOException
    {

  // [1] Build the HttpClient Object using Builder pattern. The HttpClient sends the HttpPost Request to the Twitter V2 API
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

    /**
        Fetch the exisitng rules from twitter V2 API.
          - The return type may be empty , So we use Optional<List<String>>.
          - If the rules endpoint returns empty response or if the "data" key is missing in the response,
            we use ***Optional*** to handle the case where there are   no rules returned from the API.
     */

    public Optional<List<String>> fetchRulesJsonHandleNull(String bearerToken)
            throws URISyntaxException, IOException {
        List<String> rulesLst = new ArrayList<>();
        // HttpGet request to the Twitter V2 API Rules endpoint to fetch the existing rules.
        URIBuilder uriBuilder = new URIBuilder(twitterToKafkaServiceConfigData.getTwitterV2RulesBaseUrl()) ;
        HttpGet httpGet = new HttpGet(uriBuilder.build());
        httpGet.setHeader("Authorization", String.format("Bearer %s", bearerToken));
        httpGet.setHeader("content-type", "application/json");

        /* Rules V2 Endpoint returns the existing rules in the response body [HttpResponse] in JSON format.*/
        HttpResponse httpResponse = twitterHttpClient.execute(httpGet);
        HttpEntity httpEntity = httpResponse.getEntity();

        /**
           1. Read the raw InputStream from HttpEntity and converts it to a String
           2. We parse the String into a JSONObject & extract key = data . This will return JSONArray.
           3. Parse the JSON to extract the rules from the "data" array in the JSON response.
           4. Each element in the "data" array represents a rule, and we extract the "id" field from each rule and add
              it to the rulesLst list.
         */
        JSONObject rulesJson = new JSONObject(EntityUtils.toString(httpEntity,"UTF-8"));
        if(rulesJson.has("data"))
         {
            JSONArray array = (JSONArray) rulesJson.get("data");
            for(int i =0; i <array.length(); i++){
                JSONObject rulejsonElement = (JSONObject)array.get(i);
                //rulesLst.add(jsonElement.getString("id"));
                rulesLst.add(rulejsonElement.get("id").toString());
            }
        };
        return Optional.of(rulesLst);
    }

    /**
       Instead of JSONObject, we use JSONNode tree structure to parse the JSON response from the rules endpoint.
      This allows us to navigate the JSON response safely without worrying about missing keys or type mismatches.
    */
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

     /**
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

// Called from the endless while() loop context
   private String getFormattedTweet(String tweetData)
   {
       // [1] Null / Empty check on raw input
       if (tweetData == null || tweetData.isEmpty()) {
           throw new TwitterToKafkaServiceException("Cannot format tweet — raw tweet data is null or empty");
       }

       // 👉👉 [2] Build JSONObject from the raw tweets [tweetData] string.
       JSONObject tweetJsonObject = new JSONObject(tweetData);

       // [3] Check — "data" key missing → throw domain exception
       if (!tweetJsonObject.has("data")) {
           throw new TwitterToKafkaServiceException(String.format("Tweet %s is not valid", tweetData));
       }

       // TODO - Add validation logic for the tweet to prevent the downstream services from corrupt data

       // [4] Safe extraction — only reached if "data" key exists
       JSONObject jsonData = (JSONObject) tweetJsonObject.get("data");

       // Fetch the JSON fields from the tweet response , parsing the JSONObject
       String[] tweetParameters =  extractJsonData(jsonData);
       return formatTweetAsJsonWithParams(tweetParameters);
   }

    /*
    String jsonData :
     {
         "created_at": "Mon Apr 08 12:34:56 UTC 2024",
         "id": "1234567890",  // Tweet_ID
         "text": "This is a sample tweet",
         "author": {
             "id": "9876543210"  // Author_ID
       }
     */

    // Extract / parse the JSON Fields from the JSONObject , key = "data"
  private String[] extractJsonData(JSONObject jsonData){
      try {
          String[] tweetParams = new String[] {
         /* 👉👉 Field -1 "created_at"extracted from the JSON response and formatting it to match Twitter4J's
                           expected date format*/
          ZonedDateTime.parse(jsonData.get("created_at").toString())
                  .withZoneSameInstant(ZoneId.of("UTC"))
                  .format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH)),

           jsonData.get("id").toString(),
           jsonData.get("text").toString().replaceAll("\"", "\\\\\""),

         /**  This is a 👉 nested object , we need to navigate to the "author" object using jsonData.getJSONObject("author")  ,
              then extract the "id" field and extract the "id" field from it.
          */
           jsonData.getJSONObject("author").get("id").toString()
          };
          return tweetParams;
      } catch (JSONException e) {
          throw new TwitterToKafkaServiceException(String.format("Tweet %s is not valid", jsonData));
      }
  }



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
         1. The method setupRulesModified1() is HOF , since it takes @Functional interface Supplier as input argument.
         2. This follows Open and Closed Principle, since we can pass different implementations of the
            supplier interface.
     */

    public void setupRulesModified1(String bearerToken , Supplier<Map<String,String>>getRulesMap)
            throws URISyntaxException , IOException
    {
     fetchRulesJsonHandleNull(bearerToken).ifPresentOrElse(
             rulesLst -> {
                 try {
                     deleteRules(bearerToken, rulesLst);
                 } catch (URISyntaxException  | IOException e) {
                     throw new TwitterToKafkaServiceException("Failed to delete rules", e);
                 }
             }
             ,
             () -> LOG.error("No Rules found to delete")
     );
     createRules(bearerToken, getRulesMap.get());
}

}
