# Application prerequisite bootstrap

이 디렉터리는 staging application 배포 전에 필요한 두 가지 최초 설정만 소유합니다.

| 순서 | Script | 결과 |
| --- | --- | --- |
| `100` | `100-provision-database-access.sh` | PostgreSQL runtime·migrator role과 최소 schema 권한 생성 |
| `200` | `200-apply-application-prerequisites.sh` | Kubernetes namespace와 runtime·migrator·auth·notification Secret 생성 |

`100`은 RDS 내부 상태를, `200`은 Kubernetes resource를 변경하므로 실패·재실행 경계가
달라 분리합니다. 정상 순서는 `100` → `200`이며 Deployment·Migration Job·Service는
`deploy/` manifest가 소유합니다.

실제 local `.env` 확인, RDS master password 입력, SSH stdin 전달과 다음 GitHub App 연결까지의
최초 실행 순서는
[Application delivery 최초 bootstrap runbook](../../../docs/operations/350-application-delivery-bootstrap-runbook.md)이
소유합니다. 이 README는 두 script의 입력과 결과 계약만 소유합니다.

## Secret 전달

실제 값은 local의 신뢰된 `.env`에만 저장합니다. `.env` file을 EC2에 복사하지 않고 필요한
값만 고정 순서의 NUL-delimited SSH stdin으로 전달합니다. 값은 Git, CLI argument와 progress
log에 기록하지 않습니다. RDS master password는 role 생성 때만 별도로 입력하며 application
Secret에 넣지 않습니다.

## `100-provision-database-access.sh`

이 script는 application DB 접근에 필요한 최소 상태만 만드는 bootstrap 도구입니다.

- PostgreSQL client가 없으면 Ubuntu 24.04 official APT의 `postgresql-client-16`을 설치하고
  client major version이 `16`인지 확인합니다. 이미 설치된 client가 다른 major이면 임의로
  교체하지 않고 중단합니다.
- `carmunity_runtime` role이 없으면 만들고 password를 설정합니다. 기존 role이면 role과
  password를 그대로 유지합니다.
- `carmunity_migrator` role도 같은 기준으로 처리합니다.
- 매 실행에서 runtime에 database `CONNECT`와 schema `USAGE`, migrator에 database
  `CONNECT`와 schema `USAGE`·`CREATE`를 부여하고 `PUBLIC`의 schema `CREATE`를 회수합니다.
- 필요한 role 생성과 권한 적용은 한 transaction에서 수행합니다.
- 적용 뒤 두 role이 존재하는지만 확인합니다.

기존 role의 password·속성·membership·ownership·현재 권한을 전수 감사하거나 자동
교정하지 않습니다. 기존 role에 전달한 password도 검증하거나 변경하지 않습니다. 그런
운영 상태 감사·복구·credential rotation은 이 최초 bootstrap 단계의 범위가 아닙니다.

Script는 Ubuntu 24.04에서 `sudo`로 실행하며 application package는 설치하지 않습니다.
JDBC의 `verify-full` 계약을 유지하기 위해 AWS Seoul RDS CA bundle을 임시 directory로
내려받아 pinned SHA-256을 확인한 뒤 연결에 사용하고 종료할 때 삭제합니다.

입력은 다음 세 password입니다. Staging DB URL과 role 이름은 script 상수로 고정하므로
입력받지 않습니다.

1. 현재 RDS master password
2. `STG_DB_RUNTIME_PASSWORD`
3. `STG_DB_MIGRATOR_PASSWORD`

세 password는 서로 달라야 합니다.

실행 중 중단됐거나 같은 bootstrap을 다시 확인해야 하면 같은 세 password로 재실행할 수
있습니다. 이미 존재하는 role은 유지하고 없는 role만 만들며, idempotent한 `GRANT`·`REVOKE`를
다시 적용합니다.

```bash
printf '%s\0' \
  "${CURRENT_RDS_MASTER_PASSWORD}" \
  "${STG_DB_RUNTIME_PASSWORD}" \
  "${STG_DB_MIGRATOR_PASSWORD}" \
| env -i HOME="${HOME}" PATH="${PATH}" ssh ubuntu@<EC2> \
  'sudo -n /home/ubuntu/car-community-scripts/bootstrap/application/100-provision-database-access.sh'
```

