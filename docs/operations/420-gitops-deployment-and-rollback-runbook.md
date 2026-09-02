# GitOps application 배포와 rollback runbook

Private ECR의 exact image digest를 현재 `car-community` repository의
`deploy/overlays/stg` 목표 상태로 변경하는 pull request를 review·merge하고, Argo CD 자동
sync와 workload 상태를 확인하는 반복 운영 절차입니다. Rollback도 live resource 직접
변경이 아니라 이전 digest로 되돌리는 revert pull request로 수행합니다.

Source→image→GitOps→cluster actor와 자동화·수동 경계는
[220 GitOps application delivery](../project/infrastructure/220-gitops-application-delivery.md)에서
확인합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | 최초 Application bootstrap이 완료된 뒤 새 exact digest를 staging에 배포하거나 이전 digest로 되돌릴 때 반복합니다. |
| 누가 | Repository PR 권한과 target EC2 SSH·cluster read 권한을 가진 운영자가 실행합니다. |
| 어디서 | Local terminal과 SSH로 접속한 target EC2 terminal입니다. |
| 시작 directory | Local 명령은 repository root에서 실행합니다. EC2 명령의 시작 directory에는 제한이 없습니다. |
| 위치별 역할 | Local에서 manifest 편집·render·PR을 수행하고 EC2에서 live 상태를 확인합니다. |
| 무엇을 | `deploy/overlays/stg/kustomization.yaml`의 digest 변경, `kubectl kustomize`, target dry-run, pull request와 표준 `kubectl` read-only 확인을 사용합니다. 현재 digest 변경 전용 repository script는 없습니다. |
| 왜 | Git PR을 배포 승인·이력·rollback의 단일 경계로 유지하고 live resource 직접 변경을 막기 위해서입니다. |
| 완료·재개 | Merge revision이 `Succeeded|Synced|Healthy`이고 Pod imageID가 목표 digest면 완료입니다. 실패하면 Git forward-fix 또는 revert PR에서 재개합니다. |

## 실행 prerequisite와 현재 상태 확인

현재 repository에는 staging Kustomize manifest가 구현돼 있지만 digest 변경 PR 자동화는
없습니다. 따라서 표준 `kubectl kustomize`와 target cluster의 server-side dry-run으로
직접 확인한 뒤 이 manual 절차를 실행합니다.

- `deploy/overlays/stg`가 존재하고 non-empty resource를 render합니다.
- `deploy/overlays/stg/kustomization.yaml`의 `images[].digest`가 application image reference를
  소유합니다.
- Runtime·migrator DB role과 Git 밖 Namespace·다섯 Secret, Git의 actual DB URL·RDS CA·notification
  ConfigMap source가 준비됐습니다.
- `main` 변경은 pull request review 뒤 merge하는 운영 정책을 따릅니다. 현재 GitHub branch
  protection이 이를 강제하지 않으므로 운영자가 direct push와 force push를 사용하지
  않아야 합니다.
- [350 Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)이
  fresh reconcile과 최초 `Synced`·`Healthy` 확인까지 완료된 후에만 이 반복 배포
  runbook을 시작합니다.

현재 staging의 live 진행 상태와 재개 위치는
[100 Staging single-node bootstrap scenario](scenarios/100-staging-single-node-bootstrap.md)가
소유합니다. 이 runbook은 최초 bootstrap이 완료됐다고 단정하지 않으며, scenario에서
fresh `Synced`·`Healthy`가 확인되지 않았다면 이 절차를 시작하지 않습니다.

기존 image owner를 식별하지 못하거나 overlay가 비어 있으면 중단합니다. 이 runbook이 exact
file·Kustomize field·검증 command를 추측해 새 manifest 계약을 만들지 않습니다.

## 배포 입력

[GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)에서 다음을 모두 확인합니다.

- 게시 source의 exact 40자리 `main` SHA
- ECR Console과 job summary가 함께 확인한 full exact digest reference
- 현재 배포하려는 PR·변경과 위 source revision의 대응 관계

Mutable tag나 `latest`를 배포 입력으로 사용하지 않습니다.

아래 local 명령은 `deploy`가 바로 아래에 보이는 현재 `car-community` repository root에서
실행합니다. `<KEY_PATH>`는 EC2 SSH private key의 절대 경로입니다.

