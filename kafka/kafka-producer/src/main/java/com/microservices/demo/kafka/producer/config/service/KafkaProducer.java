package com.microservices.demo.kafka.producer.config.service;

import org.apache.avro.specific.SpecificRecordBase;

import java.io.Serializable;

/**
 1. The KafkaProducer interface defines a contract for (type of the key , value) for sending messages to a Kafka topic.
       <K extends Serializable, V extends SpecificRecordBase>

 2. The generic type ensures that any class that implements the KafkaProducer
     - key is serialized for transmission,
     - value is compatible with Avro serialization, which is commonly used in Kafka messaging.
 */

public interface KafkaProducer<K extends Serializable, V extends SpecificRecordBase>
{
    // THe Generics Ensures that K is Serializable & V is subtype of SpecificRecordBase
    void send(String topicName, K key, V message);
}
