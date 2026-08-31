# Auth Event 정책

이 문서는 `auth_event`에 저장되는 인증/인가 감사 이벤트의 taxonomy, 생성 조건, 저장 값, transaction 경계를 정리합니다.

## 목적

- 인증/인가 감사 이벤트의 저장 기준을 한곳에서 확인합니다.
- `event_type`, `result`, `reason_code`, `metadata`의 책임을 분리해 이벤트가 늘어나도 의미가 무너지지 않게 합니다.
- 운영자가 이벤트를 보고 어떤 인증 흐름에서 어떤 결과와 원인이 발생했는지 판단할 수 있게 합니다.
- 새 인증 이벤트를 추가할 때 schema, persistence mapper, 테스트, 문서를 함께 갱신할 기준을 제공합니다.

## 적용 범위

- `auth_event` table
- 회원 자체 가입 감사 이벤트
- 비밀번호 로그인 성공/실패 감사 이벤트
- refresh token rotation, reuse detection, account 상태 거부 감사 이벤트
- logout 감사 이벤트
- 이메일 인증 요청/완료 감사 이벤트
- 최초 최고 관리자 bootstrap 감사 이벤트
- 관리자 초대 생성·수락·회수 감사 이벤트
- 비밀번호 변경 성공/실패 감사 이벤트
- 비밀번호 재설정 요청/완료 감사 이벤트

애플리케이션 일반 로그, HTTP access log, 관리자 활동 로그 전체, 비즈니스 도메인 이벤트 전체는 이 문서의 범위가 아닙니다.

## Taxonomy 원칙

`auth_event`의 분류 column은 다음 책임을 가집니다.

| column | 책임 | 금지 규칙 |
| --- | --- | --- |
| `event_type` | 어떤 인증/인가 흐름에서 발생한 이벤트인지 나타냅니다. | 성공, 실패, 탐지, 완료 같은 결과나 원인을 넣지 않습니다. |
| `result` | 해당 흐름의 결과를 `SUCCESS` 또는 `FAILURE`로 나타냅니다. | 실패 원인이나 처리 사유를 넣지 않습니다. |
| `reason_code` | 같은 `event_type/result` 안에서 결과가 발생한 세부 원인을 나타냅니다. | `event_type`과 같은 의미를 반복하지 않습니다. |
| `metadata` | column으로 승격하기에는 보조적인 구조화 값을 event별 허용 key로만 담습니다. | 민감정보, 원문 입력, 무정의 key를 넣지 않습니다. |

`event_type`과 `reason_code`가 같은 문자열이면 DB constraint로 거부합니다.
문자열이 다르더라도 같은 의미를 반복하면 taxonomy 위반입니다.
예를 들어 `TOKEN_REUSE_DETECTED/FAILURE/TOKEN_REUSE_DETECTED`는 금지하고, `TOKEN_REFRESH/FAILURE/TOKEN_REUSE_DETECTED`로 저장합니다.

## 공통 저장 모델

| column | 의미 | 현재 저장 기준 |
| --- | --- | --- |
| `account_id` | 이벤트와 연결된 account 내부 id | account를 특정할 수 있으면 저장하고, 특정할 수 없는 로그인 실패는 null입니다. |
| `event_type` | 인증/인가 흐름 이름 | DB check constraint에 허용된 흐름 문자열만 저장합니다. |
| `result` | 성공/실패 분류 | `SUCCESS` 또는 `FAILURE`입니다. |
| `reason_code` | 같은 흐름 안에서의 상세 원인 | 원인 구분이 필요한 이벤트만 저장하고, 없으면 null입니다. |
| `identifier_hash` | 로그인 식별자 HMAC-SHA256 hash | 비밀번호 로그인 실패에서 실패 시도 추적이 필요하면 저장합니다. |
| `ip_address` | 요청 IP | 현재 이벤트 writer는 저장하지 않습니다. |
| `user_agent` | 요청 User-Agent | 현재 이벤트 writer는 저장하지 않습니다. |
| `metadata` | event별 허용 key를 가진 JSON object | 기본값은 `{}`이며, object가 아닌 JSON 값은 DB constraint로 거부합니다. |
| `occurred_at` | 이벤트 발생 시각 | application clock 또는 DB auditing/default가 채웁니다. |

`identifier_hash`는 로그인 실패 식별자 추적용입니다.
원문 email이나 단순 SHA-256이 아니라 별도 HMAC-SHA256 secret으로 계산한 64자 hex 값입니다.

