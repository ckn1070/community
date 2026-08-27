# CI/CD pipeline

이 문서는 application 변경이 GitHub Actions의 검증과 image 게시를 거쳐, 사람이 승인한
GitOps 목표 상태와 Kubernetes workload로 이어지는 현재 CI/CD 흐름의 설명 SOT입니다.
Trigger, actor, job·step의 목적과 조건, 전달 artifact, 자동·수동 경계와 완료 의미를
한곳에서 소유합니다.

실제 실행 정의는 [CI workflow](../../../.github/workflows/ci.yml),
[Publish image workflow](../../../.github/workflows/publish-image.yml), `build.gradle`,
`Dockerfile`, `deploy/**`와 Argo CD Application manifest가 소유합니다. 이 문서는 source의
정확한 Action SHA·명령·manifest 전문을 복제하지 않고 각 단계의 의미와 handoff를 설명합니다.
도구별 선택 이유와 운영 절차는 관련 상세 SOT·runbook으로 연결합니다.

## 전체 흐름

```text
Pull request
  -> CI: application quality 검증
  -> review·merge

main push
  -> CI: application quality 검증
  -> before..after 변경 범위 분류
  -> SHA·run attempt에 결속한 publish decision artifact
  -> Publish image workflow
       -> deploy/** 전용 변경: privileged publish skip
       -> 그 밖의 변경: ARM64 image build 또는 기존 SHA image 재사용
       -> private ECR exact digest
  -> 운영자가 deploy/overlays/stg digest 변경 PR 작성·검증·merge
  -> Argo CD가 main의 staging overlay를 pull·render
  -> PreSync DB URL·RDS CA ConfigMap
  -> PreSync Migration Job
  -> Deployment·Service·Ingress sync
  -> Application condition 없음 + Synced + Healthy + target digest 실행
```

CI, image 게시와 배포는 서로 다른 사건입니다. CI 성공은 source 품질을, image 게시 성공은
ECR runtime artifact 생성을, GitOps merge는 staging deployment intent 승인을, Argo CD와
workload 상태는 live reconciliation을 각각 증명합니다.

## Event와 분기

| 입력 event | CI | Sonar | Publish decision | ECR publish | Staging sync |
| --- | --- | --- | --- | --- | --- |
| Same-repository pull request | 실행 | 실행 | 생성하지 않음 | 실행하지 않음 | 발생하지 않음 |
| Fork·Dependabot pull request | 기본 검증 실행 | Secret 부재 사유를 남기고 skip | 생성하지 않음 | 실행하지 않음 | 발생하지 않음 |
| `deploy/**`만 바뀐 `main` push | 실행 | 실행 | `publish=false` | Privileged job skip | 해당 merge를 Argo CD가 자동 sync |
| `deploy/**` 밖의 경로가 하나라도 있는 `main` push | 실행 | 실행 | `publish=true` | CI 성공 뒤 실행 | 새 digest를 승인하는 별도 PR merge 뒤 발생 |

Feature branch push만으로 CI를 별도 실행하지 않습니다. Pull request와 `main` push가 현재
workflow trigger이며, image 게시 workflow는 성공한 `main` push CI의 완료 event만 소비합니다.
두 번째 `main` 분기에는 application source뿐 아니라 docs·workflow·script 변경과 source·
`deploy/**` 혼합 변경도 포함됩니다. Classifier는 변경의 업무 의미가 아니라 모든 path가
`deploy/**` 아래인지로만 판정합니다. Source와 deployment intent를 한 PR에 섞는 것은 기술적으로
`publish=true`이지만 source PR과 digest 배포 PR을 분리하는 운영 원칙에는 어긋납니다.

## CI workflow

### Workflow 계약

| 항목 | 현재 계약 |
| --- | --- |
| Trigger | 모든 pull request의 open·synchronize·reopen, `main` push |
| Job | `Application quality` 하나 |
| Runner·timeout | `ubuntu-24.04`, 30분 |
| 기본 권한 | `contents: read` |
| 동시성 | 같은 PR의 이전 실행은 취소하고 `main` 실행은 exact revision 증거로 보존 |
| 완료 결과 | 기본 build·test·format·coverage와 적용 대상의 Sonar Quality Gate 성공; `main`은 publish decision artifact까지 생성 |

