# Catalog Redis 활성화·credential runbook

이 Procedure는 PostgreSQL을 원본으로 유지하는 Catalog 모델 상세 Redis cache의 staging
credential 최초 생성·동일 입력 재수렴·rotation, flag 활성화와 복구를 소유합니다. Repository
후보 검증이나 문서 작성만으로 live 실행 권한이 생기지 않습니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Redis desired state가 승인된 뒤 staging에 처음 연결하거나 세 ACL credential 중 하나를 rotation할 때 실행합니다. |
| 누가 | Repository 변경·merge 권한과 target cluster admin 접근 권한을 가진 운영자가 실행합니다. |
| 어디서 | Source·credential 준비는 mode `0600`인 `.env`가 있는 local repository root, Kubernetes mutation·관찰은 SSH stdin으로 연결한 target cluster에서 수행합니다. |
| 입력 | Target SSH host/key, Application·Namespace는 current tracked source에서 확인합니다. `.env`에는 application·operation·probe 비밀번호가 서로 다른 32 bytes 이상 값으로 준비돼야 합니다. 기능 확인에는 공개 API base URL과 조회 가능한 모델 public UUID가 필요합니다. |
| 완료 | AppProject 최소 permission, 두 Secret schema, Redis와 application Ready, authenticated PING, flag-off DB-only, flag-on cold/warm 동일 응답과 1~300초 TTL을 확인합니다. |
| 중단·복구 | 어느 단계든 실패하면 flag를 `false`로 유지하거나 되돌립니다. Secret 부분 적용은 삭제하지 않고 같은 입력으로 재수렴하며 PostgreSQL data와 Redis key를 scan·flush하지 않습니다. |

## 고정 역할과 Secret 경계

| 역할 | Cluster에 저장하는 값 | 허용 command·key | 소비자 |
| --- | --- | --- | --- |
| `application` | Username·plaintext password를 application Secret에, password hash를 server ACL file에 저장 | 모델 상세 v1 prefix의 `GET`, TTL을 포함한 `SET`, `DEL`, `PING` | Application Pod |
| `operation` | Password hash만 server ACL file에 저장. Plaintext는 local `.env`에만 유지 | 같은 prefix의 `TTL`, `INFO commandstats`, `INFO stats`, `PING`. `MEMORY PURGE`·`LATENCY RESET`을 포함한 다른 command는 거부 | 운영자가 Redis Pod 안에서 stdin으로 인증 |
| `probe` | Password hash, username·plaintext password를 server Secret에 저장 | `PING` | Redis startup/readiness/liveness probe |
| `default` | 없음 | `off` | 소비자 없음 |

Git에는 Secret manifest, ACL hash, password와 생성된 임시 파일을 남기지 않습니다. Application은
`car-community-catalog-redis-application`만 참조하고 Redis Pod는
`car-community-catalog-redis-server`만 참조합니다.

## 1. Source와 입력 확인

**실행 위치:** Local terminal

**시작 directory:** Repository root

```bash
set -euo pipefail

# [1/4] 현재 branch와 전달할 script 변경 상태를 확인합니다.
# 확인: 승인할 exact 변경만 표시됩니다.
printf '\n\n========== [1/4] tracked source 상태 ==========\n\n'
git status --short --branch

# [2/4] Staging overlay가 오류 없이 render되는지 확인합니다.
# 확인: 출력 없이 exit 0입니다.
printf '\n\n========== [2/4] staging render ==========\n\n'
kubectl kustomize deploy/overlays/stg >/dev/null

# [3/4] Redis manifest와 기존 test support contract를 검증합니다.
# 확인: Gradle BUILD SUCCESSFUL입니다.
printf '\n\n========== [3/4] Redis manifest contract ==========\n\n'
./gradlew test --tests '*RedisDeploymentManifestContractTest' \
  --tests '*PostgreSqlIntegrationTestSupportContractTest'

# [4/4] Redis credential entrypoint contract를 검증합니다.
# 확인: script test PASS입니다.
printf '\n\n========== [4/4] Redis credential contract ==========\n\n'
scripts/operations/application/tests/test-reconcile-catalog-redis-secrets.sh
```

