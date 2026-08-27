# Kubernetes bootstrap script

이 디렉터리는 Ubuntu host 준비부터 단일 node Kubernetes cluster network 검증까지의 순차
실행 script를 소유합니다.
AWS resource 생성이나 EC2 접속 방법은 소유하지 않습니다. 전체 실행 순서와 공통 완료
경계는 [Kubernetes bootstrap runbook](../../../docs/operations/200-kubernetes-bootstrap-runbook.md),
각 단계의 실제 동작·선택 이유·live checkpoint와 복구는 연결된 단계별 runbook이
소유합니다.
EC2로 전달할 file, SSH 명령과 local·server 실행 directory는
[150 EC2 실행 artifact 전달](../../../docs/operations/150-ec2-execution-artifact-transfer-runbook.md)이
소유합니다.

현재 구현된 단계는 `100-prepare-host.sh`, `200-install-containerd.sh`,
`300-install-kubernetes-components.sh`, `400-initialize-cluster.sh`,
`500-install-calico.sh`입니다. 번호는 실행 순서를 나타냅니다. 실제 EC2에서 `100`부터
`500`까지 각 postcondition을 통과해야 빈 단일 node Kubernetes cluster가 준비됩니다.

## 위치·번호와 단계 분리 기준

- `bootstrap`은 처음부터 원하는 host 상태로 수렴시키는 순차 작업을 담습니다. Kubernetes
  manifest나 cluster 운영 명령만 모으는 `k8s` 디렉터리보다 package·kernel·systemd까지
  다루는 현재 책임을 정확히 표현합니다.
- `100` 단위 번호는 lexical sort만으로 큰 실행 순서를 드러내면서, 같은 단계 안에
  보완 script가 필요할 때 기존 file을 모두 rename하지 않고 중간 번호를 둘 여지를
  남깁니다. 번호 자체가 실행을 자동화하거나 의존성을 검증하지는 않습니다.
- Script는 command 개수나 package 하나마다 나누지 않습니다. 선행 조건, 함께 적용해야 할
  side effect, 독립 검증 가능한 postcondition, 재부팅·복구 경계가 하나로 닫히는 단위를
  한 file로 둡니다.
- `100`은 OS package, swap과 IPv4 forwarding이라는 host 준비 경계를 닫습니다. 실제
  consumer가 정해져야 판단할 kernel module과 container runtime 설정은 이 경계를 넘으므로
  포함하지 않습니다.
- `200`은 고정 version의 containerd package, `overlay` module, CRI·cgroup 설정과 service
  검증이라는 container runtime 경계를 닫습니다. Kubernetes package와 cluster state를
  변경하지 않습니다.
- `300`은 Kubernetes v1.36 official APT repository와 keyring, exact version의 `kubelet`,
  `kubeadm`, `kubectl` package와 hold라는 component 설치 경계를 닫습니다. `kubeadm init`,
  CNI add-on 선택·설정과 cluster state는 변경하지 않습니다.
- `400`은 확정한 kubeadm 설정으로 control-plane state를 최초 한 번 생성하고 API server,
  etcd, control-plane static Pod, kubelet과 kube-proxy를 검증하는 경계를 닫습니다. CNI 전
  Node `NotReady`와 CoreDNS `Pending`은 허용하며 CNI와 taint는 변경하지 않습니다.
- `500`은 Calico를 독립 적용·재적용하고 control-plane taint를 제거한 뒤 Node·CoreDNS와
  임시 일반 workload의 DNS·ClusterIP 통신을 검증하는 network 경계를 닫습니다. Cluster
  state 최초 생성과 AWS Load Balancer Controller는 변경하지 않습니다.

## 공통 실행 계약

다섯 script는 정상 반복과 부분 실패 뒤의 재실행이 상태를 망치지 않는 범위에서 같은 목표
상태로 수렴합니다. 모든 command를 생략하는 형식적 멱등성은 목표가 아니며, 안전한 package
갱신·설치와 service 동작은 반복될 수 있습니다. 공통 설계·검증 기준은
[스크립트 엔지니어링 가이드](../../../docs/workflow/240-script-engineering-guideline.md)가
소유합니다. 실제 전체 순서·공통 경계는
[Kubernetes bootstrap runbook](../../../docs/operations/200-kubernetes-bootstrap-runbook.md),
단계별 선택 이유·중단과 복구는 각 공개 계약에 연결된 단계별 runbook이 소유합니다.

