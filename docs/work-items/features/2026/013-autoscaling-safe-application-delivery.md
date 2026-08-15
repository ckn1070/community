# Staging application GitOps delivery

## 상태

In Progress

## 최종 목표

Argo CD가 `main`의 `deploy/overlays/stg`를 full sync하면 다음 결과가 순서대로 완성되는
staging application delivery를 구축합니다.

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
External Secret system 없이 local `.env`를 사용하는 현재 단계에서는 actual Secret 값만
Git 밖에서 bootstrap·operation script로 적용하고, 공개 설정과 Kubernetes manifest는 Git과
Argo CD가 소유합니다.

Repository source 구현과 live staging 적용은 별도 결과입니다. 이 작업은 두 결과를 모두
완료해야 끝나며, 실제 RDS endpoint·notification public value·GitHub App credential처럼
repository에서 결정할 수 없는 값은 추측하지 않고 live 적용 전제에 남깁니다.

## 사용자 요구와 고정 제약

- 오늘 진행한 application 설정·Secret 생애주기 작업과
  `feature/autoscaling-safe-application-delivery` 결과를 하나의 delivery 결과로 다룹니다.
- 최종 consumer는 Argo CD가 적용하는 staging application입니다. Script나 manifest의 존재만
  완료로 판정하지 않습니다.
- 필요한 기능은 빠뜨리지 않되 현재 consumer·복구 경계가 없는 미래 기능과 방어 자동화는
  선반영하지 않습니다.
- Local `.env`의 actual value는 Git, CLI argument, log, EC2 영구 file과 Pod mount에 남기지
  않습니다. External Secrets·Secrets Manager 연동은 이번 범위가 아닙니다.
- Kubernetes Secret encryption-at-rest retrofit과 HPA·Cluster Autoscaler 설치는 제외합니다.
- 아직 원격에 공유하지 않은 과잉 delivery commit은 필요한 결과를 보존한 최소 commit
  구조로 교체할 수 있습니다.

## 전체 작업 전수조사

### 최종 consumer까지 필요한 결과

| 결과 | Producer | Consumer | 판정 |
| --- | --- | --- | --- |
| Runtime·migrator DB identity | `bootstrap/application/100` | Migration Job·runtime Deployment | 유지. 두 role과 최소 schema 권한은 application identity 분리에 필요 |
| Namespace와 actual Secret 최초 생성 | `bootstrap/application/200` | Argo destination·Job·Deployment | 유지. External Secret system이 없으므로 local `.env`와 Kubernetes API 사이의 trusted bridge가 필요 |
| 공개 DB URL·RDS CA | Staging Kustomize PreSync ConfigMap | Migration Job·Deployment | 유지. Secret이 아니며 Git에서 review할 수 있고 Job보다 먼저 적용 가능 |
| Schema migration | 단일 PreSync Job | Runtime image가 기대하는 DB schema | 유지. Runtime Pod와 autoscaling 생애주기에서 분리해야 함 |
| Runtime table·sequence 권한 | Flyway `afterMigrate` callback | `carmunity_runtime` | 결과 유지, 구현 축소. 별도 Job 없이 migration 성공 뒤 같은 process에서 수렴 |
| Runtime workload | Deployment·Service | Argo CD·후속 in-cluster client | 유지. Flyway off, runtime credential, readiness와 최소 보안 경계 필요 |
| Notification 공개 설정 | Staging Kustomize ConfigMap | Runtime Deployment만 | 유지. Git-managed public configuration이며 content 변경이 Pod template을 바꿈 |
| Resend actual key | Notification Secret | Runtime Deployment만 | 유지. 최초 bootstrap과 구축 뒤 value 교체 경로가 모두 필요 |
| Secret value 교체 반영 | Application operation | 새 runtime Pod | 유지. Environment variable은 기존 Pod에서 갱신되지 않으므로 rollout·Ready 확인 필요 |
| Private repository 연결 | GitOps bootstrap | Argo CD Application | 유지. Exact repository·path·destination·resource allow-list가 필요 |
| Repository·manifest 검증 | Contract test·Gradle process test·Kustomize·API dry-run | Merge·live apply 판단 | 유지하되 실제 위험만 검증 |

### 제거하거나 축소할 과잉 구조

