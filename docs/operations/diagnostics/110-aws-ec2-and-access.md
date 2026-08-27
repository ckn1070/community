# AWS EC2·접근 진단: instance·metric·ENI·Security Group·route

SSH로 guest OS를 볼 수 없을 때 AWS control plane에서 먼저 확인할 경계를 설명합니다.
Instance health, resource metric과 packet이 EC2까지 도달하는 경로를 구분합니다.

**기본 실행 위치:** 장애 대상 AWS account·region의 AWS Console

## 상단 빠른 확장 조회 (bounded preset)

다음 항목을 **같은 장애 시각** 기준으로 AWS Console에서 기록합니다. 설정은 바꾸지 않습니다.

| 순서 | Console 위치 | 확인 목적 | 먼저 볼 이상 신호 | 기록할 값 |
| --- | --- | --- | --- | --- |
| 1 | EC2 Instance summary | Instance가 실행 중이고 조사 대상이 맞는지 확인 | `stopped`·다른 AZ·변경된 public IPv4 | Instance ID·state·type·AZ·public/private IPv4 |
| 2 | Status checks | AWS host 문제와 guest 응답 문제를 분리 | System 또는 Instance check 실패·Scheduled event | System check·Instance check·Scheduled event |
| 3 | Monitoring | 장애 시각의 CPU·network·status check 추세 확인 | CPU credit 고갈·status check 실패·traffic 급변 | CPUUtilization·CPUCreditBalance·StatusCheckFailed·NetworkIn/Out |
| 4 | EBS volume·metric | Volume 연결과 I/O 지연 여부 확인 | Attachment 불완전·queue와 latency의 지속 증가 | Attachment state·VolumeQueueLength·VolumeAvgReadLatency·VolumeAvgWriteLatency |
| 5 | Network interface | Packet이 도달할 실제 ENI·subnet·주소 확인 | 다른 subnet·public IPv4 누락·예상하지 않은 Security Group | ENI·subnet·public IPv4·attached Security Group |
| 6 | Security Group | 현재 운영자 주소에서 SSH가 허용되는지 확인 | TCP 22 규칙 누락·source `/32` 불일치 | 운영자 public IPv4 `/32` → TCP 22 ingress |
| 7 | Route table | Public subnet의 인터넷 경로 확인 | Default route 누락·Internet Gateway가 아닌 target | Public subnet의 `0.0.0.0/0` → Internet Gateway |

## 해석 순서

```text
Instance lifecycle·AWS host health
→ guest 응답·resource metric
→ ENI·public IP·subnet
→ Security Group·route
→ Linux interface·SSH listener
```

AWS 경계가 정상이면 [150 Linux network 진단](150-linux-network.md)으로 내려갑니다.

## 기본 조회 경로와 선택 조건

AWS Console은 shell command 대신 `EC2 → Instances → <대상 instance>`를 기본 조회 경로로
사용합니다. Instance ID·region·장애 시각을 먼저 고정하고, 아래 조건은 질문에 따라
추가합니다.

| 선택 조건 | 붙이는 위치 | 조합 기준 |
| --- | --- | --- |
| 장애 전·중·후 time range | Monitoring graph | 한 시점이 아니라 같은 metric의 추세 비교 |
| 가능한 가장 작은 period | CloudWatch metric | Detailed monitoring 여부를 확인하고 짧은 장애가 큰 period 평균에 묻히지 않게 함 |
| `Average` | CPU·network·latency | 구간 평균 추세 확인 |
| `Maximum` | Status check·짧은 spike | 평균이 숨기는 순간 실패 확인 |
| Volume ID·ENI ID | EBS·Network interface 상세 | Instance summary에서 고른 실제 attachment만 조회 |

상단 표의 metric 묶음은 빠른 preset입니다. 기간·period·statistic을 고정값으로 보지 말고
사건 시각과 평소 baseline에 맞게 바꿉니다.

## EC2 status check: AWS host와 guest 응답 구분

| 항목 | 핵심 의미 | 정상 신호 | 이상일 때 첫 질문 |
| --- | --- | --- | --- |
| Instance state | EC2 lifecycle | `running` | 누가 stop·terminate를 요청했는가 |
| System status check | AWS 물리 host·전원·network | Passed | AWS event·host recovery가 필요한가 |
| Instance status check | Guest network·응답 | Passed | Kernel hang·OOM·filesystem·network인가 |
| Scheduled events | Maintenance·retirement 예정 | 없음 | 사건 시각과 event 시각이 겹치는가 |

`2/2 checks passed`는 Kubernetes·application 정상 판정이 아닙니다. AWS의 두 health probe를
통과했다는 뜻이며 service·port·API는 guest와 cluster에서 별도로 봅니다.

## CloudWatch metric: 시계열 resource 신호

