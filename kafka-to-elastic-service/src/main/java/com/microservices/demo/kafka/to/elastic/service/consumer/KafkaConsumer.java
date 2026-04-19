package com.microservices.demo.kafka.to.elastic.service.consumer;

import com.microservices.demo.kafka.avro.model.TwitterAvroModel;
import org.apache.avro.specific.SpecificRecordBase;
import org.apache.kafka.clients.consumer.Consumer;
import org.springframework.kafka.support.Acknowledgment;

import java.io.Serializable;
import java.util.List;

/*
    KafkaConsumer is an interface that defines a method to receive messages from Kafka.
    It is parameterized with a type T that extends SpecificRecordBase, allowing it to work with Avro records.
    The receive method takes lists of messages <avro format>, keys, partitions, and offsets as parameters.
 */
public interface KafkaConsumer<K extends Serializable, V extends SpecificRecordBase> {
    void receive(List<V> messages, List<Long> keys, List<Integer> partitions, List<Long> offsets,
                 Acknowledgment acknowledgment ,
                 Consumer<Long, TwitterAvroModel> consumer);
}
