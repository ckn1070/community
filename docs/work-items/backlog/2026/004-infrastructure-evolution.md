# Infrastructure 진화 Backlog

## 상태

Open

## 목적

현재 staging infrastructure의 운영 범위를 넘어서는 확장·보안·관측·자동화 후보를 한곳에서
검토합니다. 이 문서는 아직 착수하지 않은 후보의 필요성, 도입 trigger와 상호 관계만
소유하며 구현 계획이나 현재 구성값을 확정하지 않습니다.

## Owner 경계

| 확인할 정보 | Canonical owner |
| --- | --- |
| 현재 topology·구성값·선택 이유와 재검토 trigger | [000 Infrastructure 개념 목차](../../../project/infrastructure/000-index.md)의 상세 SOT |
| 실제 AWS·EC2·Kubernetes live 상태와 재개 위치 | [100 Staging 단일 node 전체 구축 시나리오](../../../operations/scenarios/100-staging-single-node-bootstrap.md) |
| 아직 착수하지 않은 infrastructure 후보와 후보 사이의 관계 | 이 문서 |
| 실행하기로 전환한 목표·범위·수용 기준과 상태 | 새 Feature·Improvement·Spike |
| 실제 구축·변경·복구 명령 | [000 운영 문서 목차](../../../operations/000-index.md)의 목적별 runbook |

현재 구성 내용은 후보 분류가 아닙니다. 후보가 현재 어떤 경계를 바꾸는지는 상세 SOT에
연결하고, 이 문서에 현재 설정을 복사하지 않습니다.

## 중요도와 도입 관계

고정 `P1`·`P2` 대신 다음 관계로 후보를 비교합니다. 아직 실행하기로 결정하지 않은 후보에
일정을 확정하거나 모든 조건을 hard prerequisite로 확대하지 않습니다.

| 관계 | 의미 |
| --- | --- |
| 선행 조건 | 후속 후보의 의미나 안전성을 판단하기 전에 닫아야 하는 경계 |
| 합류 조건 | 독립적으로 준비할 수 있지만 무인 운영·Production 같은 시점 전에는 함께 필요 |
| 조건부 | 부하·가용성·보안·비용 요구가 관찰될 때 검토 |
| 독립 | 다른 후보와 병행 검토할 수 있고 직접 선행 관계가 없음 |

도입 시점은 `현재 반복 운영의 안전성`, `Pod autoscaling 전`, `다중 node·node autoscaling 전`,
`무인 운영 전`, `Production 전`, `실측 trigger 발생`으로 구분합니다. 중요도는 장애·보안·
data 손실 영향과 가까운 도입 시점을 함께 보고 판단합니다.

## 용량·성능·확장

### Workload resource 기준과 Pod autoscaling

- **검토할 문제**: 현재 application은 기본 replica `1`이고 resource request·limit과 HPA가
  없습니다. 부하와 startup, readiness, OOM·throttling을 측정해 resource 기준과 Pod
  autoscaling 필요성을 결정해야 합니다.
- **관계**: 사용 metric과 target 의미, utilization metric을 쓸 때의 request, probe와
  `최대 replica × Hikari pool`이 RDS connection capacity를 넘지 않는다는 기준이 실제 HPA의
  선행 조건입니다. 중앙 로그·alert는 첫 제한 실험의 선행 조건은 아니지만 무인 확장 전
  관측성 후보와 합류합니다.
- **도입 trigger**: 단일 replica가 응답 시간·처리량·가용성 목표를 충족하지 못하거나
  반복 부하에서 resource pressure가 관찰될 때입니다.
- **현재 근거**: [220 GitOps application delivery](../../../project/infrastructure/220-gitops-application-delivery.md)

### 공개 요청의 공유 limiter

- **검토할 문제**: 016 일반 커뮤니티의 첫 범위는 desired replica `1`과 `RollingUpdate`,
  교체 가능한 Port 뒤의 Pod별 local limiter를 사용합니다. 평상시에는 Pod 하나가 상한을
  소유하지만 rollout 중 최대 두 Pod가 잠시 겹치면 요청·이미지 동시성·전달량의 이론적 합이
  두 배가 될 수 있습니다. 이 일시적 증가는 현재 허용하며 shared/edge limiter를 첫 공개의
  선행 조건으로 만들지 않습니다.