하나라도 실패하면 Secret이나 workload를 변경하지 않습니다. Source 후보가 통과하면
[150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)로 현재 commit의
Redis credential entrypoint를 target EC2에 전달합니다.

직접 관리한 `.env`와 target을 같은 local shell에서 준비합니다.

```bash
# [1/2] .env가 현재 사용자 소유 regular file·mode 0600인지 확인하고 tracing 없이 읽습니다.
# 확인: 모든 test가 exit 0이며 값은 출력되지 않습니다.
printf '\n\n========== [1/2] credential source ==========\n\n'
set -euo pipefail
set +x
test -f .env
test ! -L .env
test "$(stat -f '%u' .env)" = "$(id -u)"
test "$(stat -f '%Lp' .env)" = 600
. ./.env
typeset +x \
  STG_CATALOG_REDIS_APPLICATION_PASSWORD \
  STG_CATALOG_REDIS_OPERATION_PASSWORD \
  STG_CATALOG_REDIS_PROBE_PASSWORD

# [2/2] 운영자가 current source에서 확인한 target과 기능 확인 입력을 변수에 넣습니다.
# 확인: 다음 source 대조 결과와 정확히 일치합니다.
printf '\n\n========== [2/2] target 입력 ==========\n\n'
TARGET_SSH_HOST='<target host>'
TARGET_SSH_KEY='<private key path>'
TARGET_NAMESPACE='<Application destination namespace>'
TARGET_API_BASE_URL='<staging public API base URL>'
TARGET_MODEL_ID='<조회 가능한 model public UUID>'
```

`TARGET_NAMESPACE`는 tracked Application destination과 같은 `car-community`여야 합니다. API base와
model ID는 기능 확인 시점의 실제 staging 값입니다. 하나로 정해지지 않으면 중단합니다.

## 2. AppProject permission 선적용

**실행 위치:** Local terminal에서 SSH stdin으로 target cluster에 적용·조회

**시작 directory:** Repository root

Redis workload sync 전에 overlay 밖 bootstrap owner의 tracked AppProject에
`networking.k8s.io/NetworkPolicy`가 exact kind로 하나만 추가됐는지 적용·확인합니다.

```bash
# [1/2] Tracked AppProject를 target cluster에 server-side apply합니다.
printf '\n\n========== [1/2] AppProject 적용 ==========\n\n'
env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  'sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --server-side -f -' \
  < scripts/bootstrap/gitops/manifests/car-community-appproject.yaml

# [2/2] 허용 목록에 exact NetworkPolicy만 추가됐는지 출력합니다.
printf '\n\n========== [2/2] AppProject permission 확인 ==========\n\n'
env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  'sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl get appproject car-community -n argocd -o jsonpath='"'"'{range .spec.namespaceResourceWhitelist[*]}{.group}{"/"}{.kind}{"\n"}{end}'"'"''
```

출력에 `networking.k8s.io/NetworkPolicy`가 있고 `Secret` 또는 wildcard가 없어야 합니다.

## 3. 역할별 Secret 생성·재수렴

**실행 위치:** Local terminal에서 NUL-delimited stdin을 target EC2의 검증된 script로 전달

**시작 directory:** Repository root

```bash
# 역할별 값을 argument·log에 넣지 않고 remote entrypoint에 전달합니다.
# 확인: exact 완료 메시지와 exit 0입니다.
printf '%s\0%s\0%s\0' \
  "${STG_CATALOG_REDIS_APPLICATION_PASSWORD}" \
  "${STG_CATALOG_REDIS_OPERATION_PASSWORD}" \
  "${STG_CATALOG_REDIS_PROBE_PASSWORD}" \
| env -i HOME="${HOME}" PATH="${PATH}" \
    ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
    'sudo -n /home/ubuntu/car-community-scripts/operations/application/reconcile-catalog-redis-secrets.sh'
```

