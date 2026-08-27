# Filesystem·storage 진단: block·inode·mount·directory·I/O

“Disk 문제”를 block 용량, inode, read-only mount, 큰 directory, 열린 삭제 file과 EBS I/O
계층으로 나눕니다.

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

probe 'Block device' 'Device·filesystem·mount 계층과 실제 filesystem 여유를 확인합니다.'
lsblk -o NAME,TYPE,SIZE,FSTYPE,FSAVAIL,FSUSE%,MOUNTPOINTS
report_status 'lsblk' "$?"

probe 'Root mount' 'Root filesystem의 source·type과 read-write mount option을 확인합니다.'
findmnt -o TARGET,SOURCE,FSTYPE,OPTIONS /
report_status 'findmnt' "$?"

probe 'Filesystem block' 'Mounted filesystem별 block 용량과 사용률을 확인합니다.'
df -hT
report_status 'df block' "$?"

probe 'Filesystem inode' 'Block 여유와 별개로 inode가 고갈됐는지 확인합니다.'
df -ih
report_status 'df inode' "$?"

probe 'Large directory' '/var 바로 아래에서 공간을 많이 사용하는 directory를 확인합니다.'
sudo -n du -x -h --max-depth=1 /var \
  | sort -h \
  | tail -n 30
pipeline_status=("${PIPESTATUS[@]}")
printf '[result] du exit code: %s; sort exit code: %s; tail exit code: %s\n' \
  "${pipeline_status[0]}" "${pipeline_status[1]}" "${pipeline_status[2]}"

probe 'Kernel I/O 기록' '현재 boot의 filesystem·block I/O·read-only 오류를 확인합니다.'
sudo -n journalctl -k -b \
  --grep='I/O error|EXT4-fs error|read-only|Buffer I/O|blk_update_request' \
  --no-pager -n 200
report_status 'journalctl' "$?"

probe 'Deleted-open file' '삭제됐지만 process가 열고 있어 공간이 반환되지 않은 file을 확인합니다.'
sudo -n lsof +L1 | sed -n '1,120p'
pipeline_status=("${PIPESTATUS[@]}")
printf '[result] lsof exit code: %s; sed exit code: %s\n' \
  "${pipeline_status[0]}" "${pipeline_status[1]}"
)
```

`lsof`가 없으면 그 사실을 기록하고 나머지 조회를 계속합니다. Pipeline producer의 실패
여부는 상단 묶음이 출력한 producer exit code로 판정합니다. 잘리지 않은 출력이 추가로
필요할 때만 출력 제한 command를 제거하고 producer를 단독으로 재실행합니다.

## 해석 순서

1. Block device → filesystem → mount 관계를 확인합니다.
2. Block과 inode 중 어느 자원이 부족한지 나눕니다.
3. Mount가 `rw`인지, kernel이 I/O·filesystem 오류를 기록했는지 봅니다.
4. 큰 directory와 deleted-open file로 사용 주체를 좁힙니다.

## `lsblk`: block device 계층 조회

### Option

| Option | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-o NAME,TYPE,SIZE,FSTYPE,FSAVAIL,FSUSE%,MOUNTPOINTS` | 필요한 column과 순서 지정 | Device→filesystem→mount 관계를 한 화면에서 볼 수 있는가 |

### 출력 field

| Field | 뜻 | 진단 질문 |
| --- | --- | --- |
| `NAME`·`TYPE` | Device 이름·disk/part | EBS device와 partition 관계는 무엇인가 |
| `SIZE` | Block device 크기 | Provisioned volume과 일치하는가 |
| `FSTYPE` | Filesystem type | ext4 등 실제 driver는 무엇인가 |
| `FSAVAIL`·`FSUSE%` | Filesystem 여유·사용률 | Device만 커지고 filesystem은 미확장인가 |
| `MOUNTPOINTS` | Mount 위치 | `/`와 별도 data mount 관계는 무엇인가 |

## `df`: mounted filesystem 용량 조회

### Option

| Option | 뜻 | 출력 단위 |
| --- | --- | --- |
| `-h` | 1024 단위 human-readable 표시 | KiB·MiB·GiB |
| `-T` | Filesystem type 표시 | ext4·tmpfs·overlay 등 |
| `-i` | Block 대신 inode 표시 | Inodes·IUsed·IFree·IUse% |

### Block 출력 field

| Field | 뜻 | 진단 질문 |
| --- | --- | --- |
| `Filesystem` | Backing device·virtual filesystem | 같은 storage를 중복 계산하는가 |
| `Size`·`Used`·`Avail` | 전체·사용·가용 block | 실제 쓰기 여유가 있는가 |
| `Use%` | Block 사용률 | 지속 증가·한계 접근인가 |
| `Mounted on` | Namespace path | 어느 workload data인가 |

Byte 여유가 있어도 `IUse%`가 한계에 가까우면 작은 file 다량 때문에 새 file을 만들지 못할
수 있습니다.

### Inode 출력 field

| Field | 뜻 | 진단 질문 |
| --- | --- | --- |
| `Inodes`·`IUsed`·`IFree` | 전체·사용·가용 inode | 새 file을 만들 inode가 남았는가 |
| `IUse%` | Inode 사용률 | Block 여유와 별개로 한계에 가까운가 |

## `findmnt`: mount source·option 조회

### Option

