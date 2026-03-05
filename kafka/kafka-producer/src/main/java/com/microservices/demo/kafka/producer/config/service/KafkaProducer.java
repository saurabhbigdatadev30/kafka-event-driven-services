package com.microservices.demo.kafka.producer.config.service;

import org.apache.avro.specific.SpecificRecordBase;

import java.io.Serializable;

/**
 1. The KafkaProducer interface defines a contract for sending messages to a Kafka topic.

 2.  The send method takes the (topic name, key, and message) as parameters and is responsible for sending the message
 to the specified Kafka topic.

 3. The generic type ensures that the key is serialized for transmission, and the value is compatible with
     Avro serialization, which is commonly used in Kafka messaging.
 */

public interface KafkaProducer<K extends Serializable, V extends SpecificRecordBase> {
    void send(String topicName, K key, V message);
}
