# Repository·delivery platform 최초 구축 runbook

Source repository의 CI 품질 게이트를 처음 설정하고, 이후 staging Kubernetes 환경에
private ECR image 공급과 Argo CD GitOps delivery 기반을 연결할 때 사용하는 순서
문서입니다. 각 단계의 실제 입력·행동·중단·복구는 연결된 runbook이 소유하며, 이 문서는
최초 구축의 handoff와 재개 지점만 소유합니다.

`310`~`350`은 repository·delivery 기반을 연결하고, 선택 `360`은 최초 Super Admin 운영
준비를, `370`은 현재 선택한 staging 외부 HTTPS 공개 확장을 소유합니다.

시스템의 actor·credential·artifact 전달과 선택 이유는
[100 Infrastructure overview](../project/infrastructure/100-infrastructure-overview.md),
[코드 품질 도구](../tech-stack/310-code-quality-tools.md),
[130 ECR·IAM 기반](../project/infrastructure/130-ecr-iam-foundation.md),
[160 Argo CD platform](../project/infrastructure/160-argocd-platform.md),
[170 AWS Load Balancer ingress](../project/infrastructure/170-aws-load-balancer-ingress.md)에서 먼저
확인합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Source repository의 CI부터 staging delivery 기반까지 처음 연결할 때, 마지막으로 완료된 하위 단계 다음부터 재개합니다. |
| 누가 | GitHub·Sonar·AWS 권한과 EC2 SSH 권한을 가진 운영자가 담당 단계만 실행합니다. 한 사람이 모든 권한을 가진다고 가정하지 않습니다. |
| 어디서 | 각 하위 runbook이 지정한 GitHub·Sonar·AWS Console, local terminal 또는 EC2 terminal입니다. |
| 무엇을 | 아래 순서표의 번호가 있는 canonical runbook을 exact 진입점으로 사용합니다. |
| 왜 | 서로 다른 권한과 실패 경계를 가진 최초 설정을 의존 순서대로 연결하고, 완료 단계를 불필요하게 다시 실행하지 않기 위해서입니다. |
| 완료·재개 | 각 하위 runbook의 완료 조건을 확인한 뒤 다음 행으로 이동하고, 실패하면 같은 하위 runbook의 재개점으로 돌아갑니다. |

## 단계별 전제

전체 단계에 공통인 Kubernetes 완료 전제는 없습니다. 지금 수행할 단계의 전제만
확인합니다.

| 단계 | 시작 전 필요한 상태 |
| --- | --- |
| `310` GitHub CI 품질 게이트 | GitHub repository 설정 권한과 SonarQube Cloud organization·project 관리 권한. AWS와 Kubernetes는 필요하지 않습니다. |
| `320` GitHub ECR 게시 설정 | Private ECR과 GitHub OIDC push role이 실제 AWS에 존재합니다. |
| 첫 image 게시 | `310`, `320`이 완료되고 `main`의 `Application quality`가 성공했습니다. |
| `330` Kubelet ECR provider | [150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)로 `bootstrap/ecr` script를 준비했고, [200 Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md)이 완료됐으며 첫 ECR image digest를 확보했습니다. |
| `340` Argo CD Core | [150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)로 `bootstrap/gitops` script를 준비했고 [200 Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md)이 완료됐습니다. |
| `350` Application GitOps 최초 연결 | [150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)로 Application·GitOps script를 준비했고 Argo CD Core가 Ready이며 RDS master credential·local `.env` 허용값, 현재 private repository의 `deploy/` manifest와 GitHub App을 준비할 수 있습니다. |
| 선택 `360` Super Admin bootstrap | `350`의 Application이 `Synced`·`Healthy`이고 runtime Pod와 Service가 Ready이며 운영상 최초 최고 관리자가 필요합니다. |
| `370` Staging 외부 HTTPS ingress | `350`의 Application이 `Synced`·`Healthy`이고 public subnet 두 개, node IAM 전환 권한, ACM·Cloudflare DNS 권한이 있습니다. |

이 문서를 위에서 아래로 한 번에 다시 실행하지 않습니다. 이미 성공한 단계는 해당
runbook의 완료 결과를 확인하고, 마지막으로 실패하거나 아직 시작하지 않은 단계에서
재개합니다.

## 최초 구축 순서

