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
import org.springframework.retry.RetryCallback;
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

  /**
     The annonymousImplForExecute() method demonstrates how to use the RetryTemplate's execute() method with
      an anonymous inner class implementation of the RetryCallback interface.
     This is an alternative to using a lambda expression or method reference for the RetryCallback.
   */

      public void annonymousImplForExecute(){
        retryTemplate.execute(new RetryCallback(){
            @Override
            public CreateTopicsResult doWithRetry(RetryContext retryContext) throws Exception {
                log.info("Retry attempt {} ", retryContext.getRetryCount());
                return doCreateTopics(retryContext);
            };
        });
    }

    public void createTopics() {
        CreateTopicsResult createTopicsResult;
        try {
             CreateTopicsResult resultUsingLambda = retryTemplate.execute(ctx -> doCreateTopics(ctx));
            // replace the above line lambda with method reference
            createTopicsResult = retryTemplate.execute(ctx -> doCreateTopics(ctx));
            createTopicsResult = retryTemplate.execute(this::doCreateTopics);
            log.info("Create topic result {}", createTopicsResult.values().values());
        } catch (Throwable t) {
            // If the topic creation fails after all retries, it throws a KafkaClientException
            log.error("Reached max number of retry for creating kafka topic(s)! {}", t.getMessage());
            throw new KafkaClientException("Reached max number of retry for creating kafka topic(s)!", t);
        }
        checkTopicsCreated();
    }

    // Check if the Topic is created with retry option
    public void checkTopicsCreated() {
        Collection<TopicListing> kafkaTopicsInCluster;
        // Fetch the Topics created ,  adminClient.listTopics().listings() , to check the topics created in the Kafka Broker
        kafkaTopicsInCluster = getTopics();
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        int multiplier = retryConfigData.getMultiplier().intValue();
        Long sleepTimeMs = retryConfigData.getSleepTimeMs();
        for (String topic : kafkaConfigData.getTopicNamesToCreate())
        {
            // Check if the topic is created, if not then retry until the topic is created or max retry is exhausted
            while (!isTopicCreated(kafkaTopicsInCluster, topic)) {
                log.info("topic is not created yet .. maxRetry so far {} ...  " , maxRetry);
                checkMaxRetry(retryCount++, maxRetry);
                sleep(sleepTimeMs);
                sleepTimeMs *= multiplier;
                kafkaTopicsInCluster = getTopics();
            }
        }
    }

   /**
     Check if SchemaRegistry is up with RetryOption , make a REST call to the SchemaRegistry URL defined
     in the configuration file to check if the SchemaRegistry is up. If the REST call fails,
     then retry until the max retry is exhausted.
    */
    public void checkSchemaRegistry() {
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        int multiplier = retryConfigData.getMultiplier().intValue();
        Long sleepTimeMs = retryConfigData.getSleepTimeMs();
        /**
           get the HttpStatusCode from the REST call to the SchemaRegistry URL to check if the SchemaRegistry is up.
           If the REST call fails, then retry until the max retry is exhausted.
           This is invoked before creating the topic, because the topic creation will fail if the SchemaRegistry is not up.
            So, we need to check if the SchemaRegistry is up before creating the topic.
          */

        while (!getSchemaRegistryStatus().is2xxSuccessful()) {
            // Check if the retry is exhausted
            checkMaxRetry(retryCount++, maxRetry);
            sleep(sleepTimeMs);
            sleepTimeMs *= multiplier;
        }
    }

    /**
      Make a REST call to the SchemaRegistry URL defined in the configuration file to check if the SchemaRegistry is up.
      If the REST call fails, then return SERVICE_UNAVAILABLE status.
     */
  private HttpStatusCode getSchemaRegistryStatus() {
      try {
          return webClient
                  .method(HttpMethod.GET)
                  .uri(kafkaConfigData.getSchemaRegistryUrl())
                  .exchangeToMono(response -> Mono.just(response.statusCode()))
                  .block();
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


   private boolean isTopicCreated(Collection<TopicListing> topics , String topicName) {
       if (topics == null) return false;
       boolean isCreated = topics.stream()
                           .anyMatch(topic -> {
                                      return topic.name().equals(topicName); // returns true if the topic is created
                                     });
       log.info("Topic {} is {}", topicName, isCreated ? "created" : "not created yet");
       return isCreated;
   }

    public boolean isTopicCreated1(Collection<TopicListing> topics, String topicName){
        return topics.stream()
                .anyMatch(topic -> {
                 return topic.name().equals(topicName);
                });

    }

    private CreateTopicsResult doCreateTopics(RetryContext retryContext) throws ExecutionException, InterruptedException {
        List<String> topicToBeCreated = kafkaConfigData.getTopicNamesToCreate();

        topicToBeCreated.stream().forEach(topicName -> {
            log.info("Topic {} to be created is ", topicName);
        });

        log.info("Creating {} Number of topics(s), The current Retry attempt {}", topicToBeCreated.size(), retryContext.getRetryCount());
        List<NewTopic> kafkaTopics = topicToBeCreated.stream()
                                     .map(topic ->
                                      { // Create a NewTopic object for each topic name to be created,
                                         return new NewTopic(topic.trim(), kafkaConfigData.getNumOfPartitions(),
                                                              kafkaConfigData.getReplicationFactor());
                                      }).collect(Collectors.toList());

      /*
        The adminClient.createTopics() returns CreateTopicsResult which contains KafkaFuture per topic.
        We can convert each KafkaFuture to CompletableFuture and attach callbacks:
       */
        return adminClient.createTopics(kafkaTopics);
    }

    private CreateTopicsResult doCreateTopicsNonBlocking(RetryContext retryContext) throws ExecutionException, InterruptedException {
        List<String> topicToBeCreated = kafkaConfigData.getTopicNamesToCreate();
        log.info("Creating {} Number of topics(s), The current Retry attempt {}", topicToBeCreated.size(), retryContext.getRetryCount());

        List<NewTopic> kafkaTopics = topicToBeCreated.stream()
                .map(topic -> new NewTopic(topic.trim(),
                                     kafkaConfigData.getNumOfPartitions(),
                                     kafkaConfigData.getReplicationFactor()))
                .collect(Collectors.toList());

        CreateTopicsResult createTopicsResult = adminClient.createTopics(kafkaTopics);

        // Attach a callback per topic using CompletableFuture
        createTopicsResult.values().forEach((topicName, kafkaFuture) -> {
            // KafkaFuture.toCompletionStage() converts KafkaFuture → CompletionStage → CompletableFuture
            kafkaFuture.toCompletionStage()
                       .toCompletableFuture()
                       .whenComplete((result, ex) -> {
                           if (ex != null) {
                               log.error("Topic {} creation FAILED: {}", topicName, ex.getMessage());
                           } else {
                               log.info("Topic {} created SUCCESSFULLY", topicName);
                           }
                       });
        });

        return createTopicsResult;
    }

   // Fetch the topics created from the adminClient
    private Collection<TopicListing> getTopics() {
        Collection<TopicListing> topics;
        try {
            // Check the topics created
            topics = retryTemplate.execute(this::doGetTopics);
        } catch (Throwable t) {
            log.error("Error reading kafka topic {}", t.getMessage());
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