| Metric | 무엇을 보여주는가 | 함께 볼 값 | 해석 한계 |
| --- | --- | --- | --- |
| `CPUUtilization` | vCPU 사용 비율 | Load·process CPU | 낮아도 memory·I/O 문제는 남음 |
| `CPUCreditBalance` | T family burst credit 잔량 | Instance type·CPUUtilization | 0 근처면 baseline 성능 제한 가능 |
| `StatusCheckFailed_System` | AWS host check 실패 | System status check | Guest command로 고칠 영역 아님 |
| `StatusCheckFailed_Instance` | Guest 응답 check 실패 | OOM·kernel·filesystem | 원인 자체는 알려주지 않음 |
| `NetworkIn/Out` | 송수신 byte 추세 | Linux error/drop·listener | Packet loss·port 상태는 알 수 없음 |
| `VolumeQueueLength` | 완료를 기다리는 read·write request 수 | Guest `df`·kernel I/O log | 순간 값보다 장애 전·중·후 추세를 비교 |
| `VolumeAvgReadLatency`·`VolumeAvgWriteLatency` | 1분 내 완료된 read·write의 평균 지연 | `VolumeReadOps`·`VolumeWriteOps` | Operation이 없는 구간의 `0`은 빠른 I/O를 뜻하지 않음 |

Graph period가 크면 짧은 장애가 평균에 묻힙니다. Outline log의 `date`와 timezone을 맞추고
장애 전·중·후를 비교합니다.

### EBS attachment·gp3 metric 해석

| 항목 | 정상 신호 | 이상 신호·다음 증거 |
| --- | --- | --- |
| Attachment state | 대상 instance에 `attached` | `attaching`·`detaching`이 지속되거나 다른 instance에 attachment |
| `VolumeQueueLength` | 평소 baseline 범위 | 부하 증가와 함께 지속 증가하면 latency·guest I/O log와 비교 |
| Read·write average latency | 평소 baseline 범위 | Operation이 있는 구간에 지속 증가하면 filesystem·kernel log로 내려감 |

현재 root volume은 `gp3`이므로 `BurstBalance`를 진단 지표로 사용하지 않습니다.
`BurstBalance`는 I/O·throughput credit bucket을 사용하는 `gp2`, `st1`, `sc1`에만
제공됩니다. EBS metric은 운영체제 내부의 filesystem 용량을 보여주지 않으므로
[130 Filesystem·storage 진단](130-filesystem-and-storage.md)의 `df`·kernel log와 같이 판독합니다.

## ENI·Security Group·route: EC2까지의 packet 경로

```text
운영자 public IP
→ Internet Gateway·public route
→ EC2 public IPv4·ENI
→ Security Group TCP 22
→ Linux interface·SSH listener
```

| 대상 | 확인할 관계 | 잘못된 해석 |
| --- | --- | --- |
| Public IPv4 | 현재 SSH destination과 같은가 | 예전 stop/start 주소를 계속 사용 |
| ENI·subnet | 의도한 VPC public subnet인가 | VPC 이름만 보고 실제 ENI를 생략 |
| Security Group | 현재 운영자 IP `/32`가 TCP 22 source인가 | 진단을 위해 `0.0.0.0/0`로 확대 |
| Route table | `0.0.0.0/0`이 Internet Gateway로 가는가 | Route 존재를 sshd 정상으로 해석 |

Security Group은 stateful이므로 허용된 inbound connection의 response를 위한 별도 ephemeral
inbound rule은 필요하지 않습니다. Network ACL을 별도로 제한했다면 stateless return path도
확인합니다.

## 증거 조합과 다음 분기

| 함께 보이는 증거 | 다음 분기 |
| --- | --- |
| System check 실패 | AWS event·host recovery option 확인 |
| Instance check 실패 + CPU/credit 이상 | [120 Linux resource 진단](120-linux-cpu-memory-and-process.md) |
| 2/2 정상 + TCP 22 timeout | ENI·SG·route 확인 뒤 [150 Linux network 진단](150-linux-network.md) |
| EBS latency·queue 증가 | [130 Filesystem·storage 진단](130-filesystem-and-storage.md)과 결합 |
| SSH와 API가 동시에 불안정 | Host resource → service → API 순서로 확인 |

## 금지 경계·관련 문서

원인을 모르는 상태에서 stop/start, reboot, Security Group 확대, ENI 교체, volume detach를
하지 않습니다.

- **전체 outline**: [100 Kubernetes platform 핵심 진단 outline](100-kubernetes-platform-diagnostic-outline.md)
- **Guest network**: [150 Linux network 진단](150-linux-network.md)
- **현재 EC2 구성**: [120 EC2 Kubernetes node](../../project/infrastructure/120-ec2-kubernetes-node.md)
- **현재 구성과 최초 postcondition 재확인**: [120 EC2 node 최초 구축](../120-ec2-node-provisioning-runbook.md)

`120`은 현재 EC2 구성과 최초 postcondition을 확인하는 절차이며 일반 host 장애 복구를
자동 수행하지 않습니다. Stop/start·reboot·instance 교체가 필요해 보이면 관찰 증거와
영향을 보존한 뒤 별도 상태 변경으로 결정합니다.

## 공식 참고

- [EC2 status checks](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/monitoring-system-instance-status-check.html)
- [EC2 burstable performance instances](https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/burstable-performance-instances.html)
- [Amazon EBS CloudWatch metrics](https://docs.aws.amazon.com/ebs/latest/userguide/using_cloudwatch_ebs.html)
- [Security group rules](https://docs.aws.amazon.com/vpc/latest/userguide/security-group-rules.html)
