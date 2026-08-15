# EC2 Kubernetes node 최초 구축 runbook

AWS Console에서 Kubernetes control plane과 worker를 함께 실행할 단일 ARM64 EC2를 직접
생성하고 SSH 접속까지 확인하는 절차입니다. 이 구성의 비용·가용성·architecture 선택과
재검토 조건은 [EC2 Kubernetes node](../project/infrastructure/110-ec2-kubernetes-node.md)가
소유합니다.

## 사용 시점과 actor

- **사용 시점**: [VPC 최초 구축](110-vpc-provisioning-runbook.md)이 완료된 뒤 staging
  Kubernetes node를 한 대 처음 만들 때 사용합니다.
- **actor**: EC2, EBS, security group과 key pair를 조회·생성할 수 있는 AWS Console
  운영자입니다.
- **실행 위치**: AWS Console `EC2 → 인스턴스 → 인스턴스 시작`입니다.

## 전제와 입력

- region은 `ap-northeast-2`입니다.
- VPC `stg-car-community-vpc`와 2a public subnet이 존재합니다.
- key pair `example-ec2-key-pair`와 대응 private key를 운영자가 안전하게 보관하고 있습니다.
  private key는 repository에 저장하지 않습니다.
- node에 배포할 image는 `linux/arm64`를 지원해야 합니다. 이 launch 절차는 application
  image 호환성을 증명하지 않습니다.
- Auto Scaling Group과 launch template을 사용하지 않는 직접 생성입니다. instance
  이름을 `stg-car-community-k8s-node-01`로 고정합니다.

## 정상 실행 순서와 단계별 기대 결과

### 1. 이름, AMI와 instance type

다음 값을 선택합니다. AMI ID·root snapshot ID·시간당 가격은 region과 release 시점에
따라 달라지므로 특정 화면 숫자를 고정 입력하지 않고 조건을 launch 직전에 대조합니다.

| Console 옵션 | 입력·확인값 |
| --- | --- |
| instance 수 | `1` |
| 이름 | `stg-car-community-k8s-node-01` |
| 추가 EC2 tag | 추가하지 않음 (`Name`만 사용) |
| AMI | Canonical의 `Ubuntu Server 24.04 LTS`, Server, ARM64 |
| AMI architecture | `64비트(Arm)` |
| SSH username | `ubuntu` |
| AMI 읽기 전용 속성 | HVM, EBS root device, ENA 활성 |
| instance type filter | `모든 세대` 활성화 |
| instance type | `t4g.small`, 2 vCPU, 2 GiB RAM |
| key pair | `example-ec2-key-pair` |
| 구매 방식 | 별도 구매 옵션 `없음`인 On-Demand |

Ubuntu Pro나 Marketplace 유료 image를 선택하지 않습니다. `t4g.micro`의 1 GiB 대신
`t4g.small`의 2 GiB를 선택하고, x86 AMI와 Arm instance를 섞지 않습니다. EC2 compute,
EBS와 public IPv4 요금은 Console의 현재 region 가격을 생성 전에 다시 확인합니다.

### 2. 기본 network와 security group

| Console 옵션 | 입력값 |
| --- | --- |
| VPC | `stg-car-community-vpc` (`10.0.0.0/16`) |
| subnet | `stg-car-community-subnet-public1-ap-northeast-2a` (`10.0.0.0/20`) |
| public IPv4 자동 할당 | 활성화 |
| security group | 새 security group |
| security group 이름 | `stg-car-community-k8s-node-sg` |
| 설명 | `stg car-community single-node Kubernetes EC2` |
| inbound SSH | TCP `22`, source `내 IP`의 현재 public IPv4 `/32` |
| SSH rule 설명 | 실행 시점의 실제 접속 위치 label. 현재 확인값은 `Sillim-Home` |
| inbound HTTP·HTTPS | 추가하지 않음 |
| 다른 inbound rule | 추가하지 않음 |
| outbound | 새 security group의 기본 outbound 허용 유지 |

`내 IP`는 실제 실행 시점의 운영자 public IPv4 한 개여야 합니다. 위치·회선이나 주소가
바뀌면 `/32` rule과 SSH rule 설명을 함께 실제 값으로 갱신합니다. SSH를 `0.0.0.0/0`에
열지 않습니다. Kubernetes API, NodePort와
application port는 source와 필요가 확정되기 전에 미리 열지 않습니다.

