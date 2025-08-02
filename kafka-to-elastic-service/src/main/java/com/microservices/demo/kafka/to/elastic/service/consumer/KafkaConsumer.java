package com.microservices.demo.kafka.to.elastic.service.consumer;

import org.apache.avro.specific.SpecificRecordBase;

import java.util.List;

/*
    KafkaConsumer is an interface that defines a method to receive messages from Kafka.
    It is parameterized with a type T that extends SpecificRecordBase, allowing it to work with Avro records.
    The receive method takes lists of messages <avro format>, keys, partitions, and offsets as parameters.
 */
public interface KafkaConsumer<T extends SpecificRecordBase> {
    void receive(List<T> messages, List<Long> keys, List<Integer> partitions, List<Long> offsets);
}
