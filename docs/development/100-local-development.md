# 로컬 개발 환경

## 목적

이 문서는 개발자가 로컬에서 애플리케이션을 실행하고 작업 위험에 맞는 검증을 직접
선택하는 방법을 정의합니다. 품질 기준은
[코드 품질 도구](../tech-stack/310-code-quality-tools.md)가 소유합니다.

## 개발과 검증 원칙

저장소는 pre-commit·pre-push hook을 설치하거나 전체 품질 wrapper를 강제하지
않습니다. 기능 개발 중에는 바꾼 범위의 test와 문서를 확인하고, 구현 완료 전에는
다음 명령으로 CI의 애플리케이션 품질 경계를 직접 재현합니다.

```sh
docker info
JAVA_HOME=<Java 21 경로> ./gradlew check --no-daemon --rerun-tasks --console=plain
```

첫 명령은 Testcontainers가 사용할 Docker daemon을 확인합니다. 두 번째 명령은 이전
task 결과를 재사용하지 않고 unit·integration test, architecture test, coverage와
format 검사를 실행합니다. commit 범위는 `git diff --cached`로, 남은 변경은
`git status --short`로 직접 확인합니다. 로컬 hook 통과를 CI 성공이나 정확한 commit
범위의 대체 증거로 사용하지 않습니다.

## 환경 변수 key와 값 주입

`.env`는 Git에 커밋하지 않는 로컬 설정 파일입니다. `.env.example`은 필요한 key 이름만
`KEY=` 형식으로 공유하며 비밀 여부와 관계없이 실제 값·예시값·기본값·placeholder를 두지
않습니다. 실제 `.env`에는 각 환경에서 사용할 값을 직접 기록합니다.

| key | 설명 |
| --- | --- |
| `DB_URL` | PostgreSQL JDBC URL입니다. |
| `DB_USERNAME` | PostgreSQL application 계정입니다. |
| `DB_PASSWORD` | PostgreSQL application 비밀번호입니다. |
| `AUTH_JWT_ISSUER` | access token issuer입니다. |
| `AUTH_JWT_ACCESS_TOKEN_TTL` | access token TTL입니다. |
| `AUTH_JWT_HMAC_SECRET` | HS256 서명·검증 secret입니다. 32 bytes 이상이어야 합니다. |
| `AUTH_LOGIN_IDENTIFIER_HMAC_SECRET` | 로그인 실패 identifier hash용 별도 secret입니다. JWT secret과 다른 값을 사용해야 하지만 현재 application은 동일값을 거부하지 않습니다. |
| `RESEND_API_KEY` | Resend API key입니다. |
| `NOTIFICATION_EMAIL_FROM` | 인증된 발신자 email입니다. |
| `EMAIL_VERIFICATION_BASE_URL` | 이메일 인증 화면 URL입니다. |
| `ADMIN_INVITATION_BASE_URL` | 관리자 초대 수락 화면 URL입니다. |
| `PASSWORD_RESET_BASE_URL` | 비밀번호 재설정 화면 URL입니다. |

Staging bootstrap key는 Spring local runtime key와 분리합니다.

| key | 설명 |
| --- | --- |
| `STG_DB_RUNTIME_PASSWORD` | Runtime role의 비밀번호입니다. |
| `STG_DB_MIGRATOR_PASSWORD` | Migrator role의 비밀번호입니다. |
| `STG_AUTH_JWT_HMAC_SECRET` | Staging JWT HS256용 32 bytes 이상 secret입니다. |
| `STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET` | Staging login identifier hash용 별도 32 bytes 이상 secret입니다. |
| `STG_RESEND_API_KEY` | Staging Kubernetes notification Secret의 Resend provider credential입니다. |

Runtime과 migrator username은 bootstrap script에 각각 `carmunity_runtime`과
`carmunity_migrator`로 고정하고, staging JDBC URL도 staging manifest와 bootstrap
script에 exact 값으로 고정하므로 `.env`에 저장하지 않습니다. Master를 포함한 세
password는 모두 서로 다른 값을 사용합니다. Role만 나누고
credential을 재사용하면 runtime credential 유출이 migrator DDL 권한으로 확대될 수
있으므로 두 bootstrap script가 값을 출력하지 않고 각자의 mutation 전에 입력 계약을
검증합니다. Deployment와 PreSync Job은 서로 다른 Secret만 참조해 runtime·migrator
credential 소비 경계를 유지합니다.

