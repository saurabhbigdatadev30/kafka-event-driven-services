package com.microservices.demo.twitter.to.kafka.service.runner.impl;

import lombok.Data;

@Data
public class TwitterRule {
    private String id;
    private String value;
    private String tag;
}
