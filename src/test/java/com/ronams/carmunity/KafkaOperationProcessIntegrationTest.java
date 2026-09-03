package com.ronams.carmunity;

import static org.assertj.core.api.Assertions.*;

import com.ronams.carmunity.support.PostgreSqlIntegrationTestSupport;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 실제 main process가 HTTP·일반 worker 없이 dry-run하고 종료하는 경계입니다. */
@SpringBootTest
class KafkaOperationProcessIntegrationTest extends PostgreSqlIntegrationTestSupport {
  @Autowired JdbcClient jdbc;

  @Test
  void dryRunNeedsNoBrokerAndCannotActivateNormalWorkers() throws Exception {
    UUID event = UUID.randomUUID();
    jdbc.sql(
            "INSERT INTO community_event_outbox(event_id,event_type,event_version,post_id,conversation_id,payload,occurred_at) VALUES(:event,'CommunityConversationCreated',1,:post,:conversation,'{}',NOW())")
        .param("event", event)
        .param("post", UUID.randomUUID())
        .param("conversation", UUID.randomUUID())
        .update();
    Path output = Files.createTempFile("kafka-operation-process-", ".log");
    output.toFile().deleteOnExit();
    var args =
        new ArrayList<>(
            List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp",
                System.getProperty("java.class.path"),
                CarmunityApplication.class.getName(),
                "--command=kafka-events",
                "--mode=replay",
                "--reason=복구 대상 확인",
                "--event-ids=" + event,
                "--carmunity.kafka.relay-enabled=true",
                "--carmunity.kafka.consumer-enabled=true",
                "--carmunity.kafka.cleanup-enabled=true",
                "--carmunity.community.enabled=true",
                "--spring.main.web-application-type=servlet"));
    var builder =
        new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(output.toFile());
    var env = builder.environment();
    env.put("DB_URL", postgresJdbcUrl());
    env.put("DB_USERNAME", postgresUsername());
    env.put("DB_PASSWORD", postgresPassword());
    env.put("SPRING_PROFILES_ACTIVE", "production");
    env.put("KAFKA_BOOTSTRAP_SERVERS", "");
    var process = builder.start();
    boolean finished = process.waitFor(60, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(10, TimeUnit.SECONDS);
    }
    String text = Files.readString(output);
    assertThat(finished).withFailMessage(text).isTrue();
    assertThat(process.exitValue()).withFailMessage(text).isZero();
    assertThat(text)
        .contains("pending=1")
        .doesNotContain(
            "Tomcat started",
            "Started NetworkTrafficServerConnector",
            "AUTH_JWT_HMAC_SECRET",
            "AUTH_LOGIN_IDENTIFIER_HMAC_SECRET");
    assertThat(
            jdbc.sql("SELECT publish_attempts FROM community_event_outbox WHERE event_id=:id")
                .param("id", event)
                .query(Integer.class)
                .single())
        .isZero();
  }
}
