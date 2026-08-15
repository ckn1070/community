# VPC 최초 구축 runbook

AWS Console에서 staging VPC와 최초 network resource를 한 번에 생성하는 절차입니다.
VPC의 주소 구조, public·private 의미, NAT·IPv6·S3 endpoint 선택 이유와 미선택 대안은
[VPC network](../project/infrastructure/100-vpc-network.md)가 소유합니다.

## 사용 시점과 actor

- **사용 시점**: `stg-car-community` AWS 기반을 처음 만들 때 가장 먼저 실행합니다.
- **actor**: 서울 리전에서 VPC resource를 생성할 권한이 있는 AWS Console 운영자입니다.
- **실행 위치**: AWS Console `VPC → VPC 생성`입니다.

## 전제와 입력

- Console region이 `ap-northeast-2`인지 확인합니다.
- 연결할 기존 network가 있다면 `10.0.0.0/16`과 CIDR이 겹치지 않는지 먼저 확인합니다.
- 다음 네 tag를 구분합니다. `Name`은 Console의 이름 태그 자동 생성이 만들며 추가 태그에
  다시 입력하지 않습니다.

| 구분 | 키 | 값 |
| --- | --- | --- |
| 이름 태그 자동 생성 접두사 | `Name` 파생 입력 | `stg-car-community` |
| 추가 태그 | `project` | `car-community` |
| 추가 태그 | `environment` | `stg` |
| 추가 태그 | `managed-by` | `manual` |

## 정상 실행 순서와 단계별 기대 결과

### 1. VPC workflow 입력

`VPC → VPC 생성`에서 다음 값을 입력합니다.

| Console 옵션 | 입력값 |
| --- | --- |
| 생성할 리소스 | `VPC 등` |
| 이름 태그 자동 생성 | 활성화, `stg-car-community` |
| IPv4 CIDR 블록 | `IPv4 CIDR 수동 입력` |
| IPv4 CIDR | `10.0.0.0/16` |
| IPv6 CIDR 블록 | `IPv6 CIDR 블록 없음` |
| 테넌시 | `기본값` |
| 가용 영역 수 | `2` |
| AZ 사용자 지정 | `ap-northeast-2a`, `ap-northeast-2b` |
| 퍼블릭 서브넷 수 | `2` |
| 프라이빗 서브넷 수 | `2` |
| NAT Gateway | `없음` |
| Internet Gateway | public subnet과 함께 생성 |
| Egress-only Internet Gateway | 생성하지 않음 |
| VPC endpoint | `S3 게이트웨이` |
| DNS 호스트 이름 활성화 | 활성화 |
| DNS 확인 활성화 | 활성화 |

`서브넷 CIDR 블록 사용자 지정`을 열고 다음 네 값을 정확히 입력합니다.

| AZ | 역할 | CIDR | 기대 `Name` 태그 |
| --- | --- | --- | --- |
| `ap-northeast-2a` | public 1 | `10.0.0.0/20` | `stg-car-community-subnet-public1-ap-northeast-2a` |
| `ap-northeast-2b` | public 2 | `10.0.16.0/20` | `stg-car-community-subnet-public2-ap-northeast-2b` |
| `ap-northeast-2a` | private 1 | `10.0.128.0/20` | `stg-car-community-subnet-private1-ap-northeast-2a` |
| `ap-northeast-2b` | private 2 | `10.0.144.0/20` | `stg-car-community-subnet-private2-ap-northeast-2b` |

각 `/20`은 `10.0.0.0/16` 안에 있고 서로 겹치지 않아야 합니다. 나머지 VPC 주소 공간의
용도는 이 단계에서 정하지 않습니다.

### 2. Preview 확인과 생성

다음을 preview에서 확인합니다.

- VPC 이름이 `stg-car-community-vpc`입니다.
- `ap-northeast-2a`와 `ap-northeast-2b`에 public·private subnet이 하나씩 표시됩니다.
- NAT Gateway는 `없음`, IPv6는 없음, S3 Gateway Endpoint는 선택됨입니다.
- Internet Gateway와 public route가 생성 대상으로 표시됩니다.
- DNS 두 옵션과 추가 tag가 입력값과 같습니다.

`미리 보기 코드`는 입력을 코드 형태로 보는 보조 기능입니다. 확인 여부가 resource
입력이나 관리 source를 바꾸지 않습니다. 값이 모두 일치할 때 `VPC 생성`을 선택합니다.

### 3. 단계 기대 결과 확인

`VPC 워크플로 생성` 결과에서 다음 항목이 모두 성공인지 확인합니다.

- VPC와 DNS 호스트 이름·DNS 확인 속성
- subnet 4개
- Internet Gateway 생성과 VPC 연결
- public·private route table, route와 subnet association
- S3 Gateway Endpoint 생성과 private subnet route table 연결

필요한 경우 `VPC → VPC → stg-car-community-vpc → 리소스 맵 → 세부 정보 표시`에서
CIDR, AZ, route, endpoint와 tag를 입력표에 대조합니다. public subnet의 `public IPv4
자동 할당` 상태는 이 VPC workflow에서 확정한 입력이 아니므로 추측하지 않습니다. EC2
launch에서 instance의 public IPv4를 명시적으로 활성화합니다.

## 중단·실패 판정

- 기존 연결 network와 `10.0.0.0/16`이 겹치면 생성하지 않습니다.
- preview의 AZ, subnet 수·CIDR, gateway·endpoint·DNS·tag가 입력표와 다르면 생성하지
  않습니다.
- workflow 결과에 실패나 누락이 하나라도 있으면 VPC 단계를 성공으로 처리하지 않습니다.
- EC2 launch 화면에서 이 VPC나 필요한 subnet을 찾을 수 없으면 EC2 생성을 시작하지
  않습니다.
- private subnet에 NAT Gateway가 없으므로 일반 IPv4 인터넷·ECR API·ECR registry
  송신이 생긴 것으로 간주하지 않습니다. S3 Gateway Endpoint는 S3와 ECR image layer의
  S3 경로만 제공하며 ECR API·registry 경로를 대신하지 않습니다.

## 복구·재개

1. workflow 결과에서 실패한 resource와 오류를 확인하고 이미 생성된 resource ID를
   보존합니다.
2. resource map에서 입력표와 다른 CIDR, association, route와 endpoint 연결을 찾습니다.
3. 잘못 생성된 VPC와 부속 resource의 삭제 순서·복구 절차는 아직 확정되지 않았으므로
   부분 성공 resource를 임의로 삭제하거나 같은 이름으로 다시 만들지 않습니다.
4. 원인을 해결한 뒤 실패한 resource의 상태를 다시 확인합니다. 모든 결과가 일치한 뒤
   [EC2 node 최초 구축](120-ec2-node-provisioning-runbook.md)으로 이동합니다.

## 완료 확인

- [ ] `VPC 생성` workflow가 실패 없이 종료됐습니다.
- [ ] VPC, DNS 두 속성, S3 Gateway Endpoint, subnet 4개, Internet Gateway, route
  table·route·association이 모두 생성됐습니다.
- [ ] VPC CIDR, subnet AZ·CIDR, NAT 부재, S3 endpoint와 tag가 입력표와 같습니다.
- [ ] 실제 완료 상태를
      [Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)에
      반영했습니다.

## 공식 기준과 관련 문서

- **선택·영향·대안**: [VPC network](../project/infrastructure/100-vpc-network.md)
- **상위 절차**: [AWS 최초 구축](100-aws-initial-provisioning-runbook.md)
- [VPC 리소스 맵 확인](https://docs.aws.amazon.com/vpc/latest/userguide/view-vpc-resource-map.html)
