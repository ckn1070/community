# Container image delivery

이 문서는 검증된 `main` revision이 private ECR image가 되고 단일 node kubelet이 exact digest로
pull하기까지 반복 delivery의 actor·artifact·identity·cache·검증 원리를 소유합니다. AWS
repository·IAM의 최초 기반은 [ECR·IAM 기반](120-ecr-iam-foundation.md), 사람이 설정·게시·
점검하는 절차는 관련 operations runbook이 소유합니다.

## End-to-end 흐름

```text
car-community main push with a non-deploy change
  -> CI success for exact 40-char SHA
  -> Publish image path classification
     -> GitHub OIDC short-lived push role
     -> linux/arm64 image
     -> immutable <SHA> tag + sha256 digest
  -> GitOps manifest consumes ECR@sha256:<digest>
  -> kubelet asks exec credential provider
     -> EC2 instance profile temporary credential
     -> ECR authorization token
  -> containerd pulls exact digest
```

Build/push와 runtime/pull은 서로 다른 실패·권한 경계입니다. 하나의 role·script로 합치지
않습니다. Image 공급 완료는 application Deployment·RDS·Ingress·GitOps 승인 완료를 뜻하지
않습니다.

## Artifact identity 선택

| 항목 | 현재 선택 | 이유와 영향 | 재검토 조건 |
| --- | --- | --- | --- |
| Registry | Private ECR `car-community` | 같은 AWS account·region에서 IAM push/pull을 분리하고 public 공개를 피합니다. Environment 공용 application artifact 저장소입니다. | Registry·account·region 변경 |
| Platform | `linux/arm64` 하나 | 현재 T4g node에서 직접 실행할 image만 만들고 실제 x86 요구 없이 multi-arch 비용·검증을 늘리지 않습니다. | x86 node·multi-arch consumer 등장 |
| Publish revision | 성공한 `main` CI의 exact 40자리 SHA 중 `deploy/**` 전용 변경 제외 | PR·실패 revision과 deployment-config-only revision을 publish하지 않고 application source와 image를 일대일 추적합니다. | Release branching·repository 분리 정책 변경 |
| Tag | Git SHA, immutable | 사람이 source revision을 찾게 하되 같은 tag가 다른 byte를 가리키지 못합니다. `latest`를 사용하지 않습니다. | Mutable label 요구가 아니라 release identity 전체 재설계 시 |
| Runtime reference | ECR digest | Content identity로 workload input을 고정합니다. GitOps manifest도 digest를 사용합니다. | OCI index·attestation 기준 변경 |
| OCI metadata | `REVISION=<SHA>`, `CREATED=<commit timestamp>`, `SOURCE_DIRTY=false` | Build source provenance를 image label에 연결합니다. | Metadata contract 변경 |

Tag는 검색 가능한 source label이고 digest는 실제 runtime content identity입니다. 둘은 서로
대체하지 않습니다.

## Publish workflow 권한과 trigger

Application `CI`는 품질을 검증하고 main push의 전체 변경 범위를 분류하되 `contents: read`만
사용합니다. 별도 `Publish image` workflow만 `id-token: write`로 AWS OIDC role을 assume합니다.
두 workflow를 합치지 않아 PR test와 경로 분류가 privileged AWS identity를 얻지 않습니다.

`workflow_run: completed` trigger 뒤 classify job은 다음 네 trust 조건을 **모두** 만족할
때만 실행합니다.

1. 원래 CI conclusion이 `success`입니다.
2. 원래 event가 `push`입니다.
3. Head branch가 `main`입니다.
4. Head repository가 현재 repository와 같습니다.

CI의 main push event가 소유하는 `before..after` 전체 범위를 실행 가능한 classifier로
분류합니다. 마지막 commit의 parent만 보지 않으므로 rebase merge와 한 push의 여러
commit에서도 앞선 source 변경을 누락하지 않습니다. Root push의 zero `before`는 empty
tree로 바꿔 같은 경로 규칙을 적용합니다. 변경이 모두 `deploy/**`이면 `publish=false`, 그
밖의 경로가 하나라도 있으면 `publish=true`입니다. Rename은 `--no-renames`로 old·new path를
각각 판정해 source 경로가 사라지는 변경을 deploy-only로 오인하지 않습니다.

