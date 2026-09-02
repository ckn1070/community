# Argo CD GitOps bootstrap script

이 디렉터리는 단일 node Kubernetes cluster에 headless Argo CD Core를 설치하고 현재
private application repository의 GitOps 경로를 **최초 연결**하는 두 bootstrap script를
소유합니다. 구축 완료 뒤
GitHub App key를 교체하는 반복 운영 작업은
[operations/gitops](../../operations/gitops/README.md)가 소유합니다.

최초 platform 선택과 actor 계약은
[160 Argo CD platform](../../../docs/project/infrastructure/160-argocd-platform.md), 반복 delivery 원리는
[220 GitOps application delivery](../../../docs/project/infrastructure/220-gitops-application-delivery.md)가
소유합니다. 실제 최초 절차는
[Argo CD Core installation](../../../docs/operations/340-argocd-core-installation-runbook.md)과
[Application delivery 최초 bootstrap](../../../docs/operations/350-application-delivery-bootstrap-runbook.md),
반복 배포·rollback은
[GitOps deployment and rollback](../../../docs/operations/420-gitops-deployment-and-rollback-runbook.md)이
소유합니다.

## 단계와 복구 경계

| 순서 | Script | 독립 완료 결과 |
| --- | --- | --- |
| `100` | `100-install-argocd-core.sh` | Argo CD Core `3.5.0` component와 CRD가 동작합니다. |
| `200` | `200-connect-private-gitops-repository.sh` | GitHub App credential, 제한된 AppProject와 Application의 최초 sync가 성공합니다. |

Core 설치는 public artifact만 필요하지만 최초 연결은 private repository와 GitHub App이
필요합니다. `200` 실패가 `100` 재실행을 요구하지 않으므로 복구 경계를 나눕니다. `200`의
부분 실패는 같은 목표로 재실행할 수 있지만 세 GitOps resource가 모두 존재하고
Application이 `Synced`·`Healthy`면 최초 구축이 완료된 상태이므로 재실행을 거부합니다.
그 뒤 credential 교체에는 operations script를 사용합니다.

## `100-install-argocd-core.sh` 공개 계약

| 항목 | 계약 |
| --- | --- |
| caller·시점 | 단일 ARM64 Kubernetes bootstrap `100`~`500`의 live postcondition 완료 뒤 실행합니다. |
| 실행 | EC2 전달 root에서 `sudo ./bootstrap/gitops/100-install-argocd-core.sh` |
| version | Kubernetes `1.36` tested 범위를 포함하는 Argo CD exact `3.5.0` |
| artifact | Official `core-install.yaml` SHA-256 `166bff...b0747` 검증 뒤 ARM64 child digest로 render |
| 직접 변경 | `argocd` namespace, Core CRD·RBAC·ConfigMap·Secret·NetworkPolicy·Deployment·StatefulSet |
| 제외 | `argocd-server`, UI, API endpoint, SSO, repository credential |
| 성공 | Core rollout, component spec·Pod imageID exact digest, Application·AppProject CRD 확인 |

Runtime은 Argo CD `sha256:8a1492...6079b`, Redis
`sha256:2fe148...5489` ARM64 child digest로 고정합니다. Download는 연결 `15s`, 단일 전송과
전체 retry window `300s`, transient retry `3회`로 제한합니다. Rollout은 `300s`, API
request는 `330s`입니다. 다른 namespace owner, full 설치, 다른 image version·digest는
자동 인수·upgrade·downgrade하지 않습니다.

## `200-connect-private-gitops-repository.sh` 공개 계약

| 항목 | 계약 |
| --- | --- |
| caller·시점 | Core와 application bootstrap 완료, private repository·read-only GitHub App·non-empty staging overlay 준비 뒤 최초 한 번 실행합니다. |
| 공개 입력 | `<GITHUB_APP_ID> <GITHUB_APP_INSTALLATION_ID>` 두 숫자 |
| 비밀 입력 | GitHub App PEM key를 stdin으로만 전달하며 argument·environment·repository file로 받지 않습니다. |
| repository | `https://github.com/example-org/car-community.git`, `main`, `deploy/overlays/stg` |
| 선언형 source | `manifests/car-community-appproject.yaml`, `manifests/car-community-stg-application.yaml` |
| 직접 변경 | Repository Secret, application-controller StatefulSet Pod template generation annotation, Git에 추적된 AppProject·Application manifest의 server-side apply |
| sync | `enabled`, `prune`, `selfHeal`, `allowEmpty: false`, Community·notification Secret rollout annotation ignore + `RespectIgnoreDifferences=true` |
| 성공 | 새 credential consumer rollout과 fresh reconcile 뒤 condition 없음, `Synced`·`Healthy` |