- **관계**: 상시 replica `2` 이상이나 HPA로 전환하기 전에는 local Port를 유지한 채 Redis 같은
  공유 저장소 또는 WAF·API gateway 같은 edge adapter로 교체하고, trusted client 식별과
  origin 우회 차단을 포함해 client별 요청량·전체 image stream 동시성·전달량을 deployment
  전체 기준으로 검증해야 합니다. 구체 제품·인프라 선택은 해당 전환 작업이 소유합니다.
- **도입 trigger**: 상시 다중 replica, HPA, local limiter 우회 abuse 관찰, 이미지 트래픽의
  비용·자원 압박 또는 deployment 전체의 정확한 상한 요구 중 하나가 생길 때입니다.
- **현재 근거**: [016 일반 커뮤니티 해결 설계](../../features/2026/016-general-community-design.md),
  [220 GitOps application delivery](../../../project/infrastructure/220-gitops-application-delivery.md)

### 다중 node 기반과 Node autoscaling

- **검토할 문제**: 현재 EC2는 launch template이나 Auto Scaling Group 없이 control plane과
  worker를 함께 실행하는 단일 node입니다. Node autoscaling만 추가해서는 replacement와
  cluster 가용성을 보장할 수 없습니다.
- **관계**: Stable API endpoint·control plane 경계, node pool owner, launch·join·drain·
  termination·replacement, name/tag 전파, `providerID`, Calico AWS routing, workload 배치와
  node IAM을 먼저 결정한 뒤 node autoscaling을 검토합니다. HPA가 만든 Pod가 실제 node
  capacity 부족으로 pending되는 관계도 같은 판단에 포함합니다.
- **도입 trigger**: 한 node의 수직 확장으로 capacity·장애 목표를 충족할 수 없거나, node
  장애와 workload capacity를 분리해야 할 때입니다.
- **현재 근거**: [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md),
  [150 Kubernetes cluster](../../../project/infrastructure/150-kubernetes-cluster.md),
  [170 AWS Load Balancer ingress](../../../project/infrastructure/170-aws-load-balancer-ingress.md)

### Database와 platform capacity

- **검토할 문제**: RDS Proxy, larger class·storage tuning, Multi-AZ와 Argo CD·Load Balancer
  Controller replica/HA 전환은 현재 비용 우선 staging 범위 밖입니다.
- **관계**: Application replica·connection budget과 database 부하, 장애 목표를 먼저 관찰하고
  각 구성요소의 capacity와 availability 결정을 분리합니다.
- **도입 trigger**: Connection 부족, CPU·memory·storage 병목, failover 목표 또는 platform
  component 단일 장애점 제거 요구가 생길 때입니다.
- **현재 근거**: [140 RDS PostgreSQL](../../../project/infrastructure/140-rds-postgresql.md),
  [160 Argo CD platform](../../../project/infrastructure/160-argocd-platform.md),
  [170 AWS Load Balancer ingress](../../../project/infrastructure/170-aws-load-balancer-ingress.md)

### Kubernetes platform 유지보수와 version upgrade

- **검토할 문제**: 현재 bootstrap은 Kubernetes·containerd·Calico·Argo CD·AWS Load Balancer
  Controller의 exact version을 재현하지만 기존 cluster를 자동 upgrade·downgrade하지 않습니다.
  단일 node의 OS package 갱신·재부팅, control plane certificate와 각 component version 변경을
  어떤 순서·중단 시간·복구 경계로 수행할지도 아직 반복 운영 절차로 확정하지 않았습니다.
- **관계**: 대상 version의 상호 호환성, Kubernetes version skew, node drain 가능 여부,
  etcd·credential·Git 밖 prerequisite 복구 source, single-node downtime과 rollback 기준을 먼저
  닫고 component별 변경과 전체 postcondition을 연결합니다. 다중 node 전환과 독립적으로
  설계할 수 있지만, node 교체 topology를 채택하면 maintenance actor와 순서를 함께 재검토합니다.
- **도입 trigger**: 보안 공지·지원 종료·호환성 요구로 현재 exact version을 유지할 수 없거나,
  Ubuntu가 재부팅을 요구하는 갱신을 반복 운영에서 수행하기 전입니다.
- **현재 근거**: [150 Kubernetes cluster](../../../project/infrastructure/150-kubernetes-cluster.md),
  [160 Argo CD platform](../../../project/infrastructure/160-argocd-platform.md),
  [170 AWS Load Balancer ingress](../../../project/infrastructure/170-aws-load-balancer-ingress.md),
  [300 Infrastructure 변경과 복구 원칙](../../../project/infrastructure/300-change-and-recovery-principles.md)

## 관측성·로그·운영 대응

