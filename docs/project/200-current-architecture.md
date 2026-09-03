# 현재 프로젝트 아키텍처

이 문서는 Carmunity backend에 실제 적용된 아키텍처 결정과 재검토 조건을 소유합니다.

## 결정

```text
DDD 기반 업무 경계
+ 단일 Spring Boot 배포
+ Modular Monolith
+ 모듈 내부 Hexagonal Architecture
+ 단순 조회의 Read Model/Projection 허용
+ Spring Modulith와 ArchUnit 경계 검증
```

## 현재 DDD 적용 판정

현재 프로젝트는 **전략적 DDD 부분 적용, 전술적 DDD 선택 적용** 상태입니다. 이번 보완으로
확인된 물리 소유권 위반은 제거됐지만, Subdomain 분류와 Context Map의 조직적 관계까지
확정된 것은 아니므로 전체 DDD 완료로 선언하지 않습니다.

- 전략적 DDD는 업무 의미의 최상위 package, 공개 계약과 단일 persistence owner를 갖췄습니다.
  `account`, `auth`, `securityaudit`는 Context 후보와 협력 경계를 코드로 보호하지만
  Subdomain 분류와 Context Map 관계 유형은 아직 확정되지 않았습니다.
- 전술적 DDD는 복잡한 규칙이 확인된 `account`에 Aggregate Root, Entity, Value Object,
  Domain Policy와 Domain Event를 적용했습니다. 모든 module에 같은 패턴을 강제하지 않는
  선택 적용 자체는 결함이 아닙니다.
- refresh token의 만료·reuse 판단은 `auth`의 `RefreshSession` Aggregate Root가 표현하고,
  최초 발급부터 credential 변경 폐기까지 `auth`가 단일 persistence owner입니다.
- email verification, password reset, admin invitation의 cooldown·terminal·만료 판단은
  account domain challenge 모델이 소유합니다.

이 판정에서 `부분 적용`은 “DDD를 적용할 수 없어서 포기했다”는 뜻이 아닙니다. 현재 구조가
DDD의 구현 기준은 충족하지만, Subdomain 분류·협력 주체·Context Map 관계 유형 같은 전략적
모델링 증거가 아직 모두 확정되지 않았다는 뜻입니다.

### 전략적 판정 근거

