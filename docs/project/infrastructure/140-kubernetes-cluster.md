# Kubernetes cluster

이 문서는 Ubuntu EC2가 Kubernetes control plane·worker를 함께 실행하는 빈 cluster가 되기까지
구성요소, 고정 계약, 단계별 상태 전이, 선택 이유와 재검토 조건을 소유합니다. 사람이 script를
전달하고 실행·재개하는 절차는
[Kubernetes bootstrap runbook](../../operations/200-kubernetes-bootstrap-runbook.md)과 단계별
runbook이 소유합니다.

## 목표 상태와 고정 계약

| 항목 | 현재 계약 | 의미 |
| --- | --- | --- |
| Host | Ubuntu Server `24.04`, ARM64 | 현재 EC2 image·architecture와 일치합니다. |
| Container runtime | Ubuntu containerd `2.2.1-0ubuntu1~24.04.3` | CRI v1·systemd cgroup으로 kubelet과 연결합니다. |
| Kubernetes | Semantic `1.36.2`, Debian package `1.36.2-2.1` | Script·test·문서가 같은 exact patch를 재현합니다. |
| Cluster 이름 | `stg-car-community-k8s` | EC2 tag가 아니라 환경·project·cluster 역할을 표현합니다. |
| API | Node primary private IPv4, `6443` | 단일 node VPC 내부 endpoint이며 stable LB·DNS는 없습니다. |
| Pod network | `192.168.0.0/16` | VPC와 Service CIDR에서 분리합니다. |
| Service network | `10.96.0.0/12` | ClusterIP virtual address 범위입니다. |
| DNS domain | `cluster.local` | Kubernetes service discovery domain입니다. |
| CNI | Calico `3.32.1`, Tigera operator | Pod network lifecycle을 관리합니다. |
| Dataplane | Iptables, kube-proxy 유지 | 첫 구축에서 eBPF·proxy replacement를 함께 도입하지 않습니다. |
| 형태 | IPv4 단일 control-plane node, workload scheduling 허용 | Production HA가 아닌 staging cluster입니다. |

Version과 CIDR을 실행일 latest로 자동 교체하지 않습니다. Pod·Service CIDR은 VPC, peering,
VPN과 조직 network에 겹치지 않아야 하며 `kubeadm init` 전까지만 비교적 안전하게 바꿀 수
있습니다. IPv6·dual-stack은 현재 범위가 아닙니다.

## 상태 전이와 단계 경계

```text
100 host 준비
  -> 200 containerd runtime
  -> 300 kubelet·kubeadm·kubectl
  -> 400 control plane (CNI 전 중간 상태)
  -> 500 Calico + workload network
  -> Ready 빈 cluster
```

| 단계 | 시작 상태 | 만드는 상태 | 의도적으로 제외하는 것 |
| --- | --- | --- | --- |
| `100` | 접속 가능한 Ubuntu host | Package 갱신, swap off, IPv4 forwarding, 재부팅 경계 | Runtime·Kubernetes·CNI·module 추측 |
| `200` | 재부팅이 끝난 host | Exact containerd, overlayfs, CRI socket·service | Kubernetes package·cluster state |
| `300` | 정상 CRI runtime | Official repository와 exact kubelet·kubeadm·kubectl | `kubeadm init`·CNI |
| `400` | Toolchain 준비 host | PKI·kubeconfig·API·etcd·control plane·kube-proxy | CNI·taint 제거·user kubeconfig |
| `500` | API는 정상, CNI 없는 cluster | Calico·Ready node·CoreDNS·일반 workload network | AWS LB·application·다른 CNI migration |

`400`과 `500`은 명령 수 때문이 아니라 실패 뒤 재시작 지점이 달라 분리합니다. 부분
`kubeadm init`은 자동 반복·reset할 수 없지만, 정상 control plane 뒤 Calico download·apply가
실패하면 control plane을 유지하고 `500`만 다시 실행할 수 있습니다.

## 실행 artifact 전달 경계

현재 첫 EC2 구축과 곧 이어질 검증·운영에 필요한 script 열두 개를 생애주기 경로를 유지한
하나의 `tar.gz`와 SHA-256 file로 SSH `scp` 한 번에 전달합니다.

- Kubernetes bootstrap 다섯 개
- ECR credential provider bootstrap 한 개와 pull verify 한 개
- Argo CD Core·repository 연결 bootstrap 두 개
- Application database·Kubernetes prerequisite bootstrap 두 개
- Argo repository credential rotation operation 한 개

