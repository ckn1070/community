# GitOps application delivery

이 상세 SOT는 private ECR exact digest가 pull request 승인을 거쳐 Kubernetes live state로
수렴하고 rollback되는 반복 delivery의 actor·artifact·state model을 소유합니다. Argo CD
최초 설치·identity·permission은 [160 Argo CD platform](160-argocd-platform.md), 실제 반복 절차는
[GitOps 배포·rollback runbook](../../operations/420-gitops-deployment-and-rollback-runbook.md)이
소유합니다.

전체 CI/CD 순서와 workflow별 step은
[200 CI/CD pipeline](200-ci-cd-pipeline.md)이 소유합니다. 이 문서는 그 흐름을 반복하지 않고
digest 승인 뒤 Git desired state가 live state로 수렴하는 actor·artifact·state model을
상세화합니다.

Image 게시와 deployment 승인은 같은 사건이 아닙니다. Publish workflow는 runtime artifact만
만들고, 같은 repository의 별도 `deploy/**` digest 변경 PR이 staging deployment 승인
지점입니다. Argo CD는 merge된 Git을 read-only로 pull하며 GitHub Actions가 cluster
credential을 소유하지 않습니다.

## Source·artifact·state의 책임

| 대상 | 소유하는 사실 | 변경 actor |
| --- | --- | --- |
| `car-community` source commit | Application source와 build identity | Source PR reviewer·merge |
| Private ECR digest | Immutable runtime content | Publish workflow |
| `car-community`의 `deploy/**` commit | 승인된 manifest·environment overlay·image digest | GitOps PR reviewer·merge |
| Argo Application status | Git 목표와 live resource의 sync·health 관찰 | Argo controller가 계산 |
| Kubernetes live resource | 현재 실행 상태 | Argo controller가 Git 목표로 수렴 |
| Git 밖 prerequisite resource | Namespace와 runtime·migrator·auth·notification Secret | Local `.env` 허용값을 전달한 bootstrap operator |
| Argo bootstrap desired state | AppProject와 `car-community-stg` Application spec | `scripts/bootstrap/gitops/manifests`의 Git-tracked YAML·최초 bootstrap operator |
| Git-managed application 설정 | DB URL·pinned RDS CA, notification 공개 설정 ConfigMap | GitOps PR reviewer·Argo controller |
| Git-managed ingress intent | Staging NodePort patch, host·listener·target·TLS policy Ingress | GitOps PR reviewer·Argo controller |
| AWS ingress platform | LBC release, IAM·subnet·ACM과 controller-managed ALB·SG | Helm bootstrap·AWS·Cloudflare operator·LBC; Argo는 이 platform을 설치하지 않음 |

ECR SHA tag, digest와 GitOps commit을 혼용하지 않습니다. Tag는 source lookup, digest는 content,
GitOps commit은 승인된 deployment intent입니다.

## Repository와 overlay 구조

현재 `car-community` repository 안의 `deploy/base`가 공통 resource,
`deploy/overlays/stg`가 staging 차이와 승인된 digest를 소유합니다. Application은 `main`의
exact staging overlay를 읽습니다.

```text
deploy/
├── base/
└── overlays/
    └── stg/
```

현재 application·environment가 하나라 Application 하나를 사용하고 app-of-apps와
ApplicationSet을 도입하지 않습니다. Scale·multi-cluster·여러 application의 실제 반복 패턴이
생기면 도입 비용과 failure domain을 다시 평가합니다.

AppProject와 Application 자체는 `scripts/bootstrap/gitops/manifests`의 정적 YAML로
관리합니다. 최초 Application이 아직 없어 이 YAML을 스스로 reconcile할 수 없는 bootstrap
경계만 GitOps `200`이 적용하며, Shell은 resource spec을 생성하지 않습니다. Application이
가리키는 실제 workload desired state는 위 `deploy/overlays/stg`가 계속 소유합니다.

## 승인과 branch 경계

- Argo CD 최초 연결 전에는 target server-side dry-run을 통과한 initial desired state를
  단일 운영자가 `main`에 직접 push할 수 있습니다. 아직 Application이 없어 이 push가
  cluster sync를 일으키지 않는 bootstrap 경계에만 적용합니다.
