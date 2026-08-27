# Kubernetes bootstrap runbook

Ubuntu 24.04 ARM64 host를 kubeadm 기반 단일 node Kubernetes cluster로 구축하는 최초
운영 진입점입니다. 이 문서는 필수 실행 순서와 단계 handoff를 소유하고, 각 단계의
명령·성공 결과·복구는 연결된 단계별 runbook이 소유합니다. Cluster가 작동하는 방식과
선택 근거는 [150 Kubernetes cluster 개념](../project/infrastructure/150-kubernetes-cluster.md)이
소유합니다.

AWS resource 생성과 EC2 접속은
[AWS 최초 구축 runbook](100-aws-initial-provisioning-runbook.md)이 선행합니다.
실제 EC2에서 필수 다섯 단계를 모두 성공해야 일반 workload를 배치할 수 있는 빈 cluster
구축이 끝납니다.

## 사용 시점과 actor

공통 실행 artifact가 staging Ubuntu EC2에 준비되고 운영자가 private key 기반 SSH로
접속할 수 있을 때 시작합니다. Bootstrap script는 AWS API나 instance metadata를 호출하지
않습니다.

## 공통 전제와 고정 계약

| 항목 | 현재 계약 |
| --- | --- |
| 운영체제·architecture | Ubuntu Server `24.04`, `arm64` |
| 권한 | 운영자는 `sudo`를 사용할 수 있음 |
| Outbound network | Ubuntu APT, `pkgs.k8s.io`, `raw.githubusercontent.com`, `registry.k8s.io`, `quay.io` 접근 가능 |
| Kubernetes | Semantic version `1.36.2`, Debian package `1.36.2-2.1` |
| Container runtime | Ubuntu containerd package `2.2.1-0ubuntu1~24.04.3` |
| CNI | Calico `3.32.1`, 일반 iptables dataplane과 kube-proxy 유지 |
| VPC·cluster network | VPC `10.0.0.0/16`, Pod `192.168.0.0/16`, Service `10.96.0.0/12`, DNS domain `cluster.local` |
| Cluster 형태 | Control plane과 workload를 함께 실행하는 단일 node IPv4 cluster |
| 기본 실행 위치 | SSH로 접속한 target EC2 terminal |
| EC2 실행 사용자 | `ubuntu` |
| 기본 시작 directory | `/home/ubuntu/car-community-scripts` |
| 동시 변경 | 다른 운영자가 같은 host의 package, swap, sysctl, firewall이나 cluster를 동시에 변경하지 않음 |

Version과 CIDR은 실행 날짜의 최신값으로 자동 교체하지 않습니다. Pod·Service CIDR은 VPC와
겹치지 않아야 하며, peering·VPN·조직 network와 충돌할 가능성이 있으면 cluster 초기화
전에 다시 결정해야 합니다. IPv6·dual-stack은 현재 범위가 아닙니다.

이 표는 사람이 실행 전에 확인할 고정 입력입니다. Version 조합, network와 단일 node
선택의 근거·대안·재검토 조건은
[150 Kubernetes cluster 개념](../project/infrastructure/150-kubernetes-cluster.md)에서 확인합니다.

## 실행 artifact 선행 조건

Kubernetes 첫 단계를 실행하기 전에
[150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)을 완료해
`/home/ubuntu/car-community-scripts/bootstrap/kubernetes/100-prepare-host.sh`부터
`500-install-calico.sh`까지 고정 경로와 mode로 준비합니다. 이 문서는 archive를 다시
소유하거나 현재 directory를 전제로 하지 않습니다.

## 필수 실행 순서

다음 순서대로 한 단계씩 실행합니다. 각 단계가 nonzero로 종료되거나 해당 문서의 성공
결과를 충족하지 못하면 다음 단계로 진행하지 않습니다.

| 순서 | 실행 절차 | 입력 상태 | 다음 단계에 넘기는 결과 |
| --- | --- | --- | --- |
| 1 | [Host 준비](210-kubernetes-host-preparation-runbook.md) — `100-prepare-host.sh` | `/home/ubuntu/car-community-scripts`에 검증·배치된 script | Package 갱신, swap 비활성화, IPv4 forwarding, 재부팅 완료 상태 |
| 2 | [Containerd 설치](220-containerd-installation-runbook.md) — `200-install-containerd.sh` | 재부팅 필요가 없는 준비된 host | Enabled·active containerd와 CRI socket |
| 3 | [Kubernetes component 설치](230-kubernetes-components-installation-runbook.md) — `300-install-kubernetes-components.sh` | 정상 containerd runtime | Exact `kubelet`·`kubeadm`·`kubectl`과 official repository |
| 4 | [Control plane 초기화](240-kubernetes-control-plane-initialization-runbook.md) — `400-initialize-cluster.sh` | Runtime과 Kubernetes component 준비 완료 | API server·PKI·kubeconfig·etcd·control-plane·kube-proxy, CNI 전 중간 상태 |
| 5 | [Calico network 구성](250-calico-network-installation-runbook.md) — `500-install-calico.sh` | 정상 control plane과 CNI가 없는 cluster | Calico, Ready node·CoreDNS와 일반 workload network |