정확히 `[완료] Catalog Redis application/server Secret이 역할별 목표 상태로 수렴했습니다.`가
출력돼야 합니다. Script가 application Secret 2개 key와 server Secret 3개 key의 owner·type·값을
내부 검증하되 value를 출력하지 않습니다. 실패하면 flag-off를 유지하고 같은 세 입력으로 이
절만 다시 실행합니다. Rotation도 flag-off revision을 먼저 sync한 뒤 같은 entrypoint에 새 세
입력을 전달합니다.

## 4. Secret key 이름 확인

**실행 위치:** Local terminal에서 SSH로 target cluster 조회

**시작 directory:** 제한 없음

```bash
# 두 Secret의 data key 이름만 출력하고 base64 value는 출력하지 않습니다.
# 확인: application 2개, server 3개의 승인된 key 이름만 표시됩니다.
env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl get secret \
    car-community-catalog-redis-application car-community-catalog-redis-server \
    -n '${TARGET_NAMESPACE}' \
    -o go-template='{{range .items}}{{.metadata.name}}{{\"\\n\"}}{{range \$key, \$_ := .data}}{{\$key}}{{\"\\n\"}}{{end}}{{end}}'"
```

출력에는 `car-community-catalog-redis-application`, `REDIS_USERNAME`, `REDIS_PASSWORD`,
`car-community-catalog-redis-server`, `users.acl`, `REDIS_PROBE_USERNAME`,
`REDIS_PROBE_PASSWORD`만 있어야 합니다. 다른 key가 있거나 조회가 실패하면 3절로 돌아갑니다.

## 5. Flag-off sync와 인증 확인

**실행 위치:** Local terminal에서 SSH로 target cluster를 조회하고 공개 staging API 호출

**시작 directory:** Repository root

먼저 tracked manifest의 `CATALOG_MODEL_DETAIL_CACHE_ENABLED=false`를 유지한 revision을
[420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)으로 merge·sync합니다.
AppProject 적용과 Secret 준비가 끝나기 전에는 이 revision을 sync하지 않습니다.

```bash
# [1/3] Redis와 application Deployment가 모두 Ready인지 확인합니다.
printf '\n\n========== [1/3] flag-off rollout ==========\n\n'
env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl rollout status \
    deployment/car-community-catalog-redis -n '${TARGET_NAMESPACE}' --timeout=180s && \
   sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl rollout status \
    deployment/car-community -n '${TARGET_NAMESPACE}' --timeout=180s"

# [2/3] Operation credential 인증 결과가 PONG인지 확인합니다.
printf '\n\n========== [2/3] operation ACL 인증 ==========\n\n'
printf '%s\n' "${STG_CATALOG_REDIS_OPERATION_PASSWORD}" | \
  env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl exec -i \
    deployment/car-community-catalog-redis -n '${TARGET_NAMESPACE}' -- sh -ec \
    'IFS= read -r REDISCLI_AUTH; export REDISCLI_AUTH; \
     redis-cli --user operation --no-auth-warning PING'"

# [3/3] Flag-off 모델 상세 응답을 이후 cold/warm 비교 기준선으로 보존합니다.
printf '\n\n========== [3/3] flag-off DB-only API 기준선 ==========\n\n'
TARGET_AS_OF="$(TZ=Asia/Seoul date +%F)"
SMOKE_DIRECTORY="$(mktemp -d /tmp/catalog-redis-smoke.XXXXXX)"
trap 'rm -rf -- "${SMOKE_DIRECTORY}"' EXIT INT TERM
curl --fail --silent --show-error \
  "${TARGET_API_BASE_URL}/api/v1/car/models/${TARGET_MODEL_ID}?asOf=${TARGET_AS_OF}" \
  > "${SMOKE_DIRECTORY}/flag-off.json"
test -s "${SMOKE_DIRECTORY}/flag-off.json"
```

