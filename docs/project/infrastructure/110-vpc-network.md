# VPC network

이 상세 SOT는 staging VPC의 network 구성요소, 현재 선택값, 선택 이유·영향·미선택 대안과
재검토 조건을 소유합니다. AWS Console에서 실제로 생성·확인하는 방법은
[VPC 최초 구축 runbook](../../operations/110-vpc-provisioning-runbook.md)이 소유합니다.

## Network mental model

VPC는 `10.0.0.0/16` 전체 address·routing·DNS 경계이고, subnet은 그 안에서 하나의 AZ와
route 성격을 가진 실제 address 범위입니다. 현재 두 AZ 각각에 public·private subnet을
하나씩 둡니다.

```text
stg-car-community-vpc 10.0.0.0/16
├── ap-northeast-2a
│   ├── public1  10.0.0.0/20
│   └── private1 10.0.128.0/20
└── ap-northeast-2b
    ├── public2  10.0.16.0/20
    └── private2 10.0.144.0/20
```

Public subnet은 Internet Gateway로 향하는 IPv4 기본 route를 가질 수 있고, private subnet은
외부에서 직접 route하지 않을 resource 후보입니다. NAT Gateway를 만들지 않았으므로 private
subnet에 일반 IPv4 인터넷 송신 경로는 없습니다. S3 Gateway Endpoint는 S3 route만 제공하며
ECR API·registry와 다른 AWS service의 private 연결을 대신하지 않습니다.

## 적용 범위와 Best Practice 판정

현재 선택은 수동 staging 단일 node 목표에 대한 조건부 구성입니다.

- SSH source `/32`, 불필요한 application port 미개방, EBS·RDS 암호화와 IMDSv2 only 같은
  최소 공개·암호화 원칙은 보안 기본기에 부합합니다.
- NAT Gateway 없음과 public EC2는 비용 우선 trade-off입니다. Private compute 일반 권고나
  production network Best Practice로 확대하지 않습니다.
- 두 AZ의 address 공간은 한 AZ에만 의존하지 않는 topology를 만들지만, 현재 EC2와 RDS
  Single-AZ 자체를 고가용성으로 만들지는 않습니다.
- IPv6·dual-stack, Auto Scaling, EKS와 장기 address plan은 현재 선택으로 선결하지
  않습니다. Staging external ingress는 이미 만든 두 public subnet을 ALB
  placement에 재사용하며 address 범위나 route를 새로 만들지 않습니다.

## 현재 선택과 이유

