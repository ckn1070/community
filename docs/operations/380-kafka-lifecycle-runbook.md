# Kafka lifecycle runbook

Community 댓글·답글 이벤트용 Strimzi를 처음 설치하고, 역할별 mTLS를 확인하고, 미완료 원본을
재발행하거나 자격 증명을 교체할 때 사용합니다. 단일 node·ephemeral broker는 기능·복구
qualification 대상이며 production HA를 증명하지 않습니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | U1~U5 repository 결과가 검증된 뒤 최초 설치, 전환 전 qualification, replay, identity rotation에 사용합니다. |
| 누가 | Cluster-scoped 설치는 platform 운영자, namespace CR과 application 전환은 repository·target 권한을 가진 운영자가 수행합니다. Replay·terminal은 event 범위와 영향을 승인할 운영자가 수행합니다. |
| 어디서 | Render와 Git 변경은 repository root의 local terminal, live 명령은 target EC2의 admin kubeconfig에서 수행합니다. |
| 입력 | 검증된 exact Git SHA와 image digest, target SSH 정보, event ID 집합·이유·예상 건수·승인자 |
| 완료 | 설치·mTLS·권한·resource가 live에서 확인되고, 전환은 receipt·inbox 기대 집합과 rollback까지 확인된 경우에만 완료입니다. |
| 중단 | CRD·자원·권한·인증·기대 집합 중 하나라도 다르면 다음 mutation을 하지 않고 해당 단계에서 재개합니다. |

`deploy/kafka`와 `deploy/kafka/application-kafka-patch.yaml`은 준비된 desired state지만 현재
`deploy/overlays/stg`에는 포함되지 않습니다. 이 상태에서는 Argo CD가 Kafka CR이나 application
Kafka 설정을 적용하지 않습니다. Secret 값과 생성된 Secret 사본은 Git·명령 인자·로그에
기록하지 않습니다.

## 1. Local 사전 확인

Repository root에서 세 경로를 별도로 render합니다.

```bash
kubectl kustomize scripts/bootstrap/kafka/manifests >/tmp/strimzi-install.yaml
kubectl kustomize deploy/kafka >/tmp/community-kafka.yaml
kubectl kustomize deploy/overlays/stg >/tmp/staging-before-kafka.yaml

rg -n 'quay.io/strimzi/operator@sha256:77f8fa8121a67561c3418de985783d197f51b8931e9a47f793dc0437dc6bb21f' /tmp/strimzi-install.yaml
rg -n 'version: 4.2.1' /tmp/community-kafka.yaml
rg -n 'metadataVersion: 4.2-IV1' /tmp/community-kafka.yaml
rg -n 'type: tls' /tmp/community-kafka.yaml
rg -n 'IdempotentWrite' /tmp/community-kafka.yaml
! rg -n '^kind: Kafka(NodePool|Topic|User)?$' /tmp/staging-before-kafka.yaml
```

모두 성공해야 cluster-scoped 설치와 아직 비활성인 namespace desired state가 분리됐습니다.
임시 render는 검사 뒤 삭제합니다.

## 2. Strimzi 최초 설치

이 단계는 CRD·ClusterRole·ClusterRoleBinding을 바꾸므로 platform 운영자만 실행합니다.
Local render를 target EC2로 보내 server-side dry-run한 뒤 같은 exact render를 적용합니다.

```bash
kubectl kustomize scripts/bootstrap/kafka/manifests | ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>'   'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -'

kubectl kustomize scripts/bootstrap/kafka/manifests | ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>'   'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --server-side --field-manager=car-community-kafka-bootstrap -f -'
```

Target EC2에서 CRD와 operator image·rollout을 확인합니다.

```bash
sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl get crd   kafkas.kafka.strimzi.io kafkanodepools.kafka.strimzi.io   kafkatopics.kafka.strimzi.io kafkausers.kafka.strimzi.io
sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl rollout status   deployment/strimzi-cluster-operator -n car-community --timeout=300s
sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl get deployment/strimzi-cluster-operator   -n car-community -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
```

Image가 1절의 operator digest와 다르거나 rollout이 실패하면 CR을 적용하지 않습니다.