`PONG`, 두 Deployment의 rollout 성공과 non-empty HTTP 2xx 기준선이 모두 필요합니다. 이
응답은 flag-on cold 응답과 비교해 PostgreSQL 원본 기준의 API 계약이 유지되는지 판정합니다.
Flag-off에서 Redis command가 0회인 분기는 repository integration test가 소유하며 live에서
command counter reset이나 key 삭제로 중복 검증하지 않습니다. 실패하면 5절 처음부터 재개합니다.

## 6. Flag-on cold/warm 확인

**실행 위치:** Local terminal에서 SSH로 target Redis를 조회하고 공개 staging API 호출

**시작 directory:** Repository root

`deploy/base/deployment.yaml`의 exact flag만 `true`로 바꾼 reviewed Git 변경을 420 runbook으로
배포합니다. Live Deployment patch나 Argo parameter override를 사용하지 않습니다.

이 확인은 flag-on rollout으로 새로 시작한 application process에서 수행합니다. 측정 구간에는
아래 재호출 한 건만 보내며 다른 API 요청·credential 변경·Redis reset을 허용하지 않습니다.
Application·Redis가 각각 하나의 Ready Pod이고 restart가 없어야 합니다. Current Git logging
설정에서 cache WARN이 활성화되고, application 시작부터 요청 완료까지 console log가 유실·
rotation·잘림 없이 조회되는지도 확인합니다. 다른 traffic이나 log 완전성을 확신할 수 없으면
성공으로 판정하지 않습니다.

Redis의 GET/hit counter는 서버가 읽었다는 증거이지 application이 값을 받았다는 증거가
아닙니다. 따라서 새 application process의 **전체 실패 요약·circuit 전이 log 부재**도 함께
확인합니다. 첫 실패는 반드시 기록되므로 이후 rate-limit으로 가려진 실패도 최초 기록에
의해 거부됩니다. 새 endpoint·collector나 반복 부하 도구는 사용하지 않습니다.

