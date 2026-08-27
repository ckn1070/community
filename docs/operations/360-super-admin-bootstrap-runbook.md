# Super Admin 최초 bootstrap runbook

배포된 staging Application에서 최초 `ROLE_SUPER_ADMIN` 한 명을 생성하고 초기 비밀번호
변경까지 완료하는 선택 절차입니다. Repository script를 추가하지 않고 기존 Application
command와 인증 API를 사람이 순서대로 실행합니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | `car-community-stg`가 `Synced`·`Healthy`이고 Deployment rollout과 Pod Ready 확인이 끝난 뒤, 운영상 최초 최고 관리자가 필요할 때 한 번 실행합니다. |
| 누가 | `/etc/kubernetes/admin.conf`를 사용할 수 있는 운영자입니다. |
| 기본 실행 위치 | EC2 SSH Bash session 두 개입니다. |
| 기본 시작 directory | 제한 없음 |
| 입력 | Super Admin email·display name, 초기 password, 새 password입니다. 두 password는 각각 10~128자이며 전체가 공백일 수 없습니다. Composition rule은 없습니다. Password와 token은 문서·Git·명령 인자에 기록하지 않습니다. |
| 변경 | 활성 Super Admin이 없으면 계정·profile·credential·role assignment를 만들고 `password_change_required=true`로 저장한 뒤, 인증 API로 새 password를 저장합니다. |
| 완료 | Command 결과, 초기 로그인 `201`, password 변경 `204`, 새 password 로그인 `201`과 `ROLE_SUPER_ADMIN`을 확인합니다. |
| 재개 | `CREATED` 뒤 중단됐으면 계정 생성 command를 반복하지 않고 초기 로그인부터 재개합니다. `ALREADY_BOOTSTRAPPED`인데 현재 credential을 모르면 새 계정이나 DB 직접 수정을 시도하지 않습니다. |

각 section의 `실행 위치` 선언이 두 session 중 사용할 session을 구분합니다.

## 1. 최초 Super Admin 생성

**실행 위치:** 첫 번째 EC2 SSH session

```bash
sudo KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl exec --stdin --tty deployment/car-community \
  -n car-community --container application -- \
  java -jar /app/application.jar \
  --spring.main.web-application-type=none \
  --spring.main.lazy-initialization=true \
  --spring.flyway.enabled=false \
  --command=bootstrap-super-admin
```

Prompt에 email, display name, 초기 password와 confirmation을 입력합니다. 초기 password는
10~128자이고 전체가 공백이 아니어야 합니다.

- `CREATED`: 생성됐으며 초기 password 변경이 필요합니다.
- `ALREADY_BOOTSTRAPPED`: 활성 Super Admin을 유지합니다. 현재 credential을 알 때만 2절로
  진행합니다.
- `INVALID_INPUT`, `ROLE_NOT_CONFIGURED`, `CONFLICT`, `FAILED`: 출력한 원인을 해결하기 전에는
  반복 실행하지 않습니다.

## 2. API port-forward

**실행 위치:** 첫 번째 EC2 SSH session

첫 번째 session에서 다음 command를 실행한 채 유지합니다. `18080`을 이미 사용 중이면 해당
process를 종료하지 말고 빈 local port를 선택해 이후 URL에도 같은 값을 사용합니다.

```bash
sudo env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl -n car-community port-forward \
  service/car-community 18080:80 \
  --address=127.0.0.1
```

`Forwarding from 127.0.0.1:18080`을 확인합니다.

## 3. 초기 로그인과 password 변경

**실행 위치:** 두 번째 EC2 SSH session

`jq`와 `curl`이 필요합니다.

