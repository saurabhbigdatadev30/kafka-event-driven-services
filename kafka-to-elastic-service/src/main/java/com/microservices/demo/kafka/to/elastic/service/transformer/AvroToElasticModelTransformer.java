package com.microservices.demo.kafka.to.elastic.service.transformer;

import com.microservices.demo.elastic.model.index.impl.TwitterIndexModel;
import com.microservices.demo.kafka.avro.model.TwitterAvroModel;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class AvroToElasticModelTransformer {

    public List<TwitterIndexModel> getElasticModels(List<TwitterAvroModel> avroModels) {
        return avroModels.stream()
                .map(avroModel -> TwitterIndexModel
                        .builder()
                        .userId(avroModel.getUserId())
                        .id(String.valueOf(avroModel.getId()))
                        .text(avroModel.getText())
                        .createdAt(ZonedDateTime.ofInstant(Instant.ofEpochMilli(avroModel.getCreatedAt()),
                                ZoneId.systemDefault()))
                        .build()
                ).collect(Collectors.toList());
    }
}

/**

 .createdAt(ZonedDateTime.ofInstant(Instant.ofEpochMilli(avroModel.getCreatedAt()),
 ZoneId.systemDefault()))

-  The selected code converts a Unix timestamp (milliseconds since epoch) to a ZonedDateTime object
     avroModel.getCreatedAt()  — Retrieves the timestamp as a long value (epoch milliseconds)
     Instant.ofEpochMilli(...) — Converts the epoch milliseconds into an Instant (a point in time)
     ZonedDateTime.ofInstant(..., ZoneId.systemDefault()) — converts the Instant to a ZonedDateTime using
                                   the system's default timezone




 */