## 배포 실행과 확인

**실행 위치:** 아래 `1`·`2`는 local repository terminal, `3`·`4`는 GitHub Web UI,
`5`의 read-only 확인은 target EC2 terminal

**시작 directory:** Local terminal은 repository root, target EC2 terminal은 제한 없음

1. 현재 `car-community` repository의 `deploy/overlays/stg/kustomization.yaml`을 확인합니다.
   `deploy/overlays/stg` 아래에 `pending.` 또는 `pending.invalid` sentinel이 하나라도 있으면
   actual RDS endpoint·notification sender·frontend URL을 승인된 값으로 바꾸기 전에는
   배포하지 않습니다.

   ```bash
   ! rg -n 'pending\.' deploy/overlays/stg
   ```
2. `images[].digest`만 게시 결과의 full exact ECR digest로 변경하고 local에서 render합니다.

   ```bash
   # Target API dry-run 입력 YAML render
   # 확인: /tmp/car-community-stg.yaml이 생성되고 command가 exit 0
   kubectl kustomize deploy/overlays/stg > /tmp/car-community-stg.yaml

   # Render 결과를 target API에서 server-side dry-run
   # 확인: 모든 resource가 configured (server dry run)이며 오류 없음
   env -i HOME="${HOME}" PATH="${PATH}" \
     ssh -i "<KEY_PATH>" "ubuntu@<EC2_SSH_HOST>" \
     'sudo -n KUBECONFIG=/etc/kubernetes/admin.conf kubectl apply --dry-run=server -f -' \
     < /tmp/car-community-stg.yaml
   ```

   첫 명령의 render 결과만 SSH stdin으로 보내 실제 cluster API schema·admission까지
   확인합니다. Resource는 생성하지 않습니다. 두 명령은 producer와 consumer인 하나의
   recipe이므로 별도 실행 block이나 출력 구분자로 쪼개지 않습니다.
3. 변경된 digest, 대상 `stg` overlay와 예상 rollout을 pull request에서 review합니다.
4. Pull request를 `main`에 merge합니다. Image 게시만으로 merge·배포 승인을 대신하지
   않습니다.
   이 `deploy/**` 전용 merge에도 `CI`와 publish workflow의 경로 분류는 실행될 수 있지만
   AWS OIDC·image build·push job은 skip돼야 합니다.
5. Argo CD가 merge된 목표 상태를 자동 sync한 뒤 EC2에서 read-only 상태를 확인합니다.

```bash
EXPECTED_REVISION='<merge된 40자 main commit SHA>'

# 1. Application 최종 상태 검증
# 확인: merge SHA|Succeeded|Synced|Healthy와 정확히 일치
printf '\n\n========== [1/5] Application 최종 상태 ==========\n\n'
APPLICATION_STATE="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{.status.sync.revision}{"|"}{.status.operationState.phase}{"|"}{.status.sync.status}{"|"}{.status.health.status}')"
test "${APPLICATION_STATE}" = "${EXPECTED_REVISION}|Succeeded|Synced|Healthy"

# 2. Application condition 부재 검증
# 확인: 출력 없음이며 test가 exit 0
printf '\n\n========== [2/5] Application condition 부재 ==========\n\n'
APPLICATION_CONDITIONS="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{range .status.conditions[*]}{.type}{"="}{.message}{"\n"}{end}')"
test -z "${APPLICATION_CONDITIONS}"

# 3. Sync resource와 hook 결과 조회
# 확인: Migration hook 성공 뒤 Deployment Synced
printf '\n\n========== [3/5] Sync resource 결과 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{range .status.operationState.syncResult.resources[*]}{.syncPhase}{"\t"}{.hookPhase}{"\t"}{.kind}{"\t"}{.name}{"\t"}{.status}{"\n"}{end}'

# 4. Runtime Deployment rollout 대기
# 확인: deployment "car-community" successfully rolled out
printf '\n\n========== [4/5] Workload rollout ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl rollout status deployment/car-community -n car-community --timeout=300s

# 5. Pod readiness와 실제 image digest 조회
# 확인: READY=true이고 IMAGE_ID가 목표 digest와 일치
printf '\n\n========== [5/5] Pod와 image digest ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get pods -n car-community \
  -o custom-columns='NAME:.metadata.name,READY:.status.containerStatuses[*].ready,IMAGE_ID:.status.containerStatuses[*].imageID'
```