## `100-prepare-host.sh` 공개 계약

실제 운영 절차와 선택 이유는
[Host 준비 runbook](../../../docs/operations/210-kubernetes-host-preparation-runbook.md)이
소유합니다.

| 항목 | 계약 |
| --- | --- |
| caller·시점 | 운영자가 Ubuntu 24.04 host에 접속하고 전달·검증된 정확한 script를 준비한 뒤 최초 Kubernetes 구성 전에 직접 실행합니다. |
| 실행 명령 | EC2의 `/home/ubuntu/car-community-scripts`에서 `sudo ./bootstrap/kubernetes/100-prepare-host.sh`를 실행합니다. 인자와 환경변수 입력은 없습니다. |
| 지원 범위 | Ubuntu `24.04`, systemd, Bash입니다. CPU architecture에는 의존하지 않습니다. |
| 선행 command | `apt-get`, `awk`, `cat`, `chmod`, `cp`, `mktemp`, `mv`, `rm`, `swapoff`, `swapon`, `sysctl`, `systemctl`, `timedatectl`이 필요합니다. `ufw`는 설치된 경우에만 상태를 확인합니다. |
| secret | 읽거나 생성하거나 출력하지 않습니다. |
| 직접 변경 | `apt-get update`, `apt-get upgrade --yes`, Super Admin 운영 절차가 사용하는 `jq` 설치, 현재 swap 비활성화, `/etc/fstab`의 활성 swap entry 주석 처리, `net.ipv4.ip_forward=1` 영구·현재 적용입니다. |
| 변경하지 않음 | 시간 동기화 provider, UFW rule·상태, systemd swap unit, kernel module, container runtime, kubeadm, CNI를 설치하거나 변경하지 않습니다. |
| systemd swap 판정 | 전체 unit file inventory 조회가 성공한 뒤 이름이 `.swap`으로 끝나는 `enabled`·`enabled-runtime` unit만 찾습니다. Swap unit이 하나도 없는 빈 필터 결과는 정상이며, 전체 inventory 조회 실패는 오류입니다. |
| 성공 postcondition | `jq` command가 있고, 활성 swap과 재부팅 시 활성화될 fstab·systemd swap 설정이 없으며, project 소유 sysctl file과 현재 kernel의 IPv4 forwarding 값이 `1`이고, system clock이 동기화되어 있고 UFW가 없거나 비활성입니다. |
| 재부팅 checkpoint | `/var/run/reboot-required`가 있으면 재부팅 필요를 보고합니다. 재부팅·재접속 뒤 같은 `100`을 다시 실행해 marker 부재와 postcondition을 재검증해야 다음 단계로 갈 수 있습니다. |
| exit | `0`은 위 postcondition 검증 통과와 재부팅 필요 여부 보고까지 끝났음을 뜻합니다. 현재 swap, `/etc/fstab` 또는 전체 systemd unit file inventory 조회가 실패하면 빈 상태로 간주하지 않고 nonzero로 중단합니다. 그 밖의 값은 완료로 취급하지 않습니다. `--help`·`-h`는 사용법만 출력하고 `0`으로 끝납니다. |

## 검증

macOS를 포함한 local shell에서는 root·systemd·kernel을 사용하는 전체 실행을 검증할 수
없습니다. 다음 test는 file 변환, 최초 backup 보존, 현재 swap·fstab 조회 실패의
fail-closed 처리, 빈 systemd swap 결과의 정상 판정, 전체 unit inventory의 enabled swap
필터와 조회 실패 전파, 재실행과 UFW 출력 판정 계약만 fixture로 확인합니다.

```bash
bash -n scripts/bootstrap/kubernetes/100-prepare-host.sh \
  scripts/bootstrap/kubernetes/tests/test-100-prepare-host.sh
shellcheck -x -P SCRIPTDIR \
  scripts/bootstrap/kubernetes/100-prepare-host.sh \
  scripts/bootstrap/kubernetes/tests/test-100-prepare-host.sh
scripts/bootstrap/kubernetes/tests/test-100-prepare-host.sh
```

실제 완료 판정에는 Ubuntu 24.04 EC2에서 script를 실행한 결과와 runbook의 postcondition
확인이 별도로 필요합니다.

## `200-install-containerd.sh` 공개 계약