Classifier는 exact head SHA·CI run attempt·`publish` 세 line을 SHA와 attempt로 이름을
고정한 artifact에 남깁니다. `workflow_run`의 classify job은 trigger한 exact 성공 CI
run·attempt의 artifact만 받고, 같은 revision에서 checkout한 validator로 일반 file·세 line·
SHA·attempt·boolean을 검증합니다. `publish=false`이면 AWS OIDC 권한이 있는 publish job을
skip합니다. 따라서 PR·fork·실패한 CI와 deploy-only push는 AWS token을 받는 job으로
들어오지 않습니다. CI 자체와 `workflow_run` event·classify job은 deploy-only 변경에도
실행될 수 있으며, 이를 image가 게시됐다는 뜻으로 사용하지 않습니다.

Publish job은 `workflow_run.head_sha`를 exact checkout하며 checkout·artifact·AWS·ECR·Buildx
action도 release tag가 아니라 검토한 action commit SHA로 고정합니다.

Publish job은 GitHub-hosted `ubuntu-24.04-arm` runner에서 target과 같은 ARM64 명령을 직접
실행합니다. 단일 ARM64 image만 필요한 현재 경계에서는 x86 runner와 QEMU instruction
translation을 추가하지 않습니다. Buildx는 CPU emulation이 아니라 BuildKit builder 준비,
ECR 직접 push와 게시 digest output을 담당하므로 유지합니다. Native ARM64 runner를 사용할 수
없거나 실제 multi-platform consumer가 생기면 runner 분산과 emulation을 같은 시작·종료
상태에서 다시 비교합니다.

현재 `provenance: false`를 사용해 single-platform runtime manifest를 직접 게시하므로 workflow
digest와 kubelet actual `imageID` digest를 같은 값으로 비교할 수 있습니다. Provenance가
불필요하다는 일반 결론이 아닙니다. Attestation을 도입하면 OCI index digest와 platform child
manifest digest 중 어느 값을 deployment·verification identity로 쓸지 함께 다시 결정합니다.

## Immutable revision 재실행

GitHub는 원래 run 뒤 30일까지 workflow를 재실행할 수 있고 재실행은 같은 SHA·ref에 새
`run_attempt`를 부여합니다. CI decision artifact도 SHA뿐 아니라 attempt를 이름과 payload에
포함하고 30일 보존하므로, 같은 CI run을 다시 실행해도 이전 immutable artifact와 충돌하지
않습니다. `Publish image` 재실행은 trigger한 exact CI run·attempt의 decision만 다시
사용합니다.

같은 SHA의 publish job을 다시 실행할 때 immutable tag를 overwrite하지 않습니다.

- ECR `BatchGetImage`로 SHA tag의 현재 digest를 조회합니다.
- 이미 있으면 digest 형식을 검증하고 build·push를 생략합니다.
- 없으면 exact source를 build·push합니다.
- 최종 job summary는 revision tag와 deployable `repository@sha256:digest`를 남깁니다.

이는 harmless 재실행을 허용하는 상태 확인입니다. 별도 marker나 build cache를 source of
truth로 만들지 않습니다. 같은 SHA에 다른 byte가 필요하다는 상황은 source identity 위반이므로
강제 push하지 않고 새 commit으로 해결합니다.

## Node credential provider mental model

Kubelet은 registry pull을 containerd에 요청할 때 configured exec credential provider를
실행합니다. Official AWS provider는 AWS SDK credential chain으로 EC2 instance profile의
temporary role credential을 얻고 ECR authorization token을 반환합니다.

```text
kubelet
  -> ecr-credential-provider process
     -> EC2 instance profile temporary credential
     -> ECR GetAuthorizationToken
     -> registry credential response + cache duration
  -> containerd image pull
```

