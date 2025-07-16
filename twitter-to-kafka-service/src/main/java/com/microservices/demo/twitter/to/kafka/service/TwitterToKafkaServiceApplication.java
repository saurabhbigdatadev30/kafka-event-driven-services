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

/*

@SpringBootApplication
@ComponentScan(basePackages = "com.microservices.demo")
@ComponentScan(basePackages = "com.microservices.demo.twitter.to.kafka.service")

(1) When we work with multiple modules there will be spring beans that  will reside in different modules . So , it is required to allow finding
    the spring beans of the other modules also inside this module.

    For example, the TwitterToKafkaServiceConfigData.java which is used to read the configuration is located in the app-config-module,
    When a spring boot application starts by default it scans only the packages  from the package directory where the
    main application class is located.

     As a practice,  We will use ->  com.microservices.demo as the starting package for all the packages in all the modules.
     So that Spring will scan and finds all the beans that resides in all modules.

     So, every module will have a package structure that starts with com.microservices.demo, only the remaining parts will be  different.

(2) In twitter-to-Kafka-service module , the base package is com.microservices.demo .
    For example, the Twitter to Kafka service config data class is in com.microservices.demo.config package,
    which doesn't fit to the default scan definition.

In this case, we can use ComponentScan annotation with base packages and mention com.microservices.demo as the base package.
Since we will use com.microservices.demo as the starting package for all packages in all modules.  Spring will scan and finds all the beans
that resides in all modules.


==================================================================================================================================
The reason for this is that the TwitterToKafkaServiceConfigData which is used to read the configuration is located the app-config-module , while
this needs to be read in another module = Twitter to Kafka service module
 */

/*
   When the service starts , we need to read data from twitter , so we implement CommandLineRunner & override the run method
 */
@Slf4j
public class TwitterToKafkaServiceApplication implements CommandLineRunner {

  //  private static final Logger LOG = LoggerFactory.getLogger(TwitterToKafkaServiceApplication.class);

    private final StreamRunner streamRunner;

    private final StreamInitializer streamInitializer;


    public TwitterToKafkaServiceApplication(StreamRunner runner, StreamInitializer initializer) {
        this.streamRunner = runner;
        this.streamInitializer = initializer;
    }

    public static void main(String[] args) {
        SpringApplication.run(TwitterToKafkaServiceApplication.class, args);
    }


    // We override the CommandLineRunner interface method
    @Override
    public void run(String... args) throws Exception {
        log.info("App starts...");
        // init method is responsible to create Kafka Topic
        streamInitializer.init();
        streamRunner.start();
    }
}