## 3. Namespace 권한과 Kafka CR 적용

먼저 AppProject의 namespace allowlist만 target에 server-side dry-run하고 적용합니다.
`clusterResourceWhitelist: []`가 유지돼야 합니다.

```bash
cat scripts/bootstrap/gitops/manifests/car-community-appproject.yaml | ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' 'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -'
cat scripts/bootstrap/gitops/manifests/car-community-appproject.yaml | ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' 'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --server-side --field-manager=car-community-gitops-bootstrap -f -'
```

`deploy/kafka` render를 target에 server-side dry-run합니다. U6의 첫 GitOps change에는
`deploy/overlays/stg/kustomization.yaml`의 `resources`에 `../../kafka`만 추가합니다. Application
patch는 U7까지 포함하지 않습니다. [420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)의
review·merge·exact revision 경로로 적용하며 운영자가 namespace CR을 별도로 apply하지 않습니다.

```bash
kubectl kustomize deploy/kafka \
| ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -'
```

Kafka Ready, topic 세 partition, 네 사용자 Ready와 live resource를 확인합니다.

```bash
sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl wait kafka/car-community \
  -n car-community --for=condition=Ready --timeout=600s
sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl wait kafkanodepool/single-node \
  -n car-community --for=condition=Ready --timeout=600s
for resource in \
  kafkatopic/community.conversation-created.v1 \
  kafkatopic/community.conversation-created.v1.dlt \
  kafkauser/community-relay kafkauser/notification-consumer \
  kafkauser/notification-dlt-writer kafkauser/community-replay; do
  sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl wait "${resource}" \
    -n car-community --for=condition=Ready --timeout=300s
done
sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl get pod -n car-community \
  -l strimzi.io/cluster=car-community \
  -o custom-columns='NAME:.metadata.name,READY:.status.containerStatuses[*].ready,REQUESTS:.spec.containers[*].resources.requests,LIMITS:.spec.containers[*].resources.limits'
sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl describe node
```

Kafka 적용으로 추가되는 request는 CPU `500m`, memory `1024Mi`, ephemeral-storage `608Mi`이고
limit은 CPU `2500m`, memory `1536Mi`, ephemeral-storage `3840Mi`입니다. U6 merge 전에는 현재
`Allocated resources`에 Kafka request와 이후 Application 동시 rollout Pod·migration Job의
증가분을 더합니다. Kafka 적용 후에는 live allocation에 이미 Kafka request가 포함되므로 아직
실행하지 않은 Application rollout·migration 증가분만 더합니다. 어느 시점이든 allocatable을
넘거나 MemoryPressure·DiskPressure eviction 여유를 설명할 수 없으면 U6을 중단합니다. 계산
시점, 단일-node 산술과 live Pod 출력은 U6 증거에 함께 저장합니다.

## 4. 역할별 mTLS와 권한 qualification

Secret 값은 출력하지 않고 필수 key 세 개를 각각 fail-closed로 확인합니다.

```bash
set -euo pipefail
for identity in community-relay notification-consumer notification-dlt-writer community-replay; do
  for key in ca.crt user.p12 user.password; do
    value="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl get secret "${identity}" \
      -n car-community -o "go-template={{index .data \"${key}\"}}")"
    test -n "${value}"
    unset value
  done
done
```

Repository root에서 다음 helper를 정의합니다. Tracked template은 고정 broker digest와 hostname
검증을 사용하고, 각 probe는 새 Job 이름으로 server dry-run한 뒤 실행합니다. `allow`는
`Complete`, `deny`는 `Failed` condition만 합격입니다. 어떤 판정이든 Job을 삭제하므로 실패하면
같은 identity·command에서 다시 시작합니다.