## 현재 저장 이벤트

현재 코드가 실제 저장하는 이벤트는 다음과 같습니다.

| event type                    | result    | reason code                 | 생성 조건                                                                             | account id     | identifier hash | metadata                                                                  |
| ----------------------------- | --------- | --------------------------- | --------------------------------------------------------------------------------- | -------------- | --------------- | ------------------------------------------------------------------------- |
| `ACCOUNT_SIGN_UP`             | `SUCCESS` | null                        | 일반 회원 자체 가입이 성공해 account graph가 저장될 때                                             | 생성된 account    | null            | `{}`                                                                      |
| `PASSWORD_LOGIN`              | `SUCCESS` | null                        | 비밀번호 로그인이 성공하고 refresh token 저장, 로그인 실패 상태 초기화, last login 갱신을 수행할 때              | 로그인 account    | null            | `{}`                                                                      |
| `PASSWORD_LOGIN`              | `FAILURE` | `UNKNOWN_IDENTIFIER`        | email로 account를 찾지 못한 비밀번호 로그인 실패                                                 | null           | 로그인 식별자 hash    | `{}`                                                                      |
| `PASSWORD_LOGIN`              | `FAILURE` | `PASSWORD_MISMATCH`         | account는 찾았지만 비밀번호가 일치하지 않는 로그인 실패                                                | 대상 account     | 로그인 식별자 hash    | `{}`                                                                      |
| `PASSWORD_LOGIN`              | `FAILURE` | `ACCOUNT_SUSPENDED`         | `SUSPENDED` account의 비밀번호 로그인 시도                                                  | 대상 account     | 로그인 식별자 hash    | `{}`                                                                      |
| `PASSWORD_LOGIN`              | `FAILURE` | `ACCOUNT_WITHDRAWN`         | `WITHDRAWN` account의 비밀번호 로그인 시도                                                  | 대상 account     | 로그인 식별자 hash    | `{}`                                                                      |
| `TOKEN_REFRESH`               | `SUCCESS` | null                        | refresh token rotation이 성공해 새 refresh token을 저장하고 기존 token을 `ROTATED`로 폐기할 때      | token의 account | null            | `{}`                                                                      |
| `TOKEN_REFRESH`               | `FAILURE` | `TOKEN_REUSE_DETECTED`      | 이미 폐기됐거나 교체된 refresh token 재사용이 감지되어 family의 활성 token을 `REUSE_DETECTED`로 폐기할 때    | token의 account | null            | `{}`                                                                      |
| `TOKEN_REFRESH`               | `FAILURE` | `ACCOUNT_SUSPENDED`         | refresh 대상 account가 `SUSPENDED`라 token family를 폐기할 때                              | token의 account | null            | `{}`                                                                      |
| `TOKEN_REFRESH`               | `FAILURE` | `ACCOUNT_WITHDRAWN`         | refresh 대상 account가 `WITHDRAWN`이라 token family를 폐기할 때                             | token의 account | null            | `{}`                                                                      |
| `LOGOUT`                      | `SUCCESS` | null                        | 활성 refresh token 하나를 `LOGOUT`으로 폐기할 때                                             | token의 account | null            | `{}`                                                                      |
| `EMAIL_VERIFICATION_REQUEST`  | `SUCCESS` | null                        | 이메일 인증 token transaction commit 이후 인증 메일 발송이 성공했을 때                               | 대상 account     | null            | `{}`                                                                      |
| `EMAIL_VERIFICATION_REQUEST`  | `FAILURE` | `DELIVERY_FAILED`           | 이메일 인증 token transaction commit 이후 인증 메일 발송이 실패했을 때                               | 대상 account     | null            | `{}`                                                                      |
| `EMAIL_VERIFICATION_CONFIRM`  | `SUCCESS` | null                        | 이메일 인증 token 검증, account `ACTIVE` 전환, token 소비가 성공했을 때                            | 대상 account     | null            | `{}`                                                                      |
| `EMAIL_VERIFICATION_CONFIRM`  | `FAILURE` | `TOKEN_INVALID`             | 제출된 token hash로 저장된 이메일 인증 token을 찾지 못했거나 application command의 raw token이 비어 있을 때 | null           | null            | `{}`                                                                      |
| `EMAIL_VERIFICATION_CONFIRM`  | `FAILURE` | `TOKEN_EXPIRED`             | 저장된 이메일 인증 token이 만료됐을 때                                                          | 대상 account     | null            | `{}`                                                                      |
| `EMAIL_VERIFICATION_CONFIRM`  | `FAILURE` | `TOKEN_ALREADY_USED`        | 저장된 이메일 인증 token이 이미 소비됐을 때                                                       | 대상 account     | null            | `{}`                                                                      |
| `EMAIL_VERIFICATION_CONFIRM`  | `FAILURE` | `TOKEN_REVOKED`             | 저장된 이메일 인증 token이 재발급 등으로 폐기됐을 때                                                  | 대상 account     | null            | `{}`                                                                      |
| `EMAIL_VERIFICATION_CONFIRM`  | `FAILURE` | `ACCOUNT_NOT_PENDING`       | token은 유효하지만 대상 account가 더 이상 `PENDING`이 아니라 `ACTIVE` 전환을 수행하지 못했을 때              | 대상 account     | null            | `{}`                                                                      |
| `PASSWORD_RESET_REQUESTED`    | `SUCCESS` | null                        | 비밀번호 재설정 token transaction commit 이후 reset 메일 발송이 성공했을 때                            | 대상 account     | null            | `{}`                                                                      |
| `PASSWORD_RESET_REQUESTED`    | `FAILURE` | null                        | 비밀번호 재설정 token transaction commit 이후 reset 메일 발송이 실패했을 때                            | 대상 account     | null            | `{}`                                                                      |
| `PASSWORD_RESET_COMPLETED`    | `SUCCESS` | null                        | reset token 소비, password hash 갱신, active refresh token 폐기가 성공했을 때                       | 대상 account     | null            | `{}`                                                                      |
| `PASSWORD_CHANGE`             | `SUCCESS` | null                        | 비밀번호 변경이 성공해 password hash를 갱신하고 active refresh token을 `PASSWORD_CHANGED`로 폐기할 때  | 대상 account     | null            | `{"actorType":"ACCOUNT","action":"CHANGED"}`                              |
| `PASSWORD_CHANGE`             | `FAILURE` | `CURRENT_PASSWORD_MISMATCH` | 비밀번호 변경 요청의 current password가 일치하지 않을 때                                           | 대상 account     | null            | `{}`                                                                      |
| `PASSWORD_CHANGE_REQUIREMENT` | `SUCCESS` | `INITIAL_ADMIN_BOOTSTRAP`   | 최초 최고 관리자 bootstrap에서 초기 비밀번호 변경 강제 상태를 만들 때                                      | 생성된 account    | null            | `{"actorType":"SYSTEM","action":"REQUIRED"}`                              |
| `ROLE_CHANGE`                 | `SUCCESS` | `SUPER_ADMIN_BOOTSTRAP`     | 최초 최고 관리자 bootstrap에서 `ROLE_SUPER_ADMIN`을 부여할 때                                   | 생성된 account    | null            | `{"roleCode":"ROLE_SUPER_ADMIN","actorType":"SYSTEM","action":"GRANTED"}` |
| `ADMIN_INVITATION_CREATED`    | `SUCCESS` | null                        | 최고 관리자가 관리자 초대를 생성하고 기존 같은 email의 open 초대가 있으면 회수 처리까지 성공했을 때                    | 초대 생성 account  | null            | 초대 public id, 대상 email hash, role, 기존 초대 회수 여부                          |
| `ADMIN_INVITATION_ACCEPTED`   | `SUCCESS` | null                        | 초대 token 검증, 관리자 account graph 생성, 초대 수락 상태 저장이 성공했을 때                              | 생성된 account    | null            | 초대 public id, role, 초대 생성 account id                                      |
| `ADMIN_INVITATION_REVOKED`    | `SUCCESS` | null                        | 최고 관리자가 open 관리자 초대를 명시적으로 회수했을 때                                               | 회수 account      | null            | 초대 public id, 대상 email hash, reason code                                  |