Repository 전체 clone, application source·Dockerfile·문서·local test와 credential은 EC2에
보내지 않습니다. 한 node에 필요한 실행 file이 작아 별도 S3 bucket·SSM 전달 체계를 만들지
않고 기존 SSH 인증·암호화 경로를 사용합니다. Checksum은 local archive와 EC2 수신 byte의
동일성만 증명하며 code signature나 배포자 identity 증명은 아닙니다. 다중 node 배포·중앙
감사 요구가 생기면 S3·Systems Manager 기반 전달을 재검토합니다.

한 번 전달한다고 실행 dependency가 합쳐지는 것은 아닙니다. Kubernetes `100`~`500`을 먼저
완료하고, ECR IAM·image 준비 뒤 provider·pull, DB role과 Git 밖 application prerequisite,
실제 digest overlay·GitHub App 준비 뒤 Argo 연결을 실행합니다.
Credential rotation은 최초 연결 뒤에만 사용합니다.

## `100` Host 준비의 원리와 선택

Host 준비는 mutation 전에 root·OS·필수 command·clock·UFW·systemd swap unit을 확인하고,
APT package 갱신, 현재·재부팅 후 swap 비활성화, project 소유 sysctl로 IPv4 forwarding을
적용한 뒤 effective state를 검증합니다.

| 항목 | 현재 선택 | 이유와 재검토 조건 |
| --- | --- | --- |
| Package 관리 | Ubuntu command·APT, Aqua 미도입 | OS·kernel·systemd는 APT 책임입니다. 독립 CLI version 통일이 필요할 때 Aqua를 별도 평가합니다. |
| Package 갱신 | `apt-get update` 후 upgrade | 초기 node를 오래된 security·bugfix 상태로 고정하지 않습니다. Snapshot·변경 window 정책이 생기면 교체합니다. |
| Swap | 현재 swap과 fstab 활성 entry 비활성 | Kubelet 기본 거부 조건을 없애고 memory 부족을 swap으로 숨기지 않습니다. Swap 운영 시 kubelet 설정·관측을 함께 설계합니다. |
| Systemd swap unit | Enabled unit이 있으면 중단 | 생성 주체를 모르는 unit을 자동 disable하지 않습니다. |
| IPv4 forwarding | 현재 kernel·재부팅 후 모두 `1` | Pod traffic 전달에 필수인 조건으로 현재 선택 가능한 tuning이 아닙니다. IPv4 cluster가 아니거나 CNI 요구가 바뀔 때 재검토합니다. |
| Sysctl owner | `/etc/sysctl.d/99-car-community-kubernetes.conf` | 다른 sysctl source를 통째로 고치지 않고 bootstrap 값만 분리합니다. 조직 관리 도구가 생기면 이관합니다. |
| Kernel module | 이 단계에서 강제 load 없음 | `overlay`는 직접 consumer인 containerd 단계가 소유하고 CNI는 실제 network 검증으로 판정합니다. |
| Clock | 현재 provider 유지, `NTPSynchronized=yes` 확인 | Certificate와 event·log 순서가 host time에 의존합니다. 실패 시 provider·network를 진단합니다. |
| UFW | 미설치 또는 inactive만 허용 | 임의로 끄거나 port를 추측해 열지 않습니다. 운영 시 kubeadm·kubelet·CNI port/source를 별도 확정합니다. |
| Reboot | Script가 필요를 보고, 사람이 수행 | SSH를 예고 없이 종료하지 않습니다. 무인 provisioning이면 reconnect·health orchestration을 별도 설계합니다. |

UFW 미설치는 모든 nftables·iptables rule 부재를 뜻하지 않습니다. APT 재실행과 update는
상태를 훼손하지 않아 반복 가능하며, 형식적으로 모든 command를 생략하는 멱등성은 목표가
아닙니다.

## `200` Containerd의 원리와 선택

| 항목 | 현재 선택 | 이유와 재검토 조건 |
| --- | --- | --- |
| Runtime | containerd | Kubelet이 CRI v1로 직접 사용하며 Docker Engine adapter를 추가하지 않습니다. CRI-O가 조직 표준일 때 재평가합니다. |
| Source·version | Ubuntu noble-updates exact `2.2.1-0ubuntu1~24.04.3` | ARM64 package·runc·systemd unit을 Ubuntu가 함께 관리합니다. Upstream 공급망을 직접 관리할 수 있을 때 2.3 LTS 등을 재평가합니다. |
| Drift 통제 | Exact install 뒤 hold | Package 부재를 latest로 대체하지 않고 security update도 호환·복구 확인 뒤 명시 변경합니다. |
| Cgroup | `SystemdCgroup=true` | Ubuntu systemd·cgroup v2와 kubelet의 관리자 불일치를 막습니다. Init·cgroup이 바뀌면 재검토합니다. |
| Module | `overlay`만 현재·영구 load | Containerd snapshotter의 직접 요구만 소유하고 CNI를 추측해 `br_netfilter`를 추가하지 않습니다. Snapshotter 변경 시 공식 요구로 교체합니다. |
| Config | 관리 표식 있는 `/etc/containerd/config.toml`, version 3 최소값 | 다른 owner config를 덮지 않고 미지정 option은 고정 version 기본값을 사용합니다. Registry·snapshotter 변경은 migration으로 다룹니다. |

