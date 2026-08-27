# Application 기능 qualification runbook

Staging application의 회원가입부터 이메일 인증 완료까지 한 흐름으로 확인하고, 검증에
사용한 account와 직접 연결된 test data를 transaction으로 정리하는 선택 절차입니다.
`POST /api/v1/members`가 application Pod의 runtime DB role로 RDS에 쓰는지, Resend가 실제
메일을 전달하는지, 전달된 token이 소비되어 account가 `ACTIVE`로 바뀌는지를 최소 범위로
확인합니다.

이 절차는 staging 전용입니다. 실행하지 않아도
[350 Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)의
기술 완료 상태는 바뀌지 않으며, 실행하지 않은 경우 기능 경로는 `미검증`으로 남습니다.
Production account, 실제 사용자 account와 최초 `ROLE_SUPER_ADMIN` account에는 이 절차와
cleanup SQL을 사용하지 않습니다.

## 실행 계약

| 항목 | 계약 |
| --- | --- |
| 언제 | `car-community-stg`가 `Synced`·`Healthy`, Deployment rollout과 exact image 확인까지 끝난 뒤 선택적으로 실행합니다. |
| 누가 | 실제 메일을 확인할 수 있고 target EC2 SSH·`sudo` 권한이 있는 운영자가 실행합니다. |
| 기본 실행 위치 | SSH로 접속한 target EC2 terminal입니다. |
| 기본 시작 directory | 제한 없음 |
| 세션별 역할 | 첫 session은 port-forward를 유지하고, 두 번째 session은 qualification 전용 child Bash에서 HTTP·RDS 확인과 cleanup을 실행합니다. |
| 입력 | 실제 수신 가능한 staging 전용 email, 회원가입 응답의 `memberId`, 실제 메일 URL의 raw token입니다. |
| 변경 | 임시 일반 회원 한 명과 이메일 인증 관련 row를 생성한 뒤, 검증에 사용한 exact account와 직접 연결된 row만 삭제합니다. |
| 완료 | `201`·`202`·실제 메일 수신·`204`·DB `ACTIVE`를 확인하고 cleanup transaction과 사후 0건 확인을 통과한 뒤 port-forward와 shell 민감값을 제거합니다. |
| 재개 | HTTP 흐름이 중단돼도 생성된 account를 추측해 재사용하지 않습니다. Exact email과 API `memberId`를 복원해 cleanup한 뒤 새 고유 입력으로 처음부터 다시 실행합니다. |

회원가입과 이메일 인증은 사람의 수신함 확인이 필요한 일시적 점검이므로 별도 repository
script를 만들지 않습니다. Cleanup도 현재 사용 빈도에서는 이 Runbook의 versioned SQL 한
곳이 더 단순합니다. 반복 빈도나 실수 위험이 실제로 커질 때만 같은 계약을 script로
승격합니다.

## 선행 조건

EC2에서 다음 기술 상태가 이미 확인돼야 합니다.

- Argo CD Application `car-community-stg`: `Succeeded`·`Synced`·`Healthy`
- `deployment/car-community`: rollout 성공, Pod Ready
- Runtime Pod imageID: 승인한 private ECR exact digest
- `car-community-db-runtime` Secret과 `car-community-rds-ca` ConfigMap 존재
- EC2에 PostgreSQL 16 client 설치 완료