실제 운영 절차와 선택 이유는
[Containerd 설치 runbook](../../../docs/operations/220-containerd-installation-runbook.md)이
소유합니다.

| 항목 | 계약 |
| --- | --- |
| caller·시점 | 운영자가 `100-prepare-host.sh`의 실제 성공과 필요한 재부팅을 완료한 뒤 직접 실행합니다. |
| 실행 명령 | EC2의 `/home/ubuntu/car-community-scripts`에서 `sudo ./bootstrap/kubernetes/200-install-containerd.sh`를 실행합니다. 인자와 환경변수 입력은 없습니다. |
| 지원 범위 | Ubuntu `24.04`, `arm64`, systemd, Bash입니다. |
| version 계약 | Kubernetes 목표 version은 `1.36.2`, Ubuntu containerd package는 `2.2.1-0ubuntu1~24.04.3`입니다. 지정 package가 repository에 없으면 다른 version으로 대체하지 않습니다. |
| 선행 command | Ubuntu 기본 `apt`, `dpkg`, `systemd`, `modprobe`, `flock`과 file 처리 command가 필요합니다. 설치 후 package가 제공하는 `containerd`, `ctr`, `runc`를 검증합니다. |
| secret | 읽거나 생성하거나 출력하지 않습니다. |
| 직접 변경 | Ubuntu package index 갱신, containerd 설치·hold, `overlay` module 현재·영구 활성화, `/etc/containerd/config.toml` 생성, containerd service enable·restart입니다. |
| 변경하지 않음 | Docker Engine, Kubernetes component, CNI, cluster state, UFW·AWS Security Group, registry credential을 설치하거나 변경하지 않습니다. |
| 성공 postcondition | 고정 package version과 hold, `overlay` module, project 관리 config, `SystemdCgroup=true`, enabled·active service, CRI socket과 정상 CRI images·runtime plugin을 모두 확인합니다. |
| exit | `0`은 모든 postcondition 검증 통과를 뜻합니다. 그 밖의 값은 완료로 취급하지 않습니다. `--help`·`-h`는 사용법만 출력하고 `0`으로 끝납니다. |

## `200` 검증

Local contract test는 containerd exact package 요청과 다른 설치 version 거부, 최소
config의 정확한 내용과 검증 실패 시 원자적 게시 차단, module·config의 재실행·backup,
관리 표식, Docker CLI와 runtime의 구분, CRI images·runtime plugin 상태 판정을 확인합니다.

```bash
bash -n scripts/bootstrap/kubernetes/200-install-containerd.sh \
  scripts/bootstrap/kubernetes/tests/test-200-install-containerd.sh
shellcheck -x -P SCRIPTDIR \
  scripts/bootstrap/kubernetes/200-install-containerd.sh \
  scripts/bootstrap/kubernetes/tests/test-200-install-containerd.sh
scripts/bootstrap/kubernetes/tests/test-200-install-containerd.sh
```

이 검증은 실제 Ubuntu package 설치, kernel module, systemd service와 Unix socket 동작을
증명하지 않습니다. 실제 완료는 Ubuntu 24.04 ARM64 EC2에서 실행한 postcondition으로만
판정합니다.

## `300-install-kubernetes-components.sh` 공개 계약

실제 운영 절차와 선택 이유는
[Kubernetes component 설치 runbook](../../../docs/operations/230-kubernetes-components-installation-runbook.md)이
소유합니다.

