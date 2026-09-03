# Application 환경변수·Secret 변경 runbook

이 절차는 최초 Application bootstrap 뒤 **변경할 환경변수 이름**을 기준으로 값의 실제
owner와 consumer를 찾고, Git-managed 값 또는 Kubernetes Secret을 변경해 새 Pod의 직접
기능 검증까지 마치는 공용 절차입니다. 특정 환경변수·provider·Secret의 현재 목록과 실제
값은 기록하지 않고 실행 시 repository source와 live Kubernetes에서 확인합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | Application 환경변수의 값을 추가·변경하거나 누락된 Secret을 생성할 때 사용합니다. |
| 누가 | Repository 변경 권한과 target cluster 접근 권한을 가진 운영자가 실행합니다. 외부 credential은 해당 provider의 발급·폐기 권한도 필요합니다. |
| 어디서 | Source와 Git-managed 값은 local repository terminal, Secret과 workload는 target cluster에 접근 가능한 terminal에서 확인·변경합니다. |
| 입력 | `<ENV_NAME>`은 변경할 환경변수 이름입니다. 환경·overlay·Application·Namespace·Secret·key·owner·field manager·모든 consumer·검증 방법은 현재 source에서 발견합니다. |
| 완료 | 새 값이 새 Pod에 반영되고 그 값을 사용하는 직접 기능이 성공해야 합니다. Provider credential이면 그 뒤에만 이전 credential을 폐기합니다. |
| 재개 | 실패한 단계에서 중단합니다. 기존 Secret은 변경 전에 확보한 이전 값으로 복원하고, 새로 만든 Secret은 consumer 변경을 되돌린 채 유지합니다. |

## 1. 변경 대상과 실행 입력 발견

### 1.1 Repository source와 consumer 확인

**실행 위치:** Local repository terminal

**시작 directory:** Repository root

```bash
TARGET_ENV_NAME='<ENV_NAME>'

printf '\n\n========== environment sources ==========\n\n'
rg -n -- "${TARGET_ENV_NAME}" \
  src/main/resources deploy scripts .env.example \
  docs/development docs/operations

TARGET_PROPERTY_PREFIX='<application.yaml에서 발견한 property prefix>'
TARGET_PROPERTY_NAME='<application.yaml에서 발견한 property name>'
rg -n -C 8 -- "${TARGET_PROPERTY_PREFIX}|${TARGET_PROPERTY_NAME}" \
  src/main/java src/test/java
```

검색 결과에서 다음 값을 실제 source로 고정합니다. 이름이 비슷한 resource나 과거 문서의
예시로 빈칸을 채우지 않습니다.

- Application property binding, 기본값과 값 validation
- 값을 소유하는 Git manifest 또는 Secret producer
- 값을 읽는 모든 workload·Job·container
- Secret-backed 값이면 Secret 이름·Namespace·key·type·`managed-by` owner와 field manager
- 값을 가리는 더 우선순위 높은 environment source의 부재
- 변경 값을 실제로 사용하는 기능의 기존 검증 명령 또는 runbook section과 기대 결과

Kustomization 목록을 확인한 뒤 후보를 하나씩 render해 해당 환경변수 또는 `envFrom`
resource를 포함하는 최종 overlay를 찾습니다. 아래 두 조회 명령은 후보 경로마다 반복합니다.

```bash
find deploy -type f -name kustomization.yaml -print | sort

kubectl kustomize '<후보 overlay 경로>' \
  | rg -n -C 8 -- "name: ${TARGET_ENV_NAME}|envFrom:|<envFrom resource 이름>"
rg -n -C 8 -F -- 'path: <후보 overlay 경로>' scripts deploy
```

환경별 최종 overlay와 이를 가리키는 Application source가 정확히 하나여야 합니다. 선택한
source에서 아래 입력을 기록합니다.

```bash
TARGET_OVERLAY_PATH='<발견한 최종 overlay 경로>'
TARGET_NAMESPACE='<발견한 Namespace>'
TARGET_APPLICATION_NAME='<overlay를 가리키는 Application 이름>'
TARGET_APPLICATION_NAMESPACE='<Application Namespace>'
TARGET_FUNCTION_VERIFICATION_OWNER='<기존 검증 명령 또는 runbook section>'
TARGET_EXPECTED_FUNCTION_RESULT='<그 owner가 정한 성공 결과>'

kubectl kustomize "${TARGET_OVERLAY_PATH}" | less
```