```bash
set -euo pipefail
run_kafka_probe() {
  identity="$1" expected="$2" network="$3" command="$4" oracle="${5:-}"
  name="kafka-access-probe-$(date -u +%Y%m%d%H%M%S)-${RANDOM}"
  file="$(mktemp /tmp/kafka-access-probe.XXXXXX.json)"
  jq --arg identity "${identity}" --arg command "${command}" --arg name "${name}" \
    '.metadata.name=$name
     | .spec.template.spec.volumes[] |=
         (if .name=="identity" then .secret.secretName=$identity else . end)
     | .spec.template.spec.containers[0].args[0] |= sub("COMMAND";$command)' \
    deploy/kafka/qualification-job-template.json >"${file}"
  if test "${network}" = outside; then
    jq 'del(.spec.template.metadata.labels["app.kubernetes.io/name"])' "${file}" >"${file}.next"
    mv "${file}.next" "${file}"
  fi
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -' <"${file}"
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl create -f -' <"${file}"
  condition=Failed
  test "${expected}" = allow && condition=Complete
  if ! ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl wait job/${name} -n car-community --for=condition=${condition} --timeout=90s"; then
    ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
      "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl logs job/${name} -n car-community || true"
    ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
      "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete job/${name} -n car-community --ignore-not-found"
    rm -f -- "${file}"
    return 1
  fi
  logs="$(ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl logs job/${name} -n car-community")"
  printf '%s\n' "${logs}"
  if test "${expected}" = deny; then
    test -n "${oracle}"
    if ! printf '%s\n' "${logs}" | grep -E "${oracle}"; then
      ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
        "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete job/${name} -n car-community --ignore-not-found --wait=true"
      rm -f -- "${file}"
      return 1
    fi
  fi
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete job/${name} -n car-community --wait=true"
  rm -f -- "${file}"
}

PRODUCE_ORIGINAL="printf 'qualification\\n' | /opt/kafka/bin/kafka-console-producer.sh --sync --bootstrap-server car-community-kafka-bootstrap:9093 --topic community.conversation-created.v1 --producer.config /tmp/client.properties"
PRODUCE_DLT="printf 'qualification\\n' | /opt/kafka/bin/kafka-console-producer.sh --sync --bootstrap-server car-community-kafka-bootstrap:9093 --topic community.conversation-created.v1.dlt --producer.config /tmp/client.properties"
CONSUME_ORIGINAL="timeout 30 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server car-community-kafka-bootstrap:9093 --topic community.conversation-created.v1 --group notification.community-conversation.v1 --from-beginning --max-messages 6 --consumer.config /tmp/client.properties"

run_kafka_probe community-relay allow inside "${PRODUCE_ORIGINAL}"
run_kafka_probe community-replay allow inside "${PRODUCE_ORIGINAL}"
run_kafka_probe community-relay allow inside "${PRODUCE_ORIGINAL}"
run_kafka_probe community-relay deny outside "${PRODUCE_ORIGINAL}" 'TimeoutException|timed out'
run_kafka_probe community-relay allow inside "${PRODUCE_ORIGINAL}"
```

마지막 `outside` 실패는 NetworkPolicy selector 경계입니다. 성공 probe가
`ssl.endpoint.identification.algorithm=https`로 연결됐으므로 TLS hostname 검증도 같은 결과에
포함됩니다. 익명 client는 다음 exact 변형으로 identity volume·mount와 keystore를 제거합니다.
Job이 `Failed`가 아니거나 server dry-run이 실패하면 중단합니다.

