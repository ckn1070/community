# 모듈 지도

이 문서는 `src/main/java/com/ronams/carmunity`의 현재 최상위 package와 책임 경계를 소유합니다.
미구현 제품 영역은 module 후보로 미리 등록하지 않고 product backlog에서 관리합니다.

## 현재 모듈

| 모듈 | 상태 | 책임 |
| --- | --- | --- |
| `account` | 구현됨 | 인증 주체, profile, password credential, role assignment, verification challenge, admin invitation |
| `auth` | 구현됨 | password session, JWT 발급·검증, refresh token lifecycle, logout, Bearer 인증 경계 |
| `securityaudit` | 구현됨 | 인증·인가 보안 감사 append 계약과 `auth_event` 단일 persistence writer |
| `notification` | 구현됨 | 이메일 발송 공개 API, Resend adapter, transactional template, provider 오류 변환 |
| `catalog` | 조회 API 구현됨 | 신차 제품·사양·판매 정보의 읽기 전용 경계, 차체·동력 형식·연료·판매 상태 공개 enum과 브랜드·모델 목록·상세 application/persistence/Web adapter |
| `shared` | 구현됨 | 여러 module이 공유하는 안정된 공개 계약과 기반 type. `shared.error`가 `ErrorCode`, `CommonErrorCode`, `ApiException`, `FieldErrorDetail` 계약을 소유합니다. |
| `config` | 구현됨 | Spring MVC, Security, OpenAPI, request id, logging, 공통 예외 처리·HTTP `ProblemDetail` 변환과 애플리케이션 조립 |

`config`는 업무 Bounded Context가 아니라 애플리케이션 조립 경계입니다.
`shared`에는 여러 module이 실제로 공유하는 계약만 두며 업무 로직을 모으지 않습니다.
`notification`은 현재 독립 업무 상태·정책 없이 발송 계약과 provider adapter를 소유하는
기술 통합 경계입니다. 독립 업무 언어·규칙·상태 생명주기가 생기면 Bounded Context 후보
여부를 다시 판정합니다.

## 현재 협력

```text
auth -> account :: api
auth -X-> account.internal

account/auth -> securityaudit :: api
account/auth -X-> securityaudit.internal

account -> notification :: api
account -X-> notification.internal

account --(CredentialChanged 동기 event)--> auth session 폐기
config TimeConfiguration --(Spring DI)--> account/auth/securityaudit Clock consumer
catalog -> shared :: error
```

- `auth`는 account의 인증 subject와 로그인 계측 경계를 공개 계약으로 사용합니다.
- `account`는 account 상태, email verification·password reset token과 admin invitation의
  생명주기를 소유하고 이메일 전달은 notification에 위임합니다.
- `auth`는 최초 발급, rotation·reuse·logout과 credential 변경에 따른 폐기를 포함한
  refresh session 전체 생명주기와 `refresh_token` 단일 writer를 소유합니다.
- `securityaudit`는 caller transaction 참여와 `REQUIRES_NEW` 독립 보존 계약을 분리하고
  `auth_event`의 유일한 JPA mapping·writer를 소유합니다.
- `notification`은 account 상태와 token lifecycle을 소유하지 않습니다.
- 공용 UTC `Clock`은 `config.TimeConfiguration`만 생성하고 업무 module은 주입받습니다.
- `catalog`는 다른 module의 내부 상태를 쓰지 않습니다. 공개 package는 검색 입력에 사용하는
  차체·동력·연료·판매 상태 enum만 제공하고, 조회 use case·read model·port는
  `internal.application`, projection SQL은 `internal.adapter.out.persistence`에 둡니다.
  jOOQ 구현은 `internal.adapter.out.persistence.jooq`에서 조회 능력별 adapter와 상세 조립·JSON
  변환 책임으로 분리합니다.
  `internal.adapter.in.web.publicapi`는 세 공개 GET의 request·response mapping을 소유하고
  공통 `ProblemDetail` 변환을 위해 `shared :: error` 공개 계약만 사용합니다.

### Persistence 소유권과 전환 상태

```text
account persistence       ──> account 계열 table
auth persistence          ──> refresh_token
securityaudit persistence ──> auth_event
```

- `DddOwnershipSourceTest`가 `auth_event`·`refresh_token`의 단일 mapping과 `auth`의
  `account` table 직접 read 금지를 검증합니다.
- V13·V14 expand migration은 기존 `account_id`와 새 `account_public_id`를 양방향 trigger로
  동기화하고 불일치를 거부합니다. Application writer는 public id만 사용합니다.
- 구 `account_id` column·FK·trigger 제거는 이전 application rollback이 필요 없다는 운영 관찰
  뒤 별도 contract migration으로 수행합니다. 현재 expand 상태를 최종 contract처럼 쓰지 않습니다.

각 module의 전략적·전술적 DDD 적용 판정과 현재 Context 후보 관계는
[200 현재 프로젝트 아키텍처](200-current-architecture.md#현재-ddd-적용-판정)가
소유합니다. 이 문서는 실제 package·협력·persistence 예외만 소유하며 module을 Bounded
Context로 확정하지 않습니다.

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

- **현재 DDD 적용 판정**: [200 현재 프로젝트 아키텍처](200-current-architecture.md#현재-ddd-적용-판정)
- **일반 배치 기준**: [210 Spring Boot 모듈 구조](../architecture/210-spring-boot-module-structure.md)
- **자동차 카탈로그 업무 경계**: [130 자동차 카탈로그 도메인](../domain/130-car-catalog.md)
- **자동차 카탈로그 물리 구조**: [110 자동차 카탈로그 Schema Reference](schema/110-car-catalog.md)
- **제품 범위**: [200 기능 범위](../product/200-feature-scope.md)
- **미확정 횡단 구조**: [001 프로젝트 횡단 열린 결정](../work-items/backlog/2026/001-project-open-decisions.md)
- **후속 정리 작업**: [009 비카탈로그 DDD 보완](../work-items/improvements/2026/009-non-catalog-ddd-hardening.md)