Docker Engine·CRI-O·`containerd.io`나 다른 version, 관리 표식 없는 config는 자동 제거·인수하지
않습니다. Service가 active라는 사실만이 아니라 CRI images·runtime plugin과 socket까지
검증합니다. 초기 bootstrap에서 exact package 요청·APT index·service restart는 반복돼도
상태를 훼손하지 않으므로 marker로 생략하지 않습니다.

## `300` Kubernetes component의 원리와 선택

| 항목 | 현재 선택 | 이유와 재검토 조건 |
| --- | --- | --- |
| Version | Kubernetes `1.36.2` | Script·test·문서가 같은 patch를 재현합니다. 새 patch는 release note·containerd 호환·host 검증과 함께 갱신합니다. |
| APT source | Official `pkgs.k8s.io/core:/stable:/v1.36/deb/` | Minor별 repository와 전용 keyring으로 1.36 후보만 둡니다. 조직 mirror는 provenance·sync·owner 확정 뒤 사용합니다. |
| Key | Official `Release.key`를 전용 keyring으로 dearmor | 임시 결과 검증 뒤 원자 게시하며 fingerprint hard-code로 official rotation을 막지 않습니다. |
| Dependency | `ca-certificates`, `curl`, `gpg` | HTTPS·dearmor 최소 도구입니다. Ubuntu 24.04 APT가 HTTPS를 직접 지원하므로 dummy `apt-transport-https`를 추가하지 않습니다. |
| Package | Kubelet·kubeadm·kubectl 모두 `1.36.2-2.1`·hold | 세 component artifact를 일치시키고 일부 손상도 같은 exact version으로 복구합니다. Upgrade는 cluster 순서와 함께 다룹니다. |
| `kubernetes-cni` | Kubelet dependency 설치는 허용, CNI add-on 구성은 안 함 | Package dependency와 Calico network 선택을 분리합니다. |
| Pre-init kubelet | Enable만 하고 active를 요구하지 않음 | Kubeadm config 전에는 inactive·failed·restart일 수 있으며 init 뒤 `400`이 실제 상태를 검증합니다. |

다른 package version·repository owner를 자동 upgrade·downgrade·덮어쓰지 않습니다. 이 단계가
cluster를 만들지는 않지만 기존 cluster 부재를 자체 증명하지도 않으며 호출 순서가 최초
구축 전제를 소유합니다.

## `400` Control plane의 원리와 상태 모델

Kubeadm v1beta4 config에 primary private IPv4, CRI socket, Pod·Service CIDR와 DNS domain을
모아 validate한 뒤 image를 먼저 pull하고 `kubeadm init`을 수행합니다. Bootstrap token은
출력하지 않고 config·credential 임시 file을 보호합니다.

| 항목 | 현재 선택 | 이유와 재검토 조건 |
| --- | --- | --- |
| Config API | `kubeadm.k8s.io/v1beta4` file | 입력을 검증하고 uploaded config와 대조합니다. Kubeadm version 변경 시 migration합니다. |
| API address | VPC 안 primary private IPv4·`6443` | AWS API·metadata 대신 host route가 보여 주는 주소를 사용합니다. ENI가 여러 개면 자동 선택을 재설계합니다. |
| Stable endpoint | 없음 | 소유하지 않는 DNS·LB를 추측하지 않습니다. HA·node 교체 전에 먼저 설계합니다. |
| Cluster network | Pod `/16`, Service `/12`, `cluster.local` | Node·Pod·Service 주소를 분리하고 Calico pool과 일치시킵니다. 충돌이면 init 전에 변경합니다. |
| CRI socket | `unix:///run/containerd/containerd.sock` 명시 | Runtime ambiguity를 없앱니다. |
| Service proxy | Kube-proxy 유지 | 첫 구축에서 eBPF와 proxy replacement를 동시에 도입하지 않습니다. 성능 계측 뒤 Calico upgrade로 평가합니다. |
| User kubeconfig | `/etc/kubernetes/admin.conf`만 사용 | Cluster-admin을 임의 user home에 복사하지 않습니다. 필요할 때 별도 access lifecycle을 씁니다. |