## Schema 허용 이벤트

DB schema는 다음 `event_type`을 허용합니다.

| event type | 현재 저장 상태 | 비고 |
| --- | --- | --- |
| `ACCOUNT_SIGN_UP` | 저장함 | 일반 회원 자체 가입 성공 |
| `PASSWORD_LOGIN` | 저장함 | 비밀번호 로그인 성공/실패 |
| `LOGOUT` | 저장함 | 활성 refresh token 단일 폐기 |
| `TOKEN_REFRESH` | 저장함 | refresh token rotation 성공, reuse detection, account 상태 거부 |
| `PASSWORD_CHANGE` | 저장함 | 비밀번호 변경 성공과 current password 불일치 |
| `PASSWORD_CHANGE_REQUIREMENT` | 저장함 | 최초 최고 관리자 초기 비밀번호 변경 강제 |
| `ROLE_CHANGE` | 저장함 | 최초 최고 관리자 `ROLE_SUPER_ADMIN` 부여 |
| `EMAIL_VERIFICATION_REQUEST` | 저장함 | 이메일 인증 메일 발송 요청 결과 |
| `EMAIL_VERIFICATION_CONFIRM` | 저장함 | 이메일 인증 완료 |
| `ADMIN_INVITATION_CREATED` | 저장함 | 관리자 초대 생성 |
| `ADMIN_INVITATION_ACCEPTED` | 저장함 | 관리자 초대 수락 |
| `ADMIN_INVITATION_REVOKED` | 저장함 | 관리자 초대 회수 |
| `PASSWORD_RESET_REQUESTED` | 저장함 | 비밀번호 재설정 메일 발송 요청 결과 |
| `PASSWORD_RESET_COMPLETED` | 저장함 | 비밀번호 재설정 완료 |

