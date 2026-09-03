# 비카탈로그 DDD 보완

## 상태

Done

## 결과

카탈로그를 제외한 현재 module의 업무 언어·상태·persistence writer를 목표 소유 경계에
맞춰 구현했습니다. `auth`는 refresh session, `securityaudit`는 `auth_event`,
`account`는 credential·verification/reset·invitation을 소유하며 구조 test와 PostgreSQL
통합 test가 이 경계를 검증합니다.

Public-id schema는 이번 RDS 초기화에 한정해 baseline migration을 최종 구조로 재작성했습니다.
`refresh_token`과 `auth_event`에는 public id 참조만 남고, 전환용 migration과 compatibility
trigger·function·구 `account_id` column은 제거합니다.

최종 검증은 Docker daemon 준비 확인 뒤
`./gradlew check --no-daemon --console=plain`으로 수행했고 모든 task가
통과했습니다. 독립 구현 검증은 단일 writer·transaction·public-id 전환·Domain 전이·Clock
경계를, 독립 문서 검증은 owner·router·local link·현재 source 대조를 확인했습니다.

최종 구현 뒤에는 다음 질문에 코드·test·문서가 같은 답을 줘야 합니다.

- account 상태·credential·verification/reset·invitation을 누가 바꾸는가: `account`
- refresh session을 누가 issue·rotate·revoke하는가: `auth`
- `auth_event`를 누가 저장하는가: `securityaudit`
- 이메일 전송 실패가 account 상태를 직접 바꾸는가: 아니요. `notification`은 전달만 합니다.
- `shared`·`config`가 업무 규칙을 소유하는가: 아니요.

## 해결 결과

- `refresh_token`은 `auth`, `auth_event`는 `securityaudit`만 매핑·기록합니다.
- `auth`의 `account` table 직접 read와 account의 refresh writer를 제거했습니다.
- `PasswordCredential`, `RefreshSession`, verification/reset challenge와 `AdminInvitation`이
  lifecycle 판단·허용 전이·terminal 재전이 금지를 framework 없이 표현합니다.
- `CredentialChanged` 동기 event가 credential 변경과 session 폐기의 보안 원자성을 유지합니다.
- `config`가 유일한 UTC Clock provider이고 구조 source test가 현재 경계를 보호합니다.

현재 근거 전체는 [200 현재 프로젝트 아키텍처](../../../project/200-current-architecture.md)의
9개 lifecycle coverage와 [상세 설계](009-non-catalog-ddd-hardening-design.md)가 소유합니다.

## 범위

- `account`, `auth`, 신규 `securityaudit`, `notification`, `shared`, `config`의 경계 판정
- refresh session과 security audit의 단일 writer 전환
- account credential·verification·reset·invitation, auth session의 전술 모델 보완
- DB 초기화 뒤 public id 최종 schema를 직접 생성하는 baseline migration과 기존 transaction·보안 결과 보존
- Spring Modulith·ArchUnit·source 구조 test 강화
- domain·project·schema·module 문서의 구현 결과 동기화

## 제외 범위

- 카탈로그의 현재 source 평가·리팩터링
- 아직 구현되지 않은 자동차·커뮤니티 module 경계 선점
- public API endpoint·request·response 변경
- auth event type·result·reason taxonomy 의미 변경과 기존 migration checksum 수정
- 로그인 rate limit·hard lock·MFA·OAuth·device session·전체 기기 로그아웃
- audit 보존 기간·조회 API·보안 알림과 notification provider 교체
- production 배포·push·PR

새 module이나 제외 영역의 후속 변경에도 일반 DDD 판정 gate는 적용합니다.

## 요구사항과 수용 기준

