# GitHub ECR image 게시 runbook

Application source 변경을 PR로 `main`에 merge한 뒤, 성공한 `CI` revision의 private ECR
ARM64 image 게시를 관찰하고 exact deployment digest를 확보하는 반복 절차입니다. 최초
repository variable 설정은
[GitHub ECR 게시 최초 설정](320-github-ecr-publishing-configuration-runbook.md)이
소유합니다.

Workflow의 actor·OIDC 경계·immutable tag와 digest 전달 방식은
[Container image delivery](../project/infrastructure/200-container-image-delivery.md), 실제
workflow step과 condition은 [Publish image source](../../.github/workflows/publish-image.yml)에서
확인합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Application source PR을 `main`에 merge한 뒤 exact deployment digest가 필요할 때 반복합니다. `deploy/**` 전용 변경에는 실행 결과만 관찰하고 새 digest를 기대하지 않습니다. |
| 누가 | Pull request를 merge하고 GitHub Actions와 target ECR repository를 읽을 수 있는 운영자가 확인합니다. |
| 어디서 | GitHub `ckn1070/community → Actions`와 AWS Console `ECR → car-community`입니다. |
| 무엇을 | GitHub의 `CI` 성공으로 자동 시작된 `Publish image` workflow를 관찰합니다. 수동 shell script를 실행하지 않습니다. |
| 왜 | 성공한 exact source revision, immutable ECR tag와 배포할 digest를 하나의 증거로 결속하기 위해서입니다. |
| 완료·재개 | Workflow summary와 ECR이 같은 digest를 가리키면 완료입니다. 실패하면 같은 revision의 해당 workflow 경계에서 재실행하거나 새 source PR로 복구합니다. |

## 사용 시점과 전제

- AWS `ap-northeast-2`의 tag immutable private ECR repository 이름은 `car-community`입니다.
- GitHub repository variable `AWS_ECR_PUSH_ROLE_ARN`이 target OIDC push role ARN으로
  [최초 설정](320-github-ecr-publishing-configuration-runbook.md)됐습니다.
- `.github/workflows/ci.yml`의 workflow 이름은 `CI`이고
  `.github/workflows/publish-image.yml`의 workflow 이름은 `Publish image`입니다.
- 게시할 변경은 PR review를 거쳐 `main`에 merge합니다. PR·fork·실패한 CI revision을
  게시 대상으로 우회하지 않습니다.
- `deploy/**` 전용 변경은 배포 승인용 GitOps 변경이므로 새 image 게시 입력이 아닙니다.
  Application source와 `deploy/**`를 같은 PR에 섞지 않습니다.

## 게시 trigger와 결과 확인

1. Application source 변경 pull request를 review하고 `main`에 merge합니다.
2. GitHub Actions에서 해당 exact revision의 `CI`가 성공했고 main push의 `before..after` 전체
   변경 분류 artifact를 exact SHA와 CI run attempt로 남겼는지 확인합니다.
3. 같은 revision으로 이어서 시작된 `Publish image`에서 `Classify image publishing change`
   job이 trigger한 exact CI run·attempt의 artifact와 head SHA를 검증해 image 게시 대상으로
   판정했는지 확인합니다.
4. `Publish ARM64 image` job이 `ubuntu-24.04-arm` runner에서 성공했는지 확인하고 summary에서
   revision tag와 다음 형식의 deployment reference를 기록합니다.

```text
<ACCOUNT_ID>.dkr.ecr.ap-northeast-2.amazonaws.com/car-community@sha256:<DIGEST>
```

5. AWS ECR Console에서 exact 40자리 Git SHA tag가 summary의 같은 digest를 가리키는지
   확인합니다.

Tag immutability 때문에 같은 SHA가 다른 image byte를 가리키게 강제 push하지 않습니다.
CI 재실행은 같은 SHA의 새 `run_attempt`와 별도 decision artifact를 만들며, 이 artifact는
GitHub의 30일 재실행 가능 기간에 맞춰 보존됩니다. 같은 publish 실행을 재실행했을 때는
trigger한 exact CI attempt의 결정을 다시 검증하고 기존 immutable tag와 digest를 재사용할
수 있습니다. 어느 경우에도 summary와 ECR의 exact digest가 일치해야 합니다.