Cluster marker는 세 상태로 해석합니다.

| 상태 | 의미 | 허용 행동 |
| --- | --- | --- |
| `pristine` | Kubeadm state marker가 없음 | Config·image 검증 뒤 최초 init 허용 |
| `complete` | `admin.conf`와 실제 API·component·uploaded config가 정상 | Init·image pull을 반복하지 않고 postcondition만 재검증 |
| `partial` | `admin.conf` 없이 PKI·manifest·kubelet·etcd marker 등이 남음 | 자동 file 삭제·reset·init 금지, data 영향 조사와 별도 승인 필요 |

CNI 전 Node `NotReady`와 CoreDNS `Pending`은 control plane 실패가 아니라 다음 단계가 닫을
예상 중간 상태입니다. API 장애나 uploaded config·endpoint drift는 완료로 덮어쓰거나
재초기화하지 않습니다.

## `500` Calico network의 원리와 선택

| 항목 | 현재 선택 | 이유와 재검토 조건 |
| --- | --- | --- |
| CNI·설치 | Calico `3.32.1`, Tigera operator | Kubernetes 1.36·ARM64 지원 조합이며 CRD·operator·Installation lifecycle을 분리합니다. |
| Artifact trust | Official tag raw manifest와 repository 고정 SHA-256 | Mutable latest가 아니라 검토한 byte를 사용합니다. Upstream provenance·checksum이 제공되면 교체합니다. |
| Dataplane | Iptables, `kubeProxyManagement: Disabled` | Kube-proxy를 유지하고 eBPF를 동시에 도입하지 않습니다. 성능 필요를 계측한 뒤 별도 upgrade합니다. |
| IPPool | `192.168.0.0/16`, block `/26`, `VXLANCrossSubnet`, IPIP Never, NAT outgoing, `NodeInternalIP` | VPC private node 주소를 쓰고 다른 subnet traffic만 VXLAN encapsulation합니다. |
| Optional UI·API | Calico APIServer·Goldmane·Whisker 없음 | 2 GiB node에서 필수 아닌 memory·운영 부담을 추가하지 않습니다. Policy·flow 관측 필요 시 계측합니다. |
| Existing CNI | 다른 CNI·raw Calico·다른 operator를 mutation 전에 거부 | Migration·upgrade를 자동화하지 않고 workload 영향·rollback을 별도 결정합니다. |
| Apply ownership | Field manager `car-community-bootstrap`, `--force-conflicts` 없음 | 같은 목표로 수렴하되 다른 manager field를 강제 인수하지 않습니다. |
| Scheduling | Calico Ready 뒤 control-plane taint 제거 | Network 전 일반 Pod scheduling을 막습니다. Worker 분리 시 taint·placement를 재설계합니다. |
| Smoke | Agnhost `2.63.0` 고정 digest, short service DNS·ClusterIP·TCP | System Pod 상태가 아니라 실제 scheduler·DNS·Service·backend 경로를 확인합니다. |
| External LB label | 변경하지 않음 | CNI와 AWS Load Balancer Controller target 책임을 분리합니다. |
| NetworkManager·UFW | 부재·inactive만 허용 | Interface·firewall 상태를 자동 인수·보정하지 않습니다. |

고정 artifact hash는 CRD `192f8b2d...ed60f71`, operator
`f18e0737...a8ab07f`이며 upstream 발표 checksum이 아니라 repository가 검토한 exact raw
byte의 경계입니다. Actual operator image는 `quay.io/tigera/operator:v1.42.3`입니다.

다중 AWS node를 같은 subnet에 추가할 때 `VXLANCrossSubnet`은 같은-subnet traffic을
encapsulate하지 않으므로 EC2 source/destination check와 native routing 영향을 검토하거나
전체 VXLAN으로 바꿔야 합니다. Server-side apply 충돌을 force하지 않고 timeout 때도 정상
control plane과 적용 resource를 삭제하지 않은 채 원인 해결 후 `500`만 재실행합니다.

## 완료 상태와 아직 아닌 것

`100`~`500`의 live postcondition이 모두 성공하면 다음을 가진 빈 cluster입니다.

- Kubernetes API server, local etcd와 control-plane component
- Containerd·kubelet·kube-proxy
- Calico Pod network, Ready 단일 node와 CoreDNS
- Control-plane taint 제거로 일반 workload 배치 가능
- 임시 Pod의 namespace DNS·ClusterIP·backend TCP 성공 뒤 smoke namespace 정리