```bash
(
set +x
set -o errexit -o nounset -o pipefail
umask 077

for command_name in curl jq; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    printf 'Required command is missing: %s\n' "${command_name}" >&2
    exit 1
  }
done

SUPER_ADMIN_WORK_DIR="$(mktemp -d)"
trap 'unset INITIAL_PASSWORD NEW_PASSWORD NEW_PASSWORD_CONFIRMATION ACCESS_TOKEN; rm -rf -- "${SUPER_ADMIN_WORK_DIR}"' EXIT

printf 'Super Admin email: ' >&2
IFS= read -r SUPER_ADMIN_EMAIL
printf 'Initial password: ' >&2
IFS= read -rs INITIAL_PASSWORD
printf '\nNew password: ' >&2
IFS= read -rs NEW_PASSWORD
printf '\nNew password confirmation: ' >&2
IFS= read -rs NEW_PASSWORD_CONFIRMATION
printf '\n' >&2
[[ "${NEW_PASSWORD}" == "${NEW_PASSWORD_CONFIRMATION}" ]]

printf '%s\n%s\n' "${SUPER_ADMIN_EMAIL}" "${INITIAL_PASSWORD}" |
  jq -Rn '{email: input, password: input}' >"${SUPER_ADMIN_WORK_DIR}/login.json"

LOGIN_STATUS="$(curl --silent --show-error \
  --output "${SUPER_ADMIN_WORK_DIR}/login-response.json" \
  --write-out '%{http_code}' \
  --header 'Content-Type: application/json' \
  --data-binary "@${SUPER_ADMIN_WORK_DIR}/login.json" \
  http://127.0.0.1:18080/api/v1/auth/sessions)"
if [[ "${LOGIN_STATUS}" != "201" ]]; then
  jq . "${SUPER_ADMIN_WORK_DIR}/login-response.json" >&2
  exit 1
fi
printf 'Initial login HTTP %s\n' "${LOGIN_STATUS}"

ACCESS_TOKEN="$(jq -er '.accessToken' "${SUPER_ADMIN_WORK_DIR}/login-response.json")"
printf 'Authorization: Bearer %s\n' "${ACCESS_TOKEN}" >"${SUPER_ADMIN_WORK_DIR}/authorization.header"

printf '%s\n%s\n%s\n' "${INITIAL_PASSWORD}" "${NEW_PASSWORD}" "${NEW_PASSWORD_CONFIRMATION}" |
  jq -Rn '{currentPassword: input, newPassword: input, newPasswordConfirmation: input}' \
  >"${SUPER_ADMIN_WORK_DIR}/password.json"

set +o errexit
PASSWORD_STATUS="$(curl --silent --show-error \
  --output "${SUPER_ADMIN_WORK_DIR}/password-response.json" \
  --write-out '%{http_code}' \
  --header "@${SUPER_ADMIN_WORK_DIR}/authorization.header" \
  --header 'Content-Type: application/json' \
  --request PUT \
  --data-binary "@${SUPER_ADMIN_WORK_DIR}/password.json" \
  http://127.0.0.1:18080/api/v1/auth/password)"
PASSWORD_CURL_EXIT=$?
set -o errexit
if (( PASSWORD_CURL_EXIT != 0 )); then
  printf 'Password change result is unknown; continue with section 4 first.\n' >&2
  exit 2
fi
if [[ "${PASSWORD_STATUS}" != "204" ]]; then
  jq . "${SUPER_ADMIN_WORK_DIR}/password-response.json" >&2
  printf 'Password change was not confirmed; continue with section 4 first.\n' >&2
  exit 1
fi
printf 'Password change HTTP %s\n' "${PASSWORD_STATUS}"

unset INITIAL_PASSWORD NEW_PASSWORD NEW_PASSWORD_CONFIRMATION ACCESS_TOKEN
rm -rf -- "${SUPER_ADMIN_WORK_DIR}"
unset SUPER_ADMIN_WORK_DIR
trap - EXIT
)
```

전체 block은 subshell 안에서 실행하므로 실패해도 SSH session과 parent shell option을
종료·변경하지 않습니다. 입력은 shell history에 password와 token을 남기지 않고 mode
`0600` 임시 파일로만 전달합니다.

첫 HTTP status가 `201`, password 변경 status가 `204`여야 합니다. 초기 로그인 실패는 출력된
API 오류를 확인한 뒤 이 절에서 재개합니다. Password 변경에서 `204`를 관찰하지 못하면 서버
commit 여부를 추측하지 말고 먼저 독립된 4절로 새 password 로그인을 시도합니다. 새 password
로그인이 성공하면 변경은 완료된 것이고, credential 오류로 실패할 때만 초기 password가 아직
유효한지 확인해 이 절의 초기 로그인과 `PUT`을 재개합니다.

## 4. 새 password 로그인 확인

**실행 위치:** 두 번째 EC2 SSH session