이 값들은 local의 신뢰된 `.env`에서 최초 bootstrap 또는 구축 후 credential operation
입력으로만 읽습니다. `.env` file 전체를 EC2로 복사하거나 container image·Kubernetes
volume에 넣지 않습니다. Runbook이 정한 allow-list와 순서로 필요한 값만 SSH stdin에
전달하고, remote script는 argument·log에 값을 출력하지 않습니다. RDS master password는
RDS가 관리하는 Secrets Manager의 현재 값을 실행 시점에 일회성으로 사용하며 `.env`의 장기
key로 추가하지 않습니다. Resend key 교체는
[540 Application 환경변수·Secret 변경의 Resend API key 교체](../operations/540-application-environment-and-secret-change-runbook.md#4-resend-api-key-교체)를
따릅니다.

실제 secret이 있는 `.env`는 symlink가 아닌 현재 사용자 소유 regular file, mode `0600`으로
유지합니다. `source`는 shell code를 실행하므로 직접 관리한 file만 읽고 각 값은 shell-safe
assignment로 작성합니다. 특히 `&`가 있는 JDBC URL은 작은따옴표로 감쌉니다. Command
substitution·backtick·임의 command를 `.env` 값에 넣지 않습니다.

새 key를 추가할 때는 application 설정의 참조와 안전한 기본값을 정하고,
`.env.example`에 빈 key를 추가하며, 관련 개발 문서와 완료 보고에 key 이름·용도만
남깁니다. 실제 값은 사용자가 로컬 `.env`나 운영 secret store에 직접 주입합니다.

## SonarQube Cloud 재현과 push 후 완료 점검

Sonar 분석은 매 push마다 로컬에서 자동 실행하지 않습니다. 원격 CI 실패를 재현하거나
분석 설정을 바꿀 때만 token과 project 설정을 현재 shell 환경변수로 주입합니다.
일반 코드 작업은 local Gradle 검증 뒤 commit·push하고 exact SHA의 CI 분석이 끝난 다음
읽기 전용 완료 점검을 실행합니다.

| key | 용도 |
| --- | --- |
| `SONAR_TOKEN` | SonarQube Cloud 분석·Web API token. private project 조회 사용자는 `Browse Project`, 분석 제출은 `Execute Analysis` 권한이 필요합니다. |
| `SONAR_ORGANIZATION` | organization key |
| `SONAR_PROJECT_KEY` | project key |
| `SONAR_HOST_URL` | 기본값 `https://sonarcloud.io` |

Gradle scanner를 사용하므로 SonarQube Cloud의 `Automatic Analysis`는 비활성화합니다.
Gradle은 `.env`를 자동으로 읽지 않습니다. 값을 local `.env`에 보관했다면 파일 내용을
검토한 뒤 분석을 실행할 현재 shell에 명시적으로 load합니다.

```bash
set +x
. ./.env
typeset +x \
  DB_PASSWORD STG_DB_RUNTIME_PASSWORD STG_DB_MIGRATOR_PASSWORD \
  AUTH_JWT_HMAC_SECRET AUTH_LOGIN_IDENTIFIER_HMAC_SECRET \
  STG_AUTH_JWT_HMAC_SECRET STG_AUTH_LOGIN_IDENTIFIER_HMAC_SECRET \
  RESEND_API_KEY STG_RESEND_API_KEY SONAR_TOKEN
```

`set +x`는 상속된 xtrace를 `.env`를 읽기 전에 끕니다. `typeset +x`는 source 전 이미
export됐거나 `.env`에서 export된 known secret의 export attribute까지 제거합니다. 아래
Sonar command는 `env -i`로 local environment를 비운 뒤 Sonar allow-list 네 개만 child
process에 전달합니다. `set -a`·`allexport`로 staging
DB·HMAC secret 전체를 Gradle이나 다른 child process에 내보내지 않으며 CI에서는
`.env`를 읽지 않습니다.
로컬 scanner와 공식 Quality Gate 대기 실행법은
[코드 품질 도구](../tech-stack/310-code-quality-tools.md)를 따릅니다. GitHub Actions는
`.env`를 읽지 않고 `SONAR_TOKEN` secret과 organization·project variables를
사용합니다.

exact SHA의 required CI가 성공한 뒤에는 위 sanitized command를 실행합니다.

```bash
env -i HOME="${HOME}" PATH="${PATH}" \
  SONAR_TOKEN="${SONAR_TOKEN}" \
  SONAR_ORGANIZATION="${SONAR_ORGANIZATION}" \
  SONAR_PROJECT_KEY="${SONAR_PROJECT_KEY}" \
  SONAR_HOST_URL="${SONAR_HOST_URL}" \
  scripts/quality/verify-sonar-status.sh --expected-revision "$(git rev-parse HEAD)"
```

latest Sonar revision이 입력 SHA와 다르면 이전 결과를 현재 작업의 성공으로 사용하지
않고 `NOT VERIFIED`로 멈춥니다. Quality Gate 실패나 Open issue가 있으면 원인을 고치고
local 검증, 새 commit·push와 exact SHA 검증을 다시 수행합니다. 도구의 출력·exit 계약은
[품질 완료 점검 스크립트](../../scripts/quality/README.md)가 소유합니다.

## 관련 문서

- **개발 절차 router**: [개발 문서 목차](000-index.md)
- **검증 기준**: [코드 품질 도구](../tech-stack/310-code-quality-tools.md)
- **GitHub·Sonar 최초 설정**: [GitHub CI 품질 게이트 최초 설정](../operations/310-github-ci-quality-configuration-runbook.md)
- **Sonar token 변경·복구**: [Sonar token 교체](../operations/530-sonar-token-rotation-runbook.md)
- **Git 완료 절차**: [커밋 워크플로우](../workflow/300-commit-workflow.md)