Source·contract test·Git 상태는 EC2 live 완료 증거가 아닙니다. 빈 cluster 완료는 application
Deployment·Service·Secret, RDS application 연결, Load Balancer Controller·Ingress·ALB,
HTTPS·DNS와 GitOps sync 완료도 아닙니다.

## 관련 운영 절차와 공식 근거

- **전체 최초 구축 순서·artifact 전달**:
  [Kubernetes bootstrap runbook](../../operations/200-kubernetes-bootstrap-runbook.md)
- **Host 준비**: [Host 준비 runbook](../../operations/210-kubernetes-host-preparation-runbook.md)
- **Containerd**: [Containerd 설치 runbook](../../operations/220-containerd-installation-runbook.md)
- **Kubernetes component**:
  [Component 설치 runbook](../../operations/230-kubernetes-components-installation-runbook.md)
- **Control plane**:
  [Control plane 초기화 runbook](../../operations/240-kubernetes-control-plane-initialization-runbook.md)
- **Calico**: [Calico 설치 runbook](../../operations/250-calico-network-installation-runbook.md)
- **취소·recovery 원리**: [Kubernetes bootstrap 취소](310-kubernetes-bootstrap-cancellation.md)
- **Admin access**: [Kubernetes admin access](320-kubernetes-admin-access.md)
- **Runtime·API·workload 진단**:
  [140 Linux node service 진단](../../operations/diagnostics/140-systemd-journal-and-node-services.md),
  [160 Kubernetes control plane 진단](../../operations/diagnostics/160-kubernetes-api-control-plane-and-node.md),
  [170 Kubernetes workload 진단](../../operations/diagnostics/170-kubernetes-workload-network-events-and-logs.md)
- [Ubuntu time synchronization](https://documentation.ubuntu.com/server/how-to/networking/timedatectl-and-timesyncd/)
- [Ubuntu UFW firewall](https://documentation.ubuntu.com/server/how-to/security/firewalls/)
- [Containerd release와 Kubernetes 호환표](https://github.com/containerd/containerd/blob/main/RELEASES.md#kubernetes-support)
- [Containerd 2.2.1 CRI 설정](https://github.com/containerd/containerd/blob/v2.2.1/docs/cri/config.md)
- [Ubuntu 24.04 containerd package](https://packages.ubuntu.com/noble-updates/containerd)
- [Kubernetes 1.36 package repository](https://pkgs.k8s.io/core:/stable:/v1.36/deb/)
- [Kubernetes release와 지원 version](https://kubernetes.io/releases/)
- [Kubernetes container runtime 준비](https://kubernetes.io/docs/setup/production-environment/container-runtimes/)
- [Kubernetes swap](https://kubernetes.io/docs/concepts/cluster-administration/swap-memory-management/)
- [Kubeadm 설치](https://kubernetes.io/docs/setup/production-environment/tools/kubeadm/install-kubeadm/)
- [Kubeadm cluster 생성](https://kubernetes.io/docs/setup/production-environment/tools/kubeadm/create-cluster-kubeadm/)
- [Kubeadm init](https://kubernetes.io/docs/reference/setup-tools/kubeadm/kubeadm-init/)
- [Kubeadm v1beta4 API](https://kubernetes.io/docs/reference/config-api/kubeadm-config.v1beta4/)
- [Calico requirements](https://docs.tigera.io/calico/latest/getting-started/kubernetes/requirements)
- [Calico operator](https://docs.tigera.io/calico/latest/getting-started/kubernetes/quickstart)
- [Calico single-host cluster 구성](https://docs.tigera.io/calico/latest/getting-started/kubernetes/hardway/standing-up-kubernetes#install-calico)
- [Calico Installation API](https://docs.tigera.io/calico/latest/reference/installation/api)
- [Calico IPPool](https://docs.tigera.io/calico/latest/reference/resources/ippool)
- [Calico overlay](https://docs.tigera.io/calico/latest/networking/configuring/vxlan-ipip)
- [Calico AWS](https://docs.tigera.io/calico/latest/reference/public-cloud/aws)
- [Calico upgrade](https://docs.tigera.io/calico/latest/operations/upgrading/kubernetes-upgrade)
- [Agnhost `connect` source](https://github.com/kubernetes/kubernetes/blob/v1.36.2/test/images/agnhost/connect/connect.go)
- [Agnhost `2.63.0` image index](https://registry.k8s.io/v2/e2e-test-images/agnhost/manifests/2.63.0)
- [Kubernetes Server-Side Apply](https://kubernetes.io/docs/reference/using-api/server-side-apply/)