schema에 허용되어도 저장 경로가 없는 reason code는 운영 대시보드나 분석에서 현재 발생 가능한 이벤트로 계산하지 않습니다.

## 저장하지 않는 실패

모든 인증 실패가 `auth_event`에 저장되는 것은 아닙니다.
현재 저장하지 않는 실패는 다음과 같습니다.

| 상황 | 저장하지 않는 이유 |
| --- | --- |
| refresh token hash에 해당하는 row가 없음 | account를 특정할 수 없고, 현재 writer가 없습니다. |
| refresh token 만료 | 현재 rotation usecase가 invalid token으로만 처리하고 이벤트를 남기지 않습니다. |
| refresh token 재발급 시 account 조회 실패 | 현재 invalid token으로만 처리하고 이벤트를 남기지 않습니다. |
| `password_change_required = true` account의 refresh token 재발급 시도 | 현재 invalid token으로만 처리하고 이벤트를 남기지 않습니다. |
| 비밀번호 변경 요청 validation 실패 | 요청 형식 오류로 처리하고 감사 이벤트를 남기지 않습니다. |
| 이메일 인증 요청 email 형식 오류 | 요청 형식 오류로 처리하고 감사 이벤트를 남기지 않습니다. |
| 이메일 인증 요청 대상 account 없음 | account enumeration 방지를 위해 public 응답만 접수 형태로 반환하고 감사 이벤트를 남기지 않습니다. |
| 이메일 인증 요청 대상 account가 `PENDING`이 아님 | 이미 활성화됐거나 인증 대상이 아니므로 상태 변화와 감사 이벤트를 만들지 않습니다. |
| 이메일 인증 요청 cooldown 중 재요청 | 기존 open token을 유지하고 새 발송과 감사 이벤트를 만들지 않습니다. |
| 이메일 인증 token 생성/저장 transaction 실패 | 발송이 실행되지 않으므로 인증 요청 감사 이벤트도 남기지 않습니다. |
| 이메일 인증 완료 Web request validation 실패 | controller가 application command 생성 전에 차단하므로 인증 완료 감사 이벤트를 남기지 않습니다. |
| 비밀번호 재설정 요청 email 형식 오류 | 요청 형식 오류로 처리하고 감사 이벤트를 남기지 않습니다. |
| 비밀번호 재설정 요청 대상 account 없음 | account enumeration 방지를 위해 public 응답만 접수 형태로 반환하고 감사 이벤트를 남기지 않습니다. |
| 비밀번호 재설정 요청 대상 account가 `ACTIVE` 또는 `PENDING`이 아님 | account 상태를 노출하지 않기 위해 token과 감사 이벤트를 만들지 않습니다. |
| 비밀번호 재설정 요청 cooldown 중 재요청 | 기존 open token을 유지하고 새 발송과 감사 이벤트를 만들지 않습니다. |
| 비밀번호 재설정 token 생성/저장 transaction 실패 | 발송이 실행되지 않으므로 reset 요청 감사 이벤트도 남기지 않습니다. |
| 비밀번호 재설정 완료 token 형식 오류, 미존재, 만료, 소비/폐기 상태 | password credential을 바꾸지 않고 감사 이벤트를 남기지 않습니다. |
| 최초 최고 관리자 bootstrap 입력 오류 | command 입력 오류로 처리하고 감사 이벤트를 남기지 않습니다. |
| 활성 최고 관리자가 이미 있어 bootstrap이 no-op으로 종료 | 계정 상태 변화가 없으므로 감사 이벤트를 남기지 않습니다. |

