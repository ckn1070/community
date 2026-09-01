# Account 인증/인가 후속 기능 Backlog

## 상태

Open

## 목적

이 문서는 Account 인증/인가 기반에서 아직 작업 문서로 전환되지 않은 후보만 소유합니다.
완료된 기능의 현재 개념·정책·구조는 영구 SOT가 소유하고, 각 feature 문서는 작업 당시 결정
과정과 구현 결과만 보존합니다. 이 backlog에서 완료된 결과를 다시 결정 대기 상태로 표현하지
않습니다. 완료 뒤 원래 기준 미충족으로 다시 열린 작업은 아래 진행 중 분리 작업에서 상태만
연결합니다.

## 완료된 분리 작업

| 작업 | 완료 결과 |
| --- | --- |
| [001 Account 인증/인가 DB Schema](../../features/2026/001-account-auth-db-schema.md) | account, role, credential, refresh token과 auth event의 Flyway 기반을 확정했습니다. |
| [002 일반 회원 자체 가입](../../features/2026/002-member-self-sign-up-api.md) | `PENDING` account와 `ROLE_MEMBER`를 생성하는 public 가입 API를 구현했습니다. |
| [003 API 공통 기반](../../features/2026/003-api-common-foundation.md) | Problem Details 오류 계약과 기본 Security 경계를 구현했습니다. |
| [004 API 로깅 기반](../../features/2026/004-api-logging-foundation.md) | request id, MDC, structured logging과 민감정보 금지 기준을 구현했습니다. |
| [005 회원 로그인](../../features/2026/005-member-login-api.md) | password session, access token과 refresh token 최초 발급을 구현했습니다. |
| [006 인증 토큰 기반](../../features/2026/006-auth-token-foundation.md) | `auth` module, JWT 발급·검증과 Bearer 인증을 구현했습니다. |
| [008 이메일 인증](../../features/2026/008-member-email-verification.md) | 이메일 인증 요청·확인, Resend 연동과 `PENDING` → `ACTIVE` 전이를 구현했습니다. |
| [009 로그아웃](../../features/2026/009-auth-logout.md) | refresh token 단일 폐기 기반의 멱등 로그아웃을 구현했습니다. |
| [010 관리자 계정 기반](../../features/2026/010-admin-account-foundation.md) | 최고 관리자 bootstrap과 인증된 비밀번호 변경을 구현했습니다. |
| [012 비밀번호 재설정](../../features/2026/012-account-password-reset.md) | email reset token 기반 비로그인 복구와 session 폐기를 구현했습니다. |
| [007 OpenAPI 실제 응답 계약 동기화](../../improvements/2026/007-openapi-response-contract.md) | 실제 응답 schema·보안·공통 오류와 생성 OpenAPI 자동 검증을 구현했습니다. |

## 진행 중 분리 작업

| 작업 | 현재 미완료 |
| --- | --- |
| [007 Refresh Token 재발급](../../features/2026/007-refresh-token-reissue-api.md) | 만료된 교체 token reuse의 family 폐기·감사 보장을 보완합니다. |
| [011 관리자 초대](../../features/2026/011-admin-invitations.md) | invitation 생성 사실과 감사 event의 내구성 계약을 보완합니다. |

## 활성 Backlog

### 로그인 Abuse 대응

- account hard lock 도입 여부, 실패 횟수, 잠금 시간과 잠금 해제 정책을 결정합니다.
- IP와 identifier 기준 rate limit을 account 잠금보다 먼저 적용할지 결정합니다.
- captcha 또는 추가 이메일 확인을 반복 실패 대응에 연결할 조건을 정합니다.
- account 잠금 자체가 제3자의 서비스 거부 공격으로 악용되지 않도록 고객 지원과 복구 비용을 함께 검토합니다.

### Session과 Token 운영

- 전체 기기 로그아웃과 account 단위 전체 refresh token family 폐기 API가 필요한지 결정합니다.
- 사용자에게 session 또는 device 목록과 개별 session 폐기 기능을 제공할지 결정합니다.
- refresh token을 response body에서 HttpOnly cookie로 옮길 조건과 그에 따른 CORS·CSRF 정책을 결정합니다.
- 만료·폐기된 refresh token과 verification token의 정리 주기와 batch 실행 방식을 결정합니다.
- account 정지·탈퇴와 관리자 강제 조치가 기존 session을 언제, 어느 범위까지 폐기하는지 결정합니다.
- access token blacklist는 짧은 JWT 만료로 감당할 수 없는 즉시 차단 요구가 생길 때만 재검토합니다.

