# EC2 Kubernetes node

이 상세 SOT는 Kubernetes control plane과 worker를 함께 실행하는 staging EC2 한 대의 역할,
현재 선택값, 선택 이유·영향·대안과 재검토 조건을 소유합니다. AWS Console의 실제 생성·확인
절차는 [EC2 node 최초 구축 runbook](../../operations/120-ec2-node-provisioning-runbook.md)이
소유합니다.

## 역할과 제약

`stg-car-community-k8s-node-01` 한 대가 Kubernetes control plane과 일반 workload를 함께
실행합니다. Launch template이나 Auto Scaling Group이 아닌 직접 생성 instance이므로 현재
이름은 고정합니다. 향후 Auto Scaling을 도입하면 launch template tag 전파와 instance
식별 방식을 그때 별도로 결정하며 현재 이름 규칙을 자동 확장한다고 가정하지 않습니다.

이 구성은 비용을 제한한 staging 학습·검증용입니다. Node·AZ 장애가 cluster 전체 장애로
이어지고 public IPv4로 직접 접속하므로 production 고가용성 구성은 아닙니다. ARM64를
선택했기 때문에 node가 실행할 binary와 container image도 `linux/arm64`를 지원해야 합니다.

## 기본 compute 선택

| 옵션 | 현재 선택 | 판정 | 이유와 영향 | 재검토 조건 |
| --- | --- | --- | --- | --- |
| Instance 수 | `1` | 단일 node 범위와 일치 | Control plane과 worker를 한 대에서 실행합니다. | 역할 분리·고가용성 요구 |
| 이름 | `stg-car-community-k8s-node-01` | 현재 직접 생성 node 식별값 | 환경·project·역할·순번을 구분합니다. AWS가 강제하는 유일한 형식은 아닙니다. | 역할·환경·생성 방식 변경 |
| 추가 EC2 tag | 없음 | 현재 입력 유지 | `Name` 외 EC2 tag taxonomy를 확정하지 않았고 VPC tag가 EC2에 상속된다고 가정하지 않습니다. | EC2 비용·owner tag 정책 확정 |
| AMI | Canonical Ubuntu Server `24.04 LTS`, ARM64, Pro 아님 | 안정성 우선 | Ubuntu `26.04`보다 현재 운영 사례와 호환 검증이 축적된 LTS를 사용하고 Pro의 추가 지원은 요구하지 않습니다. | 26.04 전환·Pro support 요구 |
| SSH username | `ubuntu` | Canonical image 계약 | 유사 이름의 Marketplace image가 아니라 확인된 Canonical image를 사용합니다. | Distribution·custom AMI 변경 |
| AMI 속성 | HVM, EBS root, ENA 활성 | 읽기 전용 파생값 | 선택 AMI와 T4g의 boot·network 조건이며 별도 입력이 아닙니다. | Custom AMI 변경 |
| AMI ID·생성일 | Launch 시 서울 region의 최신 조건 일치 image | 변동 파생값 | Release update·region마다 달라 특정 ID를 영구 고정하지 않고 24.04 LTS·ARM64·Canonical 조건을 대조합니다. | AMI pinning 정책 확정 |
| Architecture | `64비트(Arm)` | T4g 필수 | Graviton T4g 비용 이점을 사용하지만 x86 전용 binary·image는 실행할 수 없습니다. | x86-only dependency 발견 |
| Instance type | 최초 `t4g.small` 2 GiB, live memory 4 GiB | 2 GiB는 실측 후 상향 | Argo reconcile·migration이 겹칠 때 2 GiB node에서 심한 memory·I/O pressure가 관찰돼 4 GiB로 늘렸습니다. 변경한 exact instance type은 현재 live source에서 다시 확인하지 않아 추측해 고정하지 않습니다. | OOM·memory pressure·지속 CPU 제한 관찰 또는 exact type inventory |
| Type filter | `모든 세대` 활성 | 검색 표시일 뿐 | 최초 launch에서 `t4g.small`을 직접 찾으며 성능·과금에는 영향을 주지 않습니다. | 탐색 범위를 줄일 때 |
| Key pair | `example-staging-key` | 현재 SSH identity | AWS는 public key, 운영자는 private key를 보유합니다. Private key를 repository에 저장하지 않으며 분실하면 같은 pair로 접속할 수 없습니다. | SSM·다른 접속 방식 채택 |
| 구매 방식 | On-Demand | 단일 control plane에 적합 | Spot 중단으로 cluster 전체가 멈추는 것을 피합니다. 장기 할인은 현재 사용량이 미확정입니다. | 중단 허용 workload 분리·장기 사용량 확정 |
| CPU credit | `Standard` | 비용 상한 우선 | Baseline 초과 credit을 소진하면 CPU가 제한될 수 있지만 Unlimited 잉여 credit 비용을 허용하지 않습니다. | 지속 부하로 cluster가 느려짐 |

