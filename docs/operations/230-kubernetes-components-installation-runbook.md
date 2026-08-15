# Kubernetes component 설치 runbook

Containerd가 준비된 host에 Kubernetes official repository와 exact
`kubelet`·`kubeadm`·`kubectl` package를 최초 설치하는 절차입니다. Component 역할,
version과 repository 선택 근거는
[Kubernetes cluster 개념](../project/infrastructure/140-kubernetes-cluster.md)이 소유합니다.

## 사용 시점과 actor

- **시점**: [Containerd 설치](220-containerd-installation-runbook.md)가 완료된 직후입니다.
- **actor·위치**: 운영자가 SSH로 접속한 EC2 terminal에서 `sudo`로 실행합니다.
- **입력**: 인자와 환경변수는 없습니다.

이 단계는 package와 repository만 준비하며 `kubeadm init`, cluster state와 CNI를 만들지
않습니다.

## 실행

```bash
cd /home/ubuntu/car-community-scripts
sudo ./bootstrap/kubernetes/300-install-kubernetes-components.sh
```

Script는 기존 package·repository owner를 확인하고 official signing key와 Kubernetes
v1.36 repository를 구성한 뒤 세 package `1.36.2-2.1`을 설치·hold합니다. 정확한 keyring,
source file, package와 검증 계약은
[Kubernetes bootstrap README](../../scripts/bootstrap/kubernetes/README.md#300-install-kubernetes-componentssh-공개-계약)가
소유합니다. Exit code가 nonzero이면 control plane을 초기화하지 않습니다.

## 완료 확인

Exit code `0`과 다음 상태를 모두 확인해야 완료입니다.

- Kubernetes signing keyring과 v1.36 repository file이 project 목표와 일치합니다.
- `kubelet`, `kubeadm`, `kubectl` package가 모두 `1.36.2-2.1`이며 hold 상태입니다.
- 세 command가 semantic version `v1.36.2`를 보고합니다.
- Kubelet service는 설치됐지만 cluster 초기화 전 상태이며, CNI와 cluster resource는 아직
  생성되지 않았습니다.

## 중단·복구와 재개

| 상황 | 보호 동작과 운영자 조치 | 재개 지점 |
| --- | --- | --- |
| 다른 Kubernetes minor·package version 또는 관리 주체가 다른 repository | 자동 upgrade·downgrade·인수하지 않습니다. 기존 설치 목적과 migration 범위를 확인합니다. | 결정 뒤 `300` |
| Signing key·repository download 또는 dearmor 실패 | 불완전한 final keyring·source를 게시하지 않습니다. Network와 official source를 확인합니다. | 같은 `300` 재실행 |
| Exact package가 repository에 없음 | 최신 candidate로 대체하지 않습니다. Repository와 target version을 대조합니다. | 같은 `300` 재실행 |
| Package 설치·hold·version 검증 실패 | 부분 성공을 완료로 보지 않습니다. 이미 설치된 exact target package는 가능한 즉시 hold한 뒤 nonzero로 끝납니다. 출력된 원인을 해결합니다. | 같은 exact 목표로 `300` 재실행 |
| 동시 실행 lock 실패 | 실행 중 process를 보호하고 lock file을 삭제하지 않습니다. | 기존 process 종료 뒤 `300` |

Package와 repository를 의도적으로 제거하는 완전한 절차는 현재 제공하지 않습니다.
[Kubernetes bootstrap 취소](500-kubernetes-bootstrap-cancellation-runbook.md)의 조사 경계를
먼저 확인하며 package data를 임의 삭제하지 않습니다.

## 다음 단계와 관련 문서

- **다음 실행**: [Control plane 초기화](240-kubernetes-control-plane-initialization-runbook.md)
- **개념·선택 근거**: [Kubernetes cluster](../project/infrastructure/140-kubernetes-cluster.md)
- **구현 source**: [`300-install-kubernetes-components.sh`](../../scripts/bootstrap/kubernetes/300-install-kubernetes-components.sh)
