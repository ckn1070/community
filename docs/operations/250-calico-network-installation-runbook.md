# Calico network 설치 runbook

정상 control plane에 Calico를 최초 설치하고 단일 control-plane node를 일반 workload도
실행할 수 있는 빈 cluster로 완성하는 절차입니다. CNI, operator, dataplane, IPPool과 smoke
선택 근거는 [150 Kubernetes cluster 개념](../project/infrastructure/150-kubernetes-cluster.md)이
소유합니다.

## 사용 시점과 actor

- **시점**: [Control plane 초기화](240-kubernetes-control-plane-initialization-runbook.md)가
  완료되고, CNI가 없거나 이전 실행이 만든 동일 exact Calico 상태만 있을 때입니다.
- **actor**: Target EC2에서 `sudo`를 사용할 수 있는 운영자
- **기본 실행 위치**: SSH로 접속한 target EC2 terminal
- **기본 시작 directory**: 제한 없음
- **입력**: 인자와 환경변수는 없습니다.

다른 CNI 전환·삭제, eBPF, AWS IAM·Security Group, Load Balancer Controller와 application
resource는 이 단계가 변경하지 않습니다.

## 실행

```bash
sudo /home/ubuntu/car-community-scripts/bootstrap/kubernetes/500-install-calico.sh
```