1. 최종 상태 검증은 merge한 exact revision과 Argo CD 상태를 결속합니다.
2. Condition 검증은 성공 상태에 가려진 reconciliation 오류가 없는지 확인합니다.
3. Sync 결과는 단일 Migration Job과 Deployment 순서, migration hook 성공을 확인합니다.
4. Rollout은 runtime Deployment가 목표 상태로 교체됐는지 확인합니다.
5. Pod 조회는 Ready 상태와 실제 image digest를 확인합니다.

성공한 hook Job은 삭제될 수
있으므로 현재 Application operation 결과를 사용합니다. Target Pod rollout과 imageID도 목표
digest와 일치해야 합니다. UI는 설치하지 않았으므로
상태 확인을 위해 `argocd-server`, port-forward나 ALB를 추가하지 않습니다.

## Runtime DB 권한 실패 진단과 복구

Application에서 `permission denied for table` 또는 `permission denied for sequence`가
발생하면 실패한 application 명령과 sync를 반복하지 않습니다. `Synced`·`Healthy`와 과거
Migration hook `Succeeded`는 현재 runtime role의 effective privilege를 증명하지 않습니다.
아래 read-only 진단으로 처음 어긋난 경계를 확정한 뒤 해당 분기만 복구합니다.

### 1. 배포 artifact와 Application 상태 확인

#### 1.1 EC2 — live revision과 image 확인

**실행 위치:** target EC2 terminal

**시작 directory:** 제한 없음

EC2 terminal에서 현재 revision·operation, runtime image와 실제 imageID를 한 번에 확인합니다.

```bash
# 1. Application revision과 상태 조회
# 확인: target·sync revision이 같고 Succeeded|Synced|Healthy
printf '\n\n========== [1/3] Application revision과 상태 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{.spec.source.targetRevision}{"|"}{.status.sync.revision}{"|"}{.status.operationState.phase}{"|"}{.status.sync.status}{"|"}{.status.health.status}{"\n"}'

# 2. Deployment의 목표 image 조회
# 확인: full ECR digest reference 한 줄
printf '\n\n========== [2/3] Deployment 목표 image ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get deployment/car-community -n car-community \
  -o jsonpath='{.spec.template.spec.containers[?(@.name=="application")].image}{"\n"}'

# 3. Pod readiness와 실제 imageID 조회
# 확인: READY=true이고 IMAGE와 IMAGE_ID digest가 일치
printf '\n\n========== [3/3] Pod 실제 image ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get pods -n car-community \
  -l app.kubernetes.io/name=car-community,app.kubernetes.io/component=api \
  -o custom-columns='NAME:.metadata.name,READY:.status.containerStatuses[0].ready,IMAGE:.spec.containers[0].image,IMAGE_ID:.status.containerStatuses[0].imageID'
```

1. Application 조회는 진단할 exact Git artifact를 고정합니다.
2. Deployment 조회는 원하는 runtime image를 고정합니다.
3. Pod 조회는 원하는 image와 실제 실행 image의 일치 여부를 판정합니다.

Revision이 40자리 Git SHA이고 Application이 `Succeeded|Synced|Healthy`여도 다음 절을 계속
실행합니다. Deployment image와 Pod `IMAGE_ID`가 서로 다른 digest면 DB 권한 문제가 아니라
rollout 불일치이므로 [170 Kubernetes workload 진단](diagnostics/170-kubernetes-workload-network-events-and-logs.md)으로
이동합니다.

#### 1.2 Local — exact revision의 Migration Job render 검증

**실행 위치:** local repository terminal

**시작 directory:** repository root

첫 출력의 revision과 두 번째 출력의 full image reference를 local repository terminal로
옮겨, 그 exact revision이 만든 Migration Job을 render합니다. 현재 checkout이나 worktree를
바꾸지 않으며 임시 directory는 subshell 종료 때 삭제합니다.