Ubuntu release와 CPU architecture는 독립 선택입니다. Ubuntu ARM64 AMI를 쓴다는 사실만으로
application image의 ARM64 호환이 보장되지는 않습니다. Software 요금 일반 안내가 보여도
현재는 Ubuntu Pro가 아니며, EC2 compute·EBS·public IPv4 요금은 별도로 발생합니다.

## AMI·instance 대안

| 대안 | 의미 | 현재 제외 이유 |
| --- | --- | --- |
| `최근 사용` | 과거 AMI를 빠르게 다시 선택합니다. | 과거 값을 무조건 재사용하지 않고 Quick Start에서 조건을 다시 확인합니다. |
| AMI 검색·더 많은 AMI | AWS·Community·Marketplace·custom image를 탐색합니다. | 검증 범위를 넓히지 않고 Canonical image를 사용합니다. |
| Amazon Linux | AWS 관리 Linux입니다. | 이번 설치·runbook을 Ubuntu 기준으로 고정했습니다. |
| macOS | EC2 Mac workload입니다. | Linux Kubernetes node 목적과 다릅니다. |
| Windows | Windows Server workload입니다. | Linux runtime·kubeadm 범위와 다릅니다. |
| RHEL | RHEL subscription·생태계입니다. | 지원·subscription 요구가 없습니다. |
| SUSE Linux | SUSE 생태계·지원 계약입니다. | 해당 요구가 없습니다. |
| Debian | 범용 Linux 대안입니다. | 현재 Ubuntu 24.04 LTS를 확정했습니다. |
| Ubuntu Pro | 확장 보안·compliance 지원을 추가합니다. | Staging node에 추가 기능·지원 계약 요구가 없습니다. |
| x86_64 | x86 package·image 호환성이 넓습니다. | 현재 비용 우선 `t4g`와 함께 쓸 수 없습니다. |
| `t4g.nano` 0.5 GiB | 더 작은 ARM burstable type입니다. | Kubernetes memory가 부족합니다. |
| `t4g.micro` 1 GiB | 저비용 ARM type입니다. | Kubeadm 시작 기준 2 GiB보다 작습니다. |
| `t4g.medium` 4 GiB | 최초 2 GiB 실측 부족 때의 첫 상향 후보입니다. | Live memory는 4 GiB로 늘었지만 변경한 exact type은 확인하지 않아 `t4g.medium`을 현재값으로 추측하지 않습니다. |
| `t4g.large` 8 GiB | 더 큰 memory입니다. | 현재 실측 부하 근거가 없습니다. |
| `t4g.xlarge` 4 vCPU·16 GiB | CPU·memory를 크게 늘립니다. | 현재 범위보다 큽니다. |
| `t4g.2xlarge` 8 vCPU·32 GiB | 더 큰 ARM type입니다. | 현재 범위보다 큽니다. |
| 새 key pair | 새 SSH key material을 만듭니다. | 기존 `example-staging-key`와 private key가 있습니다. |
| Key pair 없이 launch | SSH key 없이 시작합니다. | 현재 SSH 성공이 완료 조건이며 SSM-only를 확정하지 않았습니다. |

화면의 시간당 가격·`현재 세대`·vCPU·memory는 type과 시점·region에서 파생된 값이며 영구
가격 계약이 아닙니다.

## Network와 security boundary