| 항목 | 계약 |
| --- | --- |
| caller·시점 | 운영자가 `200-install-containerd.sh`의 실제 성공을 확인한 뒤 `kubeadm init` 전에 직접 실행합니다. |
| 실행 명령 | EC2의 `/home/ubuntu/car-community-scripts`에서 `sudo ./bootstrap/kubernetes/300-install-kubernetes-components.sh`를 실행합니다. 인자와 환경변수 입력은 없습니다. |
| 지원 범위 | Ubuntu `24.04`, `arm64`, systemd, Bash입니다. |
| version 계약 | Kubernetes 목표 version은 `1.36.2`, 세 Debian package의 exact version은 `1.36.2-2.1`, minor repository는 `v1.36`입니다. 다른 version으로 자동 대체하거나 upgrade·downgrade하지 않습니다. |
| repository 계약 | Official `https://pkgs.k8s.io/core:/stable:/v1.36/deb/`와 `Release.key`를 사용합니다. Keyring은 `/etc/apt/keyrings/kubernetes-apt-keyring.gpg`, source는 `/etc/apt/sources.list.d/kubernetes.list`가 소유합니다. |
| 선행 dependency | Ubuntu 기본 `apt`, `dpkg`, `systemd`, `flock`과 file 처리 command가 필요합니다. Repository 구성을 위해 `ca-certificates`, `curl`, `gpg`를 Ubuntu package로 설치합니다. Ubuntu 24.04의 APT가 HTTPS를 직접 지원하므로 dummy 호환 package인 `apt-transport-https`는 추가하지 않습니다. |
| secret | 읽거나 생성하거나 출력하지 않습니다. Repository signing key는 공개 key입니다. |
| 직접 변경 | Ubuntu package index 갱신, official keyring·repository 설정, `kubelet`·`kubeadm`·`kubectl` exact package 설치·hold, kubelet service enable입니다. `kubelet` dependency가 `kubernetes-cni` package를 설치할 수 있지만 이 script는 CNI add-on을 선택하거나 구성하지 않습니다. |
| 변경하지 않음 | `kubeadm init`, Kubernetes manifest·cluster state, CNI add-on, UFW·AWS Security Group, containerd 설정을 생성하거나 변경하지 않습니다. Kubelet을 명시적으로 start·restart하지 않습니다. |
| 성공 postcondition | Exact repository·keyring과 mode `0644`, 세 package·binary version, 세 package hold, kubelet service `enabled`를 확인합니다. `kubeadm init` 전 kubelet의 `active` 여부는 성공 조건이 아닙니다. |
| exit | `0`은 모든 postcondition 검증 통과를 뜻합니다. 그 밖의 값은 완료로 취급하지 않습니다. `--help`·`-h`는 사용법만 출력하고 `0`으로 끝납니다. |

## `300` 검증

Local contract test는 exact version·repository 계약, final file mode 수렴, keyring 게시와
index 갱신 순서, 미관리 source 거부, download·dearmor 실패 시 final keyring 보존과 임시
file 정리, 다른 package version 거부, exact package 요청, install·hold 순서, 부분 설치
실패와 pre-init kubelet enable 경계를 확인합니다.

```bash
bash -n scripts/bootstrap/kubernetes/300-install-kubernetes-components.sh \
  scripts/bootstrap/kubernetes/tests/test-300-install-kubernetes-components.sh
shellcheck -x -P SCRIPTDIR \
  scripts/bootstrap/kubernetes/300-install-kubernetes-components.sh \
  scripts/bootstrap/kubernetes/tests/test-300-install-kubernetes-components.sh
scripts/bootstrap/kubernetes/tests/test-300-install-kubernetes-components.sh
```

이 검증은 실제 signing key download, APT signature·package 설치와 systemd 동작을 증명하지
않습니다. 실제 완료는 Ubuntu 24.04 ARM64 EC2에서 실행한 postcondition으로만 판정합니다.

## `400-initialize-cluster.sh` 공개 계약

실제 운영 절차와 선택 이유는
[Control plane 초기화 runbook](../../../docs/operations/240-kubernetes-control-plane-initialization-runbook.md)이
소유합니다.

