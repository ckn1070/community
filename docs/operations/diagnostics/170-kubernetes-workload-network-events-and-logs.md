# Kubernetes workload 진단: Pod·CNI·DNS·Service·event·log

Workload가 scheduling, image pull, Pod network, process start, readiness, Service routing 중 어느
단계에서 멈췄는지 찾습니다. 먼저 전체 상태를 좁히고, 문제 object 하나의 event와 bounded
log를 읽습니다.

**기본 실행 위치:** SSH로 접속한 target EC2의 Bash terminal

**기본 시작 directory:** 제한 없음

## 상단 빠른 확장 조회 (bounded preset)

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

probe 'Cluster Pod' '전체 namespace의 Pod readiness·status·restart·Node·IP를 확인합니다.'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s get pods -A -o wide
report_status 'kubectl pods' "$?"

probe 'Workload owner' 'Deployment·StatefulSet·DaemonSet·Job의 desired와 완료 상태를 확인합니다.'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s get deployments,statefulsets,daemonsets,jobs -A
report_status 'kubectl workload owners' "$?"

probe 'Recent event' 'Scheduler·image pull·CNI·mount·probe 실패를 최근 발생 순서로 확인합니다.'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s get events -A \
  --sort-by=.metadata.creationTimestamp | tail -n 180
pipeline_status=("${PIPESTATUS[@]}")
printf '[result] kubectl events exit code: %s; tail exit code: %s\n' \
  "${pipeline_status[0]}" "${pipeline_status[1]}"

probe 'Calico Pod' 'Pod network를 구성하는 Calico component의 readiness와 Node 분포를 확인합니다.'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n calico-system get pods -o wide
report_status 'kubectl Calico pods' "$?"

probe 'CoreDNS Pod' 'Cluster DNS component의 readiness·restart·Node 분포를 확인합니다.'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n kube-system \
  get pods -l k8s-app=kube-dns -o wide
report_status 'kubectl CoreDNS pods' "$?"

probe 'Application Pod·Service' 'Application Pod readiness와 Service selector 경계를 함께 확인합니다.'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n car-community \
  get pods,services -o wide
report_status 'kubectl application resources' "$?"

probe 'Application EndpointSlice' 'Service가 ready Pod 주소를 endpoint로 선택했는지 확인합니다.'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n car-community \
  get endpointslices.discovery.k8s.io \
  -o 'jsonpath={range .items[*]}{"slice="}{.metadata.name}{" service="}{.metadata.labels.kubernetes\.io/service-name}{"\n"}{range .endpoints[*]}{"  endpoint="}{range .addresses[*]}{.}{","}{end}{" ready="}{.conditions.ready}{" serving="}{.conditions.serving}{" terminating="}{.conditions.terminating}{" target="}{.targetRef.kind}{"/"}{.targetRef.name}{"\n"}{end}{end}'