### `Application quality` step

| Step | 조건·입력 | 직접 하는 일 | 출력·다음 handoff | 실패 의미 |
| --- | --- | --- | --- | --- |
| `Checkout` | 현재 PR 또는 `main` revision | Full history를 credential persistence 없이 checkout | Sonar SCM과 뒤 Gradle·classifier가 읽을 source | Checkout 실패로 전체 job 중단 |
| `Set up Java` | Repository·Dockerfile의 Java 계약 | Temurin Java 21 toolchain 준비 | Gradle 실행 환경 | Toolchain 준비 실패 |
| `Set up Gradle` | Gradle Wrapper source | Wrapper 검증과 basic cache 준비; PR cache는 read-only | `./gradlew` 실행 환경 | Wrapper·cache 준비 실패 |
| `Verify Docker for PostgreSQL integration tests` | Runner Docker daemon | `docker info`로 daemon 연결을 확인 | Testcontainers 실행 가능 상태 | PostgreSQL 통합 test가 실행되지 않는 상태를 성공으로 통과시키지 않음 |
| `Run Gradle checks` | Source·JDK·Docker | `./gradlew check --rerun-tasks`로 test·architecture·format·JaCoCo 실행 | 검증 결과와 JaCoCo report | 어느 Gradle 검증이든 실패하면 중단 |
| `Determine Sonar execution scope` | Event, actor, head·current repository | Fork·Dependabot PR인지 판정 | `run`, `skip-reason` step output | 판정 script 실패 |
| `Explain untrusted pull request Sonar skip` | `run=false` | Secret을 받지 않는 PR의 skip 이유를 log에 기록 | 기본 CI만 완료 가능한 명시적 경계 | 예상하지 못한 skip 사유면 실패 |
| `Validate Sonar settings` | `run=true`, GitHub secret·variables | Organization·project·token 존재를 fail-close 확인 | Sonar 실행 admission | Trusted revision의 설정 누락은 CI 실패 |
| `Run SonarQube Cloud analysis` | `run=true`, 현재 `GITHUB_SHA` | Gradle scanner로 현재 revision을 분석하고 Quality Gate를 기다림 | Exact revision analysis와 Gate 결과 | 분석·timeout·Gate 실패를 CI 실패로 전파 |
| `Classify image publishing change` | `main` push의 `before`, `after`, run attempt | 전체 push diff를 `deploy/**` 전용인지 분류 | SHA·attempt·`publish` 세 line decision file | 분류 실패 시 게시 입력을 만들지 않음 |
| `Upload image publishing decision` | `main` push와 decision file | Exact SHA·attempt 이름의 artifact를 30일 보존 | `Publish image` classify job의 입력 | 누락·upload 실패 시 게시 workflow admission 실패 |

`./gradlew check`가 포함하는 도구·test·coverage 기준은
[310 코드 품질 도구](../../tech-stack/310-code-quality-tools.md)가 소유합니다. Classifier와
validator의 file 형식·검증 계약은 [Repository 품질 자동화](../../../scripts/quality/README.md)가
소유합니다.

## Publish image workflow

`Publish image`는 `CI` workflow가 `completed`가 되면 event를 받지만, 다음 조건을 모두
통과한 실행만 처리합니다.

- CI conclusion이 `success`
- 원래 event가 `push`
- head branch가 `main`
- head repository가 현재 repository와 같음

### `Classify image publishing change` job

| Step | 직접 하는 일 | 전달 결과 |
| --- | --- | --- |
| `Checkout successful CI decision validator` | CI가 검증한 exact SHA의 validator source를 read-only checkout | Validator 실행 source |
| `Download successful CI publishing decision` | Trigger한 exact CI run에서 SHA·attempt 결속 artifact 다운로드 | Decision file |
| `Validate successful CI publishing decision` | 일반 file·세 line·SHA·attempt·boolean을 fail-close 검증 | `publish` job output |

