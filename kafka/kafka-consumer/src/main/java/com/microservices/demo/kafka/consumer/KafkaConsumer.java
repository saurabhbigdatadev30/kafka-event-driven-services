package com.microservices.demo.kafka.consumer;

import org.apache.avro.specific.SpecificRecordBase;

import java.io.Serializable;
import java.util.List;

public interface KafkaConsumer<K extends Serializable, V extends SpecificRecordBase> {
    // Generics ensure that the message is of Type SpecificRecordBase .
    void receive(List<V> messages, List<K> keys, List<Integer> partitions, List<Long> offsets);
}