ECR authorization token의 최대 유효기간은 12시간입니다. Provider는 실제 `ExpiresAt`까지
남은 시간의 절반을 kubelet cache duration으로 반환합니다. Provider configuration의 필수
`defaultCacheDuration: 12h`는 response에 cache duration이 없을 때 쓰는 fallback이며 token
수명을 늘리거나 항상 12시간 cache한다는 뜻이 아닙니다.

현재 provider는 Kubernetes `1.36.2`와 같은 release line의 official AWS provider `v1.36.1`
ARM64 artifact, SHA-256 `96b58e...ed62`를 고정합니다. Kubelet은 다음 두 flag로 binary
directory와 stable v1 config를 사용합니다.

- `--image-credential-provider-bin-dir`
- `--image-credential-provider-config`

Provider·config·kubelet defaults 최초 구성은 bootstrap lifecycle이고, image pull 확인은
반복 verify lifecycle입니다. Provider 설치가 성공한 뒤 IAM·image 문제로 pull이 실패하면
provider를 반복 설치하지 않고 verify만 재실행합니다.

## `imagePullSecret`을 사용하지 않는 이유

- 최대 12시간 ECR login token을 Kubernetes Secret으로 복사하는 CronJob을 만들지 않습니다.
- 모든 namespace의 ServiceAccount 연결·secret rotation·노출 경계를 만들지 않습니다.
- AWS static access key를 node filesystem이나 Kubernetes API에 저장하지 않습니다.
- EC2 role policy를 target ECR read로 제한합니다.

EC2 role은 node 전체 identity이므로 Pod별 AWS authorization을 제공하지 않습니다. 그러나
현재 목적은 kubelet의 registry pull 하나입니다. Application workload 권한을 이 role에
추가하지 않고 Pod identity를 별도로 설계합니다.

IMDSv2 required와 EC2 metadata hop limit `2`는 기존 node 계약을 유지합니다. Provider가 host
process라는 이유만으로 hop limit을 바꾸지 않습니다. Hop limit `2` 자체는 container network의
IMDS 가능성을 넓히므로 Pod IMDS가 불필요해지면 node 보안 설계에서 `1`을 재검토합니다.

## Exact digest pull smoke의 의미

반복 verify는 immutable digest reference만 받고 다음 경로를 실제로 통과합니다.

1. 고정 smoke namespace와 `imagePullPolicy: Always` Job을 만듭니다.
2. Kubelet이 local layer cache가 있어도 registry에서 digest name을 확인합니다.
3. Job complete와 actual Pod `imageID`가 요청 digest와 일치하는지 확인합니다.
4. Java application image의 `java -version` log를 확인합니다.
5. Smoke namespace를 삭제합니다.

따라서 IAM, provider, DNS·network, ECR registry, containerd, scheduler와 실제 container
실행을 함께 검증합니다. Application readiness·RDS connection은 검증하지 않습니다.

## 실패·복구 경계

| 실패 | 먼저 확인할 경계 | 하지 않는 우회 |
| --- | --- | --- |
| Publish gate에서 PR·fork가 실행 | Workflow four-gate와 OIDC trust | Broad trust·static key 추가 |
| Deploy-only push에서 publish job 실행 | CI `before..after` 분류 artifact와 publish job guard | CI 전체 생략·AWS role 발급 후 조건 처리 |
| 분류 artifact 누락·SHA·attempt/형식 불일치 | Trigger한 exact CI run·attempt·head SHA, 30일 보존 artifact upload/download | 마지막 commit만 다시 계산·publish 강행 |
| Role ARN·OIDC assume 실패 | Repository variable, audience, exact `sub`, `id-token: write` | Long-lived AWS key |
| ECR AccessDenied | `GetAuthorizationToken`, exact repository actions·ARN | ECR full-access 관리 policy |
| Build 실패 | Dockerfile·source를 새 commit에서 수정 | 같은 SHA에 다른 byte 강제 push |
| Provider checksum 실패 | Official release byte와 repository hash | 임의 checksum 교체 |
| Provider 기존 file drift | Owner·systemd command line·세 file 계약 | 자동 merge·overwrite |
| `401`·`403`·ImagePullBackOff | Instance profile, token, read actions, exact URI | `imagePullSecret`·push 권한 추가 |
| Smoke timeout·failure | Job·Pod·event, API·network·runtime | Provider 재설치부터 반복 |
| Smoke namespace 선존재 | 실제 owner·resource 확인 | 알 수 없는 namespace 자동 삭제 |
| Cleanup 실패 | Namespace 삭제 원인 | 성공으로 판정 |