| 옵션·결과 | 현재 선택 | 판정 | 이유와 영향 | 재검토 조건 |
| --- | --- | --- | --- | --- |
| 생성 단위 | `VPC 등` | 현재 Console 수동 구축에 적합 | VPC와 subnet·route table·Internet Gateway·선택한 endpoint를 한 workflow로 구성하는 Console 편의 기능입니다. Terraform 사용 여부와 무관합니다. | Resource별 생성·검증을 완전히 분리할 때 |
| 이름 태그 | 자동 생성, 접두사 `stg-car-community` | 명명 일관성에 부합 | 환경·project 접두사 누락과 오타를 줄이고 VPC 이름을 `stg-car-community-vpc`로 만듭니다. AWS가 강제하는 유일한 형식은 아닙니다. | Project·환경·이름 정책 변경 |
| IPv4 CIDR | 수동 `10.0.0.0/16` | 유효한 RFC 1918 대역, 용량 근거는 미확정 | IPAM 없이 65,536개 주소 범위를 직접 소유합니다. Workload 규모에서 역산한 장기 address plan은 아닙니다. | Peering·VPN·조직 network 연결, 용량 계산 또는 충돌 발견 |
| IPv6 CIDR | 없음 | 현재 운영 범위에 대한 조건부 선택 | IPv6 route·security·DNS·장애 대응을 함께 운영하지 않아 복잡도를 제한합니다. IPv6가 일반적으로 불필요하다는 뜻은 아닙니다. | IPv6 cluster·dual-stack 요구 확정 |
| Tenancy | VPC 기본값 | 일반 구성에 적합 | Dedicated hardware 비용·instance type 제약을 VPC 전체에 강제하지 않습니다. | 규제·계약·license가 전용 hardware를 요구 |
| AZ | `ap-northeast-2a`, `ap-northeast-2b` 두 개 | Staging의 두 failure domain 선택 | 두 AZ에 public·private address 공간을 둡니다. 현재 resource가 모두 다중 AZ라는 뜻은 아닙니다. | 3-AZ 가용성 또는 단일 AZ 비용 절감 요구 |
| Public subnet | AZ별 하나, 총 2개 | 현재 2-AZ ingress 구조와 일치 | `2a`·`2b`에 internet-facing ALB placement를 둡니다. LBC discovery를 위해 public role tag를 사용하지만 subnet ID는 Git에 고정하지 않습니다. | Public ingress 제거 또는 AZ 수 변경 |
| Private subnet | AZ별 하나, 총 2개 | 현재 격리 구조와 일치 | 외부에 직접 노출하지 않을 RDS 등 resource 후보를 둡니다. Compute·database 계층을 각각 별도 subnet으로 분리하지는 않았습니다. | 계층별 route·NACL·address plan 또는 AZ 수 변경 |
| NAT Gateway | 없음 | 사용자 비용 제약에 따른 조건부 선택 | NAT Gateway의 시간당·처리량 비용을 사용하지 않으며 private subnet의 일반 IPv4 outbound를 포기합니다. | Private resource가 일반 인터넷으로 나가야 함 |
| Internet Gateway | Public subnet과 함께 생성 | NAT와 다른 필수 public 연결 | Public IPv4 resource가 internet route를 사용하게 합니다. NAT를 만들지 않는다고 Internet Gateway까지 없어지는 것은 아닙니다. | Public subnet 제거 |
| Egress-only Internet Gateway | 없음 | IPv6가 없어 해당 없음 | IPv6 private outbound 전용 resource입니다. | IPv6 사용 확정 |
| VPC Endpoint | S3 Gateway Endpoint | S3 경로에 한정된 조건부 선택 | S3 traffic을 Internet/NAT 대신 endpoint route로 보내며 Gateway Endpoint 자체의 시간당 요금은 없습니다. S3를 사용하지 않으면 효과가 없습니다. | S3 미사용 또는 endpoint policy·route table 범위 변경 |
| DNS hostnames | 활성 | AWS 기본 DNS 사용 | EC2·AWS resource DNS hostname 사용을 허용합니다. | 별도 DNS 체계 채택 |
| DNS resolution | 활성 | AWS 기본 resolver 사용 | Route 53 Resolver가 VPC·AWS service name을 해석합니다. | Custom resolver·forwarding 정책 확정 |

## Subnet address 계약

| AZ | 역할 | CIDR | 현재 의미 |
| --- | --- | --- | --- |
| `ap-northeast-2a` | Public | `10.0.0.0/20` | 현재 단일 EC2 node와 internet-facing ALB placement 대상 subnet |
| `ap-northeast-2b` | Public | `10.0.16.0/20` | Internet-facing ALB의 두 번째 AZ placement 대상 subnet |
| `ap-northeast-2a` | Private | `10.0.128.0/20` | RDS DB subnet group 후보 |
| `ap-northeast-2b` | Private | `10.0.144.0/20` | RDS DB subnet group 후보 |

각 `/20`은 4,096개 주소를 포함하고 서로 겹치지 않으며 VPC `/16` 안에 있습니다. `/20`
크기와 public/private 사이의 간격은 workload 용량에서 산출하지 않았으므로 AWS 일반 권고나
장기 address plan으로 승격하지 않습니다. 나머지 VPC 주소 공간은 현재 용도가 미정입니다.

추가 tag는 `project=car-community`, `environment=stg`, `managed-by=manual`입니다. 앞의 두
tag는 검색·비용 분류, 마지막 tag는 현재 수동 관리 방식을 식별할 뿐 resource 동작이나
변경 권한을 강제하지 않습니다. `Name`은 자동 생성이 소유하므로 중복 tag로 추가하지
않습니다.

