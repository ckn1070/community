# Terraform 전환 경계

이 문서는 현재 Terraform 구현이 없는 상태와, 기존 수동 AWS resource를 Terraform으로
관리하기 전에 결정해야 할 경계를 소유합니다. 실행 가능한 Terraform module·state·import
절차를 제공하지 않습니다. 현재 AWS resource의 실제 Console 구축 절차는
[AWS 최초 구축 runbook](../../operations/100-aws-initial-provisioning-runbook.md)이 소유합니다.

## 현재 상태

- Repository에 Terraform configuration, module, provider lock, backend와 state가 없습니다.
- Terraform은 현재 AWS resource의 source of truth가 아니며 resource 생성 완료 증거도
  아닙니다.
- AWS Console의 `미리 보기 코드`는 입력을 code 형태로 보는 보조 기능일 뿐 Terraform
  ownership을 만들지 않습니다.
- VPC·EC2·RDS를 포함한 전체 수동 resource의 Terraform address·module mapping은 아직
  설계하지 않았습니다.

따라서 이 문서는 실행 runbook이 아니라 **전환 전에 닫아야 할 결정의 SOT**입니다.

## 기존 골격에서 보존한 최소 target

향후 Terraform 범위는 적어도 현재 image 공급 기반의 다음 resource를 누락하지 않아야
합니다.

| Target | 보존해야 할 현재 계약 |
| --- | --- |
| Private ECR | `car-community`, tag immutability, encryption, scan-on-push configuration |
| GitHub OIDC provider | Account에 이미 있는 provider를 재사용할지 Terraform이 생성·소유할지 |
| GitHub ECR push role | Exact `repo:ckn1070/community:ref:refs/heads/main` trust와 target repository push-only permission |
| EC2 ECR pull role·instance profile | EC2 service trust, target repository read-only permission과 기존 instance 연결 |

이는 target inventory의 **최소 보존 범위**이지 Terraform resource block·module 경계가
확정됐다는 뜻이 아닙니다. VPC·subnet·route·endpoint, EC2·security group·EBS와 RDS·Secrets
Manager·monitoring을 어디까지 관리할지도 별도 범위 결정이 필요합니다.

## 전환 전에 닫아야 할 결정

| 결정 | 현재 상태 | 결정하지 않으면 생기는 위험 |
| --- | --- | --- |
| 관리 범위 | 미정 | 일부 resource만 code가 소유하면서 dependency·tag·security 설정이 drift할 수 있습니다. |
| Import vs replace | 미정 | 기존 live resource를 의도치 않게 재생성하거나 downtime·data loss를 만들 수 있습니다. |
| Resource address·module | 미정 | Import address와 향후 refactor·state move를 확정할 수 없습니다. |
| OIDC provider owner | 미정 | Account 공용 provider를 중복 생성하거나 다른 repository consumer와 충돌할 수 있습니다. |
| Existing EC2 profile 연결 | 미정 | Profile 교체가 기존 role·workload 권한을 끊을 수 있습니다. |
| Backend·state locking | 미정 | State 유실·동시 apply·credential 노출 통제가 없습니다. |
| State access·secret 경계 | 미정 | Sensitive output·resource attribute가 부적절하게 노출될 수 있습니다. |
| Provider·Terraform version | 미정 | 동일 configuration의 재현성과 upgrade 책임이 없습니다. |
| Plan·apply 승인 | 미정 | 누가 어떤 environment 변경을 승인하는지 없습니다. |
| Drift·import 검증 | 미정 | 수동 resource와 planned state의 동일성을 증명할 수 없습니다. |
| Rollback·restore | 미정 | Apply 실패·replacement 뒤 복구 기준이 없습니다. |
| 수동→Terraform handoff | 미정 | `managed-by=manual` tag와 실제 변경 owner가 불일치할 수 있습니다. |

## Import와 replace의 의미

- **Import**는 기존 AWS resource ID를 Terraform address에 연결합니다. Import 자체가
  configuration이 live 속성과 완전히 일치함을 보장하지 않으므로 import 뒤 zero-diff plan과
  sensitive·replacement field를 대조해야 합니다.
- **Replace**는 Terraform이 새 resource를 만들고 consumer를 전환한 뒤 기존 resource를
  폐기하는 migration입니다. VPC·EC2·RDS·IAM은 network identity·data·permission에 영향을
  주므로 단순 `destroy/apply`로 취급할 수 없습니다.
- 어떤 방식을 선택해도 state를 live resource보다 먼저 authoritative하다고 선언하지
  않습니다. 검증된 handoff와 owner 전환이 완료돼야 `managed-by` 의미도 갱신합니다.

## 최소 검증·rollback 질문

Terraform 작업을 설계할 때 다음 질문을 각각 관찰 가능한 수용 기준으로 바꿔야 합니다.

- Import 또는 신규 apply 전후 resource ID·ARN·URI·CIDR·SG rule·IAM trust·policy가 현재
  계약과 같은가?
- Plan에 의도하지 않은 replacement·permission 확대·public exposure가 없는가?
- ECR immutable SHA·digest, GitHub OIDC publish와 node pull smoke가 계속 동작하는가?
- RDS·Secrets Manager와 VPC network를 범위에 넣으면 data·credential·connection을 어떻게
  보존하고 실패 시 어느 resource로 되돌리는가?
- Backend·state가 손상되거나 apply가 부분 실패하면 누가 어떤 evidence로 재개·복구하는가?

이 답이 없는 상태에서는 resource block, import command, state address, apply·rollback
procedure를 실행 가능한 현재 문서로 작성하지 않습니다.

## 관련 문서

- **현재 AWS 구성 개념**: [VPC network](100-vpc-network.md),
  [EC2 Kubernetes node](110-ec2-kubernetes-node.md),
  [ECR·IAM 기반](120-ecr-iam-foundation.md), [RDS PostgreSQL](130-rds-postgresql.md)
- **현재 수동 구축 절차**: [AWS 최초 구축 runbook](../../operations/100-aws-initial-provisioning-runbook.md)
- **Image 공급 계약**: [Container image delivery](200-container-image-delivery.md)
