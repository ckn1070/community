# Infrastructure 개념 목차

이 문서는 현재 프로젝트 infrastructure와 CI/CD 설명 SOT를 질문별로 안내하는 router입니다.
실제 명령과 Console 입력, 성공·중단·복구 절차는
[운영 문서 목차](../../operations/000-index.md)가 소유합니다.

## 먼저 볼 문서

- AWS부터 Kubernetes·ECR·GitOps까지 전체 구조와 경계:
  [100 Infrastructure overview](100-infrastructure-overview.md)
- Push부터 CI·image publish·GitOps 배포까지 전체 흐름:
  [200 CI/CD pipeline](200-ci-cd-pipeline.md)
- 실제 구축·반복 점검·변경·복구를 수행할 때:
  [운영 문서 목차](../../operations/000-index.md)
- 현재 외부 환경에서 실제 확인한 상태:
  [Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md)
- 아직 착수하지 않은 infrastructure 확장·관측·보안·자동화 후보:
  [004 Infrastructure 진화 Backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)

## `100` Infrastructure 구조와 구성요소

| 질문 | Canonical owner |
| --- | --- |
| 전체 topology와 component·trust boundary는 무엇인가 | [100-infrastructure-overview.md](100-infrastructure-overview.md) |
| VPC·subnet·route·gateway·DNS는 왜 이렇게 나뉘는가 | [110-vpc-network.md](110-vpc-network.md) |
| 한 EC2가 Kubernetes node가 되는 경계와 선택 이유는 무엇인가 | [120-ec2-kubernetes-node.md](120-ec2-kubernetes-node.md) |
| ECR·GitHub OIDC push role·EC2 pull role은 왜 분리되는가 | [130-ecr-iam-foundation.md](130-ecr-iam-foundation.md) |
| RDS·DB subnet group·credential·monitoring·backup은 어떻게 동작하는가 | [140-rds-postgresql.md](140-rds-postgresql.md) |
| Host부터 Calico까지 빈 cluster가 어떤 상태를 거치는가 | [150-kubernetes-cluster.md](150-kubernetes-cluster.md) |
| Argo CD Core·GitHub App·AppProject·Application의 책임은 무엇인가 | [160-argocd-platform.md](160-argocd-platform.md) |
| Public HTTPS 요청이 Cloudflare DNS·ALB·NodePort·Pod로 어떻게 전달되는가 | [170-aws-load-balancer-ingress.md](170-aws-load-balancer-ingress.md) |

## `200` CI/CD 설명

| 질문 | Canonical owner |
| --- | --- |
| Push 뒤 어떤 workflow와 step이 어떤 조건·입력·출력을 거쳐 배포로 이어지는가 | [200-ci-cd-pipeline.md](200-ci-cd-pipeline.md) |
| Image identity·ARM64 build representation과 kubelet exact digest pull은 어떻게 동작하는가 | [210-container-image-delivery.md](210-container-image-delivery.md) |
| Digest PR merge가 Kubernetes live state로 수렴하는 상세 state model은 무엇인가 | [220-gitops-application-delivery.md](220-gitops-application-delivery.md) |

## `300` 구성 변경과 복구 개념

| 질문 | Canonical owner |
| --- | --- |
| 정상 재실행·변경·취소·destructive recovery를 어떻게 구분하는가 | [300-change-and-recovery-principles.md](300-change-and-recovery-principles.md) |
| Kubernetes bootstrap 취소가 왜 제한적이며 어디까지 복원 가능한가 | [310-kubernetes-bootstrap-cancellation.md](310-kubernetes-bootstrap-cancellation.md) |
| Admin kubeconfig는 왜 선택적으로만 배포하는가 | [320-kubernetes-admin-access.md](320-kubernetes-admin-access.md) |
| GitHub App key rotation이 어떤 순서와 증거를 요구하는가 | [330-argocd-repository-credential-rotation.md](330-argocd-repository-credential-rotation.md) |

번호는 운영 명령 순서가 아니라 이 개념 router 안의 생애주기와 독자 질문을 나타냅니다.
