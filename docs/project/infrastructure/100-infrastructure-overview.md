# Infrastructure overview

이 문서는 현재 staging infrastructure의 구성요소가 어떤 경계로 연결되는지 설명하는
상위 SOT입니다. 각 구성요소의 선택값·원리·재검토 조건은 이 directory의 상세 SOT가,
CI/CD의 전체 흐름은 [200 CI/CD pipeline](200-ci-cd-pipeline.md)이, 실제 구축·점검·복구
절차는 [000 운영 문서 목차](../../operations/000-index.md)가 소유합니다.

이 문서는 전체 topology와 구성요소 사이의 책임·신뢰 경계만 소유합니다. 구성요소별
상세 선택, 운영 명령, 현재 live 상태와 실행 이력을 반복하지 않습니다.

## 전체 구조

```text
AWS VPC
├── public subnet 2a: EC2 단일 Kubernetes control plane·worker
├── public subnet 2a·2b: internet-facing ALB 배치
└── private subnet 2a·2b: RDS PostgreSQL 배치 후보

GitHub source·deploy desired state
├── CI/CD pipeline ─> private ECR immutable ARM64 image
└── Argo CD Core ─> Kubernetes desired state reconciliation

Kubernetes workload
├── PreSync Migration Job ─> RDS schema·runtime 권한 동기화
├── Application Deployment ─> RDS runtime access
└── NodePort Service·Ingress ─> AWS Load Balancer Controller ─> ALB

Cloudflare DNS-only stg-api.example.com ─> ALB HTTPS ─> NodePort ─> Pod
```

VPC는 address·route·DNS·security 경계를, EC2는 Kubernetes 실행 위치를, RDS는 application
data를 소유합니다. ECR은 실행 image를 보관하고 Git의 `deploy/overlays/stg`는 staging의
목표 상태를 소유합니다. Argo CD는 이 Git 목표를 Kubernetes live resource로 수렴시킵니다.

## 구성요소와 신뢰 경계

| 경계 | 생산자·권한 | 전달 대상 | 상세 SOT |
| --- | --- | --- | --- |
| Network | AWS 운영자가 VPC·subnet·route·security 경계를 구성 | EC2·RDS·ALB placement와 통신 | [110 VPC network](110-vpc-network.md) |
| Compute | EC2 node role과 host 운영자가 단일 ARM64 node를 구성 | Kubernetes control plane·worker | [120 EC2 Kubernetes node](120-ec2-kubernetes-node.md) |
| Image 공급 기반 | GitHub OIDC push role은 ECR write, EC2 instance profile은 ECR read | CI/CD publish와 kubelet pull | [130 ECR·IAM image 공급 기반](130-ecr-iam-foundation.md) |
| Database | RDS master·migrator·runtime credential을 역할별로 분리 | Migration Job과 Application Pod | [140 RDS PostgreSQL](140-rds-postgresql.md) |
| Cluster | Host·containerd·kubeadm·Calico가 node 실행 상태를 만듦 | Argo CD와 application workload | [150 Kubernetes cluster](150-kubernetes-cluster.md) |
| GitOps control plane | Read-only GitHub App과 AppProject가 repository·resource 범위를 제한 | `deploy/overlays/stg`를 Kubernetes에 수렴 | [160 Argo CD platform](160-argocd-platform.md) |
| Public ingress | LBC가 Git-owned Service·Ingress를 AWS ALB resource로 변환 | Public HTTPS request를 Pod로 전달 | [170 AWS Load Balancer ingress](170-aws-load-balancer-ingress.md) |
| Delivery | GitHub Actions·ECR·GitOps reviewer·Argo CD가 서로 다른 identity를 사용 | Source revision을 승인된 live workload로 전달 | [200 CI/CD pipeline](200-ci-cd-pipeline.md) |

AWS Console 권한, GitHub Actions 권한, EC2 instance profile, GitHub App, database credential은
서로 대체하지 않습니다. 한 경계의 성공도 다음 경계의 완료 증거가 아닙니다. 예를 들어
ECR image 게시 성공은 node pull이나 Argo CD reconciliation 성공을 증명하지 않습니다.

## 현재 구성 소유권