### 3. 고급 network 구성

기본 network section의 선택이 고급 section에 반영됐는지 확인하고 다음 값을 유지합니다.

| Console 옵션 | 입력·확인값 |
| --- | --- |
| device index | `0` |
| network interface | 새 interface |
| interface 설명 | 비워둠 |
| subnet·security group | 앞 단계 선택값 |
| public IP 자동 할당 | 활성화 |
| primary private IPv4 | 자동 할당, 직접 입력하지 않음 |
| secondary private IPv4 | 선택하지 않음 |
| IPv4 prefix | 선택하지 않음 |
| IPv6 IP·prefix·primary IPv6 | 비활성, 선택하지 않음 |
| 종료 시 interface 삭제 | `예` |
| interface type | 기본값 |
| network card index | 비활성 기본값 |
| ENA Express·ENA Express UDP·ENA queue | 지원되지 않는 기본 상태 |
| 유휴 연결 추적 제한 시간 | 비활성 |
| 추가 network interface | 추가하지 않음 |

`기본 IP`의 `123.123.123.1` 같은 흐린 문자열은 placeholder이며 입력값이 아닙니다.
`보조 IP`, prefix와 IPv6의 `선택`은 address가 지정됐다는 뜻이 아니라 초기 상태입니다.
subnet ID, owner와 남은 IP 수는 live 파생값이므로 실행 시 이름·AZ·CIDR과 교차 확인하되
runbook 입력으로 고정하지 않습니다.

### 4. Storage와 filesystem

| Console 옵션 | 입력·확인값 |
| --- | --- |
| EBS volume 수 | `1` |
| root device mapping | `/dev/sda1`, 선택한 AMI의 root snapshot |
| root volume | `20 GiB` |
| volume type | `gp3` |
| IOPS | `3000` |
| throughput | `125 MiB/s` |
| encryption | 활성화, 기본 `aws/ebs` KMS key |
| 종료 시 삭제 | `예` |
| volume 초기화 속도 | 입력하지 않음 |
| 추가 EBS volume | 추가하지 않음 |
| file system | `없음` |

OS에서 root device가 NVMe 이름으로 보일 수 있어도 Console mapping `/dev/sda1`을 임의로
바꾸지 않습니다. `20 GiB`는 현재 시작값이며 EBS는 나중에 확장할 수 있지만 직접 축소할
수 없습니다. terminate하면 root volume도 삭제되므로 보존할 데이터는 먼저 별도
backup해야 합니다. `백업 정보를 보려면 새로 고침` 영역은 Data Lifecycle Manager
정보이며 launch 입력이 아닙니다. 선택한 type에 instance store가 없는 안내도 추가 local
disk가 생겼다는 뜻이 아닙니다.

### 5. 고급 세부 정보

다음 표의 `기본값`은 최종 Console 동작을 그대로 유지한다는 뜻입니다.

| Console 옵션 | 입력·확인값 |
| --- | --- |
| domain join directory | 선택하지 않음 |
| IAM instance profile | launch 시 선택하지 않음 |
| hostname type | `IP 이름` |
| DNS hostname | IP 이름의 IPv4 `A` record 활성, resource 기반 IPv4·IPv6 record 비활성 |
| instance auto recovery | `기본값` |
| instance initiated shutdown behavior | `중지` |
| 최대 절전 | 비활성 |
| 종료 방지 | 비활성 |
| 중지 방지 | 비활성 |
| 세부 CloudWatch monitoring | 비활성, 기본 5분 metric |
| CPU credit 사양 | `표준(Standard)` |
| placement group | 선택하지 않음 |
| EBS-optimized | 활성화 |
| instance bandwidth configuration | 선택하지 않음 |
| 구매 옵션 | `없음` |
| capacity reservation | Console 기본값, 특정 reservation 미지정 |
| tenancy | VPC 기본값 |
| RAM disk ID·kernel ID | 선택하지 않음 |
| Nitro Enclave | 비활성 |
| license configuration | 선택하지 않음 |
| CPU options | 기본 CPU 옵션, 2 vCPU |
| nested virtualization | 지원되지 않는 기본 상태 |
| metadata accessible | 활성화 |
| metadata IPv6 endpoint | 비활성 |
| metadata version | `V2만(토큰 필수)` |
| metadata response hop limit | `2` |
| metadata tag access | 비활성 |
| user data | 비워둠, file 선택 안 함 |
| user data base64 checkbox | 체크하지 않음 |

