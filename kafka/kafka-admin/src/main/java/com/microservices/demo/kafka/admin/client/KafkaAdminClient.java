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

The kafka-admin module [KafkaAdminClient.java]  depends  on the other module classes

(1) KafkaConfigData.java - @Configuration class defined in the app-config-data module which reads the Kafka configurations
                            [prefix = "kafka-config"]  from the config-client-twitter_to_kafka.yml file

(2) RetryConfigData.java - @Configuration class defined in the app-config-data module which reads the Retry  configurations
                            [prefix = "retry-config"] from the config-client-kafka_to_elastic.yml file

(3) AdminClient -          We instantiate this Bean in Configuration class KafkaAdminConfig

(4) RetryTemplate -        We instantiate this Bean in Configuration class RetryConfig in common-config module

 */
@Component
@Slf4j
public class KafkaAdminClient {

  //  private static final Logger LOG = LoggerFactory.getLogger(KafkaAdminClient.class);

    // The KafkaConfigData is a @Configuration class that includes from Module =  app-config-data module
    private final KafkaConfigData kafkaConfigData;

    // The RetryConfigData is a @Configuration class that includes from  Module =  app-config-data module
    private final RetryConfigData retryConfigData;

    // AdminClient is a Spring Bean instantiated by KafkaAdminConfig (a @Configuration class) in the kafka-admin module.
    private final AdminClient adminClient;

    // RetryTemplate is a Spring Bean instantiated by RetryConfig (a @Configuration class) in the common-config module.
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
       /**
        1. The retryTemplate.execute(....) method is a HOF , it takes an interface RetryCallback as an argument."

                   @Override
                   public final <T, E extends Throwable> T execute(RetryCallback<T, E> retryCallback)
                   {
                       ...
                   }

           2. The RetryCallback is a functional interface which has a single method doWithRetry(RetryContext context)
                   public interface RetryCallback<T, E extends Throwable>
                   {
                      T doWithRetry(RetryContext context) throws E;
                   }

         Option 1 -
            Legacy Way :- Using annonymous class implementation of the RetryCallback interface which is the argument to
                          the retryTemplate.execute(...) method .
             */

            retryTemplate.execute(new RetryCallback()
              {
                @Override
                public CreateTopicsResult doWithRetry(RetryContext retryContext) throws Exception
                {
                    return doCreateTopics(retryContext);
                }
              });


            /**
             ️ Option 2 - Using the Lambda Function
             💥💥💥
             1. retryTemplate.execute(... ) method takes the Functional interface RetryCallback as input argument .
             2. This functional interface RetryCallback defines SAM - doWithRetry(RetryContext) method .
             3. The lambda function (ctx -> doCreateTopics(ctx)) is implementation of  doWithRetry(RetryContext) method
                 of the Functionan interface RetryCallback  interface
             */

            createTopicsResult = retryTemplate.execute(ctx -> doCreateTopics(ctx));

