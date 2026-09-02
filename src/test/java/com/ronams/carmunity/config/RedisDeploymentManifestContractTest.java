package com.ronams.carmunity.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RedisDeploymentManifestContractTest {

  @Test
  void pinsEphemeralSingleArm64RedisWithBoundedResourcesAndAuthenticatedProbe() throws Exception {
    String deployment = Files.readString(Path.of("deploy/base/catalog-redis-deployment.yaml"));

    assertThat(deployment)
        .contains(
            "replicas: 1",
            "type: Recreate",
            "redis:8.2.9-alpine3.22@sha256:77dcf738f8eba3ca2c7a26a5a1756e1259a7eca7451234f766eca3bc4a46790e",
            "kubernetes.io/arch: arm64",
            "--appendonly",
            "--maxmemory",
            "384mb",
            "--maxmemory-policy",
            "allkeys-lfu",
            "emptyDir: {}",
            "runAsNonRoot: true",
            "readOnlyRootFilesystem: true",
            "REDISCLI_AUTH",
            "--user",
            "PING")
        .doesNotContain("hostPath:", "PersistentVolumeClaim", "requirepass");
  }

  @Test
  void exposesOnlyClusterIpAndAllowsOnlyApplicationTraffic() throws Exception {
    String service = Files.readString(Path.of("deploy/base/catalog-redis-service.yaml"));
    String networkPolicy =
        Files.readString(Path.of("deploy/base/catalog-redis-network-policy.yaml"));

    assertThat(service)
        .contains("type: ClusterIP", "port: 6379")
        .doesNotContain("NodePort", "LoadBalancer");
    assertThat(networkPolicy)
        .contains(
            "kind: NetworkPolicy",
            "app.kubernetes.io/component: catalog-cache",
            "app.kubernetes.io/component: api",
            "policyTypes:",
            "Ingress",
            "Egress");
  }

  @Test
  void applicationUsesOnlyItsExternalSecretAndCacheRemainsOffByDefault() throws Exception {
    String deployment = Files.readString(Path.of("deploy/base/deployment.yaml"));

    assertThat(deployment)
        .contains(
            "name: REDIS_HOST",
            "value: car-community-catalog-redis",
            "name: REDIS_USERNAME",
            "name: REDIS_PASSWORD",
            "name: car-community-catalog-redis-application",
            "name: CATALOG_MODEL_DETAIL_CACHE_ENABLED",
            "value: \"false\"")
        .doesNotContain("car-community-catalog-redis-server");
  }

  @Test
  void appProjectAllowsOnlyTheNewNetworkPolicyKind() throws Exception {
    String project =
        Files.readString(
            Path.of("scripts/bootstrap/gitops/manifests/car-community-appproject.yaml"));

    assertThat(project).contains("group: networking.k8s.io\n      kind: NetworkPolicy");
    assertThat(project).doesNotContain("kind: Secret", "kind: \"*\"");
  }
}