report_status 'kubectl EndpointSlice' "$?"
)
```

Event 조회 실패 여부는 상단 묶음이 출력한 `kubectl events exit code`로 판정합니다. 180행
이전 event까지 추가로 필요할 때만 `| tail -n 180`을 제거해 `kubectl`을 단독으로
재실행합니다.

## 해석 순서

1. Pod `STATUS`, `READY`, `NODE`, `IP`에서 멈춘 lifecycle 단계를 찾습니다.
2. 같은 object의 event로 scheduler·pull·CNI·mount·probe 실패를 분류합니다.
3. Container current·previous log에서 process 원인을 확인합니다.
4. Calico와 CoreDNS를 Node·Pod 상태와 연결합니다.
5. Ready Pod가 Service EndpointSlice에 들어가는지 확인합니다.

## Workload lifecycle

```text
Deployment·Job desired state
→ scheduler가 Node 선택
→ kubelet이 image pull
→ CNI가 Pod network 구성
→ container process start
→ readiness 성공
→ Service EndpointSlice에 ready endpoint 등록
```

앞 단계가 실패하면 뒤 단계는 정상일 수 없습니다.

## `kubectl`: workload object·event·log 조회 command

`kubectl`은 Kubernetes API object를 조회하고 container log를 요청하는 client입니다. 이
문서에서는 `get`과 `logs`만 사용하고 object를 변경하지 않습니다.

### `get`: object 목록·요약 상태 조회

#### 기본 명령

먼저 resource와 범위만 정한 기본형으로 전체 출력을 확인합니다. `-A`, `-o wide`와 selector는
필요한 범위·표현에 따라 뒤에 붙입니다.

```bash
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get pods
```

#### 선택 option·resource

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `pods -A` | 모든 namespace의 Pod | 실패가 platform 전체인가 application 하나인가 |
| `deployments,statefulsets,daemonsets,jobs -A` | Workload owner 상태 | Desired·available·completed 수가 맞는가 |
| `-o wide` | Node·Pod IP 등 추가 column | 특정 Node·주소에 집중되는가 |
| `-n NAMESPACE` | Namespace 범위 제한 | 문제 application만 좁혔는가 |

#### Pod column

| Column | 뜻 | 이상 신호 |
| --- | --- | --- |
| `READY` | Ready container 수/전체 | `0/1`, 일부 sidecar 미준비 |
| `STATUS` | Phase·대표 waiting reason | `Pending`, `ImagePullBackOff`, `CrashLoopBackOff` |
| `RESTARTS` | Restart 누적 | 최근 계속 증가 |
| `IP` | Pod IP | Running 기대인데 비어 있음 |
| `NODE` | 배치 Node | 비어 있으면 scheduling 전, 특정 Node에만 실패 가능 |

| 상태·reason | 먼저 볼 증거 |
| --- | --- |
| `Pending` + Node 없음 | Scheduler event·resource·taint·affinity |
| `ContainerCreating` 지속 | CNI·volume·image unpack event |
| `ErrImagePull`·`ImagePullBackOff` | Exact image digest·ECR provider·event |
| `CrashLoopBackOff` | Current/previous log·exit code |
| `Running` + readiness 실패 | Probe와 dependency |
| `Evicted` | Node pressure·event |

#### Workload owner 출력 field

| Resource·field | 뜻 | 이상 신호 |
| --- | --- | --- |
| Deployment `READY` | Ready replica/desired replica | Desired보다 작고 회복되지 않음 |
| Deployment `UP-TO-DATE`·`AVAILABLE` | 최신 spec replica·사용 가능 replica | 둘 중 하나가 desired보다 작음 |
| StatefulSet `READY` | Ready Pod/desired Pod | 순서 있는 rollout이 멈춤 |
| DaemonSet `DESIRED`·`READY`·`AVAILABLE` | 대상 Node 수와 준비 수 | 특정 Node에서만 미준비 |
| Job `COMPLETIONS`·`DURATION` | 완료 횟수와 실행 시간 | 실패·장기 실행·완료 정체 |

#### Event 출력 field

| Field | 뜻 | 진단 질문 |
| --- | --- | --- |
| `TYPE` | `Normal`·`Warning` 분류 | Warning이 반복되는가 |
| `REASON` | Scheduler·kubelet·controller가 붙인 짧은 원인 | 어느 lifecycle 단계가 실패했는가 |
| `OBJECT` | Event 대상 resource | 실패한 Pod·Node·owner가 무엇인가 |
| `MESSAGE` | 구체 원인·대상 값 | Pull·CNI·mount·probe 중 무엇인가 |
| `LAST SEEN`·`COUNT` | 최근 시각·반복 횟수 | 일회성인가 계속 재발하는가 |

Event는 영구 audit log가 아니며 retention 뒤 사라질 수 있습니다. `TYPE=Normal`만으로
정상을 확정하지 않고 현재 object 상태와 함께 봅니다.

#### Service·EndpointSlice 출력 field

| Resource·field | 뜻 | 이상 신호 |
| --- | --- | --- |
| Service `CLUSTER-IP` | Cluster 내부 virtual IP | `None`이 의도되지 않았는데 headless 상태 |
| Service `PORT(S)` | Service port와 protocol | Git manifest의 port와 다름 |
| EndpointSlice `slice`·`service` | Slice 이름과 소유 Service label | 기대 Service의 Slice가 없음 |
| EndpointSlice `endpoint` | 선택된 endpoint address | Ready Pod가 있는데 비어 있음 |
| EndpointSlice `ready` | Service traffic 수신 준비 여부 | 명시적 `false`; 빈 값은 API 계약상 `true`로 해석 |
| EndpointSlice `serving`·`terminating` | 현재 응답 가능 여부·종료 진행 여부 | `serving=false` 또는 의도하지 않은 `terminating=true` |
| EndpointSlice `target` | Endpoint가 가리키는 Pod 등 object | 기대 Pod가 아니거나 비어 있음 |

### `get pod`·`get events`: 대상 Pod 상태와 관련 event 조회

#### 기본 명령

`namespace`와 `pod_name`은 앞선 `kubectl get pods -A` 출력에서 고릅니다. Pod 원문 전체가
필요하면 먼저 아래 기본형을 실행하고, 필요한 field만 좁힐 때 `-o jsonpath=...`를 붙입니다.

```bash
namespace='<Namespace>'
pod_name='<Pod 이름>'

sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl -n "${namespace}" get pod "${pod_name}"

sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl -n "${namespace}" get events
```

#### 선택 resource·option

| 요소 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `get pod POD` | 문제 Pod 하나 선택 | 전체 목록에서 좁힌 대상이 맞는가 |
| `-o jsonpath=...` | 명시한 metadata·status field만 출력 | Environment 없이 lifecycle을 볼 수 있는가 |
| `get events` | Event resource 조회 | 대상 Pod 관련 실패 reason이 남아 있는가 |
| `--field-selector` | Pod kind·name으로 event 제한 | 다른 object event와 섞이지 않았는가 |
| `--sort-by=.metadata.creationTimestamp` | 생성 시각순 정렬 | 실패 순서를 복원할 수 있는가 |

#### 상태·event 조합 예시 (bounded preset)

```bash
namespace='<Namespace>'
pod_name='<Pod 이름>'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n "${namespace}" \
  get pod "${pod_name}" \
  -o 'jsonpath={"name="}{.metadata.name}{"\nnode="}{.spec.nodeName}{"\nphase="}{.status.phase}{"\n"}{range .status.conditions[*]}{"condition\t"}{.type}{"\t"}{.status}{"\t"}{.reason}{"\t"}{.message}{"\n"}{end}{range .status.initContainerStatuses[*]}{"init\t"}{.name}{"\tready="}{.ready}{"\trestarts="}{.restartCount}{"\twaiting="}{.state.waiting.reason}{"\tterminated="}{.state.terminated.reason}{"\texitCode="}{.state.terminated.exitCode}{"\n"}{end}{range .status.containerStatuses[*]}{"container\t"}{.name}{"\tready="}{.ready}{"\trestarts="}{.restartCount}{"\twaiting="}{.state.waiting.reason}{"\tterminated="}{.state.terminated.reason}{"\texitCode="}{.state.terminated.exitCode}{"\n"}{end}'
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n "${namespace}" \
  get events \
  --field-selector "involvedObject.kind=Pod,involvedObject.name=${pod_name}" \
  --sort-by=.metadata.creationTimestamp | tail -n 120
