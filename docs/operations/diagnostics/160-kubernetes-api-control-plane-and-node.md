# Kubernetes control plane 진단: API·static Pod·etcd·Node

Kubernetes API가 요청을 받을 수 있는지, kubeadm control plane과 etcd가 동작하는지, 각 Node가
workload를 받을 수 있는지를 위에서 아래로 구분합니다. `NotReady`와 timeout은 원인이 아니라
결과이므로 health endpoint, component, Node condition을 연결해서 봅니다.

## 상단 수동 확장 조회

[000 Platform 진단 공통 실행 계약](000-index.md#공통-실행-계약)에 따라 아래 Bash block
전체를 한 번에 실행합니다.

```bash
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s get --raw='/livez?verbose'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s get --raw='/readyz?verbose'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n kube-system \
  get pods -l tier=control-plane -o wide
sudo -n ls -l /etc/kubernetes/manifests
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s get nodes -o wide
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s get nodes \
  -o 'custom-columns=NAME:.metadata.name,READY:.status.conditions[?(@.type=="Ready")].status,MEMORY:.status.conditions[?(@.type=="MemoryPressure")].status,DISK:.status.conditions[?(@.type=="DiskPressure")].status,PID:.status.conditions[?(@.type=="PIDPressure")].status,NETWORK:.status.conditions[?(@.type=="NetworkUnavailable")].status'
```

API가 완전히 불통이면 `kubectl` 실패 자체가 증거입니다. 그때 cluster를 reset하지 않고
[140 Linux node service 진단](140-systemd-journal-and-node-services.md)의 kubelet·containerd와
local API server·etcd log로 내려갑니다.

## 해석 순서

1. `/livez`로 API process 생존을 확인합니다.
2. `/readyz`의 개별 check에서 request 수용을 막는 dependency를 찾습니다.
3. Control-plane·etcd Pod와 local static Pod manifest를 연결합니다.
4. 전체 Node를 비교하고 문제 Node의 condition·reason으로 좁힙니다.
5. API가 없으면 Linux listener·service·local container log로 전환합니다.

## `kubectl`: API health·component·Node 조회 command

`kubectl`은 Kubernetes API resource와 raw endpoint를 조회하는 client입니다. 이 문서에서는
상태를 바꾸지 않는 `get`만 사용합니다.

### `get --raw`: API health endpoint 조회

#### Endpoint·option

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `/livez` | API process가 진행 가능한지 확인 | Process restart가 필요할 정도로 멈췄는가 |
| `/readyz` | 현재 request를 받을 준비가 됐는지 확인 | Etcd·informer 같은 dependency가 준비됐는가 |
| `?verbose` | Named health check를 각각 출력 | 어느 check가 실패했는가 |
| `--request-timeout=10s` | Client request 전체 제한 | Terminal이 무기한 기다리지 않는가 |

#### 출력 field

| 출력 | 뜻 | 이상 신호 |
| --- | --- | --- |
| Named check `ok` | 해당 dependency health check 통과 | 특정 check가 non-ok·누락 |
| HTTP·client error | API 요청 단계 실패 | Timeout·TLS·connection refused |

HTTP response를 받은 것과 모든 check가 `ok`인 것은 다릅니다. TLS timeout이면 listener,
API 부하, certificate·handshake, etcd를 다른 증거로 분리합니다.

### `get pods`: control-plane component 조회

#### Resource·option

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-n kube-system` | System component namespace | Application Pod와 구분했는가 |
| `-l tier=control-plane` | Control-plane Pod label 선택 | API·controller·scheduler·etcd 중 무엇이 실패했는가 |
| `-o wide` | Node·Pod IP 등 추가 column | 특정 Node·주소에 한정되는가 |

#### 출력 field

| Column | 뜻 | 이상 신호 |
| --- | --- | --- |
| `READY` | Ready container 수 | 전체 수보다 작음 |
| `STATUS` | 대표 lifecycle 상태 | `CrashLoopBackOff`, `Pending`, `Error` |
| `RESTARTS` | Container restart 누적 | 장애 시각에 계속 증가 |
| `NODE` | 실행 Node | 특정 Node에만 문제 집중 |

### `get nodes`: Node 요약·condition 조회

#### Resource·option

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `get nodes` | Cluster의 모든 Node 조회 | 특정 Node만 다른가 |
| `-o wide` | Version·internal IP·runtime 추가 | Node 간 drift가 있는가 |
| `-o custom-columns=...` | 필요한 condition만 열로 투영 | Ready·pressure를 한 번에 비교하는가 |

#### `-o wide` 주요 column

| Column | 뜻 | 이상 신호 |
| --- | --- | --- |
| `STATUS` | Scheduler 관점의 Node 준비 상태 | `NotReady`, `Unknown` |
| `ROLES` | Control-plane·worker 역할 | 기대 role 누락 |
| `VERSION` | Kubelet version | Node 간 version 불일치 |
| `INTERNAL-IP` | Cluster 내부 Node 주소 | VPC private IP 불일치 |
| `CONTAINER-RUNTIME` | Runtime 종류·version | Node 간 drift |

#### Condition

| Condition | 정상 방향 | 비정상 의미 |
| --- | --- | --- |
| `Ready` | `True` | `False`는 workload 수용 불가, `Unknown`은 상태 보고 중단 |
| `MemoryPressure` | `False` | Memory 여유 부족 |
| `DiskPressure` | `False` | Block·inode·image filesystem 압박 |
| `PIDPressure` | `False` | 사용 가능한 PID 부족 |
| `NetworkUnavailable` | CNI 설치 뒤 `False` | Node network 미준비 |

특정 Node의 `reason`, `message`, `lastTransitionTime`을 kubelet journal과 연결합니다. Node가
여러 개여도 정상 Node와 문제 Node를 비교하는 절차는 같습니다.

## `ls`: Static Pod manifest inventory 조회

### Option·대상

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-l` | Mode·owner·size·mtime과 이름 표시 | File 존재와 최근 변경 시각을 볼 수 있는가 |
| `/etc/kubernetes/manifests` | Kubelet static Pod manifest directory | 기대 manifest들이 존재하는가 |

### 출력 field

| Field | 뜻 | 이상 신호 |
| --- | --- | --- |
| Mode·owner | File permission·소유자 | Kubelet이 읽을 수 없는 owner·mode |
| Size | Manifest file 크기 | 0 byte·비정상적으로 작음 |
| Mtime | 마지막 변경 시각 | Incident 직전 예상 밖 변경 |
| Name | Component manifest 이름 | API server·controller·scheduler·etcd 중 누락 |

## kubeadm static Pod: manifest에서 mirror Pod까지

```text
/etc/kubernetes/manifests/*.yaml
→ kubelet file watcher
→ containerd CRI
→ static Pod container
→ kube-system mirror Pod
```

API가 불통이어도 manifest·kubelet·containerd·local log는 확인할 수 있습니다. Manifest를
이동하거나 편집하면 kubelet이 component를 중단·재생성하므로 읽기만 합니다.

## `etcd`: Kubernetes state 저장소

Etcd가 느리거나 실패하면 API process가 살아 있어도 `/readyz`와 일반 API request가
실패할 수 있습니다.

| Log 단서 | 의미 후보 | 함께 볼 증거 |
| --- | --- | --- |
| Request timeout | Etcd 응답 지연 | API readyz·host load |
| Leader change | Cluster leadership 변화 | 발생 빈도·network |
| Database space | DB quota·disk 여유 | Filesystem block·inode |
| Fsync latency | Storage write 지연 | EBS metric·kernel I/O log |
| Corruption | Data 무결성 문제 | 복구 절차로 즉시 전환하지 말고 증거 보존 |

Etcd data 삭제나 새 cluster 초기화는 진단이 아닙니다.

## 증거 조합과 다음 분기

| 함께 보이는 증거 | 다음 분기 |
| --- | --- |
| 6443 listener 없음 + API 불통 | [140 Linux node service 진단](140-systemd-journal-and-node-services.md) |
| `livez` 성공 + `readyz` etcd check 실패 | Etcd·[130 Filesystem 진단](130-filesystem-and-storage.md) |
| Control-plane Pod 일부만 반복 restart | 해당 component local/API log |
| Control plane 정상 + 특정 Node `NotReady` | 해당 Node의 resource·kubelet·network |
| `NetworkUnavailable=True` + CNI Pod 이상 | [170 Kubernetes workload 진단](170-kubernetes-workload-network-events-and-logs.md) |

## 금지 경계·관련 문서

`kubeadm reset/init`, static manifest edit, etcd data 삭제, Node delete·taint 변경을 하지 않습니다.

- **전체 outline**: [100 Kubernetes platform 핵심 진단 outline](100-kubernetes-platform-diagnostic-outline.md)
- **Control plane 구성·재검증**: [240 Kubernetes control plane 초기화](../240-kubernetes-control-plane-initialization-runbook.md)
- **CNI 복구**: [250 Calico network 설치](../250-calico-network-installation-runbook.md)

`240`은 최초 구성과 같은 cluster의 재검증 owner이며 일반 API 장애 복구를 자동 수행하지
않습니다. 증거가 destructive recovery를 가리키면 별도 범위와 승인을 먼저 확정합니다.

## 공식 참고

- [Kubernetes API health endpoints](https://kubernetes.io/docs/reference/using-api/health-checks/)
- [Node status](https://kubernetes.io/docs/reference/node/node-status/)
- [Kubeadm implementation details](https://kubernetes.io/docs/reference/setup-tools/kubeadm/implementation-details/)
