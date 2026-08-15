# Kubernetes control plane 초기화 runbook

준비된 runtime과 Kubernetes component로 단일 node control plane을 최초 초기화하는
절차입니다. Kubeadm config, cluster name, endpoint, CIDR과 kube-proxy 선택 근거는
[Kubernetes cluster 개념](../project/infrastructure/140-kubernetes-cluster.md)이 소유합니다.

## 사용 시점과 actor

- **시점**: [Kubernetes component 설치](230-kubernetes-components-installation-runbook.md)가
  완료되고 Pod·Service CIDR이 연결할 network와 겹치지 않음을 확인한 뒤입니다.
- **actor·위치**: 운영자가 SSH로 접속한 EC2 terminal에서 `sudo`로 실행합니다.
- **입력**: 인자와 환경변수는 없습니다.

이 단계는 Calico를 설치하거나 일반 사용자 home에 kubeconfig를 복사하지 않습니다.

## 실행

```bash
cd /home/ubuntu/car-community-scripts
sudo ./bootstrap/kubernetes/400-initialize-cluster.sh
```

Script는 현재 cluster marker를 `pristine`, `complete`, `partial`로 구분합니다. `pristine`에서만
private IPv4와 고정 network 계약으로 kubeadm config를 검증하고 control-plane image를
받은 뒤 `kubeadm init`을 실행합니다. `complete`이면 init을 반복하지 않고 실제 cluster를
재검증하며, `partial`이면 자동 reset·삭제하지 않고 중단합니다. 상세 state와 marker 계약은
[Kubernetes bootstrap README](../../scripts/bootstrap/kubernetes/README.md#400-initialize-clustersh-공개-계약)가
소유합니다.

Private IPv4는 외부 통신을 실행하지 않는 `ip -4 route get 1.1.1.1` route 조회 결과의
`src` 값에서 자동 확인합니다. 값을 찾지 못하거나 staging VPC `10.0.0.0/16` 밖이면
cluster mutation 전에 중단합니다.

Uploaded kubeadm 설정은 기대 설정과 함께 현재 API 기본값으로 정규화한 뒤
`ClusterConfiguration` 문서만 비교합니다. `kubeadm config migrate`가 별도로 생성하는
`InitConfiguration`의 임의 bootstrap token은 cluster-wide 설정이 아니며 실행마다 달라져
비교에서 제외합니다. `ClusterConfiguration`이 없거나 그 내용이 다르면 중단합니다.

## 완료 확인

Exit code `0`과 다음 상태를 모두 확인해야 완료입니다.

- Uploaded ClusterConfiguration의 Kubernetes version, cluster 이름, Pod·Service CIDR와
  DNS domain이 고정 계약과 같습니다.
- Admin kubeconfig endpoint가 현재 private IPv4의 `6443`을 사용하고 API `/readyz`가
  `ok`입니다.
- Kubelet, API server, controller manager, scheduler, local etcd와 kube-proxy가 정상입니다.
- Node는 하나이고 control-plane role, kubelet `v1.36.2`와 현재 private InternalIP를
  사용합니다.
- CoreDNS resource가 존재합니다. CNI 전 Node `NotReady`와 CoreDNS `Pending`은 이 단계의
  실패가 아닙니다.

## 중단·복구와 재개

| 상황 | 보호 동작과 운영자 조치 | 재개 지점 |
| --- | --- | --- |
| Exact version·private IPv4·route·CIDR 계약 불일치 | Cluster mutation 전에 중단합니다. Tool version, ENI·route와 network 충돌을 확인합니다. | 수정 뒤 같은 `400` |
| Config validation 또는 image pull 실패 | Cluster state를 만들기 전에 실패합니다. Registry, CRI 또는 config 원인을 해결합니다. | 같은 `400` 재실행 |
| `complete` cluster | Init·image pull을 반복하지 않고 현재 cluster만 검증합니다. | 성공하면 `500` 단계 진행 |
| `admin.conf` 없이 PKI·manifest·kubelet·etcd marker가 남은 `partial` | File 삭제와 `kubeadm reset`을 자동 실행하지 않습니다. PKI·etcd·data 손실 영향을 조사해 별도 승인을 받습니다. | 승인된 별도 복구 뒤 재개 |
| API 장애 또는 uploaded config·endpoint 불일치 | 완료 후보를 덮어쓰거나 재초기화하지 않습니다. 실제 cluster와 data 영향을 진단합니다. | 별도 복구 뒤 `400` 재검증 |
| 동시 실행 lock 실패 | 현재 초기화 process를 보호하며 lock file을 삭제하지 않습니다. | 기존 process 종료 뒤 `400` |

이 runbook은 data 손실 가능성이 있는 일반 `kubeadm reset` 명령을 제공하지 않습니다.
구축 취소도 [Kubernetes bootstrap 취소](500-kubernetes-bootstrap-cancellation-runbook.md)의
제한을 먼저 확인합니다.

## 다음 단계와 관련 문서

- **다음 실행**: [Calico network 설치](250-calico-network-installation-runbook.md)
- **선택 작업**: 일반 사용자 cluster-admin credential이 필요할 때만
  [Admin kubeconfig 배포](510-kubernetes-admin-kubeconfig-runbook.md)
- **개념·선택 근거**: [Kubernetes cluster](../project/infrastructure/140-kubernetes-cluster.md)
- **구현 source**: [`400-initialize-cluster.sh`](../../scripts/bootstrap/kubernetes/400-initialize-cluster.sh)