```bash
(
set -o errexit -o nounset -o pipefail
TARGET_REVISION='<첫 출력의 40자리 .status.sync.revision>'
RUNTIME_IMAGE='<두 번째 출력의 full image reference>'
[[ "${TARGET_REVISION}" =~ ^[0-9a-f]{40}$ ]]
[[ "${RUNTIME_IMAGE}" =~ @sha256:[0-9a-f]{64}$ ]]

for command_name in git tar kubectl awk grep; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    printf 'DIAGNOSTIC_INPUT_FAILED missing=%s\n' "${command_name}" >&2
    exit 2
  }
done

RENDER_CHECK_DIR="$(mktemp -d)"
trap 'rm -rf -- "${RENDER_CHECK_DIR}"' EXIT
git cat-file -e "${TARGET_REVISION}^{commit}" 2>/dev/null || {
  printf 'REVISION_OBJECT_MISSING revision=%s\n' "${TARGET_REVISION}" >&2
  exit 3
}
if ! git archive "${TARGET_REVISION}" deploy \
    | tar -x -C "${RENDER_CHECK_DIR}"; then
  printf 'DIAGNOSTIC_INPUT_FAILED step=archive\n' >&2
  exit 2
fi
if ! kubectl kustomize "${RENDER_CHECK_DIR}/deploy/overlays/stg" \
    | awk 'BEGIN { RS="---" } /kind: Job/ && /name: car-community-db-migration/ { print }' \
    >"${RENDER_CHECK_DIR}/migration-job.yaml"; then
  printf 'DIAGNOSTIC_INPUT_FAILED step=render\n' >&2
  exit 2
fi
if [[ ! -s "${RENDER_CHECK_DIR}/migration-job.yaml" ]]; then
  printf 'MIGRATION_JOB_WIRING_MISMATCH reason=job_missing\n' >&2
  exit 4
fi

if [[ "$(grep -Fc "image: ${RUNTIME_IMAGE}" "${RENDER_CHECK_DIR}/migration-job.yaml")" -ne 1 \
    || "$(grep -Fc -- '- --spring.flyway.callback-locations=classpath:db/callback' "${RENDER_CHECK_DIR}/migration-job.yaml")" -ne 1 \
    || "$(grep -Fc 'name: car-community-config' "${RENDER_CHECK_DIR}/migration-job.yaml")" -ne 1 \
    || "$(grep -Fc 'key: DB_URL' "${RENDER_CHECK_DIR}/migration-job.yaml")" -ne 1 \
    || "$(grep -Fc 'name: car-community-db-migrator' "${RENDER_CHECK_DIR}/migration-job.yaml")" -ne 2 \
    || "$(grep -Fc 'key: DB_MIGRATOR_USERNAME' "${RENDER_CHECK_DIR}/migration-job.yaml")" -ne 1 \
    || "$(grep -Fc 'key: DB_MIGRATOR_PASSWORD' "${RENDER_CHECK_DIR}/migration-job.yaml")" -ne 1 ]]; then
  printf 'MIGRATION_JOB_WIRING_MISMATCH reason=contract\n' >&2
  exit 4
fi
printf 'MIGRATION_JOB_WIRING=PASS\n'
)
```

- `REVISION_OBJECT_MISSING`: 올바른 repository인지 확인하고 remote에서 해당 revision을
  read-only로 fetch한 뒤 이 단계만 재개합니다.
- `DIAGNOSTIC_INPUT_FAILED`: 표시된 command·archive·render 입력 문제를 해결한 뒤 이 단계만
  재개합니다. Manifest 결함으로 판정하지 않습니다.
- `MIGRATION_JOB_WIRING_MISMATCH`: Render는 성공했지만 과거 sync revision의 desired Job이
  runtime image·callback·DB reference 계약과 달랐습니다. 같은 revision을 재실행하지 않고
  manifest·image source를 수정한 새 revision을 정상 digest PR로 배포합니다.
- `MIGRATION_JOB_WIRING=PASS`: 아래 callback entry와 live wiring을 계속 확인합니다.

#### 1.3 EC2 — runtime image callback entry 확인

**실행 위치:** target EC2 terminal

**시작 directory:** 제한 없음

실행 중인 동일 image의 JAR에 callback resource entry가 실제로 포함됐는지 확인합니다. 이
명령은 JAR을 실행하거나 DB에 접속하지 않고 ZIP central directory의 entry 이름만 찾습니다.

