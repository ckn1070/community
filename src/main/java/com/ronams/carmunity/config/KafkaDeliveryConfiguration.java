package com.ronams.carmunity.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;

/** 같은 application에서도 relay와 consumer가 서로 다른 client identity를 주입받도록 조립합니다. */
@Configuration
@EnableKafka
class KafkaDeliveryConfiguration {
  @Bean
  @org.springframework.context.annotation.Lazy
  @ConditionalOnProperty(name = "carmunity.kafka.replay-enabled", havingValue = "true")
  DefaultKafkaProducerFactory<String, String> conversationReplayProducerFactory(
      KafkaProperties properties, Environment env) {
    return producerFactory(properties, env, "replay");
  }

  @Bean
  @org.springframework.context.annotation.Lazy
  @ConditionalOnProperty(name = "carmunity.kafka.replay-enabled", havingValue = "true")
  KafkaTemplate<String, String> conversationReplayKafkaTemplate(
      @org.springframework.beans.factory.annotation.Qualifier("conversationReplayProducerFactory")
          DefaultKafkaProducerFactory<String, String> factory) {
    return kafkaTemplate(factory);
  }

  @Bean
  @ConditionalOnProperty(name = "carmunity.kafka.relay-enabled", havingValue = "true")
  DefaultKafkaProducerFactory<String, String> communityKafkaProducerFactory(
      KafkaProperties properties, Environment env) {
    return producerFactory(properties, env, "relay");
  }

  @Bean
  @ConditionalOnProperty(name = "carmunity.kafka.consumer-enabled", havingValue = "true")
  DefaultKafkaProducerFactory<String, String> notificationDltProducerFactory(
      KafkaProperties properties, Environment env) {
    return producerFactory(properties, env, "dlt");
  }

  @Bean
  @ConditionalOnProperty(name = "carmunity.kafka.consumer-enabled", havingValue = "true")
  KafkaTemplate<String, String> notificationDltKafkaTemplate(
      @org.springframework.beans.factory.annotation.Qualifier("notificationDltProducerFactory")
          DefaultKafkaProducerFactory<String, String> factory) {
    return kafkaTemplate(factory);
  }