Provider download와 verify API 작업은 무기한 기다리지 않고 bounded timeout과 transient
retry만 사용합니다. Exact 시간·횟수·HTTP 분류는
[ECR bootstrap script 계약](../../../scripts/bootstrap/ecr/README.md)이 소유합니다. Create
요청 timeout은 적용 여부를 추측하지 않고 실제 namespace를 조회합니다.

## Initial과 recurring operation

| 생애주기 | 사람 목표 | 운영 절차 |
| --- | --- | --- |
| 최초 GitHub/ECR 연결 | Repository variable과 publish 입력을 구성 | [GitHub ECR publish 구성 runbook](../../operations/320-github-ecr-publishing-configuration-runbook.md) |
| 최초 node 구성 | Kubelet credential provider 설치 | [Kubelet ECR provider 설치 runbook](../../operations/330-kubelet-ecr-credential-provider-installation-runbook.md) |
| 매 publish | 성공한 main SHA image·digest 확인 | [GitHub ECR image publish runbook](../../operations/400-github-ecr-image-publishing-runbook.md) |
| 필요 시 반복 점검 | Node exact digest pull·실행 확인 | [Private ECR pull verify runbook](../../operations/410-private-ecr-image-pull-verification-runbook.md) |

## 변경·폐기 책임

- Kubernetes minor·provider artifact·node architecture 변경은 provider version·checksum,
  publish platform과 live pull smoke를 함께 갱신합니다.
- Repository name·region 변경은 workflow, IAM ARN, provider match, smoke input과 Terraform
  target을 한 변경에서 맞춥니다.
- Source repository·branch 변경은 OIDC `sub`, workflow gate와 branch policy를 함께 바꿉니다.
- Image digest의 승인·consumer·cluster sync는
  [GitOps application delivery](210-gitops-application-delivery.md)가 소유합니다.

## 증거와 공식 근거

Workflow syntax·contract test는 trigger·render·guard를 증명하지만 실제 AWS role assumption,
ECR push와 EC2 pull은 증명하지 않습니다. Live 완료에는 ECR image·job summary digest, kubelet
actual provider args, exact digest Job·imageID·cleanup이 필요합니다.

- [GitHub `workflow_run`](https://docs.github.com/actions/using-workflows/events-that-trigger-workflows#workflow_run)
- [GitHub workflow artifact 공유](https://docs.github.com/actions/using-workflows/storing-workflow-data-as-artifacts)
- [GitHub push webhook payload](https://docs.github.com/webhooks/webhook-events-and-payloads#push)
- [GitHub workflow 재실행](https://docs.github.com/actions/how-tos/manage-workflow-runs/re-run-workflows-and-jobs)
- [GitHub upload-artifact 계약](https://github.com/actions/upload-artifact/blob/main/README.md)
- [GitHub Actions OIDC로 AWS 인증](https://docs.github.com/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)
- [GitHub-hosted runner 사양](https://docs.github.com/actions/reference/runners/github-hosted-runners)
- [ECR login action](https://github.com/aws-actions/amazon-ecr-login)
- [Docker build-push action](https://github.com/docker/build-push-action)
- [Docker Buildx builder 구성](https://docs.docker.com/build/ci/github-actions/configure-builder/)
- [Kubelet credential provider](https://kubernetes.io/docs/tasks/administer-cluster/kubelet-credential-provider/)
- [AWS ECR credential provider source](https://github.com/kubernetes/cloud-provider-aws/tree/master/cmd/ecr-credential-provider)
- [ECR registry 인증](https://docs.aws.amazon.com/AmazonECR/latest/userguide/registry_auth.html)