```bash
# Runtime image의 Flyway callback entry 조회
# 확인: CALLBACK_PRESENT
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl exec deployment/car-community -n car-community --container application -- \
  grep -aFq 'BOOT-INF/classes/db/callback/afterMigrate__grant_runtime_privileges.sql' \
  /app/application.jar \
  && printf 'CALLBACK_PRESENT\n'
```

`CALLBACK_PRESENT`가 나오지 않으면 현재 image에는 권한 callback이 없습니다. 이 경우 같은 digest를
sync하지 않고 callback source를 포함한 `main` image를 [400 GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)로
게시한 뒤 이 Runbook의 일반 digest 변경 PR 절차로 배포합니다.

### 2. Runtime과 Migration DB wiring 확인

**실행 위치:** EC2 terminal

**시작 directory:** 제한 없음

다음 명령은 password를 출력하지 않고 live ConfigMap과 Secret의 DB URL·username만 비교합니다.

```bash
RUNTIME_DB_URL="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get configmap/car-community-config -n car-community \
  -o jsonpath='{.data.DB_URL}')"
RUNTIME_DB_USERNAME="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get secret/car-community-db-runtime -n car-community \
  -o jsonpath='{.data.DB_USERNAME}' | base64 --decode)"
MIGRATOR_DB_USERNAME="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get secret/car-community-db-migrator -n car-community \
  -o jsonpath='{.data.DB_MIGRATOR_USERNAME}' | base64 --decode)"

printf 'DB_URL=%s\nRUNTIME_USERNAME=%s\nMIGRATOR_USERNAME=%s\n' \
  "${RUNTIME_DB_URL}" "${RUNTIME_DB_USERNAME}" "${MIGRATOR_DB_USERNAME}"
test "${RUNTIME_DB_USERNAME}" = 'carmunity_runtime'
test "${MIGRATOR_DB_USERNAME}" = 'carmunity_migrator'
unset RUNTIME_DB_URL RUNTIME_DB_USERNAME MIGRATOR_DB_USERNAME
```

`DB_URL`은 `carmunity` database와 `currentSchema=public`을 가리켜야 합니다. Username이나 URL이
repository 계약과 다르면 sync하지 않습니다. 승인된 실제 값으로
[350 Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)의
Application prerequisite owner에서 Secret·ConfigMap source를 바로잡고 rollout을 확인합니다.

### 3. Runtime effective privilege 확인

**실행 위치:** EC2 terminal

**시작 directory:** 제한 없음

EC2에 이미 설치된 `psql`과 live runtime Secret credential을 사용해 실제 접속 role·database·
schema와 `auth_role`의 `SELECT` 권한을 확인합니다. Subshell 종료 시 password와 임시 CA file을
삭제하며 SSH session이나 parent shell option은 바꾸지 않습니다. `psql`이 없으면 설치하지
말고 `350`의 Application DB role prerequisite가 완료됐는지 먼저 확인합니다.

```bash
(
set +x
set -o errexit -o nounset -o pipefail
umask 077
command -v psql >/dev/null

DB_CHECK_DIR="$(mktemp -d)"
trap 'unset RUNTIME_DB_PASSWORD DB_URL PSQL_URL; rm -rf -- "${DB_CHECK_DIR}"' EXIT

sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get configmap/car-community-rds-ca -n car-community \
  -o jsonpath='{.data.ap-northeast-2-bundle\.pem}' \
  >"${DB_CHECK_DIR}/ap-northeast-2-bundle.pem"
DB_URL="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get configmap/car-community-config -n car-community \
  -o jsonpath='{.data.DB_URL}')"
RUNTIME_DB_PASSWORD="$(sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get secret/car-community-db-runtime -n car-community \
  -o jsonpath='{.data.DB_PASSWORD}' | base64 --decode)"

PSQL_URL="${DB_URL#jdbc:}"
PSQL_URL="${PSQL_URL/currentSchema=public&/}"
PSQL_URL="${PSQL_URL/&sslrootcert=\/etc\/car-community\/rds-ca\/ap-northeast-2-bundle.pem/}"

PGPASSWORD="${RUNTIME_DB_PASSWORD}" \
PGSSLROOTCERT="${DB_CHECK_DIR}/ap-northeast-2-bundle.pem" \
  psql "${PSQL_URL}" --username=carmunity_runtime --no-psqlrc \
  --set=ON_ERROR_STOP=on --tuples-only --no-align \
  --command="SELECT current_user, current_database(), current_schema(), has_table_privilege(current_user, 'public.auth_role', 'SELECT');"
)
```