AWS resource는 현재 Console과 목적별 runbook으로 수동 관리하며 `managed-by=manual` 경계를
사용합니다. Repository에는 Terraform configuration, provider lock, backend와 state가 없고,
AWS Console의 preview code도 Terraform ownership이나 실행 결과를 만들지 않습니다.

따라서 현재 구성값과 resource 관계는 이 directory의 상세 SOT가 계속 소유합니다. Terraform을
포함한 미래 구성 소유권·자동화 후보와 전환 전에 결정할 경계는
[004 Infrastructure 진화 Backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
소유합니다.

## 실행 artifact 전달 경계

Local 운영자는 EC2에서 실행할 Kubernetes·ECR·Argo CD·Load Balancer·application bootstrap
script와 그 script가 읽는 manifest·shared library만 생애주기 directory를 보존한 하나의
archive로 묶습니다. Archive와 SHA-256 file을 기존 SSH 경로로 `/tmp`에 전달하고, EC2에서
checksum·entry·file mode를 검증한 뒤 `/home/ubuntu/car-community-scripts`에 배치합니다.
각 lifecycle runbook은 이 공통 artifact의 고정 절대 경로에서 자기 실행 단위만 소비하며,
공통 전달이 실행 순서나 권한을 합치지는 않습니다.

Repository 전체 clone, application source·Dockerfile·문서·local test와 credential은 이
archive에 포함하지 않습니다. Checksum은 local archive와 수신 byte의 동일성만 증명하며 code
signature나 배포자 identity를 증명하지 않습니다. 다중 node 배포나 중앙 이력·서명·감사가
필요해지면 S3·Systems Manager 또는 서명된 artifact 전달 체계를 다시 결정합니다.

실제 inventory, 생성·검증·전달·재개 명령은
[150 EC2 실행 artifact 전달](../../operations/150-ec2-execution-artifact-transfer-runbook.md),
archive 내부 의존 관계는
[Repository script 목차](../../../scripts/README.md#ec2-전달-archive-구현-의존성)가 소유합니다.

## 생애주기별 탐색

| 질문 | Canonical owner |
| --- | --- |
| 빈 환경을 어떤 순서로 구축하고 현재 어디까지 완료했는가 | [100 Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md) |
| Source 변경이 어떻게 검증·게시·배포되는가 | [200 CI/CD pipeline](200-ci-cd-pipeline.md) |
| OIDC trust와 ECR push·pull IAM resource 관계는 어떻게 동작하는가 | [130 ECR·IAM image 공급 기반](130-ecr-iam-foundation.md) |
| Image identity·ARM64 build·kubelet pull은 어떻게 동작하는가 | [210 Container image delivery](210-container-image-delivery.md) |
| Digest 승인·migration·reconciliation·rollback은 어떻게 동작하는가 | [220 GitOps application delivery](220-gitops-application-delivery.md) |
| 정상 재실행·변경·취소·복구를 어떻게 구분하는가 | [300 Infrastructure 변경과 복구 원칙](300-change-and-recovery-principles.md) |
| 실제 구축·점검·복구 명령은 어디에 있는가 | [000 운영 문서 목차](../../operations/000-index.md) |
| 현재 staging live 상태와 다음 행동은 무엇인가 | [100 Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md#현재-실행-상태) |
| 아직 착수하지 않은 확장·관측·보안·자동화 후보는 무엇인가 | [004 Infrastructure 진화 Backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md) |

## 증거 경계

Repository source·문서·contract test는 현재 구조와 자동화 계약을 증명합니다. 실제
AWS·GitHub·EC2·Kubernetes 상태는 해당 runbook의 live postcondition과 staging scenario만
소유합니다. 현재 상태를 이 overview나 상세 SOT에 복제하지 않습니다.

## 관련 진입점

- **Infrastructure 상세 탐색**: [000 Infrastructure 개념 목차](000-index.md)
- **CI/CD 설명 SOT**: [200 CI/CD pipeline](200-ci-cd-pipeline.md)
- **운영 절차**: [000 운영 문서 목차](../../operations/000-index.md)
- **현재 live 상태**: [100 Staging 단일 node 전체 구축 시나리오](../../operations/scenarios/100-staging-single-node-bootstrap.md)
- **미래 infrastructure 후보**: [004 Infrastructure 진화 Backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)