| 현재 내용 | 처분 | 이유 |
| --- | --- | --- |
| `grant-runtime.sql`을 ConfigMap으로 운반 | 제거 | SQL은 application migration artifact이며 별도 Kubernetes 전달물이 아님 |
| `migration-permissions-job.yaml` | 제거 | 같은 image의 두 번째 Spring process 없이 Migration Job callback으로 동일 결과를 만들 수 있음 |
| Permission Job 전용 volume·mount·wave | 제거 | 두 번째 Job 제거에 따라 consumer가 없음 |
| Process test의 두 번째 permission JVM | 제거 | 단일 실제 Migration process의 종료·DB 결과만 검증하면 됨 |
| Deployment의 고정 `replicas` | 제거 | 현재 HPA가 없고 Kubernetes 기본 1 replica로 충분함. 미래 replica owner를 미리 고정하지 않음 |
| Argo Application의 `/spec/replicas` ignore | 제거 | 현재 HPA가 없고 manifest도 replicas를 소유하지 않으므로 선반영 불필요 |
| 검증되지 않은 CPU·memory request/limit | 제거 | 실제 2 GiB node 측정 없이 수치를 확정하지 않음 |
| 검증되지 않은 rollout 수치·termination grace | 제거 | 기본 동작으로 목표가 충족되며 현재 측정 근거가 없음 |
| Startup·liveness probe | 제거 | 현재 완료 조건에는 readiness가 직접 필요하며 restart 정책은 live 관찰 뒤 추가 |
| GitOps bootstrap의 Secret key schema 전수 검사 | 축소 | Destination Namespace owner만 선검증하고, 누락 Secret은 Pod 생성 경계가 안전하게 차단함 |
| 대형 custom manifest parser | 두지 않음 | Kustomize render와 target API server-side dry-run이 syntax·schema를 검증함 |

### 누락돼 보완할 결과

| 누락 | 필요한 보완 | 완료 증거 |
| --- | --- | --- |
| 단일 migration과 권한 동기화 결속 | Migration Job에서만 Flyway callback location 활성화 | 실제 별도 JVM이 migration·권한을 끝내고 exit 0 |
| Callback 실패 뒤 재실행 | `afterMigrateApplied`가 아니라 migration 0건에도 실행되는 `afterMigrate` 사용 | 같은 DB에 재실행해 권한 상태 유지 |
| Runtime 최소 권한 | Table DML·sequence `USAGE`만 grant하고 Flyway history는 revoke | Runtime query 성공, history query 실패 |
| Minimal GitOps prerequisite | Namespace owner만 repository 연결 전에 확인 | Secret schema와 GitOps 연결 script가 분리됨 |
| Staging 공개 notification 값의 권위 | 확인되지 않은 domain·sender를 정상값으로 단정하지 않음 | Live 적용 전에 approved 값으로 교체·review |
| Full sync 실제 검증 | Selective sync 없이 hook부터 runtime까지 관찰 | Fresh revision의 Synced·Healthy·Pod Ready |
| Notification key 실제 소비 | Secret 적용 뒤 새 Pod generation과 대표 email 기능 확인 | New key smoke 뒤 old provider key revoke |

## 최종 구조

### Database와 migration

`100-provision-database-access.sh`는 최초 구축 때 다음 결과만 만듭니다.

- Ubuntu 24.04 official APT의 `postgresql-client-16` 설치와 PostgreSQL 16 client 검증
- `carmunity_runtime`: application DML용 login role
- `carmunity_migrator`: Flyway DDL용 login role
- 두 role의 database connect와 public schema 최소 권한

Role별 존재 여부를 확인해 없는 role만 생성하고 기존 role과 password는 유지합니다. 매
실행에서 idempotent한 최소 권한을 적용하고 두 role의 존재를 확인하므로 중단 뒤 같은 입력으로
재실행할 수 있습니다. 기존 role의 속성·membership·ownership·password와 전체 권한을 감사하거나
자동 교정하는 운영 도구로 확장하지 않습니다.
Role 이름은 `carmunity_runtime`, `carmunity_migrator`로 script에 고정하므로 입력으로 다시
받지 않습니다.

