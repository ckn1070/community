# Argo CD platform

이 상세 SOT는 단일 node cluster에 적용한 Argo CD Core의 형태, repository·identity·project·
application 경계와 선택 이유를 소유합니다. 실제 Core 설치와 private repository 최초 연결은
[Repository·delivery platform 최초 구축 runbook](../../operations/300-repository-and-delivery-platform-bootstrap-runbook.md)의
단계별 절차가 소유하고, 평소 배포는 [220 GitOps application delivery](220-gitops-application-delivery.md)가
소유합니다.

## Platform mental model

```text
private car-community/main
  └── GitHub App Contents read-only
      └── Argo repository Secret
          └── argocd-application-controller + repo-server
              ├── car-community AppProject permission boundary
              └── car-community-stg Application
                  └── deploy/overlays/stg
                      └── car-community namespace
```

Argo CD는 cluster 안에서 Git 목표 상태를 pull합니다. GitHub Actions가 private Kubernetes API
credential을 가질 필요가 없습니다. 현재 repository 안에서 application source와 `deploy/`
config의 경로·PR을 나눠 build 이력과 deployment intent·승인·rollback 이력을 구분합니다.

## Core 형태와 version 선택

| 항목 | 현재 선택 | 이유와 영향 | 재검토 조건 |
| --- | --- | --- | --- |
| CD controller | Argo CD | 사용자가 선택했고 pull-based reconciliation으로 GitHub Actions와 cluster credential을 분리합니다. | 다른 GitOps controller를 명시적으로 채택 |
| 설치 형태 | Argo CD Core `3.5.0`, headless·non-HA | 운영자 한 명, UI·SSO·multi-cluster API가 불필요해 endpoint·credential·component를 줄입니다. 단일 node cluster에서 Argo만 HA로 만들지 않습니다. | 여러 운영자·SSO·API·multi-cluster 또는 cluster HA 요구 |
| Version | Exact `3.5.0` | Kubernetes `1.36`을 official tested 범위에 포함하는 `3.5` line입니다. 더 오래된 `3.4`는 tested 범위가 `1.35`까지라 선택하지 않습니다. | Kubernetes·Argo version 변경 |
| Install artifact | Official `v3.5.0/manifests/core-install.yaml`, SHA-256 `166bffca5000482f8ae36d73b255610c9bb4c7d8da38fcf62003c2fdd8ab0747` | Mutable URL 대신 검토한 exact byte를 적용합니다. | Version·upstream provenance 변경 |
| Runtime image | ARM64 child manifest digest 고정 | Tag가 이동해도 재실행 결과와 actual Pod imageID가 달라지지 않습니다. | Node architecture·version 변경 |
| Argo CD digest | `sha256:8a14921f7e5dc3408111714f05bff68e337d459653c45f0aea738330d156079b` | 검증한 ARM64 child manifest입니다. | Version·architecture 변경 |
| Redis digest | `sha256:2fe148588d93987cea3533873195ec2450f5dbf420c78606aaacad7b78bb5489` | 검증한 ARM64 child manifest입니다. | Version·architecture 변경 |

Core에는 `argocd-server`와 UI·API endpoint가 없습니다. UI가 없다는 사실은 test-only라는
뜻이 아니며 reconciliation은 application-controller가 수행합니다. Full·HA 전환은 current
script가 자동 수행하지 않고 endpoint 보안·SSO·migration을 별도 설계해야 합니다.

현재 Core가 유지하는 runtime component는 `argocd-application-controller`,
`argocd-repo-server`, `argocd-applicationset-controller`, `argocd-redis`입니다. Repository
render·desired state 비교·reconciliation에 필요한 이 component만 두고, UI·CLI API를
제공하는 `argocd-server`는 설치하지 않습니다.

같은 owner와 exact digest의 partial apply는 같은 목표로 수렴시키지만, 다른 namespace owner,
full 설치, 다른 Argo·Redis image는 `--force-conflicts`로 인수·upgrade하지 않습니다. Official
large CRD manifest 적용에 force conflict를 사용하더라도 이 preflight 경계를 통과한 같은
목표에 한정합니다.

## Repository와 config 경계

