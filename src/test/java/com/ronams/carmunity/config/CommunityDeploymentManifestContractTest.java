package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CommunityDeploymentManifestContractTest {
  @Test
  void deploymentPinsSingleReplicaRollingUpdateAndProjectedStsIdentity() throws Exception {
    String deployment = Files.readString(Path.of("deploy/base/deployment.yaml"));
    assertThat(deployment)
        .contains("replicas: 1", "type: RollingUpdate", "maxSurge: 1", "maxUnavailable: 0");
    assertThat(deployment)
        .contains(
            "serviceAccountName: car-community-application",
            "audience: sts.amazonaws.com",
            "expirationSeconds: 3600",
            "path: token");
    assertThat(deployment).contains("name: COMMUNITY_TRUSTED_PROXY_CIDRS");
    assertThat(deployment).doesNotContain("kind: HorizontalPodAutoscaler");
  }

  @Test
  void capabilityRemainsOffInBaseManifest() throws Exception {
    String deployment = Files.readString(Path.of("deploy/base/deployment.yaml"));
    assertThat(deployment).contains("name: COMMUNITY_ENABLED", "value: \"false\"");
  }
}
