# Container image delivery

이 상세 SOT는 private ECR image의 artifact·identity·build representation과 단일 node kubelet의
exact digest pull·cache·검증 원리를 소유합니다. AWS
repository·IAM의 최초 기반은 [130 ECR·IAM 기반](130-ecr-iam-foundation.md), 사람이 설정·게시·
점검하는 절차는 관련 operations runbook이 소유합니다.

전체 CI/CD 순서와 workflow별 step은
[200 CI/CD pipeline](200-ci-cd-pipeline.md)에서 확인합니다. 여기서는 그 흐름을 반복하지 않고
image identity·build 선택과 node pull 계약만 상세화합니다.

Build/push와 runtime/pull은 서로 다른 실패·권한 경계이며 하나의 role·script로 합치지
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

## Image build 선택

Image는 target node와 같은 native ARM64 runner에서 만들며 QEMU instruction translation을
추가하지 않습니다. Buildx는 CPU emulation이 아니라 BuildKit builder, ECR direct push와
digest output을 담당합니다. Native ARM64 runner를 사용할 수 없거나 multi-platform consumer가
생기면 runner 분산과 emulation을 같은 입력·출력 기준으로 다시 비교합니다.

현재 `provenance: false`로 single-platform runtime manifest를 직접 게시하므로 ECR digest와
kubelet actual `imageID` digest를 같은 content identity로 비교합니다. Provenance가 불필요하다는
일반 결론은 아닙니다. Attestation을 도입하면 OCI index와 platform child manifest 중 어느
digest를 deployment·verification identity로 사용할지 함께 다시 결정합니다.

Publish trigger·trust gate·decision artifact·workflow step과 같은 SHA 재실행 순서는
[200 CI/CD pipeline](200-ci-cd-pipeline.md)이 단독으로 소유합니다.

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

Publish admission·OIDC·ECR build/push 실패 분류는
[200 CI/CD pipeline의 실패와 복구 경계](200-ci-cd-pipeline.md#실패와-복구-경계)가 소유합니다.

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
- Repository name·region 변경은 workflow, IAM ARN, provider match, smoke input과 현재 구성
  SOT를 한 변경에서 맞춥니다.
- Source repository·branch 변경은 OIDC `sub`, workflow gate와 branch policy를 함께 바꿉니다.
- Image digest의 승인·consumer·cluster sync는
  [220 GitOps application delivery](220-gitops-application-delivery.md)가 소유합니다.

Credential·delivery 자동화와 구성 소유권 후보의 상태는
[004 Infrastructure evolution backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
소유합니다.

## 증거와 공식 근거

Workflow syntax·contract test는 trigger·render·guard를 증명하지만 실제 AWS role assumption,
ECR push와 EC2 pull은 증명하지 않습니다. Live 완료에는 ECR image·job summary digest, kubelet
actual provider args, exact digest Job·imageID·cleanup이 필요합니다.

- [GitHub-hosted runner 사양](https://docs.github.com/actions/reference/runners/github-hosted-runners)
- [ECR login action](https://github.com/aws-actions/amazon-ecr-login)
- [Docker build-push action](https://github.com/docker/build-push-action)
- [Docker Buildx builder 구성](https://docs.docker.com/build/ci/github-actions/configure-builder/)
- [Kubelet credential provider](https://kubernetes.io/docs/tasks/administer-cluster/kubelet-credential-provider/)
- [AWS ECR credential provider source](https://github.com/kubernetes/cloud-provider-aws/tree/master/cmd/ecr-credential-provider)
- [ECR registry 인증](https://docs.aws.amazon.com/AmazonECR/latest/userguide/registry_auth.html)
