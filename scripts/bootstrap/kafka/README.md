# Kafka bootstrap artifact

`manifests/`는 Strimzi 1.2.0 공식 release archive의 `install/cluster-operator`를 보존합니다.
출처는 `https://github.com/strimzi/strimzi-kafka-operator/releases/tag/1.2.0`이며 설치 caller와
live 검증은 [380 Kafka lifecycle](../../../docs/operations/380-kafka-lifecycle-runbook.md)이
소유합니다. Namespace는 `car-community`로 고정하고 Cluster Operator와 Kafka 4.2.1 image는
확정 digest로 고정했습니다.

Version·CRD·RBAC·image mapping은 하나의 release 단위입니다. Upgrade할 때는 새 공식 archive를
별도 후보로 가져와 전체 manifest diff, Kubernetes 호환성, CR conversion, ARM64 image,
server-side dry-run과 rollback 경로를 검증합니다. 기존 파일 일부만 섞어 교체하지 않습니다.
제거는 namespace CR과 PostgreSQL 원본 보존을 먼저 확인한 뒤 operator와 cluster-scoped CRD를
별도 승인으로 처리합니다.