| Option | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-o TARGET,SOURCE,FSTYPE,OPTIONS` | 출력 column 지정 | Root mount의 source와 mode를 바로 비교할 수 있는가 |
| `/` | Root mount만 선택 | 문제 filesystem 범위를 먼저 좁혔는가 |

### 출력 field

| Field | 뜻 | 이상 신호 |
| --- | --- | --- |
| `TARGET` | Mount point | 기대 path와 다름 |
| `SOURCE` | Backing device | 의도하지 않은 device |
| `FSTYPE` | Filesystem driver | 예상 type과 다름 |
| `OPTIONS` | `rw`, `ro` 등 option | `ro`로 remount됨 |

Kernel은 filesystem 오류 뒤 보호를 위해 read-only로 remount할 수 있습니다. 이 경우 용량이
남아 있어도 쓰기가 실패합니다.

## `du`: directory 사용량 조회

### Option

| Option | 뜻 | 안전 경계 |
| --- | --- | --- |
| `-x` | 다른 filesystem으로 넘어가지 않음 | 중복·원격 mount 탐색 방지 |
| `-h` | Human-readable size | `sort -h`와 결합 |
| `--max-depth=1` | 한 단계만 합계 | 무제한 tree scan 방지 |

### 출력 field

| Field | 뜻 | 진단 질문 |
| --- | --- | --- |
| Size | Directory 아래 같은 filesystem의 합계 | 어느 subtree가 block을 많이 쓰는가 |
| Path | 합계를 계산한 directory | 다음 한 단계 조회 대상을 고를 수 있는가 |

첫 결과에서 큰 directory 하나를 골라 같은 방식으로 한 단계씩 좁힙니다. Kubernetes node의
주요 후보는 `/var/lib/containerd`, `/var/lib/kubelet`, `/var/lib/etcd`, `/var/log`, `/tmp`입니다.

## `sort`·`tail`: Directory 결과 정렬·상한

### Option·출력

| Command·option | 뜻 | 진단 질문 |
| --- | --- | --- |
| `sort -h` | Human-readable size를 수치로 정렬 | 큰 directory가 마지막에 모이는가 |
| `tail -n 30` | 마지막 30행만 출력 | 출력 상한 안에서 큰 후보를 볼 수 있는가 |

Pipeline의 성공은 [000 Platform 진단 공통 실행 계약](000-index.md#공통-실행-계약)의
producer·filter 분리 원칙으로 판정합니다.

## `journalctl`: Kernel filesystem·I/O 오류 조회

### Option

| Option | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-k` | Kernel message만 선택 | Filesystem·block device 계층 오류인가 |
| `-b` | 현재 boot만 선택 | 이전 boot 사건과 섞이지 않았는가 |
| `--grep=PATTERN` | I/O·filesystem·read-only pattern 선택 | 용량 외 오류 단서가 있는가 |
| `--no-pager`·`-n 200` | Pager 없이 최근 최대 200행 | 출력 상한을 지켰는가 |

### 출력 field

| Log 단서 | 뜻 | 이상 신호 |
| --- | --- | --- |
| `I/O error`·`Buffer I/O` | Block read/write 실패 | EBS metric·device 상태와 함께 발생 |
| `EXT4-fs error` | ext4 metadata·journal 오류 | Mount 보호 전환 가능성 |
| `read-only` | Read-only remount·쓰기 거부 | `findmnt OPTIONS=ro`와 일치 |

## `lsof +L1`: 삭제됐지만 열린 file 조회

### Option

| Option | 뜻 | 진단 질문 |
| --- | --- | --- |
| `+L1` | Link count가 1보다 작은 열린 file 선택 | 삭제됐지만 process가 계속 잡고 있는가 |
| `sed -n '1,120p'` | 결과 첫 120행만 표시 | 출력 상한을 지켰는가 |

### 출력 field

| Field | 뜻 | 진단 질문 |
| --- | --- | --- |
| `COMMAND`·`PID`·`USER` | File을 연 process·owner | 어느 service가 block을 점유하는가 |
| `SIZE/OFF` | File size·offset | 큰 deleted-open file인가 |
| `NLINK`·`NAME` | Link count·삭제 path | `(deleted)` 상태인가 |

Pathname이 삭제돼도 process가 file descriptor를 열고 있으면 block은 반환되지 않습니다.
`df`는 가득 찼는데 `du` 합계가 작을 때 확인합니다. `+L1`은 link count가 1보다 작은 file을
선택합니다. Process를 즉시 kill하지 않고 PID·owner·service를 먼저 확인합니다.

## 증거 조합과 다음 분기

| 함께 보이는 증거 | 다음 분기 |
| --- | --- |
| Block `Use%` 높음 + 큰 `/var/lib/containerd` | Image·snapshot owner 확인 |
| `IUse%`만 높음 | 작은 log·cache·layer file 경로 조사 |
| Mount `ro` + kernel filesystem error | EBS·filesystem 복구 별도 결정 |
| `/var/lib/etcd` I/O error + API timeout | [160 Kubernetes control plane 진단](160-kubernetes-api-control-plane-and-node.md) |
| `df`와 `du` 차이 큼 + `lsof +L1` 결과 | Deleted-open file owner 확인 |

## 금지 경계·관련 문서

Containerd snapshot, kubelet·etcd data와 journal을 직접 삭제하지 않습니다. Volume resize와
filesystem grow도 별도 상태 변경입니다.

- **전체 outline**: [100 Kubernetes platform 핵심 진단 outline](100-kubernetes-platform-diagnostic-outline.md)
- **AWS EBS**: [110 AWS EC2·접근 진단](110-aws-ec2-and-access.md)
- **Node service**: [140 Linux node service 진단](140-systemd-journal-and-node-services.md)

## 공식 참고

- [GNU df](https://www.gnu.org/software/coreutils/manual/html_node/df-invocation.html)
- [findmnt(8)](https://man7.org/linux/man-pages/man8/findmnt.8.html)
- [du(1)](https://man7.org/linux/man-pages/man1/du.1.html)