현재 RDS monitoring·log export와 EC2 기본 metric, 운영자가 실행하는 read-only platform
진단은 각각 일부 관측 surface를 제공합니다. Application도 structured console log를
출력합니다. 이 현재 기반을 지속 수집·검색·alert 체계가 이미 있다는 뜻으로 확대하지
않습니다.

| 단계 | 검토할 후보 | 도입 관계 |
| --- | --- | --- |
| 지속 metric | Host/node, AWS managed·edge, Kubernetes/platform, application·dependency, delivery/control의 연속 metric과 health 수집 | HPA metric 계약과 독립적으로 설계할 수 있으며 무인 운영 전 합류 |
| 중앙 log·event | Ephemeral Pod·node lifecycle과 분리된 수집, 검색, 보존 기간, 접근 권한, 삭제 책임, 비용 상한 | 다중 replica·node 확장 전 log 유실과 node-local 종속을 제거 |
| Alert·on-call | Infra alert source, threshold 전달 경로, escalation과 on-call 책임 | 무인 운영·Production 전 합류 |
| Trace·SLO | Request·dependency 구간과 사용자 목표를 연결할 tracing·SLO 필요성 | Metric·log만으로 병목과 영향 범위를 판단하기 어려울 때 조건부 검토 |

중앙 로그 후보는 cluster·namespace·Pod·container·node·Deployment·image digest와 application
`requestId` 같은 상관관계 metadata, 수집 실패·buffer·retry와 민감정보 최소화 경계를 함께
검토합니다. Auth event의 감사 의미·보존·보안 alert 대상은
[003 Account 인증 후속 후보](003-account-auth-follow-up.md)가 소유하며 application 진단 log와
섞지 않습니다. 외부 연동·데이터 무결성 event의 업무 의미와 자동 대응은
[001 프로젝트 횡단 열린 결정](001-project-open-decisions.md)이 소유합니다.

현재 근거는 [140 RDS PostgreSQL](../../../project/infrastructure/140-rds-postgresql.md),
[120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md),
[000 Platform 진단 목차](../../../operations/diagnostics/000-index.md)와
[280 Logging](../../../tech-stack/280-logging.md)에서 확인합니다.

## 보안·identity·secret

| 후보 | 검토할 문제와 도입 trigger | 현재 근거 |
| --- | --- | --- |
| Workload identity와 Secret 공급 | Node 전체 AWS identity 대신 workload별 권한이 필요하거나, application Secret의 수동 주입·rotation 범위를 줄여야 할 때 External Secrets·Secrets Manager·Pod identity 경계를 결정합니다. | [130 ECR·IAM 기반](../../../project/infrastructure/130-ecr-iam-foundation.md), [170 AWS Load Balancer ingress](../../../project/infrastructure/170-aws-load-balancer-ingress.md) |
| Kubernetes Secret at-rest 보호 | Self-managed etcd의 Secret 저장 위협·key lifecycle 요구가 현재 RBAC·host 접근 제한보다 강해질 때 encryption 경계를 결정합니다. | [160 Argo CD platform](../../../project/infrastructure/160-argocd-platform.md) |
| Admin access lifecycle | 여러 운영자·장기 off-host access가 필요할 때 cluster-admin 복제 대신 역할별 RBAC·identity·certificate 발급·회수를 결정합니다. | [320 Kubernetes admin access](../../../project/infrastructure/320-kubernetes-admin-access.md) |
| Edge와 origin 보호 | Cloudflare proxy·WAF·rate limiting·cache, origin lock-down, AWS WAF가 필요한 공개 범위와 우회 방지 경계를 결정합니다. | [170 AWS Load Balancer ingress](../../../project/infrastructure/170-aws-load-balancer-ingress.md) |

## 구성 소유권·Infrastructure as Code

현재 AWS resource는 수동 관리되며 repository에 Terraform configuration, provider lock,
backend와 state가 없습니다. Terraform은 현재 구성이나 실행 절차가 아니라 미래의 구성
소유권 후보입니다.

1. **소유권 설계 후보**: 관리 범위, import와 replace의 경계, resource address·module,
   account 공용 OIDC provider owner, 기존 EC2 profile 연결, backend·locking, state 접근·secret,
   Terraform·provider version, plan·apply 승인과 수동 변경 정책을 결정합니다.
2. **검증된 전환 후보**: 현재 resource 계약과 import 뒤 zero-diff, 의도하지 않은 replacement·
   permission 확대·public exposure 부재, data·credential·connection 보존, 부분 실패 rollback,
   `managed-by=manual`에서 새 owner로의 handoff를 검증할 수 있을 때 실제 전환 작업으로
   승격합니다.