| 옵션 | 현재 선택 | 이유와 영향 |
| --- | --- | --- |
| VPC | `stg-car-community-vpc` | 현재 staging network를 사용합니다. |
| Subnet | `public1-ap-northeast-2a`, `10.0.0.0/20` | NAT 없이 SSH·package·image outbound를 확보합니다. 단일 node라 현재 한 AZ만 소비합니다. |
| Public IPv4 | 자동 할당 활성 | Internet Gateway로 SSH·download를 수행합니다. 별도 요금과 공격 표면을 수용하고 stop/start 때 주소가 바뀔 수 있습니다. |
| Security group | 새 `stg-car-community-k8s-node-sg` | Node ingress를 다른 resource와 분리합니다. |
| 설명 | `stg car-community single-node Kubernetes EC2` | Group 대상·용도를 사람에게 설명합니다. |
| SSH ingress | TCP `22`, 현재 운영자 public IPv4 `/32` | 전체 internet 대신 한 source만 허용합니다. 운영자 IP 변경 시 rule을 갱신합니다. 실제 IP는 문서에 고정하지 않습니다. |
| SSH rule 설명 | 현재 확인값 `Sillim-Home` | 현재 접속 위치를 구분할 label이며 network 동작은 바꾸지 않습니다. 운영 위치·회선이 바뀌면 `/32` source와 설명을 함께 실제 값으로 갱신합니다. |
| HTTP·HTTPS ingress | Node에 직접 없음 | Public `80`·`443`은 LBC가 만든 ALB frontend SG만 받습니다. Node SG에 동일 port를 public으로 열지 않습니다. |
| NodePort ingress | LBC-managed backend SG source의 실제 target port만 | `0.0.0.0/0`에 NodePort range를 열지 않고 controller가 frontend→backend 경계를 수렴시킵니다. |
| Outbound | 새 group 기본 허용 | Ubuntu package·container image download에 사용합니다. Registry·package source가 고정되면 제한 정책을 재검토합니다. |

다른 VPC·subnet이나 새 subnet은 현재 topology를 바꾸므로 선택하지 않습니다. Public IP
비활성에는 NAT·bastion·SSM 같은 대체 접속·outbound가 필요합니다. 기존 security group을
재사용하면 node 전용 경계를 잃습니다. `0.0.0.0/0` SSH, HTTP·HTTPS 또는 필요가 확정되지
않은 custom rule은 공격 표면을 늘리므로 사용하지 않습니다.

Public IPv4는 영구 설정값이 아니며 접속 실패 시 SSH를 전체 internet에 열지 않고 현재
운영자 `/32`, instance public IPv4, subnet route와 security group을 대조합니다.

## Network interface 선택

| 옵션 | 현재 선택 | 이유와 영향 |
| --- | --- | --- |
| Device index | `0` | Primary interface 기본 index입니다. |
| ENI | 새 interface | 기존 ENI 재사용·고정 IP 요구가 없습니다. |
| ENI 설명 | 비움 | 별도 ENI inventory 규칙이 없어 instance·SG 이름으로 식별합니다. |
| Primary private IPv4 | AWS 자동 할당 | Subnet의 사용 가능한 주소를 AWS가 고릅니다. Placeholder를 실제 값으로 입력하지 않습니다. |
| Secondary IPv4·IPv4 prefix | 없음 | 단일 node에 추가 address·prefix가 필요하지 않습니다. |
| IPv6 address·prefix | 없음·비활성 | VPC·subnet에 IPv6 CIDR이 없습니다. |
| Termination 시 ENI 삭제 | `예` | 재부착 계획이 없는 orphan ENI·주소를 남기지 않습니다. |
| Interface type | 기본 | EFA 같은 특수 interface 요구가 없습니다. |
| Network card index | 비활성 기본 | 현재 type의 multi-card 구성이 아닙니다. |
| ENA Express·UDP·queue | 최초 선택에서 지원되지 않는 기본 상태 | 최초 `t4g.small`에서 사용할 수 없었고 현재 tuning 근거도 없습니다. Exact live type을 확정하면 지원 여부도 다시 읽습니다. |
| Idle connection tracking timeout | 비활성 | 연결 수 고갈·별도 timeout 요구가 없습니다. |
| 추가 ENI | 없음 | 별도 VPC·traffic plane·multi-homing 요구가 없습니다. |

