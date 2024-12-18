package com.microservices.demo.kafka.admin.client;

import com.microservices.demo.config.KafkaConfigData;
import com.microservices.demo.config.RetryConfigData;
import com.microservices.demo.kafka.admin.exception.KafkaClientException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicListing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.RetryContext;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/*
KafkaAdminClient is utility class defined in kafka-admin module which is responsible to create Topic .

The kafka-admin module [KafkaAdminClient.java]   depends  on the other module classes

(1) KafkaConfigData.java - @Configuration class defined in the app-config-data module which reads the Kafka configurations [prefix = "kafka-config"]  from the
                           config-client-analytics.yml file

(2) RetryConfigData.java - @Configuration class defined in the app-config-data module which reads the Retry  configurations [prefix = "retry-config"] from the
                           config-client-kafka_to_elastic.yml file

(3) AdminClient -          We instantiate this Bean in Configuration class KafkaAdminConfig

(4) RetryTemplate -        We instantiate this Bean in Configuration class RetryConfig in common-config module

 */
@Component
public class KafkaAdminClient {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaAdminClient.class);

    // The KafkaConfigData is a Configuration class that includes from  Module =  app-config-data
    private final KafkaConfigData kafkaConfigData;

    // The RetryConfigData is a Configuration class that includes from  Module =  app-config-data module
    private final RetryConfigData retryConfigData;

    /*
      The  @Configuration class = KafkaAdminConfig , creates  @Bean --> public AdminClient adminClient().
      This  AdminClient will then be injected using the CI
    */
    private final AdminClient adminClient;


    // RetryTemplate is configured in common-config module
    private final RetryTemplate retryTemplate;


    /*
     Create a  @Configuration class =  WebClientConfig which is responsible to create @Bean = WebClient .

       @Bean
       WebClient webClient() {
        return WebClient.builder().build();
       }

        We then autowire WebClientConfig  in KafkaAdminClient
     */

    private final WebClient webClient;