Internet-facing ALB를 발견하도록 public `2a`·`2b` subnet에만
`kubernetes.io/role/elb=1`을 추가합니다. 이 tag는 비용 분류가 아니라 AWS Load
Balancer Controller가 public load balancer placement 후보를 찾는 기능 계약입니다.
적용 전에 각 subnet의 `0.0.0.0/0 -> igw-*` route와 available IPv4 8개 이상을
live 확인하며 private subnet에는 이 tag를 두지 않습니다.

## 검토했지만 선택하지 않은 대안

| 대안 | 의미 | 현재 제외 이유 | 적합해지는 조건 |
| --- | --- | --- | --- |
| `VPC만` | VPC container만 만들고 부속 resource를 각각 만듭니다. | 확정한 네 subnet과 연결 resource를 같은 Console workflow로 만들기로 했습니다. | 각 resource를 완전히 분리해 조립·학습할 때 |
| 이름 자동 생성 비활성 | 모든 `Name` tag를 직접 입력합니다. | 공통 접두사 반복 입력의 누락·오타를 줄입니다. | Resource별 이름이 공통 접두사로 표현되지 않을 때 |
| IPv4 IPAM 할당 | 중앙 pool과 allocation rule로 CIDR을 할당합니다. | 현재 IPAM pool·조직 단위 주소 관리가 없습니다. | 다중 account·region CIDR 중복 방지와 중앙 할당이 필요할 때 |
| AWS 제공 IPv6 | AWS가 VPC IPv6 범위를 할당합니다. | 현재 route·security·DNS 운영을 IPv4로 제한했습니다. | Dual-stack 운영·보안 절차를 확정할 때 |
| IPAM IPv6 | 조직 IPv6 pool에서 할당합니다. | IPv6와 IPAM pool 모두 사용하지 않습니다. | 조직이 관리하는 IPv6 pool을 배포할 때 |
| BYOIP IPv6 | AWS에 등록한 소유 IPv6 범위를 사용합니다. | 등록된 소유 범위와 routing 책임이 없습니다. | BYOIP 등록·routing 책임이 확정될 때 |
| Dedicated tenancy | Launch instance에 dedicated hardware를 기본 요구합니다. | 규제·license 요구 없이 비용·type 제약만 늘어납니다. | 계약·규제가 필수로 요구할 때 |
| AZ 1개 | Subnet을 한 AZ에만 둡니다. | Address failure domain을 하나로 제한하지 않습니다. | 폐기 가능한 일회성 환경에서 비용·단순성이 우선일 때 |
| AZ 3개 | 세 AZ에 subnet을 둡니다. | 단일 node staging에 세 번째 AZ 공간을 미리 소비할 근거가 없습니다. | Workload·ingress가 3-AZ 배치를 요구할 때 |
| AZ 자동 선택 | Console이 가능한 AZ를 고릅니다. | 현재는 `2a`·`2b`를 직접 확인해 재현합니다. | 특정 AZ 이름 고정이 필요 없을 때 |
| Public subnet 0개 | Internet route용 subnet을 만들지 않습니다. | NAT·bastion·SSM 없이 EC2 SSH·outbound 경로가 필요합니다. | Compute를 모두 private에 두고 접속·outbound를 별도로 확정할 때 |
| Private subnet 0개 | 격리 resource용 subnet을 만들지 않습니다. | 두 AZ에 RDS 등 private 후보 공간을 둡니다. | Public-only 실험으로 명시적으로 제한할 때 |
| Private subnet 4개 | AZ마다 application·database 등 private 계층을 더 나눕니다. | 계층별 route·NACL·address plan이 미확정입니다. | 두 private 계층의 독립 정책이 확정될 때 |
| Console subnet CIDR 제안 | Console이 VPC 안의 subnet 범위를 자동 제안합니다. | Exact 네 범위를 직접 확인·고정했습니다. | 제안값을 검토해 그대로 수용할 때 |
| Regional NAT Gateway | 다중 AZ에 자동 확장되는 유료 IPv4 outbound를 제공합니다. | 비용과 private 일반 outbound를 현재 범위에서 제외했습니다. | 관리형 다중 AZ private outbound가 필요할 때 |
| Zonal NAT Gateway | 특정 AZ에서 유료 IPv4 outbound를 제공합니다. | 같은 비용 제약이며 multi-AZ면 AZ별 route·복원 설계가 필요합니다. | AZ별 egress와 비용을 수용할 때 |
| VPC Endpoint 없음 | S3 endpoint와 route를 만들지 않습니다. | S3 경로를 NAT 없이 쓸 수 있게 현재 선택했습니다. | S3 미사용 또는 명시한 다른 경로 사용 |
| DNS hostname 비활성 | Public EC2의 AWS DNS hostname을 사용하지 않습니다. | 현재 public EC2 hostname 사용 가능성을 유지합니다. | 별도 DNS만 사용할 때 |
| DNS resolution 비활성 | Amazon-provided resolver를 끕니다. | EC2·AWS service hostname 해석이 필요합니다. | Custom resolver가 전체 이름 해석을 소유할 때 |