기존 ENI, 특정 primary IP, secondary address·prefix, ENI 보존, ENA tuning, connection tracking
timeout 조정과 multi-ENI는 각각 고정 endpoint·network 성능·traffic plane 요구가 생길 때만
도입합니다. Live subnet ID·owner·남은 IP 수는 파생 정보이며 고정 입력이 아닙니다.

## Storage와 filesystem

| 옵션 | 현재 선택 | 판정 | 이유와 영향 | 재검토 조건 |
| --- | --- | --- | --- | --- |
| Volume | Root EBS 1개 | Root-only 시작 | OS·runtime·Kubernetes를 한 volume에 둡니다. | Workload data lifecycle 분리 |
| Device | AMI의 `/dev/sda1` mapping | AMI 파생값 | OS에서는 NVMe 이름으로 보일 수 있으며 임의로 바꾸지 않습니다. | Custom block mapping |
| Snapshot | 선택 AMI root snapshot | 변동 파생값 | AMI release마다 달라 ID를 고정하지 않습니다. | Custom AMI·snapshot pinning |
| 용량 | `20 GiB` | 현재 시작 용량 | 30 GiB가 Kubernetes 요구는 아닙니다. 사용량을 관찰하며 EBS는 확장 가능하지만 직접 축소하지 못합니다. | Disk pressure·image·log 증가 |
| Type | `gp3` | 범용 SSD | 작은 root volume에 용량과 독립된 baseline 성능을 제공합니다. | 다른 latency·throughput 요구 |
| IOPS | `3000` | Gp3 baseline | 추가 IOPS를 구매하지 않습니다. | 실측 IOPS 병목 |
| Throughput | `125 MiB/s` | Gp3 baseline | 추가 throughput을 구매하지 않습니다. | 실측 sequential 병목 |
| Encryption | 활성, `aws/ebs` | 기본 보안 | 별도 customer key lifecycle 없이 at-rest encryption을 사용합니다. | Customer managed key 정책 |
| Termination 시 삭제 | `예` | 비용 우선 | Terminate 뒤 root 비용을 남기지 않습니다. Stop은 삭제하지 않으며 보존 data는 별도 backup이 필요합니다. | Root data 보존 요구 |
| Volume 초기화 속도 | 지정하지 않음 | 추가 성능 미구매 | Launch 직후 일정한 snapshot read SLA가 없습니다. | 초기 disk 성능 SLA |
| 추가 EBS | 없음 | Persistence 미분리 | Data·mount·backup 요구가 미확정입니다. | Data lifecycle 분리 |
| 추가 filesystem | 없음 | Managed shared filesystem 불필요 | Ubuntu root filesystem만 사용합니다. | Shared·managed filesystem 요구 |

30 GiB 이상, 다른 EBS type, baseline 상향, customer KMS, root 보존, 초기화 속도 지정과 추가
volume은 실제 용량·성능·lifecycle 요구가 생기면 선택합니다. 암호화 비활성은 현재 기본 보안보다
나은 이유가 없습니다. S3 mount는 VPC S3 Gateway Endpoint와 다른 기능이고, EFS는 다중
Linux shared NFS 요구, FSx는 특정 workload filesystem 요구가 없어 제외합니다. Data
Lifecycle Manager 정보 영역은 현재 backup policy 존재를 증명하지 않습니다. T4g.small에는
최초 `t4g.small`에 instance store가 추가됐다고 간주하지 않습니다.

## Advanced instance 선택