이 목록은 현재 구현의 저장 계약입니다.
새 저장 요구가 생기면 이 문서, DB constraint, persistence mapper, 테스트를 함께 갱신합니다.

## Transaction 경계

감사 이벤트는 아래 표의 흐름별 transaction 경계를 사용합니다. 대부분의 상태 변경 event는
관련 상태와 같은 transaction에 저장하지만, after-commit notification 결과와 실패 응답을
보존해야 하는 일부 event는 별도 `REQUIRES_NEW` transaction을 사용합니다. 따라서 모든
event가 상태 변경과 원자적이라고 일반화하지 않습니다.

물리 저장은 `securityaudit`만 수행합니다. `SecurityAuditAppender`는 caller transaction에
참여해 실패를 전파하고, `IndependentSecurityAuditAppender`는 `REQUIRES_NEW`로 기록한 뒤
저장 실패를 `FAILED` 결과와 구조화 로그로 분리합니다.

| 흐름 | transaction 기준 | rollback 기준 |
| --- | --- | --- |
| 일반 회원 자체 가입 | account, profile, password credential, role assignment, `ACCOUNT_SIGN_UP` 이벤트를 함께 저장합니다. | 하나라도 실패하면 전체 가입 저장이 rollback됩니다. |
| 최초 최고 관리자 bootstrap | `ROLE_SUPER_ADMIN` role row lock, account graph, `ROLE_CHANGE`, `PASSWORD_CHANGE_REQUIREMENT` 이벤트를 함께 저장합니다. | 중복, role seed 누락, 저장 실패 시 생성 결과와 이벤트가 함께 남지 않습니다. |
| 로그인 성공 | refresh token hash 저장, last login 갱신, credential 실패 상태 초기화, `PASSWORD_LOGIN/SUCCESS` 이벤트를 함께 저장합니다. | 저장 경계 안의 실패는 함께 rollback됩니다. |
| 로그인 실패 | credential 실패 상태 갱신과 `PASSWORD_LOGIN/FAILURE` 이벤트를 함께 저장합니다. | 저장 경계 안의 실패는 함께 rollback됩니다. |
| 이메일 인증 요청 | `account_verification_token` 저장 transaction이 commit된 뒤 메일 발송을 시도하고, 발송 성공/실패 결과를 `EMAIL_VERIFICATION_REQUEST` 이벤트로 별도 transaction에 저장합니다. | token 저장 transaction이 rollback되면 발송과 감사 이벤트가 실행되지 않습니다. 발송 실패는 token 저장 결과를 rollback하지 않습니다. |
| 이메일 인증 완료 성공 | account `ACTIVE` 전환, token 소비, `EMAIL_VERIFICATION_CONFIRM/SUCCESS` 이벤트를 함께 저장합니다. | 성공 저장 경계 안의 실패는 함께 rollback됩니다. |
| 이메일 인증 완료 실패 | token invalid, expired, already used, revoked, account not pending 실패를 `EMAIL_VERIFICATION_CONFIRM/FAILURE` 이벤트로 별도 transaction에 저장합니다. | 실패 응답을 위해 `ApiException`을 던져도 감사 이벤트는 rollback되지 않습니다. |
| 비밀번호 재설정 요청 | `account_verification_token`에 `PASSWORD_RESET` token을 저장한 transaction이 commit된 뒤 메일 발송을 시도하고, 발송 성공/실패 결과를 `PASSWORD_RESET_REQUESTED` 이벤트로 별도 transaction에 저장합니다. | token 저장 transaction이 rollback되면 발송과 감사 이벤트가 실행되지 않습니다. 발송 실패는 token 저장 결과를 rollback하지 않습니다. |
| 비밀번호 재설정 완료 | reset token 소비, password hash 갱신, `password_change_required = false`, active refresh token `PASSWORD_CHANGED` 폐기, `PASSWORD_RESET_COMPLETED/SUCCESS` 이벤트를 함께 저장합니다. | token 검증 실패, credential 저장 실패 시 완료 결과와 이벤트가 함께 남지 않습니다. |
| 비밀번호 변경 성공 | password hash 갱신, `password_change_required = false`, active refresh token `PASSWORD_CHANGED` 폐기, `PASSWORD_CHANGE/SUCCESS` 이벤트를 함께 저장합니다. | 저장 경계 안의 실패는 함께 rollback됩니다. |
| 비밀번호 변경 current password 불일치 | `PASSWORD_CHANGE/FAILURE/CURRENT_PASSWORD_MISMATCH` 이벤트를 남긴 뒤 API 오류를 반환합니다. | `ApiException`은 rollback 대상에서 제외해 실패 이벤트가 남습니다. |
| 관리자 초대 생성 | 같은 email의 기존 open 초대 회수, 새 `admin_invitation`과 `ADMIN_INVITATION_CREATED` 이벤트를 한 transaction에 저장하고 commit 이후 notification을 발송합니다. | 초대 또는 이벤트 저장이 실패하면 전체 저장이 rollback되고 notification을 발송하지 않습니다. commit 이후 provider 실패는 이미 저장된 초대와 이벤트를 바꾸지 않습니다. |
| 관리자 초대 수락 | 초대 token 검증 뒤 account graph 생성, 초대 accepted 상태 저장, `ADMIN_INVITATION_ACCEPTED` 이벤트 저장을 함께 처리합니다. | 저장 경계 안의 실패는 함께 rollback됩니다. |
| 관리자 초대 회수 | open 초대 revoked 상태 저장과 `ADMIN_INVITATION_REVOKED` 이벤트 저장을 함께 처리합니다. | 저장 경계 안의 실패는 함께 rollback됩니다. |
| refresh token rotation 성공 | 새 token 저장, 기존 token `ROTATED` 폐기, `TOKEN_REFRESH/SUCCESS` 이벤트를 함께 저장합니다. | 저장 경계 안의 실패는 함께 rollback됩니다. |
| refresh token reuse detection | family 활성 token `REUSE_DETECTED` 폐기, `TOKEN_REFRESH/FAILURE/TOKEN_REUSE_DETECTED` 이벤트를 함께 저장합니다. | `ApiException`은 rollback 대상에서 제외해 폐기 결과와 실패 이벤트가 남습니다. |
| refresh token account 상태 거부 | family 활성 token `ACCOUNT_SUSPENDED` 폐기, `TOKEN_REFRESH/FAILURE/ACCOUNT_SUSPENDED` 또는 `ACCOUNT_WITHDRAWN` 이벤트를 함께 저장합니다. | `ApiException`은 rollback 대상에서 제외해 폐기 결과와 실패 이벤트가 남습니다. |