현재 application 하나·cluster 하나·staging 하나를 같은 관리자가 다루므로 별도 GitOps
repository를 만들지 않습니다. `car-community` 안에서 source와 deployment config를 다음
경로로 분리합니다.

| 경로 | 소유하는 것 | 변경 경계 |
| --- | --- | --- |
| `deploy/` 밖 application source | Application source·Dockerfile·CI·image publish·bootstrap source | Source PR merge와 성공한 CI가 image 게시 입력이 됩니다. |
| `deploy/base` | 환경 공통 Kubernetes application manifest | Manifest PR review 뒤 `main`에 merge합니다. |
| `deploy/overlays/stg` | Staging 차이, approved image digest와 deployment intent | 별도 digest PR review 뒤 Argo CD가 읽습니다. |

```text
deploy/
├── base/
│   ├── deployment.yaml
│   ├── migration-job.yaml
│   ├── service.yaml
│   └── kustomization.yaml
└── overlays/
    └── stg/
        ├── ingress.yaml
        ├── pre-sync/
        ├── service-nodeport-patch.yaml
        └── kustomization.yaml
```

공통 resource와 staging 차이를 Kustomize 경계로 나누고 Application은 staging overlay 한
곳을 직접 읽습니다. Base는 Service·Deployment와 단일 Migration PreSync Job을 소유합니다.
Staging overlay의 DB URL·pinned RDS CA ConfigMap은 PreSync wave `-2`에서 Job보다 먼저
적용하고, runtime 권한 SQL은 application image의 Flyway `afterMigrate` callback이 실행합니다.
Notification 공개 설정 ConfigMap은 일반 Sync에서 runtime Deployment가 소비합니다.
Staging overlay는 namespace와 ARM64 ECR digest를 소유합니다. 현재 app·environment가 하나라
app-of-apps, ApplicationSet과 root application을 추가하지 않습니다. 빈 overlay를 최초 연결
성공으로 처리하지 않으며 `allowEmpty: false`가 이를 보호합니다.

같은 repository를 선택하면 repository·ruleset·credential 운영 대상을 늘리지 않고 source와
배포 변경을 한 곳에서 review할 수 있습니다. 대신 GitHub App의 `Contents: Read-only`는
directory 단위 권한이 아니므로 `deploy/`뿐 아니라 private repository content 전체를 읽을
수 있고, source와 deployment history도 repository 수준에서는 격리되지 않습니다.
AppProject의 exact repository와 Application의 exact path는 Argo CD가 적용하는 목표를
`deploy/overlays/stg`로 제한하지만 GitHub permission 자체를 path scope로 줄이지는 않습니다.

`deploy/**` 전용 merge도 application `CI`는 검증하되 image publish workflow는 변경 경로를
분류해 AWS OIDC·build·push job을 건너뜁니다. 서비스·environment·배포 승인 주체나 repository
read 권한을 분리해야 할 때 별도 GitOps repository를 다시 검토합니다.

## GitHub App identity와 Secret

| 항목 | 현재 선택 | 이유와 영향 |
| --- | --- | --- |
| Identity | GitHub App | 개인 PAT·사용자 lifecycle과 분리된 repository identity입니다. |
| Repository permission | `Contents: Read-only` | Git clone에 필요한 최소 permission이고 write를 주지 않습니다. Metadata read는 기본입니다. |
| Account·organization permission | 없음 | Repository 읽기 외 scope를 추가하지 않습니다. |
| Installation | `Only select repositories`, `car-community` 하나 | App이 읽을 repository를 한 개로 제한합니다. Directory 단위 permission은 아닙니다. |
| Webhook | 비활성 | 현재 polling으로 충분해 webhook endpoint·secret을 만들지 않습니다. |
| Credential form | App ID·Installation ID·private key | Argo CD repository Secret이 공식 지원하는 GitHub App 형식입니다. |

Private key는 local 안전 경로에서 SSH stdin으로 전달하고 argument·environment·Git·EC2 영구
file에 두지 않습니다. Remote에서는 `/run` mode `0600` 임시 file로만 materialize하고
Secret apply 직후와 중간 실패 trap에서 삭제합니다. Kubernetes Secret에는 지속되며 현재
self-managed etcd의 Secret at-rest encryption을 별도로 구성하지 않았으므로 API RBAC·etcd
접근 제한을 유지하고 encryption at rest는 후속 보안 범위입니다.