정상 출력은 `carmunity_runtime|carmunity|public|t`입니다. 접속 자체가 실패하면 runtime Secret
credential·RDS TLS·network 문제이므로 callback을 재실행하지 않습니다. 마지막 값만 `f`이면
callback의 권한 결과가 실제 DB에 없다는 사실이 확정됩니다.

### 4. Migration 실행 증거와 원인 분기

**실행 위치:** EC2 terminal

**시작 directory:** 제한 없음

현재 Application operation에 남은 Migration hook 결과와, 실패 Job이 남아 있으면 그 log를
확인합니다.

```bash
# 1. Application에 보존된 Migration hook 결과 조회
# 확인: car-community-db-migration의 phase·status·message
printf '\n\n========== [1/3] Migration hook 결과 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get application/car-community-stg -n argocd \
  -o jsonpath='{range .status.operationState.syncResult.resources[*]}{.syncPhase}{"\t"}{.hookPhase}{"\t"}{.kind}{"\t"}{.name}{"\t"}{.status}{"\t"}{.message}{"\n"}{end}'

# 2. 실패 Migration Job 잔존 여부 조회
# 확인: Job 정보 또는 없으면 빈 출력
printf '\n\n========== [2/3] Migration Job 잔존 상태 ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl get job/car-community-db-migration -n car-community --ignore-not-found

# 3. 잔존 Migration Job log 조회
# 확인: 실패 원인을 나타내는 log 또는 Job이 없으면 빈 출력
printf '\n\n========== [3/3] Migration Job log ==========\n\n'
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl logs -n car-community job/car-community-db-migration \
  --all-containers=true --ignore-errors=true
```

1. Application 결과는 삭제될 수 있는 hook의 실행 결과를 보존합니다.
2. Job 조회는 추가 진단 가능한 실패 workload가 남았는지 확인합니다.
3. Log 조회는 남은 Job의 구체적인 migration·callback·RDS 실패를 찾습니다.

다음처럼 원인과 복구를 결속합니다.

| 확인 결과 | 원인 경계 | 복구 |
| --- | --- | --- |
| 1절 exact revision Job wiring 실패 | 과거 sync의 desired Migration Job이 runtime image·callback·DB reference 계약과 다름 | Manifest·image source를 수정한 새 revision을 digest PR로 배포 |
| 1절 callback entry 없음 | 게시 image가 callback source를 포함하지 않음 | 새 source image 게시 후 digest PR 배포 |
| 2절 URL·username 불일치 | Runtime 또는 migrator wiring이 repository 계약과 다름 | `350` prerequisite owner에서 승인된 값으로 수정 |
| 3절 접속 실패 | Runtime credential·TLS·network 경계 실패 | 출력된 접속 오류를 해당 owner에서 수정 |
| 실패 Migration Job과 log 존재 | Log가 가리킨 migration·callback·RDS 원인 | 원인을 source에서 수정한 뒤 새 revision을 정상 배포 |
| Callback entry·wiring 정상, effective privilege `f`, 과거 성공 Job log 없음 | 과거 hook이 callback을 실행하지 않은 원인은 현재 증거만으로 미확정 | 같은 revision 재실행을 복구로 간주하지 말고, callback discovery·실행을 관찰할 진단 재현 계획을 먼저 승인받음 |

어느 분기에서도 DB master 직접 `GRANT`, `flyway repair`, selective sync, live Job manifest 적용으로
원인을 덮지 않습니다. 원인 수정과 정상 배포 뒤 3절 결과가
`carmunity_runtime|carmunity|public|t`가 되어야 권한 복구가 완료됩니다. 그 후 최초 구축이면
`350`의 6절을, Super Admin 생성에서 발견했다면 `360`의 1절을 재개합니다.

## 배포 중단과 복구

**실행 위치:** Repository source 수정은 local repository terminal, PR·merge·workflow 확인은
GitHub Web UI·Actions, live Application·workload 진단은 target EC2 terminal

**시작 directory:** Local terminal은 repository root, target EC2 terminal은 제한 없음