| 요구사항 | 관찰 가능한 수용 기준 |
| --- | --- |
| 상태·생명주기별 단일 owner | `refresh_token` JPA mapping·writer는 `auth`에 하나, `auth_event`는 `securityaudit`에 하나만 존재합니다. |
| 공개 계약 협력 | 다른 module의 `internal`·JPA type·table·내부 DB id를 직접 참조하는 production code가 없고 invitation actor metadata도 public id를 사용합니다. |
| Session 규칙 모델링 | issue·rotate·reuse·logout과 금지 전이가 framework 없는 Domain test로 고정됩니다. |
| Account 규칙 모델링 | verification·reset·invitation의 TTL·cooldown·terminal 전이·금지 전이가 Domain test로 고정됩니다. |
| 보안 결과 보존 | 비밀번호 변경·reset과 session 폐기가 원자적이며 reuse·상태 거부 감사도 기존 401 뒤 남습니다. |
| 직접 전환 | 빈 DB에 V1부터 migration을 적용하면 public-id 최종 schema만 생성되고 구 account id column·trigger·function이 존재하지 않습니다. |
| 기술 경계 절제 | `notification`, `shared`, `config`에 업무 Domain Model이 없고 업무 규칙 유입을 architecture test가 차단합니다. |
| 전체 회귀 | module·architecture·migration·account/auth integration test와 `./gradlew check --no-daemon --console=plain`이 통과합니다. |
| 문서 현재성 | DDD 판정, Context 후보, writer·transaction과 schema reference가 final source와 일치합니다. |

## 남은 결정

없습니다. Module·schema·transaction·test 선택은 현재 제품 범위와 source에서 닫을 수 있는
Agent 기술 결정이며 [상세 설계](009-non-catalog-ddd-hardening-design.md)에 대안·trade-off와
재검토 조건을 기록했습니다. 자동차·커뮤니티의 Core Domain 분류와 제외된 제품 기능은 이
계획의 구현 결정을 막지 않으며 현재 사실로 선점하지 않습니다.

## 확정 결정 색인

- Refresh session과 `refresh_token` writer: `auth`
- Security audit와 `auth_event` writer: 신규 `securityaudit`
- Security audit 분류: append·taxonomy·보존 언어를 소유하는 Supporting Subdomain Context 후보
- Audit transaction 계약: caller와 함께 rollback되는 `REQUIRED` append와 독립 보존하는 `REQUIRES_NEW` append 분리
- credential 변경 뒤 session 폐기: account 공개 사실을 auth가 같은 transaction에서 처리
- 다른 Context 참조 식별자: 내부 DB id가 아닌 account public id
- 전술 모델: 확인된 상태 전이가 있는 account/auth 생명주기에만 적용
- 기술 경계: `notification`, `shared`, `config`는 DDD 전술 패턴 `해당 없음`
- migration: 이번 RDS 초기화에 한정한 baseline 재작성 → public-id 최종 schema 직접 생성
- 공용 시간: `config` 소유 `Clock` bean을 account/auth가 주입받음

근거와 배제 대안은 [009 상세 설계](009-non-catalog-ddd-hardening-design.md)를 기준으로 합니다.

## 남은 구현

### 작업 1. 목표 Context 계약과 실패 test 고정

작업 목표: 구현이 따라야 할 owner·공개 계약·금지 접근을 executable test로 먼저 고정합니다.

선행 조건: 이 계획과 상세 설계 commit이 구현 branch의 fixed base에 포함돼야 합니다.

수행 내용:

- Spring Modulith 목표 module과 허용 의존을 test에 선언합니다.
- `refresh_token`·`auth_event` 공동 mapping, 다른 Context table native query와 기술 경계의
  Domain package 유입을 탐지하는 실패 test를 추가합니다.
- 기존 9개 lifecycle의 정상·거부·동시성·transaction 결과를 변경 전 회귀 test로 고정합니다.

검증·복구: 새 구조 test는 현재 공동 writer 때문에 red여야 하며 기존 동작 test는 green이어야
합니다. Production source는 바꾸지 않으므로 실패 test commit을 되돌리면 원상 복구됩니다.

완료 결과: 이후 작업이 owner를 우회하면 build에서 즉시 실패하는 기준선이 생깁니다.

### 작업 2. Security Audit 단일 writer 전환

작업 목표: account/auth가 만드는 감사 기록을 `securityaudit`의 한 저장 경계로 모읍니다.

선행 결과: 작업 1의 공동 `auth_event` mapping 실패 test가 이 작업의 제거 결과를 소비합니다.

수행 내용:

- `securityaudit.api` append 계약과 internal persistence adapter를 추가합니다.
- caller transaction과 함께 실패하는 `SecurityAuditAppender`, 요청 결과를
  `REQUIRES_NEW`로 독립 보존하는 `IndependentSecurityAuditAppender`를 분리합니다.
- 독립 append 실패는 safe structured error·counter를 남기되 복원 source가 없는 record를
  자동 replay한다고 주장하지 않습니다. 새 `550 Security Audit 장애 복구` runbook이 실제
  source로 복원 가능한 record와 복원 불가능한 audit gap을 구분합니다.
- 현재 event type·result·reason·metadata와 transaction 결과를 바꾸지 않고 account/auth의
  JPA entity·repository를 제거합니다.
- Bug 005의 provider 결과·audit 저장 실패 분리를 먼저 구현해 독립 append의 `FAILED` 결과가
  notification 실패로 다시 분류되지 않고 별도 audit gap 신호로 남게 합니다.
- `auth_event`를 nullable `account_public_id` FK로 처음부터 생성하고 invitation actor metadata의
  내부 id를 public id로 전환합니다.
- Blocked인 taxonomy 개선 001과 migration 번호·constraint 변경 충돌을 먼저 대조합니다.

검증·복구: 각 account/auth lifecycle에서 event 수·값·rollback/no-rollback을 PostgreSQL
integration test로 검증합니다. 빈 DB 재생성 전 보존 data나 지속 DB가 발견되면 baseline
재작성을 중단하고 상세 설계의 호환 전환 대안을 다시 엽니다.

완료 결과: `auth_event` production writer와 mapping이 `securityaudit` 하나가 됩니다.

### 작업 3. Auth Refresh Session 단일 writer 전환

작업 목표: 최초 로그인부터 rotation·reuse·logout까지 `auth`가 하나의 session 모델과
repository를 사용합니다.

선행 결과: 작업 2의 security audit append 계약은 auth session 상태 변경이 감사를 기록하는
첫 단계에서 소비됩니다.

수행 내용:

- `RefreshSession` Aggregate와 repository port를 만들고 issue·rotate·reuse·logout 전이를
  기존 policy 순서대로 옮깁니다.
- 로그인 성공에서 auth가 refresh session을 저장하고 account에는 로그인 계측만 요청합니다.
- `refresh_token`을 `account_public_id NOT NULL` FK로 처음부터 생성하고 auth의 `account` native
  SQL과 account의 refresh JPA mapping·repository를 제거합니다. Auth production repository는
  public id만 쓰며 account table을 join하지 않습니다.
- row lock, family 폐기와 `noRollbackFor` 의미를 유지합니다.

검증·복구: 최초 발급, 동시 rotation 한 건 성공, 만료와 reuse 우선순위, logout 멱등성,
account 상태 거부를 Domain·adapter·API test로 검증합니다. 초기화 뒤에는 구 column을 사용하는
이전 image rollback을 지원하지 않습니다.

완료 결과: `refresh_token` writer·상태 규칙·transaction owner가 모두 `auth`와 일치합니다.

### 작업 4. Credential 변경과 Session 폐기 결속

작업 목표: account가 auth repository를 소유하지 않으면서 비밀번호 변경·reset의 즉시 session
폐기 보안을 유지합니다.

선행 결과: 작업 3의 account-public-id 기반 session repository와 account 단위 active-session
폐기 port는 이 작업의 auth listener가 처음 소비합니다.

수행 내용:

- 먼저 `account.api.CredentialChanged` 공개 event 계약과 동기 transaction 의미를 정의합니다.
- 다음으로 `PasswordCredential`의 change/reset 성공이 이 event를 동기 발행하는 producer
  결과와 단위 test를 만듭니다. 이 단계는 listener 없는 production checkpoint로 배포하지
  않습니다.
- 이어서 auth listener가 event의 account public id를 작업 3의 session 폐기 port에 전달합니다.
- 마지막으로 change/reset integration test에서 producer→listener→session 폐기와 전체
  rollback을 검증한 하나의 배포 가능 결과로 commit합니다.
- securityaudit append를 같은 transaction 결과에 결속하고 account의 refresh repository
  의존을 삭제합니다.