- Argo CD 연결 이후 `deploy/**` 변경과 rollback은 pull request review 뒤 merge합니다.
- Image digest diff와 manifest 변경을 사람이 검토합니다.
- Application build PR과 deployment PR을 분리해 image publish가 곧 자동 production/staging
  deployment가 되지 않습니다.
- GitHub App은 Contents read-only이므로 Argo CD가 PR·commit을 만들지 않습니다.
- 현재 private repository의 `main` branch protection과 required check 강제는 비활성입니다.
  반복 GitOps PR은 운영 정책으로 사용하며 GitHub가 merge를 강제하지는 않습니다.
- 향후 digest 변경 PR 자동 생성 workflow나 독립 reviewer를 도입하면 별도 Git write
  identity·최소 권한과 branch protection 통합을 함께 재검토합니다. 현재는 구현되지
  않았습니다.

현재 application·cluster·environment가 각각 하나이고 같은 관리자가 source와 배포 설정을
관리하므로 별도 GitOps repository는 두지 않습니다. 이 선택은 repository·ruleset·credential
운영 대상을 줄이지만, GitHub App의 repository read 권한과 source·deployment history를
repository 수준에서 격리하지는 못합니다. Exact Application path가 적용 범위를
`deploy/overlays/stg`로 제한합니다.

`deploy/**` 전용 merge에도 `CI`와 `Publish image` workflow event는 생길 수 있지만, 변경 경로
분류 뒤 privileged publish job은 skip합니다. Source와 `deploy/**`가 함께 바뀌면 image를
게시하므로 source PR과 deployment PR을 분리합니다. 서비스·environment·배포 승인 주체나
repository read 권한을 분리해야 할 때 별도 GitOps repository를 다시 검토합니다.

## Reconciliation state model

| 상태·option | 의미 | 현재 계약 |
| --- | --- | --- |
| Automated enabled | Merge된 Git revision을 자동 적용 | 활성 |
| `prune` | Git에서 제거한 resource를 live에서도 정리 | 활성 |
| `selfHeal` | 사람이 만든 live drift를 Git 상태로 복구 | 활성 |
| `allowEmpty` | Empty render를 정상 목표로 허용 | 비활성, 잘못 빈 overlay의 전체 삭제를 차단 |
| Namespace owner | Target namespace 생성 책임 | Application bootstrap이 먼저 생성하고 Argo CD는 생성하지 않음 |
| Application Secret rollout annotation | Community·notification Secret operation이 각 current `resourceVersion`을 기록하는 Pod template field | Exact annotation ignore + `RespectIgnoreDifferences=true` |
| `Synced` | Live resource가 target Git revision과 일치 | 배포 완료에 필요하지만 health를 대신하지 않음 |
| `Healthy` | Resource health assessment가 정상 | `Synced`와 함께 필요 |
| Condition 없음 | Repository·render·permission 등 오류가 없음 | 완료에 필요 |

Credential 최초 적용·rotation 뒤에는 이미 남아 있던 `Synced`·`Healthy`를 새 credential
증거로 사용하지 않습니다. 새 application-controller revision·informer initial sync와 hard
refresh가 소비된 뒤 `reconciledAt`이 바뀐 fresh reconciliation을 확인합니다.

## Staging workload와 migration 경계

Migration Job은 Deployment와 같은 immutable ARM64 image를 lazy non-web Spring process로
실행하고 migrator Secret만 datasource에 mapping합니다. Job에서만 Flyway callback location을
활성화해 migration 성공 뒤 `afterMigrate` SQL이 현재 application table·identity sequence에
runtime 최소 권한을 부여하고 Flyway history 권한을 회수합니다. Job이 실패하면 runtime
Deployment를 sync하지 않습니다.

Deployment는 runtime DB Secret만 사용하고 `SPRING_FLYWAY_ENABLED=false`를 고정합니다.
DB·auth는 explicit key reference로 제한하고, runtime에서 함께 진화하는 notification
ConfigMap과 Secret만 `envFrom`으로 소비합니다. Migration Job에는 notification credential을
주입하지 않습니다. 따라서 replica가 늘거나 재시작돼도 migration을 실행하지 않습니다.
Staging overlay는 Deployment의 profile만 `production,staging`으로 바꿔 production 운영
기준 위에 관리자용 Swagger 설정과 보안 체인을 추가합니다. HTTP endpoint가 없는 Migration
Job은 `production` profile만 유지합니다.

