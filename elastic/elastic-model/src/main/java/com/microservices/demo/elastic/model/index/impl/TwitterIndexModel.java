package com.microservices.demo.elastic.model.index.impl;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.microservices.demo.elastic.model.index.IndexModel;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

@Data
@Builder
@Document(indexName = "#{@elasticConfigData.indexName}")
public class TwitterIndexModel implements IndexModel {

    @JsonProperty
    private String id;
    @JsonProperty
    private Long userId;
    @JsonProperty
    private String text;

    /**
       Apart from @JsonProperty, we need to define 2 annotations [@JsonFormat ,  @Field]  to convert createdAt,
       from local date time to the elasticsearch date during indexing operation.
     - for year, we use 'u' instead of 'y', which is the correct way of defining a year in the pattern for a custom Elasticsearch date.
    */

    // Required parsing a Json to this object
    @Field(type = FieldType.Date, format = {}, pattern = "uuuu-MM-dd'T'HH:mm:ssZZ")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd'T'HH:mm:ssZZ")
    @JsonProperty
    private ZonedDateTime createdAt;

    // This is not needed , taken care by the Lombok s@Data
    @Override
    public String getId() {
        return id;
    }
}