| 항목 | 계약 |
| --- | --- |
| caller·시점 | 운영자가 같은 EC2에서 `100`~`300`의 실제 postcondition과 필요한 재부팅을 완료한 뒤 최초 cluster 초기화 시점에 직접 실행합니다. |
| 실행 명령 | EC2의 `/home/ubuntu/car-community-scripts`에서 `sudo ./bootstrap/kubernetes/400-initialize-cluster.sh`를 실행합니다. 인자와 환경변수 입력은 없습니다. |
| 지원 범위 | Ubuntu `24.04`, `arm64`, systemd, Bash, Kubernetes `1.36.2`입니다. |
| kubeadm 계약 | Config API `kubeadm.k8s.io/v1beta4`, cluster 이름 `stg-car-community-k8s`, containerd CRI socket `unix:///run/containerd/containerd.sock`, 현재 primary private IPv4와 port `6443`, Pod CIDR `192.168.0.0/16`, Service CIDR `10.96.0.0/12`, DNS domain `cluster.local`입니다. Stable `controlPlaneEndpoint`는 만들지 않습니다. |
| 선행 검증 | `100`~`300`의 완료 판정은 caller가 소유하며 이 script가 각 단계의 package·hold·설정 구현을 반복 검사하지 않습니다. 현재 소비하는 exact `kubeadm`·`kubelet` version, staging VPC `10.0.0.0/16`의 primary private IPv4와 최초 init 후 바꾸기 어려운 Pod·Service CIDR의 host route 충돌만 확인합니다. Route 조회 실패는 충돌 없음으로 간주하지 않습니다. |
| secret·kubeconfig | Bootstrap token을 생성하되 `--skip-token-print`로 terminal에 출력하지 않습니다. `/etc/kubernetes/admin.conf`와 `super-admin.conf` 내용을 출력하거나 사용자 홈에 자동 복사하지 않습니다. 일반 사용자용 kubeconfig 배포는 runbook의 명시적 수동 단계입니다. |
| 직접 변경 | Control-plane image pull, kubeadm PKI·kubeconfig·static Pod·etcd·kubelet cluster 설정, kube-proxy와 CoreDNS resource를 최초 생성합니다. 생성에 사용한 두 설정 file은 mode `0600`의 임시 directory에 두고 실행 종료 시 삭제합니다. |
| 변경하지 않음 | 자동 `kubeadm reset`, 기존 cluster file 삭제·덮어쓰기, CNI 설치, control-plane taint, 사용자 홈 kubeconfig, application·RDS·AWS resource를 변경하지 않습니다. |
| ClusterConfiguration 비교 | 기대 설정과 uploaded 설정을 각각 `kubeadm config migrate`로 현재 API 기본값까지 정규화한 뒤 `ClusterConfiguration` 문서만 비교합니다. Migrate가 별도로 생성하는 `InitConfiguration`의 임의 bootstrap token은 cluster-wide 설정이 아니며 실행마다 달라지므로 비교에서 제외합니다. 정규화 결과에 `ClusterConfiguration`이 없으면 오류입니다. |
| 성공 postcondition | Uploaded ClusterConfiguration, Node InternalIP·version과 admin endpoint가 exact 계약과 같고, API `/readyz`, kubelet, 네 control-plane static Pod, etcd와 kube-proxy가 정상입니다. CoreDNS resource는 존재해야 하지만 CNI 전 readiness와 Node `Ready`는 요구하지 않습니다. |
| exit·재실행 | `0`은 위 검증 통과를 뜻합니다. Script가 판정하는 kubeadm marker가 없는 host만 init합니다. `admin.conf`가 있는 완료 후보에서는 init·image pull 없이 실제 cluster를 다시 검증합니다. `admin.conf` 없이 해당 marker가 남은 부분 상태, API 장애 또는 설정 불일치는 자동 복구하지 않고 nonzero로 종료합니다. |

전체 필수 순서와 단계 간 재진입 경계는
[Kubernetes bootstrap runbook](../../../docs/operations/200-kubernetes-bootstrap-runbook.md),
이 단계의 실패 판정과 복구는
[Control plane 초기화 runbook](../../../docs/operations/240-kubernetes-control-plane-initialization-runbook.md)이
소유합니다.

## `400` 검증

Local contract test는 고정 kubeadm 설정, binary version, 정상 route의 source IP 추출과
VPC 밖 primary IP 거부, typed route 충돌과 route 조회 실패의 차단, migrate가 생성한 임의
InitConfiguration token의 비교 제외와 ClusterConfiguration 누락 차단, 깨진 symlink를
포함한 cluster 상태 분류, 최초 init의 손상 방지 순서, 완료 후보의 init 생략, 부분 상태의
mutation 전 중단과 config validation·image pull 실패 전파를 확인합니다.

```bash
bash -n scripts/bootstrap/kubernetes/400-initialize-cluster.sh \
  scripts/bootstrap/kubernetes/tests/test-400-initialize-cluster.sh
shellcheck -x -P SCRIPTDIR \
  scripts/bootstrap/kubernetes/400-initialize-cluster.sh \
  scripts/bootstrap/kubernetes/tests/test-400-initialize-cluster.sh
scripts/bootstrap/kubernetes/tests/test-400-initialize-cluster.sh
```

이 검증은 실제 image pull, kubeadm PKI·etcd·static Pod와 API 동작을 증명하지 않습니다.
실제 완료는 Ubuntu 24.04 ARM64 EC2에서 script가 출력한 postcondition으로만 판정합니다.