  @Bean
  @ConditionalOnProperty(name = "carmunity.kafka.consumer-enabled", havingValue = "true")
  org.springframework.kafka.listener.DefaultErrorHandler conversationKafkaErrorHandler(
      @org.springframework.beans.factory.annotation.Qualifier("notificationDltKafkaTemplate")
          KafkaTemplate<String, String> kafka) {
    var recoverer =
        new org.springframework.kafka.listener.DeadLetterPublishingRecoverer(
            kafka,
            (record, exception) ->
                new org.apache.kafka.common.TopicPartition(
                    "community.conversation-created.v1.dlt", record.partition()));
    // DLT 실패는 recovery 실패로 전파하고 원본 offset을 보존합니다. 예외 메시지·stack·payload는 header에 복제하지 않습니다.
    recoverer.setFailIfSendResultIsError(true);
    recoverer.setVerifyPartition(false);
    recoverer.setWaitForSendResultTimeout(java.time.Duration.ofSeconds(10));
    recoverer.setTimeoutBuffer(0);
    recoverer.setLogRecoveryRecord(false);
    recoverer.setExceptionHeadersCreator(
        (headers, exception, key, names) ->
            headers.add(
                "delivery-failure-class",
                org.springframework.core.NestedExceptionUtils.getMostSpecificCause(exception)
                    .getClass()
                    .getName()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    var backoff = new org.springframework.kafka.support.ExponentialBackOffWithMaxRetries(3);
    backoff.setInitialInterval(1000);
    backoff.setMultiplier(2);
    backoff.setMaxInterval(4000);
    var handler = new org.springframework.kafka.listener.DefaultErrorHandler(recoverer, backoff);
    handler.setClassifications(
        Map.of(
            org.springframework.dao.TransientDataAccessException.class,
            true,
            org.springframework.jdbc.CannotGetJdbcConnectionException.class,
            true),
        false);
    handler.setCommitRecovered(true);
    // Framework record formatting에는 payload가 포함되므로 일반 운영 log에서는 출력하지 않습니다.
    handler.setLogLevel(org.springframework.kafka.KafkaException.Level.TRACE);
    return handler;
  }

  @Bean
  @ConditionalOnProperty(name = "carmunity.kafka.relay-enabled", havingValue = "true")
  KafkaTemplate<String, String> communityKafkaTemplate(
      @org.springframework.beans.factory.annotation.Qualifier("communityKafkaProducerFactory")
          DefaultKafkaProducerFactory<String, String> factory) {
    return kafkaTemplate(factory);
  }

  private DefaultKafkaProducerFactory<String, String> producerFactory(
      KafkaProperties properties, Environment env, String role) {
    Map<String, Object> config = roleProperties(properties.buildProducerProperties(), env, role);
    validateRole(config, env, role);
    config.put("key.serializer", StringSerializer.class);
    config.put("value.serializer", StringSerializer.class);
    config.put("acks", "all");
    config.put("enable.idempotence", true);
    config.put("max.block.ms", 1000);
    config.put("delivery.timeout.ms", 10000);
    config.put("request.timeout.ms", 5000);
    config.put("linger.ms", 0);
    return new DefaultKafkaProducerFactory<>(config);
  }

  @Bean
  @ConditionalOnProperty(name = "carmunity.kafka.consumer-enabled", havingValue = "true")
  ConcurrentKafkaListenerContainerFactory<String, String> notificationKafkaListenerFactory(
      KafkaProperties properties,
      Environment env,
      org.springframework.kafka.listener.DefaultErrorHandler conversationKafkaErrorHandler) {
    Map<String, Object> config =
        roleProperties(properties.buildConsumerProperties(), env, "consumer");
    validateRole(config, env, "consumer");
    config.put("key.deserializer", StringDeserializer.class);
    config.put("value.deserializer", StringDeserializer.class);
    config.put("enable.auto.commit", false);
    config.put("auto.offset.reset", "earliest");
    config.put("allow.auto.create.topics", false);
    config.put("max.poll.records", 10);
    // 한 poll의 10개 record에 DB 연결 대기·transaction·1/2/4초 retry·DLT ack 대기 여유를 둡니다.
    config.put("max.poll.interval.ms", 1800000);
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
    factory.setConcurrency(1);
    factory.setCommonErrorHandler(conversationKafkaErrorHandler);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    return factory;
  }

  private Map<String, Object> roleProperties(
      Map<String, Object> defaults, Environment env, String role) {
    Map<String, Object> config = new HashMap<>(defaults);
    config.putAll(
        Binder.get(env)
            .bind(
                "carmunity.kafka." + role + ".properties",
                Bindable.mapOf(String.class, String.class))
            .orElse(Map.of()));
    return config;
  }

  private KafkaTemplate<String, String> kafkaTemplate(
      DefaultKafkaProducerFactory<String, String> factory) {
    KafkaTemplate<String, String> template = new KafkaTemplate<>(factory);
    template.setProducerListener(
        new org.springframework.kafka.support.ProducerListener<>() {
          @Override
          public void onError(
              org.apache.kafka.clients.producer.ProducerRecord<String, String> record,
              org.apache.kafka.clients.producer.RecordMetadata metadata,
              Exception exception) {
            org.slf4j.LoggerFactory.getLogger(KafkaDeliveryConfiguration.class)
                .warn(
                    "Kafka send 실패 topic={} partition={} failure={}",
                    record.topic(),
                    record.partition(),
                    org.springframework.core.NestedExceptionUtils.getMostSpecificCause(exception)
                        .getClass()
                        .getSimpleName());
          }
        });
    return template;
  }

  private void validateRole(Map<String, Object> config, Environment env, String role) {
    Object servers = env.getProperty("spring.kafka.bootstrap-servers");
    if (servers == null || servers.toString().replace("[", "").replace("]", "").isBlank()) {
      throw new IllegalStateException("활성 Kafka role에 bootstrap server가 없습니다: " + role);
    }
    if (!java.util.Arrays.asList(env.getActiveProfiles()).contains("production")) return;
    for (String key :
        java.util.List.of(
            "security.protocol",
            "ssl.truststore.location",
            "ssl.keystore.location",
            "ssl.keystore.password")) {
      Object value = config.get(key);
      if (value == null
          || value.toString().isBlank()
          || (key.equals("security.protocol") && !value.equals("SSL"))) {
        throw new IllegalStateException(
            "production Kafka role의 mTLS 설정이 없습니다: " + role + "/" + key);
      }
    }
  }
}
