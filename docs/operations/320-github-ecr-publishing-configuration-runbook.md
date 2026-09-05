# GitHub ECR 게시 최초 설정 runbook

이 절차는 repository 품질 게이트 최초 설정이 완료된 뒤 실행합니다.

GitHub Actions의 `Publish image` workflow가 AWS OIDC push role을 선택할 수 있도록 source
repository에 `AWS_ECR_PUSH_ROLE_ARN` repository variable을 최초 설정하는 절차입니다. Image
게시 자체는 [400 GitHub ECR 반복 image 게시](400-github-ecr-image-publishing-runbook.md)가 소유합니다.

OIDC trust·push/pull 권한 분리와 variable의 역할은
[130 ECR·IAM 기반](../project/infrastructure/130-ecr-iam-foundation.md), 전체 image 흐름은
workflow 관점의 [200 CI/CD pipeline](../project/infrastructure/200-ci-cd-pipeline.md)과 image
identity·pull 관점의
[210 Container image delivery](../project/infrastructure/210-container-image-delivery.md)에서
각각 확인합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | ECR push role이 준비되고 GitHub Actions가 아직 그 role ARN을 받지 못한 최초 설정 시점입니다. |
| 누가 | AWS IAM role을 조회하고 GitHub repository variable을 관리할 권한이 있는 운영자가 실행합니다. |
| 어디서 | AWS Console의 IAM role 상세와 GitHub `example-org/car-community → Settings → Secrets and variables → Actions → Variables` 화면입니다. |
| 무엇을 | Repository variable `AWS_ECR_PUSH_ROLE_ARN` 하나를 수동 생성합니다. Repository script는 없습니다. |
| 왜 | Workflow source에 account별 role ARN이나 장기 AWS key를 저장하지 않고 OIDC role을 선택하기 위해서입니다. |
| 완료·재개 | Variable 이름과 exact role ARN을 확인합니다. 실패하면 role ARN·권한을 확인한 뒤 같은 Console 단계부터 재개합니다. |

## 입력과 전제

- AWS `ap-northeast-2`에 tag immutable private ECR repository `car-community`가 있습니다.
- GitHub OIDC provider와 `car-community-github-ecr-push-role`의 trust·최소 push policy가
  [AWS ECR·IAM 최초 구성](130-ecr-iam-provisioning-runbook.md)에 따라 준비됐습니다.
- AWS Console에서 위 push role의 exact ARN을 확인했습니다.
- GitHub repository `example-org/car-community`의 Actions 설정을 변경할 권한이 있습니다.

Role ARN은 secret이 아니지만 account별 workflow 입력이므로 source에 고정하지 않습니다.
Password나 AWS access key를 이 절차에 입력하지 않습니다.

## Repository variable 설정

**실행 위치:** GitHub Web UI의 `example-org/car-community` repository

1. GitHub의 `example-org/car-community` repository를 엽니다.
2. `Settings → Secrets and variables → Actions → Variables`로 이동합니다.
3. `New repository variable`을 선택합니다.
4. 이름에 `AWS_ECR_PUSH_ROLE_ARN`, 값에 AWS Console에서 확인한 exact push role ARN을
   입력합니다.
5. 저장 뒤 Variables 목록에서 이름이 존재하는지 확인합니다.

이미 같은 이름의 variable이 있으면 값을 추측해 덮어쓰지 않습니다. 현재 workflow가
사용하는 account·role과 AWS Console의 exact ARN을 대조한 뒤 필요한 변경을 별도 승인합니다.

## 성공 결과

- Actions repository variable 목록에 `AWS_ECR_PUSH_ROLE_ARN`이 있습니다.
- 값은 target AWS account의 `car-community-github-ecr-push-role` ARN입니다.
- GitHub Actions secret이나 repository source에 장기 AWS access key를 추가하지 않았습니다.

이 설정만으로 image가 게시된 것은 아닙니다. 다음 application source PR의 `main` merge와
CI 성공 뒤
[GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)에서 실제 workflow와
digest를 확인합니다.

## 중단과 복구

**실행 위치:** Push role·OIDC trust 확인은 AWS Console, repository variable 수정과 publish
재실행은 GitHub Web UI·Actions에서 수행합니다.

| 상황 | 대응 |
| --- | --- |
| Push role ARN을 확인할 수 없음 | Variable을 만들지 말고 AWS ECR·IAM 최초 구성의 role과 account를 먼저 확인합니다. |
| Repository 설정 권한 없음 | 권한이 있는 repository 관리자가 exact 이름·값으로 설정할 때까지 중단합니다. |
| 동명 variable이 다른 role을 가리킴 | 임의 교체하지 않고 현재 배포 account·OIDC trust·workflow 소비자를 대조한 뒤 변경합니다. |
| Workflow에서 variable이 비었거나 ARN 형식 오류로 실패 | Actions secret이나 static key로 우회하지 않고 variable의 이름과 exact role ARN을 수정한 뒤 해당 publish 실행을 재실행합니다. |

## 관련 문서

- **현재 ECR·IAM 계약**: [130 ECR·IAM 기반](../project/infrastructure/130-ecr-iam-foundation.md)
- **최초 AWS 절차**: [130 AWS ECR·IAM 최초 구성](130-ecr-iam-provisioning-runbook.md)
- **반복 게시 절차**: [400 GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)
- **Workflow trigger·step·handoff**: [200 CI/CD pipeline](../project/infrastructure/200-ci-cd-pipeline.md)
- **Image identity·build·pull**: [210 Container image delivery](../project/infrastructure/210-container-image-delivery.md)
- **구현 source**: [Publish image workflow](../../.github/workflows/publish-image.yml)
