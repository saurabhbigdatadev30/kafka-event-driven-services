package com.microservices.demo.twitter.to.kafka.service;

import com.microservices.demo.twitter.to.kafka.service.init.StreamInitializer;
import com.microservices.demo.twitter.to.kafka.service.runner.StreamRunner;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.microservices.demo")
@Slf4j
public class TwitterToKafkaServiceApplication implements CommandLineRunner {
   // We have 3 implementations of StreamRunner interface responsible to read the stream from Twitter & put into Kafka Topic
   // 1. Twitter 4J Library  , 2. Twitter V2 API  , 3.MockKafkaStreamRunner
    private final StreamRunner streamRunner;
   // Check for the Topics are created
    private final StreamInitializer streamInitializer;


    public TwitterToKafkaServiceApplication(StreamRunner runner, StreamInitializer initializer) {
        this.streamRunner = runner;
        this.streamInitializer = initializer;
    }

    public static void main(String[] args) {
        SpringApplication.run(TwitterToKafkaServiceApplication.class, args);
    }


    // We override the CommandLineRunner interface method run() , this method will be executed after the Spring Boot application starts.
    // In this method we call the init() method of StreamInitializer to create the Kafka topic
    @Override
    public void run(String... args) throws Exception {
         log.info("App starts...>>>");
        // init method is responsible to create Kafka Topic
        streamInitializer.init();
        /*
          We have 3 implementations of StreamRunner interface responsible to read the stream from Twitter & put into Kafka Topic
            1. Twitter 4J Library  , 2. Twitter V2 API  , 3.MockKafkaStreamRunner
              Based on the @ConditionalOnProperty annotation , the respective implementation class will be load at runtime & execute the
              start() method.
         */
        streamRunner.start();
    }
}
