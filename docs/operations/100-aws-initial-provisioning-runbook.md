# AWS 최초 구축 runbook

AWS Console 운영자가 서울 리전에 staging 기반 resource를 처음 구축할 때 사용하는
진입점입니다. 이 문서는 공통 전제와 실행 순서만 소유하고, 각 resource의 exact 입력과
복구 checkpoint는 목적별 runbook이 소유합니다.

시스템의 구성요소·작동 원리·선택 이유는
[인프라 시스템](../project/300-infrastructure-system.md), 현재 실제 구축 상태와 재개 위치는
[Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)에서
확인합니다. 이 runbook의 checklist나 repository 검증은 AWS 계정의 live 완료 증거가
아닙니다.

## 사용 시점과 actor

- **사용 시점**: 새 staging AWS 기반을 수동으로 처음 만들거나 같은 계약으로 다시
  구축할 때 `110`을 시작하기 전에 읽고, `140`까지 마친 뒤 전체 완료를 판정할 때 다시
  사용합니다.
- **actor**: 대상 AWS 계정에서 VPC·EC2·ECR·IAM·RDS를 생성할 권한이 있는 AWS Console
  운영자입니다.
- **대상 리전**: 서울 `ap-northeast-2`입니다. 다른 리전에서 실행하지 않습니다.

## 공통 전제와 입력

- 대상 프로젝트는 `car-community`, 환경은 `stg`, 현재 관리 방식은 `manual`입니다.
- AWS Console의 현재 화면에서 region과 account를 매 단계 다시 확인합니다.
- key pair private key, AWS Secrets Manager secret 값과 account credential을 repository,
  문서, 화면 캡처와 채팅에 기록하지 않습니다.
- VPC CIDR을 연결할 기존 VPC·회사망·VPN·온프레미스 network가 이미 있다면
  `10.0.0.0/16`과 겹치지 않는지 VPC 생성 전에 확인합니다. 아직 연결 대상이 없다면
  미래 연결을 가정해 생성을 막지 않고, 연결을 결정할 때 다시 검토합니다.
- 각 단계의 입력과 Console preview가 다르면 다음 단계로 진행하지 않습니다.
- 이 구성은 비용을 제한한 staging 학습·검증용입니다. 단일 EC2가 Kubernetes control
  plane과 worker를 함께 실행하며 production 고가용성을 보장하지 않습니다.

이 umbrella는 자체 AWS resource를 만들지 않습니다. 아래 순서의 목적별 runbook이 실제
resource를 만들며, 이 문서의 완료 확인은 그 결과를 한 번에 판정하는 종료 checkpoint입니다.

선택값의 의미와 재검토 조건은 각 resource 개념 문서가 소유합니다.

- **VPC 기준**: [VPC network](../project/infrastructure/100-vpc-network.md)
- **EC2 기준**: [EC2 Kubernetes node](../project/infrastructure/110-ec2-kubernetes-node.md)
- **ECR·IAM 기준**: [ECR·IAM 기반](../project/infrastructure/120-ecr-iam-foundation.md)
- **RDS 기준**: [RDS PostgreSQL](../project/infrastructure/130-rds-postgresql.md)

## 정상 실행 순서와 handoff

1. [VPC 최초 구축](110-vpc-provisioning-runbook.md)을 완료합니다.
   - 기대 결과: VPC, 2개 AZ의 public·private subnet, Internet Gateway, route table,
     S3 Gateway Endpoint와 DNS 속성이 생성됩니다.
2. [EC2 Kubernetes node 최초 구축](120-ec2-node-provisioning-runbook.md)을 완료합니다.
   - 기대 결과: public subnet의 단일 ARM64 node가 실행되고 제한된 SSH 경로가
     동작합니다.
3. [Private ECR·image 공급 IAM 최초 구축](130-ecr-iam-provisioning-runbook.md)을
   완료합니다.
   - 기대 결과: private repository, GitHub OIDC push role과 EC2 pull instance profile이
     준비됩니다.
4. [RDS PostgreSQL 최초 구축](140-rds-provisioning-runbook.md)을 완료합니다.
   - 기대 결과: private subnet의 PostgreSQL DB가 `사용 가능`이고 EC2에서 private
     DNS·TCP `5432`·credential 인증이 성공합니다.
5. EC2에 접속한 뒤 host와 Kubernetes를 변경하는 작업은
   [Kubernetes bootstrap runbook](200-kubernetes-bootstrap-runbook.md)으로 인계합니다.

VPC는 EC2와 RDS의 선행 조건이고, EC2는 ECR pull profile 연결과 RDS 자동 연결의 선행
조건입니다. RDS live 연결 확인이 끝나지 않았더라도 EC2 host bootstrap 자체는 시작할
수 있지만, AWS 최초 구축 전체를 완료로 판정하지는 않습니다.

## 중단·실패 판정

- 어떤 단계든 Console 작업이 일부 resource에서 실패하거나 preview·생성 결과가 해당
  runbook의 exact 입력과 다르면 이후 resource를 생성하지 않습니다.
- 이미 존재하는 같은 이름의 resource를 발견하면 새로 만들거나 덮어쓰지 말고 ARN·ID,
  설정과 연결 관계를 먼저 대조합니다.
- 예상하지 않은 route, public access, security group rule 또는 IAM permission이
  생기면 성공으로 간주하지 않습니다.
- 실제 live 상태를 확인하지 못한 단계는 repository 문서가 있다는 이유로 완료 처리하지
  않습니다.

## 복구·재개

1. 실패한 목적별 runbook의 `중단·실패 판정`에서 마지막 정상 checkpoint를 찾습니다.
2. AWS Console event·오류와 이미 생성된 resource ID를 기록하되 secret 값은 기록하지
   않습니다.
3. 부분 생성 resource를 임의로 삭제하거나 새 이름으로 우회하지 않습니다. 삭제·교체
   절차가 확정되지 않은 resource는 그대로 두고 원인을 먼저 확인합니다.
4. 해당 runbook의 복구 조건을 충족한 뒤 실패한 단계부터 재개합니다. 완료한 선행
   resource를 다시 만들지 않습니다.

## 완료 확인

다음을 모두 live 환경에서 확인해야 AWS 최초 구축이 완료됩니다.

- VPC 단계의 생성·구성 checklist를 통과했습니다.
- EC2가 `실행 중`, 상태 검사 통과 상태이고 SSH 접속이 성공합니다.
- ECR·IAM resource가 최소 권한 checklist를 충족하고 EC2에 pull profile이 연결됐습니다.
- RDS가 `사용 가능`이고 EC2에서 `carmunity` database 인증까지 성공합니다.
- 실제 완료·미완료 상태가
  [Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)에
  현재형으로 반영돼 있습니다.

## 관련 문서

- **시스템 설명**: [인프라 시스템](../project/300-infrastructure-system.md)
- **현재 live 상태·재개 위치**:
  [Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)
- **후속 실행 절차**: [Kubernetes bootstrap runbook](200-kubernetes-bootstrap-runbook.md)
- **향후 자동화 변경 경계**: [Terraform 전환 경계](../project/infrastructure/340-terraform-transition.md)