이 job은 `actions: read`, `contents: read`만 사용하며 AWS identity를 얻지 않습니다.

### `Publish ARM64 image` job

이 job은 위 `publish=true`와 네 trust 조건을 모두 통과할 때만 `id-token: write`를 받습니다.

| Step | 직접 하는 일 | 출력·다음 handoff |
| --- | --- | --- |
| `Validate publishing contract` | 40자리 SHA와 ECR push role ARN 형식 확인 | AWS admission 입력 |
| `Checkout successful CI revision` | 성공한 CI의 exact source revision checkout | Docker build source |
| `Configure AWS credentials` | GitHub OIDC로 제한된 ECR push role의 단기 credential 획득 | AWS API session |
| `Log in to private ECR` | Target private registry 인증 | Registry endpoint·Docker 인증 |
| `Reuse an existing immutable revision` | SHA tag가 이미 있으면 exact digest 조회 | 재실행 시 build·push skip 여부와 digest |
| `Set up Docker Buildx` | 기존 image가 없을 때 native ARM64 BuildKit 준비 | Image builder |
| `Read immutable image metadata` | Commit 시각을 OCI metadata 입력으로 읽음 | `CREATED` 값 |
| `Build and push ARM64 image` | Dockerfile로 `linux/arm64` image를 build하고 immutable SHA tag로 push | ECR digest |
| `Record published image` | 신규·기존 digest를 full ECR reference로 조립해 job summary에 기록 | GitOps digest PR 입력 |

같은 SHA의 workflow 재실행은 immutable tag를 다시 push하지 않고 기존 digest를 재사용합니다.
OIDC trust와 ECR push·pull IAM resource 관계는
[130 ECR·IAM image 공급 기반](130-ecr-iam-foundation.md), image identity·native ARM64 build와
kubelet pull 원리는 [210 Container image delivery](210-container-image-delivery.md)가
소유합니다.

## GitOps deployment handoff

Image 게시 뒤 staging 배포는 자동으로 시작되지 않습니다. 운영자가 job summary와 ECR에서
full exact digest를 확인한 뒤 `deploy/overlays/stg/kustomization.yaml`의 digest를 바꾸는
별도 pull request를 만들고 render·target dry-run·diff를 검토해 merge합니다. 이 수동 PR이
배포 승인과 rollback history의 경계입니다.

Merge 뒤에는 Argo CD가 read-only GitHub App으로 `main`의 `deploy/overlays/stg`를 pull하고
Kustomize render를 적용합니다. PreSync DB URL·RDS CA ConfigMap이 먼저 적용된 뒤 PreSync
Migration Job이 같은 image로 schema migration과 runtime 최소 권한 동기화를 마쳐야
Deployment·Service·Ingress sync가 진행됩니다. 완료에는 merge revision과 target revision
일치, condition 없음, `Synced`·`Healthy`, workload rollout과 Pod의 target digest 실행이
필요합니다. PreSync resource와 wave의 상세 계약은
[160 Argo CD platform](160-argocd-platform.md)이 소유합니다.

Desired state·migration·reconciliation·rollback의 상세 원리는
[220 GitOps application delivery](220-gitops-application-delivery.md), 실제 digest 변경·확인·
rollback 절차는 [420 GitOps 배포와 rollback](../../operations/420-gitops-deployment-and-rollback-runbook.md)이
소유합니다.

## Identity와 권한 전달

| Identity·artifact | 생산자 | 의미 | 최초 consumer |
| --- | --- | --- | --- |
| Source SHA | Git merge·push | Application source identity | CI checkout·Sonar·image tag |
| CI run attempt | GitHub Actions | 같은 SHA의 CI 재실행 identity | Decision artifact 이름·validator |
| Publish decision artifact | CI classifier | Exact push가 image 게시 대상인지 | Publish classify job |
| ECR SHA tag | Publish job | Source revision을 찾는 immutable label | ECR 조회·재실행 |
| ECR digest | Registry | Runtime content identity | GitOps digest PR·kubelet |
| `deploy/**` commit | PR reviewer·merge | 승인된 staging desired state | Argo CD |
| Argo target·sync revision | Argo CD | Git 목표와 적용 revision | 완료 확인 |
| Pod imageID | Container runtime | 실제 실행 content identity | 배포 postcondition |

