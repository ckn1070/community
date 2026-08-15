# Argo CD reconciliation 진단: Core·repository·render·sync·health

Kubernetes API가 동작하는 상태에서 Argo CD Core, private Git repository, manifest render,
sync, workload health 중 첫 실패 경계를 찾습니다. Git을 목표 상태의 source로 유지하고 live
Application·Deployment를 직접 수정하지 않습니다.

## 상단 수동 확장 조회

[000 Platform 진단 공통 실행 계약](000-index.md#공통-실행-계약)에 따라 아래 Bash block
전체를 한 번에 실행합니다.

```bash
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n argocd \
  get pods,deployments,statefulsets -o wide
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n argocd \
  get appproject.argoproj.io/car-community \
  -o 'jsonpath={range .spec.sourceRepos[*]}{"sourceRepo="}{.}{"\n"}{end}{range .spec.destinations[*]}{"destinationServer="}{.server}{" destinationNamespace="}{.namespace}{"\n"}{end}'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n argocd \
  get application.argoproj.io/car-community-stg \
  -o 'jsonpath={"repoURL="}{.spec.source.repoURL}{"\ntargetRevision="}{.spec.source.targetRevision}{"\npath="}{.spec.source.path}{"\ndestinationServer="}{.spec.destination.server}{"\ndestinationNamespace="}{.spec.destination.namespace}{"\nsync="}{.status.sync.status}{"\nhealth="}{.status.health.status}{"\nrevision="}{.status.sync.revision}{"\nreconciledAt="}{.status.reconciledAt}{"\noperationPhase="}{.status.operationState.phase}{"\n"}{range .status.conditions[*]}{.lastTransitionTime}{"\t"}{.type}{"\t"}{.message}{"\n"}{end}'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n argocd \
  get secret car-community-gitops-repository \
  -o 'custom-columns=NAME:.metadata.name,TYPE:.metadata.labels.argocd\.argoproj\.io/secret-type,OWNER:.metadata.labels.app\.kubernetes\.io/managed-by,RESOURCE_VERSION:.metadata.resourceVersion'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n argocd \
  logs statefulset/argocd-application-controller --since=60m --tail=240
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n argocd \
  logs deployment/argocd-repo-server --since=60m --tail=240
```

Repository Secret은 metadata만 조회하고 `.data`를 출력하지 않습니다.

## 해석 순서

1. Core Pod·controller·repo-server가 Ready인지 확인합니다.
2. Application source axis와 repository credential metadata를 대조합니다.
3. Condition에서 fetch·revision·path·render·permission 오류를 분류합니다.
4. `sync`, `health`, `revision`, `reconciledAt`을 서로 독립적으로 봅니다.
5. Controller와 repo-server log를 같은 reconcile 시각으로 연결합니다.

## Reconciliation 경로

```text
Application repoURL·targetRevision·path
→ repository credential 선택
→ repo-server Git fetch·Kustomize render
→ application-controller desired/live diff
→ sync가 Kubernetes API에 적용
→ resource health 집계
```

앞 경계가 실패하면 뒤 상태는 과거 값이거나 계산되지 않을 수 있습니다.

## Argo CD Core component

| Component | 역할 | 실패 신호 |
| --- | --- | --- |
| application-controller | Reconcile·diff·sync·health 상태 갱신 | `reconciledAt` 정체, 상태 미갱신 |
| repo-server | Git fetch·credential·manifest 생성 | Repository·revision·Kustomize 오류 |
| Redis | Argo CD cache | Controller·repo-server cache 오류 |
| ApplicationSet controller | ApplicationSet 처리 | 현재 단일 Application과 직접 무관할 수 있음 |

UI/API server는 현재 Core 설치 범위가 아닙니다.

## `Application`: source·destination 계약

| Field | 현재 계약 | 진단 질문 |
| --- | --- | --- |
| `repoURL` | 현재 `car-community` private repository | Credential URL과 일치하는가 |
| `targetRevision` | `main` | 기대 commit이 remote main에 있는가 |
| `path` | `deploy/overlays/stg` | Overlay가 존재하고 render되는가 |
| destination | 현재 cluster의 application namespace | AppProject 허용 범위와 일치하는가 |

Live Application을 patch해 임시 branch·path로 돌리지 않습니다.

## `kubectl`: Argo CD resource·log 조회 command

### `get pods,deployments,statefulsets`: Core component 상태 조회

#### Resource·option

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `pods,deployments,statefulsets` | Core runtime resource를 함께 조회 | Pod와 controller desired state가 일치하는가 |
| `-n argocd` | Argo CD namespace 제한 | Application workload와 섞이지 않았는가 |
| `-o wide` | Pod Node·IP 등 추가 column | 특정 Node에만 실패가 집중되는가 |

#### 출력 field

| Resource·field | 뜻 | 이상 신호 |
| --- | --- | --- |
| Pod `READY` | Ready container 수/전체 | `0/1` 또는 일부 container 미준비 |
| Pod `STATUS`·`RESTARTS` | 대표 상태와 restart 누적 | `Pending`·`CrashLoopBackOff`·계속 증가 |
| Deployment `READY` | Ready replica/desired replica | Desired보다 작고 회복되지 않음 |
| Deployment `UP-TO-DATE`·`AVAILABLE` | 최신 spec·사용 가능 replica | Rollout 정체 |
| StatefulSet `READY` | Ready Pod/desired Pod | application-controller rollout 미완료 |

Core component가 Ready가 아니면 Application 상태보다 component event·bounded log를 먼저
확인합니다.

### `get AppProject`: 허용 source·destination scope 조회

#### Output projection

`jsonpath`는 AppProject `.spec.sourceRepos`와 `.spec.destinations`만 읽습니다.

#### 출력 field

| 출력 field | 뜻 | 진단 질문 |
| --- | --- | --- |
| `sourceRepo` | Project가 허용한 repository URL pattern | Application `repoURL`이 허용되는가 |
| `destinationServer` | Project가 허용한 cluster API destination | Application destination server가 허용되는가 |
| `destinationNamespace` | Project가 허용한 namespace | `car-community` 배포가 허용되는가 |

### `get Secret`: Repository credential metadata 조회

#### Output projection

`custom-columns`는 Secret `.metadata`의 이름·label·resourceVersion만 읽고 `.data`를 제외합니다.

#### 출력 field

| 출력 field | 뜻 | 진단 질문 |
| --- | --- | --- |
| Secret `NAME` | Repository credential resource 이름 | 기대 resource가 존재하는가 |
| Secret `TYPE` | Argo CD repository Secret type label | Repository credential로 분류되는가 |
| Secret `OWNER` | 관리 주체 label | Bootstrap owner가 유지되는가 |
| Secret `RESOURCE_VERSION` | API object 갱신 generation | Credential apply 뒤 새 version인가 |

Secret metadata 존재는 private key 유효성이나 실제 fetch 성공을 증명하지 않습니다.
`.data`는 조회하지 않고 Application condition·repo-server log와 결합합니다.

### `get application`: status field 조회

#### Output projection

`jsonpath`는 source·destination spec과 reconcile status만 읽습니다.

#### 출력 field

| Field | 뜻 | 진단 질문 |
| --- | --- | --- |
| `spec.source.repoURL` | 실제 Git source repository | AppProject·credential URL과 일치하는가 |
| `spec.source.targetRevision` | 추적 Git revision | 현재 계약인 `main`인가 |
| `spec.source.path` | Repository 내부 manifest path | `deploy/overlays/stg`인가 |
| `spec.destination.server`·`namespace` | 배포 cluster·namespace | AppProject 허용 destination인가 |
| `status.sync.status` | Git 목표와 live spec 일치 여부 | `Synced`, `OutOfSync`, `Unknown` 중 무엇인가 |
| `status.health.status` | Managed resource health 집계 | `Healthy`, `Progressing`, `Degraded` 중 무엇인가 |
| `status.sync.revision` | 비교한 Git revision | 기대 main SHA인가 |
| `status.reconciledAt` | 마지막 reconcile 시각 | 장애 뒤 새 reconcile이 있었는가 |
| `operationState.phase` | Sync operation 상태 | `Running`, `Succeeded`, `Failed`인가 |
| `status.conditions`의 `lastTransitionTime`·`type`·`message` | Condition 전환 시각과 comparison·repository·render 오류 | 첫 실패가 언제 어떤 type과 message로 기록됐는가 |

#### Sync·Health 조합

| Sync | Health | 의미 |
| --- | --- | --- |
| `Synced` | `Healthy` | Git과 live가 일치하고 resource도 정상 |
| `OutOfSync` | `Healthy` | Workload는 동작하지만 spec 차이 존재 |
| `Synced` | `Progressing`·`Degraded` | 적용됐지만 rollout·Job·readiness 실패 |
| `Unknown` | Any | Repository·render·API 비교 자체 실패 가능 |

`reconciledAt`은 성공 보증이 아니며 operation이 없으면 phase는 비어 있을 수 있습니다.

### `logs`: controller·repo-server log 조회

#### Target·option

| Option·target | 뜻 | 진단 질문 |
| --- | --- | --- |
| `statefulset/argocd-application-controller` | Reconcile·diff·sync·health 계산 log | 상태 갱신이 어디서 실패했는가 |
| `deployment/argocd-repo-server` | Git fetch·render log | Credential·revision·path·render 중 어디인가 |
| `--since=60m` | 최근 시간 범위 | Incident 시각을 포함하는가 |
| `--tail=240` | 마지막 행 수 제한 | 출력 상한을 지켰는가 |

#### 출력·민감정보 경계

Controller·repo-server의 자유 형식 log에는 repository URL·revision·path와 오류 message가
포함될 수 있습니다. Secret `.data`를 직접 조회하지는 않지만 producer가 남긴 token 조각을
완전히 배제할 수 없으므로 local mode `0600`으로 보존하고 외부 공유 전에 가립니다.

## Condition·log: 첫 실패 분류

| Condition·log 단서 | 첫 경계 |
| --- | --- |
| Authentication·repository not found | GitHub App·credential·repoURL |
| Revision not found | Remote `main`·commit |
| Path does not exist·Kustomize build 실패 | `deploy/overlays/stg` source |
| Permission denied applying resource | AppProject·Argo service account RBAC |
| Comparison error + API timeout | [160 Kubernetes control plane 진단](160-kubernetes-api-control-plane-and-node.md) |
| Sync 성공 + health degraded | [170 Kubernetes workload 진단](170-kubernetes-workload-network-events-and-logs.md) |

Application `reconciledAt`·condition 전환 시각, controller 요청의 repository·revision·path,
repo-server fetch·render 결과를 같은 시간대에서 연결합니다. TLS handshake timeout 한 번만으로
credential 오류를 확정하지 않습니다.

## Repository credential: 존재와 사용 결과 분리

`car-community-gitops-repository` Secret의 type·owner label·resourceVersion은 resource 존재와
소유자를 보여주지만 private key 유효성을 증명하지 않습니다. 실제 fetch 결과와 GitHub App
설정을 함께 봅니다. Credential 변경은 bootstrap·rotation runbook이 소유합니다.

## 증거 조합과 다음 분기

| 함께 보이는 증거 | 다음 분기 |
| --- | --- |
| Core Pod restart·not ready | [340 Argo CD Core 설치](../340-argocd-core-installation-runbook.md) |
| Credential·fetch 실패 | [350 Application delivery 최초 bootstrap](../350-application-delivery-bootstrap-runbook.md) 또는 [520 Repository credential 교체](../520-argocd-repository-credential-rotation-runbook.md) |
| Render 실패 | Git overlay를 local `kubectl kustomize`로 검증 |
| `OutOfSync` + comparison 정상 | 승인된 main revision·sync policy |
| `Synced` + `Degraded` | [170 Kubernetes workload 진단](170-kubernetes-workload-network-events-and-logs.md) |
| API timeout 동반 | [160 Kubernetes control plane 진단](160-kubernetes-api-control-plane-and-node.md) |

## 금지 경계·관련 문서

Application·AppProject·Secret patch/delete, hard refresh annotation, controller restart, live image
변경을 하지 않습니다.

- **전체 outline**: [100 Kubernetes platform 핵심 진단 outline](100-kubernetes-platform-diagnostic-outline.md)
- **Argo CD 구조**: [150 Argo CD platform](../../project/infrastructure/150-argocd-platform.md)
- **GitOps delivery**: [210 GitOps application delivery](../../project/infrastructure/210-gitops-application-delivery.md)
- **배포·rollback**: [420 GitOps 배포와 rollback](../420-gitops-deployment-and-rollback-runbook.md)

## 공식 참고

- [Argo CD architecture](https://argo-cd.readthedocs.io/en/stable/operator-manual/architecture/)
- [Argo CD Application specification](https://argo-cd.readthedocs.io/en/stable/user-guide/application-specification/)
- [Argo CD private repositories](https://argo-cd.readthedocs.io/en/stable/user-guide/private-repositories/)