```bash
run_kafka_probe community-relay allow inside "${PRODUCE_ORIGINAL}"
name="kafka-anonymous-probe-$(date -u +%Y%m%d%H%M%S)"
file="$(mktemp /tmp/kafka-anonymous-probe.XXXXXX.json)"
jq --arg name "${name}" --arg command \
  "cat >/tmp/client.properties <<EOF
security.protocol=SSL
ssl.truststore.type=PEM
ssl.truststore.location=/etc/kafka/ca/ca.crt
ssl.endpoint.identification.algorithm=https
EOF
printf 'qualification\\n' | /opt/kafka/bin/kafka-console-producer.sh --sync --bootstrap-server car-community-kafka-bootstrap:9093 --topic community.conversation-created.v1 --producer.config /tmp/client.properties" \
  '.metadata.name=$name
   | del(.spec.template.spec.volumes[] | select(.name=="identity"))
   | del(.spec.template.spec.containers[0].volumeMounts[] | select(.name=="identity"))
   | .spec.template.spec.containers[0].args[0]=$command' \
  deploy/kafka/qualification-job-template.json >"${file}"
ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -' <"${file}"
ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl create -f -' <"${file}"
if ! ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl wait job/${name} -n car-community --for=condition=Failed --timeout=90s"; then
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete job/${name} -n car-community --ignore-not-found --wait=true"
  rm -f -- "${file}"
  exit 1
fi
if ! ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl logs job/${name} -n car-community" \
  | grep -E 'SSLHandshakeException|handshake.*fail|certificate.*required'; then
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete job/${name} -n car-community --ignore-not-found --wait=true"
  rm -f -- "${file}"
  exit 1
fi
ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete job/${name} -n car-community --wait=true"
rm -f -- "${file}"
run_kafka_probe community-relay allow inside "${PRODUCE_ORIGINAL}"

run_kafka_probe notification-consumer allow inside "${CONSUME_ORIGINAL}"
run_kafka_probe notification-dlt-writer allow inside "${PRODUCE_DLT}"
run_kafka_probe notification-consumer deny inside "${PRODUCE_ORIGINAL}" 'TopicAuthorizationException'
run_kafka_probe community-relay deny inside "${CONSUME_ORIGINAL}" 'TopicAuthorizationException|GroupAuthorizationException'
run_kafka_probe notification-dlt-writer deny inside "${PRODUCE_ORIGINAL}" 'TopicAuthorizationException'
run_kafka_probe community-replay deny inside "${PRODUCE_DLT}" 'TopicAuthorizationException'
```

Qualification이 만든 문자열은 Notification application을 연결하기 전에 consumer probe가
소진합니다. Group lag가 0인지 다음 명령으로 확인합니다.

```bash
run_kafka_probe notification-consumer allow inside \
  "/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server car-community-kafka-bootstrap:9093 --describe --group notification.community-conversation.v1 --command-config /tmp/client.properties | tee /tmp/group.txt; awk 'NR>2 && $6 != 0 {exit 1}' /tmp/group.txt"
```

Replay Secret은 일반 Deployment에 mount하지 않고 승인 replay Job에만 넣습니다.

## 5. Application 연결과 전환

U6가 끝난 뒤 staging overlay의 `patches`에
`../../kafka/application-kafka-patch.yaml`과
`../../kafka/application-kafka-shadow-patch.yaml`을 함께 추가하는 별도 U7 change를 만듭니다.
첫 patch는 relay·consumer·DLT credential만 연결하고, 두 번째 patch가 다음 shadow flag를
source로 고정합니다.

```text
KAFKA_RELAY_ENABLED=true
KAFKA_CONSUMER_ENABLED=true
KAFKA_CLEANUP_ENABLED=false
KAFKA_SYNC_NOTIFICATIONS_ENABLED=true
```

[420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)으로 candidate render,
server-side dry-run, exact revision, migration, rollout과 image digest를 확인합니다. 해당 runbook의
runtime DB credential 준비 절차를 사용한 EC2 subshell 안에서 qualification event ID를
`EVENT_IDS`에 comma-separated UUID로 넣고 아래 query를 실행합니다.

```bash
PGPASSWORD="${RUNTIME_DB_PASSWORD}" PGSSLROOTCERT="${DB_CHECK_DIR}/ap-northeast-2-bundle.pem" \
psql "${PSQL_URL}" --username=carmunity_runtime --no-psqlrc --set=ON_ERROR_STOP=on \
  --set=event_ids="${EVENT_IDS}" --csv <<'SQL'
WITH selected AS (
  SELECT unnest(string_to_array(:'event_ids', ',')::uuid[]) AS event_id
)
SELECT s.event_id, o.conversation_id, o.published_at IS NOT NULL AS published,
       o.publish_attempts, COALESCE(o.last_publish_error, '') AS last_publish_error,
       r.completed_at IS NOT NULL AS received,
       COUNT(i.id) AS inbox_count,
       COALESCE(ARRAY_AGG(i.recipient_account_public_id ORDER BY i.recipient_account_public_id)
         FILTER (WHERE i.id IS NOT NULL), ARRAY[]::uuid[]) AS recipient_ids,
       EXTRACT(EPOCH FROM (NOW() - o.created_at))::bigint AS outbox_age_seconds
FROM selected s
LEFT JOIN community_event_outbox o ON o.event_id=s.event_id
LEFT JOIN notification_event_receipt r ON r.event_id=s.event_id
LEFT JOIN notification_inbox i ON i.source_content_public_id=o.conversation_id
GROUP BY s.event_id, o.conversation_id, o.published_at, o.publish_attempts,
         o.last_publish_error, r.completed_at, o.created_at
ORDER BY s.event_id;
SQL
```

