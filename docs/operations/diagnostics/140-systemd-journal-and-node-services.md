# Linux node service 진단: systemd·journal·containerd·kubelet

Unit의 현재 state, restart 결과와 journal을 연결하고 Kubernetes node의 runtime·kubelet 경계를
구분합니다. `active` 한 값만으로 정상이라 판단하지 않습니다.

## 상단 수동 확장 조회

[000 Platform 진단 공통 실행 계약](000-index.md#공통-실행-계약)에 따라 아래 Bash block
전체를 한 번에 실행합니다.

```bash
systemctl is-system-running
systemctl --failed --no-pager
systemctl show ssh.service containerd.service kubelet.service \
  --property=Id,LoadState,ActiveState,SubState,Result,MainPID,NRestarts,ExecMainStatus
systemctl status ssh.service containerd.service kubelet.service \
  --no-pager --full --lines=80
systemctl cat containerd.service kubelet.service
sudo -n journalctl -u containerd.service -u kubelet.service \
  -b --since '-60 min' --no-pager -n 400
sudo -n ctr plugins ls | sed -n '1,160p'
sudo -n tail -n 160 /var/log/containers/kube-apiserver-*.log
sudo -n tail -n 160 /var/log/containers/etcd-*.log
```

Local log glob이 일치하지 않으면 해당 file이 없다는 증거로 기록합니다. Pipeline upstream
실패가 의심되면 `ctr plugins ls`를 출력 제한 없이 단독으로 재실행합니다.

## 해석 순서

1. System 전체가 `running`인지 `degraded`인지 확인합니다.
2. Failed unit과 세 주요 service property를 비교합니다.
3. Unit definition과 journal에서 마지막 실패·restart 원인을 찾습니다.
4. Containerd service와 CRI plugin, kubelet과 API 연결을 나눕니다.
5. API 불통이면 local static Pod log로 내려갑니다.

## `systemctl`: systemd unit 상태·정의 조회

### Subcommand·option

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `is-system-running` | Manager 전체 상태 요약 | Boot 완료·failed unit이 있는가 |
| `--failed` | Failed state unit 목록 | 어떤 unit이 전체 `degraded`를 만들었는가 |
| `show` | `Property=Value` 출력 | 자동 비교할 현재 state는 무엇인가 |
| `status` | 상태·process tree·최근 log 요약 | 마지막 실패와 process는 무엇인가 |
| `cat` | Vendor unit·drop-in 출력 | 실제 ExecStart·environment file은 무엇인가 |
| `--no-pager` | Pager 비활성 | 비대화형 출력이 멈추지 않게 함 |
| `--full` | 긴 줄 생략 방지 | 오류 message 전체 확인 |

### Manager 상태

| 값 | 뜻 | 다음 확인 |
| --- | --- | --- |
| `running` | Boot 완료, failed unit 없음 | 개별 service·상위 API |
| `degraded` | 동작하지만 failed unit 존재 | `systemctl --failed` |
| `starting` | Boot job 진행 중 | 오래 걸리는 unit |
| `maintenance`·`emergency` | 복구 mode | Console·journal 우선 |

### Unit property

| Property | 뜻 | 이상 신호 |
| --- | --- | --- |
| `LoadState` | Unit definition load 결과 | `not-found`, `error`, `masked` |
| `ActiveState`·`SubState` | 큰 상태·세부 상태 | `failed`, `auto-restart`, 장시간 `activating` |
| `Result` | 마지막 job 결과 | `exit-code`, `signal`, `timeout`, `oom-kill` |
| `MainPID` | 현재 main process | Active인데 `0`, 반복 변경 |
| `NRestarts` | 자동 restart 누적 | 짧은 시간에 증가 |
| `ExecMainStatus` | Main process exit status | Service별 nonzero 원인 |

### `status` 출력 field

| 출력 요소 | 뜻 | 이상 신호·다음 증거 |
| --- | --- | --- |
| `Loaded` | Unit file load 상태와 enable 상태 | `not-found`·`masked`면 `cat`·package ownership 확인 |
| `Active` | ActiveState·SubState와 상태 전이 시각 | `failed`·`activating` 지속은 `Result`·journal과 결합 |
| Main PID·Tasks·Memory·CPU | 현재 process tree와 resource 요약 | Active인데 Main PID가 없거나 resource가 급증하면 process·host 증거 확인 |
| `CGroup` | Unit에 속한 process tree | 예상 외 child·반복 생성 process가 있는지 확인 |
| 최근 journal 행 | Unit 상태 전후의 요약 log | 출력 행수가 제한되므로 시간 범위 `journalctl`로 원인·후속 restart 확인 |

`status`는 사람이 빠르게 보는 요약입니다. 최종 판정은 `show`의 property와
같은 시간 범위의 `journalctl`을 결합합니다.

### `cat` 출력·override 순서

| 출력 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `# /path/to/unit` | 이어지는 fragment의 source 경로 | Vendor unit인가, local override인가 |
| Vendor unit fragment | Package가 제공한 기본 definition | 기본 `ExecStart`·`EnvironmentFile`은 무엇인가 |
| `*.d/*.conf` drop-in | Vendor unit 뒤에 합성되는 local override | 어느 drop-in이 같은 directive를 덮어쓰는가 |
| 반복 directive | Unit 유형에 따라 reset·추가·override되는 값 | `show`의 최종 property와 일치하는가 |

`cat`은 source 순서를, `show`는 systemd가 합성한 현재 값을 보여줍니다. 둘이
기대와 다르면 수정하지 말고 어느 vendor fragment·drop-in이 값을 만들었는지를
증거로 남깁니다.

## `journalctl`: 시간·unit·priority 기준 log 조회

### Option

| Option | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-u UNIT` | 특정 unit 선택, 반복 가능 | 어느 service log인가 |
| `-b` | 현재 boot로 제한 | 이전 boot와 섞였는가 |
| `--since '-60 min'` | 최근 시간 범위 | 장애 시각을 포함하는가 |
| `--priority=warning` | Warning 이상 선택 | 넓은 log에서 우선 볼 행은 무엇인가 |
| `-k` | Kernel message 선택 | OOM·I/O·driver 문제인가 |
| `-n N` | 마지막 N행 | 출력 상한을 지켰는가 |
| `--no-pager` | Pager 비활성 | 복사 실행이 멈추지 않는가 |

Error 한 줄만 보지 말고 앞의 configuration·dependency 실패와 뒤의 restart를 함께 읽습니다.

### 출력 field

| 출력 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| Timestamp·unit | 발생 시각과 log producer | Incident 시각·대상 service가 맞는가 |
| Priority | Warning·error 심각도 | 반복되는 첫 warning은 무엇인가 |
| Message | Configuration·dependency·restart 내용 | 원인과 후속 restart를 함께 볼 수 있는가 |

## `ctr`: containerd plugin 조회

### Subcommand

| Subcommand | 뜻 | 진단 질문 |
| --- | --- | --- |
| `plugins ls` | Load된 containerd plugin 목록 | CRI plugin이 존재하는가 |

### 출력 field

| Field | 뜻 | 진단 질문 |
| --- | --- | --- |
| Plugin type·ID | Plugin identity | `io.containerd.grpc.v1.cri` 계열인가 |
| Status | Plugin init 결과 | `ok`인가, error인가 |

Containerd service가 active여도 CRI plugin이 error면 kubelet은 runtime을 사용할 수 없습니다.
진단 중 `/etc/containerd/config.toml`을 수정하지 않습니다.

## `kubelet`: Node agent 관계 해석

```text
kubelet service
→ kubelet config·kubeadm flags
→ containerd CRI
→ API server
→ Node condition·Pod status
```

| Log 단서 | 첫 경계 |
| --- | --- |
| `container runtime is down` | containerd service·CRI |
| `NetworkPluginNotReady` | CNI, [170 Kubernetes workload 진단](170-kubernetes-workload-network-events-and-logs.md) |
| API connection refused·timeout | [150 Linux network 진단](150-linux-network.md)·[160 Control plane 진단](160-kubernetes-api-control-plane-and-node.md) |
| OOM·signal restart | [120 Linux resource 진단](120-linux-cpu-memory-and-process.md) |

## `tail`: Local static Pod log를 API 없이 조회

### Option·대상

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-n 160` | 각 file의 마지막 160행 | 출력 상한을 지켰는가 |
| `/var/log/containers/kube-apiserver-*.log` | API server container symlink log | Listener·etcd·certificate 오류가 있는가 |
| `/var/log/containers/etcd-*.log` | Etcd container symlink log | Timeout·fsync·space 오류가 있는가 |

### 출력 field

| 출력 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| Timestamp·stream | 발생 시각과 stdout·stderr stream | Incident 시각에 집중되는가 |
| Component message | API server·etcd의 자유 형식 log | 첫 실패와 반복 후속 오류는 무엇인가 |
| `tail` nonzero | Glob 불일치·read 실패 | Local log가 없는가, 읽기 실패인가 |

API가 완전히 불통이면 `kubectl logs`도 실패합니다. 상단 수동 조회는
`/var/log/containers/kube-apiserver-*.log`와 `etcd-*.log`에 일치하는 각 file의 마지막
160행을 읽습니다. Glob이 일치하지 않아 `tail`이 nonzero이면 local log가 없다는 증거로
기록합니다.

Log가 없으면 다음 관계를 확인합니다.

1. `/etc/kubernetes/manifests`에 static Pod manifest가 있는가
2. Kubelet journal에 manifest watch·sandbox·container start 실패가 있는가
3. Containerd·CRI가 정상인가
4. Log symlink target과 filesystem을 읽을 수 있는가

## 증거 조합과 다음 분기

| 함께 보이는 증거 | 다음 분기 |
| --- | --- |
| Containerd failed + CRI 조회 실패 | Runtime service/configuration |
| Containerd·CRI 정상 + kubelet failed | Kubelet flag·config·API |
| 두 service 정상 + Node `NotReady` | [160 Control plane·Node 진단](160-kubernetes-api-control-plane-and-node.md) |
| Service 정상 + Pod만 실패 | [170 Workload 진단](170-kubernetes-workload-network-events-and-logs.md) |

## 금지 경계·관련 문서

Service restart, `daemon-reload`, unit enable/disable, config edit, log truncate를 하지 않습니다.

- **전체 outline**: [100 Kubernetes platform 핵심 진단 outline](100-kubernetes-platform-diagnostic-outline.md)
- **Containerd 복구**: [220 Containerd 설치](../220-containerd-installation-runbook.md)
- **Kubernetes component 복구**: [230 Kubernetes component 설치](../230-kubernetes-components-installation-runbook.md)
- **Control plane 구성·재검증**: [240 Kubernetes control plane 초기화](../240-kubernetes-control-plane-initialization-runbook.md)

`240`은 최초 구성과 같은 cluster의 재검증 owner이며 일반 API 장애 복구를 자동 수행하지
않습니다. 증거가 destructive recovery를 가리키면 별도 범위와 승인을 먼저 확정합니다.

## 공식 참고

- [systemctl(1)](https://www.freedesktop.org/software/systemd/man/latest/systemctl.html)
- [journalctl(1)](https://www.freedesktop.org/software/systemd/man/latest/journalctl.html)
- [Kubernetes container runtimes](https://kubernetes.io/docs/setup/production-environment/container-runtimes/)