`100`이 재부팅 필요를 보고하면 EC2를 재부팅·재접속하고 `100`을 다시 실행합니다. 이
재검증이 성공하기 전에는 `200`을 실행하지 않습니다.

Control plane 초기화와 Calico 설치는 실패 후 재시작 지점이 달라 분리되어 있습니다.
`kubeadm init`의 부분 상태는 자동 반복하거나 reset하면 안 되지만, 정상 control plane을
유지한 채 Calico download·apply 실패는 `500`만 다시 실행할 수 있습니다.

## 선택 절차와 예외 경로

- 일반 사용자가 `kubectl`을 사용해야 할 때만
  [Admin kubeconfig 배포](510-kubernetes-admin-kubeconfig-runbook.md)를 실행합니다. 필수
  bootstrap 단계가 아니며 `400`과 `500`은 root의 `/etc/kubernetes/admin.conf`를 직접
  사용합니다.
- 정상 오류를 해결해 같은 단계로 재진입하는 대신 구축 자체를 의도적으로 되돌릴 때만
  [Kubernetes bootstrap 취소](500-kubernetes-bootstrap-cancellation-runbook.md)를
  사용합니다. 이 문서는 현재 허용된 backup 복원 경계만 제공하며 임의의
  `kubeadm reset`이나 data 삭제 절차를 만들지 않습니다.

## 전체 완료 결과

필수 다섯 단계가 실제 EC2에서 모두 성공하면 다음 상태입니다.

- Kubernetes `1.36.2` API server, local etcd와 control-plane component가 동작합니다.
- Calico와 kube-proxy가 동작하고 단일 node와 CoreDNS가 Ready입니다.
- Control-plane taint가 제거되어 일반 Pod를 배치할 수 있습니다.
- 임시 일반 workload로 namespace DNS, ClusterIP와 backend TCP 통신을 검증했습니다.
- 임시 smoke namespace가 삭제되어 애플리케이션이 없는 빈 cluster로 남습니다.

Repository의 script·contract test·Git 상태는 실제 EC2 완료 증거를 대신하지 않습니다.
실제 host 실행 전에는 문서와 local source가 정합하다는 것만 확인할 수 있습니다.

## 후속 범위

이 bootstrap 완료는 서비스 infrastructure 전체 완료가 아닙니다. 다음은 별도 절차입니다.

- Private ECR image 공급과 [exact digest pull 검증](410-private-ecr-image-pull-verification-runbook.md)
- Application bootstrap·manifest의 RDS role, ConfigMap·Secret과 workload 적용
- AWS Load Balancer Controller, instance·NodePort target, Ingress와 ALB
- `node.kubernetes.io/exclude-from-external-load-balancers` label 제거·검증
- HTTPS·domain·DNS
- [Repository·delivery platform bootstrap](300-repository-and-delivery-platform-bootstrap-runbook.md)의
  Core 설치·private repository 연결과 `deploy/` application sync

다중 AWS node로 확장하기 전에는 `VXLANCrossSubnet`과 EC2 source/destination check 또는
전체 `VXLAN` 전환을 다시 결정해야 합니다. Control plane HA·node 교체 전에는 stable
control-plane endpoint를 먼저 설계해야 합니다.

## 관련 문서

- **AWS 선행 절차**: [AWS 최초 구축 runbook](100-aws-initial-provisioning-runbook.md)
- **시스템 개념**: [100 현재 infrastructure overview](../project/infrastructure/100-infrastructure-overview.md)
- **Script 생애주기 목차**: [Repository script 목차](../../scripts/README.md)
- **Repository·delivery platform 후속 절차**: [Repository·delivery platform bootstrap](300-repository-and-delivery-platform-bootstrap-runbook.md)
- **전체 구축의 현재 위치**: [Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)
- **운영 문서 탐색**: [운영 문서 목차](000-index.md)