```

첫 명령은 Pod의 metadata·status와 `.spec.nodeName`만 명시적으로 조회합니다.
`.spec.containers[*].env`와 `.envFrom`은 조회하지 않으므로 literal environment와
Secret·ConfigMap reference name·key를 출력하지 않습니다.

#### 출력 field

| 출력 | 뜻 | 진단 질문 |
| --- | --- | --- |
| `node`·`phase` | 배치 Node와 Pod lifecycle phase | Scheduling 전인가 실행 단계인가 |
| `condition` | Condition type·status·reason·message | 어느 condition이 false인가 |
| `init`·`container` | Ready·restart·waiting·terminated·exit code | 어떤 container가 어느 상태에서 멈췄는가 |
| 관련 Event | 해당 Pod의 시간순 event | Scheduler→pull→CNI→start 중 어디까지 갔는가 |

이 하단 개별 조회는 상단 묶음과 달리 `PIPESTATUS`를 출력하지 않습니다. `kubectl` 실패
여부를 별도로 확인해야 하면 `| tail -n 120`을 제거하고 같은 명령을 다시 실행합니다.

### `logs`: container stdout·stderr 조회

#### 기본 명령

아래 명령은 선택한 container의 **현재 instance에서 kubelet이 제공할 수 있는 현재 log
file 전체**를 요청합니다. 시간·행 제한은 기본값이 아니며 필요할 때 선택 옵션으로
붙입니다. Pod에 container가 하나뿐이면 `-c`를 생략할 수 있지만, 운영 진단에서는 대상을
명확히 하기 위해 넣는 편이 안전합니다.

```bash
namespace='<Namespace>'
pod_name='<Pod 이름>'
container_name='<Container 이름>'

sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl -n "${namespace}" \
  logs "${pod_name}" \
  -c "${container_name}"
```

`--request-timeout=10s`는 bounded snapshot에는 유용하지만 `--follow`에 붙이면 stream도 해당
timeout 뒤 종료될 수 있으므로 기본 명령에는 넣지 않습니다.

#### 대상·stream 선택 옵션

| Option | 뜻 | 조합 기준 |
| --- | --- | --- |
| `-c NAME`, `--container=NAME` | 특정 container 선택 | Multi-container Pod 또는 JSON application log는 명시 권장 |
| `--all-containers=true` | 선택된 Pod의 모든 container log | 서로 다른 format을 섞을 수 있으므로 JSON `jq` 처리와 함께 쓰지 않음 |
| `-l SELECTOR`, `--selector=SELECTOR` | Label이 일치하는 여러 Pod 선택 | Pod 이름 대신 사용하며 source 구분을 위해 `--prefix` 결합 |
| `--all-pods=true` | 지정한 workload resource의 모든 Pod 선택 | `deployment/NAME` 같은 resource target과 사용 |
| `-p`, `--previous` | 같은 Pod 안에서 직전에 종료된 container instance 조회 | 회전 파일이 아니라 restart 전 instance이며 존재하지 않을 수 있음 |
| `-f`, `--follow` | 새 log를 계속 stream | 종료하려면 `Ctrl-C`; 장시간 수집에는 별도 중앙 log backend가 필요 |
| `--ignore-errors=true` | Follow 중 개별 Pod 오류를 non-fatal로 처리 | 여러 Pod follow에서 일부 stream 단절 뒤 나머지를 계속 볼 때 사용 |

#### 범위·표시·동시성 옵션

| Option | 뜻 | 조합 기준 |
| --- | --- | --- |
| `--since=DURATION` | 현재 시각 기준 상대 범위, 예: `60m` | `--since-time`과 함께 사용할 수 없음 |
| `--since-time=RFC3339` | 지정 절대 시각 이후, 예: `2026-08-27T01:00:00Z` | 사건 시각이 확정됐을 때 사용; `--since`와 배타적 |
| `--tail=N` | 현재 log file의 마지막 N행 | Selector가 없으면 기본 `-1`(제한 없음), selector가 있으면 기본 10행 |
| `--limit-bytes=N` | 반환할 최대 byte 수 | 행 중간이 잘릴 수 있으므로 JSON parse·stack trace 조사에는 주의 |
| `--timestamps=true` | 각 행 앞에 kubelet의 RFC3339 timestamp 추가 | Producer timestamp가 없는 plain text에 유용; JSON `jq` 처리 전에는 사용 금지 |
| `--prefix=true` | 각 행 앞에 Pod·container source 추가 | 여러 source를 합칠 때 유용; JSON `jq` 처리 전에는 사용 금지 |
| `--max-log-requests=N` | Selector follow의 동시 요청 수 | 기본 5; Node/API 부하를 고려해 필요한 만큼만 높임 |
| `--pod-running-timeout=DURATION` | 하나 이상의 Pod가 Running이 되기를 기다리는 시간 | 기본 20초; 시작 중인 workload 조회에 사용 |

`--insecure-skip-tls-verify-backend`는 kubelet serving certificate 검증을 건너뛰므로 일반 진단
옵션으로 사용하지 않습니다. 인증 문제의 원인과 위험을 별도로 확정한 경우에만 검토합니다.

#### 조합 예시 (bounded preset)

현재 container의 최근 60분·240행을 여러 container source prefix와 함께 보는 예시입니다.
숫자는 고정 규칙이 아니라 첫 화면을 제한하기 위한 preset입니다.

```bash
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n "${namespace}" \
  logs "${pod_name}" --all-containers=true \
  --since=60m --tail=240 --prefix=true
