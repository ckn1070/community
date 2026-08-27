# Linux network 진단: interface·route·socket·DNS·egress

EC2 guest 안의 packet 경로를 interface → route → socket → DNS·egress 순서로 해석합니다.
AWS Security Group·route table과 Linux guest 상태를 섞지 않습니다.

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

probe 'Interface address' 'Primary interface가 UP이고 expected private IP를 갖는지 확인합니다.'
ip -brief address
report_status 'ip address' "$?"

probe 'Interface counter' 'RX·TX error와 dropped counter가 증가하는지 확인합니다.'
ip -s link
report_status 'ip link' "$?"

probe 'Main route' 'VPC local route와 default egress route의 gateway·interface를 확인합니다.'
ip route show table main
report_status 'ip route' "$?"

probe 'Policy rule' 'Main routing table보다 먼저 적용되는 별도 routing policy가 있는지 확인합니다.'
ip rule show
report_status 'ip rule' "$?"

probe 'Socket summary' 'TCP·UDP socket의 전체 사용량과 비정상 누적 상태를 확인합니다.'
ss -s
report_status 'ss summary' "$?"

probe 'Listening socket' 'SSH·Kubernetes API 등 필요한 port를 어떤 process가 listen하는지 확인합니다.'
sudo -n ss -lntp | sed -n '1,240p'
pipeline_status=("${PIPESTATUS[@]}")
printf '[result] ss exit code: %s; sed exit code: %s\n' \
  "${pipeline_status[0]}" "${pipeline_status[1]}"

probe 'Resolver state' 'Interface별 DNS server·domain과 현재 resolver 경로를 확인합니다.'
resolvectl status | sed -n '1,220p'
pipeline_status=("${PIPESTATUS[@]}")
printf '[result] resolvectl exit code: %s; sed exit code: %s\n' \
  "${pipeline_status[0]}" "${pipeline_status[1]}"

probe 'DNS lookup' 'System resolver가 github.com의 IPv4 주소를 반환하는지 확인합니다.'
getent ahostsv4 github.com | sed -n '1,20p'
pipeline_status=("${PIPESTATUS[@]}")
printf '[result] getent exit code: %s; sed exit code: %s\n' \
  "${pipeline_status[0]}" "${pipeline_status[1]}"

probe 'HTTPS egress' 'DNS 다음 단계인 TCP·TLS·HTTP egress가 제한 시간 안에 완료되는지 확인합니다.'
curl --connect-timeout 5 --max-time 10 \
  --silent --show-error --output /dev/null \
  --write-out='github_https=%{http_code} remote=%{remote_ip}\n' \
  https://github.com/
report_status 'curl' "$?"
)
```

`resolvectl`이 없으면 그 사실을 기록하고 `getent` 결과로 resolver 경계를 확인합니다.
Pipeline producer의 실패 여부는 상단 묶음이 출력한 producer exit code로 판정합니다. 잘리지
않은 출력이 추가로 필요할 때만 출력 제한 command를 제거하고 producer를 단독 실행합니다.

## 해석 순서

1. Interface가 expected private address와 `UP` state를 갖는지 봅니다.
2. Main route와 policy rule로 destination 경로를 확인합니다.
3. 필요한 port를 process가 listen하는지 봅니다.
4. Packet error·drop이 증가하는지 비교합니다.
5. DNS 성공과 TCP·TLS egress를 별도 단계로 나눕니다.

## `ip`: interface·address·route·rule 조회·관리 command

진단에서는 `ip`의 조회 subcommand만 사용합니다.

### Option·subcommand

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-brief address` | Interface·state·address를 한 줄로 압축 | Private IP와 interface가 존재하는가 |
| `-s link` | RX·TX packet counter 포함 | Error·drop이 증가하는가 |
| `route show table main` | Main routing table 조회 | Local VPC·default route가 있는가 |
| `rule show` | Routing table 선택 policy 조회 | Main 외 policy route가 개입하는가 |

### `-brief address` 출력

| Field | 뜻 | 이상 신호 |
| --- | --- | --- |
| Interface | `lo`, primary ENI 등 device 이름 | 예상 primary interface 누락 |
| State | `UP`, `DOWN`, `UNKNOWN` | Primary interface `DOWN` |
| Address/prefix | IPv4·IPv6와 prefix | 예상 VPC private IPv4 누락 |

Loopback `127.0.0.1`은 외부 접속 주소가 아닙니다.

### `-s link` 출력

| Field | 뜻 | 이상 신호 |
| --- | --- | --- |
| RX·TX bytes/packets | 누적 traffic | 장애 전후 traffic 단절·급증 |
| errors | Driver·frame·전송 오류 | 짧은 간격 재조회에서 증가 |
| dropped | Queue·policy 등으로 drop | 지속 증가하면 journal·AWS metric 비교 |
| overruns | 처리 queue 초과 | 부하와 함께 증가 |

### `route show` 출력

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `default` | 더 구체적인 route가 없을 때 사용 | Egress default가 있는가 |
| `via` | Next-hop gateway | VPC gateway가 맞는가 |
| `dev` | 나갈 interface | Primary interface인가 |
| `src` | 기본 source address | Expected private IP인가 |
| `scope link` | Gateway 없이 직접 도달 | VPC local prefix인가 |

### `rule show` 출력

| 출력 요소 | 뜻 | 이상 신호·다음 증거 |
| --- | --- | --- |
| Priority | 작은 번호부터 평가하는 rule 순서 | 예상하지 않은 우선 rule이 main route를 우회 |
| `from`·`to` | Source·destination address selector | 특정 Pod·host traffic만 다른 table을 사용하는가 |
| `lookup TABLE` | 선택할 routing table | `local`·`main`·`default` 외 table이 의도된 구성인가 |

