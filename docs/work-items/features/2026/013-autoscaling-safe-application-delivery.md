# Staging application GitOps delivery

## 상태

Done

## 결과

Argo CD가 `main`의 `deploy/overlays/stg`를 full sync하면 다음 결과가 순서대로 완성되는
staging application delivery를 구축했습니다.

```text
최초 application bootstrap
  -> car-community Namespace와 Git에 둘 수 없는 Secret 생성

Argo CD full sync
  -> PreSync -2: Git의 DB URL·RDS CA ConfigMap
  -> PreSync -1: 단일 database migration Job
       -> Flyway migration
       -> migration 성공 뒤 runtime 최소 권한 동기화
  -> Sync: runtime notification ConfigMap·Deployment·Service
  -> Application Synced·Healthy, runtime Pod Ready
```

Runtime Pod는 `carmunity_runtime`만 사용하고 Flyway를 실행하지 않습니다. Schema 변경과
runtime 권한 동기화는 같은 immutable image를 한 번 실행하는 Migration Job만 담당합니다.
Actual Secret value는 Git 밖에서 application bootstrap·operation script로 적용하고, 공개
설정과 Kubernetes manifest는 Git과 Argo CD가 소유합니다.

Repository source와 live staging 적용을 모두 완료했습니다. 실제 RDS endpoint,
notification public value와 ECR exact digest를 반영했고 다음 상태를 live에서 확인했습니다.

- Runtime·migrator DB role과 최소 권한
- `car-community` Namespace와 네 application Secret
- Target API server-side dry-run
- Read-only GitHub App repository 연결
- ConfigMap·Migration Job·Deployment full sync 순서
- Application `Synced`·`Healthy`, Deployment rollout과 runtime Pod Ready
- Runtime Pod의 private ECR exact digest imageID

환경별 exact revision·resource와 실행 결과는
[100 Staging 단일 node 전체 구축 시나리오](../../../operations/scenarios/100-staging-single-node-bootstrap.md)가
소유합니다.

- 전체 완료 여부: 예
- 남은 필수 책임: 없음
- 선택 후속: 대표 API→RDS→실제 인증 메일 경로는
  [430 Application 기능 qualification](../../../operations/430-application-functional-qualification-runbook.md)으로
  점검할 수 있으며, 미실행은 이 delivery 결과를 미완료로 되돌리지 않습니다.

## 보존 범위

- Runtime·migrator DB identity와 최초 권한 구성
- Namespace와 actual Secret의 Git 외부 bootstrap
- Git-managed DB URL·RDS CA·notification 공개 설정
- 단일 PreSync Migration Job과 Flyway `afterMigrate` runtime 권한 동기화
- Flyway를 비활성화한 runtime Deployment와 Service
- Private repository AppProject·Application과 automated full sync
- Notification Secret value 교체 뒤 rollout·Ready 확인
- Repository contract test, Kustomize render와 target API server-side dry-run

## 확정 결정 색인

### Database와 migration

- `carmunity_runtime`은 application table DML과 identity sequence만 사용하고
  `flyway_schema_history` direct privilege를 갖지 않습니다.
- `carmunity_migrator`를 사용하는 단일 PreSync Job만 Flyway를 실행합니다.
- Runtime 권한 SQL은 별도 Kubernetes Job·ConfigMap이 아니라 Migration Job의
  `afterMigrate` callback으로 수렴합니다. Migration이 없는 재실행에서도 callback을
  적용해 이전 권한 단계 실패를 복구합니다.

### ConfigMap과 Secret

- DB URL·RDS CA는 Secret이 아니므로 Git-managed PreSync ConfigMap으로 관리합니다.
- Notification 공개 설정은 content hash가 Deployment rollout을 만드는 Sync ConfigMap으로
  관리합니다.
- Actual Secret value는 mode `0600` local `.env`에서 허용된 값만 NUL-delimited SSH stdin으로
  EC2 bootstrap·operation script에 전달하며 Git·CLI argument·log·EC2 영구 file에 남기지
  않습니다.
- External Secrets·Secrets Manager application 연동 전까지 application `200`이 네 Secret을
  최초 생성하고, notification key 교체는 전용 operation이 새 Pod rollout·Ready를 확인합니다.

### Workload와 GitOps

- Base는 Deployment, 단일 Migration Job, Service와 Kustomization만 소유합니다.
- Deployment는 runtime DB credential과 notification runtime 설정만 소비하며 migrator
  credential을 받지 않습니다. Migration Job은 notification credential과 runtime password를
  받지 않습니다.
- Manifest에는 현재 검증된 ARM64 scheduling, non-root·capability drop·read-only filesystem,
  RDS CA mount와 readiness만 명시합니다.
- AppProject는 exact repository·namespace와 현재 resource kind만 허용하고 Secret과
  cluster-scoped resource를 허용하지 않습니다.
- Application은 automated sync, prune, self-heal과 `allowEmpty: false`를 유지합니다.

## 제외 범위

- Ingress·ALB·HTTPS·DNS는 독립된
  [014 Staging 외부 HTTPS ingress](014-staging-external-https-ingress.md)에서 완료했습니다.
- Autoscaling, Secret 공급·암호화, Production, credential rotation과 delivery 자동화는
  [004 Infrastructure 진화 Backlog](../../backlog/2026/004-infrastructure-evolution.md)가
  후속 후보로 소유합니다.
- 대표 API·RDS write·notification email 기능 qualification은 선택 운영 절차입니다.

기능 qualification은 기술 delivery와 다른 선택 절차입니다. 실행할 때는 `202` 응답만
provider 성공으로 간주하지 않고
[430 Application 기능 qualification](../../../operations/430-application-functional-qualification-runbook.md)의
실제 수신·token confirmation·DB `ACTIVE`·test data cleanup까지 확인합니다. 기존과 다른 old
provider key가 실제로 있을 때만
[540 Notification credential 변경](../../../operations/540-application-notification-credential-runbook.md)의
새 key 검증·폐기 절차를 적용합니다.

## 영구 SOT

- [140 RDS PostgreSQL](../../../project/infrastructure/140-rds-postgresql.md)
- [160 Argo CD platform](../../../project/infrastructure/160-argocd-platform.md)
- [220 GitOps application delivery](../../../project/infrastructure/220-gitops-application-delivery.md)
- [100 Infrastructure overview](../../../project/infrastructure/100-infrastructure-overview.md)
- [240 Flyway](../../../tech-stack/240-flyway.md)

## 운영 Procedure

- [350 Application delivery 최초 bootstrap](../../../operations/350-application-delivery-bootstrap-runbook.md)
- [420 GitOps 배포·rollback](../../../operations/420-gitops-deployment-and-rollback-runbook.md)
- [430 Application 기능 qualification](../../../operations/430-application-functional-qualification-runbook.md)
- [540 Notification credential 변경](../../../operations/540-application-notification-credential-runbook.md)

## 관련 문서

- **작업 목록**: [000 2026 기능 작업 문서](000-index.md)
- **로컬 Secret source**: [100 로컬 개발 환경](../../../development/100-local-development.md)
- **Application bootstrap interface**: [Application bootstrap README](../../../../scripts/bootstrap/application/README.md)
- **Application operation interface**: [Application operations README](../../../../scripts/operations/application/README.md)
- **현재 staging live 상태**:
  [100 Staging 단일 node 전체 구축 시나리오](../../../operations/scenarios/100-staging-single-node-bootstrap.md)