Base Service는 환경 공통 port contract을 소유하고 staging overlay가 `NodePort`로 patch합니다.
고정 `nodePort`는 주지 않고 Kubernetes가 할당하게 하며, self-managed kubeadm·Calico
overlay에서 LBC `instance` target이 node를 거쳐 Pod로 전달하는 경계입니다.
Ingress는 `stg-api.example.com`, HTTP→HTTPS, certificate host discovery와 TLS policy를
소유하지만 controller release·IAM·certificate·DNS를 소유하지 않습니다. HPA·Cluster
Autoscaler 자체는 현재 manifest에 포함하지 않습니다.

현재 manifest는 Kubernetes 기본 1 replica를 사용하고 resource·rollout·restart probe 수치를
추측해 고정하지 않습니다. Migration Job은 작업의 무한 대기를 막기 위한 deadline `300s`와
retry `0`만 명시합니다. 최초 live 배포 뒤 `kubectl top`, startup 시간, OOM·throttling과 RDS
`max_connections` 대비 `최대 replica × Hikari pool`을 측정한 별도 변경에서 resource와 HPA
정책을 결정합니다.

Pod·Node autoscaling과 그에 결합되는 metric·log 후보의 상태와 도입 순서는
[004 Infrastructure evolution backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
소유합니다.

반복 delivery의 순서와 각 단계 출력은 [200 CI/CD pipeline](200-ci-cd-pipeline.md)이
소유합니다. Selective sync는 PreSync hook을 건너뛸 수 있으므로 이 delivery 경로에서
사용하지 않습니다.

Full sync가 Job을 다시 만들 수 있지만 Flyway history는 적용된 version을 다시 실행하지 않고,
`afterMigrate` callback은 migration 0건인 재실행에서도 같은 최소 권한으로 다시 수렴합니다.

Git PR 밖에서 Application source 경로나 live Deployment image를 직접 바꾸지 않습니다.
Core에는 UI가 없으므로 UI button을 운영 상태 변경 경로로 두지 않으며, full UI를 도입해도
Git outside change를 canonical delivery로 만들지 않습니다.

## Rollback model

Rollback은 이전 정상 digest commit을 되돌리는 **새 pull request**입니다.

- Revert diff·목표 digest를 review하고 merge합니다.
- Argo CD가 이전 content identity로 새 desired state를 적용합니다.
- Rollback 자체도 Git history와 승인 기록에 남습니다.
- Live Deployment image patch나 Argo Application의 임시 rollback command를 사용하지
  않습니다. Automated self-heal과 충돌하고 Git 목표 상태가 다시 덮어쓰기 때문입니다.
- Git revert는 이미 적용된 database migration을 되돌리지 않습니다. 이전 image는 현재
  schema와 backward-compatible할 때만 rollback 후보가 됩니다.

ECR lifecycle policy를 만들 때 rollback window가 필요로 하는 digest가 삭제되지 않도록
보존 기간·개수를 먼저 결정해야 합니다.

## 실패와 복구 경계

| 실패 위치 | 의미 | 복구 방향 |
| --- | --- | --- |
| Image publish 실패 | Deployment input이 없음 | Source·workflow·IAM을 고쳐 새 publish 결과 확보 |
| Node exact digest pull 실패 | Runtime이 artifact를 소비할 수 없음 | Kubelet provider·IAM·ECR·network를 먼저 복구 |
| GitOps PR render 실패 | Desired state가 유효하지 않음 | PR에서 Kustomize·manifest를 수정, merge 금지 |
| PreSync Migration Job 실패 | Schema·credential·RDS TLS 또는 권한 callback 실패 | Job log와 prerequisite를 복구한 뒤 full sync, selective sync·`flyway repair` 우회 금지 |
| Repository authentication 실패 | Argo가 desired state를 읽지 못함 | GitHub App installation·key·Secret lifecycle 복구, PAT 우회 금지 |
| Sync condition | Permission·render·apply failure | AppProject·event·Argo log·resource owner를 진단해 Git 또는 platform을 forward fix |
| `OutOfSync`·unhealthy | Target과 live 또는 workload health 불일치 | Live patch 대신 Git target·runtime 원인을 수정 |
| 잘못된 정상 merge | 승인된 desired state 자체가 잘못됨 | 이전 digest·manifest commit revert PR |
| Ingress model build 실패 | Certificate·subnet·IAM·annotation 계약 불일치 | ALB를 Console에서 직접 만들지 않고 Ingress event·LBC log·platform prerequisite를 forward fix |
| ALB target unhealthy | NodePort·endpoint·Pod readiness·managed SG 경계 문제 | NodePort를 public으로 열지 않고 Service→Pod→SG 순서로 진단 |

Argo timeout은 Application·workload 자동 삭제나 automated sync 비활성의 신호가 아닙니다.
Controller·repository·image·manifest 원인을 확인하고 Git 목표 또는 credential을 고칩니다.

## Initial과 recurring 경계

다음 initial 결과가 있어야 반복 delivery를 시작할 수 있습니다.

- Argo CD Core exact version·digest가 Ready입니다.
- Non-empty valid `deploy/overlays/stg`가 `main`에 승인돼 있습니다.
- Runtime·migrator DB role과 Git 밖 Namespace·다섯 Secret이 준비돼 있습니다.
- Actual DB URL·pinned RDS CA와 notification 공개 설정이 staging overlay에 승인돼 있습니다.
- Read-only GitHub App과 project-scoped repository Secret이 있습니다.
- AppProject와 `car-community-stg` Application의 최초 fresh sync가 성공했습니다.
- ECR image를 node가 exact digest로 pull할 수 있습니다.

GitHub App private key와 notification provider credential 변경은 deployment PR 자체가 아니라
각각 별도 change lifecycle입니다. Notification Secret 변경은 새 Pod rollout·Ready와 실제
email smoke 뒤 기존 provider key를 폐기합니다. 실행 절차는
[550 Resend API key 교체](../../operations/550-resend-api-key-rotation-runbook.md),
GitHub App rotation 원리는
[Repository credential rotation](330-argocd-repository-credential-rotation.md)이 소유합니다.

## Repository capability 경계

현재 repository capability에는 image publish workflow, application prerequisite,
Community·notification Secret operation, Service·Deployment·단일 Migration PreSync Job,
staging NodePort·Ingress manifest, Argo·LBC bootstrap·rotation script와 contract test가
포함됩니다. Digest 변경 PR workflow와 그 Git write credential은 구현하지 않았습니다.

Repository capability와 live 실행 완료는 서로 다른 사실입니다. 현재 staging 실행 상태는
[Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md)가
소유합니다.

## 관련 문서와 공식 근거

- **Image identity·build·kubelet pull**: [210 Container image delivery](210-container-image-delivery.md)
- **Argo platform·permission**: [160 Argo CD platform](160-argocd-platform.md)
- **Workload network·Argo reconcile 진단**:
  [170 Kubernetes workload 진단](../../operations/diagnostics/170-kubernetes-workload-network-events-and-logs.md),
  [180 Argo CD reconciliation 진단](../../operations/diagnostics/180-argocd-reconciliation.md)
- **실제 배포·rollback 절차**:
  [GitOps 배포·rollback runbook](../../operations/420-gitops-deployment-and-rollback-runbook.md)
- **현재 staging 실행 상태**:
  [Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md)
- **Application prerequisite와 private repository 최초 연결**:
  [Application delivery 최초 bootstrap runbook](../../operations/350-application-delivery-bootstrap-runbook.md)
- **Ingress desired state와 AWS lifecycle 경계**:
  [170 AWS Load Balancer ingress](170-aws-load-balancer-ingress.md)
- **Staging external HTTPS 최초 절차**:
  [370 Staging 외부 HTTPS ingress 최초 구축 runbook](../../operations/370-staging-external-https-ingress-bootstrap-runbook.md)
- **Notification provider credential 변경**:
  [550 Resend API key 교체](../../operations/550-resend-api-key-rotation-runbook.md)
- **Community Secret 준비와 기능 활성화**:
  [560 Community 기능 활성화](../../operations/560-community-feature-activation-runbook.md)
- [Argo CD automated sync](https://argo-cd.readthedocs.io/en/stable/user-guide/auto_sync/)
- [Argo CD AppProject](https://argo-cd.readthedocs.io/en/stable/operator-manual/project-specification/)
- [Argo CD repository Secret](https://argo-cd.readthedocs.io/en/stable/operator-manual/declarative-setup/#repositories)
