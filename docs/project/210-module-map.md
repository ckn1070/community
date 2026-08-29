# 모듈 지도

이 문서는 `src/main/java/com/ronams/carmunity`의 현재 최상위 package와 책임 경계를 소유합니다.
미구현 제품 영역은 module 후보로 미리 등록하지 않고 product backlog에서 관리합니다.

## 현재 모듈

| 모듈 | 상태 | 책임 |
| --- | --- | --- |
| `account` | 구현됨 | 인증 주체, profile, password credential, role assignment, verification token, admin invitation, auth event |
| `auth` | 구현됨 | password session, JWT 발급·검증, refresh token lifecycle, logout, Bearer 인증 경계 |
| `notification` | 구현됨 | 이메일 발송 공개 API, Resend adapter, transactional template, provider 오류 변환 |
| `catalog` | 조회 기반 구현됨 | 신차 제품·사양·판매 정보의 읽기 전용 경계, 차체·동력 형식·연료·판매 상태 공개 enum과 브랜드·모델 목록·상세 application/persistence. Web API는 아직 미구현입니다. |
| `shared` | 구현됨 | 여러 module이 공유하는 안정된 공개 계약과 기반 type. `shared.error`가 `ErrorCode`, `CommonErrorCode`, `ApiException`, `FieldErrorDetail` 계약을 소유합니다. |
| `config` | 구현됨 | Spring MVC, Security, OpenAPI, request id, logging, 공통 예외 처리·HTTP `ProblemDetail` 변환과 애플리케이션 조립 |

`config`는 업무 Bounded Context가 아니라 애플리케이션 조립 경계입니다.
`shared`에는 여러 module이 실제로 공유하는 계약만 두며 업무 로직을 모으지 않습니다.

## 현재 협력

```text
auth -> account :: api
auth -X-> account.internal

account -> notification :: api
account -X-> notification.internal

catalog -> 외부 module 의존 없음

ChangePasswordService 등 --(Spring DI: java.time.Clock)--> auth.internal JwtTokenConfiguration
email verification·invitation·password reset service --> Clock.systemUTC() 직접 생성
```

- `auth`는 account의 인증 subject와 저장 경계를 공개 계약으로 사용합니다.
- `account`는 token과 account 상태 전이를 소유하고 이메일 전달은 notification에 위임합니다.
- `notification`은 account 상태와 token lifecycle을 소유하지 않습니다.
- `catalog`는 다른 module의 내부 상태를 쓰지 않습니다. 공개 package는 검색 입력에 사용하는
  차체·동력·연료·판매 상태 enum만 제공하고, 조회 use case·read model·port는
  `internal.application`, projection SQL은 `internal.adapter.out.persistence`에 둡니다.
  현재 Web adapter는 없으며 다음 016 작업 단계에서 추가합니다.
- `ChangePasswordService`처럼 실제 `Clock` bean을 주입받는 일부 `account` bean은 production의 유일한 provider인 `auth.internal`의 `JwtTokenConfiguration`에 의존합니다. 이메일 인증·관리자 초대·비밀번호 재설정 service는 public constructor에서 `Clock.systemUTC()`를 직접 만들어 이 bean을 우회합니다. 전자는 source import에 드러나지 않는 runtime DI 예외이고, 후자는 공용 시간 조립 경계가 일관되지 않은 예외입니다.

`Clock` provider를 중립적인 조립 경계로 이동하고 이 예외를 제거하는 작업은 [공용 Clock bean의 auth 내부 소유](../work-items/bugs/2026/003-shared-clock-bean-ownership.md)가 소유합니다.

### 현재 Persistence 예외

Java package 의존은 위 공개 계약을 지키지만 물리 persistence는 아직 같은 경계를 이루지 못합니다.

```text
account persistence ─┬─> refresh_token <─┬─ auth persistence
                     └─> auth_event    <─┘
```

- `account`의 `RefreshTokenJpaEntity`·`AuthEventJpaEntity` 계열은 로그인, 비밀번호 변경·재설정과 account 감사 흐름에서 두 table을 씁니다.
- `auth`의 `AuthRefreshTokenJpaEntity`·`AuthTokenEventJpaEntity` 계열은 rotation, logout, reuse detection과 account 상태 거부에서 같은 두 table을 씁니다.
- `auth`의 `AuthRefreshTokenJpaRepository`는 native SQL로 `account` table을 직접 읽어 내부 account id를 public id로 바꿉니다. Java package 의존은 아니지만 `account.api` 협력을 우회하는 물리 DB 의존입니다.
- 따라서 “`auth`가 refresh token lifecycle을 소유한다”는 논리 책임만으로 단일 물리 writer를 뜻하지 않습니다. 현재는 두 module이 같은 table을 별도 entity·repository로 매핑하는 예외입니다.

이 구조는 모듈 간 협력을 공유 table로 처리하지 않는 일반 기준을 충족하지 않습니다. 단일 persistence owner와 transaction 공개 계약으로 이관하고 `auth`의 `account` table 직접 조회를 제거하거나 명시적인 소유 경계로 바꾸는 후속 범위는 [Account 인증/인가 후속 기능 Backlog](../work-items/backlog/2026/003-account-auth-follow-up.md#authaccount-persistence-owner-정리)가 소유합니다.

## 배치 기준

새 기능을 배치할 때 다음을 확인합니다.

- 핵심 업무 용어와 생명주기를 누가 소유하는가
- 상태 전이와 transaction이 어느 module에 묶이는가
- 다른 module이 내부 Entity와 table을 알아야 하는가
- 외부 기술을 Port로 대체해야 하는가
- 공개 계약으로 협력할 수 있는가

커뮤니티, member profile, moderation, media, listing은 현재 Java module이 아닙니다.
실제 기능 설계가 확정될 때 별도 module 또는 기존 module 소유 여부를 결정합니다.

## 관련 문서

- **현재 구조 SOT**: [프로젝트 아키텍처](200-current-architecture.md)
- **일반 배치 기준**: [210 Spring Boot 모듈 구조](../architecture/210-spring-boot-module-structure.md)
- **자동차 카탈로그 업무 경계**: [130 자동차 카탈로그 도메인](../domain/130-car-catalog.md)
- **자동차 카탈로그 물리 구조**: [110 자동차 카탈로그 Schema Reference](schema/110-car-catalog.md)
- **제품 범위**: [200 제품 기능 범위](../product/200-feature-scope.md)
- **미확정 횡단 구조**: [프로젝트 횡단 열린 결정](../work-items/backlog/2026/001-project-open-decisions.md)
- **현재 persistence 예외 owner**: [Account 인증/인가 후속 기능 Backlog](../work-items/backlog/2026/003-account-auth-follow-up.md#authaccount-persistence-owner-정리)
