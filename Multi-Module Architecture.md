## Multi-Module Architecture — Full Visual (Including `twitter-to-kafka-service`)

---

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          app-config-data module                                 │
│                                                                                 │
│  @ConfigurationProperties(prefix = "retry-config")                             │
│  class RetryConfigData {                                                        │
│      Long    initialIntervalMs;  Double multiplier;                             │
│      Long    maxIntervalMs;      Integer maxAttempts;   Long sleepTimeMs;       │
│  }                                                                              │
│                                                                                 │
│  @ConfigurationProperties(prefix = "kafka-config")                             │
│  class KafkaConfigData {                                                        │
│      List<String> topicNamesToCreate;   Integer numOfPartitions;                │
│      Short  replicationFactor;          String  schemaRegistryUrl;              │
│      String topicName;                                                          │
│  }                                                                              │
│                                                                                 │
│  @ConfigurationProperties(prefix = "twitter-to-kafka-service")                 │
│  class TwitterToKafkaServiceConfigData {                                        │
│      List<String> twitterKeywords;   Boolean enableMockTweets;                  │
│      Boolean      enableV2Tweets;    Long    mockSleepMs;    ...                │
│  }                                                                              │
│                                                                                 │
│       Source: config-client-twitter_to_kafka.yml  (via config-server)          │
└──────────┬───────────────────────────────────────────────────────┬──────────────┘
           │ dependency                                            │ dependency
           ▼                                                       ▼
┌────────────────────────────────┐         ┌──────────────────────────────────────┐
│      common-config module      │         │         kafka-model module           │
│                                │         │                                      │
│  @Configuration                │         │  *.avsc (Avro Schema)                │
│  class RetryConfig {           │         │  ┌─────────────────────────────┐    │
│                                │         │  │  twitter-avro-model.avsc    │    │
│    @Bean                       │         │  │  {                          │    │
│    RetryTemplate               │         │  │    "name":"TwitterAvroModel"│    │
│    retryTemplate() {           │         │  │    "fields": [              │    │
│      SimpleRetryPolicy     ◄───┼─────────┼──┼── id, userId, text,        │    │
│        .setMaxAttempts()       │         │  │    createdAt                │    │
│      ExponentialBackOff    ◄───┼─────────┼──┼── ]                        │    │
│        .setInitialInterval()   │  reads  │  │  }                          │    │
│        .setMultiplier()        │  Retry  │  └─────────────────────────────┘    │
│        .setMaxInterval()       │  Config │       ↓ mvn clean install           │
│      return retryTemplate;     │         │  TwitterAvroModel.java (generated)  │
│    }                           │         └──────────────────────────────────────┘
│  }                             │
└──────────┬─────────────────────┘
           │ dependency
           ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          kafka-admin module                                     │