입력 ID가 한 행씩 존재하고 `published=true`, `received=true`, `last_publish_error`가 비었으며
`recipient_ids`가 사전에 기록한 댓글·답글의 자기 제외·중복 제거 기대 UUID 집합과 정렬 상태로
정확히 같아야 합니다. `inbox_count`는 그 배열 길이와 같아야 합니다.
누락 ID, pending outbox, 증가하는 age·attempt, error class는 relay 문제이고 receipt 없는 published
row와 consumer group lag는 consumer 문제입니다. DLT header의 실제 확인은 아래 임시 observer
절차를 사용합니다.

처리 불가 event를 의도적으로 만든 qualification에서만 임시 DLT observer를 생성합니다. 이
identity는 DLT read와 자기 임시 group만 사용할 수 있습니다. `DLT_EVENT_ID`의 record에서
`delivery-failure-class` header를 실제로 읽고 receipt가 없음을 위 query로 확인한 뒤 group과
KafkaUser를 삭제합니다.

```bash
cleanup_dlt_observer() {
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete kafkauser/qualification-dlt-observer -n car-community --ignore-not-found --wait=true' || true
  rm -f -- /tmp/kafka-dlt-observer.yaml
}
trap cleanup_dlt_observer EXIT INT TERM
cat >/tmp/kafka-dlt-observer.yaml <<'YAML'
apiVersion: kafka.strimzi.io/v1
kind: KafkaUser
metadata:
  name: qualification-dlt-observer
  namespace: car-community
  labels: {strimzi.io/cluster: car-community}
spec:
  authentication: {type: tls}
  authorization:
    type: simple
    acls:
      - resource: {type: topic, name: community.conversation-created.v1.dlt, patternType: literal}
        operations: [Read, Describe]
        host: "*"
      - resource: {type: group, name: qualification.dlt-observer, patternType: literal}
        operations: [Read, Describe, Delete]
        host: "*"
YAML
cat /tmp/kafka-dlt-observer.yaml | ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -'
cat /tmp/kafka-dlt-observer.yaml | ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --server-side --field-manager=kafka-qualification -f -'
ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl wait kafkauser/qualification-dlt-observer -n car-community --for=condition=Ready --timeout=300s'

DLT_EVENT_ID='<UUID>'
DLT_READ="timeout 30 /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server car-community-kafka-bootstrap:9093 --topic community.conversation-created.v1.dlt --group qualification.dlt-observer --from-beginning --property print.headers=true --consumer.config /tmp/client.properties | awk -v id='${DLT_EVENT_ID}' 'index(\$0,\"delivery-failure-class\") && index(\$0,\"\\\"eventId\\\":\\\"\" id \"\\\"\") {for (i=1;i<=NF;i++) if (\$i ~ /delivery-failure-class:/) {print \"eventId=\" id, \$i; found=1; exit}} END {exit !found}'"
run_kafka_probe qualification-dlt-observer allow inside "${DLT_READ}"
run_kafka_probe qualification-dlt-observer allow inside \
  "/opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server car-community-kafka-bootstrap:9093 --delete --group qualification.dlt-observer --command-config /tmp/client.properties"
ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete kafkauser/qualification-dlt-observer -n car-community --wait=true'
rm -f -- /tmp/kafka-dlt-observer.yaml
trap - EXIT INT TERM
```

Observer 생성·header 확인·group 삭제·KafkaUser 삭제 중 하나라도 실패하면 U7을 중단하고 남은
exact resource에서 재개합니다. Payload는 terminal에 재출력하지 않고 qualification 증거에는
event ID, failure class, command exit만 남깁니다.

