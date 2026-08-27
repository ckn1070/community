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
| 기본 실행 위치 | SSH로 접속한 target EC2 terminal입니다. |
| 기본 시작 directory | 제한 없음 |
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

**실행 위치:** Target EC2 terminal

**실행 조건:** 현재 cluster가 실행 중이지 않고 host 준비 취소가 확정됨

### 100.1 Host backup 확인

먼저 backup 내용을 확인합니다.

```bash
# 1. fstab backup 존재 확인
# 확인: exit 0
printf '\n\n========== [1/3] fstab backup 존재 ==========\n\n'
sudo test -f /etc/fstab.before-kubernetes-bootstrap

# 2. fstab backup 내용 조회
# 확인: 복원하려는 원래 mount·swap 항목
printf '\n\n========== [2/3] fstab backup 내용 ==========\n\n'
sudo sed -n '1,240p' /etc/fstab.before-kubernetes-bootstrap

# 3. Project sysctl backup 존재 확인
# 확인: 기존 file을 복원할 때 exit 0
printf '\n\n========== [3/3] sysctl backup 존재 ==========\n\n'
sudo test -f /etc/sysctl.d/99-car-community-kubernetes.conf.before-kubernetes-bootstrap
```

1. 첫 확인은 복원할 `fstab` backup이 실제로 있는지 판정합니다.
2. 내용 조회는 backup이 의도한 원래 host 상태인지 사람이 확인하게 합니다.
3. 마지막 확인은 project sysctl이 기존 file 복원 분기인지 판정합니다.

### 100.2 fstab과 swap 복원

`fstab` backup이 원래 상태임을 확인했다면 복원하고 swap을 다시 적용합니다.

```bash
# 1. 확인한 fstab backup 복원
# 확인: exit 0
printf '\n\n========== [1/2] fstab 복원 ==========\n\n'
sudo cp -p /etc/fstab.before-kubernetes-bootstrap /etc/fstab

# 2. 복원한 fstab의 swap 적용
# 확인: 오류 없이 exit 0
printf '\n\n========== [2/2] swap 재적용 ==========\n\n'
sudo swapon --all
```

1. Backup을 원래 경로에 복원합니다.
2. 복원된 설정의 swap 항목을 현재 host에 적용합니다.

### 100.3 기존 project sysctl 복원

기존 project sysctl file의 backup이 있다면 그 file만 복원하고 전체 sysctl source를 다시
적용합니다.

```bash
# 1. Project sysctl backup 복원
# 확인: exit 0
printf '\n\n========== [1/2] sysctl file 복원 ==========\n\n'
sudo cp -p \
  /etc/sysctl.d/99-car-community-kubernetes.conf.before-kubernetes-bootstrap \
  /etc/sysctl.d/99-car-community-kubernetes.conf

# 2. 전체 sysctl source 재적용
# 확인: 오류 없이 설정 목록 출력 후 exit 0
printf '\n\n========== [2/2] sysctl 재적용 ==========\n\n'
sudo sysctl --system
```

1. 확인한 project sysctl backup만 원래 경로에 복원합니다.
2. 복원된 값을 포함한 현재 sysctl source를 kernel에 적용합니다.

### 100.4 Bootstrap 생성 sysctl 제거

기존 project sysctl file이 없어서 backup도 없다면, 현재 file 첫 줄과
[`100-prepare-host.sh`](../../scripts/bootstrap/kubernetes/100-prepare-host.sh)을 대조해 이 bootstrap이
만든 file인지 확인합니다. 다른 관리 주체가 사용하지 않는 project file임을 확인한 경우에만
제거하고 나머지 sysctl source를 다시 적용합니다.

```bash
# 1. Bootstrap이 만든 project sysctl file 제거
# 확인: exit 0
printf '\n\n========== [1/2] project sysctl 제거 ==========\n\n'
sudo rm /etc/sysctl.d/99-car-community-kubernetes.conf

# 2. 남은 sysctl source 재적용
# 확인: 오류 없이 설정 목록 출력 후 exit 0
printf '\n\n========== [2/2] sysctl 재적용 ==========\n\n'
sudo sysctl --system
```

1. 소유권을 확인한 project file만 제거합니다.
2. 제거 뒤 남은 sysctl source로 effective state를 다시 계산합니다.

Package upgrade는 위 backup으로 rollback되지 않습니다. Downgrade가 필요하면 설치 version과
영향 범위를 확인한 별도 복구 작업으로 다룹니다.

## `200` containerd 구성 취소

**실행 위치:** Target EC2 terminal

**실행 조건:** Kubernetes component와 cluster를 아직 만들지 않았고 containerd 구성 취소가
확정됨

### 200.1 Containerd 현재 상태와 backup 확인

현재 상태와 backup을 확인합니다.