| 옵션 | 현재 선택 | 이유와 영향 |
| --- | --- | --- |
| Domain join | 없음 | Active Directory identity·policy 요구가 없습니다. |
| IAM instance profile | `stg-car-community-k8s-node-role` | Exact ECR pull policy와 LBC policy를 분리해 연결하고, node가 private image pull과 ALB lifecycle에 같은 EC2 role credential을 사용합니다. 기존 ECR-only profile의 정리 여부와 live 검증 상태는 operations scenario가 소유합니다. |
| Hostname | Private IP 기반 `IP 이름` | AWS 기본 hostname을 사용하며 Kubernetes node 이름은 bootstrap에서 확인합니다. |
| DNS record | IP 이름 IPv4 `A` 활성, resource IPv4·IPv6 비활성 | IPv4-only 기본 DNS를 유지합니다. |
| Auto recovery | Console 기본값 | 지원 상태에 따른 simplified recovery를 사용합니다. |
| OS shutdown | `중지` | OS shutdown으로 instance·root EBS가 terminate되지 않게 합니다. |
| Hibernate | 비활성 | Memory state 보존보다 재현 가능한 재시작을 우선합니다. |
| Termination protection | 비활성 기본 | 실수 terminate 위험을 수용하며 장기 node 승격 시 재검토합니다. |
| Stop protection | 비활성 | 미사용 시 stop해 compute 비용을 줄일 수 있습니다. |
| Detailed monitoring | 비활성 | 기본 5분 metric을 사용하고 1분 metric 추가 비용을 쓰지 않습니다. |
| CPU credit | `Standard` | Unlimited 추가 비용을 막습니다. |
| Placement group | 없음 | Instance 한 대에 multi-instance placement 정책이 필요하지 않습니다. |
| EBS optimized | 활성 | Type의 기본 최적화 경로를 유지합니다. |
| Bandwidth weighting | 없음 | Network·EBS 실측 병목이 없습니다. |
| Purchase option | 없음, On-Demand | Capacity block·interruptible·Spot을 사용하지 않습니다. |
| Capacity Reservation | Console 기본 matching 동작 | 특정 reservation·group을 고정하지 않습니다. 조건이 맞는 open reservation 사용 가능성은 기본 동작에 따릅니다. |
| Tenancy | VPC 기본 shared | Dedicated 비용·제약을 사용하지 않습니다. |
| RAM disk·kernel ID | 없음 | HVM Ubuntu AMI boot 구성을 사용합니다. |
| Nitro Enclave | 비활성 | Enclave·attestation workload가 없습니다. |
| License config | 없음 | License Manager로 추적할 상용 license가 없습니다. |
| CPU options | 기본 2 vCPU | 이미 최소 시작점이라 줄이지 않습니다. |
| Nested virtualization | 미지원 기본 | Kubernetes에 필요하지 않습니다. |
| Metadata endpoint | 활성 | Instance metadata 사용 가능성을 유지합니다. |
| Metadata IPv6 | 비활성 | IPv6를 사용하지 않습니다. |
| Metadata version | IMDSv2 only | Token 없는 IMDSv1을 차단합니다. |
| Metadata hop limit | `2` | LBC Pod가 node role credential을 쓰는 현재 self-managed staging에서 IMDSv2 응답을 유지합니다. 다른 Pod의 node credential 노출 가능성도 넓혀지는 trade-off이므로 multi-tenant·production은 Pod Identity/IRSA를 재검토합니다. |
| Metadata tag access | 비활성 | Workload에 EC2 tag를 노출하지 않습니다. |
| User data | 없음, base64 flag 비활성 | 검토하지 않은 bootstrap을 boot 때 실행하지 않고 후속 script 과정을 관찰합니다. |

## Advanced 대안과 재검토

- Directory join, resource-based hostname·A/AAAA record, hibernation, termination·stop protection,
  detailed monitoring, placement group, bandwidth weighting, Nitro Enclave, License Manager와 CPU
  override는 해당 identity·복구·관측·성능·보안 요구가 생기면 각각 검토합니다.
- Shutdown `terminate`는 OS 명령 한 번으로 node와 root volume을 삭제할 수 있어 제외합니다.
- CPU `Unlimited`는 sustained burst에 유리하지만 추가 credit 비용을 허용하지 않아 제외합니다.
- Capacity Block, interruptible reservation과 Spot은 현재 단일 control plane의 중단을 수용하지
  않아 제외합니다. 특정 reservation은 capacity 계약을 먼저 확정해야 합니다.
