package com.microservices.demo.kafka.admin.client;

import com.microservices.demo.config.KafkaConfigData;
import com.microservices.demo.config.RetryConfigData;
import com.microservices.demo.kafka.admin.exception.KafkaClientException;
import lombok.extern.slf4j.Slf4j;
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

(1) KafkaConfigData.java - @Configuration class defined in the app-config-data module which reads the Kafka configurations
                            [prefix = "kafka-config"]  from the config-client-analytics.yml file

(2) RetryConfigData.java - @Configuration class defined in the app-config-data module which reads the Retry  configurations
                            [prefix = "retry-config"] from the config-client-kafka_to_elastic.yml file

(3) AdminClient -          We instantiate this Bean in Configuration class KafkaAdminConfig

(4) RetryTemplate -        We instantiate this Bean in Configuration class RetryConfig in common-config module

 */
@Component
@Slf4j
public class KafkaAdminClient {

  //  private static final Logger LOG = LoggerFactory.getLogger(KafkaAdminClient.class);

    // The KafkaConfigData is a Configuration class that includes from  Module =  app-config-data module
    private final KafkaConfigData kafkaConfigData;

    // The RetryConfigData is a Configuration class that includes from  Module =  app-config-data module
    private final RetryConfigData retryConfigData;

    /**

    */
    private final AdminClient adminClient;

   /**
    1. The @Configuration class = RetryConfig defined in common-config module & is responsible to create @Bean - RetryTemplate.
    2. The RetryTemplate is configured with the Retry policies defined in RetryConfigData, defined in app-config-data module.
    3. So , the app-config-data module [RetryConfigData] is added as dependency in the common-config module to read the
       retry configurations from the config-client-twitter-to-kafka.yml file and build the RetryTemplate Bean in the common-config module.
    4. The kafka-admin module , KafkaAdminClient class is responsible to create topics in Kafka Broker, and it uses
       the RetryTemplate Bean defined in the common-config module to retry the topic creation operation if it fails.
    5. We add dependency of module = app-config-data .. in module =  common-config module , which contains RetryConfigData . This is
       responsible to read the retry configurations.

        @Configuration
        public class RetryConfig {
        // Defined in the module = app-config-data , reads the retry configurations from the config-client-twitter-to-kafka.yml file
        private RetryConfigData retryConfigData;

          @Bean
          public RetryTemplate retryTemplate() {
            RetryTemplate retryTemplate = new RetryTemplate();
            ...  configures the RetryTemplate with the retry policy defined in RetryConfigData
            ....
            return retryTemplate;
          }
        */
         private final RetryTemplate retryTemplate;

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


    public void createTopics() {
        CreateTopicsResult createTopicsResult;
        try {
             CreateTopicsResult resultUsingLambda = retryTemplate.execute(ctx -> doCreateTopics(ctx));
            // replace the above line lambda with method reference
            createTopicsResult = retryTemplate.execute(this::doCreateTopics);
            log.info("Create topic result {}", createTopicsResult.values().values());
        } catch (Throwable t) {
            // If the topic creation fails after all retries, it throws a KafkaClientException
            log.error("Error creating topic {}", t.getMessage());
            throw new KafkaClientException("Reached max number of retry for creating kafka topic(s)!", t);
        }
        checkTopicsCreated();
    }

    // Check if the Topic is created with retry option
    public void checkTopicsCreated() {
        // Fetch the Topics created . We rely on the adminClient.listTopics().listings() , to check the topics created in the Kafka Broker
        Collection<TopicListing> kafkaTopicsInCluster = getTopics();
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        int multiplier = retryConfigData.getMultiplier().intValue();
        Long sleepTimeMs = retryConfigData.getSleepTimeMs();
        for (String topic : kafkaConfigData.getTopicNamesToCreate())
        {
            while (!isTopicCreated(kafkaTopicsInCluster, topic)) {
                log.info("topic is not created yet .. maxRetry so far {} ...  " , maxRetry);
                checkMaxRetry(retryCount++, maxRetry);
                sleep(sleepTimeMs);
                sleepTimeMs *= multiplier;
                kafkaTopicsInCluster = getTopics();
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
            // Make a REST call , to check if the Schema Registry is up
            // We use WebClient to make a REST call to the Schema Registry URL
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

    /*
      Check if the topic is created
      @param topics - Collection<TopicListing> - The topics created
      @param topicName - String - The topic name to check
      @return boolean - true if the topic is created, false otherwise
     */
    private boolean isTopicCreated(Collection<TopicListing> topics, String topicName) {
        if (topics == null) {
            return false;
        }
       // returns true if the topicName is found
      //  return topics.stream().anyMatch(topic -> topic.name().equals(topicName));
        return topics.stream().anyMatch(topic ->
        {
            boolean isCreated = topic.name().equals(topicName);
            if (isCreated) {
                log.info("Topic {} is created", topicName);
            } else {
                log.info("Topic {} is not created yet", topicName);
            }
            return isCreated;
        });
    }


    private CreateTopicsResult doCreateTopics(RetryContext retryContext) throws ExecutionException, InterruptedException {
        List<String> topicNames = kafkaConfigData.getTopicNamesToCreate();

        topicNames.stream().forEach(topicName -> {
            log.info("Topic {} to be created is ", topicName);
        });

        log.info("Creating {} Number of topics(s), The current Retry attempt {}", topicNames.size(), retryContext.getRetryCount());
        // Create a list of NewTopic objects from the topic names read from the configuration
        List<NewTopic> kafkaTopics = topicNames.stream()
                                     .map(topic ->  // Build NewTopic(topicName, numOfPartitions, replicationFactor)
                                      new NewTopic(topic.trim(), kafkaConfigData.getNumOfPartitions(), kafkaConfigData.getReplicationFactor()
                                      )).collect(Collectors.toList());
        // Create the topics using the AdminClient . The AdminClient is injected using the CI in this class
        // The createTopics method returns CreateTopicsResult
        return adminClient.createTopics(kafkaTopics);
    }

   // Fetch the topics created from the adminClient
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
        log.info("Reading kafka topic {}, The number of attempt {}",
                   kafkaConfigData.getTopicNamesToCreate().toArray(), retryContext.getRetryCount());

        // Fetch the topics created from the adminClient ..  Collection<TopicListing>
        Collection<TopicListing> topics = adminClient.listTopics().listings().get();
        if (topics != null) {
            topics.forEach(topic -> {
                log.debug("Topic with name {} is created", topic.name());
            });
        }
        return topics;
    }

}