Migration Job은 `carmunity_migrator` credential로 같은 application image를 non-web process로
실행합니다. Job argument에서만 `classpath:db/callback`을 Flyway callback location으로
활성화합니다. 따라서 local application과 runtime Deployment는 runtime 권한 callback을
실행하지 않습니다.

Callback은 `afterMigrate`를 사용합니다. 새 version이 없는 재실행에서도 권한 SQL을 다시
적용해야 이전 callback 실패를 복구할 수 있기 때문입니다. Callback은 migration 성공 뒤
실행되며 다음 결과만 idempotent하게 맞춥니다.

- `public` schema `USAGE`
- 현재 table의 `SELECT`, `INSERT`, `UPDATE`, `DELETE`
- 현재 sequence의 `USAGE`
- `flyway_schema_history` direct privilege 제거

Callback 실패는 Job 실패이므로 Argo CD는 runtime Deployment를 진행하지 않습니다. 이미
commit된 migration 자체를 되돌린다고 주장하지 않으며, 원인을 복구한 full sync가
`afterMigrate`를 다시 실행해 권한 단계부터 수렴합니다.

### ConfigMap

| ConfigMap | Owner | Phase | Consumer |
| --- | --- | --- | --- |
| `car-community-config` | Staging Git/Kustomize | PreSync `-2` | Migration Job·Deployment |
| `car-community-rds-ca` | Staging Git/Kustomize | PreSync `-2` | Migration Job·Deployment volume |
| `car-community-notification-config` | Staging Git/Kustomize | Sync | Deployment `envFrom` |

DB URL과 RDS CA는 Secret이 아닙니다. Argo CD hook annotation이 있는 Git ConfigMap을 Job보다
앞선 wave에 적용할 수 있으므로 Shell bootstrap이 만들 이유가 없습니다. RDS TLS
`verify-full`, mounted CA path와 pinned Seoul bundle 검증은 유지합니다.

Notification 공개 설정은 Deployment만 소비합니다. Content hash가 Deployment의 ConfigMap
reference를 바꿔 Git 변경 자체가 새 rollout을 만들도록 합니다. 실제 staging sender와
frontend URL이 확정되기 전에는 guessed production 값을 완료값으로 취급하지 않습니다.

### Secret과 `.env`

Actual Secret value의 전달 경로는 다음과 같습니다.

```text
Local Mac의 mode 0600 .env
  -> caller가 approved key만 선택
  -> sanitized SSH의 NUL-delimited stdin
  -> EC2 root entrypoint의 process memory
  -> Kubernetes Secret API
```

최초 `200-apply-application-prerequisites.sh`는 Namespace와 현재 schema의 Secret 네 개를
생성합니다.

| Secret | Key | Consumer |
| --- | --- | --- |
| `car-community-db-runtime` | `DB_USERNAME`, `DB_PASSWORD` | Deployment |
| `car-community-db-migrator` | `DB_MIGRATOR_USERNAME`, `DB_MIGRATOR_PASSWORD` | Migration Job |
| `car-community-auth` | 두 auth HMAC | Migration Job 기동·Deployment |
| `car-community-notification` | `RESEND_API_KEY` | Deployment |

`DB_USERNAME`과 `DB_MIGRATOR_USERNAME` 값은 bootstrap script의 `carmunity_runtime`,
`carmunity_migrator` 고정 상수에서 만듭니다. Caller는 두 username을 `.env`와
stdin으로 중복 전달하지 않고 password 두 개와 application Secret 값만 전달합니다.

구축 뒤 `reconcile-notification-secret.sh`는 existing managed notification Secret의 현재
approved schema만 수렴시키고 새 Pod rollout·Ready를 확인합니다. 임의 Secret 이름이나
임의 `key=value`를 받는 범용 편집기는 만들지 않습니다.

- 새 key 추가: code·approved schema·`.env.example`·bootstrap·consumer manifest·operation
  contract를 같은 변경에서 추가하고, value를 먼저 적용한 뒤 consumer를 rollout합니다.
- 같은 key value 교체: provider에서 새 key 발급, Secret 적용, 새 Pod Ready·기능 smoke,
  기존 key revoke 순서로 진행합니다.
- Key 폐기: code·consumer 참조 제거와 rollout을 먼저 완료한 뒤 approved schema에서 key를
  제거하고 provider credential을 폐기합니다.