Script는 control plane과 host network precondition, 기존 CNI·Calico owner를 확인하고
검증된 Calico artifact와 project Installation을 적용합니다. Calico Ready 뒤에만
control-plane taint를 제거하고, Node·CoreDNS·IPPool과 임시 workload의 DNS·ClusterIP·TCP를
검증한 뒤 smoke namespace를 삭제합니다. Exact artifact·resource·wait 계약은
[Kubernetes bootstrap README](../../scripts/bootstrap/kubernetes/README.md#500-install-calicosh-공개-계약)가
소유합니다.

Pod CIDR은 API가 반환한 Pod 목록에서 이름이 `kube-controller-manager`인 container의
`command[*]`를 순회하고, 각 문자열 scalar를 JSONPath 현재 객체 `{@}`로 출력해 읽습니다.
`--allow-missing-template-keys=false`를 사용하므로 field 누락을 빈 값으로 숨기지 않습니다.
조회 결과가 비거나 `--cluster-cidr=192.168.0.0/16`이 정확히 하나가 아니면 어떤 Calico
resource도 변경하기 전에 중단합니다.

NetworkManager는 `systemctl is-active`의 출력과 exit code 조합을 추측하지 않습니다.
`systemctl show`에서 `LoadState=not-found`·`ActiveState=inactive`인 unit 부재 또는
`LoadState=loaded`·`ActiveState=inactive`인 비활성 상태만 허용합니다. 그 밖의 상태와
property 누락·조회 실패는 network 변경 전에 중단합니다.

기존 CNI는 containerd가 `/etc/cni/net.d`에서 실제 network 설정으로 읽는 `.conf`,
`.conflist`, `.json` entry를 숨김 file과 symlink까지 확인합니다. Exact
`10-calico.conflist`는 재실행 대상으로 허용하고 다른 이름은 별도 CNI 또는 손상된
설정일 수 있으므로 mutation 전에 중단합니다. 설정 directory가 없는 상태만 빈 상태로
허용하며, 경로 유형이 다르거나 directory 열거에 실패하면 중단합니다.
확장자 없는 `.kubernetes-cni-keep`, `calico-kubeconfig` 같은 보조 file과 실제 directory는
CNI network 설정이 아니므로 차단하거나 삭제하지 않습니다. Symlink는 containerd도 설정
후보로 읽으므로 확장자가 일치하면 같은 판정에 포함합니다.

## 완료 확인

Exit code `0`과 다음 상태를 모두 확인해야 완료입니다.

- 적용한 Calico CRD가 각각 `Established` condition을 충족합니다.
- Tigera operator image가 `quay.io/tigera/operator:v1.42.3`이고 Calico가
  `Available`입니다.
- `default-ipv4-ippool`은 CIDR `192.168.0.0/16`, block size `26`,
  `vxlanMode: CrossSubnet`, `ipipMode: Never`, `natOutgoing: true`,
  `nodeSelector: all()`이며 활성 상태입니다.
- Kube-proxy가 유지되고 단일 Node와 CoreDNS가 Ready이며 control-plane taint가 없습니다.
- 고정 agnhost image로 일반 Pod scheduling, namespace DNS, ClusterIP와 backend TCP가
  성공했습니다.
- `car-community-network-smoke` namespace가 삭제됐습니다.

`spec.disabled`가 생략됐거나 `false`이면 pool은 활성입니다. `true`는 성공으로 인정하지
않습니다. Local contract test는 실제 cluster 완료를 대신하지 않습니다.

## 중단·복구와 재개

| 상황 | 보호 동작과 운영자 조치 | 재개 지점 |
| --- | --- | --- |
| Admin kubeconfig·API·Node·forwarding 불일치 | Calico mutation 전에 중단합니다. Control plane 결과를 복구합니다. | `400` 재검증 뒤 `500` |
| Controller-manager command 조회 실패·빈 결과 | Calico mutation 전에 중단합니다. 아래 read-only 명령으로 실제 Pod spec 조회가 가능한지 확인합니다. | 조회 원인 해결 뒤 `500` |
| `--cluster-cidr` 누락·중복·다른 값 | Calico mutation 전에 중단합니다. 실제 control plane의 Pod CIDR 계약을 복구합니다. | `400` 재검증 뒤 `500` |
| NetworkManager active·failed·전이·property 누락·조회 실패 | NetworkManager나 interface를 자동 변경하지 않습니다. 아래 read-only 명령으로 unit 상태를 확인합니다. | 상태·정책 정리 뒤 `500` |
| UFW 불명확 | Host firewall을 자동 변경하지 않습니다. 현재 정책과 Calico traffic 요구를 확인합니다. | 정책 정리 뒤 `500` |
| `.conf`·`.conflist`·`.json`의 다른 CNI 설정, raw Calico·다른 operator | 다른 owner의 network를 바꾸지 않습니다. 생성 주체와 migration 필요성을 확인합니다. 확장자 없는 marker·보조 file은 이 실패에 해당하지 않습니다. | 정리 뒤 `500` |
| Manifest download·SHA-256 실패 | 검증되지 않은 bytes를 apply하지 않습니다. Network와 upstream artifact를 확인합니다. | 같은 `500` 재실행 |
| Server-side apply field 충돌 | `--force-conflicts`로 우회하지 않습니다. 기존 manager와 충돌 field를 조사합니다. | 별도 이관 결정 뒤 `500` |
| CRD·operator·Calico·Node·DNS readiness timeout | 정상 control plane과 적용 resource를 유지합니다. Tigerastatus, Pod와 event를 진단합니다. | 원인 해결 뒤 `500`만 재실행 |
| Smoke namespace marker 불일치 | 알 수 없는 namespace를 삭제하지 않습니다. 생성 주체와 내부 resource를 확인합니다. | 수동 결정 뒤 `500` |
| Smoke 또는 cleanup 실패 | 완료로 판정하지 않습니다. DNS·Service·Pod 또는 삭제 원인을 해결합니다. | 같은 `500` 재실행 |
| 동시 실행 lock 실패 | 현재 process를 보호하고 lock file을 삭제하지 않습니다. | 기존 process 종료 뒤 `500` |

NetworkManager 상태는 다음 read-only 명령으로 확인합니다.

```bash
sudo systemctl show NetworkManager.service \
  --property=LoadState \
  --property=ActiveState \
  --no-pager
```

`LoadState=not-found`·`ActiveState=inactive`이면 unit이 없어 허용하고,
`LoadState=loaded`·`ActiveState=inactive`이면 설치됐지만 비활성이므로 허용합니다. 다른
조합은 NetworkManager와 Calico의 interface 관리 충돌 가능성을 별도로 판단하기 전까지
허용하지 않습니다.

Controller-manager command는 다음 read-only 명령으로 확인합니다.

```bash
sudo kubectl \
  --kubeconfig /etc/kubernetes/admin.conf \
  --request-timeout=10s \
  --allow-missing-template-keys=false \
  -n kube-system \
  get pods \
  -l component=kube-controller-manager \
  -o 'jsonpath={range .items[*].spec.containers[?(@.name=="kube-controller-manager")].command[*]}{@}{"\n"}{end}'
```

정상 결과에는 `--cluster-cidr=192.168.0.0/16`이 정확히 한 줄 있어야 합니다. 빈 결과를
Pod CIDR 없음이나 불일치로 추측하지 말고 API 응답과 조회식을 먼저 확인합니다.

Marker가 다르면 다음 read-only 명령으로 확인합니다.

```bash
sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl get namespace \
  car-community-network-smoke \
  -o 'jsonpath={.metadata.labels.app\.kubernetes\.io/managed-by}{"\n"}'
```

결과가 `car-community-bootstrap`이 아니면 삭제하지 않습니다. 이 label은 삭제 guard이며
namespace 전체 소유권 증명이 아닙니다. CRD, operator, Calico, Node, kube-proxy와 CoreDNS의
wait에는 각각 독립적인 최대 10분 timeout이 적용됩니다. Timeout은 즉시 삭제·재구축 신호가
아니며, 다른 CNI 전환·Calico rollback·upgrade·field 강제 인수는 별도 변경 작업입니다.

## 다음 단계와 관련 문서

- **Cluster 완료**: 이 단계 성공으로 일반 Pod를 실행할 수 있는 빈 단일 node cluster가
  완성됩니다.
- **다음 최초 구축**: [Repository·delivery platform bootstrap](300-repository-and-delivery-platform-bootstrap-runbook.md)
- **선택 작업**: [Admin kubeconfig 배포](510-kubernetes-admin-kubeconfig-runbook.md)
- **개념·선택 근거**: [150 Kubernetes cluster](../project/infrastructure/150-kubernetes-cluster.md)
- **구현 source**: [`500-install-calico.sh`](../../scripts/bootstrap/kubernetes/500-install-calico.sh)