관리자 초대 생성 이벤트는 초대 생성 사실만 소유하며 commit 이후 notification delivery
결과를 기록하지 않습니다. Delivery 결과를 장기 보존하려면 생성 이벤트와 다른 taxonomy·
저장·복구 계약이 필요합니다. 이메일 인증 요청과 비밀번호 재설정 요청의 독립 append는
저장 실패를 `FAILED`로 반환하므로 provider 결과를 바꾸지 않습니다. Audit gap 판정과 복구는
[Security Audit 장애 복구](../operations/550-security-audit-failure-recovery-runbook.md)가 소유합니다.

## Metadata 기준

metadata는 항상 JSON object여야 하며 event별 허용 key만 사용합니다.

현재 허용된 metadata key는 다음과 같습니다.

| event type | key | 값 |
| --- | --- | --- |
| `ROLE_CHANGE` | `roleCode` | `ROLE_SUPER_ADMIN` |
| `ROLE_CHANGE` | `actorType` | `SYSTEM` |
| `ROLE_CHANGE` | `action` | `GRANTED` |
| `PASSWORD_CHANGE_REQUIREMENT` | `actorType` | `SYSTEM` |
| `PASSWORD_CHANGE_REQUIREMENT` | `action` | `REQUIRED` |
| `PASSWORD_CHANGE` 성공 | `actorType` | `ACCOUNT` |
| `PASSWORD_CHANGE` 성공 | `action` | `CHANGED` |
| `ADMIN_INVITATION_CREATED` | `invitationPublicId` | 초대 public id |
| `ADMIN_INVITATION_CREATED` | `targetEmailHash` | 대상 email SHA-256 hash |
| `ADMIN_INVITATION_CREATED` | `roleCode` | `ROLE_ADMIN` |
| `ADMIN_INVITATION_CREATED` | `previousInvitationRevoked` | 기존 open 초대 회수 여부 |
| `ADMIN_INVITATION_ACCEPTED` | `invitationPublicId` | 초대 public id |
| `ADMIN_INVITATION_ACCEPTED` | `roleCode` | `ROLE_ADMIN` |
| `ADMIN_INVITATION_ACCEPTED` | `invitedByAccountPublicId` | 초대 생성 account public id |
| `ADMIN_INVITATION_REVOKED` | `invitationPublicId` | 초대 public id |
| `ADMIN_INVITATION_REVOKED` | `targetEmailHash` | 대상 email SHA-256 hash |
| `ADMIN_INVITATION_REVOKED` | `reasonCode` | `ADMIN_REVOKED` |