### 관리자 Role과 복구

- 기존 account의 관리자 role 부여·회수 API, 권한 주체와 감사 이벤트를 결정합니다.
- `ROLE_SUPER_ADMIN` 추가와 회수에 필요한 다중 승인 또는 비상 복구 정책을 결정합니다.
- 모든 최고 관리자가 접근 불능일 때 사용할 emergency recovery 절차 또는 별도 command를 설계합니다.
- 관리자 초대 목록·상세 조회와 별도 재발송 API의 필요성을 결정합니다.

### 생성 Resource URI 정책

- 회원가입, 관리자 초대 생성과 관리자 초대 수락이 반환하는 `Location`을 생성 resource의 식별자로 사용할지, 실제 조회 API까지 제공하는 project 정책으로 강화할지 결정합니다.
- `/members/{memberId}`, `/admin/invitations/{invitationId}`, `/accounts/{accountId}`가 현재 같은 URI의 `GET` 지원 여부와 별개로 각각 어떤 resource identity를 뜻하는지 일관되게 정의합니다.
- 조회 API를 추가한다면 권한, 개인정보 field와 존재 여부 노출 정책을 별도 기능으로 설계합니다.

### Auth Event 운영

- auth event 보존 기간, 정리 주기와 저장 비용 기준을 결정합니다.
- 운영 조회·필터링 API 또는 관리자 화면의 필요성을 결정합니다.
- IP, User-Agent와 추가 metadata를 저장할 때 개인정보 최소화와 보존 기간을 함께 결정합니다.
- 보안 알림으로 연결할 event와 threshold를 결정하되 일반 애플리케이션 로그와 감사 event의 책임을 섞지 않습니다.
- 중앙 log·event 수집, infrastructure alert 전달과 on-call platform은
  [004 Infrastructure 진화 Backlog](004-infrastructure-evolution.md)가 소유합니다.

### Email과 Account Identity 변경

- email 변경 요청, 새 email 검증, 기존 session 유지와 감사 event 정책을 결정합니다.
- display name 변경과 중복 정책이 현재 가입 규칙을 그대로 재사용할지 결정합니다.
- email 또는 인증 수단 변경 시 기존 verification token과 refresh token을 폐기할 범위를 결정합니다.

### 약관과 개인정보 동의

- 약관과 개인정보 처리방침 version을 저장할지 결정합니다.
- account별 동의 이력, 필수·선택 동의와 재동의 정책을 결정합니다.
- 공개 서비스 전 필요한 법적 고지와 보존 기간을 제품·운영 정책과 함께 확정합니다.

### 탈퇴와 익명화

- 탈퇴 account의 개인정보 익명화 범위를 결정합니다.
- 게시글, 댓글과 신고 이력의 보존 또는 익명화 정책을 결정합니다.
- 탈퇴 후 재가입, email·display name 재사용과 DB unique 전략을 함께 결정합니다.
- 법적 보존이 필요한 데이터와 즉시 삭제해야 하는 데이터를 구분합니다.

### OAuth, MFA와 세부 Permission

- OAuth provider identity와 password credential이 공존하는 account 연결 모델을 결정합니다.
- MFA 수단, 복구 code와 account recovery 흐름을 결정합니다.
- role만으로 권한을 표현하기 어려워지는 시점과 permission model 도입 조건을 정합니다.

## 전환 기준

- 활성 항목을 구현하기 시작하면 하나의 명확한 목표와 완료 조건을 가진 feature, improvement 또는 spike 문서로 옮깁니다.
- 새 작업 문서는 배경, 범위, 결정, 수용 기준, 개발·검증 계획과 영구 SOT 영향을 다시 확인합니다.
- 전환한 항목은 이 문서에서 제거하고 완료 결과 표 또는 생성된 작업 문서 index로 연결합니다.
- 폐기한 항목은 장기적으로 다시 검토할 이유가 있을 때만 짧은 폐기 근거와 재검토 조건을 남깁니다.
- 활성 항목이 모두 전환되거나 폐기되면 상태를 `Closed`로 바꿉니다.

## 관련 문서

- **업무 범위**: [Account domain](../../../domain/100-account.md), [Account 인증 정책](../../../domain/110-account-auth-policy.md)
- **현재 데이터 구조**: [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md), [물리 Schema Reference](../../../project/schema/100-account-auth.md)
- **Persistence owner 전환 작업**: [009 비카탈로그 DDD 보완](../../improvements/2026/009-non-catalog-ddd-hardening.md)
