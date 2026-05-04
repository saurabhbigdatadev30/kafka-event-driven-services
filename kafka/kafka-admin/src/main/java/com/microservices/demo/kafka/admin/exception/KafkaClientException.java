package com.microservices.demo.kafka.admin.exception;

/**
 * Exception class for Kafka client error situations.
 */
public class KafkaClientException extends RuntimeException
{

  //  ❌ NO private String message field defined here , it is inherited from getMessage() of Throwable

      // private String message

    public KafkaClientException() {
    }

    public KafkaClientException(String message) {
        super(message);
    }

    public KafkaClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