## `500-install-calico.sh` 공개 계약

실제 운영 절차와 선택 이유는
[Calico network 구성 runbook](../../../docs/operations/250-calico-network-installation-runbook.md)이
소유합니다.

| 항목 | 계약 |
| --- | --- |
| caller·시점 | 운영자가 같은 EC2에서 확정 계약과 일치하는 `400` cluster의 실제 postcondition을 확인한 뒤 실행합니다. |
| 실행 명령 | EC2의 `/home/ubuntu/car-community-scripts`에서 `sudo ./bootstrap/kubernetes/500-install-calico.sh`를 실행합니다. 인자와 환경변수 입력은 없습니다. |
| 지원 범위 | Kubernetes `1.36.2`, Ubuntu `24.04`, `arm64`, 단일 control-plane node와 Calico `3.32.1`입니다. |
| artifact 계약 | Official tag의 `v1_crd_projectcalico_org.yaml`과 `tigera-operator.yaml`을 HTTPS로 임시 directory에 내려받고 repository가 검토·고정한 SHA-256 `192f8b...60f71`, `f18e07...ab07f`를 각각 확인합니다. 이 값은 upstream 발표 checksum이 아니라 exact raw bytes를 저장소가 고정한 값입니다. |
| network 계약 | Tigera operator, `Iptables` dataplane, `kubeProxyManagement: Disabled`, `NodeInternalIP`, 단일 pool `192.168.0.0/16`·block size `26`, `VXLANCrossSubnet`, `natOutgoing: Enabled`, `nodeSelector: all()`입니다. Calico APIServer·Goldmane·Whisker는 설치하지 않습니다. |
| target 사전 확인 | `400` 완료 판정은 caller가 소유하며, `500`은 자신이 직접 소비하는 admin kubeconfig·API readiness·IPv4 forwarding, 전체 Node가 정확히 하나인 단일 control-plane 구성, 해당 kubelet `v1.36.2`, API에 등록된 kube-controller-manager Pod spec의 유일한 `--cluster-cidr=192.168.0.0/16`만 확인합니다. 이름이 일치하는 container의 `command[*]`를 순회하고 JSONPath 현재 객체 `{@}`로 각 scalar를 출력하며, missing template key와 빈 command 결과도 허용하지 않습니다. 앞 단계의 package·static Pod·전체 kubeadm 설정을 누적 재검사하지 않습니다. |
| 기존 상태·조회 | NetworkManager는 `systemctl show`의 `LoadState`·`ActiveState`를 함께 확인해 `not-found/inactive`인 unit 부재 또는 `loaded/inactive`인 확정된 비활성 상태만 허용합니다. Active·failed·전이 중 상태, property 누락과 조회 실패는 중단합니다. Containerd CNI loader가 network 설정으로 읽는 `.conf`, `.conflist`, `.json` entry를 숨김 file과 symlink까지 기존 CNI 판정 대상으로 삼고, exact `10-calico.conflist` 외 항목을 발견하면 자동 전환하지 않습니다. 설정 directory가 없는 상태만 빈 상태로 허용하고, 경로 유형이 다르거나 열거에 실패하면 중단합니다. 확장자 없는 `.kubernetes-cni-keep`, `calico-kubeconfig` 같은 보조 file과 실제 directory는 network 설정으로 오인하거나 삭제하지 않습니다. `kube-system`의 raw-manifest Calico도 자동 전환하지 않습니다. 기존 Tigera operator는 없거나 exact image `quay.io/tigera/operator:v1.42.3`일 때만 허용해 이 단계가 version을 자동 upgrade·downgrade하지 않게 합니다. 이 확인은 cluster 전체 CNI inventory나 resource owner 증명을 대신하지 않습니다. |
| apply 충돌 경계 | CRD·operator·Installation을 field manager `car-community-bootstrap`의 server-side apply로 매번 수렴시키되 `--force-conflicts`를 사용하지 않습니다. Field manager는 이 apply가 관리하는 field 집합을 식별할 뿐 resource 전체 소유권을 뜻하지 않습니다. 다른 manager가 다른 값으로 관리하는 field와 충돌하면 강제로 인수하지 않고 nonzero로 중단합니다. |
| 직접 변경 | Exact CRD·operator·Installation을 server-side apply하고 Calico를 Ready로 만듭니다. 이후 단일 node의 `node-role.kubernetes.io/control-plane` taint만 상태를 확인해 제거합니다. 임시 일반 server·client Pod와 ClusterIP Service로 DNS resolution과 TCP Service routing을 검증한 뒤 bootstrap marker가 있는 고정 smoke namespace를 삭제합니다. |
| 변경하지 않음 | 다른 CNI 삭제·교체, field ownership 강제 인수, 자동 `kubeadm reset`, eBPF·kube-proxy replacement, Calico APIServer·Goldmane·Whisker, AWS IAM·Security Group·source/destination check, `node.kubernetes.io/exclude-from-external-load-balancers` label, Load Balancer Controller·ALB·application을 변경하지 않습니다. |
| 성공 postcondition | Exact operator·Installation과 실제 단일 IPPool의 이름·CIDR·block size·VXLAN·IPIP·NAT·node selector·활성 상태, `tigerastatus/calico`, kube-proxy, 단일 Node `Ready`, CoreDNS rollout이 정상입니다. IPPool의 활성 상태는 API 직렬화에서 `spec.disabled`가 생략되거나 명시적 `false`인 경우이며 `true`는 거부합니다. ARM64를 포함한 agnhost `2.63.0` multi-architecture index digest를 고정하고 공식 `agnhost connect`로 같은 namespace의 short Service DNS `network-smoke:8080`을 resolve해 namespace DNS search path와 ClusterIP 뒤 backend TCP 연결을 확인합니다. Cluster DNS domain을 hard-code하지 않습니다. |
| exit·재실행 | `0`은 위 postcondition과 임시 namespace 삭제까지 통과했음을 뜻합니다. 같은 exact Calico는 CRD·operator·Installation apply와 readiness 검증을 다시 수행해 안전하게 수렴하고, 이미 없는 taint는 다시 제거하지 않습니다. 이전 실패가 bootstrap marker와 함께 남긴 고정 smoke namespace는 삭제 후 다시 만들지만 marker가 다르면 변경하지 않습니다. Timeout·download·checksum·apply·smoke·cleanup 실패는 nonzero입니다. |

