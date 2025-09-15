package com.microservices.demo.twitter.to.kafka.service.response;

import com.microservices.demo.twitter.to.kafka.service.runner.impl.TwitterRule;
import lombok.Data;

import java.util.List;

/*
     This class represents the response from Twitter API when fetching the rules.
     It contains a list of TwitterRule objects. List<TwitterRule>
     The TwitterRule contains the details of each rule (id, value, and tag).

     public class TwitterRule {
         private String id;
         private String value;
         private String tag;
 */
@Data
public class TwitterRulesResponse {
    private List<TwitterRule> data;
}
