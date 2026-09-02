# Application 환경변수·Secret 변경 runbook

이 절차는 최초 Application bootstrap 뒤 환경변수 계약이나 값을 추가·변경할 때 현재
실행 가능한 owner를 찾는 반복 운영 진입점입니다. 현재 변수·Secret inventory와 실제 값은
기록하지 않고 실행 시 repository source와 live Kubernetes에서 확인합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | 최초 Application sync 뒤 환경변수를 추가하거나 현재 값을 바꿀 때 사용합니다. |
| 누가 | Repository 또는 credential owner 권한과 target 환경 확인 권한을 가진 운영자가 실행합니다. |
| 어디서 | Source 확인은 local repository terminal, live 확인은 target cluster에 접근 가능한 terminal에서 수행합니다. |
| 무엇을 | 값 owner, 모든 consumer와 검증 entrypoint를 확인해 기존 canonical 변경 절차로 연결합니다. |
| 완료·재개 | 연결된 절차의 rollback·기능 검증까지 성공하면 완료합니다. 안전한 entrypoint가 없으면 값을 변경하지 않고 아래 중단점에서 재개합니다. |

## 1. 대상·consumer와 검증 경로 확인

### 1.1 Repository source와 render

**실행 위치:** Local repository terminal

**시작 directory:** Repository root

**필수 입력:** `<ENV_NAME>`은 추가하거나 변경할 application 환경변수 이름입니다.

```bash
TARGET_ENV_NAME='<ENV_NAME>'

# 1. Application·manifest·local·operation owner source 확인
printf '\n\n========== [1/2] environment sources ==========\n\n'
rg -n -- "${TARGET_ENV_NAME}" \
  src/main/resources deploy scripts/bootstrap/application \
  scripts/operations/application scripts/lib \
  .env.example docs/development/100-local-development.md

# 2. 전체 render에서 resource와 모든 consumer 확인
printf '\n\n========== [2/2] rendered consumers ==========\n\n'
kubectl kustomize deploy/overlays/stg | less
```

`less`에서 `/<ENV_NAME>`과 `/envFrom:`을 검색합니다. 발견한 모든 resource의 `kind`,
`metadata.name`, `metadata.namespace`, container와 value source를 함께 확인합니다.
`Deployment`·`StatefulSet`·`DaemonSet`은 Pod selector까지, Argo hook `Job`은 hook phase와
kind·name·namespace까지 기록합니다. 둘 이상의 consumer가 있으면 모두 같은 변경
범위에 포함합니다. Consumer 유형에 필요한 identity 하나라도 식별할 수 없으면 값을
변경하지 않습니다.

Render에서 Secret reference를 발견했다면 아래 live identity 확인을 계속합니다.
발견한 Secret을 생성·변경하는 `<OWNER_SOURCE_PATH>`에서 live label과 비교할
semantic owner 선언과 사용부를 먼저 출력합니다.

```bash
TARGET_OWNER_SOURCE='<OWNER_SOURCE_PATH>'
rg -n -C 4 'readonly (APPLICATION_OWNER|FIELD_MANAGER)=|application_secret_apply_notification|local owner_label=|\$\{owner_label\}|app\.kubernetes\.io/managed-by' \
  "${TARGET_OWNER_SOURCE}" scripts/lib/application-secret.sh
```

`managed-by` 사용부에 전달되는 `APPLICATION_OWNER` 또는 `FIELD_MANAGER` 값을 live
`managedBy`의 기대값으로 고정합니다. Shared function을 사용하면 caller의 argument,
callee의 `owner_label` 매핑과 `managed-by` 사용부까지 위 context 출력에서 연결합니다.
선언과 사용부의 연결을 해당 source에서 확인할 수 없으면 추측하지 않습니다.

### 1.2 Live Secret identity

**실행 위치:** Target EC2 terminal

**시작 directory:** 제한 없음

값을 출력하지 않고 resource identity·semantic owner·key schema만 확인합니다.

```bash
TARGET_NAMESPACE='<NAMESPACE>'
TARGET_SECRET_NAME='<SECRET_NAME>'

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get "secret/${TARGET_SECRET_NAME}" -n "${TARGET_NAMESPACE}" \
  -o go-template='name={{.metadata.name}}{{"\n"}}namespace={{.metadata.namespace}}{{"\n"}}managedBy={{index .metadata.labels "app.kubernetes.io/managed-by"}}{{"\n"}}type={{.type}}{{"\n"}}{{range $key, $_ := .data}}key={{$key}}{{"\n"}}{{end}}'
```