Shadow corpus가 이 query와 group lag 0에 수렴한 뒤 별도 change로
`KAFKA_SYNC_NOTIFICATIONS_ENABLED=false`를 적용합니다. Async-only corpus에도 같은 query와
기대 수신자 집합을 다시 실행합니다. Community 자체 활성화는
[560 Community 기능 활성화](560-community-feature-activation-runbook.md)의 선행 gate를 따릅니다.

Rollback은 먼저 `KAFKA_CLEANUP_ENABLED=false`, 이어 relay·consumer를 끄고
`KAFKA_SYNC_NOTIFICATIONS_ENABLED=true`를 복구합니다. Outbox·receipt·inbox·DLT는 삭제하지
않습니다. Rollback revision에서 동기 댓글 알림과 기존 미완료 원본 보존을 같은 query로 확인합니다.

## 6. 미완료 원본 재발행과 terminal 처분

Event ID는 최대 100개로 제한합니다. 먼저 DB credential만 있는
`operation-job-template.json`으로 dry-run합니다. 승인 replay만
`operation-replay-job-template.json`을 사용하며, 두 template 모두 JWT·로그인 HMAC Secret을
받지 않습니다. Template은 Kustomization에 포함되지 않아 자동 실행되지 않습니다.

```bash
set -euo pipefail
OPERATION_DIRECTORY="$(mktemp -d /tmp/kafka-event-operation.XXXXXX)"
trap 'rm -rf -- "${OPERATION_DIRECTORY}"' EXIT INT TERM
IMAGE="$(ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl get deployment/car-community -n car-community -o jsonpath='"'"'{.spec.template.spec.containers[?(@.name=="application")].image}'"'"'')"
EVENT_IDS='<UUID,UUID>'
REASON='<REASON>'

run_operation_job() {
  template="$1" args_json="$2"
  job_name="kafka-event-operation-$(date -u +%Y%m%d%H%M%S)-${RANDOM}"
  jq --arg image "${IMAGE}" --arg name "${job_name}" --argjson args "${args_json}" \
    '.metadata.name=$name | del(.metadata.generateName)
     | .spec.template.spec.containers[0].image=$image
     | .spec.template.spec.containers[0].args=$args' \
    "${template}" >"${OPERATION_DIRECTORY}/job.json"
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -' \
    <"${OPERATION_DIRECTORY}/job.json"
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    'sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl create -f -' \
    <"${OPERATION_DIRECTORY}/job.json"
  succeeded=''
  for attempt in {1..60}; do
    succeeded="$(ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
      "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl get job/${job_name} -n car-community -o jsonpath='{.status.succeeded}'")"
    failed="$(ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
      "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl get job/${job_name} -n car-community -o jsonpath='{.status.failed}'")"
    test "${succeeded}" = 1 && break
    if test -n "${failed}" && test "${failed}" -ge 1; then
      ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
        "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl logs job/${job_name} -n car-community || true"
      ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
        "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete job/${job_name} -n car-community --ignore-not-found --wait=true"
      return 1
    fi
    sleep 5
  done
  if test "${succeeded}" != 1; then
    ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
      "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl logs job/${job_name} -n car-community || true"
    ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
      "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete job/${job_name} -n car-community --ignore-not-found --wait=true"
    return 1
  fi
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl logs job/${job_name} -n car-community"
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
    "sudo KUBECONFIG=/etc/kubernetes/admin.conf kubectl delete job/${job_name} -n car-community --wait=true"
}

DRY_ARGS="$(jq -cn --arg ids "${EVENT_IDS}" --arg reason "${REASON}" \
  '["--command=kafka-events","--mode=replay","--event-ids="+$ids,"--reason="+$reason]')"
run_operation_job deploy/kafka/operation-job-template.json "${DRY_ARGS}"
```

출력의 `requested`, `pending`, `eventIds`를 원래 장애 범위와 5절 receipt query에 대조합니다.
이미 publish됐어도 receipt가 없으면 pending이고 receipt 또는 terminal 처분이 있으면 제외됩니다.
건수와 ID가 승인 범위와 정확히 일치할 때만 새 Job으로 승인 replay를 실행합니다.