Rule 자체만으로 packet의 next hop을 알 수 없습니다. 해당 rule이 선택한 table의
route와 함께 봅니다.

## `ss`: socket·listener 조회 command

### Option

| Option | 뜻 | 진단 질문 |
| --- | --- | --- |
| `-s` | 전체 socket summary | Established·time-wait가 평소보다 급증했는가 |
| `-l` | Listen socket만 | 필요한 port가 열려 있는가 |
| `-n` | Address·port를 숫자로 표시 | DNS/service name 변환 없이 exact port 확인 |
| `-t` | TCP socket | SSH·Kubernetes API TCP 확인 |
| `-p` | Socket owner process | 어떤 service가 listen하는가 |

`127.0.0.1:6443`은 local host만, `0.0.0.0:22`는 모든 IPv4 local address에서 listen합니다.
Security Group이 허용해도 listener가 없으면 접속할 수 없습니다.

### 출력 field

| Field | 뜻 | 이상 신호 |
| --- | --- | --- |
| `State` | Socket state | 필요한 listener에 `LISTEN` 없음 |
| `Recv-Q`·`Send-Q` | 수신·송신 queue | 지속적으로 큰 queue |
| `Local Address:Port` | Listen address·port | 22·6443 누락 또는 loopback에만 bind |
| `Process` | Socket owner PID·name | 기대 service가 아닌 process |

### `-s` summary 출력 field

| Field | 뜻 | 이상 신호·다음 증거 |
| --- | --- | --- |
| `Total` | 현재 전체 socket 수 | 평소 baseline 대비 지속 급증하면 protocol별 상태로 내려감 |
| `estab` | Established TCP connection 수 | 부하·request 수와 무관하게 증가하면 process·peer 상세 조회 |
| `timewait` | 종료 후 TIME-WAIT 상태 connection 수 | 급증이 반복되면 connection churn·client reuse 확인 |
| `orphaned` | User process와 연결되지 않은 TCP socket 수 | 지속 증가하면 connection 종료·kernel resource 경계 확인 |

`ss -s`는 순간 요약이므로 절대 임계값 하나로 판정하지 않습니다. 같은 부하
조건의 baseline과 비교하고, 증가한 상태를 상세 socket·owner process 조회로 이어갑니다.

## `resolvectl`·`getent`: DNS 경로 조회 command

### Subcommand·option

| Command | 뜻 | 진단 질문 |
| --- | --- | --- |
| `resolvectl status` | Interface별 DNS server·search domain | 어느 resolver로 질의하는가 |
| `getent ahostsv4 NAME` | libc/NSS 경로의 IPv4 name resolution | Application과 같은 경로로 해석되는가 |

DNS 성공은 TCP route·remote service 성공을 보장하지 않습니다.

### 출력 field

| Command·field | 뜻 | 이상 신호 |
| --- | --- | --- |
| `resolvectl` DNS Servers | Interface별 resolver | 기대 resolver 누락·잘못된 interface |
| `resolvectl` DNS Domain | Search·route domain | 의도하지 않은 domain routing |
| `getent` address | NSS가 반환한 IPv4 | 결과 없음·예상 밖 address |

## `curl`: bounded HTTPS egress probe

### Option

| Option | 뜻 | 안전·판정 경계 |
| --- | --- | --- |
| `--connect-timeout 5` | Connection 성립 제한 | 접속 단계 무한 대기 방지 |
| `--max-time 10` | 전체 요청 제한 | DNS·TLS·HTTP 전체 상한 |
| `--output /dev/null` | Body 폐기 | 불필요한 content 미수집 |
| `--write-out` | HTTP code·remote IP 출력 | 어느 endpoint까지 갔는지 확인 |

### 출력 field

| Field | 뜻 | 진단 질문 |
| --- | --- | --- |
| `github_https` | 최종 HTTP status code | DNS·TCP·TLS 뒤 HTTP 응답까지 왔는가 |
| `remote` | 연결된 remote IP | DNS 결과 중 어느 address를 사용했는가 |
| Stderr | DNS·connect·TLS 실패 단계 | 어느 network 단계에서 중단됐는가 |

HTTP 2xx가 아니어도 DNS·TCP·TLS가 진행됐을 수 있으므로 stderr·remote IP와 함께 봅니다.

## 증거 조합과 다음 분기

| 함께 보이는 증거 | 다음 분기 |
| --- | --- |
| AWS 2/2 정상 + private address 없음 | Guest interface·DHCP |
| Address 정상 + default route 없음 | Guest route |
| Route 정상 + 22 listener 없음 | [140 Linux node service 진단](140-systemd-journal-and-node-services.md) |
| 6443 listener 있음 + readyz timeout | [160 Kubernetes control plane 진단](160-kubernetes-api-control-plane-and-node.md) |
| DNS 실패 + direct IP route 정상 | Resolver |
| DNS 정상 + HTTPS timeout | Egress route·SG·remote path |

## 금지 경계·관련 문서

Address·route·rule을 추가하거나 interface를 down/up 하지 않고 Security Group을 확대하지 않습니다.

- **전체 outline**: [100 Kubernetes platform 핵심 진단 outline](100-kubernetes-platform-diagnostic-outline.md)
- **AWS 경계**: [110 AWS EC2·접근 진단](110-aws-ec2-and-access.md)
- **API 경계**: [160 Kubernetes control plane 진단](160-kubernetes-api-control-plane-and-node.md)

## 공식 참고

- [ip(8)](https://man7.org/linux/man-pages/man8/ip.8.html)
- [ss(8)](https://man7.org/linux/man-pages/man8/ss.8.html)
- [systemd-resolved](https://www.freedesktop.org/software/systemd/man/latest/systemd-resolved.service.html)