출력한 Secret 이름·key와 repository 검색 결과의 bootstrap·operation owner가 일치하는지
확인합니다. Resource가 없거나 소유 source, `managedBy` 또는 key schema가 서로 다르면
새 resource를 만들거나 값을 변경하지 않습니다.

변경 전에 해당 값의 직접 기능을 검증할 canonical entrypoint와 기대 결과도
[000 운영 문서 목차](000-index.md) 또는 기능 owner에서 확인합니다. 찾지 못하면 Ready 상태를
기능 성공으로 대신하지 않고 검증 절차를 먼저 개발합니다.

## 2. 확인 결과에 따른 변경 경로

| 확인 결과 | 현재 실행 경로 |
| --- | --- |
| Application code·`application.yaml` 변경 | [200 개발 작업 워크플로우](../workflow/200-development-workflow.md)로 구현·검증하고 [400 GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)와 [420 GitOps 배포와 rollback](420-gitops-deployment-and-rollback-runbook.md)으로 새 image 배포 |
| Deployment literal·Git-tracked ConfigMap 변경 | [3절](#3-git-managed-설정-변경과-배포-및-rollback) 실행 |
| `RESEND_API_KEY` 값 교체 | [4절](#4-resend-api-key-교체) 실행 |
| 전용 entrypoint가 없는 기존 Secret key 추가·값 변경 | [5절](#5-전용-entrypoint가-없는-secret-변경의-현재-중단점)에서 중단 |
| 새 환경변수 계약 | Application·manifest와 `.env.example`·local development 문서의 적용 여부를 함께 개발·검증한 뒤 위 경로를 다시 선택 |

Git-managed 값은 live ConfigMap이나 workload environment를 직접 patch하지 않습니다. 새
Secret-backed consumer는 Secret key를 안전하게 준비할 entrypoint가 생기기 전에 required
reference를 배포하지 않습니다.

## 3. Git-managed 설정 변경과 배포 및 rollback

### 3.1 Local render와 consumer gate

**실행 위치:** Local repository terminal

**시작 directory:** Repository root

수정 전 baseline을 고유 임시 directory에 render하고
[200 개발 작업 워크플로우](../workflow/200-development-workflow.md)로 source를 수정한 뒤 candidate를
render합니다.

```bash
CONFIG_RENDER_DIRECTORY="$(mktemp -d /tmp/car-community-config-change.XXXXXX)"
trap 'rm -rf -- "${CONFIG_RENDER_DIRECTORY}"' EXIT INT TERM
kubectl kustomize deploy/overlays/stg > "${CONFIG_RENDER_DIRECTORY}/before.yaml"
# 여기서 source를 수정합니다.
kubectl kustomize deploy/overlays/stg > "${CONFIG_RENDER_DIRECTORY}/after.yaml"
diff -u "${CONFIG_RENDER_DIRECTORY}/before.yaml" \
  "${CONFIG_RENDER_DIRECTORY}/after.yaml" | less
```

Diff에서 의도한 설정·consumer만 바뀌었는지 확인합니다. 1절에서 발견한 **모든**
consumer별로 candidate Pod template에 예상한 reference·value diff가 있어야 합니다.
ConfigMap 이름이 그대로이고 해당 consumer Pod template diff가 없으면 새 Pod가 변경 값을
읽지 못하므로 적용하지 말고 Git-owned rollout 계약을 먼저 개발·검증합니다.

### 3.2 Target server-side dry-run

**실행 위치:** 3.1을 실행한 같은 local shell session에서 target EC2로 SSH

**시작 directory:** Repository root

**필수 입력:** 3.1이 생성한 같은 shell의 `CONFIG_RENDER_DIRECTORY`와 그 아래
`after.yaml`, `<KEY_PATH>`, `<EC2_SSH_HOST>`입니다.

`<KEY_PATH>`와 `<EC2_SSH_HOST>`를 target access 값으로 바꿔 candidate를 검증합니다.

```bash
env -i HOME="${HOME}" PATH="${PATH}" \
  ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
  'sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -' \
  < "${CONFIG_RENDER_DIRECTORY}/after.yaml"
```

Dry-run 결과는 baseline·candidate diff와 live lifecycle에 대조해 다음처럼 판정합니다.

| 결과 | 허용 조건 |
| --- | --- |
| `unchanged (server dry run)` | Candidate에서 바뀌지 않은 기존 resource |
| `configured (server dry run)` | Baseline·candidate diff에 있는 기존 resource |
| `created (server dry run)` | Diff에서 예상한 새 suffix generated ConfigMap, 성공 후 삭제되는 기존 Argo hook Job 또는 review한 신규 resource |

표와 다른 상태, 예상하지 않은 `created` 또는 error가 하나라도 있으면 적용하지
않습니다. 확인 뒤 임시 render를 정리합니다.

```bash
rm -rf -- "${CONFIG_RENDER_DIRECTORY}"
trap - EXIT INT TERM
```

### 3.3 Local Git 반영

**실행 위치:** Local repository terminal

**시작 directory:** Repository root

[300 커밋 워크플로우](../workflow/300-commit-workflow.md)로 의미 단위 커밋을 만듭니다.

### 3.4 Pull request 승인과 merge

**실행 위치:** GitHub Web UI

Pull request에서 render diff, 모든 consumer의 Pod 재생성 또는 Argo hook 실행, 직접
기능 검증 계획을 review한 뒤 `main`에 merge합니다. Direct push로 운영 승인을
우회하지 않습니다.

### 3.5 Exact revision과 consumer 확인

**실행 위치:** Target EC2 terminal

**시작 directory:** 제한 없음

Application 전체와 Argo hook consumer를 먼저 확인합니다.

```bash
EXPECTED_REVISION='<MERGED_MAIN_SHA>'

# 1. Application 상태
# 기대: <MERGED_MAIN_SHA>|Succeeded|Synced|Healthy
printf '\n\n========== [1/3] Application state ==========\n\n'
APPLICATION_STATE="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{.status.sync.revision}{"|"}{.status.operationState.phase}{"|"}{.status.sync.status}{"|"}{.status.health.status}')"
printf '%s\n' "${APPLICATION_STATE}"
test "${APPLICATION_STATE}" = "${EXPECTED_REVISION}|Succeeded|Synced|Healthy"

# 2. Application condition
# 기대: 출력 없음
printf '\n\n========== [2/3] Application conditions ==========\n\n'
APPLICATION_CONDITIONS="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{range .status.conditions[*]}{.type}{"="}{.message}{"\n"}{end}')"
printf '%s' "${APPLICATION_CONDITIONS}"
test -z "${APPLICATION_CONDITIONS}"

# 3. Argo hook·resource 결과
# 기대: 1절에서 발견한 각 hook Job이 Succeeded이고 각 resource가 Synced
printf '\n\n========== [3/3] Argo hook and resources ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{range .status.operationState.syncResult.resources[*]}{.syncPhase}{"\t"}{.hookPhase}{"\t"}{.kind}{"\t"}{.name}{"\t"}{.status}{"\n"}{end}'
```

1절에서 발견한 각 `Deployment`·`StatefulSet`·`DaemonSet` consumer에 아래 블록을
반복합니다. `<WORKLOAD_KIND>`, `<WORKLOAD_NAME>`, `<NAMESPACE>`와 `<POD_SELECTOR>`는
1절의 같은 consumer 기록을 사용합니다.

```bash
WORKLOAD_KIND='<Deployment|StatefulSet|DaemonSet>'
WORKLOAD_NAME='<WORKLOAD_NAME>'
WORKLOAD_NAMESPACE='<NAMESPACE>'
WORKLOAD_POD_SELECTOR='<POD_SELECTOR>'

# 1. Workload rollout
# 기대: successfully rolled out
printf '\n\n========== [1/2] Workload rollout ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl rollout status "${WORKLOAD_KIND}/${WORKLOAD_NAME}" \
  -n "${WORKLOAD_NAMESPACE}" --timeout=300s

# 2. Consumer Pod Ready
# 기대: selector에 해당하는 모든 현재 Pod가 Ready
printf '\n\n========== [2/2] Consumer Pod Ready ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl wait --for=condition=Ready pod \
  -n "${WORKLOAD_NAMESPACE}" -l "${WORKLOAD_POD_SELECTOR}" --timeout=300s
```

1절에서 고정한 직접 기능 검증까지 실행해 변경 값이 모든 consumer에서 작동함을
확인해야 완료입니다. 새 consumer 유형의 postcondition을 이 절에서 표현할 수 없으면
추측하지 말고 해당 검증 계약을 먼저 개발합니다. Argo CD 불일치는
[180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md), workload 실패는
[170 Kubernetes workload 진단](diagnostics/170-kubernetes-workload-network-events-and-logs.md)에서 재개합니다.

### 3.6 실패와 rollback

배포 또는 직접 기능 검증이 실패하면 live resource를 patch하지 않습니다. 원인을
수정한 forward-fix 또는 문제 설정 commit만 되돌리는 revert를
[3.1 Local render와 consumer gate](#31-local-render와-consumer-gate)의 repository root에서 작성·검증하고,
[3.3 Local Git 반영](#33-local-git-반영)에서 commit합니다. Pull request review·merge는
[3.4 Pull request 승인과 merge](#34-pull-request-승인과-merge)에서 재개합니다.

새 merge SHA로 render·target dry-run·exact revision·모든 consumer·직접 기능 검증을
반복합니다. 변경 전 값을 문서에 기록하거나 live patch로 rollback하지 않습니다.

## 4. Resend API key 교체

이 절은 일반 환경변수·Secret 변경 가운데 현재 검증된 전용 entrypoint가 있는
`RESEND_API_KEY` 값 교체만 소유합니다. 최초 `car-community-notification` Secret 생성은
[350 Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)이
소유합니다.

### 4.1 실행 전 경계

- Provider에서 새 key를 발급하고 기존 key는 아직 폐기하지 않습니다.
- `car-community` Namespace, bootstrap owner의 `car-community-notification` Secret과 Argo CD
  owner의 `car-community` Deployment가 이미 존재해야 합니다.
- Script는 `RESEND_API_KEY` 외의 임의 key, 다른 Secret 이름과 최초 resource 생성을 받지 않습니다.
- Secret 값은 environment variable로 주입되므로 실행 중 Pod에는 자동 반영되지 않습니다.
  Script가 Deployment annotation을 current Secret `resourceVersion`으로 바꾸고 새 Pod Ready까지
  기다립니다.

### 4.2 Local 입력 준비

**실행 위치:** Local repository terminal

**시작 directory:** `.env`가 바로 아래에 보이는 repository root

[150 EC2 실행 artifact 전달](150-ec2-execution-artifact-transfer-runbook.md)을 완료해 operation
script를 고정 절대 경로에 준비합니다. `.env`의 owner·type·mode를 확인하고 xtrace를 끈 뒤
값만 읽습니다. Actual key를 출력하지 않습니다.

```bash
set +x
test -f .env
test ! -L .env
test "$(stat -f '%u' .env)" = "$(id -u)"
test "$(stat -f '%Lp' .env)" = '600'
. ./.env
typeset +x STG_RESEND_API_KEY
test -n "${STG_RESEND_API_KEY}"
```

### 4.3 적용과 rollout

**실행 위치:** Local repository terminal

**시작 directory:** Repository root

`<KEY_PATH>`를 local SSH private key의 절대 경로로 바꿔 같은 terminal에서 실행합니다.

```bash
printf '%s\0' "${STG_RESEND_API_KEY}" \
| env -i HOME="${HOME}" PATH="${PATH}" ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
  'sudo -n /home/ubuntu/car-community-scripts/operations/application/reconcile-notification-secret.sh'
```

Exit code `0`과 다음 완료 메시지 중 하나를 확인합니다.

```text
[완료] Notification Secret을 적용하고 새 runtime Pod Ready를 확인했습니다.
[완료] Notification Secret과 runtime Pod가 이미 목표 상태입니다.
```

Script는 actual value를 출력하지 않고 Secret owner·type·exact key·encoded value와 Deployment
rollout annotation을 API에서 검증합니다. `--force-conflicts`와 Secret 삭제는 사용하지 않습니다.

### 4.4 Provider 전환 완료

**실행 위치:** `1`·`4`는 Staging service 요청을 보낼 client, `2`·`3`은 Resend Console

새 Pod Ready만으로 Resend provider가 새 key를 실제 사용했다고 단정하지 않습니다.

1. Staging의 이메일 인증, 관리자 초대 또는 비밀번호 재설정 중 대표 email을 한 건 발송합니다.
2. Resend에서 새 key의 요청 성공을 확인하되 recipient·token·API key를 기록하지 않습니다.
3. 성공한 뒤에만 기존 Resend key를 폐기합니다.
4. 폐기 뒤 대표 email을 다시 확인합니다.

### 4.5 중단과 재개

**실행 위치:** 입력·script 재실행은 Local repository terminal, rollout 확인은 target EC2
terminal, provider key 확인·폐기는 Resend Console, service 응답 재확인은 Staging service에
요청을 보낼 client에서 수행합니다.

**시작 directory:** Local terminal은 repository root, target EC2 terminal은 제한 없음

| 실패 위치 | 남을 수 있는 상태 | 재개 |
| --- | --- | --- |
| 입력·owner·resource preflight | 변경 없음 | 원인을 고친 뒤 같은 명령 |
| Secret apply 실패 | 이전 값 또는 API가 적용한 새 값 | 값을 바꾸지 말고 같은 명령 |
| Secret apply 뒤 rollout 실패 | Secret은 새 값, Deployment annotation은 이전 값일 수 있음 | 같은 명령이 current `resourceVersion` rollout을 재개 |
| 새 Pod Ready 뒤 email smoke 실패 | 새 key를 가진 Pod | 기존 key를 폐기하지 말고 Pod log·provider 설정을 진단한 뒤 재확인 |
| 기존 key 폐기 뒤 실패 | Provider rollback 필요 가능 | Provider가 허용하면 기존 key를 복구하고 이 절차를 새 key로 다시 수행 |

### 4.6 Notification key schema 변경의 현재 중단점

현재 executable의 approved schema는 `RESEND_API_KEY` 하나입니다. 새 notification key를
추가하거나 기존 key를 schema에서 제거하는 lifecycle에는 현재 안전한 repository
entrypoint와 검증·재개 계약이 없습니다. `reconcile-notification-secret.sh`에 임의 field를
전달하거나 빈 문자열·Secret object 삭제로 우회하지 않습니다.

Application code·properties, `.env.example`, bootstrap reconstruction, operation schema,
Deployment consumer와 Secret field-manager ownership을 함께 변경하는 개발 범위를 먼저
논의·승인해야 합니다. 그 구현과 검증된 Procedure가 마련되기 전에는 schema 변경을
실행하지 않습니다.

## 5. 전용 entrypoint가 없는 Secret 변경의 현재 중단점

현재 repository에는 임의 Application Secret key를 안전하게 추가·변경하는 canonical
entrypoint가 없습니다. Bootstrap entrypoint는 최초 구축의 고정된 전체 schema와 값
불변식을 소유하고, notification operation은 승인된 provider key 하나만 변경합니다.
두 절차를 일반 변경에 재사용하지 않습니다.

일반 Secret 변경은 다음 조건을 한 번에 보장해야 합니다.

- 실제 값이 argument·log·repository·임시 파일에 남지 않습니다.
- 현재 owner가 정한 non-blank, byte 길이와 key 사이 불일치 같은 값 불변식을 적용합니다.
- 동일 key의 동시 변경을 덮어쓰지 않습니다.
- 다른 key와 Secret owner metadata를 유지합니다.
- 기존 Pod 교체, 직접 기능 검증과 같은 key 단위 rollback을 하나의 재개 가능한 결과로 묶습니다.

표준 `kubectl patch` 몇 줄만으로는 현재 key별 값 검증과 안전한 입력·rollback 전체를
보장할 수 없습니다. 이 logic을 긴 Bash block으로 문서 안에 구현하는 것도 검증되지 않은
프로그램을 새 관리 지점으로 만드는 것이므로 허용하지 않습니다.

따라서 전용 entrypoint가 없는 Secret은 live 값을 출력하거나 `kubectl edit`, 임의 patch,
Secret 삭제·재생성, bootstrap 재실행과 빈 문자열로 우회하지 않습니다. 현재 수정 가능한
범위는 3절의 Git-managed 값과 [4절](#4-resend-api-key-교체)처럼 검증된 전용 절차가 존재하는
credential뿐입니다.

일반 Secret 변경을 실제로 열려면 별도 승인된 개발 범위에서 key별 validation source를
소비하고 stdin 입력·동시성·Pod 반영·rollback을 검증한 canonical entrypoint를 먼저 만든
뒤 이 문서를 갱신해야 합니다. 별도 executable을 만들지 않는 제약이 유지되는 동안에는
이 중단점이 재개 조건입니다.

## 6. 완료 확인

- Current source·Secret metadata와 전체 render에서 값 owner·key schema·모든 consumer를 확인했습니다.
- 새 환경변수 계약이면 `.env.example`과 local development 문서의 적용 여부도 확인했습니다.
- 실행 전에 직접 기능 검증 entrypoint·기대 결과·rollback을 확정했습니다.
- 기존 canonical 변경 절차만 사용했고 현재 변수 inventory나 실제 값을 이 문서에 추가하지 않았습니다.
- 일반 Secret 변경에 안전한 entrypoint가 없으면 live mutation 없이 중단했습니다.
