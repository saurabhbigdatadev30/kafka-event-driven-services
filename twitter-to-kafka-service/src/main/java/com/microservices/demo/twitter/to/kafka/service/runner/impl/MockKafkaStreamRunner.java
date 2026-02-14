package com.microservices.demo.twitter.to.kafka.service.runner.impl;

import com.microservices.demo.config.TwitterToKafkaServiceConfigData;
import com.microservices.demo.twitter.to.kafka.service.exception.TwitterToKafkaServiceException;
import com.microservices.demo.twitter.to.kafka.service.listener.TwitterKafkaStatusListener;
import com.microservices.demo.twitter.to.kafka.service.runner.StreamRunner;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Component
@Slf4j
/*
   @ConditionalOnProperty(name = "twitter-to-kafka-service.enable-mock-tweets", havingValue = "true")
   MockKafkaStreamRunner is only enabled when enable-mock-tweets is true and enable-v2-tweets is false.
   This is to ensure that only one StreamRunner implementation is active at a time.
 */
 @ConditionalOnExpression("not ${twitter-to-kafka-service.enable-v2-tweets} &&  ${twitter-to-kafka-service.enable-mock-tweets}")

public class MockKafkaStreamRunner implements StreamRunner {

    private final TwitterToKafkaServiceConfigData twitterToKafkaServiceConfigData;

    private final TwitterKafkaStatusListener twitterKafkaStatusListener;

    private static final Random RANDOM = new Random();

    private static final String[] WORDS = new String[]{
            "Lorem",
            "ipsum",
            "dolor",
            "sit",
            "amet",
            "consectetuer",
            "adipiscing",
            "elit",
            "Maecenas",
            "porttitor",
            "congue",
            "massa",
            "Fusce",
            "posuere",
            "magna",
            "sed",
            "pulvinar",
            "ultricies",
            "purus",
            "lectus",
            "malesuada",
            "libero"
    };

    // Structure of the tweet as raw JSON , tweets will contain the following fields: [created_at, tweet.id, text, user.id]
    private static final String tweetAsRawJson = "{" +
            "\"created_at\":\"{0}\"," +
            "\"id\":\"{1}\"," +
            "\"text\":\"{2}\"," +
            "\"user\":{\"id\":\"{3}\"}" +
            "}";

   /*
    Twitter's date format pattern

    Reference: https://developer.twitter.com/en/docs/twitter-api/v1/data-dictionary/object

         EEE -> Day of the week in short form (e.g., Mon, Tue)
         MMM -> Month in short form (e.g., Jan, Feb ,...)
         dd  -> Day of the month (01 to 31)
         HH  -> Hour in 24-hour format (00 to 23)
         mm  -> Minutes (00 to 59)
         ss  -> Seconds (00 to 59)
         zzz -> Time zone (e.g., GMT, PST)
         yyyy -> Year in four digits (e.g., 2023)
         */
    private static final String TWITTER_STATUS_DATE_FORMAT = "EEE MMM dd HH:mm:ss zzz yyyy";

    /**
    + Constructor-based Dependency Injection
          Manually writing the constructor is better than using the  @AllArgsConstructor annotation.
          The lombok annotation @AllArgsConstructor will include all fields (including constants and parameters we don't want injected).
          in the generated constructor. By writing the constructor manually, we control which fields are injected and which
          are initialized internally . This ensures only the required dependencies are passed in.
     */
    public MockKafkaStreamRunner(TwitterToKafkaServiceConfigData configData,
                                 TwitterKafkaStatusListener statusListener) {
        this.twitterToKafkaServiceConfigData = configData;
        this.twitterKafkaStatusListener = statusListener;
    }

    @Override
    public void start() throws TwitterException {
        final String[] keywords = twitterToKafkaServiceConfigData.getTwitterKeywords().toArray(new String[0]);
        final int minTweetLength = twitterToKafkaServiceConfigData.getMockMinTweetLength();
        final int maxTweetLength = twitterToKafkaServiceConfigData.getMockMaxTweetLength();
        long sleepTimeMs = twitterToKafkaServiceConfigData.getMockSleepMs();
        log.info("Starting mock filtering twitter streams for keywords {}", Arrays.toString(keywords));
        // We want to simulate the twitter stream with random tweets, so we call the simulateTwitterStream method
        simulateTwitterStream(keywords, minTweetLength, maxTweetLength, sleepTimeMs);
    }


/*
  simulateTwitterStream() method generates tweets with random content in infinite loop ,with random length between min and max tweet length.
  We don't want to block the main thread, so we use a ExecutorService --> SingleThreadExecutor to simulate the twitter stream.
  submit() method is used to run the tweet simulation in a separate thread implementing Functional Interface Runnable.
 */

