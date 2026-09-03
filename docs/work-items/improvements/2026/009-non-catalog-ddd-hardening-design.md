# 비카탈로그 DDD 보완 설계

## 배경

현재 코드는 업무 package와 공개 API, Hexagonal 경계를 갖추고 있지만 `refresh_token`과
`auth_event`를 `account`와 `auth`가 함께 씁니다. 또한 verification, password reset,
admin invitation의 상태 전이 일부가 application service와 JPA adapter에 흩어져 있습니다.
이 설계는 카탈로그를 제외한 현재 module의 업무 소유권과 전술 모델을 정리합니다.

카탈로그는 현재 구현 평가와 보완 범위에서 제외합니다. 새 module을 포함한 이후 모든
변경에는 [DDD 판정 gate](../../../workflow/110-design-workflow.md#설계-입력)를 그대로
적용합니다.

## 목표 경계

### Subdomain과 Context 후보

제품 문서는 인증·계정 복구·관리자 운영을 자동차 정보와 커뮤니티를 지원하는 기반으로
분류합니다. 따라서 이 범위는 제품 차별화 Core Domain으로 가정하지 않고 generic·supporting
성격의 기반으로 다룹니다. 자동차·커뮤니티의 Core Domain 판정은 이 작업에서 선점하지
않습니다.

| 경계 | 분류 | 소유하는 언어·상태 | 소유하지 않는 것 |
| --- | --- | --- | --- |
| `account` | 업무 Context 후보 | Account, Profile, Credential, Role Assignment, Verification, Password Reset, Admin Invitation | JWT 발급, refresh session row, 감사 저장 구현 |
| `auth` | 업무 Context 후보 | Password Authentication, Session Access, Access Token, Refresh Session, rotation·reuse·logout | account 상태·credential 변경, 감사 저장 구현 |
| `securityaudit` | Supporting Subdomain의 Bounded Context 후보 | Security Audit Record, event taxonomy, append 방식·보존 경계 | account·session 상태 변경, 일반 application log |
| `notification` | 기술 통합 경계 | 발송 요청, provider 호출·오류 변환 | verification·reset·invitation 정책과 상태 |
| `shared` | 공통 계약 경계 | 안정된 공통 오류 계약 | 업무 규칙과 상태 |
| `config` | composition 경계 | Spring 전역 조립 | 업무 규칙과 상태 |

`account`와 `auth`는 package 이름만으로 Bounded Context 확정을 선언하지 않습니다. 이
작업은 언어·상태·writer를 위 목표에 맞추고, 구현 결과를 근거로 Context 후보를 다시
판정합니다. 팀 관계 근거가 없으므로 Customer/Supplier 같은 조직 협력 패턴 이름도
추측하지 않습니다.

### 목표 협력

```text
auth ── account.api 조회 ──> account
account ── credential 변경 사실 ──> auth의 session 폐기 listener
account ── 감사 append ──> securityaudit
auth ── 감사 append ──> securityaudit
account ── 발송 요청 ──> notification
```

- `auth`는 account public id와 공개 snapshot만 사용합니다.
- `account`는 `refresh_token` repository를 갖지 않습니다.
- `securityaudit`만 `auth_event`를 JPA mapping하고 씁니다.
- module 간 전달에는 public id와 공개 DTO·event를 사용하며 내부 DB id를 공개하지 않습니다.
- `securityaudit.api.SecurityAuditAppender`는 caller transaction에 `REQUIRED`로 참여해 상태
  변경과 함께 rollback되는 기록만 소유합니다.
- `securityaudit.api.IndependentSecurityAuditAppender`는 `REQUIRES_NEW`로 요청 결과를 독립
  보존하며 audit 저장 실패를 provider·업무 실패로 재분류하지 않습니다. 두 계약은 호출
  lifecycle과 예외 전파가 다르므로 하나의 method나 adapter로 합치지 않습니다.
- `SecurityAuditAppender` 실패는 caller에 전파해 함께 rollback합니다.
  `IndependentSecurityAuditAppender`는 별도 transactional writer의 commit 예외를 facade에서
  잡아 `FAILED` 결과로 반환하고, caller는 event type·request id만 구조화 ERROR와 counter로
  남깁니다. 원문 식별자·token은 진단 값에 넣지 않습니다. 실패한 전체 command를 내구적으로
  보존하지 못한 상태에서 자동 replay 가능하다고 주장하지 않으며, 별도 security audit 장애
  runbook은 실제 authoritative source로 복원 가능한 record와 복원 불가능한 audit gap을
  구분해 후자를 조작된 record로 메우지 않게 합니다.

## 결정 1. Refresh Session 소유권

### 선택지

| 선택지 | 장점 | 결함·비용 |
| --- | --- | --- |
| A. `account`가 계속 소유 | credential 변경과 session 폐기를 한 adapter에서 처리하기 쉽습니다. | rotation·reuse·logout 규칙이 있는 `auth`와 상태·writer가 갈라지고 현재 문제가 유지됩니다. |
| B. `auth`가 단일 소유 | session 언어·정책·row lock·writer가 한 경계에 모이고 로그인·rotation·logout이 같은 모델을 사용합니다. | password 변경·reset에서 transaction을 보존하는 공개 event handoff가 필요합니다. |
| C. 별도 `session` Context | 향후 device session·전체 기기 로그아웃을 독립 확장하기 쉽습니다. | 현재 session 기능 규모에 비해 새 Context·API·운영 비용이 크고 auth와 강하게 결합합니다. |

### 선택

선택은 **B. `auth` 단일 소유**입니다. `refresh_token`은 account 자체보다 인증 session의
rotation·reuse·logout 생명주기에 의해 바뀝니다. 단순 이동이 아니라 다음 계약을 함께
적용해야 단일 owner가 성립합니다.

- 최초 비밀번호 로그인에서 `auth`가 refresh token을 저장합니다.
- account는 로그인 성공 시각과 credential 실패 계측만 갱신합니다.
- 비밀번호 변경·reset은 account의 credential 변경과 `CredentialChanged` 공개 사실을
  만들고, auth의 동기 listener가 같은 transaction에서 모든 active session을 폐기합니다.
- `RefreshSession` Aggregate가 issue, rotate, reuse rejection과 logout 전이를 표현하고,
  repository는 이 Aggregate의 저장·복원 계약이 됩니다.
- 현재 pessimistic lock, reuse 우선 판정, `noRollbackFor` 보안 처분은 그대로 보존합니다.

별도 session Context는 device/session 조회·개별 폐기 같은 독립 제품 언어와 변경 주기가
생길 때 재검토합니다.

## 결정 2. Security Audit 소유권

### 선택지

| 선택지 | 장점 | 결함·비용 |
| --- | --- | --- |
| A. `account` 단일 소유 | 현재 account 흐름 변경량이 작습니다. | auth가 account에 감사 명령까지 의존하고 account가 session 감사를 소유하는 의미 왜곡이 남습니다. |
| B. `auth` 단일 소유 | 인증 이벤트 이름과 가까우며 refresh 흐름 변경이 작습니다. | account→auth와 auth→account의 양방향 공개 의존이 생기기 쉽습니다. |
| C. `securityaudit` 단일 소유 | 두 업무 Context에서 writer를 제거하고 append·보존·민감정보 정책을 한 경계에 둡니다. | 새 module과 공개 append 계약, migration이 필요합니다. |

### 선택

선택은 **C. `securityaudit` 단일 소유**이며, 이 module은 Security Audit Record의 분류·
append·보존 언어를 소유하는 Supporting Subdomain의 Bounded Context 후보입니다. 이는 모든 감사 사실을 Domain Event로
취급한다는 뜻이 아닙니다. Account 등록처럼 실제 업무 사실은 Domain Event에서 감사
record로 변환할 수 있고, 로그인 실패처럼 보안 추적용 사실은 명시적 audit command로
기록합니다.

- `securityaudit.api`는 event type·result·reason·public subject·발생 시각·안전한 metadata를
  받는 최소 append 계약을 제공합니다.
- raw email·password·token·token hash는 계약에 포함하지 않습니다.
- event type·result·reason taxonomy 의미는 현재 Blocked인 `001-auth-event-taxonomy-redesign`과
  충돌시키지 않고 현재 enum·constraint를 보존합니다. 다만 module 간 내부 DB id 금지를 위해
  `ADMIN_INVITATION_ACCEPTED.invitedByAccountId` metadata는
  `invitedByAccountPublicId`로 바꿉니다. 선택한 DB 초기화 전환에는 기존 JSON row가 없으므로
  backfill을 만들지 않습니다.
- 보존 기간·조회 API·보안 알림은 기존 backlog 범위이며 이번 구조 이동으로 결정하지 않습니다.

## 결정 3. Account 내부 전술 모델

모든 JPA Entity를 Domain Entity로 바꾸지 않습니다. 상태 전이와 불변식이 확인된 다음
생명주기만 모델링합니다.

| 생명주기 | 목표 모델 | 모델이 소유할 규칙 |
| --- | --- | --- |
| Password Credential | `PasswordCredential` | 변경·reset 결과, 강제 변경 flag, 실패 계측 의미 |
| Email Verification | `EmailVerificationChallenge` | open/expired/consumed, TTL·cooldown, `PENDING`→`ACTIVE` 전이 조건 |
| Password Reset | `PasswordResetChallenge` | 공개 enumeration 방지와 분리된 내부 발급, 만료·소비, credential 변경 사실 |
| Admin Invitation | `AdminInvitation` Aggregate | issue·replace·accept·revoke·expire와 terminal 전이 금지 |

Verification과 reset이 같은 table을 사용해도 목적별 업무 모델은 분리합니다. Persistence
adapter가 두 모델을 공통 row 표현에 매핑할 수 있지만 application service가 purpose 문자열과
JPA 상태를 직접 조합하지 않게 합니다.

`notification`, `shared`, `config`에는 현재 독립 업무 상태가 없으므로 Aggregate·Repository를
추가하지 않습니다. 이 생략은 DDD 미적용이 아니라 source에 근거한 전술 패턴 `해당 없음`입니다.

## 결정 4. 데이터 전환 방식

**결정 질문:** `refresh_token`과 `auth_event`가 account 내부 id 대신 public id만 참조하는
같은 최종 schema로 이동할 때, 기존 data와 구 application 호환성을 유지할 것인가, 전체 DB를
초기화하고 최종 schema를 처음부터 만들 것인가?

**공통 전제:** 두 후보 모두 최종적으로 `refresh_token.account_public_id UUID NOT NULL`과
nullable `auth_event.account_public_id UUID`만 유지하며, 구 `account_id` FK·column과 호환
trigger·function은 남기지 않습니다.

| 비교 축 | A. expand/contract 호환 전환 | B. DB 초기화 후 최종 schema 재생성 |
| --- | --- | --- |
| 적용 방식 | nullable public id 추가·backfill 뒤 구 application과 새 application을 함께 지원하고, 관찰 후 contract migration을 실행합니다. | 모든 적용 환경의 DB와 Flyway 이력을 폐기한 뒤 V1부터 수정된 migration을 실행합니다. |
| 임시 구조·코드 | 양방향 identity trigger·function, 구·신 column과 FK·index, reconciliation이 필요합니다. | 임시 trigger·function·dual write·backfill이 없습니다. |
| 추가 작업 | expand migration, application 전환 release, rollback 관찰, reconciliation, 별도 contract migration과 제거 검증이 필요합니다. | V6·V7을 최종 schema로 수정하고 전환 전용 migration을 제거한 뒤 빈 PostgreSQL 재생성을 검증합니다. |
| data·rollback | 기존 data와 구 image rollback을 유지하지만 중간 schema와 임시 객체를 운영해야 합니다. | 기존 data와 구 image rollback을 포기하며 초기화 대상 환경 전체가 같은 시점에 재생성돼야 합니다. |
| 실패 경계 | dual identity 불일치, trigger 동작, backfill 누락과 contract 시점 오판이 추가됩니다. | 초기화 누락 환경은 checksum·missing migration으로 실패하며, 재생성 전 backup 외 data 복구 경로가 없습니다. |

**선택: B. DB 초기화 후 최종 schema 재생성.** 현재 production data가 없고 사용자가 이번에
RDS DB 자체를 초기화하기로 확정했으므로 기존 data·구 application rollback을 보존할 이유가
없습니다. V6은 `refresh_token`을 처음부터 public id `NOT NULL` FK로 만들고, V7은 미식별
로그인 실패를 위해 nullable public id FK로 만듭니다. 전환만을 위해 존재하던 migration과
trigger·function·구 column·구 index는 제거합니다.

- 감수 비용·위험: 초기화 대상에서 빠진 지속 DB는 수정된 versioned migration과 호환되지
  않습니다. 따라서 이 결정은 이번 RDS 초기화에만 적용하며 반복 운영 절차로 일반화하지 않습니다.
- 배제 대안: A는 data와 구 image rollback이 필요한 운영 환경에는 적합하지만, 현재 조건에서는
  임시 DB 객체와 후속 contract 작업만 추가합니다.
- 최종 도달 조건: 빈 PostgreSQL에 V1부터 최신 migration을 적용했을 때 두 table에 public id
  column만 존재하고 transition trigger·function이 0개이며 전체 integration test가 통과합니다.
- 재검토 조건: DB 초기화 전에 보존해야 할 data나 동시에 실행할 구 application이 발견되면
  migration 수정을 중단하고 A를 다시 검토합니다.

## Transaction과 실패 계약

- 로그인 성공: auth application transaction 안에서 account 계측, auth session 저장,
  security audit append가 함께 성공하거나 rollback됩니다.
- 로그인 실패: 현재와 같이 외부 401에도 실패 계측·감사가 남아야 하며,
  `IndependentSecurityAuditAppender`와 기존 no-rollback 의미를 회귀 테스트로 고정합니다.
- password 변경·reset: account credential/token 소비, auth session 폐기와 감사 append가
  같은 transaction에 참여합니다.
- rotation·reuse·logout: auth가 token row lock부터 상태 변경까지 소유하고 securityaudit가
  같은 transaction에 참여합니다. Reuse·상태 거부 기록은 401에도 commit됩니다.
- notification 요청: 업무 token을 먼저 commit하고 provider는 after-commit에 호출합니다.
  발송 결과는 `IndependentSecurityAuditAppender`로 별도 transaction에 남기되, 먼저
  [005 Notification 결과와 감사 저장 실패 혼합](../../bugs/2026/005-notification-audit-result-separation.md)의
  provider 결과·audit 실패 분리 결과를 구현해 소비합니다. Audit commit 실패는 provider
  결과를 바꾸지 않고 별도 signal과 audit gap으로 분류하며 자동 replay를 약속하지 않습니다.

이 계약을 비동기 eventual consistency로 바꾸지 않습니다. 비밀번호 변경 뒤 짧은 시간이라도
기존 session이 살아 있는 것은 현재 보안 결과를 약화하기 때문입니다.

## 구조 검증

- Spring Modulith는 `account`, `auth`, `securityaudit`, `notification`, `shared`, `config`의
  허용 의존만 검증합니다.
- ArchUnit은 다른 module의 `internal` 접근, Domain의 Spring/JPA 의존과 기술 경계의 업무
  domain package 생성을 거부합니다.
- 별도 architecture test는 `refresh_token` JPA mapping이 `auth`에 하나, `auth_event` JPA
  mapping이 `securityaudit`에 하나뿐인지 고정합니다.
- repository native query가 다른 Context의 table을 읽는지 source test로 검사하고,
  예외 allow-list를 두지 않습니다.
- `Clock` bean은 `config`가 제공하고 account/auth production constructor의
  `Clock.systemUTC()` 직접 생성을 금지합니다.
- Domain unit test는 framework 없이 상태 전이와 금지 전이를 검증합니다.

## 배제 대안과 재검토 조건

- 모든 코드를 하나의 `account` Context로 합치지 않습니다. 이는 package conflict는 줄여도
  session·account의 서로 다른 언어와 변경 이유를 지웁니다.
- table만 한 package로 옮기고 application 규칙을 그대로 분산하지 않습니다. Writer 수만
  줄어도 상태 생명주기 owner가 둘이면 DDD 목표를 달성하지 못합니다.
- 모든 module에 Aggregate·Value Object를 만들지 않습니다. 확인된 불변식이 없는 기술
  경계의 의식적 생략이 더 정확합니다.
- 외부 Identity Provider 도입, device session 제품 기능, 별도 audit 보존·조회 서비스,
  community profile의 독립 생명주기가 생기면 관련 Context 경계를 다시 판정합니다.

## 관련 문서

- **작업 계획**: [009 비카탈로그 DDD 보완](009-non-catalog-ddd-hardening.md)
- **일반 기준**: [120 DDD 개념과 적용 기준](../../../architecture/120-domain-driven-design.md)
- **현재 판정**: [200 현재 프로젝트 아키텍처](../../../project/200-current-architecture.md)
- **현재 물리 경계**: [210 모듈 지도](../../../project/210-module-map.md), [220 Account/Auth 데이터 모델](../../../project/220-account-auth-data-model.md)
- **현재 업무 정책**: [100 Account 도메인](../../../domain/100-account.md), [110 Account/Auth 인증 정책](../../../domain/110-account-auth-policy.md), [120 Auth Event 정책](../../../domain/120-auth-event-policy.md)
- **독립 선행 충돌 범위**: [001 Auth Event Taxonomy 재설계](001-auth-event-taxonomy-redesign.md)
- **감사 실패 의미 선행 작업**: [005 Notification 결과와 감사 저장 실패 혼합](../../bugs/2026/005-notification-audit-result-separation.md)
- **시간 조립 선행 작업**: [003 공용 Clock bean의 auth 내부 소유](../../bugs/2026/003-shared-clock-bean-ownership.md)
