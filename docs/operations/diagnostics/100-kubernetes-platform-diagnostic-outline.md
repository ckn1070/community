# Kubernetes 운영 stack 핵심 진단 outline: EC2 host·Linux·Kubernetes·Argo CD

이 문서는 AWS EC2 위 Linux node, Kubernetes와 Argo CD에서 **어느 영역을 더 깊게 봐야
하는지** 빠르게 찾는 read-only 진단입니다. 각 section에는 대표 probe 1~3개만 두며,
원인 분석은 연결된 상세 문서의 상단 bounded preset과 하단 기본 명령·선택 option을 사용합니다.

**기본 실행 위치:** SSH로 접속한 target EC2의 Bash terminal

**기본 시작 directory:** 제한 없음

## 실행 전 확인

- 조사할 EC2에 SSH로 접속한 **Bash**에서 실행합니다.
- Host section은 node마다 반복하고, Kubernetes·Argo CD section은 cluster마다 한 번 봅니다.
- `sudo -n`과 `/etc/kubernetes/admin.conf`를 사용하며 Secret 입력은 없습니다.
- 한 probe가 실패해도 다음 section을 계속 수집합니다. Nonzero와 stderr도 증거입니다.
- Resource 생성·변경·재시작·삭제는 하지 않습니다.

SSH 자체가 불가능하면 먼저 [110 AWS EC2·접근 진단](110-aws-ec2-and-access.md)의 Console
묶음을 확인합니다.

## 출력에서 먼저 볼 것

| Section | 핵심 질문 | 대표 이상 신호 |
| --- | --- | --- |
| `01` Host resource | Host 전체가 CPU·memory pressure인가 | 높은 load, 작은 `available` |
| `02` Process | 특정 process가 resource를 차지하는가 | 한 PID의 큰 RSS·CPU, `D`·`Z` state |
| `03` Filesystem | Block·inode가 고갈됐는가 | `Use%`·`IUse%`가 한계에 가까움 |
| `04` Node service | systemd·containerd·kubelet이 실패했는가 | failed unit, restart·inactive |
| `05` Host log | Kernel·node service가 원인을 기록했는가 | OOM, I/O, runtime·kubelet warning |
| `06` Linux network | Address·route·listener가 존재하는가 | private IP·default route·22/6443 listener 누락 |
| `07` API·Node | API가 ready이고 Node가 상태를 보고하는가 | readyz 실패, `NotReady` |
| `08` Control plane | Static control-plane Pod가 동작하는가 | restart 증가, not ready |
| `09` Workload | Pod가 schedule·pull·start·ready 중 어디서 막혔는가 | Pending, pull error, crash, event warning |
| `10` Argo CD | Core와 Application reconcile이 진행되는가 | component not ready, sync·health·condition 이상 |

## 핵심 진단 명령 (bounded preset)