    private void simulateTwitterStream(String[] keywords, int minTweetLength, int maxTweetLength, long sleepTimeMs) {
        // () -> { code to run }  ->  Lambda expression implementing Runnable interface
        Executors.newSingleThreadExecutor().submit(() ->
        {
            // Lambda expression to run the tweet simulation in a separate thread implementing Runnable
            try {
                log.info("Thread {} started for simulating twitter stream", Thread.currentThread().getName());
                while (true) {
                    // Generate a random tweet with the given keywords and length
                    // The tweet will be formatted as a JSON string with the (current date, tweet_id, tweet content ,user_id)
                    String formattedTweetAsRawJson = getFormattedTweet(keywords, minTweetLength, maxTweetLength);
                    // Create a Twitter Status object from the formatted tweet JSON
                    Status status = TwitterObjectFactory.createStatus(formattedTweetAsRawJson);
                    log.info("Simulated tweet generated: {}", status.getText());
                    // Notify the listener about the new status
                    // This will trigger the Kafka producer to send the tweet to the Kafka topic
                    twitterKafkaStatusListener.onStatus(status);
                    // Add delay to simulate the time taken to create a tweet
                    // This is to avoid flooding the Kafka topic with too many tweets in a short time.
                    sleep(sleepTimeMs);
                }
            } catch (TwitterException e) {
                log.error("Error creating twitter status!", e.getErrorMessage());
                throw new TwitterToKafkaServiceException("Error creating twitter status!!" , e);
            }
        });
    }

    private void sleep(long sleepTimeMs) {
        try {
            Thread.sleep(sleepTimeMs);
        } catch (InterruptedException e) {
            throw new TwitterToKafkaServiceException("Error while sleeping for waiting new status to create!!");
        }
    }

    // Create tweet , contains the following fields: [created_at, tweet.id, text, user.id]
    private String getFormattedTweet(String[] keywords, int minTweetLength, int maxTweetLength) {
        // Generate a random tweet with the given keywords and length
        // The tweet will be formatted as a JSON string with the (current date, tweet_id, tweet content ,user_id)


    /*
        ----------------------------------------------------------------------------------------------------------------------
          ## Understanding the difference between ZonedDateTime vs Instant
              ZonedDateTime represents a date and time with a timezone (e.g., 2024-06-10T10:15:30+01:00[Europe/London]).
              Instant represents a moment on the UTC timeline (e.g., 2024-06-10T09:15:30Z), without any timezone information.
              Use ZonedDateTime when you need to work with local times and timezones.
              Use Instant for timestamps or when you only care about the absolute point in time (UTC).
         ----------------------------------------------------------------------------------------------------------------------
          ZonedDateTime.now().format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH))

          Note: ZonedDateTime.now().format(...) uses the system's default timezone, so the date is not guaranteed to be in UTC.
                So, we will convert the current ZonedDateTime to UTC timezone and formats it to match Twitter's date format
        */

        String[] params = new String[]{
                ZonedDateTime.now()
                             .withZoneSameInstant(ZoneId.of("UTC"))
                             .format(DateTimeFormatter.ofPattern(TWITTER_STATUS_DATE_FORMAT, Locale.ENGLISH)),
                // tweetID is a random long value
                String.valueOf(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE)),
                // tweet content is generated with random words and keywords
                getRandomTweetContent(keywords, minTweetLength, maxTweetLength),
                // userID is a random long value
                String.valueOf(ThreadLocalRandom.current().nextLong(Long.MAX_VALUE))
        };
        return formatTweetAsJsonWithParams(params);
    }

    // This method formats the tweet as a JSON string by replacing the placeholders with the actual values
    private String formatTweetAsJsonWithParams(String[] params) {
        String tweet = tweetAsRawJson;

        for (int i = 0; i < params.length; i++) {
            tweet = tweet.replace("{" + i + "}", params[i]);
        }
        return tweet;
    }


    private String getRandomTweetContent(String[] keywords, int minTweetLength, int maxTweetLength) {
        int tweetLength = RANDOM.nextInt(maxTweetLength - minTweetLength + 1) + minTweetLength;
        StringBuilder tweet = new StringBuilder();

        // Add random words
        for (int i = 0; i < tweetLength; i++) {
            tweet.append(WORDS[RANDOM.nextInt(WORDS.length)]).append(" ");
        }

        // Insert a random keyword at the end
        tweet.append(keywords[RANDOM.nextInt(keywords.length)]);

        return tweet.toString().trim();
    }


    // The method is complex and we are not using this logic
    private String getRandomTweetContentLegacy(String[] keywords, int minTweetLength, int maxTweetLength) {
        // Generate a random tweet length between minTweetLength and maxTweetLength
        int tweetLength = RANDOM.nextInt(maxTweetLength - minTweetLength + 1) + minTweetLength;
        return constructRandomTweet(keywords, tweetLength);
    }

    private String constructRandomTweet(String[] keywords , int tweetLength) {
        StringBuilder tweet = new StringBuilder();
        for (int i = 0; i < tweetLength; i++) {
            tweet.append(WORDS[RANDOM.nextInt(WORDS.length)]).append(" ");
            if (i == tweetLength / 2) {
                tweet.append(keywords[RANDOM.nextInt(keywords.length)]).append(" ");
            }
        }
        return tweet.toString().trim();
    }

}