| 순서 | 사람이 수행할 절차 | 다음 단계에 넘기는 결과 |
| --- | --- | --- |
| 1 | [GitHub CI 품질 게이트 최초 설정](310-github-ci-quality-configuration-runbook.md) | `main`의 `Application quality`, Sonar exact revision·Quality Gate와 현재 branch governance 경계 |
| 2 | [AWS ECR·IAM 최초 구성](130-ecr-iam-provisioning-runbook.md) | Tag immutable private ECR, GitHub push role ARN, EC2 pull instance profile |
| 3 | [GitHub ECR 게시 설정](320-github-ecr-publishing-configuration-runbook.md) | Repository variable `AWS_ECR_PUSH_ROLE_ARN` |
| 4 | [첫 image 게시](400-github-ecr-image-publishing-runbook.md) | 성공한 `main` revision의 exact ECR digest reference |
| 5 | [Kubelet ECR credential provider 설치](330-kubelet-ecr-credential-provider-installation-runbook.md) | 실행 중 kubelet의 provider 구성 |
| 6 | [Private ECR exact digest pull 점검](410-private-ecr-image-pull-verification-runbook.md) | Node instance profile로 실제 pull·실행 성공 |
| 7 | [Argo CD Core 설치](340-argocd-core-installation-runbook.md) | Headless Core component와 CRD Ready |
| 8 | [Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md) | Runtime·migrator DB role, Git 밖 Namespace·다섯 Secret, Git-managed ConfigMap, read-only GitHub App credential, AppProject와 최초 Application `Synced`·`Healthy` |
| 선택 9 | [Super Admin 최초 bootstrap](360-super-admin-bootstrap-runbook.md) | 최초 `ROLE_SUPER_ADMIN`, 초기 password 변경 `204`와 새 password 로그인 `201` |
| 10 | [370 Staging 외부 HTTPS ingress 최초 구축](370-staging-external-https-ingress-bootstrap-runbook.md) | Node platform role, LBC, ACM certificate, ALB·healthy target과 `stg-api.example.com` DNS-only HTTPS 경로 |

EC2에 archive를 한 번에 전달하는 것과 실행 의존 순서는 다른 계약입니다. 예를 들어 Argo CD
Core 설치 script가 이미 전달돼 있어도 Kubernetes bootstrap이 끝나기 전에는 실행하지
않습니다.

## 단계별 중단과 재개

- 한 단계가 실패하면 뒤 단계를 시작하지 않습니다.
- 실패한 단계의 runbook에서 상태와 외부 전제를 확인하고 같은 단계의 재개 지점을
  따릅니다.
- IAM role을 합치거나 static AWS key·`imagePullSecret`·PAT를 임시 우회로 추가하지
  않습니다.
- Argo CD 최초 연결 전 현재 repository의 non-empty `deploy/overlays/stg`와 image reference
  owner를 확인하지 못하면 `350`에서 중단합니다.
- Runtime·migrator DB role과 Git 밖 application prerequisite를 적용하지 못하면 같은
  `350`의 GitHub App 연결 단계로 진행하지 않습니다.
- 이미 완료된 최초 연결 뒤 credential을 바꾸려는 경우 `350`을 다시 실행하지 않고
  [Argo CD repository credential 교체](520-argocd-repository-credential-rotation-runbook.md)를
  사용합니다.
- Existing Application에 Ingress permission을 추가할 때 repository credential과 Application을
  다시 만들지 않습니다. `370`의 tracked AppProject one-time apply와 정상 GitOps merge만
  사용합니다.
- 완료된 Sonar 설정의 token을 바꾸려는 경우 `310`을 다시 실행하지 않고
  [Sonar token 교체](530-sonar-token-rotation-runbook.md)를 사용합니다.

## 완료 확인

최초 기반 구축은 다음을 실제 환경에서 모두 확인했을 때 완료입니다.

- `main`에서 `Application quality`와 해당 exact revision의 `Sonar way` Quality Gate가
  성공합니다. 현재 단일 운영자·private repository의 비활성 branch protection은 최초
  구축 완료를 막지 않으며, Argo CD 연결 이후 배포 변경은 `420`의 PR 정책을 따릅니다.
- 성공한 `main` SHA의 ARM64 image와 exact digest가 private ECR에 있습니다.
- 실행 중 kubelet이 ECR credential provider를 사용하고 exact digest smoke가 성공했습니다.
- Argo CD Core component와 CRD가 Ready이고 `argocd-server`는 없습니다.
- Runtime·migrator DB role과 Git 밖 application prerequisite가 적용됐습니다.
- `car-community-stg` Application이 fresh reconcile 뒤 `Synced`·`Healthy`입니다.
- External ingress를 이번 최초 platform 범위에 포함했다면 controller Ready, target healthy,
  HTTP→HTTPS redirect와 `stg-api.example.com`의 DNS·TLS를 `370`에서 확인합니다.

Local source·contract test는 AWS, GitHub, EC2와 Kubernetes의 live 결과를 대신하지 않습니다.
최초 기반 구축 뒤 평소 image 게시·배포는 [반복 image 게시](400-github-ecr-image-publishing-runbook.md)와
[GitOps 배포·rollback](420-gitops-deployment-and-rollback-runbook.md)을 사용합니다.

## 관련 문서

- **전체 구축의 현재 위치**:
  [Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)
- **현재 시스템 개념**: [100 Infrastructure overview](../project/infrastructure/100-infrastructure-overview.md)
- **CI/CD workflow와 handoff**: [200 CI/CD pipeline](../project/infrastructure/200-ci-cd-pipeline.md)
- **CI 품질 기준**: [310 코드 품질 도구](../tech-stack/310-code-quality-tools.md)
- **Image identity·build·pull**: [210 Container image delivery](../project/infrastructure/210-container-image-delivery.md)
- **GitOps 전달 개념**: [220 GitOps application delivery](../project/infrastructure/220-gitops-application-delivery.md)
- **Kubernetes 최초 구축**: [200 Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md)
- **선택형 최초 관리자**: [360 Super Admin 최초 bootstrap](360-super-admin-bootstrap-runbook.md)