- DB password와 auth HMAC은 외부 DB·token/hash 생애주기가 다르므로 notification operation에
  섞지 않습니다.

### Workload와 Argo CD

Base는 다음 네 source만 가집니다.

```text
deploy/base/
├── deployment.yaml
├── migration-job.yaml
├── service.yaml
└── kustomization.yaml
```

Deployment는 runtime DB credential만 받고 Flyway를 비활성화합니다. Notification
ConfigMap·Secret처럼 같은 consumer와 함께 증가하는 묶음은 `envFrom`, DB identity처럼
좁고 이름 mapping이 필요한 계약은 explicit key reference를 사용합니다. Migration Job에는
notification credential을 주지 않습니다.

Manifest는 현재 필요한 ARM64 scheduling, non-root·capability drop·read-only filesystem,
RDS CA mount와 readiness만 명시합니다. Resource 수치, restart probe, rollout tuning은 target
node와 실제 startup·memory·latency를 측정한 별도 변경에서 추가합니다.

AppProject는 exact repository·namespace와 현재 resource kind만 허용하며 Secret과
cluster-scoped resource를 허용하지 않습니다. Application은 automated sync, prune, self-heal,
`allowEmpty: false`를 유지합니다. Notification operation이 새 Pod를 만들기 위해 쓰는 한 개의
Pod template annotation만 Argo diff에서 제외합니다.

## 작업 계획

### 1. 문서 기준선

- [x] 오늘 작업과 feature를 하나의 Argo CD application delivery 결과로 묶습니다.
- [x] Final consumer까지 producer·consumer·caller·test·runbook을 전수조사합니다.
- [x] 유지·제거·축소·누락 결과와 근거를 이 문서에 고정합니다.
- [x] 이 문서 후보를 source·link에 대조하고 별도 documentation commit으로 기록합니다.

### 2. 단일 Migration Job

- [x] Runtime permission SQL을 application Flyway callback resource로 옮깁니다.
- [x] Migration Job에서만 callback location을 활성화합니다.
- [x] Permission Job·ConfigMap·volume·mount를 제거합니다.
- [x] Process integration test를 실제 단일 JVM 성공·실패·권한 결과로 축소합니다.

### 3. 최소 workload와 GitOps 경계

- [x] Deployment에서 근거 없는 replica·resource·rollout·restart tuning을 제거합니다.
- [x] GitOps Application에서 미래 HPA replica ignore를 제거합니다.
- [x] GitOps bootstrap prerequisite를 application Namespace owner 확인으로 축소합니다.
- [x] AppProject allow-list와 annotation ignore가 실제 manifest·operation만 허용하는지 검증합니다.

### 4. ConfigMap·Secret lifecycle 유지

- [x] Git-managed DB URL·RDS CA·notification ConfigMap과 consumer wiring을 재검증합니다.
- [x] Bootstrap 200의 네 Secret 최초 생성과 notification operation의 value 교체·rollout을
      유지합니다.
- [x] 확인되지 않은 staging notification public value를 정상 운영값으로 단정하지 않도록
      source와 runbook을 정리합니다.
- [x] `.env.example`, bootstrap·operations README와 archive caller를 실제 executable에 맞춥니다.

### 5. 문서·검증·Git 마무리

- [x] Permission Job·HPA 선반영·임의 tuning을 현재 구조로 설명한 SOT·runbook·README를 모두
      단일 Job·최소 manifest 구조로 동기화합니다.
- [x] Bash syntax·ShellCheck·script contract test·focused Gradle process test·`./gradlew check`를
      실행합니다.
- [x] `kubectl kustomize deploy/overlays/stg` local render를 검증하고 target API server-side
      dry-run은 live 수용 기준에 유지합니다.
- [x] 구현을 독립 revert 가능한 Conventional Commit 경계로 기록합니다.
- [x] 미공유 과잉 commit은 필요한 결과를 보존한 새 commit 구조로 교체하고 cumulative tree와
      Git 상태를 확인합니다.

## Repository 수용 기준

- [x] Base에는 Deployment, 단일 Migration Job, Service와 Kustomization만 있습니다.
- [x] Runtime 권한 SQL은 Flyway `afterMigrate` callback이며 별도 permission Job·ConfigMap이
      없습니다.