PAT·write credential은 repository 인증 실패의 우회 수단이 아닙니다. GitHub App permission,
installation repository, ID와 key pair를 고칩니다.

## AppProject와 Application permission boundary

AppProject와 Application은 credential이 아닌 선언형 desired state이므로
[`scripts/bootstrap/gitops/manifests`](../../../scripts/bootstrap/gitops/manifests)에 YAML로
추적합니다. Bootstrap script 내부에서 YAML 문자열을 생성하지 않습니다. 다만 최초
Application이 존재하기 전에는 Argo CD가 repository의 자기 선언을 읽어 적용할 수 없으므로,
GitOps `200`이 Repository Secret을 구성한 뒤 이 두 고정 manifest를 최초 한 번
server-side apply하고 fresh reconciliation까지 검증합니다. Script는 비밀 입력과 실행
순서를, YAML은 resource spec을 각각 소유합니다.

| Resource | 현재 계약 | 이유와 영향 |
| --- | --- | --- |
| Repository Secret | `car-community` project scope, exact Git URL | 다른 project가 credential을 일반 사용하지 않게 합니다. |
| AppProject | `car-community` | Application의 source·destination·cluster-scoped resource 경계를 분리합니다. |
| Source | Exact `https://github.com/example-org/car-community.git` | 다른 repository를 읽지 않습니다. Application path는 별도로 제한합니다. |
| Destination | In-cluster exact server·`car-community` namespace | 현재 application target 한 곳만 허용합니다. |
| Cluster-scoped allow | 없음 | `car-community` Namespace는 actual Secret을 적용하는 application bootstrap만 생성합니다. Argo CD가 owner를 나누지 않습니다. |
| Namespaced allow-list | Core `ConfigMap`·`Service`, `apps/Deployment`, `batch/Job`, `networking.k8s.io/Ingress` | Git manifest가 직접 소유하는 kind만 허용합니다. ALB·Target Group·listener·SG는 LBC가 AWS에 만들므로 Argo에 cluster-scoped wildcard가 필요하지 않고 Git 밖 Secret도 허용하지 않습니다. |
| Application | `car-community-stg`, `main`, exact `deploy/overlays/stg` | 현재 한 application·environment를 직접 표현합니다. |

Repository 연결 전에 GitOps bootstrap은 application bootstrap 소유 Namespace를 확인합니다.
Secret schema 검증을 repository credential 연결 책임에 중복하지 않으며, 누락 Secret은
PreSync Job 또는 Deployment의 resource reference 경계가 안전하게 차단합니다. Git-owned DB
URL·RDS CA·notification ConfigMap은 최초 Application sync에서 생성되므로 연결 전 live
prerequisite로 요구하지 않습니다.

## Automated reconciliation 계약

Application은 automated sync `enabled`, `prune`, `selfHeal`을 활성화하고 `allowEmpty`를
비활성화합니다.

- Merge된 Git 목표를 자동 적용합니다.
- Live drift를 Git 상태로 되돌립니다.
- Git에서 제거된 resource를 정리합니다.
- 잘못 비어 버린 overlay가 모든 application resource를 삭제하는 것은 막습니다.
- Notification credential operation이 current Secret revision으로 새 Pod를 만드는 annotation만
  diff에서 제외하고 `RespectIgnoreDifferences=true`로 sync에도 같은 경계를 적용합니다.

Repository credential을 최초 적용하거나 교체한 뒤 Secret informer를 소비하는 application-controller의
Pod template에 Secret `resourceVersion` generation을 기록해 새 controller revision을
강제합니다. 새 Pod가 Ready하고 informer initial sync를 마친 뒤 hard refresh가 소비되며
`reconciledAt`이 이전 값에서 바뀐 fresh reconciliation만 현재 credential 증거로 인정합니다.
이 과정에서 reconciliation은 잠시 멈추지만 기존 workload를 삭제·재시작하지 않습니다.

