# Kubernetes bootstrap 취소 runbook

정상 오류를 해결하고 같은 script를 재실행하는 대신, 아직 서비스가 없는 bootstrap host를
의도적으로 이전 상태로 되돌리려 할 때만 사용하는 절차입니다. 각 단계의 일반 실패 복구는
해당 `210`~`250` runbook이 소유합니다. 취소 가능한 범위와 data 보존 원칙은
[Kubernetes bootstrap 취소 개념](../project/infrastructure/310-kubernetes-bootstrap-cancellation.md)에서
확인합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | 아직 service workload가 없는 bootstrap host를 의도적으로 이전 상태로 되돌리기로 결정했을 때만 사용합니다. 일반 실패 재실행에는 사용하지 않습니다. |
| 누가 | 현재 host·cluster data 영향과 backup source를 판단하고 EC2에서 `sudo`를 사용할 수 있는 운영자가 실행합니다. |
| 어디서 | SSH로 접속한 target EC2 terminal입니다. 명령마다 path가 절대 경로이므로 고정 current directory는 없습니다. |
| 무엇을 | 아래 단계의 표준 read-only 확인과 제한된 file 복원 명령을 사람이 선택해 실행합니다. 전체 취소 repository script는 없습니다. |
| 왜 | 자동 reset·광범위 삭제 대신 현재 bootstrap이 보존한 file만 확인 후 복원해 data 손실을 제한하기 위해서입니다. |
| 완료·재개 | 선택한 단계의 원래 file·service 상태를 직접 확인합니다. Source·영향을 확정할 수 없으면 명령을 실행하지 않고 별도 복구 결정을 기다립니다. |

## 사용 전 경계

- 실행 중인 cluster나 workload가 있으면 이 문서의 host·runtime 복원 명령을 사용하지
  않습니다.
- Backup 존재만으로 복원 가능성을 추정하지 않고 현재 file, 생성 주체와 backup 내용을
  먼저 비교합니다.
- Package upgrade·runtime data·PKI·etcd·cluster resource는 file backup 몇 개로 되돌아가지
  않습니다.
- 이 문서는 현재 bootstrap이 이미 보존한 file의 제한된 복원만 소유합니다. 일반적인
  `kubeadm reset`, package 제거, `/var/lib/containerd`·etcd data 삭제와 CNI rollback 명령은
  제공하지 않습니다.

## `100` host 준비 취소

현재 cluster가 실행 중이지 않고 host 준비를 취소하기로 확정했을 때만 backup 내용을 먼저
확인합니다.

```bash
sudo test -f /etc/fstab.before-kubernetes-bootstrap
sudo sed -n '1,240p' /etc/fstab.before-kubernetes-bootstrap
sudo test -f /etc/sysctl.d/99-car-community-kubernetes.conf.before-kubernetes-bootstrap
```

`fstab` backup이 원래 상태임을 확인했다면 복원하고 swap을 다시 적용합니다.

```bash
sudo cp -p /etc/fstab.before-kubernetes-bootstrap /etc/fstab
sudo swapon --all
```

기존 project sysctl file의 backup이 있다면 그 file만 복원하고 전체 sysctl source를 다시
적용합니다.

```bash
sudo cp -p \
  /etc/sysctl.d/99-car-community-kubernetes.conf.before-kubernetes-bootstrap \
  /etc/sysctl.d/99-car-community-kubernetes.conf
sudo sysctl --system
```

기존 project sysctl file이 없어서 backup도 없다면, 현재 file 첫 줄과
[`100-prepare-host.sh`](../../scripts/bootstrap/kubernetes/100-prepare-host.sh)을 대조해 이 bootstrap이
만든 file인지 확인합니다. 다른 관리 주체가 사용하지 않는 project file임을 확인한 경우에만
제거하고 나머지 sysctl source를 다시 적용합니다.

```bash
sudo rm /etc/sysctl.d/99-car-community-kubernetes.conf
sudo sysctl --system
```

Package upgrade는 위 backup으로 rollback되지 않습니다. Downgrade가 필요하면 설치 version과
영향 범위를 확인한 별도 복구 작업으로 다룹니다.

## `200` containerd 구성 취소

Kubernetes component와 cluster를 아직 만들지 않았고 containerd 구성을 취소하기로 확정한
경우에만 현재 상태와 backup을 확인합니다.

```bash
dpkg-query -W -f='${Status} ${Version}\n' containerd
apt-mark showhold | grep -Fx containerd
systemctl status containerd
sudo sed -n '1p' /etc/containerd/config.toml
sudo test -f /etc/containerd/config.toml.before-containerd-bootstrap
sudo test -f /etc/modules-load.d/99-car-community-containerd.conf.before-containerd-bootstrap
```

