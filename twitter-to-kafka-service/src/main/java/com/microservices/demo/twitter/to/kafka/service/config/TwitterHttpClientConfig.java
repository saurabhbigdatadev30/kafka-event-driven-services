package com.microservices.demo.twitter.to.kafka.service.config;

import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**

  TwitterHttpClientConfig is responsible for creating the @Bean CloseableHttpClient.
  1. CloseableHttpClient is used by TwitterV2StreamHelper to make HTTP requests to the Twitter V2 API.
  2. CloseableHttpClient is created ONCE as a Spring Bean (Singleton), shared across all methods.
  3. Spring manages the lifecycle — destroyMethod = "close" ensures the connection pool
     is properly released when the Spring context shuts down.

  Why CloseableHttpClient and NOT HttpClient?
    - HttpClient is just an interface — no close() method, Spring cannot manage its lifecycle.
    - CloseableHttpClient implements Closeable — Spring calls close() on context shutdown,
      releasing connection pool resources properly.
 */
@Configuration
public class TwitterHttpClientConfig
{
    @Bean(destroyMethod = "close")   // ← Spring calls close() automatically on context shutdown
    public CloseableHttpClient twitterHttpClient() {
        return HttpClients.custom()
                .setDefaultRequestConfig(
                        RequestConfig.custom()
                                .setCookieSpec(CookieSpecs.STANDARD)
                                .build()
                )
                .build();
    }
}