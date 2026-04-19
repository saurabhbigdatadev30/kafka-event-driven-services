package com.microservices.demo.kafka.to.elastic.service.consumer.impl;

import com.microservices.demo.config.KafkaConfigData;
import com.microservices.demo.config.KafkaConsumerConfigData;
import com.microservices.demo.elastic.index.client.service.ElasticIndexClient;
import com.microservices.demo.elastic.model.index.impl.TwitterIndexModel;
import com.microservices.demo.kafka.admin.client.KafkaAdminClient;
import com.microservices.demo.kafka.avro.model.TwitterAvroModel;
import com.microservices.demo.kafka.to.elastic.service.consumer.KafkaConsumer;
import com.microservices.demo.kafka.to.elastic.service.transformer.AvroToElasticModelTransformer;
import org.apache.kafka.clients.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;


@Service
public class TwitterKafkaConsumer implements KafkaConsumer<Long, TwitterAvroModel> {

    private final ExecutorService executor;

    private static final Logger LOG = LoggerFactory.getLogger(TwitterKafkaConsumer.class);

    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    private final KafkaAdminClient kafkaAdminClient;

    private final KafkaConfigData kafkaConfigData;

    private final KafkaConsumerConfigData kafkaConsumerConfigData;

    private final AvroToElasticModelTransformer avroToElasticModelTransformer;

    private final ElasticIndexClient<TwitterIndexModel> elasticIndexClient;

    public TwitterKafkaConsumer(KafkaListenerEndpointRegistry listenerEndpointRegistry,
                                KafkaAdminClient adminClient,
                                KafkaConfigData configData,
                                KafkaConsumerConfigData consumerConfigData,
                                AvroToElasticModelTransformer transformer,
                                ElasticIndexClient<TwitterIndexModel> indexClient,
                                ExecutorService executor
    ) {
        this.kafkaListenerEndpointRegistry = listenerEndpointRegistry;
        this.kafkaAdminClient = adminClient;
        this.kafkaConfigData = configData;
        this.kafkaConsumerConfigData = consumerConfigData;
        this.avroToElasticModelTransformer = transformer;
        this.elasticIndexClient = indexClient;
        this.executor = executor;
    }

     //  This method is called when the application starts
    // It checks if the Kafka topics are created and starts the listener container
    @EventListener
    public void onAppStarted(ApplicationStartedEvent event) {
        kafkaAdminClient.checkTopicsCreated();
        LOG.info("Topics with name {} is ready for operations!", kafkaConfigData.getTopicNamesToCreate().toArray());
       // We manually start the Kafka Listener only when the checkTopicCreated returns topics
        Objects.requireNonNull(kafkaListenerEndpointRegistry
                .getListenerContainer(kafkaConsumerConfigData.getConsumerGroupId())).start();
    }

  /*  @Override
    @KafkaListener(id = "${kafka-consumer-config.consumer-group-id}",
            topics = "${kafka-config.topic-name}")
    public void receive(@Payload List<TwitterAvroModel> messages,
                        @Header(KafkaHeaders.RECEIVED_KEY) List<Long> keys,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
                        @Header(KafkaHeaders.OFFSET) List<Long> offsets,
                        Acknowledgment acknowledgment,              // ← requires MANUAL_IMMEDIATE ack mode
                        Consumer<Long, TwitterAvroModel> consumer)  // ← raw Kafka consumer for pause/resume
    {
        LOG.info("{} number of message received with keys {}, partitions {} and offsets {}, " +
                        "sending it to elastic: Thread id {}",
                messages.size(),
                keys.toString(),
                partitions.toString(),
                offsets.toString(),
                Thread.currentThread().getId());
        List<TwitterIndexModel> twitterIndexModels = avroToElasticModelTransformer.getElasticModels(messages);
        List<String> documentIds = elasticIndexClient.save(twitterIndexModels);
        LOG.info("Documents saved to elasticsearch with ids {}", documentIds.toArray());
    }*/

    @Override
    @KafkaListener(id = "${kafka-consumer-config.consumer-group-id}",
            topics = "${kafka-config.topic-name}")
    public void receive(@Payload List<TwitterAvroModel> messages,
                        @Header(KafkaHeaders.RECEIVED_KEY) List<Long> keys,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
                        @Header(KafkaHeaders.OFFSET) List<Long> offsets,
                        Acknowledgment acknowledgment,              // ← requires MANUAL_IMMEDIATE ack mode
                        Consumer<Long, TwitterAvroModel> consumer)  // ← raw Kafka consumer for pause/resume
    {
        LOG.info("{} messages received from partitions {} at offsets {} — pausing consumer",
                messages.size(), partitions, offsets);

        // Step 1: PAUSE — poll() keeps running but returns empty
        //         Heartbeats still sent → no rebalance ✅
        consumer.pause(consumer.assignment());

        // Step 2: Offload to processing thread pool — Kafka poll thread RETURNS IMMEDIATELY
        CompletableFuture.runAsync(() -> {
                    List<TwitterIndexModel> twitterIndexModels = avroToElasticModelTransformer.getElasticModels(messages);
                    elasticIndexClient.save(twitterIndexModels);  // heavy 8-min operation runs here
                }, executor)

                // Step 3: whenComplete — runs on processingPool thread after save() completes or fails
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        // ✅ SUCCESS — commit the offset , on success
                        acknowledgment.acknowledge();
                        LOG.info("Batch successfully indexed and committed. Partitions: {}, Offsets: {}",
                                partitions, offsets);
                    } else {
                        // ❌ FAILURE — do NOT commit, let next consumer re-read (at-least-once)
                        LOG.error("Failed to index batch. Partitions: {}, Offsets: {}. Skipping commit.",
                                partitions, offsets, ex);
                        // TODO: send to Dead Letter Topic for manual inspection
                    }

                    // Step 4: RESUME — always resume regardless of success/failure
                    //         Next poll() will now return new records
                    consumer.resume(consumer.assignment());
                    LOG.info("Consumer resumed for partitions {}", partitions);
                });


    }

    // Demo of HOF Implementation , using legacy way
     public void methodX()
     {
        CompletableFuture.runAsync(() -> {
           methodA();
        } , executor)
                .whenComplete(new BiConsumer<Void, Throwable>() {
                    @Override
                    public void accept(Void unused, Throwable throwable) {
                            if (throwable == null) {
                                System.out.println("success");
                            } else {
                                System.out.println("failure");
                            }
                    }
                });
     }

    private void methodA() {
    }


}