기술 상태가 불일치하면 이 절차로 우회하지 않고
[350 Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md#6-최초-full-sync와-workload-기술-검증)에서
먼저 복구합니다.

## 1. API port-forward

**실행 위치:** 첫 번째 EC2 SSH Bash session

Qualification이 끝날 때까지 session을 유지합니다.

```bash
sudo env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl -n car-community port-forward \
  service/car-community 18080:80 \
  --address=127.0.0.1
```

외부 ALB가 아직 없으므로 ClusterIP Service를 EC2 loopback에만 연결합니다.

`Forwarding from 127.0.0.1:18080`을 확인합니다. 다른 process가 `18080`을 사용하면 임의로
종료하지 말고 사용 원인을 확인한 뒤 빈 local port를 골라 이 문서의 `18080`을 같은 값으로
바꿉니다.

## 2. Test member 입력 준비

**실행 위치:** 두 번째 EC2 SSH Bash session

Qualification 전용 child Bash를 먼저 시작합니다.

```bash
bash --noprofile --norc
```

이제부터 9절의 `exit`까지 같은 child Bash에서 실행합니다. 2절에서 만든
변수·임시 파일은 후속 절에서 계속 사용하지만, shell option·trap은 parent SSH
shell을 바꾸지 않습니다. 명령이나 확인이 실패하면 다음 block을 실행하지 않고,
현재 child Bash의 입력값과 임시 응답을 유지한 채 아래 중단·재개 경계에서 원인을
확인하고 cleanup합니다. Child Bash를 중간에 `exit`하면 `EXIT` trap이 민감한 shell
변수와 임시 directory를 정리하고 parent SSH shell로 돌아갑니다.

Email은 실제로 확인할 수 있는 staging 전용 주소를 소문자 ASCII로 입력합니다.
`+alias` 지원 여부는 provider 사실을 확인하지 않고 가정하지 않습니다.

```bash
set +x
set -o nounset -o pipefail

printf 'Staging qualification email: ' >&2
IFS= read -r QUALIFICATION_EMAIL
if [[ "${QUALIFICATION_EMAIL}" =~ ^[a-z0-9._%+-]+@[a-z0-9.-]+$ ]]; then
  printf 'QUALIFICATION_EMAIL_FORMAT=PASS\n'
else
  printf 'QUALIFICATION_EMAIL_FORMAT=FAIL\n' >&2
fi

QUALIFICATION_DISPLAY_NAME="stg-q-$(date -u +%Y%m%d%H%M%S)-$$"
QUALIFICATION_PASSWORD="$(tr -d '-' </proc/sys/kernel/random/uuid)"
QUALIFICATION_WORK_DIR="$(mktemp -d)"
chmod 0700 "${QUALIFICATION_WORK_DIR}"

cleanup_qualification_shell() {
  unset \
    VERIFICATION_TOKEN QUALIFICATION_PASSWORD STG_DB_RUNTIME_PASSWORD \
    QUALIFICATION_EMAIL QUALIFICATION_MEMBER_ID QUALIFICATION_DISPLAY_NAME \
    RUNTIME_DB_USERNAME QUALIFICATION_DB_URL QUALIFICATION_EXPECT_ACTIVE
  if [[ -n "${QUALIFICATION_WORK_DIR:-}" && -d "${QUALIFICATION_WORK_DIR}" ]]; then
    rm -rf -- "${QUALIFICATION_WORK_DIR}"
  fi
  unset QUALIFICATION_WORK_DIR
}
trap cleanup_qualification_shell EXIT
```

Password는 10~128자 정책을 만족하는 임시값이며 stdout, Git과 shell history에 기록하지
않습니다. 이 shell을 잃으면 password를 복원하려 하지 않고 아래 cleanup 경계에서 test
account를 정리합니다.

## 3. 회원가입

**실행 위치:** 2절에서 시작한 두 번째 EC2 child Bash session

같은 두 번째 session에서 요청 body를 stdin으로 전달합니다.

```bash
registration_status="$({
  printf '{"email":"%s","password":"%s","displayName":"%s"}' \
    "${QUALIFICATION_EMAIL}" \
    "${QUALIFICATION_PASSWORD}" \
    "${QUALIFICATION_DISPLAY_NAME}"
} | curl \
  --silent \
  --show-error \
  --output "${QUALIFICATION_WORK_DIR}/registration.json" \
  --dump-header "${QUALIFICATION_WORK_DIR}/registration.headers" \
  --write-out '%{http_code}' \
  --header 'Content-Type: application/json' \
  --data-binary @- \
  http://127.0.0.1:18080/api/v1/members)"

printf 'REGISTRATION_STATUS=%s\n' "${registration_status}"
test "${registration_status}" = '201'
grep -i '^Location: /api/v1/members/' \
  "${QUALIFICATION_WORK_DIR}/registration.headers"
sed -n '1,20p' "${QUALIFICATION_WORK_DIR}/registration.json"
```

응답에서 다음을 확인합니다.

- HTTP status `201`
- `Location: /api/v1/members/<memberId>`
- Body의 `email`이 입력한 normalized email과 같음
- Body의 `displayName`이 생성한 값과 같음
- Body의 `status`가 `PENDING`

Body의 `memberId`를 화면에 표시된 그대로 입력합니다. DB 내부 `id`를 입력하지 않습니다.

```bash
printf 'Registration response memberId: ' >&2
IFS= read -r QUALIFICATION_MEMBER_ID
if [[ "${QUALIFICATION_MEMBER_ID}" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$ ]]; then
  printf 'QUALIFICATION_MEMBER_ID_FORMAT=PASS\n'
else
  printf 'QUALIFICATION_MEMBER_ID_FORMAT=FAIL\n' >&2
fi
```

`201`을 받았지만 응답을 보존하지 못했다면 같은 email로 다시 가입하지 않습니다. 아래
RDS read-only 조회에서 exact email에 대응하는 `public_id`가 한 건인지 확인해
`QUALIFICATION_MEMBER_ID`를 복원한 뒤 cleanup하고 새 입력으로 재시작합니다.

## 4. 인증 메일 요청과 실제 수신

**실행 위치:** 2절의 두 번째 EC2 child Bash session과 실제 test email 수신함

```bash
email_request_status="$({
  printf '{"email":"%s"}' "${QUALIFICATION_EMAIL}"
} | curl \
  --silent \
  --show-error \
  --output /dev/null \
  --write-out '%{http_code}' \
  --header 'Content-Type: application/json' \
  --data-binary @- \
  http://127.0.0.1:18080/api/v1/auth/verifications/email)"

printf 'EMAIL_REQUEST_STATUS=%s\n' "${email_request_status}"
test "${email_request_status}" = '202'
```

`202`는 요청 접수만 뜻하며 account 존재나 provider 성공을 외부에 노출하지 않습니다. 따라서
다음을 사람이 별도로 확인해야 합니다.

1. 입력한 실제 수신함에 새 메일이 도착합니다.
2. 발신자는 승인된 `no-reply@ronams.com`입니다.
3. 인증 URL은 승인된 `https://ronams.com`을 사용하며 `token` query를 포함합니다.

메일 본문, recipient와 token을 issue, Git, scenario와 장기 log에 복사하지 않습니다.

## 5. 이메일 인증 완료

**실행 위치:** 2절에서 시작한 두 번째 EC2 child Bash session

메일 URL의 `token` 값만 masked prompt로 입력하고 request stdin으로 전달합니다.

```bash
printf 'Email verification token: ' >&2
IFS= read -r -s VERIFICATION_TOKEN
printf '\n' >&2
if [[ "${VERIFICATION_TOKEN}" =~ ^[A-Za-z0-9_-]{32,512}$ ]]; then
  printf 'VERIFICATION_TOKEN_FORMAT=PASS\n'
  confirmation_status="$({
    printf '{"token":"%s"}' "${VERIFICATION_TOKEN}"
  } | curl \
    --silent \
    --show-error \
    --output /dev/null \
    --write-out '%{http_code}' \
    --header 'Content-Type: application/json' \
    --data-binary @- \
    http://127.0.0.1:18080/api/v1/auth/verifications/email/confirm)"
else
  printf 'VERIFICATION_TOKEN_FORMAT=FAIL\n' >&2
  confirmation_status='NOT_SENT'
fi

unset VERIFICATION_TOKEN
printf 'CONFIRMATION_STATUS=%s\n' "${confirmation_status}"
test "${confirmation_status}" = '204'
```

같은 token의 재사용 실패, 로그인·JWT·refresh token과 전체 API 검증은 이 최소 qualification의
완료 조건이 아닙니다.

## 6. RDS 결과 확인 준비

**실행 위치:** 2절에서 시작한 두 번째 EC2 child Bash session

Application이 실제 사용하는 credential과 RDS CA를 Kubernetes resource에서 가져옵니다.

```bash
RUNTIME_DB_USERNAME="$(
  sudo env KUBECONFIG=/etc/kubernetes/admin.conf \
    kubectl -n car-community get secret car-community-db-runtime \
    -o 'jsonpath={.data.DB_USERNAME}' \
  | base64 --decode
)"
test "${RUNTIME_DB_USERNAME}" = 'carmunity_runtime'

STG_DB_RUNTIME_PASSWORD="$(
  sudo env KUBECONFIG=/etc/kubernetes/admin.conf \
    kubectl -n car-community get secret car-community-db-runtime \
    -o 'jsonpath={.data.DB_PASSWORD}' \
  | base64 --decode
)"
test -n "${STG_DB_RUNTIME_PASSWORD}"

# Redirect는 root 권한을 확대하지 않고 user-owned 임시 directory에 기록합니다.
# shellcheck disable=SC2024
sudo env KUBECONFIG=/etc/kubernetes/admin.conf \
  kubectl -n car-community get configmap car-community-rds-ca \
  -o 'jsonpath={.data.ap-northeast-2-bundle\.pem}' \
  >"${QUALIFICATION_WORK_DIR}/ap-northeast-2-bundle.pem"

test -s "${QUALIFICATION_WORK_DIR}/ap-northeast-2-bundle.pem"
chmod 0600 "${QUALIFICATION_WORK_DIR}/ap-northeast-2-bundle.pem"
printf '%s  %s\n' \
  '913fb5b814f17af79d4c1622584a8d0ceddf5b0d76fe353d0c7d1186cdd6b229' \
  "${QUALIFICATION_WORK_DIR}/ap-northeast-2-bundle.pem" \
| sha256sum --check
CA_CHECK_STATUS=$?

QUALIFICATION_DB_URL='postgresql://stg-car-community-postgres.cjemi62mqsl6.ap-northeast-2.rds.amazonaws.com:5432/carmunity?sslmode=verify-full'
if [[ "${RUNTIME_DB_USERNAME}" == 'carmunity_runtime' \
    && -n "${STG_DB_RUNTIME_PASSWORD}" \
    && -s "${QUALIFICATION_WORK_DIR}/ap-northeast-2-bundle.pem" \
    && "${CA_CHECK_STATUS}" == '0' ]]; then
  printf 'RUNTIME_DB_INPUTS=PASS\n'
else
  printf 'RUNTIME_DB_INPUTS=FAIL\n' >&2
fi
unset CA_CHECK_STATUS
```

Secret value는 출력하지 않으며 master·migrator credential과 새 DB role을 사용하지
않습니다.

다른 endpoint, database, DB username이나 CA checksum이면 cleanup SQL을 실행하지 않습니다.
Schema와 runtime credential 계약을 먼저 복구합니다.

회원가입이 RDS에 반영됐지만 `201` body의 `memberId`를 잃은 경우에만 exact email로
read-only 조회합니다.

```bash
PGPASSWORD="${STG_DB_RUNTIME_PASSWORD}" \
PGSSLROOTCERT="${QUALIFICATION_WORK_DIR}/ap-northeast-2-bundle.pem" \
psql "${QUALIFICATION_DB_URL}" \
  --username="${RUNTIME_DB_USERNAME}" \
  --no-psqlrc \
  --set=ON_ERROR_STOP=1 \
  --set=qualification_email="${QUALIFICATION_EMAIL}" \
  <<'SQL'
SELECT public_id, email_normalized, status
FROM account
WHERE email_normalized = lower(btrim(:'qualification_email'));
SQL
```

결과가 정확히 한 행이고 입력한 qualification email인지 확인한 경우에만 해당 `public_id`를
`QUALIFICATION_MEMBER_ID`로 입력합니다. 0건이면 생성된 account가 없으므로 새 입력으로
다시 시작하고, 두 건 이상이거나 다른 관계가 의심되면 cleanup하지 않습니다.

## 7. RDS write와 인증 결과 확인

**실행 위치:** 2절에서 시작한 두 번째 EC2 child Bash session

다음 조회는 입력한 exact normalized email과 API가 반환한 `memberId`를 함께 사용합니다.

```bash
PGPASSWORD="${STG_DB_RUNTIME_PASSWORD}" \
PGSSLROOTCERT="${QUALIFICATION_WORK_DIR}/ap-northeast-2-bundle.pem" \
psql "${QUALIFICATION_DB_URL}" \
  --username="${RUNTIME_DB_USERNAME}" \
  --no-psqlrc \
  --set=ON_ERROR_STOP=1 \
  --set=qualification_email="${QUALIFICATION_EMAIL}" \
  --set=qualification_member_id="${QUALIFICATION_MEMBER_ID}" \
  <<'SQL'
SELECT public_id, email_normalized, status, email_verified_at
FROM account
WHERE email_normalized = lower(btrim(:'qualification_email'))
  AND public_id = :'qualification_member_id'::uuid;

SELECT event_type, result, count(*)
FROM auth_event
WHERE account_id = (
    SELECT id
    FROM account
    WHERE email_normalized = lower(btrim(:'qualification_email'))
      AND public_id = :'qualification_member_id'::uuid
)
  AND event_type IN (
      'ACCOUNT_SIGN_UP',
      'EMAIL_VERIFICATION_REQUEST',
      'EMAIL_VERIFICATION_CONFIRM'
  )
GROUP BY event_type, result
ORDER BY event_type, result;
SQL
```

첫 조회는 정확히 한 행이며 `status=ACTIVE`, `email_verified_at` not null이어야 합니다. 두 번째
조회에는 세 event type의 `SUCCESS`가 각각 한 건 이상 있어야 합니다. 이것이 HTTP 응답과
실제 RDS write·token 소비를 결속하는 완료 증거입니다.

## 8. Test account cleanup

**실행 위치:** 2절에서 시작한 두 번째 EC2 child Bash session

성공 흐름이면 `QUALIFICATION_EXPECT_ACTIVE=true`를 유지합니다. `201` 뒤 인증이 실패해
`PENDING` account만 정리하는 복구에서는 `false`로 바꿀 수 있지만, exact email과 API
`memberId` 두 값은 모두 있어야 합니다.

`auth_event` 삭제는 이 절차가 만든 staging qualification test data에만 적용하는 예외입니다.
실제 사용자·Production·Super Admin의 감사 이벤트에는 같은 SQL을 사용하지 않습니다.

```bash
QUALIFICATION_EXPECT_ACTIVE=true

PGPASSWORD="${STG_DB_RUNTIME_PASSWORD}" \
PGSSLROOTCERT="${QUALIFICATION_WORK_DIR}/ap-northeast-2-bundle.pem" \
psql "${QUALIFICATION_DB_URL}" \
  --username="${RUNTIME_DB_USERNAME}" \
  --no-psqlrc \
  --set=ON_ERROR_STOP=1 \
  --set=qualification_email="${QUALIFICATION_EMAIL}" \
  --set=qualification_member_id="${QUALIFICATION_MEMBER_ID}" \
  --set=qualification_expect_active="${QUALIFICATION_EXPECT_ACTIVE}" \
  <<'SQL'
BEGIN;

CREATE TEMP TABLE qualification_cleanup_target (
    id BIGINT PRIMARY KEY,
    email_normalized VARCHAR(254) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expect_active BOOLEAN NOT NULL
) ON COMMIT DROP;

INSERT INTO qualification_cleanup_target (id, email_normalized, status, expect_active)
SELECT id, email_normalized, status, :'qualification_expect_active'::boolean
FROM account
WHERE email_normalized = lower(btrim(:'qualification_email'))
  AND public_id = :'qualification_member_id'::uuid
FOR UPDATE;

DO $cleanup$
DECLARE
    target_id BIGINT;
    target_email_normalized VARCHAR(254);
    target_status VARCHAR(20);
    expect_active BOOLEAN;
    affected_rows BIGINT;
BEGIN
    IF (SELECT count(*) FROM qualification_cleanup_target) <> 1 THEN
        RAISE EXCEPTION 'qualification target must be exactly one account';
    END IF;

    SELECT cleanup_target.id,
           cleanup_target.email_normalized,
           cleanup_target.status,
           cleanup_target.expect_active
    INTO target_id, target_email_normalized, target_status, expect_active
    FROM qualification_cleanup_target AS cleanup_target;

    IF target_status NOT IN ('PENDING', 'ACTIVE') THEN
        RAISE EXCEPTION 'unexpected qualification account status: %', target_status;
    END IF;

    IF expect_active AND target_status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'successful qualification target is not ACTIVE';
    END IF;

    IF expect_active AND NOT EXISTS (
        SELECT 1 FROM account
        WHERE id = target_id AND email_verified_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'successful qualification target has no verified timestamp';
    END IF;

    IF (SELECT count(*) FROM account_profile WHERE account_id = target_id) <> 1 THEN
        RAISE EXCEPTION 'unexpected profile count';
    END IF;

    IF (SELECT count(*) FROM account_password_credential WHERE account_id = target_id) <> 1 THEN
        RAISE EXCEPTION 'unexpected password credential count';
    END IF;

    IF (SELECT count(*) FROM account_role_assignment WHERE account_id = target_id) <> 1
       OR NOT EXISTS (
           SELECT 1
           FROM account_role_assignment assignment
           JOIN auth_role role ON role.id = assignment.role_id
           WHERE assignment.account_id = target_id
             AND role.code = 'ROLE_MEMBER'
             AND assignment.revoked_at IS NULL
             AND assignment.granted_by_account_id IS NULL
             AND assignment.revoked_by_account_id IS NULL
       ) THEN
        RAISE EXCEPTION 'qualification target is not an exact system-granted ROLE_MEMBER';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM account_role_assignment
        WHERE account_id <> target_id
          AND (granted_by_account_id = target_id OR revoked_by_account_id = target_id)
    ) THEN
        RAISE EXCEPTION 'qualification target is referenced by another role assignment';
    END IF;

    IF EXISTS (SELECT 1 FROM refresh_token WHERE account_id = target_id) THEN
        RAISE EXCEPTION 'qualification target has refresh tokens';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM account_verification_token
        WHERE account_id = target_id
          AND (
              purpose <> 'EMAIL_VERIFICATION'
              OR delivery_channel <> 'EMAIL'
              OR recipient_normalized <> target_email_normalized
          )
    ) THEN
        RAISE EXCEPTION 'qualification target has an unexpected verification token';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM auth_event
        WHERE account_id = target_id
          AND event_type NOT IN (
              'ACCOUNT_SIGN_UP',
              'EMAIL_VERIFICATION_REQUEST',
              'EMAIL_VERIFICATION_CONFIRM'
          )
    ) THEN
        RAISE EXCEPTION 'qualification target has an unexpected auth event';
    END IF;

    IF expect_active AND EXISTS (
        SELECT required.event_type
        FROM (VALUES
            ('ACCOUNT_SIGN_UP'),
            ('EMAIL_VERIFICATION_REQUEST'),
            ('EMAIL_VERIFICATION_CONFIRM')
        ) AS required(event_type)
        WHERE NOT EXISTS (
            SELECT 1
            FROM auth_event event
            WHERE event.account_id = target_id
              AND event.event_type = required.event_type
              AND event.result = 'SUCCESS'
        )
    ) THEN
        RAISE EXCEPTION 'successful qualification evidence is incomplete';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM admin_invitation
        WHERE invited_by_account_id = target_id
           OR accepted_account_id = target_id
           OR revoked_by_account_id = target_id
           OR email_normalized = target_email_normalized
    ) THEN
        RAISE EXCEPTION 'qualification target is referenced by an admin invitation';
    END IF;

    DELETE FROM account_verification_token WHERE account_id = target_id;

    DELETE FROM refresh_token WHERE account_id = target_id;
    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 0 THEN
        RAISE EXCEPTION 'unexpected refresh token delete count: %', affected_rows;
    END IF;

    DELETE FROM auth_event WHERE account_id = target_id;

    DELETE FROM account_role_assignment WHERE account_id = target_id;
    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 1 THEN
        RAISE EXCEPTION 'unexpected role assignment delete count: %', affected_rows;
    END IF;

    DELETE FROM account_password_credential WHERE account_id = target_id;
    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 1 THEN
        RAISE EXCEPTION 'unexpected password credential delete count: %', affected_rows;
    END IF;

    DELETE FROM account_profile WHERE account_id = target_id;
    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 1 THEN
        RAISE EXCEPTION 'unexpected profile delete count: %', affected_rows;
    END IF;

    DELETE FROM account WHERE id = target_id;
    GET DIAGNOSTICS affected_rows = ROW_COUNT;
    IF affected_rows <> 1 THEN
        RAISE EXCEPTION 'unexpected account delete count: %', affected_rows;
    END IF;

    IF EXISTS (SELECT 1 FROM account WHERE id = target_id) THEN
        RAISE EXCEPTION 'qualification account remains after cleanup';
    END IF;
END
$cleanup$;

COMMIT;
SQL
```

`ON_ERROR_STOP` 때문에 guard, FK나 row count가 하나라도 어긋나면 `psql`은 non-zero로
끝나고 열린 transaction은 commit되지 않습니다. 실패를 다른 account 삭제나 `CASCADE`로
우회하지 않습니다. 현재 schema가 바뀌었다면
[100 Account 인증/인가 Schema Reference](../project/schema/100-account-auth.md#갱신-규칙)와 이
SQL의 guard·삭제 순서를 같은 작업에서 갱신한 뒤 재개합니다.

## 9. 종료와 증거

**실행 위치:** 첫 번째와 두 번째 EC2 SSH Bash session

Cleanup 성공 뒤 같은 exact email과 `memberId`의 account가 0건인지 확인합니다.

```bash
QUALIFICATION_REMAINING_COUNT="$({
  PGPASSWORD="${STG_DB_RUNTIME_PASSWORD}" \
  PGSSLROOTCERT="${QUALIFICATION_WORK_DIR}/ap-northeast-2-bundle.pem" \
  psql "${QUALIFICATION_DB_URL}" \
    --username="${RUNTIME_DB_USERNAME}" \
    --no-psqlrc \
    --set=ON_ERROR_STOP=1 \
    --set=qualification_email="${QUALIFICATION_EMAIL}" \
    --set=qualification_member_id="${QUALIFICATION_MEMBER_ID}" \
    --tuples-only \
    --no-align \
    <<'SQL'
SELECT count(*)
FROM account
WHERE email_normalized = lower(btrim(:'qualification_email'))
  AND public_id = :'qualification_member_id'::uuid;
SQL
})"

printf 'QUALIFICATION_REMAINING_COUNT=%s\n' "${QUALIFICATION_REMAINING_COUNT}"
if [[ "${QUALIFICATION_REMAINING_COUNT}" == '0' ]]; then
  unset STG_DB_RUNTIME_PASSWORD QUALIFICATION_PASSWORD QUALIFICATION_REMAINING_COUNT
  cleanup_qualification_shell
  trap - EXIT
  printf 'QUALIFICATION_COMPLETE\n'
  exit
else
  printf 'QUALIFICATION_COMPLETE=FAIL\n' >&2
fi
```

Count가 `0`이고 `QUALIFICATION_COMPLETE`가 출력된 뒤 parent SSH shell로 돌아와야
합니다. 첫 번째 EC2 session에서 `Ctrl-C`로 port-forward를 종료합니다.
Scenario에는 실행 시각, `201`·`202`·실제 수신·`204`·DB `ACTIVE`·cleanup 성공 여부만
기록합니다. Email, `memberId`, password, token, Secret value와 메일 본문은 기록하지 않습니다.

## 중단과 재개

| 실패 위치 | 남을 수 있는 상태 | 하지 않는 일 | 재개 |
| --- | --- | --- | --- |
| Port-forward·가입 전 | Application 상태만 유지 | Service·Pod 재생성 | 기술 상태를 복구한 뒤 `1`절 |
| 회원가입 `201` 전 | Account가 없거나 응답 직전 생성됐을 수 있음 | 같은 email 가입 반복·email만으로 삭제 | Exact email read-only 조회로 0건인지 확인합니다. 한 건이면 `public_id`를 복원해 `8`절을 `false`로 실행하고, 0건이면 새 입력으로 `2`절부터 재개합니다. |
| `201` 뒤 메일 요청·수신·confirm 실패 | `PENDING` test account와 token·event | Account 방치·다른 email로 계속 생성 | 원인을 관찰한 뒤 exact email·`memberId`로 `8`절을 `false`로 실행하고 새 입력으로 재개합니다. |
| `204` 뒤 RDS 증거 불일치 | `ACTIVE` test account | 성공 추정·cleanup 생략 | Account·event 조회를 보존해 원인을 확인하고, guard가 허용하면 `8`절 cleanup 뒤 새 입력으로 재개합니다. |
| Cleanup guard·transaction 실패 | Test account와 모든 관련 row가 유지 | Guard 완화·`CASCADE`·master credential 우회 | Schema와 실제 참조를 확인해 Runbook SQL을 먼저 갱신한 뒤 같은 exact email·`memberId`로 cleanup만 재실행합니다. |
| Cleanup 성공 뒤 사후 확인 실패 | Commit 결과 불명확 | 같은 가입 흐름 즉시 반복 | 같은 exact email·`memberId`로 read-only 0건 확인부터 재개합니다. |
| Child Bash 중간 종료·SSH 단절 | `EXIT` trap이 shell 민감값·임시 directory를 정리하지만 test account는 남을 수 있음 | Parent shell의 변수를 추측해 계속 실행 | Exact email·`memberId`를 복원해 현재 상태를 조회하고, guard가 허용하면 8절 cleanup만 새 child Bash에서 재개합니다. |

## 제외 범위

- Production의 상태 변경 smoke
- 로그인·JWT·refresh token과 전체 API 검증
- Token 재사용 실패 시험
- ALB·domain·외부 HTTPS 경로
- 부하·재시작·장애 복구 시험
- Resend dashboard 이중 확인
- 최초 `ROLE_SUPER_ADMIN` 생성

## 관련 문서

- **기술 bootstrap**: [350 Application delivery 최초 bootstrap](350-application-delivery-bootstrap-runbook.md)
- **현재 staging 실행 상태**: [100 Staging 단일 node 전체 구축 시나리오](scenarios/100-staging-single-node-bootstrap.md)
- **Account schema**: [100 Account 인증/인가 Schema Reference](../project/schema/100-account-auth.md)
- **RDS·role 경계**: [140 RDS PostgreSQL](../project/infrastructure/140-rds-postgresql.md)
- **이메일 인증 정책**: [110 Account 인증/인가 정책](../domain/110-account-auth-policy.md)
