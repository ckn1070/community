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
        .contains("replicas: 1", "type: RollingUpdate", "maxSurge: 1", "maxUnavailable: 0")
        .contains(
            "serviceAccountName: car-community-application",
            "audience: sts.amazonaws.com",
            "expirationSeconds: 3600",
            "path: token")
        .contains("name: COMMUNITY_TRUSTED_PROXY_CIDRS")
        .doesNotContain("kind: HorizontalPodAutoscaler");
  }

  @Test
  void capabilityRemainsOffInBaseManifest() throws Exception {
    String deployment = Files.readString(Path.of("deploy/base/deployment.yaml"));
    assertThat(deployment).contains("name: COMMUNITY_ENABLED", "value: \"false\"");
  }

  @Test
  void stagingOverlayEnablesCommunityCapability() throws Exception {
    String deploymentPatch =
        Files.readString(Path.of("deploy/overlays/stg/deployment-profile-patch.yaml"));
    String kustomization = Files.readString(Path.of("deploy/overlays/stg/kustomization.yaml"));

    assertThat(deploymentPatch)
        .contains("- name: COMMUNITY_ENABLED\n              value: \"true\"");
    assertThat(kustomization).contains("path: deployment-profile-patch.yaml");
  }
}