`car-community-gitops-repository`는 Kubernetes Repository Secret의 resource 이름이며 별도 Git
repository 이름이 아닙니다. 현재 Secret의 URL은 `car-community.git`로 고정합니다.

Private key는 `/run/car-community-argocd-github-app.*` 임시 directory의 mode `0600` file로만
materialize하고 `openssl pkey -noout -check`로 형식을 검증한 뒤 Secret apply 직후 삭제합니다.
중간 실패도 종료 trap이 임시 directory를 정리합니다. Secret에는 credential이 지속되며
self-managed etcd encryption at rest는 별도 책임입니다. Secret `resourceVersion`을 newline
없는 base64 token으로 바꿔 application-controller Pod template generation annotation에
기록하고 새 revision을 강제합니다. Informer initial sync 뒤 hard refresh가 소비된 fresh
reconcile만 성공으로 인정합니다. Core owner와 동일 이름 resource owner가 다르면 중단하며
자동 삭제·reset하지 않습니다.

AppProject와 Application은 비밀정보가 아니므로 Shell heredoc으로 생성하지 않습니다.
두 resource의 desired state는 `manifests/`의 YAML이 소유하고, `200`은 최초 bootstrap의
순서·credential 처리·적용·검증만 orchestration합니다. 최초 Application이 아직 없을
때에는 Argo CD가 자기 자신을 Git에서 생성할 수 없으므로 이 두 manifest를 `200`이 한 번
server-side apply합니다. 이후 application workload의 목표 상태는 Application이 가리키는
`deploy/overlays/stg`에서 관리합니다.

Application bootstrap 소유 `car-community` Namespace가 없으면 GitOps resource를 적용하지
않습니다. Secret schema는 repository 연결 script가 중복 검사하지 않고, 누락된 Secret은
Job·Deployment의 resource reference가 차단합니다. DB URL·RDS CA와 notification 공개 설정
ConfigMap은 staging overlay가 최초 sync에서 적용합니다. Git에 추적된 AppProject manifest는 cluster-scoped resource를
허용하지 않고 Git manifest에 필요한 `ConfigMap`·`ServiceAccount`·`Service`·`Deployment`·
`Job`·`networking.k8s.io/Ingress`만 허용합니다. Staging overlay의 `Ingress`는 AWS Load Balancer
Controller가 public ALB를 만들 desired state이고, controller의 cluster 권한과 설치는
AppProject 허용 범위가 아닙니다.
Notification operation이 새 Pod를 만드는 annotation만 `RespectIgnoreDifferences` 경계로
보존합니다.

## Local 검증

```bash
bash -n \
  scripts/bootstrap/gitops/100-install-argocd-core.sh \
  scripts/bootstrap/gitops/200-connect-private-gitops-repository.sh \
  scripts/bootstrap/gitops/tests/test-100-install-argocd-core.sh \
  scripts/bootstrap/gitops/tests/test-200-connect-private-gitops-repository.sh

shellcheck -x -P SCRIPTDIR \
  scripts/bootstrap/gitops/100-install-argocd-core.sh \
  scripts/bootstrap/gitops/200-connect-private-gitops-repository.sh \
  scripts/bootstrap/gitops/tests/test-100-install-argocd-core.sh \
  scripts/bootstrap/gitops/tests/test-200-connect-private-gitops-repository.sh

scripts/bootstrap/gitops/tests/test-100-install-argocd-core.sh
scripts/bootstrap/gitops/tests/test-200-connect-private-gitops-repository.sh
```

Local 검증은 version·checksum·owner guard·secret 전달과 mutation 순서만 증명합니다. 실제
artifact download, cluster rollout, GitHub App 인증과 최초 sync는 target postcondition으로만
완료 판정합니다.
