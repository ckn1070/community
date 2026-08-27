# Platform 진단 목차

이 그룹은 AWS EC2 host → Linux node → Kubernetes → Argo CD로 이어지는 **Kubernetes 운영
stack**의 현재 상태를 읽기 전용으로
수집하고, 출력의 의미를 이해해 fault domain을 좁히는 절차와 설명을 소유합니다. 평소
baseline 확인과 장애 발생 뒤 조사는 같은 관찰 도구를 사용하므로 별도 폴더로 나누지
않습니다. 관찰 결과와 조사 깊이만 달라집니다.

VPC·RDS의 AWS resource 구성, GitHub CI·ECR image 게시와 application 기능 자체는 이
outline의 전체 coverage가 아닙니다. Runtime 증거가 그 경계를 가리키면 해당 AWS·delivery·
application runbook으로 이동합니다.

## 어디서 시작할지 고르기

| 질문 | 시작 문서 |
| --- | --- |
| 주요 fault domain을 빠르게 확인하기 | [100 Kubernetes platform 핵심 진단 outline](100-kubernetes-platform-diagnostic-outline.md) |
| SSH 전 AWS Console의 instance 상태·metric·network 경계 확인 | [110 AWS EC2·접근 진단](110-aws-ec2-and-access.md) |
| CPU·memory·load·swap과 process 출력을 해석 | [120 Linux resource 진단](120-linux-cpu-memory-and-process.md) |
| Block·inode·mount·I/O 상태 해석 | [130 Filesystem·storage 진단](130-filesystem-and-storage.md) |
| systemd·journal과 containerd·kubelet 해석 | [140 Linux node service 진단](140-systemd-journal-and-node-services.md) |
| Interface·route·socket·DNS·egress 해석 | [150 Linux network 진단](150-linux-network.md) |
| API readiness·control plane·etcd·Node condition 해석 | [160 Kubernetes control plane 진단](160-kubernetes-api-control-plane-and-node.md) |
| Pod·CNI·CoreDNS·Service·EndpointSlice·event·log 해석 | [170 Kubernetes workload 진단](170-kubernetes-workload-network-events-and-logs.md) |
| Argo CD component·repository·sync·health·reconcile 해석 | [180 Argo CD reconciliation 진단](180-argocd-reconciliation.md) |

문서는 세 깊이로 읽습니다.

1. `100`은 각 영역의 핵심 probe 1~3개만 실행해 Kubernetes 운영 stack에서 첫 이상 경계를 찾습니다.
2. `110`~`180`의 상단 수동 확장 조회는 한 영역의 관련 probe를 더 넓게 확인합니다.
3. 같은 상세 문서의 하단은 명령 문법, option·출력 필드, 정상·이상 신호, 증거 조합,
   추가 조회와 한계를 설명합니다.

따라서 outline은 상세 문서의 명령을 전부 복제하지 않고, 상세 문서도 상단 묶음과 하단
설명을 단순 반복하지 않습니다. 실제 상태 변경·재실행·복구는 연결된 기존 runbook이
소유합니다.

## 상세 문서 공통 구조

모든 상세 문서는 다음 순서를 사용합니다. 같은 레벨의 command·option은 같은 heading과 표
형식으로 설명하고, 서로 다른 정보 레벨일 때만 표현 형식을 바꿉니다. `110`은 AWS Console
항목별 확인 목적·첫 이상 신호를 표로 제시하고, `120`~`180`은 아래 Bash probe 계약을
적용합니다.

1. **상단 수동 확장 조회**: `110`은 Console 확인표, `120`~`180`은 각 probe가 확인하는 목적을 출력하고 exit code를 보존하면서 이 영역의 read-only command를 한 번에 실행하는 묶음
2. **해석 순서**: 출력을 어떤 순서와 관계로 볼지 요약
3. **명령어 family**: `command: 한 줄 핵심` 제목 아래 option·subcommand를 같은 표로 설명
4. **출력 필드와 신호**: 각 column·state의 뜻과 정상·이상 관계
5. **증거 조합과 다음 분기**: 단일 값이 아니라 함께 나타난 증거로 다음 owner 선택
6. **금지 경계·관련 문서**: 진단과 mutation을 분리하고 복구 절차로 연결