전체 필수 순서와 단계 간 재진입 경계는
[Kubernetes bootstrap runbook](../../../docs/operations/200-kubernetes-bootstrap-runbook.md),
이 단계의 실패 판정과 복구는
[Calico network 구성 runbook](../../../docs/operations/250-calico-network-installation-runbook.md)이
소유합니다.

## `500` 검증

Local contract test는 최소 Installation 값, artifact checksum 불일치 차단, 단일
control-plane Node·kubelet version, `command[*]` scalar를 현재 객체 `{@}`로 출력하고 missing
template key를 오류로 처리하는 Pod CIDR 조회, 빈 값·drift·중복 차단, 기존 Tigera operator
version guard, NetworkManager의 unit 부재·비활성 허용과 active·failed·전이·조회 실패 차단,
UFW의 허용·거부 상태, containerd가 읽는 CNI 설정 확장자와 숨김 file·symlink 탐색, 비설정
marker·보조 file·directory 제외, 설정 directory 부재 허용과 경로 유형·열거 실패 차단, 다른
CNI·raw-manifest Calico 차단, no-force
server-side apply와 충돌 전파, 재실행마다 Installation을 수렴시키는 동작,
Installation·실제 IPPool의 대표 정상·drift, taint의 상태 기반 제거, 고정 smoke namespace의
재실행 안전성·cleanup과 손상 방지에 필요한 상대 순서만 확인합니다.

```bash
bash -n scripts/bootstrap/kubernetes/500-install-calico.sh \
  scripts/bootstrap/kubernetes/tests/test-500-install-calico.sh
shellcheck -x -P SCRIPTDIR \
  scripts/bootstrap/kubernetes/500-install-calico.sh \
  scripts/bootstrap/kubernetes/tests/test-500-install-calico.sh
scripts/bootstrap/kubernetes/tests/test-500-install-calico.sh
```

이 검증은 실제 Calico download·API apply·Pod scheduling·CoreDNS·Service network를 증명하지
않습니다. `100`~`500`을 실제 EC2에서 순서대로 실행하고 각 postcondition을 통과해야만 빈
단일 node Kubernetes cluster 구축을 완료로 판정합니다.