```bash
# [1/7] 현재 모델·날짜의 exact key가 TTL 만료로 사라진 cold 시작 상태인지 확인합니다.
printf '\n\n========== [1/7] exact key cold 시작 상태 ==========\n\n'
TARGET_CACHE_KEY="carmunity:catalog:model-detail:v1:${TARGET_MODEL_ID}:representative:${TARGET_AS_OF}"
ABSENT_TTL="$(printf '%s\n' "${STG_CATALOG_REDIS_OPERATION_PASSWORD}" | \
  env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl exec -i \
    deployment/car-community-catalog-redis -n '${TARGET_NAMESPACE}' -- sh -ec \
    'IFS= read -r REDISCLI_AUTH; export REDISCLI_AUTH; \
     redis-cli --user operation --no-auth-warning TTL '"'"'${TARGET_CACHE_KEY}'"'"''")"
printf 'exact-key-before-cold-ttl=%s\n' "${ABSENT_TTL}"
test "${ABSENT_TTL}" = -2

# [2/7] 첫 API 호출로 PostgreSQL source 응답과 cache key를 생성합니다.
printf '\n\n========== [2/7] cold API 호출 ==========\n\n'
curl --fail --silent --show-error \
  "${TARGET_API_BASE_URL}/api/v1/car/models/${TARGET_MODEL_ID}?asOf=${TARGET_AS_OF}" \
  > "${SMOKE_DIRECTORY}/cold.json"

# [3/7] Cold 응답이 flag-off 기준선과 같고 생성된 key TTL이 1~300인지 확인합니다.
printf '\n\n========== [3/7] cold 결과·TTL ==========\n\n'
cmp "${SMOKE_DIRECTORY}/flag-off.json" "${SMOKE_DIRECTORY}/cold.json"
FIRST_TTL="$(printf '%s\n' "${STG_CATALOG_REDIS_OPERATION_PASSWORD}" | \
  env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl exec -i \
    deployment/car-community-catalog-redis -n '${TARGET_NAMESPACE}' -- sh -ec \
    'IFS= read -r REDISCLI_AUTH; export REDISCLI_AUTH; \
     redis-cli --user operation --no-auth-warning TTL '"'"'${TARGET_CACHE_KEY}'"'"''")"
printf 'exact-key-after-cold-ttl=%s\n' "${FIRST_TTL}"
test "${FIRST_TTL}" -ge 1
test "${FIRST_TTL}" -le 300

# [4/7] 동일 instance 경계와 warm 직전 GET·SET·DEL·keyspace hit count를 기록합니다.
printf '\n\n========== [4/7] warm 직전 instance·count ==========\n\n'
env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl get pods \
    -n '${TARGET_NAMESPACE}' \
    -l 'app.kubernetes.io/component in (api,catalog-cache)' \
    -o custom-columns='NAME:.metadata.name,UID:.metadata.uid,READY:.status.containerStatuses[*].ready,RESTARTS:.status.containerStatuses[*].restartCount,STARTED:.status.containerStatuses[*].state.running.startedAt'" \
  | tee "${SMOKE_DIRECTORY}/pods-before.txt"
printf '%s\n' "${STG_CATALOG_REDIS_OPERATION_PASSWORD}" | \
  env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl exec -i \
    deployment/car-community-catalog-redis -n '${TARGET_NAMESPACE}' -- sh -ec \
    'IFS= read -r REDISCLI_AUTH; export REDISCLI_AUTH; \
     redis-cli -e --raw --user operation --no-auth-warning INFO commandstats; \
     redis-cli -e --raw --user operation --no-auth-warning INFO stats'" \
  | grep -E '^(cmdstat_(get|set|del):|keyspace_hits:)' \
  | tee "${SMOKE_DIRECTORY}/counts-before.txt"

# [5/7] 같은 API를 다시 호출합니다.
printf '\n\n========== [5/7] warm API 호출 ==========\n\n'
curl --fail --silent --show-error \
  "${TARGET_API_BASE_URL}/api/v1/car/models/${TARGET_MODEL_ID}?asOf=${TARGET_AS_OF}" \
  > "${SMOKE_DIRECTORY}/warm.json"

# [6/7] GET/hit 증가·no-write·전체 process log로 DB fallback과 warm hit를 구별합니다.
printf '\n\n========== [6/7] warm 소비 증거 ==========\n\n'
printf '%s\n' "${STG_CATALOG_REDIS_OPERATION_PASSWORD}" | \
  env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl exec -i \
    deployment/car-community-catalog-redis -n '${TARGET_NAMESPACE}' -- sh -ec \
    'IFS= read -r REDISCLI_AUTH; export REDISCLI_AUTH; \
     redis-cli -e --raw --user operation --no-auth-warning INFO commandstats; \
     redis-cli -e --raw --user operation --no-auth-warning INFO stats'" \
  | grep -E '^(cmdstat_(get|set|del):|keyspace_hits:)' \
  | tee "${SMOKE_DIRECTORY}/counts-after.txt"
env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl logs \
    deployment/car-community -n '${TARGET_NAMESPACE}' --tail=-1" \
  > "${SMOKE_DIRECTORY}/application.log"
grep -E 'Starting CarmunityApplication|Started CarmunityApplication|Catalog 모델 상세 Redis (cache 실패 요약|circuit 상태 전이)' \
  "${SMOKE_DIRECTORY}/application.log"
cmp "${SMOKE_DIRECTORY}/cold.json" "${SMOKE_DIRECTORY}/warm.json"
SECOND_TTL="$(printf '%s\n' "${STG_CATALOG_REDIS_OPERATION_PASSWORD}" | \
  env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl exec -i \
    deployment/car-community-catalog-redis -n '${TARGET_NAMESPACE}' -- sh -ec \
    'IFS= read -r REDISCLI_AUTH; export REDISCLI_AUTH; \
     redis-cli --user operation --no-auth-warning TTL '"'"'${TARGET_CACHE_KEY}'"'"''")"
printf 'exact-key-after-warm-ttl=%s\n' "${SECOND_TTL}"
test "${SECOND_TTL}" -ge 1
test "${SECOND_TTL}" -le "${FIRST_TTL}"

# [7/7] 세 응답·instance 관찰·application log만 임시 보존됐는지 확인합니다.
printf '\n\n========== [7/7] smoke artifact 확인 ==========\n\n'
env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl get pods \
    -n '${TARGET_NAMESPACE}' \
    -l 'app.kubernetes.io/component in (api,catalog-cache)' \
    -o custom-columns='NAME:.metadata.name,UID:.metadata.uid,READY:.status.containerStatuses[*].ready,RESTARTS:.status.containerStatuses[*].restartCount,STARTED:.status.containerStatuses[*].state.running.startedAt'" \
  | tee "${SMOKE_DIRECTORY}/pods-after.txt"
find "${SMOKE_DIRECTORY}" -maxdepth 1 -type f -print | sort
```