```bash
EXPECTED_COUNT='<COUNT>'
OPERATOR='<OPERATOR>'
REPLAY_ARGS="$(jq -cn --arg ids "${EVENT_IDS}" --arg reason "${REASON}" \
  --arg expected "${EXPECTED_COUNT}" --arg operator "${OPERATOR}" \
  '["--command=kafka-events","--mode=replay","--event-ids="+$ids,"--reason="+$reason,
    "--approved=true","--expected-count="+$expected,"--operator="+$operator]')"
run_operation_job deploy/kafka/operation-replay-job-template.json "${REPLAY_ARGS}"
```

각 `broker-ack eventId`는 broker 수락만 뜻합니다. 5절 query로 Notification receipt와 정확한
inbox 수신자 집합을 확인해야 완료입니다. 중간 실패면 완료 receipt가 생긴 ID가 다음 dry-run에서
빠지므로 같은 전체 ID 집합으로 재개합니다.

재처리 불가 사건도 먼저 위 dry-run을 수행합니다. 원인·영향·복구 불가 근거와 승인자가 모두
있고 pending 집합이 정확히 일치할 때 DB 전용 template으로 다음 terminal Job을 실행합니다.

```bash
EXPECTED_COUNT='<COUNT>'
OPERATOR='<OPERATOR>'
IMPACT='<IMPACT>'
UNRECOVERABLE_EVIDENCE='<UNRECOVERABLE_EVIDENCE>'
TERMINAL_ARGS="$(jq -cn --arg ids "${EVENT_IDS}" --arg reason "${REASON}" \
  --arg expected "${EXPECTED_COUNT}" --arg operator "${OPERATOR}" \
  --arg impact "${IMPACT}" --arg evidence "${UNRECOVERABLE_EVIDENCE}" \
  '["--command=kafka-events","--mode=terminal","--event-ids="+$ids,"--reason="+$reason,
    "--approved=true","--expected-count="+$expected,"--operator="+$operator,
    "--impact="+$impact,"--unrecoverable-evidence="+$evidence]')"
run_operation_job deploy/kafka/operation-job-template.json "${TERMINAL_ARGS}"
```

DB 조회 오류나 일시 실패를 terminal로 바꾸지 않습니다. 실패 Job은 log 확인 뒤 명시적으로
삭제하고 같은 입력에서 재개합니다.

## 7. Cleanup과 broker 데이터 소실 복구

Receipt 또는 승인 terminal이 적격을 만든 뒤 7일이 지나야 cleanup 대상입니다. 전환과 복구
qualification이 끝난 뒤에만 `KAFKA_CLEANUP_ENABLED=true`를 별도 배포합니다. 한 실행은 최대
100건이며 완료 조회 실패·응답 불일치이면 삭제하지 않습니다. Receipt는 자동 삭제하지 않습니다.

Ephemeral broker의 topic·offset이 소실되면 먼저 relay·consumer와 cleanup을 중지합니다.
Topic·user·TLS 준비를 다시 확인하고 6절 dry-run에 장애 기간 event ID와 이미 publish된 의심
범위를 함께 넣습니다. Receipt·terminal 없는 정확한 집합만 재발행하고 receipt·inbox가 기대
집합에 수렴한 뒤 worker를 재개합니다. Kafka count나 lag만으로 누락 0을 선언하지 않습니다.

## 8. Identity rotation과 제거

새 이름의 KafkaUser를 추가해 Ready와 Secret key를 확인합니다. Application이 새 Secret을 함께
mount하도록 배포하고 해당 역할의 정상 동작과 다른 역할 거부를 확인합니다. 모든 Pod와 일회
Job이 새 principal을 사용하는 것을 확인한 뒤 이전 KafkaUser를 Git에서 제거하고 다시
server-side dry-run·GitOps 배포합니다. 이전 credential의 인증 실패까지 확인해야 폐기가 끝납니다.

Kafka 제거는 먼저 cleanup·relay·consumer를 끄고 동기 알림을 복구한 뒤 receipt·inbox·outbox
보존 상태를 확인합니다. Namespace CR과 operator/CRD 제거는 서로 다른 권한·복구 경계이므로
별도 승인합니다. PostgreSQL 원본과 migration을 자동으로 되돌리거나 삭제하지 않습니다.