검증·복구: credential update, reset token 소비, session 폐기와 감사 저장 중 하나가 실패하면
전체 rollback되는지, 현재 password mismatch 감사가 4xx 뒤 남는지 검증합니다. Event listener를
되돌릴 때에는 account 직접 writer를 다시 추가하지 않고 작업 3 이전 branch로 전체 결과를
되돌립니다.

완료 결과: credential과 session은 별 owner를 유지하면서 보안상 필요한 원자성이 보존됩니다.

### 작업 5. Account 상태 전이 모델 보완

작업 목표: verification·reset·invitation 규칙을 JPA 상태가 아니라 업무 모델이 소유합니다.

선행 결과: 작업 2의 audit 계약은 각 terminal 전이가 감사를 남기는 단계에서 소비되고, 작업
4의 credential 변경 사실은 password reset 완료에서 소비됩니다.

수행 내용:

- `EmailVerificationChallenge`, `PasswordResetChallenge`, `AdminInvitation` 상태·행위를
  도입하고 기존 table mapping은 adapter에 유지합니다.
- TTL·cooldown·open 교체·consume·accept·revoke·terminal 금지를 모델로 이동합니다.
- Application service는 transaction과 notification after-commit 조율만 남깁니다.

검증·복구: 각 모델의 정상·만료·중복·이미 terminal·cooldown 경계를 단위 test로 먼저
작성하고 기존 API·PostgreSQL transaction test를 회귀시킵니다. Schema 변경이 없으므로 결과
단위별 source revert가 가능합니다.

완료 결과: account의 복잡한 상태 전이가 framework 없는 모델 test로 설명됩니다.

### 작업 6. 공용 시간 조립 경계 정리

작업 목표: account/auth가 다른 업무 module 내부 bean이나 service-local system clock에
의존하지 않고 같은 UTC 시간 기준을 주입받습니다.

선행 결과: [Bug 003](../../bugs/2026/003-shared-clock-bean-ownership.md)의 목표 계약을 이
작업의 exact 입력으로 사용합니다. 작업 2~5와 write set이 겹치지 않아 병렬 실행할 수
있으며, 작업 7의 final 경계 검증이 이 결과를 처음 필수 소비합니다.

수행 내용:

- `Clock @Bean`을 `auth.internal`에서 `config`의 중립 composition으로 이동합니다.
- account/auth production constructor의 `Clock.systemUTC()` 직접 생성을 제거하고 같은 bean을
  주입합니다.
- production context와 test override가 같은 조립 지점을 사용하는지 검증합니다.
- Bug 003의 상태·router와 module map을 실제 결과에 맞춰 종결합니다.

검증·복구: context load, account/auth focused test와 Clock source rule을 검증합니다. Schema와
public API 변경이 없으므로 config·constructor·test 결과를 한 단위로 revert할 수 있습니다.

완료 결과: `config`가 유일한 공용 Clock provider이고 account/auth 경계 예외가 사라집니다.

### 작업 7. 구조 gate와 영구 SOT 종결

작업 목표: final source가 목표 경계를 계속 지키도록 자동 검증과 문서를 닫습니다.

선행 결과: 작업 2~6의 final package·writer·transaction·Clock 조립은 이 작업의 architecture
test, 운영 runbook과 문서가 직접 소비합니다.

수행 내용:

- 공동 JPA mapping·다른 Context table read·internal 접근이 0건인지 구조 test를 green으로
  만듭니다.
- `notification`, `shared`, `config`의 DDD `해당 없음`과 업무 규칙 유입 금지를 검증합니다.
- Context 후보·협력, lifecycle owner, schema·transaction과 전술 모델을 architecture,
  project, domain SOT에 반영합니다.
- `430 Application 기능 qualification`의 `auth_event` 조회와 `refresh_token` cleanup SQL을
  public-id final schema와 child-first 삭제 순서에 맞춰 갱신·실행 검증합니다.
- `550 Security Audit 장애 복구` runbook과 operations router를 추가해 append 실패 signal,
  복원 source 판정, audit gap 기록과 중단 조건을 소유하게 합니다. `430`은 qualification
  SQL 동기화만 소유합니다.