│                                                                                 │
│  @Component                                                                     │
│  class KafkaAdminClient {                                                       │
│      KafkaConfigData  kafkaConfigData;   ◄── reads topic names, partitions     │
│      RetryConfigData  retryConfigData;   ◄── reads retry config                │
│      AdminClient      adminClient;       ◄── KafkaAdminConfig @Bean            │
│      RetryTemplate    retryTemplate;     ◄── common-config @Bean               │
│      WebClient        webClient;         ◄── checks SchemaRegistry health      │
│                                                                                 │
│      createTopics()                                                             │
│        └── retryTemplate.execute(this::doCreateTopics)  ← HOF + method ref    │
│              └── doCreateTopics(RetryContext ctx)                               │
│                    └── adminClient.createTopics(kafkaTopics) ──► Kafka Broker  │
│                                                                                 │
│      checkTopicsCreated()                                                       │
│        └── manual retry loop (sleepTimeMs *= multiplier)                       │
│              └── getTopics() → retryTemplate.execute(this::doGetTopics)        │
│                    └── adminClient.listTopics().listings().get()                │
│                                                                                 │
│      checkSchemaRegistry()                                                      │
│        └── webClient.GET(schemaRegistryUrl) → 2xx? ✅ : retry ⚠️              │
└──────────┬──────────────────────────────────────────────────────────────────────┘
           │ dependency (kafka-admin module added in twitter-to-kafka pom.xml)
           ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                      twitter-to-kafka-service module                            │
│                                                                                 │
│  @SpringBootApplication                                                         │
│  @ComponentScan("com.microservices.demo")  ◄── scans ALL modules' @Beans       │
│  class TwitterToKafkaServiceApplication implements CommandLineRunner {          │
│      StreamRunner      streamRunner;     ◄─── conditional impl loaded          │
│      StreamInitializer streamInitializer;                                       │
│                                                                                 │
│      run() {                                                                    │
│          streamInitializer.init();  ──────────────────────────────────┐        │
│          streamRunner.start();  ──────────────────────────────────┐   │        │
│      }                                                            │   │        │
│  }                                                                │   │        │
│                                                                   │   │        │
│  ┌────────────────────────────────────────────┐                  │   │        │
│  │  KafkaStreamInitializer  (StreamInitializer)│  ◄──────────────┘   │        │
│  │                                            │                      │        │
│  │  init() {                                  │                      │        │
│  │      kafkaAdminClient.createTopics();   ───┼──► KafkaAdminClient  │        │
│  │      kafkaAdminClient.checkSchemaRegistry()┼──► KafkaAdminClient  │        │
│  │  }                                         │                      │        │
│  └────────────────────────────────────────────┘                      │        │
│                                                                       │        │
│  ┌──────────────────────────────────────────────────────────────┐    │        │
│  │  StreamRunner  (interface)  ◄────────────────────────────────┼────┘        │
│  │                                                              │             │
│  │  @ConditionalOnExpression("enable-v2-tweets")               │             │
│  │  TwitterV2KafkaStreamRunner                                  │             │
│  │      └── connectStream() → Twitter V2 API (real tweets)     │             │
│  │                                                              │             │
│  │  @ConditionalOnProperty("enable-twitter-tweets")            │             │
│  │  TwitterKafkaStreamRunner                                    │             │
│  │      └── Twitter4J stream (real tweets via Twitter4J lib)   │             │
│  │                                                              │             │
│  │  @ConditionalOnExpression                                    │             │
│  │   ("!enable-v2-tweets && enable-mock-tweets")               │             │
│  │  MockKafkaStreamRunner                                       │             │
│  │      └── generates random tweets (no API needed)            │             │
│  └───────────────────────────────┬───────────────────────────┬─┘             │
│                                  │ tweet received            │                │
│                                  ▼                           │                │
│  ┌───────────────────────────────────────────┐               │                │
│  │  TwitterKafkaStatusListener               │               │                │
│  │  (extends StatusAdapter)                  │               │                │
│  │                                           │               │                │
│  │  onStatus(Status status) {                │               │                │
│  │      twitterAvroModel =                   │               │                │
│  │        transformer                        │               │                │
│  │          .getTwitterAvroModel(status); ───┼──►  ┌─────────┴──────────────┐│
│  │      kafkaProducer                        │     │TwitterStatusToAvro      ││
│  │        .send(topicName,                   │     │Transformer              ││
│  │              userId,          ────────────┼──►  │  Status ──► Avro Model  ││
│  │              twitterAvroModel);           │     │  id, userId, text,      ││
│  │  }                                        │     │  createdAt              ││
│  └───────────────────────────────────────────┘     └────────────────────────┘│
└──────────┬──────────────────────────────────────────────────────────────────┘
           │ KafkaProducer.send(topicName, key=userId, value=TwitterAvroModel)
           ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Kafka Broker (Event Store)                            │
│                                                                                 │
│   Topic: "twitter-topic"                                                        │
│   ┌─────────────────────────────────────────────────────────────────────────┐  │
│   │ Partition 0  │ Partition 1  │ Partition 2  │  ...                       │  │
│   │ key=userId   │ key=userId   │ key=userId   │                            │  │
│   │ val=AvroMsg  │ val=AvroMsg  │ val=AvroMsg  │                            │  │
│   └─────────────────────────────────────────────────────────────────────────┘  │
└──────────┬──────────────────────────────────────────────────────────────────────┘
           │ consumed by downstream services
           ▼
  kafka-to-elastic-service  /  kafka-streams-service  /  analytics-service ...
```

---

### 📌 Key Design Observations

| Concern | How it's Addressed |
|---|---|
| **Topic creation** | `KafkaStreamInitializer.init()` → `KafkaAdminClient.createTopics()` at startup |
| **Schema Registry check** | `KafkaAdminClient.checkSchemaRegistry()` before any message is sent |
| **Retry logic** | Centralised in `common-config` → `RetryTemplate` — reused by both `kafka-admin` and `kafka-producer` |
| **Runner selection** | `@ConditionalOnExpression` / `@ConditionalOnProperty` — only ONE `StreamRunner` bean loaded at runtime |
| **Serialisation** | `TwitterStatusToAvroTransformer` converts `Twitter4J Status` → `TwitterAvroModel` before publish |
| **Kafka key** | `userId` is used as the partition key — ensures all tweets from the same user land in the **same partition** |

### 🔗 Startup Sequence
```
Spring Boot starts
      │
      ▼
@ComponentScan discovers all beans across modules
      │
      ▼
CommandLineRunner.run()
      │
      ├──► streamInitializer.init()
      │         ├──► kafkaAdminClient.createTopics()    → retryTemplate → AdminClient → Kafka
      │         └──► kafkaAdminClient.checkSchemaRegistry() → WebClient → Schema Registry
      │
      └──► streamRunner.start()   (only ONE active based on @Conditional)
                └──► infinite stream loop → onStatus() → transform → KafkaProducer.send()
```

