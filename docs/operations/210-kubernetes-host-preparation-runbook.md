# Kubernetes host 준비 runbook

Ubuntu 24.04 EC2에서 host package, swap과 IPv4 forwarding을 Kubernetes가 사용할 수
있는 상태로 만드는 최초 구축 절차입니다. 구성의 작동 원리와 선택 근거는
[Kubernetes cluster 개념](../project/infrastructure/140-kubernetes-cluster.md)이 소유합니다.

## 사용 시점과 actor

- **시점**: [Bootstrap archive 전달](200-kubernetes-bootstrap-runbook.md#ec2-실행-파일-전달)을
  완료한 직후 한 번 실행하고, 재부팅이 필요하면 같은 절차로 재검증합니다.
- **actor·위치**: 운영자가 SSH로 접속한 EC2 terminal에서 `sudo`로 실행합니다.
- **시작 조건**: Ubuntu Server 24.04 ARM64이며 `/home/ubuntu/car-community-scripts`의
  전달된 script를 실행할 수 있습니다.

이 단계는 containerd, Kubernetes component와 CNI를 설치하지 않습니다.

## 실행

```bash
cd /home/ubuntu/car-community-scripts
sudo ./bootstrap/kubernetes/100-prepare-host.sh
```

Script는 변경 전에 OS·권한·필수 command, 시간 동기화, UFW와 systemd swap unit을
확인합니다. 통과하면 APT package를 갱신하고 현재·재부팅 후 swap을 비활성화하며 project
소유 sysctl file로 `net.ipv4.ip_forward = 1`을 현재 kernel과 영구 설정에 적용합니다.
내부 파일·backup·검증 계약은
[Kubernetes bootstrap README](../../scripts/bootstrap/kubernetes/README.md#100-prepare-hostsh-공개-계약)가
소유합니다.

Systemd swap 확인은 변경 전 preflight와 변경 후 postcondition에서 전체 unit file
inventory 조회가 성공한 뒤 이름이 `.swap`으로 끝나는 unit만 필터링합니다. Swap unit이
하나도 없는 빈 결과는 정상입니다. Preflight 조회 실패는 변경 전에, postcondition 조회
실패는 앞선 변경을 유지한 채 nonzero로 중단하며 두 경우 모두 `systemctl` 오류 출력을
유지합니다.

Exit code가 nonzero이면 다음 단계로 진행하지 않습니다. Script는
`/var/run/reboot-required` 존재를 재부팅 checkpoint로 사용합니다. 성공 출력에 재부팅이
필요하다고 표시되면 EC2를 재부팅·재접속한 뒤 같은 명령을 다시 실행하고 marker가 없어진
상태의 postcondition을 재검증합니다.

## 완료 확인

재부팅 필요가 없다고 표시된 exit code `0`과 다음 상태를 모두 확인해야 완료입니다.

- 현재 swap과 `/etc/fstab`의 활성 swap entry가 없고 enabled systemd swap unit도 없습니다.
- Project sysctl file과 현재 kernel의 IPv4 forwarding 값이 `1`입니다.
- System clock이 동기화되어 있고 UFW가 없거나 `inactive`입니다.

첫 실행이 exit code `0`이어도 재부팅 필요가 표시되면 아직 완료가 아닙니다. Local test와
repository 상태도 실제 EC2 완료 증거를 대신하지 않습니다.

## 중단·복구와 재개

| 상황 | 보호 동작과 운영자 조치 | 재개 지점 |
| --- | --- | --- |
| OS·권한·필수 command·시간·UFW 불일치 | Host 변경 전에 중단합니다. 출력된 조건을 해당 owner에서 바로잡습니다. | 같은 `100` 재실행 |
| Systemd unit file inventory 조회 실패 | Preflight이면 host 변경 전에 중단합니다. Postcondition이면 앞서 적용된 무해한 변경은 유지합니다. 출력된 `systemctl` 오류의 원인을 해결하며, swap unit이 하나도 없는 빈 필터 결과는 이 실패가 아닙니다. | 같은 `100` 재실행 |
| Enabled systemd swap unit 발견 | 이름을 추측해 disable하지 않습니다. 생성 주체와 용도를 확인해 사람이 처리합니다. | 같은 `100` 재실행 |
| APT·swap·fstab·sysctl 변경 또는 검증 실패 | 뒤 단계를 실행하지 않습니다. 원인을 해결하고 이미 적용된 무해한 변경은 유지합니다. | 같은 `100` 재실행 |
| 재부팅 필요 | 다음 script를 실행하지 않습니다. EC2를 재부팅하고 재접속합니다. | `100`부터 재검증 |

Script는 최초 변경 전 `/etc/fstab`과 기존 project sysctl file의 backup을 보존합니다.
정상 실패 복구가 아니라 host 준비 자체를 취소할 때만
[Kubernetes bootstrap 취소](500-kubernetes-bootstrap-cancellation-runbook.md)를 사용합니다.
Package upgrade는 이 backup으로 되돌아가지 않습니다.

## 다음 단계와 관련 문서

- **다음 실행**: [Containerd 설치](220-containerd-installation-runbook.md)
- **개념·선택 근거**: [Kubernetes cluster](../project/infrastructure/140-kubernetes-cluster.md)
- **구현 source**: [`100-prepare-host.sh`](../../scripts/bootstrap/kubernetes/100-prepare-host.sh)