```bash
# 1. Containerd package 상태·version 조회
# 확인: install ok installed와 현재 version
printf '\n\n========== [1/6] containerd package ==========\n\n'
dpkg-query -W -f='${Status} ${Version}\n' containerd

# 2. Containerd package hold 조회
# 확인: hold 상태면 containerd 한 줄
printf '\n\n========== [2/6] containerd hold ==========\n\n'
apt-mark showhold | grep -Fx containerd

# 3. Containerd service 상태 조회
# 확인: 현재 active·enabled 상태
printf '\n\n========== [3/6] containerd service ==========\n\n'
systemctl status containerd

# 4. 현재 config owner marker 조회
# 확인: project가 생성한 file이면 식별 가능한 첫 줄
printf '\n\n========== [4/6] containerd config owner ==========\n\n'
sudo sed -n '1p' /etc/containerd/config.toml

# 5. Containerd config backup 존재 확인
# 확인: 기존 file 복원 분기이면 exit 0
printf '\n\n========== [5/6] containerd config backup ==========\n\n'
sudo test -f /etc/containerd/config.toml.before-containerd-bootstrap

# 6. Module config backup 존재 확인
# 확인: 기존 file 복원 분기이면 exit 0
printf '\n\n========== [6/6] module config backup ==========\n\n'
sudo test -f /etc/modules-load.d/99-car-community-containerd.conf.before-containerd-bootstrap
```

1. Package 조회는 취소 대상 version을 고정합니다.
2. Hold 조회는 해제할 package pin 상태를 확인합니다.
3. Service 조회는 중지·disable 전 현재 상태를 기록합니다.
4. Owner marker는 현재 config가 이 bootstrap 소유인지 판정합니다.
5. Config backup 확인은 기존 file 복원 가능 여부를 판정합니다.
6. Module backup 확인은 기존 module file 복원 가능 여부를 판정합니다.

### 200.2 Package hold와 service 해제

취소 대상과 backup 내용을 확인한 뒤 package hold와 service를 해제합니다.

```bash
# 1. Containerd package hold 해제
# 확인: Canceled hold on containerd
printf '\n\n========== [1/2] containerd hold 해제 ==========\n\n'
sudo apt-mark unhold containerd

# 2. Containerd service 중지·disable
# 확인: 오류 없이 exit 0
printf '\n\n========== [2/2] containerd service 해제 ==========\n\n'
sudo systemctl disable --now containerd
```

1. Package hold를 해제해 bootstrap의 package 고정을 취소합니다.
2. Service를 즉시 중지하고 boot 자동 시작을 해제합니다.

### 200.3 Containerd file backup 복원

각 backup이 존재하고 원본임을 확인했다면 해당 명령만 개별 실행해 file을 복원합니다.

```bash
# 1. Containerd config backup 복원
# 확인: 해당 backup이 있을 때 exit 0
printf '\n\n========== [1/2] containerd config 복원 ==========\n\n'
sudo cp -p \
  /etc/containerd/config.toml.before-containerd-bootstrap \
  /etc/containerd/config.toml

# 2. Module config backup 복원
# 확인: 해당 backup이 있을 때 exit 0
printf '\n\n========== [2/2] module config 복원 ==========\n\n'
sudo cp -p \
  /etc/modules-load.d/99-car-community-containerd.conf.before-containerd-bootstrap \
  /etc/modules-load.d/99-car-community-containerd.conf
```

1. 첫 복원은 기존 containerd config backup이 확인된 경우에만 실행합니다.
2. 두 번째 복원은 기존 module config backup이 확인된 경우에만 실행합니다.

두 backup 중 하나만 존재하면 해당 번호의 명령만 실행하며, 없는 backup의 `cp`를 실행하지
않습니다.

### 200.4 Backup이 없는 file의 제거 판단

Backup이 없다면 원래 file도 없었는지 Git의 script와 현재 file 첫 줄을 대조한 뒤
project가 생성한 file만 제거할 수 있습니다. 이 문서는 removal 명령을 제공하지 않습니다.

- `overlay` module은 다른 process가 사용할 수 있으므로 자동 unload하지 않습니다.
- Image·snapshot이 있을 수 있는 `/var/lib/containerd`는 삭제하지 않습니다.
- Containerd package와 `runc` 제거는 다른 consumer와 보존할 runtime data를 확인한 별도
  작업입니다.

## `300` Kubernetes component 설치 취소

**실행 위치:** Target EC2 terminal

**실행 조건:** `kubeadm init`을 실행하지 않음

현재 package·repository·service 상태를 조사합니다. `300`은 cluster state를 만들지 않지만
package·repository를 자동 제거하지도 않습니다.

```bash
# 1. Kubernetes component package 상태·version 조회
# 확인: kubelet·kubeadm·kubectl의 상태와 version
printf '\n\n========== [1/4] Kubernetes package ==========\n\n'
dpkg-query -W -f='${db:Status-Abbrev} ${Package} ${Version}\n' kubelet kubeadm kubectl

# 2. Kubernetes component hold 조회
# 확인: hold된 component 이름
printf '\n\n========== [2/4] Kubernetes package hold ==========\n\n'
apt-mark showhold | grep -E '^(kubelet|kubeadm|kubectl)$'

# 3. Kubernetes APT source 조회
# 확인: 현재 pkgs.k8s.io repository 항목
printf '\n\n========== [3/4] Kubernetes APT source ==========\n\n'
cat /etc/apt/sources.list.d/kubernetes.list

# 4. Kubelet enable 상태 조회
# 확인: enabled·disabled 등 현재 상태
printf '\n\n========== [4/4] kubelet enable 상태 ==========\n\n'
systemctl is-enabled kubelet
```

1. Package 조회는 취소 후보 component와 version을 고정합니다.
2. Hold 조회는 별도 제거 작업에서 해제할 pin 상태를 확인합니다.
3. APT source 조회는 삭제 판단 전에 현재 repository owner를 확인합니다.
4. Kubelet 조회는 service 자동 시작 상태를 확인합니다.

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