`less`에서 `/<ENV_NAME>`과 `/envFrom:`을 검색합니다. 각 consumer의 kind·name·Namespace·
container와 workload라면 Pod selector, Job이라면 hook phase를 기록합니다. 기존 검증
명령·runbook section과 기대 결과까지 하나라도 확정할 수 없으면 변경하지 않습니다.

### 1.2 Secret identity 확인

Secret-backed 값일 때만 실행합니다. `<SECRET_NAME>`, `<SECRET_KEY>`, `<SECRET_OWNER>`와
`<SECRET_FIELD_MANAGER>`는 render와 producer source에서 각각 가져옵니다. Label owner를
field manager로 대신 사용하지 않습니다.

**실행 위치:** Target cluster에 접근 가능한 terminal

**시작 directory:** 제한 없음

```bash
TARGET_NAMESPACE='<1.1에서 기록한 Namespace>'
TARGET_SECRET_NAME='<발견한 Secret 이름>'
TARGET_SECRET_KEY='<발견한 Secret key>'
TARGET_SECRET_OWNER='<source가 선언한 managed-by 값>'
TARGET_SECRET_FIELD_MANAGER='<producer source가 사용하는 field manager>'

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get "secret/${TARGET_SECRET_NAME}" -n "${TARGET_NAMESPACE}" \
  --ignore-not-found \
  -o go-template='name={{.metadata.name}}{{"\n"}}namespace={{.metadata.namespace}}{{"\n"}}managedBy={{index .metadata.labels "app.kubernetes.io/managed-by"}}{{"\n"}}type={{.type}}{{"\n"}}{{range $key, $_ := .data}}key={{$key}}{{"\n"}}{{end}}'
```

기존 resource가 있으면 source의 Namespace·owner·type·key schema와 모두 같아야 합니다.
없으면 현재 source가 그 Secret과 key의 최초 생성을 명시하고 consumer reference가 optional이거나
아직 배포되지 않은 상태여야 합니다. 다른 Secret의 값을 읽거나 Secret을 삭제·재생성해
불일치를 우회하지 않습니다.

## 2. 변경 경로 선택

