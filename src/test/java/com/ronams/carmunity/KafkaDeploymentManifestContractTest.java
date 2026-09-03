package com.ronams.carmunity;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class KafkaDeploymentManifestContractTest {
  private static String render(String path) throws Exception {
    var process =
        new ProcessBuilder("kubectl", "kustomize", path).redirectErrorStream(true).start();
    String output = new String(process.getInputStream().readAllBytes());
    assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue()).withFailMessage(output).isZero();
    return output;
  }

  @Test
  void pinsOfficialOperatorAndKeepsClusterInstallationOutOfArgoProject() throws Exception {
    String install = render("scripts/bootstrap/kafka/manifests");
    assertThat(install)
        .contains(
            "quay.io/strimzi/operator@sha256:77f8fa8121a67561c3418de985783d197f51b8931e9a47f793dc0437dc6bb21f",
            "name: JAVA_OPTS",
            "value: -Xms128m -Xmx256m",
            "ephemeral-storage: 32Mi",
            "ephemeral-storage: 256Mi",
            "quay.io/strimzi/kafka@sha256:47ea0bd5cd416a92cbcfa248ae4b4b01a1e5d6b87aeafa67a691abdc29e20d0d");
    String project =
        Files.readString(
            Path.of("scripts/bootstrap/gitops/manifests/car-community-appproject.yaml"));
    assertThat(project)
        .contains("clusterResourceWhitelist: []", "kind: KafkaNodePool", "kind: KafkaUser")
        .doesNotContain("kind: Secret");
  }

  @Test
  void rendersBoundedTlsOnlySingleNodeResourcesButDoesNotActivateThem() throws Exception {
    String kafka = render("deploy/kafka");
    String cluster = Files.readString(Path.of("deploy/kafka/cluster.yaml"));
    assertThat(cluster)
        .contains("name: tls", "type: internal", "tls: true", "authentication: {type: tls}")
        .doesNotContain(
            "type: external",
            "type: nodeport",
            "type: loadbalancer",
            "type: route",
            "type: ingress",
            "tls: false");
    assertThat(kafka)
        .contains(
            "version: 4.2.1",
            "metadataVersion: 4.2-IV1",
            "type: internal",
            "authentication:",
            "type: tls",
            "partitions: 3",
            "replicas: 1",
            "retention.bytes: 67108864",
            "sizeLimit: 2Gi",
            "ephemeral-storage: 512Mi",
            "ephemeral-storage: 3Gi",
            "-Xms: 64m",
            "-Xmx: 128m",
            "ephemeral-storage: 32Mi",
            "ephemeral-storage: 256Mi");
    assertThat(kafka).doesNotContain("tls: false", "type: nodeport", "name: \"*\"", "kind: Secret");
    assertThat(kafka)
        .contains(
            "name: community-relay",
            "name: notification-consumer",
            "name: notification-dlt-writer",
            "name: community-replay",
            "operations:",
            "IdempotentWrite");
    assertThat(kafka)
        .contains(
            "name: community.conversation-created.v1",
            "name: community.conversation-created.v1.dlt",
            "name: notification.community-conversation.v1",
            "app.kubernetes.io/name: car-community",
            "port: 9093");
    String users = Files.readString(Path.of("deploy/kafka/users.yaml"));
    assertThat(contractLines(users, "name: "))
        .containsExactly(
            "name: community-relay",
            "name: notification-consumer",
            "name: notification-dlt-writer",
            "name: community-replay");
    assertThat(contractLines(users, "- resource:"))
        .containsExactly(
            "- resource: {type: topic, name: community.conversation-created.v1, patternType: literal}",
            "- resource: {type: cluster, patternType: literal}",
            "- resource: {type: topic, name: community.conversation-created.v1, patternType: literal}",
            "- resource: {type: group, name: notification.community-conversation.v1, patternType: literal}",
            "- resource: {type: topic, name: community.conversation-created.v1.dlt, patternType: literal}",
            "- resource: {type: cluster, patternType: literal}",
            "- resource: {type: topic, name: community.conversation-created.v1, patternType: literal}",
            "- resource: {type: cluster, patternType: literal}");
    assertThat(contractLines(users, "operations:"))
        .containsExactly(
            "operations: [Write, Describe]",
            "operations: [IdempotentWrite]",
            "operations: [Read, Describe]",
            "operations: [Read, Describe]",
            "operations: [Write, Describe]",
            "operations: [IdempotentWrite]",
            "operations: [Write, Describe]",
            "operations: [IdempotentWrite]");
    assertThat(users)
        .doesNotContain(
            "patternType: prefix",
            "patternType: any",
            "name: *",
            "operations: [All]",
            "operations: [Alter]",
            "operations: [Create]",
            "operations: [Delete]");
    assertThat(
            java.nio.file.Files.readString(
                java.nio.file.Path.of("deploy/kafka/application-kafka-patch.yaml")))
        .doesNotContain("community-replay", "KAFKA_REPLAY");
    String dryRun = Files.readString(Path.of("deploy/kafka/operation-job-template.json"));
    assertThat(dryRun)
        .doesNotContain(
            "community-replay",
            "KAFKA_REPLAY_KEYSTORE_PATH",
            "AUTH_JWT_HMAC_SECRET",
            "AUTH_LOGIN_IDENTIFIER_HMAC_SECRET");
    String approvedReplay =
        Files.readString(Path.of("deploy/kafka/operation-replay-job-template.json"));
    assertThat(approvedReplay)
        .contains("community-replay", "KAFKA_REPLAY_KEYSTORE_PATH")
        .doesNotContain("AUTH_JWT_HMAC_SECRET", "AUTH_LOGIN_IDENTIFIER_HMAC_SECRET");
    assertThat(Files.readString(Path.of("deploy/kafka/application-kafka-shadow-patch.yaml")))
        .contains(
            "KAFKA_RELAY_ENABLED",
            "KAFKA_CONSUMER_ENABLED",
            "KAFKA_CLEANUP_ENABLED",
            "KAFKA_SYNC_NOTIFICATIONS_ENABLED");
    assertThat(render("deploy/overlays/stg"))
        .doesNotContain("kind: Kafka\n", "kind: KafkaTopic\n", "kind: KafkaUser\n");
  }

  private static java.util.List<String> contractLines(String source, String prefix) {
    return source.lines().map(String::trim).filter(line -> line.startsWith(prefix)).toList();
  }
}