`미리 보기 코드`는 Console 입력을 code로 보여 주는 보조 기능일 뿐 현재 Terraform source of
truth가 아닙니다. Resource map에 표시되는 ID·남은 IP·association은 live 파생값이므로
고정 개념 계약으로 복사하지 않습니다.

## 실패·변경 시 함께 볼 경계

- 다른 VPC·회사망·VPN·on-premises와 연결하기 전에 `10.0.0.0/16`, Pod
  `192.168.0.0/16`, Service `10.96.0.0/12`의 충돌을 함께 다시 검토합니다.
- Private node가 ECR API·registry나 일반 internet에 접근해야 하면 NAT 또는 필요한 VPC
  endpoint 집합을 별도로 설계해야 합니다. S3 endpoint만으로 충분하다고 가정하지 않습니다.
- `2c`·`2d`를 RDS 후보로 쓰려면 AZ 이름만 선택하는 것이 아니라 먼저 이 VPC에 subnet과
  route를 설계·생성해야 합니다.
- VPC resource 삭제 순서와 복구 절차는 현재 확정되지 않았습니다.

이 경계에서 아직 착수하지 않은 network·복구 후보의 상태와 도입 순서는
[004 Infrastructure evolution backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
소유합니다.

## 근거와 관련 문서

- **실제 최초 구축 절차**: [VPC 최초 구축 runbook](../../operations/110-vpc-provisioning-runbook.md)
- **EC2 배치와 node 선택**: [120 EC2 Kubernetes node](120-ec2-kubernetes-node.md)
- **RDS private placement**: [140 RDS PostgreSQL](140-rds-postgresql.md)
- **Public subnet을 소비하는 ingress 구조**:
  [170 AWS Load Balancer ingress](170-aws-load-balancer-ingress.md)
- **Subnet tag·route·free IP live 절차**:
  [370 Staging 외부 HTTPS ingress 최초 구축 runbook](../../operations/370-staging-external-https-ingress-bootstrap-runbook.md)
- [AWS VPC와 부속 resource 생성](https://docs.aws.amazon.com/vpc/latest/userguide/create-vpc.html)
- [VPC 생성 option](https://docs.aws.amazon.com/vpc/latest/userguide/create-vpc-options.html)
- [Regional NAT Gateway](https://docs.aws.amazon.com/vpc/latest/userguide/nat-gateways-regional.html)
- [Zonal NAT Gateway](https://docs.aws.amazon.com/vpc/latest/userguide/nat-gateway-basics.html)
- [S3 Gateway Endpoint](https://docs.aws.amazon.com/vpc/latest/privatelink/vpc-endpoints-s3.html)
- [VPC DNS 속성](https://docs.aws.amazon.com/vpc/latest/userguide/vpc-dns.html)
- [ECR VPC Endpoint 요구사항](https://docs.aws.amazon.com/AmazonECR/latest/userguide/vpc-endpoints.html)