- [x] Migration process가 실제 PostgreSQL에서 migration과 runtime 권한을 완료하고 0으로
      종료하며 잘못된 credential에서는 non-zero로 종료합니다.
- [x] Runtime role은 application table DML과 sequence 사용이 가능하고 Flyway history를 읽지
      못합니다.
- [x] Deployment는 Flyway off·runtime identity·notification runtime 설정만 소비하며 migrator
      credential을 받지 않습니다.
- [x] Migration Job은 notification credential과 runtime password를 받지 않습니다.
- [x] Git에는 actual Secret value가 없고 bootstrap·operation은 fixed NUL input과 redaction
      계약을 유지합니다.
- [x] DB URL·RDS CA는 PreSync ConfigMap, notification public 설정은 hashed Sync ConfigMap입니다.
- [x] AppProject는 Secret·cluster resource를 허용하지 않고 Application은 현재 필요한 한 개의
      rollout annotation만 ignore합니다.
- [x] Manifest에 근거 없는 resource·rollout·restart tuning과 미래 HPA 설정이 없습니다.
- [x] 관련 SOT·runbook·README가 단일 Job과 실제 lifecycle을 설명하며 폐기 경로를 참조하지
      않습니다.
- [x] Kustomize render와 repository local test가 통과합니다.

## 외부 qualification 요구

이 작업을 완료하려면 다음 외부 결과가 필요합니다. 각 항목의 현재 실행 여부와 재개
위치는 [Staging 단일 node 전체 구축 시나리오](../../../operations/scenarios/100-staging-single-node-bootstrap.md)가
한 곳에서 소유합니다.

- Actual staging RDS endpoint와 approved notification sender·frontend URL을 Git에 반영하고
  placeholder·추측값이 없음을 확인합니다.
- `100`으로 두 RDS role을 최초 구성하고 runtime·migrator login과 권한 분리를 확인합니다.
- `200`으로 Namespace와 네 Secret을 적용하고 실제 value를 출력하지 않은 채 resource를
  확인합니다.
- Target API server의 server-side dry-run이 성공합니다.
- Full sync에서 ConfigMap, Migration Job, Deployment 순서를 확인하고 selective sync를
  사용하지 않습니다.
- Fresh revision이 `Synced`·`Healthy`, runtime Pod가 Ready이며 Deployment에서는 Flyway가
  실행되지 않습니다.
- Runtime Pod가 RDS에 연결하고 대표 application API가 성공합니다.
- Resend key가 새 Pod에서 실제 사용되는 대표 email smoke 뒤 이전 provider key를
  폐기합니다.

## 제외 범위

- HPA·Cluster Autoscaler 설치와 tuning
- Ingress·ALB·HTTPS·DNS 구축
- External Secrets·AWS Secrets Manager application 연동
- Kubernetes Secret encryption-at-rest retrofit
- Production 배포와 production credential
- DB password·auth HMAC rotation 구현
- Image digest PR 자동화

## 영구 SOT·Procedure 동기화 대상

- [RDS PostgreSQL](../../../project/infrastructure/130-rds-postgresql.md)
- [Argo CD platform](../../../project/infrastructure/150-argocd-platform.md)
- [GitOps application delivery](../../../project/infrastructure/210-gitops-application-delivery.md)
- [Infrastructure system](../../../project/300-infrastructure-system.md)
- [Flyway](../../../tech-stack/240-flyway.md)
- [Application delivery 최초 bootstrap](../../../operations/350-application-delivery-bootstrap-runbook.md)
- [GitOps 배포·rollback](../../../operations/420-gitops-deployment-and-rollback-runbook.md)
- [Notification credential 변경](../../../operations/540-application-notification-credential-runbook.md)

## 관련 문서

- **작업 목록**: [2026 기능 작업 문서](000-index.md)
- **로컬 Secret source**: [로컬 개발 환경](../../../development/100-local-development.md)
- **Application bootstrap interface**: [Application bootstrap README](../../../../scripts/bootstrap/application/README.md)
- **Application operation interface**: [Application operations README](../../../../scripts/operations/application/README.md)
- **현재 staging live 상태와 재개 위치**:
  [Staging 단일 node 전체 구축 시나리오](../../../operations/scenarios/100-staging-single-node-bootstrap.md)
