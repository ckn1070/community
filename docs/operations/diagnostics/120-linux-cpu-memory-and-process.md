# Linux resource 진단: load·CPU·memory·swap·process

Host 전체 pressure와 특정 process 문제를 구분합니다. Load, memory, swap, process state를
같은 시각에 수집하고 단일 숫자 대신 관계로 판단합니다.

**기본 실행 위치:** SSH로 접속한 target EC2의 Bash terminal

**기본 시작 directory:** 제한 없음

## 상단 수동 확장 조회

[000 Platform 진단 공통 실행 계약](000-index.md#공통-실행-계약)에 따라 아래 Bash block
전체를 한 번에 실행합니다.

```bash
(
set +e

probe() {
  printf '\n------------------------------------------------------------\n'
  printf 'PROBE: %s\n' "$1"
  printf '%s\n' "$2"
  printf '%s\n' '------------------------------------------------------------'
}

report_status() {
  printf '[result] %s exit code: %s\n' "$1" "$2"
}

probe 'Online CPU' 'Load와 run queue를 비교할 논리 CPU 기준을 확인합니다.'
getconf _NPROCESSORS_ONLN
report_status 'getconf' "$?"

probe 'Load' '현재 uptime과 1·5·15분 load 추세를 확인합니다.'
uptime
report_status 'uptime' "$?"

probe 'Memory' 'Used가 아니라 새 workload에 제공할 available memory를 확인합니다.'
free -h
report_status 'free' "$?"

probe 'Swap' '활성 swap의 크기·사용량·우선순위를 확인합니다.'
swapon --show --output=NAME,TYPE,SIZE,USED,PRIO
report_status 'swapon' "$?"

probe 'System activity' 'Run queue, CPU wait와 swap in·out의 짧은 변화 추세를 확인합니다.'
vmstat 1 5
report_status 'vmstat' "$?"

probe 'RSS 상위 process' 'Memory를 많이 점유하는 PID·state·시작 시각을 확인합니다.'
ps -eo pid,ppid,lstart,etimes,stat,comm,%cpu,%mem,rss --sort=-rss \
  | sed -n '1,31p'
pipeline_status=("${PIPESTATUS[@]}")
printf '[result] ps exit code: %s; sed exit code: %s\n' \
  "${pipeline_status[0]}" "${pipeline_status[1]}"

probe 'CPU 상위 process' 'CPU를 많이 사용하는 PID와 실행 시간을 확인합니다.'
ps -eo pid,ppid,lstart,etimes,stat,comm,%cpu,%mem,rss --sort=-%cpu \
  | sed -n '1,31p'
pipeline_status=("${PIPESTATUS[@]}")
printf '[result] ps exit code: %s; sed exit code: %s\n' \
  "${pipeline_status[0]}" "${pipeline_status[1]}"

probe 'Kernel OOM 기록' '현재 boot에서 OOM kill·memory pressure가 발생했는지 확인합니다.'
sudo -n journalctl -k -b \
  --grep='Out of memory|Killed process|oom-kill|memory pressure' \
  --no-pager -n 200
report_status 'journalctl' "$?"
)
```

`vmstat`가 없으면 그 사실을 기록하고 나머지 조회를 계속합니다. `ps | sed`의 upstream
실패 여부는 상단 묶음이 출력한 `ps exit code`로 판정합니다. 잘리지 않은 process 목록이
추가로 필요할 때만 같은 `ps` 명령에서 `| sed ...`를 제거해 다시 실행합니다.

## 해석 순서

1. CPU 개수와 1·5·15분 load 추세를 비교합니다.
2. `available`, swap 사용과 `vmstat si/so`로 active memory pressure를 확인합니다.
3. RSS·CPU 상위 process와 `STAT`를 연결합니다.
4. OOM log 시각과 process·service restart 시각을 맞춥니다.

## `getconf`: Online CPU 개수 조회

`getconf`는 현재 system configuration 값을 조회합니다. `_NPROCESSORS_ONLN`은 OS가 현재
online으로 인식하는 논리 CPU 개수입니다.

### 인자·출력 field

| 인자·출력 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `_NPROCESSORS_ONLN` | 현재 online 논리 CPU 개수 | Load·`vmstat r`과 비교할 CPU 기준이 몇 개인가 |

값이 EC2 instance type의 vCPU 수와 다르면 CPU offline 상태와 instance metadata를 추가로
확인합니다.

## `uptime`: load 추세 조회

### 출력 field

| 출력 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| Uptime | Boot 뒤 경과 시간 | 최근 reboot가 있었는가 |
| 1분 load | 최근 부하 | 5·15분보다 올라가는가 |
| 5분 load | 중간 추세 | 일시 spike인가 지속 부하인가 |
| 15분 load | 장기 기준 | 현재 부하가 평소보다 높은가 |

Load는 CPU 사용률이 아니라 실행 가능 또는 uninterruptible sleep task 수의 평균입니다. CPU
개수보다 높다는 사실만으로 CPU 포화를 확정하지 않고 `vmstat r`, `b`, `wa`와 결합합니다.

## `free`: memory 관계 조회

### Option

| Option | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-h` | KiB·MiB·GiB 단위로 표시 | 크기를 빠르게 비교할 수 있는가 |

### 출력 field

| Field | 뜻 | 진단 질문 |
| --- | --- | --- |
| `used` | `total - available` 기반 사용량 | 단독으로 부족 판정하지 않음 |
| `free` | 완전히 미사용 page | 작아도 cache 때문에 정상 가능 |
| `buff/cache` | 회수 가능한 cache 포함 | `available`과 함께 확인 |
| `available` | Swap 없이 새 workload에 줄 수 있는 추정량 | 실제 여유의 첫 기준 |
| Swap `used` | Swap으로 이동한 page | 현재 이동 중인지 `si/so` 확인 |

## `swapon`: 활성 swap inventory 조회

`swapon`은 활성 swap device·file을 조회하거나 변경하는 명령입니다. 이 문서에서는 조회만
하며 swap을 활성화하지 않습니다.

### Option

| Option | 뜻 | 진단 질문 |
| --- | --- | --- |
| `--show` | 활성 swap만 표 형태로 출력 | 현재 실제로 사용 가능한 swap이 있는가 |
| `--output=NAME,TYPE,SIZE,USED,PRIO` | 필요한 column만 지정 | 위치·종류·사용량·우선순위를 한 번에 비교할 수 있는가 |

### 출력 field

| Field | 뜻 | 이상 신호 |
| --- | --- | --- |
| `NAME` | Swap device 또는 file 경로 | 의도하지 않은 swap이 존재 |
| `TYPE` | `partition`·`file` 등 종류 | 기대 구성과 다름 |
| `SIZE` | 전체 swap 크기 | 예상보다 작거나 큼 |
| `USED` | 현재 사용량 | `vmstat si/so`와 함께 증가 |
| `PRIO` | 여러 swap 사이 우선순위 | 의도하지 않은 대상이 먼저 사용됨 |

출력이 비어 있으면 활성 swap이 없다는 뜻입니다. Kubernetes node의 현재 계약과 일치하는지
[200 Kubernetes bootstrap runbook](../200-kubernetes-bootstrap-runbook.md)과 대조합니다.

## `vmstat`: system 변화 조회

### 인자

| 인자 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `1` | Sample 간격 1초 | 순간값이 아니라 변화를 볼 수 있는가 |
| `5` | 총 5회 sample | 첫 boot 평균 뒤 반복 sample을 확보했는가 |

### 출력 field

| Field | 뜻 | 이상 신호 |
| --- | --- | --- |
| `r` | CPU 실행 대기 task | CPU 수보다 지속적으로 큼 |
| `b` | Uninterruptible sleep task | I/O stall과 함께 지속 증가 |
| `si`·`so` | Swap in·out | 0이 아닌 값이 반복됨 |
| `us`·`sy` | User·kernel CPU | 어느 계층이 CPU를 쓰는지 |
| `id` | Idle CPU | 낮은 값이 지속됨 |
| `wa` | I/O wait | Load·`D` process와 함께 높음 |
| `st` | Hypervisor에 빼앗긴 CPU | Virtualization contention 단서 |

첫 sample은 boot 이후 평균일 수 있으므로 뒤 네 sample의 변화를 봅니다.

## `ps`: process snapshot 조회

### Option·subcommand

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-e` | 모든 process 선택 | System 전체를 비교 |
| `-o FIELDS` | 출력 column 지정 | 민감 argument 없이 필요한 field만 표시 |
| `--sort=-rss` | RSS 내림차순 | Memory 상위 process는 무엇인가 |
| `--sort=-%cpu` | CPU 비율 내림차순 | CPU 상위 process는 무엇인가 |

`-eo`는 `-e -o`를 붙여 쓴 short option입니다.

### 출력 field

| Field | 뜻 | 진단 질문 |
| --- | --- | --- |
| `PID` | Process ID | 어떤 실행 instance인가 |
| `PPID` | Parent PID | 누가 생성·관리하는가 |
| `LSTART`·`ETIMES` | 시작 시각·경과 초 | 반복 restart하는가 |
| `STAT` | Process state·modifier | CPU 대기·I/O·zombie인가 |
| `COMM` | Executable 이름 | Argument·environment 노출 없이 종류 확인 |
| `%CPU`·`%MEM` | CPU·memory 비율 | 상위 process 비교 |
| `RSS` | Resident memory, KiB | 실제 RAM 점유 근사치 |

### `STAT` 값

| 값 | 뜻 | 다음 확인 |
| --- | --- | --- |
| `R` | 실행 중·실행 가능 | 다수 지속 시 CPU 경쟁 |
| `S` | Interruptible sleep | 일반적인 대기 가능 |
| `D` | Uninterruptible sleep | I/O wait·stalled device |
| `T` | Stop | Signal·debugger 여부 |
| `Z` | Zombie | PPID parent의 회수 문제 |

## `journalctl`: Kernel OOM·memory pressure 기록 조회

`journalctl`은 systemd journal을 조회합니다. 여기서는 현재 boot의 kernel message 중
memory pressure와 OOM 단서만 제한된 행 수로 읽습니다.

### Option

| Option | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-k` | Kernel message만 조회 | Application log가 아닌 kernel 판정인가 |
| `-b` | 현재 boot만 조회 | 이전 boot 사건을 현재 장애와 섞지 않았는가 |
| `--grep=PATTERN` | OOM·kill·pressure pattern만 선택 | Memory 강제 회수 증거가 있는가 |
| `--no-pager` | Pager 없이 출력 | 자동 대기 없이 bounded 결과를 얻는가 |
| `-n 200` | 최근 최대 200행 | 출력 상한을 지켰는가 |

### 출력 field

| 출력 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| Timestamp | Kernel message 시각 | Incident·restart 시각과 겹치는가 |
| `Out of memory`·`oom-kill` | Kernel OOM 판정 | Memory pressure가 실제 kill로 이어졌는가 |
| `Killed process` | 종료된 PID·process 정보 | 어떤 service·workload가 영향을 받았는가 |

`Killed process`가 있으면 시각·PID·process 이름을 `ps`와 연결합니다. 현재 `ps`에 그 PID가
없을 수 있으므로, 존재하지 않는다는 사실을 OOM 부재로 해석하지 않습니다.

## 증거 조합과 다음 분기

| 함께 보이는 증거 | 해석·다음 문서 |
| --- | --- |
| `available` 낮음 + `si/so` 반복 + OOM | Active memory pressure |
| Load·`r` 높음 + `id` 낮음 | CPU run queue 경쟁 |
| Load·`b`·`wa` 높음 + `D` process | [130 Filesystem·storage 진단](130-filesystem-and-storage.md) |
| 특정 service PID 반복 변경 | [140 Linux node service 진단](140-systemd-journal-and-node-services.md) |
| Host 여유 충분 + API timeout | [160 Kubernetes control plane 진단](160-kubernetes-api-control-plane-and-node.md) |

## 금지 경계·관련 문서

진단 중 process kill, swap 생성, service restart, instance size 변경을 하지 않습니다.

- **전체 outline**: [100 Kubernetes platform 핵심 진단 outline](100-kubernetes-platform-diagnostic-outline.md)
- **Filesystem·I/O**: [130 Filesystem·storage 진단](130-filesystem-and-storage.md)
- **현재 EC2 size**: [120 EC2 Kubernetes node](../../project/infrastructure/120-ec2-kubernetes-node.md)

## 공식 참고

- [ps(1)](https://man7.org/linux/man-pages/man1/ps.1.html)
- [vmstat(8)](https://man7.org/linux/man-pages/man8/vmstat.8.html)
- [free(1)](https://man7.org/linux/man-pages/man1/free.1.html)