`deploy/**`만 바뀐 revision은 `CI`와 `Publish image` workflow event가 발생할 수 있지만
분류 결과 `Publish ARM64 image` job이 skip되는 것이 정상입니다. 이 경우 새 digest를
기대하거나 기존 digest를 새 배포 입력으로 기록하지 않습니다.

## 성공 결과와 handoff

- `CI`와 이어진 `Publish image`가 같은 exact `main` SHA에 대해 성공했습니다.
- ECR SHA tag와 job summary의 digest가 일치합니다.
- 다음 단계에 넘길 값은 tag가 아니라 full exact digest reference입니다.

Node pull 경로를 확인하려면
[Private ECR exact digest pull](410-private-ecr-image-pull-verification-runbook.md)에 이 값을
입력합니다. Staging application을 배포하려면
[GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)의 GitOps PR 입력으로
사용합니다. Image 게시 자체는 Kubernetes·RDS·Argo CD resource를 변경하지 않습니다.

## 중단·실패와 복구

| 상태 | 확인과 복구 |
| --- | --- |
| `CI` 실패 | Publish를 수동 우회하지 않습니다. Source 문제를 새 commit으로 수정하고 다시 PR·CI를 통과시킵니다. |
| 분류 artifact가 없거나 head SHA·attempt·형식 검증 실패 | Trigger한 exact CI run·attempt의 `Classify image publishing change`와 30일 보존 artifact upload를 확인합니다. 마지막 commit만 수동 분류해 publish하지 않습니다. |
| `deploy/**` 전용 merge에서 publish job 실행 | 완료로 인정하지 않고 path classifier와 publish job `needs`·condition을 복구합니다. AWS role을 유지한 채 build step에서 우회하지 않습니다. |
| PR·fork·실패 revision에서 publish job 실행 또는 AWS role assumption 발생 | 해당 실행을 완료로 인정하지 않고 workflow condition과 OIDC trust를 먼저 차단·대조합니다. |
| `AWS_ECR_PUSH_ROLE_ARN`이 비었거나 ARN 형식 오류 | Repository variable에 AWS Console에서 확인한 exact push role ARN을 설정하고 해당 publish 실행을 재실행합니다. |
| OIDC role assumption 실패 | 장기 access key를 추가하지 않습니다. Provider audience, trust의 exact `sub`, role ARN과 job의 `id-token: write`를 확인합니다. |
| ECR lookup·push `AccessDenied` | 광범위한 관리 policy를 붙이지 않습니다. Target repository ARN, 필요한 push action과 `GetAuthorizationToken`을 대조합니다. |
| Native ARM64 runner 할당 실패·장시간 대기 | QEMU를 임시 추가해 우회하지 않습니다. GitHub Actions runner 상태와 `ubuntu-24.04-arm` 지원 여부를 확인하고 정상화 뒤 같은 실행을 재실행합니다. Runner 정책을 바꾸려면 image delivery 선택을 함께 재검토합니다. |
| Build 실패 | Dockerfile·source 문제를 고친 새 commit과 CI로 해결합니다. 같은 SHA tag에 다른 byte를 게시하지 않습니다. |
| Summary digest 누락·형식 오류 | 게시 완료로 판정하지 않습니다. Workflow log와 ECR의 SHA tag를 확인해 원인을 해결한 뒤 같은 실행을 재실행합니다. |
| Summary와 ECR digest 불일치 | 어느 값을 배포에도 사용하지 않습니다. Exact SHA, account·region·repository와 immutable tag 상태를 조사합니다. |

## 관련 문서

- **현재 image 전달 개념**: [Container image delivery](../project/infrastructure/200-container-image-delivery.md)
- **최초 GitHub 설정**: [GitHub ECR 게시 최초 설정](320-github-ecr-publishing-configuration-runbook.md)
- **Workflow 구현 source**: [Publish image](../../.github/workflows/publish-image.yml)
- **Exact digest node 점검**: [Private ECR image pull 점검](410-private-ecr-image-pull-verification-runbook.md)
- **Digest 배포 승인**: [GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)