Table DML과 identity sequence 권한은 role 생성 시 미리 부여하지 않습니다. Migration Job이
성공한 뒤 Flyway `afterMigrate` callback이 현재 table·sequence에 runtime 권한을 부여하고
`flyway_schema_history` 권한을 회수합니다.

## `200-apply-application-prerequisites.sh`

`200`은 다음 Git 밖 resource를 server-side apply로 생성합니다.

| Resource | data key |
| --- | --- |
| Namespace `car-community` | 없음 |
| Secret `car-community-db-runtime` | `DB_USERNAME`, `DB_PASSWORD` |
| Secret `car-community-db-migrator` | `DB_MIGRATOR_USERNAME`, `DB_MIGRATOR_PASSWORD` |
| Secret `car-community-auth` | `AUTH_JWT_HMAC_SECRET`, `AUTH_LOGIN_IDENTIFIER_HMAC_SECRET` |
| Secret `car-community-community` | `COMMUNITY_CURSOR_HMAC_SECRET` |
| Secret `car-community-notification` | `RESEND_API_KEY` |

입력은 두 role password, 서로 다른 32 bytes 이상의 인증 HMAC secret 두 개, 별도의 32 bytes
이상 Community cursor HMAC secret과 `STG_RESEND_API_KEY`인 여섯 NUL field입니다. Role 이름은 `carmunity_runtime`과
`carmunity_migrator`로 고정하므로 다시 입력받지 않습니다. Project field manager로
`kubectl apply --server-side`를 실행하고
`--force-conflicts`는 사용하지 않습니다. Secret 값은 base64 `data`로 변환하므로 CLI
argument와 log에 원문을 넣지 않으며, 적용 뒤 resource 6개를 Kubernetes API에서 확인합니다.

DB URL과 pinned Seoul RDS CA는 비밀이 아니므로 `200`이 받거나 내려받지 않습니다.
`deploy/overlays/stg/pre-sync`의 Git-tracked ConfigMap이 Argo CD PreSync wave `-2`에서 먼저
적용됩니다. Notification 발신자와 세 frontend URL도 staging overlay의 Git-managed
ConfigMap이 소유합니다.

```bash
printf '%s\0' \
  "${STG_DB_RUNTIME_PASSWORD}" \
  "${STG_DB_MIGRATOR_PASSWORD}" \
  "${STG_AUTH_JWT_HMAC_SECRET}" \
  "${STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET}" \
  "${STG_COMMUNITY_CURSOR_HMAC_SECRET}" \
  "${STG_RESEND_API_KEY}" \
| env -i HOME="${HOME}" PATH="${PATH}" ssh ubuntu@<EC2> \
  'sudo -n /home/ubuntu/car-community-scripts/bootstrap/application/200-apply-application-prerequisites.sh'
```

## 검증

```bash
bash -n \
  scripts/bootstrap/application/100-provision-database-access.sh \
  scripts/bootstrap/application/200-apply-application-prerequisites.sh \
  scripts/bootstrap/application/tests/test-100-provision-database-access.sh \
  scripts/bootstrap/application/tests/test-200-apply-application-prerequisites.sh

shellcheck -x -P SCRIPTDIR \
  scripts/bootstrap/application/100-provision-database-access.sh \
  scripts/bootstrap/application/200-apply-application-prerequisites.sh \
  scripts/bootstrap/application/tests/test-100-provision-database-access.sh \
  scripts/bootstrap/application/tests/test-200-apply-application-prerequisites.sh

scripts/bootstrap/application/tests/test-100-provision-database-access.sh
scripts/bootstrap/application/tests/test-200-apply-application-prerequisites.sh
```

Local 검증은 PostgreSQL client 설치 분기·major version, input parsing, 두 role의
존재 조합별 생성·유지, SQL과 Kubernetes manifest 조립을 확인합니다. 실제 Ubuntu package
설치, RDS 인증·권한, Kubernetes API 적용과 Pod 연결은 target 환경에서 각 script와 배포 후
확인합니다.
최초 bootstrap 뒤 Resend key 교체는
[Application configuration operations](../../operations/application/README.md)가 소유합니다.
