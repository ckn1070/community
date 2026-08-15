# 관리자 계정 기반 상세 설계

## 문서 내 진입점

| 확인할 내용 | 위치 |
| --- | --- |
| 결정별 선택 이유 | [설계 결정 근거](#설계-결정-근거) |
| bootstrap·보안·transaction 계약 | [상세 설계](#상세-설계) |
| 최종 기준 | [확정된 설계](#확정된-설계) |

## 배경

현재 account 모델은 일반 회원과 관리자를 같은 `account`로 다루고, `ROLE_MEMBER`, `ROLE_ADMIN`, `ROLE_SUPER_ADMIN` 기준 역할을 이미 갖고 있습니다.
로그인과 refresh token은 role claim을 발급하고 Spring Security authority로 변환할 수 있습니다.

이 작업 시작 전에는 실제 최초 `ROLE_SUPER_ADMIN` 계정을 안전하게 생성하는 기능이 없었습니다.
초기 최고 관리자 계정이 없으면 관리자 초대, 관리자 역할 부여, 관리자 CMS, 신고 처리 같은 운영 기능의 시작점이 없습니다.

## 설계 결정 근거

### 전체 방향

초기 최고 관리자는 공개 HTTP endpoint가 아니라 운영자가 실행하는 bootstrap 흐름으로 생성합니다.
생성된 계정은 `ROLE_SUPER_ADMIN`을 받고, 비밀번호는 최초 로그인 후 반드시 변경해야 합니다.
관리자 API는 일반 보호 API보다 강한 role authorization을 요구합니다.

이 방향은 기존 결정과 맞습니다.
관리자도 일반 account이며, 실제 관리자 비밀번호를 Flyway migration에 seed하지 않습니다.

### 결정 1. 초기 최고 관리자 생성 방식

- 선택: 공개 HTTP endpoint 없이 운영 command로 bootstrap하고, 활성 `ROLE_SUPER_ADMIN`이 없을 때만 1명을 생성합니다. 같은 command를 다시 실행해도 중복 생성하지 않습니다.
- 선택 근거: 초기 최고 관리자가 없는 시점에 공개 endpoint를 열거나 SQL로 도메인 규칙을 우회하지 않으면서 기존 password encoder와 transaction을 재사용할 수 있습니다.
- 결정 범위: bootstrap 흐름의 외부 진입 방식을 운영 command로 정하고, 기본 재실행 정책은 idempotent guard로 둡니다.
- 선택 이유:
  - 공개 HTTP endpoint는 보호할 최고 관리자 계정이 없는 시점에 열리므로 공격 표면이 큽니다.
  - 수동 DB 작업은 password hashing, role assignment, auth event, transaction 정책을 우회하기 쉽습니다.
  - Flyway seed는 비밀번호가 Git에 남을 위험 때문에 기존 문서에서 제외되어 있습니다.
- command 방식의 의미:
  - 이 command는 사용자가 웹 API로 호출하는 기능이 아니라 운영자가 배포된 application artifact를 일회성 모드로 실행하는 운영 진입점입니다.
  - command 실행 시 Spring application context를 띄워 기존 `PasswordEncoder`, transaction, JPA repository, account 저장 경계, auth event 저장 경계를 그대로 사용합니다.
  - command는 필요한 입력을 받고 bootstrap usecase를 실행한 뒤 결과를 출력하고 프로세스를 종료합니다. 상시 실행되는 관리자 API endpoint를 추가하지 않습니다.
- 구체 플로우:
  1. 운영자가 DB migration이 적용된 환경에서 bootstrap command를 실행합니다.
  2. command는 email, display name 같은 비밀이 아닌 입력을 option 또는 prompt로 받습니다.
  3. 초기 비밀번호는 command argument나 환경변수로 받지 않고 masked console 입력과 재입력 확인으로만 받습니다.
  4. command는 현재 활성 `ROLE_SUPER_ADMIN` role assignment가 존재하는지 먼저 확인합니다.
  5. 활성 `ROLE_SUPER_ADMIN`이 없으면 account, profile, password credential, `ROLE_SUPER_ADMIN` role assignment, 감사 이벤트를 하나의 transaction으로 저장합니다.
  6. 저장되는 password credential은 `password_change_required = true`로 둡니다.
  7. 성공하면 생성된 account public id와 후속 조치만 출력하고, 비밀번호 원문과 password hash는 출력하지 않습니다.
  8. 실패하면 어떤 단계에서 실패했는지 운영자가 판단할 수 있는 오류 code만 출력하고, 비밀번호 원문, hash, token, 원문 email을 로그나 감사 metadata에 남기지 않습니다.
- 여러 번 실행 정책:
  - command 파일이나 binary 자체는 여러 번 실행할 수 있습니다.
  - 기본 동작은 idempotent guard입니다. 이미 활성 `ROLE_SUPER_ADMIN`이 있으면 새 관리자 계정을 만들지 않고 `ALREADY_BOOTSTRAPPED` 성격의 결과로 종료합니다.
  - 같은 email로 다시 실행해도 두 번째 계정을 만들지 않습니다.
  - 여러 명의 최고 관리자나 일반 관리자를 만들고 싶다면 bootstrap command 반복이 아니라 후속 관리자 초대 또는 역할 부여 기능으로 처리합니다.
  - 모든 최고 관리자가 사라졌거나 접근 불능인 emergency recovery는 기본 bootstrap과 분리해 별도 command 또는 운영 절차로 설계합니다.
- 선택하지 않는 대안:
  - 공개 HTTP endpoint: 초기 편의성은 높지만 bootstrap secret, 재실행 방지, rate limit, 로그 민감정보까지 새로 설계해야 합니다.
  - Flyway seed: 비밀번호와 운영 계정 정보가 migration에 남을 수 있습니다.
  - 수동 SQL: 도메인 규칙, password encoder, 감사 이벤트를 우회할 위험이 큽니다.

### 결정 2. 초기 비밀번호 전달 방식

- 선택: 비밀번호는 command argument나 환경변수 값으로 받지 않고, command 실행 중 interactive 입력으로 받습니다.
- 선택 근거:
  - interactive 입력은 운영자가 bootstrap command를 실행한 뒤 터미널 prompt에서 비밀번호를 직접 입력하는 방식입니다.
  - 입력한 비밀번호는 command argument처럼 shell history에 남지 않고, `.env`나 migration에도 저장하지 않습니다.
  - 현재 범위는 운영자의 일회성 수동 bootstrap이므로 secret manager 자동 주입은 기본안에서 제외합니다.
- 결정 범위: 초기 비밀번호 원문은 interactive 입력으로만 받고, migration, `.env.example`, 로그, 감사 metadata에 남기지 않습니다.
- 선택 이유:
  - `.env.example`에는 값이나 placeholder를 쓰지 않는 저장소 규칙이 있습니다.
  - 환경변수는 프로세스/배포 로그에 노출될 수 있어 장기 secret 보관에 적합하지 않을 수 있습니다.
  - 실행 시 입력은 Git과 DB migration에 비밀번호가 남는 경로를 차단합니다.
- 선택하지 않는 대안:
  - `.env` 장기 보관: 편하지만 로컬/배포 환경에서 secret 확산 위험이 큽니다.
  - command argument 전달: shell history와 process list에 남을 수 있습니다.
  - secret manager 단기 주입: 자동화 배포에는 맞지만, 현재 방향은 운영자가 실행 중 직접 입력하는 방식으로 둡니다.
  - migration literal: 저장소에 비밀번호가 남을 수 있습니다.

### 결정 3. 강제 비밀번호 변경 범위

- 선택: `password_change_required = true` 계정은 로그인할 수 있지만, 비밀번호 변경 API를 제외한 일반·관리자 보호 API 접근은 비밀번호 변경 전 차단합니다. 완료 당시와 이후 추가된 `permitAll` endpoint는 이 authority 차단과 별개이며, 현재 전체 allow-list는 [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)이 소유합니다.
- 선택 근거: 비밀번호 변경용 인증 수단은 유지하면서 임시 비밀번호 상태의 업무·관리자 API 사용을 차단할 수 있습니다.
- 결정 범위: 비밀번호 변경 전에는 비밀번호 변경 API만 인증이 필요한 보호 API에서 허용하고, 관리자 API를 포함한 나머지 일반·관리자 보호 API는 차단합니다. public allow-list는 별도입니다.
- 선택 이유:
  - 로그인 자체를 막으면 사용자가 비밀번호 변경 API를 호출할 인증 수단이 없습니다.
  - full session을 주되 관리자 API에서 추가 차단하면 기존 로그인/refresh token 구조를 크게 바꾸지 않습니다.
  - 관리자 기능 사용 전에 임시 비밀번호 제거를 강제할 수 있습니다.
- 선택하지 않는 대안:
  - 로그인 자체 차단: 비밀번호 변경 플로우가 별도 인증 토큰을 요구해 범위가 커집니다.
  - 관리자 API 허용: 임시 비밀번호 상태에서 운영 기능이 노출됩니다.
  - limited session 재사용: 현재 limited session은 `PENDING` 계정의 이메일 인증 보조 기능을 위한 의미라 관리자 초기 비밀번호와 섞으면 세션 의미가 흐려집니다.

### 결정 4. 관리자 API 권한 기준

- 선택: `/api/v1/admin/**`는 `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`와 `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN`을 요구하고, 최고 관리자 전용 API는 `ROLE_SUPER_ADMIN`을 추가로 요구합니다.
- 선택 근거: 관리자 영역을 `/api/v1/admin/**`로 묶으면 Security matcher와 API 탐색 경계가 명확하고, 최고 관리자 전용 기능만 더 강한 role로 제한할 수 있습니다.
- 결정 범위: 관리자 API의 공통 authorization 기준과 최고 관리자 전용 기준을 정합니다.
- 관용 후보:
  - `/api/v1/admin/**`: 관리자 영역을 prefix로 묶는 일반적인 형태입니다.
  - `/api/v1/backoffice/**`: 내부 운영 도구 느낌이 강하지만 현재 제품 문서의 관리자 기능 표현과는 덜 맞습니다.
- 프로젝트 일관성 후보:
  - 기존 public API는 `/api/v1/members`, `/api/v1/auth/sessions`처럼 소비자 리소스를 path에 둡니다.
  - 관리자 기능은 소비자 리소스라기보다 운영 영역이므로 prefix 분리가 명확합니다.
- 선택한 후보와 이유:
  - `/api/v1/admin/**`: 관용적이고, 보안 설정에서 관리자 경계를 한눈에 볼 수 있습니다.
- 제외 후보와 제외 이유:
  - `/api/v1/members/{memberId}/role-assignments`: 역할 관리 API에는 맞지만 관리자 CMS와 신고 처리까지 포괄하지 못합니다.
  - `/api/v1/super-admin/**`: 최고 관리자 전용 기능이 늘 때만 세부 path로 고려합니다.
- 관용성 판정: 관용적입니다.
- 비주류 여부: 비주류가 아닙니다.

### 결정 5. 감사 이벤트 범위

- 선택: 완료 당시 bootstrap 성공은 `ROLE_GRANTED`와 `PASSWORD_RESET_REQUIRED`, 비밀번호 변경 완료는 `PASSWORD_CHANGED`로 남겼습니다.
- 선택 근거: 기존 역할·비밀번호 event 조합으로 운영 추적에 필요한 사실을 표현할 수 있어 새 taxonomy와 migration을 추가하지 않습니다.
- 결정 범위: 기존 `auth_event.event_type` 후보 안에서 관리자 bootstrap과 비밀번호 변경 이력을 남깁니다.
- 선택 이유:
  - 최초 관리자 생성은 계정 생성, 역할 부여, 임시 비밀번호 설정이 결합된 보안 이벤트입니다.
  - 완료 당시 schema 후보에 `ROLE_GRANTED`, `PASSWORD_RESET_REQUIRED`, `PASSWORD_CHANGED`가 있어 새 event type을 늘리지 않았습니다.
  - 비밀번호 원문, token 원문, email 원문은 metadata에 남기지 않는 기존 기준을 유지합니다.
- 선택하지 않는 대안:
  - `ADMIN_BOOTSTRAPPED` 새 event type 추가: 의미는 명확하지만 migration과 event taxonomy 확장이 필요합니다.
  - 이벤트 미기록: 최초 최고 관리자 생성은 운영 감사에서 가장 중요한 이벤트라 추적성이 부족합니다.

## 상세 설계

아래 내용은 앞선 선택 근거를 command, transaction, API와 module 계약으로 구체화한 구현 계약입니다.

### D1. Bootstrap command contract

- 선택: 운영 command는 `bootstrap-super-admin` 성격의 단일 command로 둡니다.
- 실행 형태:
  - 운영자는 배포된 application artifact를 command 모드로 실행합니다.
  - command 식별자는 비밀값이 아니므로 CLI option으로 전달할 수 있습니다.
  - email과 display name은 option으로 받을 수 있고, option이 없으면 prompt로 받습니다.
  - 초기 비밀번호는 option, 환경변수, 설정 파일, migration으로 받지 않습니다.
- 입력 계약:
  - `--command=bootstrap-super-admin`
  - `--admin-email=<email>` 또는 prompt 입력
  - `--admin-display-name=<displayName>` 또는 prompt 입력
  - password와 password confirmation은 masked interactive prompt로만 입력
- 출력 계약:
  - 성공 시 account public id, 결과 code, 다음 조치만 출력합니다.
  - email 원문, password 원문, password hash, token 원문은 출력하지 않습니다.
  - 이미 활성 `ROLE_SUPER_ADMIN`이 있으면 새 account를 만들지 않고 `ALREADY_BOOTSTRAPPED` 결과를 출력합니다.
- 결과 code:
  - `CREATED`: 최초 최고 관리자 생성 완료
  - `ALREADY_BOOTSTRAPPED`: 활성 최고 관리자가 이미 있어 생성하지 않음
  - `INVALID_INPUT`: email, display name, password 정책 위반
  - `ROLE_NOT_CONFIGURED`: `ROLE_SUPER_ADMIN` seed 누락
  - `CONFLICT`: email 또는 display name unique constraint 충돌
  - `FAILED`: 분류하지 않은 내부 실패
- exit code:
  - `0`: `CREATED`, `ALREADY_BOOTSTRAPPED`
  - `2`: `INVALID_INPUT`
  - `3`: `ROLE_NOT_CONFIGURED`
  - `4`: `CONFLICT`
  - `1`: `FAILED`
- 선택하지 않는 대안:
  - 공개 HTTP bootstrap endpoint: bootstrap 전 보호 경계가 약합니다.
  - Flyway seed: 운영 비밀번호가 저장소에 남을 수 있습니다.
  - 수동 SQL: password encoder, aggregate 규칙, 감사 이벤트를 우회합니다.
  - 같은 command 반복으로 여러 관리자 생성: bootstrap과 관리자 초대/역할 부여의 책임이 섞입니다.

### D2. Interactive 비밀번호 입력

- 선택: 초기 비밀번호는 `System.console().readPassword` 같은 masked console 입력으로 받고, 한 번 더 입력해 일치 여부를 확인합니다.
- 입력 규칙:
  - password와 confirmation이 다르면 저장하지 않고 `INVALID_INPUT`으로 종료합니다.
  - `PasswordPolicy`를 통과한 뒤에만 hash를 생성합니다.
  - 입력받은 `char[]`는 hash 직후 best-effort로 초기화합니다.
- console이 없는 경우:
  - 첫 구현에서는 stdin fallback을 허용하지 않고 `CONSOLE_REQUIRED` 성격의 `INVALID_INPUT`으로 종료합니다.
  - 이유는 stdin fallback이 CI 로그, shell redirection, process supervision 로그에 secret을 남길 수 있기 때문입니다.
- 비기록 기준:
  - password 원문은 log, stdout, stderr, auth_event metadata, exception message에 남기지 않습니다.
  - password hash도 stdout, auth_event metadata, API 응답에 남기지 않습니다.
- 후속 확장:
  - 자동화 bootstrap이 필요해지면 secret manager 또는 one-shot file descriptor 방식의 별도 작업으로 분리합니다.
  - 이 후속 확장은 현재 interactive bootstrap 범위와 별개입니다.

### D3. Module, port, adapter 경계

- 선택: 최초 관리자 생성은 account 소유의 usecase로 두고, command 실행은 account inbound adapter로 둡니다.
- 권장 위치:
  - command adapter: `account/internal/adapter/in/command`
  - usecase port: `account/internal/application/port/in/BootstrapSuperAdminAccountUseCase`
  - input command: `account/internal/application/command/BootstrapSuperAdminAccountCommand`
  - result model: `account/internal/application/result/BootstrapSuperAdminAccountResult`
  - store port: `account/internal/application/port/out/SuperAdminBootstrapStorePort`
- 책임 분리:
  - command adapter는 CLI argument, prompt, masking, exit code 변환만 담당합니다.
  - application service는 입력 검증, role seed 확인, idempotency 판단, password hashing, aggregate 생성 요청을 조율합니다.
  - persistence adapter는 account/profile/password credential/role assignment/auth_event 저장 묶음과 DB lock을 담당합니다.
  - domain은 Spring, JPA, CLI, Security API를 참조하지 않습니다.
- auth 모듈 영향:
  - 로그인과 token 발급은 auth 모듈 기존 책임을 유지합니다.
  - 다만 `password_change_required` 차단을 위해 account 공개 조회 모델과 JWT claim은 확장합니다.
- 선택하지 않는 대안:
  - auth 모듈에 bootstrap usecase 배치: password login 흐름과 account 생성 책임이 섞입니다.
  - config 패키지에 command 구현 집중: 운영 진입점과 도메인 저장 경계가 섞입니다.
  - 기존 `MemberAccountStorePort` 재사용: 회원 가입 저장 의미와 최고 관리자 bootstrap 의미가 달라집니다.

### D4. Transaction, idempotency, rollback

- 선택: command 한 번은 하나의 transaction 안에서 완료하거나 rollback합니다.
- 동시 실행 방지:
  - persistence adapter가 `ROLE_SUPER_ADMIN`의 `auth_role` row를 `SELECT ... FOR UPDATE` 성격으로 잠급니다.
  - 같은 transaction 안에서 활성 `ROLE_SUPER_ADMIN` role assignment 존재 여부를 다시 확인합니다.
  - 존재하면 저장하지 않고 `ALREADY_BOOTSTRAPPED`로 반환합니다.
- 저장 묶음:
  - account
  - account_profile
  - account_password_credential with `password_change_required = true`
  - account_role_assignment with `ROLE_SUPER_ADMIN`
  - auth_event `ROLE_GRANTED` / `SUCCESS`
  - auth_event `PASSWORD_RESET_REQUIRED` / `SUCCESS`
- rollback 기준:
  - 위 저장 중 하나라도 실패하면 전체 transaction을 rollback합니다.
  - email/display name unique constraint 충돌은 `CONFLICT`로 변환합니다.
  - role seed 누락은 저장 전에 `ROLE_NOT_CONFIGURED`로 종료합니다.
- emergency recovery:
  - 활성 최고 관리자가 이미 있으면 접근 불능 여부를 command가 판단하지 않습니다.
  - 접근 불능 최고 관리자 복구는 별도 emergency recovery 작업으로 분리합니다.

### D5. 비밀번호 변경 API와 차단 정책

- 선택: 비밀번호 변경 API는 `PUT /api/v1/auth/password`로 둡니다.
- 요청 계약:
  - `currentPassword`
  - `newPassword`
  - `newPasswordConfirmation`
- 응답 계약:
  - 성공 시 `204 No Content`
  - 변경 후 기존 refresh token family는 `PASSWORD_CHANGED` reason으로 폐기합니다.
  - 사용자는 새 비밀번호로 다시 로그인해 새 access/refresh token을 받습니다.
- 차단 정책:
  - 로그인은 허용하고 access token을 발급합니다.
  - access token에는 `password_change_required` claim을 넣습니다.
  - JWT authority 변환 시 `PASSWORD_CHANGE_REQUIRED` 또는 `PASSWORD_CHANGE_NOT_REQUIRED` authority를 부여합니다.
  - `PASSWORD_CHANGE_REQUIRED` 계정의 Bearer authority로는 `PUT /api/v1/auth/password`만 보호 API에서 허용합니다. public allow-list는 별도 matcher 계약입니다.
  - refresh token 재발급은 `password_change_required` 상태에서 거부합니다. access token이 만료되면 다시 로그인한 뒤 비밀번호 변경을 진행합니다.
- password 변경 처리:
  - current password가 기존 hash와 일치해야 합니다.
  - new password는 `PasswordPolicy`를 통과해야 합니다.
  - new password와 confirmation이 다르면 `INVALID_REQUEST`로 거부합니다.
  - 성공하면 새 password hash 저장, `password_change_required = false`, refresh token family `PASSWORD_CHANGED` 폐기, `PASSWORD_CHANGED` 감사 이벤트 저장을 하나의 transaction으로 처리합니다.
- 선택하지 않는 대안:
  - 로그인 자체 차단: 비밀번호 변경 API 호출에 필요한 인증 수단이 없어집니다.
  - limited session 재사용: 기존 `SESSION_LIMITED`는 `PENDING` 계정용 의미라 초기 관리자 비밀번호 변경과 섞입니다.
  - 변경 성공 후 새 token 즉시 발급: 구현은 편할 수 있지만 token rotation, refresh token 폐기, 응답 계약이 커집니다.

### D6. 관리자 API authorization과 감사 이벤트

- 선택: 관리자 URL 권한은 `SecurityFilterChain`에서 먼저 차단하고, 최고 관리자 전용 세부 행위는 method security 또는 application policy로 좁힙니다.
- URL authorization:
  - `PUT /api/v1/auth/password`: `SESSION_FULL`과 인증된 principal 필요. `PASSWORD_CHANGE_REQUIRED`도 허용합니다.
  - `/api/v1/admin/**`: `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`, `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN` 필요
  - 선행 public allow-list와 위 비밀번호 변경·관리자 matcher에 해당하지 않는 인증 필요 `/api/**`: `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED` 필요
- 최고 관리자 전용 기준:
  - 관리자 초대, 관리자 역할 부여/회수, 최고 관리자 전용 운영 API는 `ROLE_SUPER_ADMIN`을 method security 또는 application policy에서 다시 요구합니다.
  - 이번 기반 작업에서는 최고 관리자 전용 실제 API가 없으면 보안 기준과 테스트 fixture까지만 둡니다.
- 감사 이벤트:
  - bootstrap 성공 role 부여: `ROLE_GRANTED` / `SUCCESS`, reason `SUPER_ADMIN_BOOTSTRAP`
  - bootstrap 성공 강제 변경 설정: `PASSWORD_RESET_REQUIRED` / `SUCCESS`, reason `INITIAL_ADMIN_BOOTSTRAP`
  - password 변경 성공: `PASSWORD_CHANGED` / `SUCCESS`, reason `REQUIRED_PASSWORD_CHANGED`
  - current password 불일치: `PASSWORD_CHANGED` / `FAILURE`, reason `CURRENT_PASSWORD_MISMATCH`
  - metadata에는 role code, actor type, result code처럼 민감하지 않은 값만 둡니다.
  - password 원문, password hash, token 원문, email 원문은 metadata에 넣지 않습니다.
## 확정된 설계

- 초기 최고 관리자는 공개 HTTP endpoint나 Flyway seed가 아니라 운영 command로 생성합니다.
- command는 `bootstrap-super-admin` 성격의 단일 command로 두고, email/display name은 option 또는 prompt로 받습니다.
- 초기 비밀번호는 masked interactive prompt와 재입력 확인으로만 받고, console이 없으면 실패시킵니다.
- bootstrap은 `ROLE_SUPER_ADMIN` role row를 transaction 안에서 잠근 뒤 활성 최고 관리자 존재 여부를 확인합니다.
- 활성 최고 관리자가 이미 있으면 새 account를 만들지 않고 `ALREADY_BOOTSTRAPPED`로 종료합니다.
- 최초 생성 시 account, profile, password credential, `ROLE_SUPER_ADMIN` role assignment, `ROLE_GRANTED`, `PASSWORD_RESET_REQUIRED` 감사 이벤트를 하나의 transaction으로 저장합니다.
- 초기 password credential은 `password_change_required = true`로 저장합니다.
- 비밀번호 변경 API는 `PUT /api/v1/auth/password`로 둡니다.
- `password_change_required` 계정은 로그인할 수 있지만 `PUT /api/v1/auth/password` 외 일반·관리자 보호 API 접근은 차단합니다. public allow-list는 별도입니다.
- 비밀번호 변경 성공 시 새 password hash 저장, `password_change_required = false`, refresh token family `PASSWORD_CHANGED` 폐기, `PASSWORD_CHANGED` 감사 이벤트 저장을 하나의 transaction으로 처리합니다.
- `/api/v1/admin/**`는 `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`, `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN`을 요구합니다.
- 최고 관리자 전용 API는 `ROLE_SUPER_ADMIN`을 method security 또는 application policy에서 추가 요구합니다.
- password 원문, password hash, token 원문, email 원문은 stdout, stderr, log, exception message, auth_event metadata에 남기지 않습니다.

위 event type과 reason은 이 작업 완료 당시 계약입니다. 후속 [Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md)가 role 변경을 `ROLE_CHANGE`, 강제 변경 상태를 `PASSWORD_CHANGE_REQUIREMENT`, 비밀번호 변경을 `PASSWORD_CHANGE`로 대체했습니다. 현재 저장 taxonomy는 [Auth Event 정책](../../../domain/120-auth-event-policy.md)이 소유하며, refresh token 폐기 reason `PASSWORD_CHANGED`와 auth event type은 서로 다른 계약입니다.

## 관련 문서

- **선행 account·auth 결과**: [DB Schema](001-account-auth-db-schema.md), [자체 가입](002-member-self-sign-up-api.md), [회원 로그인](005-member-login-api.md), [인증 토큰 기반](006-auth-token-foundation.md), [Refresh Token 재발급](007-refresh-token-reissue-api.md)
- **후속 Account 범위**: [Account Backlog](../../backlog/2026/003-account-auth-follow-up.md)
- **업무·데이터 기준**: [Account domain](../../../domain/100-account.md), [Account 데이터 모델](../../../project/220-account-auth-data-model.md)
- **후속 taxonomy 대체**: [Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md), [현재 Auth Event 정책](../../../domain/120-auth-event-policy.md)