첫 후보는 독립적으로 검토할 수 있지만 두 번째 후보의 선행 조건입니다. 다중 node·새 network
topology처럼 수동 resource가 크게 늘어나기 전에는 구성 소유권을 다시 평가하되, Terraform을
HPA나 다른 infrastructure 변화의 무조건적인 선행 조건으로 만들지 않습니다. 현재 보존해야
할 resource 계약은 [000 Infrastructure 개념 목차](../../../project/infrastructure/000-index.md)의
각 상세 SOT가 계속 소유합니다.

## Delivery·credential 자동화

| 후보 | 검토할 문제와 도입 trigger | 관계 |
| --- | --- | --- |
| RDS master credential 진입점 | 새 환경에서 Secrets Manager의 현재 master credential을 EC2 인증에 안전하게 조회·주입하는 canonical procedure가 없습니다. 반복 구축·복구를 다시 수행하기 전에 별도 작업으로 전환합니다. | 현재 반복 운영의 안전성 후보 |
| DB password·auth HMAC rotation | Application DB role과 auth HMAC의 소비자·동시 전환·구 credential 폐기·rollback이 notification key 교체와 다르므로 각각의 rotation lifecycle을 결정합니다. | Secret 공급 방식과 연계하되 독립 후보로 승격 가능 |
| Image digest PR 자동화 | Git write identity·최소 권한, branch protection·required review와 함께 digest 변경 PR 자동 생성 여부를 결정합니다. | 현재 수동 PR lifecycle을 보존하며 독립 검토 |
| ECR lifecycle·rollback 보존 | Rollback 가능한 digest가 삭제되지 않도록 보존 window·개수·비용을 먼저 결정합니다. | GitOps rollback 정책의 선행 결정 |

현재 근거는 [140 RDS PostgreSQL](../../../project/infrastructure/140-rds-postgresql.md),
[200 CI/CD pipeline](../../../project/infrastructure/200-ci-cd-pipeline.md),
[210 Container image delivery](../../../project/infrastructure/210-container-image-delivery.md)와
[220 GitOps application delivery](../../../project/infrastructure/220-gitops-application-delivery.md)입니다.

## 가용성·복구·Production 진입

| 후보 | 검토할 문제와 도입 trigger | 관계 |
| --- | --- | --- |
| Resource 삭제·복구와 DR | VPC·cluster·RDS 삭제, backup restore, cross-region DR의 owner·RTO/RPO·data 영향·재개 절차를 결정합니다. | Data 보존·Production 전 합류 |
| Production ingress와 credential | Production domain·certificate·DNS, credential·network isolation과 배포 환경 경계를 staging과 분리합니다. | Production 진입 조건 |
| HA target | Control plane, database, Argo CD와 ingress controller의 허용 장애와 복구 목표를 먼저 정하고 Multi-AZ·multi-node·replica 후보를 선택합니다. | Production 또는 명시적 availability 목표 전 합류 |

## 작업 전환과 폐기 조건

- 후보를 실제로 착수할 때는 목표가 하나인 Feature·Improvement·Spike로 옮기고 목표·범위·
  수용 기준·검증·복구를 새 작업 문서에서 확정합니다.
- 기술 선택이나 비용 검증이 먼저 필요하면 Spike로, 구조·운영 결과를 실제로 바꾸기로
  결정하면 Improvement 또는 Feature로 전환합니다.
- 현재 trigger가 사라지거나 비용·복잡도가 효과보다 크다고 결정하면 근거와 함께 폐기합니다.
- 모든 후보가 새 작업으로 전환되거나 폐기되면 이 backlog를 `Closed`로 바꿉니다.

## 관련 문서

- **현재 infrastructure SOT**: [000 Infrastructure 개념 목차](../../../project/infrastructure/000-index.md)
- **현재 live 상태**: [100 Staging 단일 node 전체 구축 시나리오](../../../operations/scenarios/100-staging-single-node-bootstrap.md)
- **횡단 architecture·privacy·운영 정책**: [001 프로젝트 횡단 열린 결정](001-project-open-decisions.md)
- **Account·Auth 후속 후보**: [003 Account 인증 후속 후보](003-account-auth-follow-up.md)
- **완료된 application delivery 경계**: [013 Staging application GitOps delivery](../../features/2026/013-autoscaling-safe-application-delivery.md)
- **완료된 외부 HTTPS ingress 경계**: [014 Staging 외부 HTTPS ingress](../../features/2026/014-staging-external-https-ingress.md)