            // Using method reference to the doCreateTopics method , replace the lambda
            createTopicsResult = retryTemplate.execute(this::doCreateTopics);
            log.info("Create topic result {}", createTopicsResult.values().values());
        }
        catch (Throwable t)
        {
            // If the topic creation fails after all retries, it throws a KafkaClientException
            log.error("Reached max number of retry for creating kafka topic(s)! {}", t.getMessage());
            throw new KafkaClientException("Reached max number of retry for creating kafka topic(s)!", t);
        }
        checkTopicsCreated();
    }

    // Check if the Topic is created with retry option
    public void checkTopicsCreated() {
        Collection<TopicListing> kafkaTopicsCreatedInCluster;
        // Fetch the Topics created ,  adminClient.listTopics().listings() , to check the topics created in the Kafka Broker
        kafkaTopicsCreatedInCluster = getTopics();
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        int multiplier = retryConfigData.getMultiplier().intValue();
        Long sleepTimeMs = retryConfigData.getSleepTimeMs();
        for (String topicToBeCreated : kafkaConfigData.getTopicNamesToCreate())
        {
            // Check if the topic is created, if not then retry until the topic is created or max retry is exhausted
            while (!isTopicCreated1(kafkaTopicsCreatedInCluster, topicToBeCreated))
            {
                log.info("topic is not created yet .. maxRetry so far {} ...  " , maxRetry);
                checkMaxRetry(retryCount++, maxRetry);
                delayMs(sleepTimeMs);
                sleepTimeMs *= multiplier;
                kafkaTopicsCreatedInCluster = getTopics();
            }
        }
    }

    public boolean isTopicCreated1(String topicName , Collection<TopicListing> topicsInCluster)
    {
        if (topicsInCluster == null) return false;
        return topicsInCluster.stream().anyMatch(topic ->
        {
          return topic.name().equals(topicName);
     });
    }


    public void checkSchemaRegistry() {
        int retryCount = 1;
        Integer maxRetry = retryConfigData.getMaxAttempts();
        int multiplier = retryConfigData.getMultiplier().intValue();
        Long sleepTimeMs = retryConfigData.getSleepTimeMs();

        while (!getSchemaRegistryStatusCode().is2xxSuccessful())
        {
            // Check if the retry is exhausted, break the while loop if exhausted
            checkMaxRetry(retryCount++, maxRetry);
            delayMs(sleepTimeMs);
            // increase the sleep time exponentially with muliplier
            sleepTimeMs *= multiplier;
        }
    }

    /**
      Make a REST call to the SchemaRegistry URL defined in the configuration file to check if the SchemaRegistry
       is up.
      If the REST call fails, then return SERVICE_UNAVAILABLE status.
     */
  private HttpStatusCode getSchemaRegistryStatus() {
      try {
          return webClient
                  .method(HttpMethod.GET)
                  .uri(kafkaConfigData.getSchemaRegistryUrl())
                  .exchangeToMono(response -> Mono.just(response.statusCode()))
                  .block();
      } catch (Exception e)
      {
          return HttpStatus.SERVICE_UNAVAILABLE;
      }
  }

  private HttpStatusCode getSchemaRegistryStatusCode(){
      return webClient.method(HttpMethod.GET)
              .uri(kafkaConfigData.getSchemaRegistryUrl())
              // from the client responsee get the status code
              .exchangeToMono(clientResponse -> Mono.just(clientResponse.statusCode()))
              .block();
  }

    private void delayMs(Long sleepTimeMs) {
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


   private boolean isTopicCreated(Collection<TopicListing> topics , String topicNameToBeCreated)
   {
       if (topics == null) return false;
       boolean isCreated = topics.stream()
                           .anyMatch(topic -> {
                                       return topic.name().equals(topicNameToBeCreated); // returns true if the topic is created
                                     });
       log.info("Topic {} is {}", topicNameToBeCreated, isCreated ? "created" : "not created yet");
       return isCreated;
   }

    public boolean isTopicCreated1(Collection<TopicListing> topics, String topicName){
        return topics.stream()
                .anyMatch(topic -> {
                 return topic.name().equals(topicName);
                });

    }
    // Using legacy way to implement the HOF
    public void demoAnnonymousClassImpl() throws Throwable
    {
       retryTemplate.execute(new RetryCallback<Object, Throwable>() {
          @Override
          public Object doWithRetry(RetryContext retryContext) throws Throwable {
              return doCreateTopics(retryContext);
          }
      });
    }

    public List<NewTopic> createTopicsFromConfig(){
        return kafkaConfigData.getTopicNamesToCreate()
                .stream()
                .map(topic -> {
                    return new NewTopic(topic.trim(),kafkaConfigData.getNumOfPartitions(),
                            kafkaConfigData.getReplicationFactor());
                })
                .collect(Collectors.toList());
    }

    /**
     👉  Signature of the doCreateTopics method is same as the signature of the doWithRetry() method of the
         RetryCallback interface.

     👉  so we can directly use the method reference to this method in the retryTemplate.execute(...) method.
     */

    private CreateTopicsResult doCreateTopics(RetryContext retryContext)
            throws ExecutionException, InterruptedException {
           List<String> topicToBeCreated = kafkaConfigData.getTopicNamesToCreate();

        topicToBeCreated.forEach(topic -> {
            log.info("Creating topic {}", topic);
        });

     // Build NewTopic object for each topic to be created using the topic name, number of partitions and replication factor
        List<NewTopic> kafkaTopics = topicToBeCreated.stream()  // Stream<String>
                                     .map(topic ->
                                      {
                                       return new NewTopic(topic.trim(), kafkaConfigData.getNumOfPartitions(),
                                                           kafkaConfigData.getReplicationFactor());
                                      }).collect(Collectors.toList());

      /*
        The adminClient.createTopics() returns CreateTopicsResult which contains KafkaFuture per topic.
        We can convert each KafkaFuture to CompletableFuture and attach callbacks:
       */
        return adminClient.createTopics(kafkaTopics);
    }

    private CreateTopicsResult doCreateTopicsNonBlocking(RetryContext retryContext)
            throws ExecutionException, InterruptedException {
        List<String> topicToBeCreated = kafkaConfigData.getTopicNamesToCreate();
        log.info("Creating {} Number of topics(s), The current Retry attempt {}", topicToBeCreated.size(), retryContext.getRetryCount());

        List<NewTopic> kafkaTopics = topicToBeCreated.stream()
                .map(topic -> new NewTopic(topic.trim(),
                                     kafkaConfigData.getNumOfPartitions(),
                                     kafkaConfigData.getReplicationFactor()))
                .collect(Collectors.toList());

        CreateTopicsResult createTopicsResult = adminClient.createTopics(kafkaTopics);

        // Attach a callback per topic using CompletableFuture
        createTopicsResult.values().forEach((topicName, kafkaFuture) ->
        {
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
            /**
             Option 1 - Using Annonymous class :-
                 a.We implement RetryCallback interface which is the argument to the retryTemplate.execute(...) method .
                 b.The RetryCallback interface defines SAM doWithRetry() which is overriden
              */

            retryTemplate.execute(new RetryCallback<Object, Throwable>()
             {
                @Override
                public Object doWithRetry(RetryContext retryContext) throws Exception {
                    return doGetTopics(retryContext);
                }
            });
            // Option 2 - Using Lambda to implement the  RetryCallback interface  SAM method doWithRetry()
            topics = retryTemplate.execute(ctx ->doGetTopics(ctx));
            topics = retryTemplate.execute(this::doGetTopics);
        }
        // If the retries are exhaused, then it throws a KafkaClientException
        catch (Throwable t) {
            log.error("Error reading kafka topic {}", t.getMessage());
            throw new KafkaClientException("Reached max number of retry for reading kafka topic(s)!", t);
        }
        return topics;
    }

    private Collection<TopicListing> doGetTopics(RetryContext retryContext)
            throws ExecutionException, InterruptedException
    {
        log.info("Reading kafka topic {}, The number of attempt {}",
                   kafkaConfigData.getTopicNamesToCreate().toArray(), retryContext.getRetryCount());
        // Fetch the topics created from the adminClient ..  Collection<TopicListing>
        Collection<TopicListing> topics = adminClient.listTopics().listings().get();
        if (topics != null)
        {
            topics.forEach(topic ->
            {
                log.debug("Topic with name {} is created", topic.name());
            });
        }
        return topics;
    }

}