- schema migration test가 구 account id column·transition trigger·function 부재와 public-id FK·
  nullability를 검증합니다.

검증·복구: focused test 뒤 전체 Gradle check, 문서 link·anchor·source 대조, 독립 개발 검증을
수행합니다. DB 초기화 전 지속 DB가 발견되면 migration 재작성을 적용하지 않습니다.

완료 결과: 전략적 DDD 판정은 owner·협력·물리 writer의 actual source에 근거해 갱신되고,
전술적 DDD는 필요한 영역의 적용과 기술 경계의 생략이 모두 자동 보호됩니다.

## 작업 의존 관계

```text
작업 1 owner 실패 test
  ├─> 작업 2 REQUIRED/REQUIRES_NEW securityaudit 계약 ─> 작업 3 session 감사 기록
  │                              └─> 작업 5 account 감사 기록
  └─> 작업 3 account 단위 session 폐기 port
          └─> 작업 4 CredentialChanged 계약 → account 발행 → auth listener → 통합 검증
                  └─> 작업 5 reset 완료

Bug 003 목표 계약 ─> 작업 6 config Clock provider

작업 2~6 final source ─> 작업 7 구조 gate·runbook·SOT
```

작업 2와 작업 3은 migration·audit 호출 경계가 겹치므로 직렬화합니다. 작업 6은 조립 source가
독립적이므로 병렬 실행할 수 있습니다. 작업 5의 invitation·verification 모델은 write set을
분리할 수 있어 대표 공통 계약과 첫 모델이 검증된 뒤에만 병렬화할 수 있습니다.

## 검증·전환·복구

- TDD는 구조 실패 test와 Domain 상태 전이 실패 test부터 시작합니다.
- DB 통합 test는 실제 PostgreSQL에서 final baseline schema, lock, transaction과 constraint를
  검증합니다.
- Public API contract는 변경하지 않으며 기존 OpenAPI·Security test를 회귀시킵니다.
- 빈 PostgreSQL에 baseline migration 전체를 적용해 public-id 최종 schema와 전환 artifact 부재를
  한 번에 검증합니다.
- Push가 별도로 요청된 코드 작업만 exact SHA의 required CI·Sonar gate까지 진행합니다.

## 영구 SOT 승격

- DDD 판정·Context 후보: `docs/project/200-current-architecture.md`
- 실제 module·의존·writer 예외: `docs/project/210-module-map.md`
- lifecycle·transaction·schema: `docs/project/220-account-auth-data-model.md`, `docs/project/schema`
- staging qualification 조회·cleanup: `docs/operations/430-application-functional-qualification-runbook.md`
- audit 장애 분류·복구: 신규 `docs/operations/550-security-audit-failure-recovery-runbook.md`와 operations router
- 업무 언어·상태 전이: `docs/domain/100-account.md`, `110-account-auth-policy.md`,
  `120-auth-event-policy.md`와 필요한 security audit domain 문서
- 일반 DDD 개념 문서는 유지하고, 설계 workflow·template·계획 skill에는 목표 구조와 전환
  방식을 별도 결정 축으로 검증하는 gate를 반영합니다.

## 관련 문서

- **결정 근거**: [009 상세 설계](009-non-catalog-ddd-hardening-design.md)
- **일반 기준**: [120 DDD 개념과 적용 기준](../../../architecture/120-domain-driven-design.md)
- **현재 판정**: [200 현재 프로젝트 아키텍처](../../../project/200-current-architecture.md)
- **현재 module·데이터**: [210 모듈 지도](../../../project/210-module-map.md), [220 Account/Auth 데이터 모델](../../../project/220-account-auth-data-model.md)
- **전환 source**: [003 Account/Auth 후속 Backlog](../../backlog/2026/003-account-auth-follow-up.md)
- **공용 시간 경계**: [003 공용 Clock bean의 auth 내부 소유](../../bugs/2026/003-shared-clock-bean-ownership.md)
- **감사 실패 의미**: [005 Notification 결과와 감사 저장 실패 혼합](../../bugs/2026/005-notification-audit-result-separation.md)
