# Containerd 설치 runbook

준비된 Ubuntu host에 Kubernetes가 사용할 containerd CRI runtime을 최초 설치·구성하는
절차입니다. Runtime, package, cgroup과 kernel module 선택 근거는
[150 Kubernetes cluster 개념](../project/infrastructure/150-kubernetes-cluster.md)이 소유합니다.

## 사용 시점과 actor

- **시점**: [Host 준비](210-kubernetes-host-preparation-runbook.md)가 재부팅 필요 없이
  완료된 직후입니다.
- **actor**: Target EC2에서 `sudo`를 사용할 수 있는 운영자
- **기본 실행 위치**: SSH로 접속한 target EC2 terminal
- **기본 시작 directory**: 제한 없음
- **입력**: 인자와 환경변수는 없습니다.

이 단계는 Kubernetes package, cluster state, CNI, UFW와 AWS Security Group을 변경하지
않습니다.

## 실행

```bash
sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/200-install-containerd.sh
```

Script는 충돌 runtime과 기존 config owner를 먼저 확인한 뒤 exact Ubuntu package,
`overlay` module, systemd cgroup 기반 CRI config, service와 socket을 목표 상태로 만듭니다.
정확한 파일·package·postcondition은
[Kubernetes bootstrap README](../../scripts/bootstrap/kubernetes/README.md#200-install-containerdsh-공개-계약)가
소유합니다. Exit code가 nonzero이면 다음 단계로 진행하지 않습니다.

## 완료 확인

Exit code `0`과 다음 상태를 모두 확인해야 완료입니다.

- Containerd package가 `2.2.1-0ubuntu1~24.04.3`이고 hold 상태입니다.
- `overlay` module이 현재 load되어 있고 재부팅 후 load file의 유일한 값도 `overlay`입니다.
- Project 관리 config version은 `3`이고 runc의 `SystemdCgroup = true`가 적용됐습니다.
- Containerd service가 `enabled`·`active`이고 socket이 존재합니다.
- CRI images·runtime plugin 상태가 모두 `ok`입니다.

## 중단·복구와 재개

| 상황 | 보호 동작과 운영자 조치 | 재개 지점 |
| --- | --- | --- |
| Host 준비 뒤 재부팅 미완료 | Package 설치 전에 중단합니다. 재부팅·재접속 후 host 준비를 다시 확인합니다. | `100`, 그 뒤 `200` |
| Docker Engine·CRI-O·`containerd.io` 또는 다른 containerd version | 자동 제거하지 않습니다. 설치 주체와 필요한 runtime을 확인해 충돌을 해소합니다. | 같은 `200` 재실행 |
| 관리 표식 없는 기존 config | 삭제·rename·덮어쓰지 않습니다. 보존할 option과 owner를 확인해 별도 migration을 결정합니다. | Migration 뒤 `200` |
| Exact package·module·config·service·CRI 검증 실패 | 다른 version으로 대체하거나 자동 rollback하지 않습니다. 출력된 원인을 해결합니다. | 같은 `200` 재실행 |
| 동시 실행 lock 실패 | 실행 중 process를 보호합니다. Lock file을 직접 삭제하지 않습니다. | 기존 process 종료 뒤 `200` |

APT index 갱신, 이미 충족된 package 요청과 service restart는 재실행될 수 있으며 목표 상태를
훼손하지 않습니다. 구성을 의도적으로 취소할 때만
[Kubernetes bootstrap 취소](500-kubernetes-bootstrap-cancellation-runbook.md)를 사용합니다.
`overlay` module과 `/var/lib/containerd` data는 자동 제거하지 않습니다.

## 다음 단계와 관련 문서

- **다음 실행**: [Kubernetes component 설치](230-kubernetes-components-installation-runbook.md)
- **개념·선택 근거**: [150 Kubernetes cluster](../project/infrastructure/150-kubernetes-cluster.md)
- **구현 source**: [`200-install-containerd.sh`](../../scripts/bootstrap/kubernetes/200-install-containerd.sh)