## 공통 안전 경계

- 진단은 `apply`, `delete`, `patch`, `annotate`, `rollout restart`, service `restart`,
  `reboot`, `kill`, `kubeadm reset`을 실행하지 않습니다.
- Secret `.data`, process environment, PEM과 password를 직접 조회하지 않습니다.
- 자유 형식 log·event·condition에는 producer가 기록한 token 조각이나 사용자 입력이 포함될
  수 있습니다. 출력 파일은 local mode `0600`으로만 보존하고, 외부 공유 전 token, public
  IP, hostname, email, repository URL과 사용자 입력을 필요한 범위만 남기고 가립니다.
- 명령 실패는 그 자체로 증거입니다. 실패를 정상으로 바꾸지 않고 stderr와 exit code를
  남긴 뒤 가능한 다음 조회를 계속합니다.
- 조회 출력 보존은 상태 수집 완료일 뿐 정상·복구 완료가 아닙니다.

## 공통 실행 계약

`120`~`180`의 명령은 target EC2에 SSH로 접속한 운영자가 **Bash**에서 실행합니다. 현재
directory는 무관하고 상단 수동 확장 조회에는 별도 입력·Secret이 없습니다. Kubernetes
조회는 문서에 적힌 `KUBECONFIG=/etc/kubernetes/admin.conf`를 그대로 사용합니다. Pod
`describe`·`logs` 같은 상세 조회의 `namespace`·`pod_name`은 앞선 목록 출력에서 고릅니다.
`110`은 SSH 전 AWS Console 조회이므로 이 host·shell 계약의 예외입니다.

`120`~`180` 상세 문서의 상단 code block은 해당 영역의 확장 probe를 한 번에 실행하는 Bash
묶음입니다. 각 probe는 실행 전에 구분자·이름·확인 목적을 출력하고, 실행 뒤에는 command
또는 pipeline producer의 exit code를 출력합니다. Block에는 `set -e`와 `&&`를 사용하지
않으므로 한 명령이 실패해도 다음 독립 조회가 계속되고, 운영자는 나타난 stdout·stderr를
함께 기록합니다. 실패를 정상으로
바꾸거나 곧바로 mutation하지 않고 남은 조회로 fault domain을 좁힙니다. 완료는 필요한
probe를 실행해 첫 이상 영역과 다음 owner를 고른 상태이며 platform 정상이나 복구 완료를
뜻하지 않습니다. 원인을 해결한 뒤에는 전체 block 또는 실패했던 단일 명령부터
재실행합니다.

상단의 probe 설명은 빠른 실행·첫 판독만 소유합니다. Command 문법, option·출력 field,
정상·이상 신호와 다른 증거를 결합하는 방법은 같은 상세 문서의 하단 command family
section에서 이어서 확인합니다.

`sed -n '1,Np'`와 `tail -n N`은 producer 결과의 출력 행 수를 제한하는 filter입니다.
Pipeline에서는 filter의 exit code만으로 producer 성공을 판단하지 않습니다. 상단 묶음이
출력하는 Bash `PIPESTATUS`의 producer exit code로 실패 여부를 판정합니다. 잘리지 않은
producer 출력이 추가로 필요할 때만 filter를 제거해 같은 producer를 단독 실행합니다.

## Node 수와 실행 범위

Host·process·filesystem·systemd·Linux network section은 조사할 node마다 반복합니다.
Kubernetes API·workload·Argo CD section은 cluster마다 한 번 실행하고, 이상 node가 여러
개이면 정상 node와 문제 node의 host 출력을 비교합니다. Node 수는 진단 원리나 문서
identity를 바꾸지 않습니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`180` | Kubernetes platform 핵심 진단과 분야별 심화 해석 |

## 관련 문서

- **변경·복구 원칙**: [300 Infrastructure 변경과 복구 원칙](../../project/infrastructure/300-change-and-recovery-principles.md)
- **현재 staging 사건과 재개점**: [100 Staging 단일 node 전체 구축 시나리오](../scenarios/100-staging-single-node-bootstrap.md)
- **전체 운영 절차**: [000 운영 문서 목차](../000-index.md)