```bash
(
set +x
set -o errexit -o nounset -o pipefail
umask 077

for command_name in curl jq; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    printf 'Required command is missing: %s\n' "${command_name}" >&2
    exit 1
  }
done

SUPER_ADMIN_VERIFY_DIR="$(mktemp -d)"
trap 'unset NEW_PASSWORD; rm -rf -- "${SUPER_ADMIN_VERIFY_DIR}"' EXIT

printf 'Super Admin email: ' >&2
IFS= read -r SUPER_ADMIN_EMAIL
printf 'New password: ' >&2
IFS= read -rs NEW_PASSWORD
printf '\n' >&2

printf '%s\n%s\n' "${SUPER_ADMIN_EMAIL}" "${NEW_PASSWORD}" |
  jq -Rn '{email: input, password: input}' >"${SUPER_ADMIN_VERIFY_DIR}/final-login.json"

FINAL_LOGIN_STATUS="$(curl --silent --show-error \
  --output "${SUPER_ADMIN_VERIFY_DIR}/final-login-response.json" \
  --write-out '%{http_code}' \
  --header 'Content-Type: application/json' \
  --data-binary "@${SUPER_ADMIN_VERIFY_DIR}/final-login.json" \
  http://127.0.0.1:18080/api/v1/auth/sessions)"
if [[ "${FINAL_LOGIN_STATUS}" != "201" ]]; then
  jq . "${SUPER_ADMIN_VERIFY_DIR}/final-login-response.json" >&2
  exit 1
fi
printf 'Final login HTTP %s\n' "${FINAL_LOGIN_STATUS}"

jq -e '.sessionAccess == "FULL" and (.roles | index("ROLE_SUPER_ADMIN") != null)' \
  "${SUPER_ADMIN_VERIFY_DIR}/final-login-response.json" >/dev/null
)
printf 'VERIFY_EXIT=%s\n' "$?"
```

`Final login HTTP 201`과 `VERIFY_EXIT=0`을 모두 확인합니다. Subshell 종료 시 trap이 민감 파일을
삭제하며 두 번째 SSH session은 유지됩니다. `printf`는 subshell 바로 뒤에서 `$?`를
소비하므로 그 사이에 다른 command를 실행하지 않습니다. 3절과 같은 session을 재사용할 수
있으며, 이 절은 3절의 변수나 임시 파일에 의존하지 않으므로 password 변경 `204` 뒤 연결이
끊겨도 새 session에서 재개할 수 있습니다. 마지막으로 첫 번째 session의 port-forward를
`Ctrl-C`로 종료합니다.

## 중단과 복구

| 상태 | 재개점 |
| --- | --- |
| Command가 `CREATED`를 출력한 뒤 SSH 중단 | 2절 port-forward와 3절 초기 로그인 |
| Command가 `ALREADY_BOOTSTRAPPED`이고 credential을 앎 | 2절 port-forward와 3절 초기 로그인 |
| Command가 runtime table·sequence `permission denied`로 `FAILED` | Command와 sync를 반복하지 않고 [420 Runtime DB 권한 실패 진단](420-gitops-deployment-and-rollback-runbook.md#runtime-db-권한-실패-진단과-복구)에서 원인을 확정·수정하고 effective privilege를 확인한 뒤 1절 |
| 초기 로그인 실패 | Email·초기 password를 확인한 뒤 3절 |
| Password 변경 `204` 뒤 연결 중단 | 4절 새 password 로그인 |
| Password 변경의 HTTP 결과를 관찰하지 못함 | 먼저 4절 새 password 로그인. 성공하면 완료, credential 오류면 3절 초기 로그인과 `PUT` |
| Password 변경이 `204`가 아닌 HTTP 응답 | Problem Details를 확인한 뒤 먼저 4절 새 password 로그인. credential 오류면 원인을 수정하고 3절 |
| 기존 Super Admin credential을 모름 | 이 절차를 중단하고 별도 credential recovery 결정을 요청 |

## 관련 문서

- **전체 구축의 현재 위치**: [100 Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)
- **Application delivery 전제**: [350 Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)
- **계정·인증 정책**: [Account 인증/인가 정책](../domain/110-account-auth-policy.md)
- **기능 설계**: [010 관리자 계정 기반 설계](../work-items/features/2026/010-admin-account-foundation-design.md)