```

CrashLoop 원인은 container를 하나 지정해 이전 instance를 조회합니다.

```bash
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl --request-timeout=10s -n "${namespace}" \
  logs "${pod_name}" -c "${container_name}" \
  --previous --tail=160
```

실시간으로 볼 때에는 snapshot용 request timeout을 제거합니다.

```bash
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl -n "${namespace}" \
  logs "${pod_name}" -c "${container_name}" \
  --follow
```

#### Production ECS JSON을 `jq`로 읽기

Production profile은
[`application-production.yaml`](../../../src/main/resources/application-production.yaml)의
`logging.structured.format.console: ecs`에 따라 application container가 한 행에 JSON object
하나를 기록합니다. 이때 `jq` 앞의 `kubectl logs`에는 JSON을 깨뜨리는 `--prefix`와
`--timestamps`를 붙이지 않고, 다른 format의 sidecar가 섞이지 않도록 `-c application`을
명시합니다.

먼저 JSON 형식 자체를 엄격하게 검사합니다. Nonzero이면 모든 행이 JSON object라는 전제가
깨진 것이므로 아래 human-readable 변환에서 fragment를 별도로 표시해야 합니다.

```bash
(
set -o pipefail
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl -n "${namespace}" logs "${pod_name}" -c application \
  | jq -e 'if type == "object" then true else error("expected JSON object") end' \
    >/dev/null
)
```

사람이 읽는 형태로 바꿀 때에는 원문 행을 먼저 보존하고 parse 실패를 버리지 않습니다.
`fromjson?`는 실패한 행을 조용히 제거하므로 stack trace fragment나 잘린 JSON을 놓칠 수 있어
사용하지 않습니다. `@timestamp`의 UTC 값을 KST로 바꾸고 ECS의 `error.stack_trace`가 있으면
message 뒤에 이어 붙입니다.

```bash
(
set -o pipefail
sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl -n "${namespace}" logs "${pod_name}" -c application \
  | TZ=Asia/Seoul jq -Rr '
      def kst:
        . as $timestamp
        | try (
            $timestamp
            | sub("\\.[0-9]+Z$"; "Z")
            | fromdateiso8601
            | strflocaltime("%Y-%m-%d %H:%M:%S KST")
          ) catch $timestamp;

      . as $raw
      | (try fromjson catch null) as $log
      | if ($log | type) != "object" then
          "[UNPARSED/FRAGMENT] \($raw)"
        else
          "\(($log["@timestamp"] // "timestamp-missing") | kst) "
          + "[\($log.log.level // "-")] \($log.log.logger // "-")\n"
          + "\($log.message // "")"
          + (if ($log.error.stack_trace // "") != ""
             then "\n" + $log.error.stack_trace
             else ""
             end)
          + "\n"
        end
    '
)
```

실시간 JSON log에는 같은 filter에 `kubectl logs --follow`와 `jq --unbuffered -Rr`를
사용합니다. `jq` parse error가 첫 행부터 발생하면 `--prefix`, `--timestamps`, shell에 복사된
HTML entity, 또는 JSON이 아닌 container가 섞였는지 먼저 확인합니다.

#### Log 시간과 보존 경계

- ECS JSON의 `@timestamp`는 application producer 시각입니다. `--timestamps=true`는 kubelet이
  각 CRI log record 앞에 붙이는 시각이며 둘은 출처가 다릅니다.
- `kubectl logs`는 kubelet이 관리하는 **가장 최신 log file만** 반환합니다. `--tail=-1`이나
  시간 제한 제거도 이미 회전된 파일까지 합치지는 않습니다.
- `--previous`는 같은 Pod의 직전 container instance를 읽는 옵션이지 회전 파일을 읽는 옵션이
  아닙니다. 기본적으로 kubelet은 종료된 container instance 하나의 log를 보존합니다.
- Node에서 kubelet은 기본적으로 container log를
  `/var/log/pods/<namespace>_<pod>_<uid>/<container>/` 아래에 두고 `/var/log/containers`에는
  접근용 symlink를 둡니다. Directory는 file이 아니므로 `cat` 대신 먼저 `find`·`ls`로
  내부 file을 고릅니다.

Kubelet 설정 파일에 rotation key가 없으면 “회전하지 않음”이 아니라 kubelet 기본값을
사용한다는 뜻입니다. 현재 config file과 key 존재 여부를 먼저 확인합니다.

```bash
sudo -n systemctl cat kubelet.service
sudo -n grep -E \
  '^(containerLogMaxSize|containerLogMaxFiles|containerLogMaxWorkers|containerLogMonitorInterval):' \
  /var/lib/kubelet/config.yaml
```

공식 기본값은 `containerLogMaxSize: 10Mi`, `containerLogMaxFiles: 5`입니다. 실제 file inventory는
Pod UID로 정확한 directory를 고른 뒤 확인합니다.

```bash
pod_uid="$(sudo -n env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl -n "${namespace}" get pod "${pod_name}" \
  -o jsonpath='{.metadata.uid}')"
pod_log_dir="/var/log/pods/${namespace}_${pod_name}_${pod_uid}/${container_name}"

sudo -n find "${pod_log_dir}" -maxdepth 1 -type f \
  -printf '%TY-%Tm-%Td %TH:%TM:%TS %s %p\n' \
  | sort
```

현재·회전 file을 읽을 때에는 위 inventory에서 정확한 path를 `log_file`에 복사합니다.
압축 여부에 따라 reader를 구분하며, 원인을 모르는 상태에서 file을 삭제·truncate하지 않습니다.

```bash
log_file='<위 find 출력에서 고른 정확한 file path>'
sudo -n less "${log_file}"
# .gz file이면: sudo -n zless "${log_file}"
```

이 Node raw file은 JSON payload 앞에 CRI timestamp·`stdout|stderr`·`F|P` tag가 붙습니다.
따라서 file 전체를 그대로 `jq`에 넣지 않고 CRI prefix와 partial record 여부를 먼저 해석합니다.

Node·Pod 삭제 뒤에도 장기 조회가 필요하다면 node local file에 의존하지 않고 별도의
cluster-level logging backend가 필요합니다. Kubernetes 자체는 장기 log storage를 제공하지
않습니다.

#### 출력·민감정보 경계

| 출력 | 뜻 | 진단 질문 |
| --- | --- | --- |
| Producer `@timestamp`·message | Application이 만든 사건 시각과 내용 | Incident 시각·request 흐름과 맞는가 |
| Kubelet timestamp·source prefix | CRI record 시각과 Pod·container 출처 | Plain text 또는 여러 source를 구분해야 하는가 |
| `[UNPARSED/FRAGMENT]` | JSON parse가 불가능한 원문 행 | 회전·byte 제한·prefix 때문에 잘렸는가, producer가 비JSON을 썼는가 |
| Token·email·URL·query 가능성 | Producer가 기록한 민감 조각 | Local mode `0600` 보존·공유 전 redaction을 지켰는가 |

이 명령은 Secret `.data`나 container environment를 직접 조회하지 않지만, application이
stdout·stderr에 token 조각·email·URL·query를 기록했다면 그대로 나타날 수 있습니다.
결과는 local mode `0600` 파일에만 보존하고 외부 공유 전에 민감 조각을 가립니다. Log
producer의 redaction이 확인되지 않은 상태에서 원문을 외부에 붙여 넣지 않습니다.

## `Calico`: Pod network 구성

| 증거 | 의미 후보 |
| --- | --- |
| Calico Pod가 특정 Node에서만 실패 | 해당 Node interface·route·kubelet 문제 |
| `NetworkPluginNotReady` | CNI 초기화 미완료 |
| Sandbox create 실패 | CNI configuration·runtime 경계 |
| Pod IP allocation 실패 | IP pool·node network 경계 |

`/etc/cni/net.d`를 삭제하거나 Calico manifest를 재적용하지 않습니다.

## `CoreDNS`: Cluster DNS 경로

```text
Pod resolver
→ kube-dns Service ClusterIP
→ CoreDNS ready EndpointSlice
→ upstream resolver 또는 cluster Service
```

CoreDNS Pod가 Ready여도 Pod network·Service routing·upstream resolver가 실패할 수 있습니다.
Test Pod 생성은 mutation이므로 이 문서에서 실행하지 않습니다.

## `Service`·`EndpointSlice`: selector에서 ready endpoint까지

| 요소 | 뜻 | 이상 신호 |
| --- | --- | --- |
| Service selector | 대상 Pod label 조건 | 실제 Pod label과 불일치 |
| Service port·targetPort | Client port와 Pod port 연결 | Container listener와 불일치 |
| EndpointSlice `slice`·`service` | Slice와 소유 Service의 연결 | 기대 Service label과 불일치 |
| EndpointSlice `endpoint`·`target` | 선택된 address와 대상 object | 비어 있거나 기대 Pod와 다름 |
| Endpoint condition `ready` | Traffic 수신 준비 여부 | 명시적 `false`; 빈 값은 API 계약상 `true` |
| Endpoint condition `serving`·`terminating` | 실제 응답 가능 여부·종료 진행 여부 | `serving=false` 또는 의도하지 않은 `terminating=true` |

Service가 있어도 ready endpoint가 없으면 traffic을 전달할 Pod가 없습니다. Live object를
edit하지 않고 Git manifest와 비교합니다.

## 증거 조합과 다음 분기

| 함께 보이는 증거 | 첫 경계 |
| --- | --- |
| Pod IP 없음 + sandbox/CNI event | Calico·Node network |
| `ImagePullBackOff` + ECR auth event | ECR credential provider·IAM·exact digest |
| Exit nonzero + previous log | Application process·configuration |
| Ready Pod 있음 + endpoint 없음·`ready=false` | Service selector·readiness |
| Endpoint 정상 + DNS 실패 | CoreDNS·Service network |

## 금지 경계·관련 문서

Test Pod 생성, CNI file 삭제, manifest 재적용, live workload edit을 하지 않습니다.

- **전체 outline**: [100 Kubernetes platform 핵심 진단 outline](100-kubernetes-platform-diagnostic-outline.md)
- **API·Node**: [160 Kubernetes control plane 진단](160-kubernetes-api-control-plane-and-node.md)
- **GitOps 복구**: [420 GitOps 배포와 rollback](../420-gitops-deployment-and-rollback-runbook.md)

## 공식 참고

- [Debug Pods](https://kubernetes.io/docs/tasks/debug/debug-application/debug-pods/)
- [kubectl logs](https://kubernetes.io/docs/reference/kubectl/generated/kubectl_logs/)
- [Kubernetes logging architecture](https://kubernetes.io/docs/concepts/cluster-administration/logging/)
- [Service and EndpointSlice](https://kubernetes.io/docs/concepts/services-networking/service/)
