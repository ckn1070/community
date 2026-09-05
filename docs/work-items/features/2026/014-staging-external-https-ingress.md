# Staging 외부 HTTPS ingress

## 상태

Done

## 목표 결과

`stg-api.example.com`의 외부 HTTPS 요청을 AWS Application Load Balancer와 Kubernetes
Ingress를 통해 staging `car-community` Service로 전달합니다.

```text
Client
  -> Cloudflare DNS-only CNAME
  -> internet-facing ALB :443
  -> instance target / auto-assigned NodePort
  -> car-community Service
  -> car-community Pod
```

HTTP `80`은 HTTPS `443`으로 redirect하고, ALB listener certificate는
`ap-northeast-2`의 ACM public certificate를 Ingress host에서 자동 탐색합니다. AWS Load
Balancer Controller가 Ingress를 기준으로 ALB·Target Group·listener·security group을
소유하며, 운영자가 ALB를 별도로 만들지 않습니다.

## 현재 결과와 책임

- Live 완료 결과: Target Group `Healthy`, DNS 전 ALB 직접 HTTPS readiness `200`·HTTP
  `301`, Cloudflare DNS-only CNAME의 exact ALB hostname 수렴, public HTTPS readiness
  `200`·HTTP→HTTPS `301`·ACM certificate, Service NodePort `30503`, Argo CD revision
  `4a75d77cf58c2139ee81915bff8dd2a2db23ea8f`의 `Synced`·`Healthy`와 condition 부재를
  확인했습니다.
- Repository source 책임: AWS Load Balancer Controller bootstrap, staging NodePort·Ingress,
  AppProject permission과 contract test, 영구 SOT·runbook을 구현하고 검증합니다.
- 사용자 실행 책임: AWS IAM·instance profile 교체, public subnet tag, ACM DNS validation,
  EC2 script 실행, GitOps merge, ALB와 Cloudflare DNS의 live 검증을 수행합니다.
- Live 완료 여부와 첫 재개점은
  [100 Staging 단일 node 전체 구축 시나리오](../../../operations/scenarios/100-staging-single-node-bootstrap.md)가
  소유합니다. 이 work item은 repository 결과와 선택 근거만 소유합니다.

Repository 검증은 외부 AWS·Cloudflare·EC2 실행 완료를 대신하지 않습니다. 이 작업은
scenario에 기록한 live 완료 조건을 실제로 확인해 `Done`으로 전환했습니다.

## 보존 범위

- AWS Load Balancer Controller chart `3.5.0`과 exact image digest
- Ephemeral verified Helm `3.21.4` client를 사용하는 controller bootstrap
- Explicit cluster name·region·VPC, single-node `replicaCount=1`, autoscaling disabled와
  foreign HPA fail-close
- Chart의 일반·Gateway CRD 7개를 fixed field manager로 server-side apply하고 Helm 전에
  각각 `Established`를 확인하는 barrier
- Existing public subnet 두 개의 internet-facing ALB discovery 계약
- Self-managed kubeadm·Calico overlay에 맞춘 `instance` target과 NodePort Service
- IMDSv2의 EC2 identity로 singleton Node `spec.providerID`를 exact 수렴시키는 prerequisite
- Controller-managed frontend·shared backend security group
- Primary ENI의 여러 SG 중 cluster tag exact-one으로 선택한
  `stg-car-community-k8s-node-sg` 경계
- `stg-api.example.com`, HTTP→HTTPS와 ACM host discovery
- `ELBSecurityPolicy-TLS13-1-2-Res-2021-06`
- Cloudflare DNS-only CNAME baseline
- 기존 ECR-only profile에서 staging node platform role/profile로의 안전한 전환
- AppProject의 application Ingress 최소 permission
- Live target health·redirect·TLS·DNS·Argo 상태 검증과 rollback 경계

## 확정 결정 색인

[014 상세 설계](014-staging-external-https-ingress-design.md)는 다음 판단을 보존합니다.

- Controller-owned ALB와 Git-owned Ingress
- Internet-facing ALB와 Cloudflare DNS-only baseline
- `instance` target·NodePort와 Calico overlay 경계
- Controller-managed security group과 node ingress 제한
- Public subnet role tag와 두 AZ
- ACM DNS validation·host discovery와 TLS listener policy
- Composite staging node role·instance profile과 IMDS trade-off
- Helm bootstrap·Argo CD owner 분리
- 재실행·삭제·rollback ownership

## 제외 범위

- Edge·origin 보안, workload identity·다중 node network, autoscaling, Production ingress와
  구성 자동화는 [004 Infrastructure 진화 Backlog](../../backlog/2026/004-infrastructure-evolution.md)가
  후속 후보로 소유합니다.

## 영구 SOT

- [170 AWS Load Balancer ingress](../../../project/infrastructure/170-aws-load-balancer-ingress.md)
- [110 VPC network](../../../project/infrastructure/110-vpc-network.md)
- [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md)
- [130 ECR·IAM image 공급 기반](../../../project/infrastructure/130-ecr-iam-foundation.md)
- [160 Argo CD platform](../../../project/infrastructure/160-argocd-platform.md)
- [220 GitOps application delivery](../../../project/infrastructure/220-gitops-application-delivery.md)

## 운영 Procedure

- [370 Staging 외부 HTTPS ingress 최초 구축](../../../operations/370-staging-external-https-ingress-bootstrap-runbook.md)
- [100 Staging 단일 node 전체 구축 시나리오](../../../operations/scenarios/100-staging-single-node-bootstrap.md)

## 관련 문서

- **결정 근거**: [014 상세 설계](014-staging-external-https-ingress-design.md)
- **선행 application delivery**:
  [013 Staging application GitOps delivery](013-autoscaling-safe-application-delivery.md)
- **작업 목록**: [000 2026 기능 작업 문서](000-index.md)