다음 묶음은 [000 Platform 진단 공통 실행 계약](000-index.md#공통-실행-계약)에 따라 target EC2의 Bash에서
실행합니다. 큰 `====` 구분자는 진단 영역과 핵심 질문을, 작은 `----` 구분자는 바로 뒤
명령의 목적을 출력합니다. 각 probe 뒤의 exit code는 성공·실패를 숨기지 않고 다음 probe를
계속 실행하기 위한 증거입니다.

여기 적힌 `60분`, 마지막 `N`행과 request timeout은 첫 화면을 안전하게 제한하는 preset이지
항상 적용할 조회 기준이 아닙니다. 전체·특정 시각·follow 조회는 이상 영역의 상세 문서에서
기본 명령에 필요한 option을 붙여 실행합니다.

`section`과 `probe`는 출력 제목을 만드는 함수이고, `set +e`는 한 조회의 실패 때문에 뒤의
read-only 조회가 중단되지 않게 합니다. `PIPESTATUS`는 pipeline에서 앞 명령과 출력 제한
명령의 결과를 따로 보존합니다. 이 네 요소는 진단용 출력 구분에만 쓰며 상태를 변경하지
않습니다.

```bash
umask 077
diagnostic_path="/tmp/kubernetes-platform-diagnostic-$(date +%Y%m%d-%H%M%S).log"

sudo -n env -i \
  PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
  KUBECONFIG=/etc/kubernetes/admin.conf \
  /bin/bash 2>&1 <<'EOF' | tee "${diagnostic_path}"
set +e

section() {
  printf '\n\n============================================================\n'
  printf 'SECTION %s: %s\n' "$1" "$2"
  printf '핵심 질문: %s\n' "$3"
  printf '============================================================\n\n'
}

probe() {
  printf '\n------------------------------------------------------------\n'
  printf 'PROBE: %s\n' "$1"
  printf '%s\n' "$2"
  printf '%s\n' '------------------------------------------------------------'
}

report_status() {
  printf '\n[result] %s exit code: %s\n' "$1" "$2"
}

section '01' 'Host resource' 'Host 전체에 CPU·memory pressure가 있는가'
probe '현재 시각·load' '사건 시각과 1·5·15분 load 추세를 확인합니다.'
date --iso-8601=seconds
report_status 'date' "$?"
uptime
report_status 'uptime' "$?"
probe 'Memory' '새 workload에 줄 수 있는 available memory를 확인합니다.'
free -h
report_status 'free' "$?"

section '02' 'Process' '특정 process가 memory를 집중적으로 쓰는가'
probe 'RSS 상위 process' 'Argument·environment 없이 PID·state·resource 사용량만 봅니다.'
ps -eo pid,ppid,stat,comm,%cpu,%mem,rss --sort=-rss | sed -n '1,31p'
pipeline_status=("${PIPESTATUS[@]}")
printf '\n[result] ps exit code: %s, sed exit code: %s\n' \
  "${pipeline_status[0]}" "${pipeline_status[1]}"

section '03' 'Filesystem' 'Root filesystem의 block 또는 inode가 고갈됐는가'
probe 'Block 사용량' 'Filesystem type·전체 크기·사용률을 확인합니다.'
df -hT /
report_status 'df block' "$?"
probe 'Inode 사용량' '작은 file 과다로 inode가 고갈됐는지 확인합니다.'
df -ih /
report_status 'df inode' "$?"

section '04' 'Node service' 'systemd·containerd·kubelet이 실패 상태인가'
probe 'Failed unit' 'systemd가 failed로 판정한 unit을 확인합니다.'
systemctl --failed --no-pager
report_status 'systemctl failed' "$?"
probe '핵심 unit 상태' 'SSH·container runtime·Node agent의 active 상태와 최근 log를 봅니다.'
systemctl status ssh.service containerd.service kubelet.service \
  --no-pager --full --lines=30
report_status 'systemctl status' "$?"

section '05' 'Host log' 'Kernel·node service가 최근 원인을 기록했는가'
probe 'Kernel warning' '최근 60분의 OOM·I/O·network 계층 warning을 찾습니다.'
journalctl -k -b --since '-60 min' --priority=warning --no-pager -n 120
report_status 'kernel journal' "$?"
probe 'containerd·kubelet warning' 'Runtime과 Node agent의 최근 warning을 같은 시간대로 봅니다.'
journalctl -u containerd.service -u kubelet.service -b \
  --since '-60 min' --priority=warning --no-pager -n 160
report_status 'node service journal' "$?"

section '06' 'Linux network' 'Private address·route·22/6443 listener가 존재하는가'
probe 'Address·route' 'Interface address와 main routing table을 확인합니다.'
ip -brief address
report_status 'ip address' "$?"
ip route show table main
report_status 'ip route' "$?"
probe 'SSH·API listener' 'TCP 22와 6443을 실제로 listen하는 process를 확인합니다.'
ss -lntp '( sport = :22 or sport = :6443 )'
report_status 'ss listener' "$?"

section '07' 'Kubernetes API·Node' 'API가 ready이고 Node가 상태를 보고하는가'
probe 'API readiness' 'API server의 개별 ready check 결과를 확인합니다.'
kubectl --request-timeout=10s get --raw='/readyz?verbose'
report_status 'kubectl readyz' "$?"
probe 'Node summary' 'Ready 여부·version·address와 특정 Node 집중 여부를 봅니다.'
kubectl --request-timeout=10s get nodes -o wide
report_status 'kubectl nodes' "$?"

section '08' 'Control plane' 'Static control-plane component가 준비됐는가'
probe 'Control-plane Pod' 'API server·scheduler·controller-manager·etcd 상태를 확인합니다.'
kubectl --request-timeout=10s -n kube-system \
  get pods -l tier=control-plane -o wide
report_status 'kubectl control plane pods' "$?"

section '09' 'Workload' 'Pod가 schedule·pull·start·ready 중 어디에서 멈췄는가'
probe '전체 Pod' 'READY·STATUS·RESTARTS·NODE·IP를 namespace 전체에서 비교합니다.'
kubectl --request-timeout=10s get pods -A -o wide
report_status 'kubectl pods' "$?"
probe '최근 Event' 'Scheduler·image pull·CNI·probe 실패 reason을 최근 120행에서 찾습니다.'
kubectl --request-timeout=10s get events -A \
  --sort-by=.metadata.creationTimestamp | tail -n 120
pipeline_status=("${PIPESTATUS[@]}")
printf '\n[result] kubectl events exit code: %s, tail exit code: %s\n' \
  "${pipeline_status[0]}" "${pipeline_status[1]}"

section '10' 'Argo CD' 'Core와 Application reconcile이 진행되는가'
probe 'Core component' 'Argo CD Pod·Deployment·StatefulSet의 readiness를 확인합니다.'
kubectl --request-timeout=10s -n argocd \
  get pods,deployments,statefulsets -o wide
report_status 'kubectl Argo CD core' "$?"
probe 'Application status' 'Sync·health·마지막 reconcile과 condition을 함께 봅니다.'
kubectl --request-timeout=10s -n argocd \
  get application.argoproj.io/car-community-stg \
  -o 'jsonpath={"sync="}{.status.sync.status}{"\nhealth="}{.status.health.status}{"\nreconciledAt="}{.status.reconciledAt}{"\n"}{range .status.conditions[*]}{.type}{"\t"}{.message}{"\n"}{end}'
report_status 'kubectl Argo CD Application' "$?"
EOF

pipeline_status=("${PIPESTATUS[@]}")
printf '\ncollector exit code: %s\n' "${pipeline_status[0]}"
printf 'tee exit code: %s\n' "${pipeline_status[1]}"
printf '진단 파일: %s\n' "${diagnostic_path}"
```

`collector exit code`는 root Bash 묶음 자체의 실행 결과이고 개별 probe 성공을 대신하지
않습니다. `tee exit code`가 nonzero이면 화면에 출력됐더라도 file 보존은 실패한 것입니다.
Pipeline의 앞 명령이 실패하면 함께 기록된 첫 번째 exit code를 기준으로 판단합니다.
`set -x`는 environment·argument의 민감값을 노출할 수 있어 사용하지 않습니다.

## 이상 영역별 다음 문서

| 이상 section | 확장 진단과 상세 해석 |
| --- | --- |
| SSH 전 AWS 경계 | [110 AWS EC2·접근 진단](110-aws-ec2-and-access.md) |
| `01`·`02` | [120 Linux resource 진단](120-linux-cpu-memory-and-process.md) |
| `03` | [130 Filesystem·storage 진단](130-filesystem-and-storage.md) |
| `04`·`05` | [140 Linux node service 진단](140-systemd-journal-and-node-services.md) |
| `06` | [150 Linux network 진단](150-linux-network.md) |
| `07`·`08` | [160 Kubernetes control plane 진단](160-kubernetes-api-control-plane-and-node.md) |
| `09` | [170 Kubernetes workload 진단](170-kubernetes-workload-network-events-and-logs.md) |
| `10` | [180 Argo CD reconciliation 진단](180-argocd-reconciliation.md) |

여러 section이 동시에 실패하면 AWS → Linux resource → node service → Kubernetes API →
workload → Argo CD 순서로 첫 실패 경계를 찾습니다. 아래 계층의 실패가 위 계층의 timeout으로
나타날 수 있습니다.

## 출력 보존과 금지 경계

Terminal 출력을 보존하면 hostname, IP, image reference, Git revision과 자유 형식
log·event·condition이 포함될 수 있습니다. `umask 077`로 새 파일을 local mode `0600`으로
만들고, 외부 공유 전 token 조각·식별 값·사용자 입력을 가립니다. PEM, password, Secret
`.data`, process environment와 kubeconfig 본문은 직접 조회하지 않습니다. 조회 완료는
정상·복구 완료가 아니며, 원인이 정해진 뒤 연결된 runbook에서 별도로 복구합니다.

## 관련 문서

- **진단 router**: [000 Platform 진단 목차](000-index.md)
- **변경·복구 원칙**: [300 Infrastructure 변경과 복구 원칙](../../project/infrastructure/300-change-and-recovery-principles.md)
- **현재 staging 재개점**: [100 Staging 단일 node 전체 구축 시나리오](../scenarios/100-staging-single-node-bootstrap.md)