- Dedicated tenancy·host는 규제·license 요구가 없습니다.
- Metadata endpoint 비활성은 AWS integration 가능성까지 막고, IMDSv1 허용은 SSRF 방어를
  약화시킵니다. Hop limit `1`은 더 제한적이지만 container network에서 IMDS가 필요한지에
  따라 재검토합니다. Node pull provider는 host process이므로 hop limit을 임의 변경하지
  않습니다.
- User data·file upload는 bootstrap을 자동화할 수 있지만 현재 직접 과정을 관찰·문서화하는
  목표와 다르며, 이미 base64라는 flag도 payload가 없으므로 사용하지 않습니다.

## 변경 시 동반 확인

- ARM64에서 x86으로 바꾸면 AMI·instance type뿐 아니라 image publish architecture와 node pull
  smoke를 함께 변경합니다.
- Public IP를 제거하면 SSH와 package·registry outbound의 대체 경로를 먼저 마련합니다.
- EC2 role은 current node platform이 소비하는 exact ECR pull과 LBC policy만 가집니다.
  Application의 일반 AWS API 권한을 추가하지 않으며 Pod identity는 별도
  permission boundary입니다.
- Auto Scaling·다중 node로 바꾸기 전에 cluster stable endpoint, node join·replacement,
  Calico AWS routing과 name/tag 정책을 먼저 결정합니다.
- Root volume은 축소가 어렵고 termination 시 삭제되므로 persistent application data를 두지
  않습니다.

다중 node·Node autoscaling·workload identity 후보의 상태와 선행 조건은
[004 Infrastructure evolution backlog](../../work-items/backlog/2026/004-infrastructure-evolution.md)가
소유합니다.

## 근거와 관련 문서

- **실제 최초 구축 절차**: [EC2 node 최초 구축 runbook](../../operations/120-ec2-node-provisioning-runbook.md)
- **Network 기반**: [110 VPC network](110-vpc-network.md)
- **Node가 될 Kubernetes 상태**: [150 Kubernetes cluster](150-kubernetes-cluster.md)
- **ECR pull role**: [130 ECR·IAM 기반](130-ecr-iam-foundation.md)
- **ALB·LBC·node security/IAM 경계**:
  [170 AWS Load Balancer ingress](170-aws-load-balancer-ingress.md)
- **Profile 전환·IMDS·NodePort live 절차**:
  [370 Staging 외부 HTTPS ingress 최초 구축 runbook](../../operations/370-staging-external-https-ingress-bootstrap-runbook.md)
- **EC2·Linux 진단**:
  [110 AWS EC2·접근 진단](../../operations/diagnostics/110-aws-ec2-and-access.md),
  [120 Linux resource 진단](../../operations/diagnostics/120-linux-cpu-memory-and-process.md),
  [130 Filesystem·storage 진단](../../operations/diagnostics/130-filesystem-and-storage.md),
  [140 Linux node service 진단](../../operations/diagnostics/140-systemd-journal-and-node-services.md),
  [150 Linux network 진단](../../operations/diagnostics/150-linux-network.md)
- [AWS T4g](https://aws.amazon.com/ec2/instance-types/t4/)
- [EC2 IP addressing](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/using-instance-addressing.html)
- [Security group rule](https://docs.aws.amazon.com/vpc/latest/userguide/security-group-rules.html)
- [ENA Express](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ena-express.html)
- [Connection tracking timeout](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/security-group-connection-tracking.html)
- [EBS gp3](https://docs.aws.amazon.com/ebs/latest/userguide/general-purpose.html)
- [EBS volume 수정](https://docs.aws.amazon.com/ebs/latest/userguide/ebs-modify-volume.html)
- [EC2에서 EFS 사용](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/AmazonEFS.html)
- [T instance CPU credit](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances-unlimited-mode.html)
- [EC2 detailed monitoring](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/manage-detailed-monitoring.html)
- [Capacity Reservation](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/capacity-reservations-launch.html)
- [Capacity Block](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/capacity-blocks-launch.html)
- [IMDSv2와 hop limit](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/configuring-IMDS-new-instances.html)