오래된 `Synced`·`Healthy` status만 보고 새 key 인증 성공으로 오인하지 않습니다. Fresh
reconcile 뒤 condition 없음, `Synced`, `Healthy`를 모두 확인합니다.
이 일반 consumer·freshness 계약을 사용하는 key 발급·revoke 순서는
[Repository credential rotation](330-argocd-repository-credential-rotation.md)이 소유합니다.

## Initial·recurring·change 경계

- Core component·CRD 최초 설치는 bootstrap 단계입니다.
- Repository Secret 구성, Git에 추적된 AppProject·Application의 최초 apply와 첫 sync는 별도 bootstrap 단계입니다.
  Private repository·GitHub App·non-empty overlay·node image pull이 준비돼야 완료할 수 있습니다.
- 이미 private repository와 Application이 연결된 뒤 AppProject permission만 늘리는
  change는 tracked YAML을 `car-community-bootstrap` field manager로 one-time
  server-side apply합니다. Repository Secret과 Application을 다시 소유하는 GitOps
  `200` 전체를 재실행하지 않습니다.
- 후속 digest 배포와 rollback은 GitOps PR 반복 lifecycle입니다.
- GitHub App private key 교체는 최초 resource를 재생성하지 않는 operation lifecycle입니다.

Core 설치는 image publish보다 먼저 해도 되지만 최초 Application `Synced`·`Healthy`에는
manifest가 참조하는 ECR image와 node pull이 필요합니다. Artifact 전달 순서와 실행 dependency는
다릅니다.

## Repository capability 경계

Repository source에는 runtime·migrator DB role과 Git 밖 prerequisite bootstrap,
Service·Deployment·단일 Migration PreSync Job, staging NodePort patch·Ingress와
Kustomize overlay가 있습니다. AWS Load Balancer Controller release는 Argo 소유가
아니라 별도 verified Helm bootstrap 소유입니다.
현재 repository에는 publish 결과 digest를 변경하는 pull request workflow와 Git write
identity, Argo full/HA·SSO·UI·API endpoint·multi-cluster가 구현되어 있지 않습니다. 이 미래
후보의 상태와 도입 순서는
[004 Infrastructure evolution backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
소유합니다.

AWS Load Balancer Controller·IAM·ACM·ALB·HTTPS·DNS의 live 완료는 repository capability가
아니며, local script·contract test와 외부 환경의 live 증거를 구분합니다. 현재 staging 실행 상태는
[Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md)가
소유합니다.

## 근거와 관련 문서

- **Core 최초 설치**: [Argo CD Core 설치 runbook](../../operations/340-argocd-core-installation-runbook.md)
- **현재 private repository 최초 연결**:
  [Application delivery 최초 bootstrap runbook](../../operations/350-application-delivery-bootstrap-runbook.md)
- **현재 staging 실행 상태**:
  [Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md)
- **Ingress permission·owner·live 구축 경계**:
  [170 AWS Load Balancer ingress](170-aws-load-balancer-ingress.md),
  [370 Staging 외부 HTTPS ingress 최초 구축 runbook](../../operations/370-staging-external-https-ingress-bootstrap-runbook.md)
- **반복 배포·rollback 원리**: [220 GitOps application delivery](220-gitops-application-delivery.md)
- **Argo CD 상태·reconcile 진단**:
  [180 Argo CD reconciliation 진단](../../operations/diagnostics/180-argocd-reconciliation.md)
- **Credential 교체 원리**:
  [Argo repository credential rotation](330-argocd-repository-credential-rotation.md)
- [Argo CD 설치 형태와 Core](https://argo-cd.readthedocs.io/en/stable/operator-manual/installation/)
- [Argo CD tested Kubernetes versions](https://argo-cd.readthedocs.io/en/stable/operator-manual/tested-kubernetes-versions/)
- [Argo CD declarative repository Secret](https://argo-cd.readthedocs.io/en/stable/operator-manual/declarative-setup/#repositories)
- [Argo CD AppProject](https://argo-cd.readthedocs.io/en/stable/operator-manual/project-specification/)
- [Argo CD automated sync](https://argo-cd.readthedocs.io/en/stable/user-guide/auto_sync/)
- [GitHub App permission](https://docs.github.com/en/apps/creating-github-apps/registering-a-github-app/choosing-permissions-for-a-github-app)