명령의 exit 0만으로 통과하지 않습니다. 운영자가 출력과 보존한 원문을 다음 표로 직접
비교한 뒤에만 6절 성공을 선언합니다.

| 비교 대상 | 반드시 확인할 값 | 불일치·불확실할 때 |
| --- | --- | --- |
| `pods-before.txt` ↔ `pods-after.txt` | Application·Redis 각각 한 Pod, 모든 READY가 true, RESTARTS가 0, 이름·UID·STARTED가 전후 동일 | 진행 중 rollout·restart 또는 다른 instance이므로 판정 중단 |
| `counts-before.txt` ↔ `counts-after.txt` | `cmdstat_get`의 `calls`와 `keyspace_hits`가 각각 정확히 1 증가, `cmdstat_set`·`cmdstat_del`의 `calls`는 불변 | Miss·bypass·write 또는 다른 traffic 가능성이므로 판정 중단 |
| Counter 원문 누락 | `keyspace_hits`와 cold write 뒤의 `cmdstat_set`은 존재해야 함. 미사용 GET·DEL 행의 부재만 0회로 해석 | 다른 누락·reset 가능성은 판정 중단 |
| `application.log` 전체 | 새 process의 Starting·Started 기록이 모두 있고 그 시작부터 재호출 종료까지 cache 실패 요약·circuit 전이가 0건 | 과거 한 건이라도 있거나 log가 불완전하면 판정 중단 |
| 응답·TTL | 세 응답 byte 동일, cold TTL 1~300, 재호출 후 양수·감소 또는 동일 TTL | Flag-off로 복구 후 원인 확인 |

사전 TTL `-2`, 첫 호출 뒤 TTL `1`~`300`, 두 번째 호출 뒤 양수 TTL, byte 동일 응답과
위 server·application 증거가 모두 필요합니다. Counter 구간에는 TTL 조회도 넣지 않습니다.
Read timeout·응답 유실·circuit-open·손상 document는 application 실패 log로 거부하고,
miss·bypass는 GET/hit 증가 조건으로 거부합니다. 동시 traffic, instance 변경, counter reset,
WARN 억제 또는 시작부터의 log 누락 가능성이 있으면 이 결과로 warm hit를 선언하지 않습니다.
Exact key가 이미 있으면 남은 TTL만큼 기다린 뒤 6절을 다시 시작하며 application credential로
삭제하지 않습니다. `-1`, cold 뒤 `-2`, 300보다 큰 값, TTL 재설정·만료 또는 API 차이는
실패이며 flag-off revision으로 복구한 뒤 5절부터 재개합니다. `GET`·`KEYS`·`SCAN`·`FLUSH*`
또는 cache value 조회는 사용하지 않습니다.