IAM profile을 비워 launch하는 것은 최종 node에 IAM 권한을 주지 않는다는 뜻이 아닙니다.
ECR 단계에서 pull 전용 instance profile을 기존 instance에 연결합니다. IMDS hop limit `2`는
container network에서 metadata 응답 경로를 보존하므로 pod에서 IMDS가 불필요해지면
개념 문서의 재검토 조건에 따라 `1`을 검토합니다. 현재는 `V2만`, tag access 비활성을
반드시 확인합니다. Capacity Reservation을 특정하지 않아도 계정에 조건이 맞는 open
reservation이 있으면 AWS 기본 동작에 따라 사용될 수 있습니다.

### 6. 요약, 생성과 단계 기대 결과

1. 요약에서 instance 수 `1`, Ubuntu Server 24.04 LTS ARM64, `t4g.small`, public subnet,
   public IPv4와 `20 GiB gp3`를 확인합니다.
2. current Console에서 instance·EBS·public IPv4 예상 요금을 다시 확인합니다.
3. `인스턴스 시작`을 선택합니다.
4. `EC2 → 인스턴스`에서 instance가 `실행 중`이고 상태 검사를 통과할 때까지 기다립니다.
5. AMI·architecture·type, VPC·subnet·AZ, public·private IPv4, security group, root
   volume과 metadata 설정을 위 표에 대조합니다.
6. 보관한 private key로 `ubuntu` 사용자 SSH 접속을 확인합니다.

public IPv4는 stop 후 start하면 바뀔 수 있으므로 영구 설정값으로 기록하지 않습니다.

## 중단·실패 판정

- Canonical Ubuntu Server 24.04 LTS ARM64 조건이나 `t4g.small`을 찾을 수 없으면 다른
  AMI·architecture·type으로 임의 대체하지 않습니다.
- VPC·subnet·AZ가 입력표와 다르거나 public IPv4가 비활성이라면 launch하지 않습니다.
- SSH source가 현재 운영자 IPv4 `/32`가 아니거나 예상하지 않은 inbound rule이 있으면
  launch하지 않습니다.
- root EBS 암호화·종료 시 삭제, IMDSv2 only 또는 hop limit이 입력과 다르면 launch하지
  않습니다.
- instance가 `실행 중`·상태 검사 통과 상태가 아니거나 SSH가 실패하면 bootstrap을
  시작하지 않습니다.

## 복구·재개

1. SSH 실패 시 `0.0.0.0/0`을 추가하지 말고 현재 운영자 public IPv4 `/32`, instance의
   현재 public IPv4, subnet public route, Internet Gateway와 security group을 차례로
   확인합니다.
2. stop/start 뒤 public IPv4가 바뀌었으면 새 주소로 접속합니다. 운영자 source IP가
   바뀌었으면 SSH rule을 새 `/32`로 갱신합니다.
3. architecture나 immutable launch 설정을 잘못 선택한 instance를 production처럼
   간주하지 않습니다. 현재 절차에는 terminate·재생성 시 data 보존 계약이 없으므로
   root data와 연결 resource를 확인하지 않고 삭제하지 않습니다.
4. 상태 검사와 SSH가 모두 정상인 checkpoint에서
   [ECR·IAM 최초 구축](130-ecr-iam-provisioning-runbook.md) 또는
   [Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md)으로 이동합니다.

## 완료 확인

- [ ] instance 이름·AMI·architecture·type이 입력표와 같습니다.
- [ ] instance가 `실행 중`이고 상태 검사를 통과했습니다.
- [ ] VPC·subnet·AZ, public·private IPv4와 SSH `/32` rule이 맞습니다.
- [ ] root volume과 metadata 설정이 입력표와 같습니다.
- [ ] `ubuntu` 사용자 SSH 접속이 성공합니다.
- [ ] 실제 완료 상태를
      [Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)에
      반영했습니다.

## 공식 기준과 관련 문서

- **선택·영향·대안**: [EC2 Kubernetes node](../project/infrastructure/110-ec2-kubernetes-node.md)
- **상위 절차**: [AWS 최초 구축](100-aws-initial-provisioning-runbook.md)
- **후속 실행 절차**: [Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md)