GitHub Actions는 cluster credential을 소유하지 않고 Argo CD는 repository write 권한을
소유하지 않습니다. Kubelet의 ECR pull role과 application·migration database credential도
GitHub OIDC push role과 분리합니다.

## 실패와 복구 경계

| 실패 경계 | 중단되는 결과 | Canonical 복구 owner |
| --- | --- | --- |
| CI build·test·Sonar 실패 | Image 게시 admission 없음 | Source·설정 수정 뒤 새 CI |
| Decision artifact 누락·불일치 | Privileged publish 차단 | CI classifier·validator source와 같은 attempt 재실행 |
| OIDC·ECR build/push 실패 | Deployment digest 없음 | [400 GitHub ECR image 게시](../../operations/400-github-ecr-image-publishing-runbook.md) |
| Digest PR render·review 실패 | Git desired state가 바뀌지 않음 | PR에서 manifest 수정, merge 금지 |
| Argo repository·sync 실패 | Live state가 target revision으로 수렴하지 않음 | [180 Argo CD reconciliation 진단](../../operations/diagnostics/180-argocd-reconciliation.md) |
| Migration·workload 실패 | Runtime rollout 미완료 | [420 GitOps 배포와 rollback](../../operations/420-gitops-deployment-and-rollback-runbook.md) |
| 잘못된 정상 merge | 잘못된 desired state가 승인됨 | 이전 정상 digest를 복원하는 revert PR |

Live Deployment image나 Argo Application spec을 직접 수정하는 것은 복구 경로가 아닙니다.
Git desired state가 다시 덮어쓰고 배포 승인 history가 끊기기 때문입니다.

## Canonical owner와 상세 탐색

| 독자 질문 | Canonical owner |
| --- | --- |
| CI/CD 전체 흐름, 각 workflow step과 handoff는 무엇인가 | 이 문서 |
| Test·coverage·Sonar 도구와 품질 기준은 무엇인가 | [310 코드 품질 도구](../../tech-stack/310-code-quality-tools.md) |
| OIDC trust와 ECR push·pull IAM resource 관계는 무엇인가 | [130 ECR·IAM image 공급 기반](130-ecr-iam-foundation.md) |
| Image identity·ARM64 build·kubelet pull 원리는 무엇인가 | [210 Container image delivery](210-container-image-delivery.md) |
| Argo CD desired state·migration·rollback 원리는 무엇인가 | [220 GitOps application delivery](220-gitops-application-delivery.md) |
| CI·Sonar를 처음 설정하는 절차는 무엇인가 | [310 GitHub CI 품질 게이트 최초 설정](../../operations/310-github-ci-quality-configuration-runbook.md) |
| ECR 게시 설정과 반복 image 확인 절차는 무엇인가 | [320 GitHub ECR 게시 최초 설정](../../operations/320-github-ecr-publishing-configuration-runbook.md), [400 GitHub ECR image 게시](../../operations/400-github-ecr-image-publishing-runbook.md) |
| Digest를 staging에 배포·rollback하는 절차는 무엇인가 | [420 GitOps 배포와 rollback](../../operations/420-gitops-deployment-and-rollback-runbook.md) |
| 현재 staging에 실제로 무엇이 배포됐는가 | [100 Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md#현재-실행-상태) |

## 구현 source

- [CI workflow](../../../.github/workflows/ci.yml)
- [Publish image workflow](../../../.github/workflows/publish-image.yml)
- [Gradle build contract](../../../build.gradle)
- [Container image build](../../../Dockerfile)
- [Image publish classifier·validator](../../../scripts/quality/README.md)
- [Staging overlay](../../../deploy/overlays/stg/kustomization.yaml)
- [Argo CD Application](../../../scripts/bootstrap/gitops/manifests/car-community-stg-application.yaml)
- [Migration Job](../../../deploy/base/migration-job.yaml)
- [Runtime Deployment](../../../deploy/base/deployment.yaml)
