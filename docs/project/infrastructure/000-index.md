# Infrastructure 개념 목차

이 문서는 현재 프로젝트 infrastructure의 작동 원리와 선택 이유를 생애주기별로 안내합니다.
실제 명령과 Console 입력, 성공·중단·복구 절차는
[운영 문서 목차](../../operations/000-index.md)가 소유합니다.

## 먼저 볼 문서

- AWS부터 Kubernetes·ECR·GitOps까지 전체 관계:
  [Infrastructure system](../300-infrastructure-system.md)
- 실제 구축·반복 점검·변경·복구를 수행할 때:
  [운영 문서 목차](../../operations/000-index.md)
- 현재 외부 환경에서 실제 확인한 상태:
  [Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md)

## `100` 최초 구축 개념

| 질문 | Canonical owner |
| --- | --- |
| VPC·subnet·route·gateway·DNS는 왜 이렇게 나뉘는가 | [100-vpc-network.md](100-vpc-network.md) |
| 한 EC2가 Kubernetes node가 되는 경계와 선택 이유는 무엇인가 | [110-ec2-kubernetes-node.md](110-ec2-kubernetes-node.md) |
| ECR·GitHub OIDC push role·EC2 pull role은 왜 분리되는가 | [120-ecr-iam-foundation.md](120-ecr-iam-foundation.md) |
| RDS·DB subnet group·credential·monitoring·backup은 어떻게 동작하는가 | [130-rds-postgresql.md](130-rds-postgresql.md) |
| Host부터 Calico까지 빈 cluster가 어떤 상태를 거치는가 | [140-kubernetes-cluster.md](140-kubernetes-cluster.md) |
| Argo CD Core·GitHub App·AppProject·Application의 책임은 무엇인가 | [150-argocd-platform.md](150-argocd-platform.md) |

## `200` 평소 반복 delivery 개념

| 질문 | Canonical owner |
| --- | --- |
| 성공한 source revision이 ECR digest가 되고 node가 pull하기까지 | [200-container-image-delivery.md](200-container-image-delivery.md) |
| Digest PR merge가 Kubernetes live state로 수렴하기까지 | [210-gitops-application-delivery.md](210-gitops-application-delivery.md) |

## `300` 구성 변경과 복구 개념

| 질문 | Canonical owner |
| --- | --- |
| 정상 재실행·변경·취소·destructive recovery를 어떻게 구분하는가 | [300-change-and-recovery-principles.md](300-change-and-recovery-principles.md) |
| Kubernetes bootstrap 취소가 왜 제한적이며 어디까지 복원 가능한가 | [310-kubernetes-bootstrap-cancellation.md](310-kubernetes-bootstrap-cancellation.md) |
| Admin kubeconfig는 왜 선택적으로만 배포하는가 | [320-kubernetes-admin-access.md](320-kubernetes-admin-access.md) |
| GitHub App key rotation이 어떤 순서와 증거를 요구하는가 | [330-argocd-repository-credential-rotation.md](330-argocd-repository-credential-rotation.md) |
| 수동 AWS resource를 Terraform으로 전환하기 전에 무엇을 결정해야 하는가 | [340-terraform-transition.md](340-terraform-transition.md) |

번호는 운영 명령 순서가 아니라 이 개념 router 안의 생애주기와 독자 질문을 나타냅니다.