// Constructor injection for setting all the above properties
    public KafkaAdminClient(KafkaConfigData config,
                            RetryConfigData retryConfigData,
                            AdminClient client,
                            RetryTemplate template,
                            WebClient webClient) {
        this.kafkaConfigData = config;
        this.retryConfigData = retryConfigData;
        this.adminClient = client;
        this.retryTemplate = template;
        this.webClient = webClient;
    }

    /*
       (1) We configure the @Bean = RetryTemplate in the common-config module
       (2) invoke the execute method of RetryTemplate .
       (3) Pass the doCreateTopics in the argument of the RetryTemplate.

     */
    public void createTopics() {
        CreateTopicsResult createTopicsResult;
        try {
            //[1]  Use RetryTemplate to create Topic [doCreateTopics]
            createTopicsResult = retryTemplate.execute(this::doCreateTopics);
            LOG.info("Create topic result {}", createTopicsResult.values().values());
        } catch (Throwable t) {
            throw new KafkaClientException("Reached max number of retry for creating kafka topic(s)!", t);
        }
        checkTopicsCreated();
    }

    // Check if the Topic is created with retry option
    public void checkTopicsCreated() {
        // Fetch the Topic created . We rely on the adminClient.listTopics().listings() , to check the topic created
        Collection<TopicListing> topics = getTopics();
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        int multiplier = retryConfigData.getMultiplier().intValue();
        Long sleepTimeMs = retryConfigData.getSleepTimeMs();
        for (String topic : kafkaConfigData.getTopicNamesToCreate()) {
            while (!isTopicCreated(topics, topic)) {
                LOG.info("topic is not created yet .. maxRetry so far {} ...  " , maxRetry);
                checkMaxRetry(retryCount++, maxRetry);
                sleep(sleepTimeMs);
                sleepTimeMs *= multiplier;
                topics = getTopics();
            }
        }
    }

    // Check if the SchemaRegistry is up with RetryOption
    public void checkSchemaRegistry() {
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        int multiplier = retryConfigData.getMultiplier().intValue();
        Long sleepTimeMs = retryConfigData.getSleepTimeMs();
        while (!getSchemaRegistryStatus().is2xxSuccessful()) {
            // Check if the retry is exhausted
            checkMaxRetry(retryCount++, maxRetry);
            sleep(sleepTimeMs);
            sleepTimeMs *= multiplier;
        }
    }

    private HttpStatusCode getSchemaRegistryStatus() {
        try {
            // Make a REST call ,
            return webClient
                    .method(HttpMethod.GET)
                    .uri(kafkaConfigData.getSchemaRegistryUrl())
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            return Mono.just(response.statusCode());
                        } else {
                            return Mono.just(HttpStatus.SERVICE_UNAVAILABLE);
                        }
                    }).block();
        } catch (Exception e) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
    }


    private void sleep(Long sleepTimeMs) {
        try {
            Thread.sleep(sleepTimeMs);
        } catch (InterruptedException e) {
            throw new KafkaClientException("Error while sleeping for waiting new created topics!!");
        }
    }

    private void checkMaxRetry(int retry, Integer maxRetry) {
        if (retry > maxRetry) {
            throw new KafkaClientException("Reached max number of retry for reading kafka topic(s)!");
        }
    }

    private boolean isTopicCreated(Collection<TopicListing> topics, String topicName) {
        if (topics == null) {
            return false;
        }
       // returns true if the topicName is found
        return topics.stream().anyMatch(topic -> topic.name().equals(topicName));
    }
    private CreateTopicsResult doCreateTopics(RetryContext retryContext) {
        List<String> topicNames = kafkaConfigData.getTopicNamesToCreate();

        topicNames.stream().forEach(topicName -> {
            LOG.info("Topic {} to be created is ", topicName);
        });

        LOG.info("Creating {} Number of topics(s), The current Retry attempt {}", topicNames.size(), retryContext.getRetryCount());
        /*
          (1) Fetch the Topics [kafkaConfigData] to be created from the configuration file using kafkaConfigData. List<String> topicNames .
          (2) From List<String> topicNames , using the streams API , iterate over each topic &  Construct  the List<NewTopic> from the topics to be created
              List<String> topicNames to List<NewTopic> kafkaTopics
          (3) Pass the List of NewTopic, to AdminClient.createTopics()
          (4) The AdminClient is responsible to create Kafka Topic
          (5) CreateTopicsResult stores the result of creation
         */
        List<NewTopic> kafkaTopics = topicNames.stream()
                                                .map(topic -> new NewTopic(topic.trim(), kafkaConfigData.getNumOfPartitions(), kafkaConfigData.getReplicationFactor()
                                                 )).collect(Collectors.toList());

        // Create Topic (List<NewTopic>) using KafkaAdmin
         CreateTopicsResult createTopicResult  = adminClient.createTopics(kafkaTopics);
         return createTopicResult;
    }

    /*
      Fetch the topics created

     */
    private Collection<TopicListing> getTopics() {
        Collection<TopicListing> topics;
        try {
            // Check the topics created
            topics = retryTemplate.execute(this::doGetTopics);
        } catch (Throwable t) {
            throw new KafkaClientException("Reached max number of retry for reading kafka topic(s)!", t);
        }
        return topics;
    }

    private Collection<TopicListing> doGetTopics(RetryContext retryContext)
            throws ExecutionException, InterruptedException {
        LOG.info("Reading kafka topic {}, The number of attempt {}",
                   kafkaConfigData.getTopicNamesToCreate().toArray(), retryContext.getRetryCount());

        // Fetch the topics created from the adminClient ..  Collection<TopicListing>
        Collection<TopicListing> topics = adminClient.listTopics().listings().get();
        if (topics != null) {
            topics.forEach(topic -> {
                LOG.debug("Topic with name {} is created", topic.name());
            });
        }
        return topics;
    }

}