취소 대상과 backup 내용을 확인한 뒤 package hold와 service를 해제합니다.

```bash
sudo apt-mark unhold containerd
sudo systemctl disable --now containerd
```

각 backup이 존재하고 원본임을 확인했다면 해당 명령만 개별 실행해 file을 복원합니다.

```bash
sudo cp -p \
  /etc/containerd/config.toml.before-containerd-bootstrap \
  /etc/containerd/config.toml
```

```bash
sudo cp -p \
  /etc/modules-load.d/99-car-community-containerd.conf.before-containerd-bootstrap \
  /etc/modules-load.d/99-car-community-containerd.conf
```

Backup이 없다면 원래 file도 없었는지 Git의 script와 현재 file 첫 줄을 대조한 뒤
project가 생성한 file만 제거할 수 있습니다. 이 문서는 removal 명령을 제공하지 않습니다.

- `overlay` module은 다른 process가 사용할 수 있으므로 자동 unload하지 않습니다.
- Image·snapshot이 있을 수 있는 `/var/lib/containerd`는 삭제하지 않습니다.
- Containerd package와 `runc` 제거는 다른 consumer와 보존할 runtime data를 확인한 별도
  작업입니다.

## `300` Kubernetes component 설치 취소

`300`은 cluster state를 만들지 않지만 package·repository를 자동 제거하지도 않습니다.
`kubeadm init`을 실행하지 않았음을 확인한 뒤 현재 상태를 조사합니다.

```bash
dpkg-query -W -f='${db:Status-Abbrev} ${Package} ${Version}\n' kubelet kubeadm kubectl
apt-mark showhold | grep -E '^(kubelet|kubeadm|kubectl)$'
cat /etc/apt/sources.list.d/kubernetes.list
systemctl is-enabled kubelet
```

Package unhold·제거와 keyring·source 삭제는 다른 APT source와 `kubernetes-cni` dependency의
consumer를 확인한 별도 작업입니다. 이 문서는 확인 없는 삭제 명령을 제공하지 않습니다.

## `400` 이후 cluster 취소

`admin.conf` 없이 PKI·manifest·kubelet·etcd marker가 남은 `partial` 상태나 이미 생성된
cluster는 host file 복원으로 되돌릴 수 없습니다.

- File을 직접 삭제하거나 일반 `kubeadm reset`을 실행하지 않습니다.
- PKI, kubeconfig, local etcd와 기존 data 상태를 조사합니다.
- 손실 영향에 따라 reset, 복구 또는 재구축 중 하나를 사용자가 승인하는 별도 작업으로
  진행합니다.

## `500` Calico 이후 취소

다른 CNI 전환, Calico rollback·upgrade, server-side apply field 인수와 cluster resource
삭제는 일반 bootstrap 취소가 아닙니다. 현재 CNI, workload 영향, 목표 version과 rollback을
확정한 별도 migration runbook이 필요합니다.

## 완료 판정

이 문서 전체를 순서대로 실행하는 것이 완료 조건이 아닙니다. 의도적으로 취소할 exact
단계만 선택하고 다음을 확인합니다.

- 다른 단계와 관리 주체의 file·package·service·data를 변경하지 않았습니다.
- 사용한 backup이 실제 기존 상태였고 복원 후 effective state를 확인했습니다.
- 이 문서가 다루지 않는 package·runtime·cluster data 책임은 별도 작업으로 남겼습니다.

## 관련 문서

- **취소 경계와 원칙**: [Kubernetes bootstrap 취소 개념](../project/infrastructure/310-kubernetes-bootstrap-cancellation.md)
- **전체 구축 경계**: [Kubernetes bootstrap](200-kubernetes-bootstrap-runbook.md)
- **Host 정상 재실행**: [Host 준비](210-kubernetes-host-preparation-runbook.md#중단복구와-재개)
- **Containerd 정상 재실행**: [Containerd 설치](220-containerd-installation-runbook.md#중단복구와-재개)
- **Component 정상 재실행**: [Kubernetes component 설치](230-kubernetes-components-installation-runbook.md#중단복구와-재개)
- **Control plane 복구 경계**: [Control plane 초기화](240-kubernetes-control-plane-initialization-runbook.md#중단복구와-재개)
- **Calico 정상 재실행**: [Calico network 설치](250-calico-network-installation-runbook.md#중단복구와-재개)