## 7. Rotation과 부분 실패 재개

**실행 위치:** Local terminal에서 SSH로 target cluster를 변경·조회

**시작 directory:** Repository root

Rotation은 먼저 flag를 `false`로 되돌린 Git revision을 sync하고 application Ready를 확인한
뒤 새 세 입력으로 1·3·4절을 반복합니다. ACL file과 application env는 process 시작 때 읽으므로
Secret 적용 뒤 두 Deployment Pod를 차례로 재생성하고 rollout을 확인합니다.

```bash
# 두 Deployment를 Redis→application 순으로 재생성하고 각 rollout 완료를 확인합니다.
env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "${TARGET_SSH_KEY}" "ubuntu@${TARGET_SSH_HOST}" \
  "sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete pod \
     -n '${TARGET_NAMESPACE}' -l app.kubernetes.io/component=catalog-cache && \
   sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl rollout status \
     deployment/car-community-catalog-redis -n '${TARGET_NAMESPACE}' --timeout=180s && \
   sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete pod \
     -n '${TARGET_NAMESPACE}' -l app.kubernetes.io/component=api && \
   sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl rollout status \
     deployment/car-community -n '${TARGET_NAMESPACE}' --timeout=180s"
```

그 뒤 5절 인증, flag-on Git revision과 6절 smoke를 다시 수행합니다. 이전 password를 폐기하는
별도 provider API는 없으며 ACL file 교체와 Redis Pod 재생성이 폐기 시점입니다.

| 마지막 성공 지점 | 실패 의미 | 재개 |
| --- | --- | --- |
| AppProject만 적용 | Workload·Secret은 바뀌지 않음 | Permission을 유지하고 3절부터 재개 |
| Secret 하나만 적용 | Consumer가 서로 다른 세대를 볼 수 있음 | Flag-off 유지, 같은 local 입력으로 3절 전체 재실행 |
| Redis Ready, application 실패 | Server ACL은 유효하나 application credential·wiring 문제 | Flag-off 유지, Secret schema와 Deployment event를 확인한 뒤 application Pod만 재생성 |
| Flag-on API 실패 | Cache logic·연결·source 중 하나 실패 | 즉시 flag-off Git revision으로 복구하고 application Ready·기존 DB 응답 확인 |
| Cold/warm 성공, TTL 실패 | Key identity·TTL 또는 operation ACL 불일치 | Flag-off로 복구하고 source를 수정; `CONFIG`, `KEYS`, `SCAN`, `FLUSH*`로 우회 금지 |

## 8. 종료와 비노출 확인

**실행 위치:** Local terminal

**시작 directory:** Repository root

```bash
# 임시 smoke artifact를 제거하고 현재 shell의 credential 변수를 해제합니다.
rm -rf -- "${SMOKE_DIRECTORY:-/tmp/nonexistent-catalog-redis-smoke}"
trap - EXIT INT TERM
unset STG_CATALOG_REDIS_APPLICATION_PASSWORD
unset STG_CATALOG_REDIS_OPERATION_PASSWORD
unset STG_CATALOG_REDIS_PROBE_PASSWORD
```

- Git status에 Secret·ACL·smoke 파일이 없습니다.
- Operation plaintext password는 Kubernetes Secret에 없습니다.
- Application은 server Secret을, Redis는 application Secret을 참조하지 않습니다.
- Redis는 ClusterIP이고 public Ingress·NodePort·LoadBalancer가 없습니다.
- 실패 복구 뒤에는 flag-off API가 PostgreSQL 결과를 반환합니다.

Redis Deployment 제거는 flag-off와 application 안정 확인 뒤 별도 desired-state 변경으로
수행합니다. AppProject `NetworkPolicy` permission은 이를 소비하는 manifest가 전혀 없을 때만
별도 tracked revert와 one-time apply로 제거합니다.