이메일 인증과 비밀번호 재설정 이벤트의 현재 metadata는 `{}`입니다.
provider message id, recipient email, raw token, token hash는 현재 감사 metadata에 저장하지 않습니다.

`ADMIN_INVITATION_ACCEPTED.invitedByAccountPublicId`는 수락으로 생성된 account가 event의
대상이고 초대 생성자가 별도 actor인 흐름을 내부 DB id 없이 결속합니다.

metadata에 새 key를 추가할 때는 다음 기준을 모두 만족해야 합니다.

- 운영자가 이벤트 의미를 해석하는 데 필요합니다.
- 원문 password, token, email, secret, Authorization header, Cookie가 아닙니다.
- account 내부 id, token hash처럼 불필요하게 민감한 내부 식별자가 아닙니다.
- key와 값 후보가 이 문서에 추가됩니다.
- 관련 persistence 테스트가 값을 고정합니다.

관리자 초대 생성·수락·회수 event의 metadata key와 값은 PostgreSQL 통합 test가 exact
object로 고정합니다. 생성 metadata에는 notification delivery 결과를 추가하지 않습니다.

## 새 이벤트 추가 기준

새 인증/인가 이벤트를 추가할 때는 다음 순서를 따릅니다.

1. 먼저 흐름이 새 `event_type`인지, 기존 `event_type`의 새 `reason_code`인지 판정합니다.
2. `event_type`은 인증/인가 흐름 이름만 사용합니다.
3. 결과는 `result`에만 둡니다.
4. 원인, 탐지 사유, 거부 사유는 `reason_code`에 둡니다.
5. `event_type`과 `reason_code`가 같은 의미를 반복하면 설계를 반려합니다.
6. metadata가 필요하면 event별 key, 값 후보, 금지 값을 이 문서에 먼저 추가합니다.
7. `auth_event.event_type` DB check constraint 확장 또는 변경이 필요하면 새 migration을 만듭니다.
8. persistence writer 또는 mapper를 추가합니다.
9. transaction 경계와 rollback 기준을 정의합니다.
10. 민감정보 미저장 검토를 수행합니다.
11. 성공/실패 저장 테스트와 schema migration 테스트를 추가합니다.
12. 이 문서와 관련 운영 문서를 갱신합니다.

역할 부여/회수, logout 실패 기록, 관리자 역할 변경 기능이 추가되면 기존 schema 허용 값만 믿지 않고 실제 저장 조건과 reason code를 별도로 정의합니다.

## 재검토 조건

- IP 주소나 User-Agent를 auth event에 저장하기로 결정합니다.
- 보안 분석을 위해 refresh token invalid 시도도 감사 이벤트로 남겨야 합니다.
- 관리자 활동 감사 로그가 auth event와 별도 table로 분리됩니다.
- 개인정보 보존 기간이나 감사 이벤트 보존 정책이 정해집니다.
- metadata key가 event별 허용 목록 없이 늘어나기 시작합니다.

## 관련 문서

- **상위 도메인·인증 정책**: [Account](100-account.md), [Account 인증/인가 정책](110-account-auth-policy.md)
- **현재 데이터 구조**: [Account 인증/인가 데이터 모델](../project/220-account-auth-data-model.md), [물리 schema](../project/schema/100-account-auth.md)
- **물리 조회 구조**: [Account/Auth schema](../project/schema/100-account-auth.md)
- **개발 절차**: [로컬 개발 환경](../development/100-local-development.md)
- **결정·구현 결과**: [Auth Event Taxonomy 재설계](../work-items/improvements/2026/001-auth-event-taxonomy-redesign.md)