| Source에서 확인한 값 owner | 실행 경로 |
| --- | --- |
| Application code·property binding | [200 개발 작업 워크플로우](../workflow/200-development-workflow.md)로 구현·검증한 뒤 새 image를 배포하고 이 문서의 consumer·기능 검증 수행 |
| Git-tracked literal·ConfigMap | [3절](#3-git-managed-값-변경) |
| Kubernetes Secret | [4절](#4-secret-생성변경과-consumer-반영) |
| 새 환경변수 계약 | Application·manifest·`.env.example`·local development 문서를 함께 개발·검증한 뒤 1절부터 재실행 |

## 3. Git-managed 값 변경

### 3.1 Render diff와 server-side dry-run

**실행 위치:** Local repository terminal

**시작 directory:** Repository root

```bash
CONFIG_RENDER_DIRECTORY="$(mktemp -d /tmp/application-config-change.XXXXXX)"
trap 'rm -rf -- "${CONFIG_RENDER_DIRECTORY}"' EXIT INT TERM

kubectl kustomize "${TARGET_OVERLAY_PATH}" \
  > "${CONFIG_RENDER_DIRECTORY}/before.yaml"

# 여기서 source를 수정합니다.

kubectl kustomize "${TARGET_OVERLAY_PATH}" \
  > "${CONFIG_RENDER_DIRECTORY}/after.yaml"
diff -u "${CONFIG_RENDER_DIRECTORY}/before.yaml" \
  "${CONFIG_RENDER_DIRECTORY}/after.yaml" | less
```

Diff에서 입력한 환경변수와 1절에서 발견한 consumer만 바뀌었는지 확인합니다. ConfigMap
이름이 그대로인데 consumer Pod template diff가 없으면 변경 값이 새 Pod에 반영되지 않으므로
적용하지 않습니다.

```bash
env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i '<KEY_PATH>' 'ubuntu@<EC2_SSH_HOST>' \
  'sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -' \
  < "${CONFIG_RENDER_DIRECTORY}/after.yaml"

rm -rf -- "${CONFIG_RENDER_DIRECTORY}"
trap - EXIT INT TERM
```

예상하지 않은 resource 변경·생성 또는 error가 있으면 배포하지 않습니다.

### 3.2 GitOps 배포

[300 커밋 워크플로우](../workflow/300-commit-workflow.md)로 변경을 커밋하고 pull request에서
render diff와 consumer 영향을 review한 뒤 `main`에 merge합니다. Image digest도 함께 바꾸는
배포일 때만 [420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)의
게시 SHA·digest 절차를 사용합니다.

Config-only 변경은 merge 뒤 아래 입력을 target cluster terminal에 넣고 실행합니다.
`<MERGED_MAIN_SHA>`는 merge 결과의 40자리 SHA이며 Application·consumer identity는 1절의
기록값입니다.

**실행 위치:** Target cluster에 접근 가능한 terminal

**시작 directory:** 제한 없음

```bash
(
set -eu
TARGET_EXPECTED_REVISION='<MERGED_MAIN_SHA>'
TARGET_APPLICATION_NAME='<1.1에서 기록한 Application 이름>'
TARGET_APPLICATION_NAMESPACE='<1.1에서 기록한 Application Namespace>'
TARGET_APPLICATION_CONVERGED=false

for TARGET_WAIT_ATTEMPT in $(seq 1 60); do
  TARGET_APPLICATION_STATE="$(
    sudo KUBECONFIG=/etc/kubernetes/admin.conf \
      kubectl get "application/${TARGET_APPLICATION_NAME}" \
        -n "${TARGET_APPLICATION_NAMESPACE}" \
        -o jsonpath='{.status.sync.revision}{"|"}{.status.operationState.phase}{"|"}{.status.sync.status}{"|"}{.status.health.status}'
  )"
  if test "${TARGET_APPLICATION_STATE}" \
      = "${TARGET_EXPECTED_REVISION}|Succeeded|Synced|Healthy"; then
    TARGET_APPLICATION_CONVERGED=true
    break
  fi
  sleep 5
done
test "${TARGET_APPLICATION_CONVERGED}" = true

TARGET_APPLICATION_CONDITIONS="$(
  sudo KUBECONFIG=/etc/kubernetes/admin.conf \
    kubectl get "application/${TARGET_APPLICATION_NAME}" \
      -n "${TARGET_APPLICATION_NAMESPACE}" \
      -o jsonpath='{range .status.conditions[*]}{.type}{"="}{.message}{"\n"}{end}'
)"
test -z "${TARGET_APPLICATION_CONDITIONS}"

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get "application/${TARGET_APPLICATION_NAME}" \
    -n "${TARGET_APPLICATION_NAMESPACE}" \
    -o jsonpath='{range .status.operationState.syncResult.resources[*]}{.syncPhase}{"\t"}{.hookPhase}{"\t"}{.kind}{"\t"}{.namespace}{"\t"}{.name}{"\t"}{.status}{"\n"}{end}'
)
```

마지막 출력에서 1절에 기록한 모든 resource가 `Synced`이고 hook `Job`은 `Succeeded`인지
확인합니다. 이어서 아래 block을 각 `Deployment`·`StatefulSet`·`DaemonSet` consumer마다
변수 값을 바꿔 반복합니다.

```bash
(
set -eu
TARGET_WORKLOAD_KIND='<Deployment|StatefulSet|DaemonSet>'
TARGET_WORKLOAD_NAME='<consumer 이름>'
TARGET_WORKLOAD_NAMESPACE='<consumer Namespace>'
TARGET_POD_SELECTOR='<consumer Pod selector>'

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl rollout status \
    "${TARGET_WORKLOAD_KIND}/${TARGET_WORKLOAD_NAME}" \
    -n "${TARGET_WORKLOAD_NAMESPACE}" --timeout=300s
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl wait --for=condition=Ready pod \
    -n "${TARGET_WORKLOAD_NAMESPACE}" \
    -l "${TARGET_POD_SELECTOR}" --timeout=300s
)
```

수렴·condition·resource·consumer 중 하나라도 실패하면 직접 기능 검증으로 넘어가지 않습니다.
문제 설정 commit의 revert 또는 forward-fix를 3.1부터 다시 검증·merge합니다. 모두 성공하면
[5절](#5-직접-기능-검증과-완료)을 실행합니다.

## 4. Secret 생성·변경과 consumer 반영

### 4.1 새 값 준비

**실행 위치:** Secret 값을 안전하게 입력할 수 있는 target cluster terminal

**시작 directory:** 제한 없음

외부 provider credential이면 새 값을 먼저 발급하고 이전 값은 폐기하지 않습니다. 내부에서
생성하는 Secret이면 source가 정한 길이·형식·다른 Secret과의 분리 조건을 만족하는 새 값을
준비합니다. 실제 값은 shell argument, repository, 문서, log와 임시 파일에 남기지 않습니다.
기존 Secret을 바꿀 때에는 이전 값을 provider나 승인된 credential 보관소에서 다시 입력할
수 있어야 합니다. Kubernetes Secret의 현재 값을 복호화해 rollback 사본으로 만들지 않습니다.
이전 값을 안전하게 다시 확보할 수 없으면 변경하지 않습니다.

Source가 최소 byte 길이를 정하면 그 숫자를, 정하지 않으면 `0`을
`<MINIMUM_BYTES>`에 넣습니다. Source가 형식·다른 Secret과의 불일치 같은 추가 validation을
요구하면 아래 mutation block을 실행하기 전에 별도로 확인합니다. 실제 값 입력과 공통
non-blank·길이 검증은 선택한 mutation block 안에서 다시 수행하며 실패하면 즉시 종료합니다.

### 4.2 누락 Secret 생성

1.2에서 resource가 없다고 확인한 경우에만 실행합니다. `kubectl create ... --dry-run`이 만든
exact 한-key schema에 source의 owner label을 붙여 server-side apply합니다. 값은 stdin으로만
전달됩니다.

**실행 위치:** 4.1을 실행한 같은 target cluster terminal

**시작 directory:** 제한 없음

```bash
(
set -eu
set +x
set -o pipefail
trap 'unset TARGET_SECRET_VALUE TARGET_SECRET_VALUE_BASE64 TARGET_LIVE_SECRET_VALUE_BASE64' EXIT INT TERM
TARGET_MINIMUM_BYTES='<MINIMUM_BYTES>'
read -r -s -p '새 Secret 값을 입력하세요: ' TARGET_SECRET_VALUE
printf '\n'

if test -z "${TARGET_SECRET_VALUE}"; then
  printf '%s\n' 'Secret 값은 비어 있을 수 없습니다.' >&2
  exit 1
fi
if test "$(printf '%s' "${TARGET_SECRET_VALUE}" | wc -c | tr -d ' ')" \
    -lt "${TARGET_MINIMUM_BYTES}"; then
  printf '%s\n' 'Secret 값이 source의 최소 byte 길이보다 짧습니다.' >&2
  exit 1
fi
TARGET_SECRET_VALUE_BASE64="$(printf '%s' "${TARGET_SECRET_VALUE}" | base64 | tr -d '\n')"

printf '%s' "${TARGET_SECRET_VALUE}" \
  | KUBERC=off KUBECONFIG=/dev/null kubectl create secret generic "${TARGET_SECRET_NAME}" \
      --namespace="${TARGET_NAMESPACE}" \
      --from-file="${TARGET_SECRET_KEY}=/dev/stdin" \
      --dry-run=client -o yaml \
  | KUBERC=off KUBECONFIG=/dev/null kubectl label --local -f - \
      "app.kubernetes.io/managed-by=${TARGET_SECRET_OWNER}" -o yaml \
  | sudo KUBECONFIG=/etc/kubernetes/admin.conf \
      kubectl apply --server-side \
        --field-manager="${TARGET_SECRET_FIELD_MANAGER}" -f -

  TARGET_LIVE_SECRET_VALUE_BASE64="$(
    sudo KUBECONFIG=/etc/kubernetes/admin.conf \
      kubectl get "secret/${TARGET_SECRET_NAME}" -n "${TARGET_NAMESPACE}" \
      -o "go-template={{index .data \"${TARGET_SECRET_KEY}\"}}"
  )"
  test "${TARGET_LIVE_SECRET_VALUE_BASE64}" = "${TARGET_SECRET_VALUE_BASE64}"
)
```

Source가 둘 이상의 key를 required schema로 정하면 이 한-key 생성 명령을 사용하지 않습니다.
모든 값을 같은 안전 기준으로 준비하고 `kubectl create secret generic`의 `--from-file` 입력을
key마다 구성한 뒤, 생성할 전체 schema를 값 없이 재검토해야 합니다.

### 4.3 기존 Secret의 한 key 변경

1.2에서 existing owner·type·schema가 모두 일치한 경우에만 실행합니다. 현재
`resourceVersion`을 precondition으로 넣은 merge patch는 다른 key와 metadata를 유지하고,
동시에 먼저 일어난 변경을 덮어쓰지 않습니다. Secret 값은 base64로 Kubernetes API에
전달되며 암호화로 간주하지 않습니다.

**실행 위치:** 4.1을 실행한 같은 target cluster terminal

**시작 directory:** 제한 없음

```bash
(
set -eu
set +x
set -o pipefail
trap 'unset TARGET_SECRET_VALUE TARGET_SECRET_VALUE_BASE64 TARGET_LIVE_SECRET_VALUE_BASE64' EXIT INT TERM
TARGET_MINIMUM_BYTES='<MINIMUM_BYTES>'
read -r -s -p '새 Secret 값을 입력하세요: ' TARGET_SECRET_VALUE
printf '\n'

if test -z "${TARGET_SECRET_VALUE}"; then
  printf '%s\n' 'Secret 값은 비어 있을 수 없습니다.' >&2
  exit 1
fi
if test "$(printf '%s' "${TARGET_SECRET_VALUE}" | wc -c | tr -d ' ')" \
    -lt "${TARGET_MINIMUM_BYTES}"; then
  printf '%s\n' 'Secret 값이 source의 최소 byte 길이보다 짧습니다.' >&2
  exit 1
fi
TARGET_SECRET_VALUE_BASE64="$(printf '%s' "${TARGET_SECRET_VALUE}" | base64 | tr -d '\n')"

TARGET_SECRET_RESOURCE_VERSION="$(
  sudo KUBECONFIG=/etc/kubernetes/admin.conf \
    kubectl get "secret/${TARGET_SECRET_NAME}" -n "${TARGET_NAMESPACE}" \
    -o jsonpath='{.metadata.resourceVersion}'
)"
printf '{"metadata":{"resourceVersion":"%s"},"data":{"%s":"%s"}}\n' \
  "${TARGET_SECRET_RESOURCE_VERSION}" \
  "${TARGET_SECRET_KEY}" \
  "${TARGET_SECRET_VALUE_BASE64}" \
| sudo KUBECONFIG=/etc/kubernetes/admin.conf \
    kubectl patch "secret/${TARGET_SECRET_NAME}" \
      -n "${TARGET_NAMESPACE}" --type=merge \
      --field-manager="${TARGET_SECRET_FIELD_MANAGER}" \
      --patch-file=/dev/stdin

  TARGET_LIVE_SECRET_VALUE_BASE64="$(
    sudo KUBECONFIG=/etc/kubernetes/admin.conf \
      kubectl get "secret/${TARGET_SECRET_NAME}" -n "${TARGET_NAMESPACE}" \
      -o "go-template={{index .data \"${TARGET_SECRET_KEY}\"}}"
  )"
  test "${TARGET_LIVE_SECRET_VALUE_BASE64}" = "${TARGET_SECRET_VALUE_BASE64}"
)
```

`Conflict`, schema 불일치 또는 owner 불일치가 발생하면 다시 읽지 않은 값으로 재시도하지
않습니다. 1.2부터 현재 identity를 다시 확인합니다.

### 4.4 새 Pod에 반영

Secret environment 값은 기존 Pod에 자동 반영되지 않습니다. 같은 변경에서 Git-managed
Pod template 변경을 배포한다면 그 rollout을 사용합니다. Secret만 변경한다면 아래 block을
1절에서 발견한 `Deployment`·`StatefulSet`·`DaemonSet`마다 변수 값을 바꿔 반복합니다.

**실행 위치:** Target cluster에 접근 가능한 terminal

**시작 directory:** 제한 없음

```bash
(
set -eu
TARGET_WORKLOAD_KIND='<Deployment|StatefulSet|DaemonSet>'
TARGET_WORKLOAD_NAME='<consumer 이름>'
TARGET_WORKLOAD_NAMESPACE='<consumer Namespace>'
TARGET_POD_SELECTOR='<consumer Pod selector>'
TARGET_APPLICATION_NAME='<1.1에서 기록한 Application 이름>'
TARGET_APPLICATION_NAMESPACE='<1.1에서 기록한 Application Namespace>'
TARGET_EXPECTED_REVISION="$(
  sudo KUBECONFIG=/etc/kubernetes/admin.conf \
    kubectl get "application/${TARGET_APPLICATION_NAME}" \
      -n "${TARGET_APPLICATION_NAMESPACE}" \
      -o jsonpath='{.status.sync.revision}'
)"

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl rollout restart \
    "${TARGET_WORKLOAD_KIND}/${TARGET_WORKLOAD_NAME}" \
    -n "${TARGET_WORKLOAD_NAMESPACE}"

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl rollout status \
    "${TARGET_WORKLOAD_KIND}/${TARGET_WORKLOAD_NAME}" \
    -n "${TARGET_WORKLOAD_NAMESPACE}" --timeout=300s

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl wait --for=condition=Ready pod \
    -n "${TARGET_WORKLOAD_NAMESPACE}" \
    -l "${TARGET_POD_SELECTOR}" --timeout=300s

TARGET_APPLICATION_CONVERGED=false
for TARGET_WAIT_ATTEMPT in $(seq 1 60); do
  TARGET_APPLICATION_STATE="$(
    sudo KUBECONFIG=/etc/kubernetes/admin.conf \
      kubectl get "application/${TARGET_APPLICATION_NAME}" \
        -n "${TARGET_APPLICATION_NAMESPACE}" \
        -o jsonpath='{.status.sync.revision}{"|"}{.status.sync.status}{"|"}{.status.health.status}'
  )"
  TARGET_RESTART_ANNOTATION="$(
    sudo KUBECONFIG=/etc/kubernetes/admin.conf \
      kubectl get "${TARGET_WORKLOAD_KIND}/${TARGET_WORKLOAD_NAME}" \
        -n "${TARGET_WORKLOAD_NAMESPACE}" \
        -o jsonpath='{.spec.template.metadata.annotations.kubectl\.kubernetes\.io/restartedAt}'
  )"
  if test "${TARGET_APPLICATION_STATE}" \
      = "${TARGET_EXPECTED_REVISION}|Synced|Healthy" \
      && test -z "${TARGET_RESTART_ANNOTATION}"; then
    TARGET_APPLICATION_CONVERGED=true
    break
  fi
  sleep 5
done
test "${TARGET_APPLICATION_CONVERGED}" = true

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl rollout status \
    "${TARGET_WORKLOAD_KIND}/${TARGET_WORKLOAD_NAME}" \
    -n "${TARGET_WORKLOAD_NAMESPACE}" --timeout=300s
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl wait --for=condition=Ready pod \
    -n "${TARGET_WORKLOAD_NAMESPACE}" \
    -l "${TARGET_POD_SELECTOR}" --timeout=300s
)
```

Secret-only restart는 Git desired state 밖의 Pod template annotation을 잠시 만듭니다. 위 bounded
조회는 Argo CD가 원래 revision의 `Synced`·`Healthy` 상태로 돌아오고 그 annotation을 제거한
뒤에만 최종 rollout·Ready 확인으로 진행합니다. 5분 안에 수렴하지 않으면 후속 기능 검증을
실행하지 않고 [180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md)에서
재개합니다.

Argo hook `Job` consumer는 `rollout restart` 대상이 아닙니다. 변경 값을 다음 완료 전에
소비해야 한다면 1절에서 발견한 기존 GitOps 재실행 절차로 새 Job을 만들고 성공을 확인합니다.
그 entrypoint가 없으면 Secret을 변경하기 전에 중단합니다.

## 5. 직접 기능 검증과 완료

`TARGET_FUNCTION_VERIFICATION_OWNER`로 고정한 기존 명령 또는 runbook section을 그대로
실행하고 `TARGET_EXPECTED_FUNCTION_RESULT`와 대조합니다. 이 두 입력이 없으면 검증 명령을
여기서 새로 만들지 않고 변경 전에 중단합니다. Ready 상태나 Swagger 노출만으로 실제
provider 요청, 서명 검증, DB·object 저장 같은 값의 기능 성공을 대신하지 않습니다.

HTTP endpoint가 직접 검증 owner이면 controller mapping, access policy와 contract test에서
method·path·성공 status·필수 response pattern을 기록하고 아래 표준 명령을 실행합니다.
인증이 필요하면 credential literal 대신 mode `600`인 curl config에 기존 인증 절차의 header나
cookie를 준비합니다. Public endpoint는 config path로 `/dev/null`을 사용합니다. 확인할 endpoint가
여러 개면 입력을 바꿔 같은 block을 반복합니다.

**실행 위치:** Target Application에 요청할 수 있는 client terminal

**시작 directory:** 제한 없음

```bash
(
set -eu
TARGET_HTTP_METHOD='<source의 HTTP method>'
TARGET_HTTP_URL='<target base URL과 source path를 결합한 URL>'
TARGET_EXPECTED_HTTP_STATUS='<source의 성공 status>'
TARGET_EXPECTED_BODY_PATTERN='<source의 필수 response pattern; 없으면 빈 값>'
TARGET_CURL_CONFIG='<mode 600 curl config path 또는 /dev/null>'
TARGET_HTTP_RESPONSE_FILE="$(mktemp /tmp/application-env-check.XXXXXX)"
trap 'rm -f -- "${TARGET_HTTP_RESPONSE_FILE}"' EXIT INT TERM

TARGET_ACTUAL_HTTP_STATUS="$(
  curl --silent --show-error \
    --config "${TARGET_CURL_CONFIG}" \
    --request "${TARGET_HTTP_METHOD}" \
    --output "${TARGET_HTTP_RESPONSE_FILE}" \
    --write-out '%{http_code}' \
    "${TARGET_HTTP_URL}"
)"
test "${TARGET_ACTUAL_HTTP_STATUS}" = "${TARGET_EXPECTED_HTTP_STATUS}"
if test -n "${TARGET_EXPECTED_BODY_PATTERN}"; then
  rg -q -F -- "${TARGET_EXPECTED_BODY_PATTERN}" "${TARGET_HTTP_RESPONSE_FILE}"
fi
)
```

외부 provider credential은 새 값으로 대표 요청이 성공한 사실을 provider 측에서도 확인한
뒤에만 이전 credential을 폐기합니다. 폐기 뒤 대표 요청을 다시 확인합니다.

기능 검증이 실패하면 이전 값을 폐기하지 않습니다. 기존 Secret 변경이었다면 4.1에서
확보한 이전 값을 다시 입력해 4.3~4.4로 복원하고 직접 기능을 재검증합니다. 누락 Secret을
새로 만든 경우에는 consumer의 Git 변경을 되돌리고 Secret은 비활성 상태로 유지합니다.
삭제는 이 절차의 자동 rollback에 포함하지 않습니다. Git-managed 값이면 live
resource를 patch하지 않고 문제 commit의 revert 또는 forward-fix를 같은 render·GitOps
절차로 배포합니다.

완료 조건은 다음과 같습니다.

- 입력한 환경변수의 binding, owner, value source와 모든 consumer를 source에서 확인했습니다.
- Git-managed 값은 render·server-side dry-run·exact revision과 consumer rollout을 확인했습니다.
- Secret은 값을 노출하지 않고 exact owner·schema에 생성 또는 변경했고 새 Pod를 확인했습니다.
- 변경 값을 실제로 사용하는 직접 기능이 성공했습니다.
- Provider credential이면 새 값 성공 뒤 이전 credential을 폐기하고 다시 확인했습니다.
- 새 계약이면 `.env.example`과 local development 문서까지 동기화했습니다.