| 일반 판정 기준 | 현재 상태 | actual source와 판정 근거 |
| --- | --- | --- |
| Core Domain·Subdomain 분류 | 미확정 | 조사 범위인 [000 제품 문서 목차](../product/000-index.md)와 [000 도메인 문서 목차](../domain/000-index.md)는 기능·정책을 설명하지만 Core/Supporting/Generic 분류를 소유하지 않습니다. |
| 업무 전문가·개발자의 협력적 모델 탐구 | 미확정 | 영구 협업·결정 기록의 조사 범위인 [000 도메인 문서 목차](../domain/000-index.md)와 [000 작업 문서 목차](../work-items/000-index.md)에서 참여 주체와 지속적 모델 정제 책임을 복원할 수 없습니다. |
| Ubiquitous Language | 충족 | [100 Account 도메인](../domain/100-account.md), [110 Account/Auth 인증 정책](../domain/110-account-auth-policy.md)과 실제 `RefreshSession`·challenge 모델의 용어와 owner가 일치합니다. |
| Bounded Context | 부분 충족 | `account`, `auth`, `securityaudit`의 공개 계약·단일 writer 경계는 구현됐지만 조직·업무 언어 경계까지 검증된 최종 Context Map은 아닙니다. |
| Context Map | 미확정 | [210 모듈 지도의 현재 협력](210-module-map.md#현재-협력)으로 공개 의존은 복원할 수 있지만 Partnership·Customer/Supplier 같은 관계 유형과 팀 협력 방식은 결정돼 있지 않습니다. |
| 단일 상태·생명주기 owner | 충족 | `account`는 credential·challenge·invitation, `auth`는 refresh session, `securityaudit`는 audit append와 물리 저장을 각각 단독 소유합니다. |
| 다른 Context 내부·table 우회 금지 | 충족 | Modulith·ArchUnit과 [DddOwnershipSourceTest.java](../../src/test/java/com/ronams/carmunity/architecture/DddOwnershipSourceTest.java)가 internal 접근, 공동 table mapping과 `auth`의 `account` table 직접 read를 함께 금지합니다. |

따라서 전략적 DDD를 완료나 미적용으로 판정할 수 없습니다. 구조적 기반은 일부 존재하지만
핵심 분류·협력 주체·Context 관계 유형이 미확정이므로 `부분 적용`입니다. 이는 현재 구현이
소유권 기준을 충족하지 못한다는 뜻이 아니라 전략적 모델링 전체가 끝나지 않았다는 뜻입니다.

### 모델 유지 실천 판정

| 일반 실천 | 현재 상태 | actual source와 판정 근거 |
| --- | --- | --- |
| Model-Driven Design | 충족 | `Account`, `PasswordCredential`, account challenge 모델과 `auth.internal.domain.RefreshSession`이 실제 lifecycle 판단·허용 전이를 표현하고 application service가 이를 조율합니다. |
| Hands-on Modelers | 미확정 | 조사 범위인 [000 도메인 문서 목차](../domain/000-index.md), [000 작업 문서 목차](../work-items/000-index.md)와 현재 source는 모델 결정자의 지속적 구현 참여를 기록하지 않습니다. package나 문서 존재로 추정하지 않습니다. |
| Bounded Context 내부 Continuous Integration | 미확정 | [ApplicationModuleVerificationTest.java](../../src/test/java/com/ronams/carmunity/architecture/ApplicationModuleVerificationTest.java)와 [ArchitectureRulesTest.java](../../src/test/java/com/ronams/carmunity/architecture/ArchitectureRulesTest.java)는 Java module·계층을 검증합니다. Bounded Context 자체가 미확정이므로 같은 Context 안의 언어·모델 통합 증거로 확대하지 않습니다. |
| Refactoring Toward Deeper Insight | 미확정 | 조사 범위인 [000 도메인 문서 목차](../domain/000-index.md), [000 작업 문서 목차](../work-items/000-index.md)와 domain source는 현재 결과를 보여 주지만 새 업무 지식에 따른 언어·경계·모델 공동 정제 절차를 소유하지 않습니다. |

모델 유지 실천은 협업과 지속적 정제 방식까지 포함하므로 정적 코드 구조만으로 완료 판정을
내리지 않습니다. 현재 source가 증명하는 Model-Driven Design 일부만 `부분 충족`이고 나머지는
후속 계획에서 증거를 만들거나 적용 방식을 정해야 합니다.

### 전술적 판정 근거

| 일반 판정 기준 | 현재 상태 | actual source와 판정 근거 |
| --- | --- | --- |
| 복잡한 규칙의 Domain Model 소유 | 충족 | `Account`, `PasswordCredential`, `EmailVerificationChallenge`, `PasswordResetChallenge`, `AdminInvitation`과 `RefreshSession`이 현재 확인된 값·terminal·만료·reuse와 금지 전이를 소유합니다. |
| Aggregate 일관성 경계와 transaction 정합성 | 충족 | Account graph 저장과 refresh session 변경은 각 owner transaction에 있고, `CredentialChanged` 동기 event가 credential 변경과 session 폐기의 보안 원자성을 유지합니다. |
| Entity와 Value Object의 의미 구분 | 충족 | Domain Aggregate·Value Object와 JPA 저장 모델은 package와 의존 규칙으로 분리되고 `RefreshSession`은 auth Aggregate Root로 명시됩니다. |
| 규칙의 Controller·JPA·설정·Mapper 분산 방지 | 충족 | Web은 UseCase에 위임하고 lifecycle 판단은 account/auth Domain Model, 저장은 owner adapter, 조립은 config에 분리됩니다. |
| 단순 영역의 불필요한 전술 패턴 방지 | 충족 | 조사 범위인 [notification 전체 source](../../src/main/java/com/ronams/carmunity/notification/)는 발송 공개 계약·외부 adapter·설정으로만 구성되고 Domain/Aggregate/Repository package가 없습니다. 이 부재는 현재 source에 한정하며 향후 업무 규칙이 생기면 재판정합니다. |

따라서 `전술적 DDD 선택 적용`은 모든 module에 전술 패턴을 채웠다는 뜻이 아닙니다.
`account`처럼 규칙과 일관성 경계가 확인된 곳은 모델을 사용하고, `notification`처럼 현재
기술 전달 책임만 확인된 곳은 생략했습니다. `auth`는 refresh session 규칙이 확인돼
`RefreshSession` Aggregate Root를 적용했고, `notification`·`shared`·`config`는 현재 업무
상태가 없어 전술 패턴을 생략했습니다.

#### 현재 구현 전체 생명주기 coverage

아래 표는 현재 `account`·`auth` application service와 [100 Account 도메인](../domain/100-account.md),
[110 Account/Auth 인증 정책](../domain/110-account-auth-policy.md),
[120 Auth Event 정책](../domain/120-auth-event-policy.md)이 소유한 변경 생명주기를 빠짐없이
대조한 결과입니다. 외부 provider 호출·transaction 순서 같은 orchestration과 업무 규칙을
구분하며, Domain type 일부가 있다는 이유로 생명주기 전체를 모델링됐다고 판정하지 않습니다.

| 생명주기 | 현재 전술 모델·application 배치 | persistence·경계 사실 | 판정 |
| --- | --- | --- | --- |
| 일반 회원 가입 | [Account.java](../../src/main/java/com/ronams/carmunity/account/internal/domain/Account.java)의 `registerMember` factory, `EmailAddress`, `DisplayName`, `PasswordPolicy`, `RoleAssignment`와 `AccountRegisteredEvent`가 `PENDING` account graph 생성 불변식을 소유합니다. | [AccountPersistenceAdapter.java](../../src/main/java/com/ronams/carmunity/account/internal/adapter/out/persistence/AccountPersistenceAdapter.java)가 graph와 가입 감사 이벤트를 한 transaction으로 저장하고 DB unique constraint가 동시 중복을 막습니다. | 생성 Aggregate·Value Object·Domain Event를 적용한 전술 모델링 영역입니다. |
| 최초 최고 관리자 bootstrap | `Account.bootstrapSuperAdmin`과 credential·role Value Object가 `ACTIVE`, `ROLE_SUPER_ADMIN`, 비밀번호 변경 강제 상태를 함께 생성합니다. [BootstrapSuperAdminAccountService.java](../../src/main/java/com/ronams/carmunity/account/internal/application/service/BootstrapSuperAdminAccountService.java)는 role seed와 이미 존재하는 최고 관리자 여부를 조율합니다. | `AccountPersistenceAdapter`가 role row lock, account graph와 두 감사 이벤트를 한 transaction으로 저장합니다. | account 생성 불변식은 Domain Model, 단일 bootstrap 동시성은 persistence가 담당하는 선택 적용입니다. |
| 비밀번호 로그인과 session 생성 | `CreatePasswordSessionService`가 token 발급을 조율하고 `RefreshSession`이 저장 session 상태를 표현합니다. | `auth`가 refresh session, `account`가 로그인 계측, `securityaudit`가 감사를 담당하며 성공 transaction에 함께 참여합니다. | 업무 규칙과 물리 owner가 일치합니다. |
| 인증된 비밀번호 변경 | `PasswordPolicy`·`PasswordHash`가 값 불변식을, `PasswordCredential`이 hash 교체와 강제 변경 해제를 소유하고 `ChangePasswordService`가 검증을 조율합니다. | `CredentialChanged` 동기 event로 account credential 변경과 auth session 폐기, security audit가 한 transaction에 결속됩니다. | Context별 owner를 분리하면서 보안 원자성을 유지합니다. |
| 이메일 인증 | `EmailVerificationChallenge`가 cooldown·소비·회수·만료 판단을 소유합니다. | account adapter가 token 소비·상태 전환을 저장하고 notification은 after-commit orchestration입니다. | 필요한 lifecycle에 전술 모델을 적용했습니다. |
| 비밀번호 재설정 | `PasswordResetChallenge`가 cooldown·terminal·만료 판단을 소유합니다. | 완료 시 `CredentialChanged`를 통해 auth session 폐기까지 같은 transaction으로 처리합니다. | challenge·credential·session의 owner와 협력이 명시됩니다. |
| 관리자 초대 | `AdminInvitation`이 terminal·만료 판단을, `Account.acceptAdminInvitation`이 신규 account graph를 소유합니다. | account persistence가 invitation 상태와 graph를 저장하고 securityaudit가 public actor id metadata를 기록합니다. | invitation lifecycle의 핵심 판단을 Domain Model로 이동했습니다. |
| refresh rotation·reuse·logout | [RefreshSession.java](../../src/main/java/com/ronams/carmunity/auth/internal/domain/RefreshSession.java)가 issue·rotation·logout과 terminal 재전이 금지, 만료·재사용 후보 판단을 제공하고 auth service가 family 폐기를 조율합니다. | auth만 `refresh_token`을 매핑하고 account 조회는 `account::api`만 사용합니다. | 전략적·전술적 owner가 일치합니다. |
| 인증·인가 감사 이벤트 | 각 Context는 공개 `SecurityAuditRecord`로 보안 사실을 전달합니다. | `securityaudit`만 `auth_event`를 매핑하며 REQUIRED와 REQUIRES_NEW 계약을 분리합니다. | Supporting Context의 단일 writer와 transaction 의미가 명시됩니다. |

따라서 현재 전술적 DDD는 가입·account graph 생성과 값 불변식에는 실제로 적용됐지만,
로그인·token·invitation·감사 생명주기 전체를 일관된 모델이 소유하지는 않습니다. 이 전체
coverage가 `전술적 DDD 선택 적용` 판정과 후속 계획의 보완 범위입니다.

## 각 아키텍처의 역할

| 적용 요소 | 이 프로젝트에서 해결하는 문제 |
| --- | --- |
| DDD | 업무 언어, 상태·생명주기와 소유권 경계를 찾습니다. |
| Modular Monolith | 하나의 Spring Boot 배포를 유지하면서 Java module을 격리합니다. |
| Hexagonal Architecture | module 내부 유스케이스와 Web·JPA·Security·외부 API를 분리합니다. |
| Layered Architecture | 규칙이 단순한 조회·기술 흐름에서 제한적으로 계층을 나눕니다. |
| Spring Modulith·ArchUnit | 공개 package와 의존 방향 같은 코드 경계를 build에서 검증합니다. |

Java module은 Bounded Context를 구현할 수 있는 구조적 수단이지만 자동으로 같은 개념은
아닙니다. 현재 `account`와 `auth`는 업무 언어·규칙·상태 생명주기가 확인된 Bounded Context
후보입니다. 그러나 둘의 Context 경계와 관계가 명시적으로 확정되지 않았으므로 module
이름만으로 독립 Bounded Context라고 선언하지 않습니다. `notification`은 현재 독립 업무
상태·정책 없이 발송 계약과 provider adapter를 소유하는 기술 통합 경계입니다. `shared`와
`config`도 각각 공통 계약과 애플리케이션 조립 경계이지 업무 Bounded Context가 아닙니다.

## 모듈별 판정

| module | Subdomain 유형 | 업무 Bounded Context 여부 | 전술적 DDD 근거 | 현재 gap·상세 source |
| --- | --- | --- | --- | --- |
| `account` | 미확정 | 현재 후보 | `Account`와 verification/reset/invitation challenge 모델이 account graph·credential·terminal 규칙을 소유합니다. | Subdomain 유형과 조직적 Context Map 관계는 미확정입니다. |
| `auth` | 미확정 | 현재 후보 | `RefreshSession` Aggregate Root와 auth service가 session access·refresh lifecycle을 소유합니다. | Subdomain 유형과 조직적 Context Map 관계는 미확정입니다. |
| `securityaudit` | Supporting 후보 | 현재 후보 | 공개 append 계약과 단일 `auth_event` writer가 감사 저장 생명주기를 소유합니다. | 보존·조회·retention 정책이 확장되면 Context 범위를 재검토합니다. |
| `notification` | 해당 없음 | 아님 | 외부 발송 계약·provider adapter·설정만 확인되며 별도 Domain Model이 필요하다는 업무 규칙 근거는 없습니다. | 현재 기술 통합 경계입니다. 독립 업무 언어·규칙·상태 생명주기가 생기면 Subdomain과 Bounded Context 지위를 재판정합니다. [210 모듈 지도](210-module-map.md) |
| `shared` | 해당 없음 | 아님 | 업무 규칙과 Domain Model을 두지 않습니다. | 업무 로직이 유입되지 않도록 현재 경계를 유지해야 합니다. [210 모듈 지도](210-module-map.md) |
| `config` | 해당 없음 | 아님 | 업무 규칙과 Domain Model을 두지 않으며 공용 `Clock`을 조립합니다. | 기술 composition 경계입니다. |

자동차 카탈로그, 커뮤니티와 그 밖의 미구현 제품 영역은 현재 main의 Java module이 아니므로
현재 source 판정 집합에 포함하지 않습니다. 그러나 새 module을 설계·구현할 때는 업무 종류와
관계없이 [120 DDD 개념과 적용 기준](../architecture/120-domain-driven-design.md)의 일반 기준을
동일하게 적용합니다. 미래 경계를 영구 현재 사실로 미리 만들지 않습니다.

## 현재 Context 후보와 기술 경계 협력

| 관계 | 공개 협력 | 현재 경계 판정 |
| --- | --- | --- |
| `auth` 후보 → `account` 후보 | 인증 subject 조회와 로그인 계측을 `account::api`로 사용합니다. | 내부 model·table을 우회하지 않는 공개 협력입니다. |
| `account` 후보 → `auth` 후보 | `CredentialChanged` 동기 사실을 발행합니다. | account credential transaction에 auth session 폐기를 참여시킵니다. |
| `account`·`auth` 후보 → `securityaudit` 후보 | REQUIRED 또는 REQUIRES_NEW append 공개 계약을 사용합니다. | 감사 table mapping은 securityaudit 내부에만 있습니다. |
| `account` 후보 → `notification` 기술 경계 | 발송 요청을 `notification::api`에 위임합니다. | 업무 상태와 token lifecycle은 `account`에 남고 `notification`은 provider 호출·template·오류 변환만 담당합니다. |
| 업무 후보 → `shared` | 안정된 오류 공개 계약을 사용합니다. | `shared`는 업무 Context가 아니라 공통 계약 경계입니다. |
| 애플리케이션 → `config` | 전역 Spring wiring과 HTTP·Security 조립을 적용받습니다. | `config`는 업무 Context가 아니라 composition 경계입니다. |

실제 package 의존, writer와 최종 persistence 상태는 [210 모듈 지도](210-module-map.md)가
소유합니다. table·transaction별 상세는 [220 Account/Auth 데이터 모델](220-account-auth-data-model.md)이
소유합니다. 조직적 Context 관계 유형은 현재 문서에서 추측해 확정하지 않습니다.

## Account/Auth 판정 근거 경로

이 문서는 Account/Auth 상세 사실을 중복 소유하지 않고 위 DDD 판정만 소유합니다.

- 업무 정책: [100 Account 도메인](../domain/100-account.md),
  [110 Account/Auth 인증 정책](../domain/110-account-auth-policy.md),
  [120 Auth Event 정책](../domain/120-auth-event-policy.md)
- 실제 module·package·의존·persistence 예외: [210 모듈 지도](210-module-map.md)
- table별 writer, operation별 application·transaction handoff:
  [220 Account/Auth 데이터 모델](220-account-auth-data-model.md#생명주기별-데이터-경계)

이 근거들은 목표 owner를 확정하지 않습니다. 현재 판정은 소유권 분할을 결함으로 식별하며,
최종 Context와 persistence owner는 후속 계획에서 대안·trade-off를 검증한 뒤 결정합니다.

## 배경

- 자동차 정보, 커뮤니티, account/auth는 서로 다른 업무 언어와 변경 이유를 가질 수 있습니다.
- 현재 규모에서 마이크로서비스는 배포, 데이터 일관성, 로컬 개발 비용이 이득보다 큽니다.
- 인증, 권한, token, 외부 이메일, persistence는 기술 의존성과 업무 정책을 분리할 필요가 있습니다.
- 단순 조회까지 모든 계층을 강제하면 매핑과 boilerplate가 업무 규칙보다 커질 수 있습니다.

## 검토한 선택지

### 계층 중심 단일 package

- 초기 코드는 단순하지만 업무 경계가 기술 계층에 묻힙니다.
- account와 auth 내부 구현의 직접 참조를 막기 어렵습니다.

### Modular Monolith와 module 내부 Hexagonal Architecture

- 단일 배포를 유지하면서 업무 module과 기술 adapter 경계를 분리합니다.
- 상태 변경과 외부 의존성이 있는 유스케이스를 순수하게 test할 수 있습니다.

### Microservices

- 장애와 배포 단위를 분리할 수 있지만 현재 제품·운영 규모에 비해 network, 배포, 관측성, transaction 비용이 큽니다.

## 선택 근거

- 단일 배포와 PostgreSQL transaction의 단순성을 유지합니다.
- 업무 module이 다른 module의 `internal` 구현을 직접 참조하지 않게 합니다.
- application use case는 Web, JPA, Security, Resend 구현과 Port로 분리합니다.
- Spring Modulith `ApplicationModules.verify()`와 ArchUnit test로 module·계층 경계를 자동 검증합니다.
- 조회 전용 흐름은 규칙과 변경 모델을 오염시키지 않는 범위에서 projection을 허용합니다.

## 영향

- 실제 Java 업무 package는 `account`, `auth`, `notification`, `community`, `moderation`, `media`,
  `shared`이며 `config`가 애플리케이션 조립을 담당합니다.
- module 간 협력은 공개 API 또는 named interface를 사용합니다.
- state change, 권한, transaction, 외부 연동이 있는 흐름은 UseCase, Port, Adapter를 명확히 둡니다.
- Spring Modulith은 module boundary 검증에 사용하며 독립 배포나 messaging을 자동 도입하지 않습니다.

## 배제한 대안

- 계층 중심 전역 package는 업무 응집도와 module encapsulation을 약화해 기본 구조로 사용하지 않습니다.
- Microservices는 현재 운영 비용이 과하므로 도입하지 않습니다.
- 모든 조회에 Domain 변환과 Port를 강제하는 구조는 실익보다 boilerplate가 커질 수 있어 사용하지 않습니다.

## 재검토 조건

- module 간 공개 계약만으로 해결하기 어려운 직접 참조가 반복됩니다.
- 특정 module이 독립 배포와 장애 격리를 요구합니다.
- 단일 DB transaction이 확장과 운영의 병목이 됩니다.
- 비동기 처리나 검색이 명확한 제품 요구와 측정된 부하를 가집니다.
- projection이 변경 모델에 재사용돼 업무 규칙을 우회합니다.
- Spring Modulith 또는 ArchUnit 검증이 실제 구조와 지속적으로 어긋납니다.

## 관련 문서

- **일반 판단 기준**: [100 아키텍처 판단 원칙](../architecture/100-architecture-principles.md), [120 DDD 개념과 적용 기준](../architecture/120-domain-driven-design.md), [200 헥사고날 아키텍처](../architecture/200-hexagonal-architecture.md)
- **현재 구현 배치**: [210 모듈 지도](210-module-map.md)
- **Account/Auth 데이터 경계**: [220 Account/Auth 데이터 모델](220-account-auth-data-model.md)
- **구조 검증 도구**: [코드 품질 도구](../tech-stack/310-code-quality-tools.md)