| 상태 | 대응 |
| --- | --- |
| Overlay 또는 image reference owner를 식별할 수 없음 | PR을 만들지 않고 현재 repository의 `deploy/` manifest owner·구조를 먼저 확정합니다. |
| PR 검증·review 실패 | Merge하지 않습니다. 현재 repository의 `deploy/` manifest나 digest 입력을 수정합니다. |
| PreSync Migration Job 실패 또는 runtime table·sequence 권한 거부 | Selective sync나 `flyway repair`로 우회하지 않습니다. [Runtime DB 권한 실패 진단과 복구](#runtime-db-권한-실패-진단과-복구)에서 artifact·wiring·effective privilege·Job evidence 순서로 원인을 확정한 뒤 해당 분기만 복구합니다. |
| Merge 뒤 Application이 `Synced`가 아님 | Live Application spec을 수정하지 않습니다. [180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md)으로 condition, repository revision/path와 controller log를 확인하고 Git 목표 상태 또는 credential 원인을 수정합니다. |
| Application이 `Healthy`가 아니거나 Pod 실패 | Live Deployment image를 수정하지 않습니다. [170 Kubernetes workload 진단](diagnostics/170-kubernetes-workload-network-events-and-logs.md)으로 event·image pull·readiness를 확인하고 forward-fix PR 또는 아래 rollback PR을 선택합니다. Runtime 자체가 이상하면 [140 Linux node service 진단](diagnostics/140-systemd-journal-and-node-services.md)을 사용합니다. |
| 잘못된 빈 목표 상태 | Automated sync나 `allowEmpty: false`를 끄지 않습니다. 현재 repository overlay를 복구하는 PR을 우선합니다. |

## Rollback

**실행 위치:** `1`은 local repository terminal, `2`·`3`은 GitHub Web UI, `4`는 target
EC2 terminal

**시작 directory:** Local terminal은 repository root, target EC2 terminal은 제한 없음

1. 마지막 정상 배포의 exact digest commit과 현재 문제 digest commit을 `car-community`
   이력에서 확인합니다.
2. 문제 digest 변경 commit을 되돌리는 revert pull request를 만듭니다.
3. Revert 내용이 이전 exact digest를 복원하는지 review하고 merge합니다.
4. 위 read-only 명령으로 Argo CD가 되돌린 목표 상태를 `Synced`·`Healthy`로 보고하고 Pod가
   정상 상태인지 다시 확인합니다.

Argo CD Application, live Deployment image 또는 Pod를 직접 수정하지 않습니다. Git revert가
배포 이력과 목표 상태의 canonical 복구 경로입니다. Revert merge 뒤에도 실패하면 추가 live
mutation을 하지 말고 Application condition·Pod event·image pull과 이전 digest의 실제 ECR
존재 여부를 조사합니다. Database migration은 Git revert로 되돌아가지 않으므로 이전 image가
현재 schema와 호환되는지도 rollback 전에 확인합니다.

## 완료 판정

- 배포 또는 rollback의 exact digest 변경이 review된 PR로 `main`에 merge됐습니다.
- `car-community-stg` Application이 merge된 목표 상태를 `Synced`·`Healthy`로 보고합니다.
- 단일 Migration PreSync와 runtime Deployment 순서가 성공했습니다.
- Target namespace Pod를 read-only로 확인했습니다.
- Live Deployment·Application 직접 변경이나 credential 우회를 만들지 않았습니다.

이 절차는 현재 repository의 `deploy/` manifest와 live cluster가 실제 존재할 때만 완료할 수
있습니다. 문서·script 정적 검증만으로 GitOps 배포 성공을 주장하지 않습니다.

## 관련 문서

- **현재 반복 전달 개념**: [220 GitOps application delivery](../project/infrastructure/220-gitops-application-delivery.md)
- **Image 게시와 digest 확보**: [GitHub ECR image 게시](400-github-ecr-image-publishing-runbook.md)
- **최초 application 구축**: [Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)
- **Credential 교체**: [Argo CD repository credential 교체](520-argocd-repository-credential-rotation-runbook.md)
- **Argo 진단**: [180 Argo CD reconciliation 진단](diagnostics/180-argocd-reconciliation.md)
- **Workload network 진단**: [170 Kubernetes workload 진단](diagnostics/170-kubernetes-workload-network-events-and-logs.md)
