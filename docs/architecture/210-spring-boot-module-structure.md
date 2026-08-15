# Spring Boot 모듈 구조 가이드

이 문서는 DDD, 모듈러 모놀리스, 헥사고날 아키텍처를 Spring Boot 프로젝트에 적용할 때 사용할 일반 패키지 구조를 정의합니다.
실제 프로젝트의 모듈명과 현재 결정은 `docs/project` 문서에 둡니다.

## 목적

- Spring Boot의 자동 구성과 컴포넌트 스캔을 활용하면서 업무 모듈 경계를 명확히 합니다.
- 패키지 이름만 보고 코드의 업무 책임과 공개 범위를 추론할 수 있게 합니다.
- 특정 도메인에 종속되지 않는 일반 구조 기준을 제공합니다.
- 실제 프로젝트 구조 문서와 원칙 문서가 섞이지 않게 합니다.

## 적용 범위

이 문서는 다음을 결정할 때 사용합니다.

- Spring Boot 루트 패키지 위치
- 모듈 우선 패키지 구조
- 모듈 내부의 `api`와 `internal` 경계
- 모듈 내부의 `domain`, `application`, `adapter`, `config` 배치
- 모듈 간 의존성 기준
- 헥사고날 적용 강도에 따른 패키지 단순화

## 기본 패키지 원칙

Spring Boot 메인 애플리케이션 클래스는 루트 패키지에 둡니다.
하위 모듈 패키지가 자동 스캔 범위에 들어오도록 루트 위치를 유지합니다.

```text
<root>
  <Application>
  <module-a>
  <module-b>
  shared
  config
```

실제 루트 패키지명은 프로젝트 구조 문서에서 관리합니다.

## 권장 구조

기본 구조는 모듈 우선 구조입니다.

```text
<root>
  <Application>

  <module>
    api
    internal
      domain
      application
        port
        service
      adapter
        in
          web
        out
          persistence
          external

  shared
    error
    time
    validation

  config
```

모듈 이름은 기술 계층이 아니라 업무 의미를 나타내야 합니다.
실제 모듈 이름은 `docs/project`와 `docs/domain`에서 관리합니다.

## `api`와 `internal`

각 업무 모듈은 공개 계약과 내부 구현을 구분합니다.

```text
<module>
  api
  internal
```

`api`는 다른 모듈이 의존할 수 있는 공개 계약입니다.
REST Controller 패키지가 아닙니다.

`api`에는 다음을 둘 수 있습니다.

- 다른 모듈이 호출하는 interface
- 모듈 간 협력에 필요한 공개 DTO
- 공개 식별자 타입
- 모듈 외부로 발행하는 이벤트 계약

`internal`은 해당 모듈 내부 구현입니다.
다른 모듈은 `internal`을 참조하지 않습니다.

`internal`에는 다음을 둡니다.

- Domain Model
- UseCase 구현
- Application Service
- Port
- Controller
- JPA Entity
- Spring Data Repository
- Persistence Adapter
- Mapper

의존성 예시는 다음과 같습니다.

```text
<module-a> -> <module-b>.api
<module-a> -X-> <module-b>.internal
```

HTTP Controller는 외부 사용자에게 API를 제공하지만 코드 의존성 관점에서는 모듈 내부 구현입니다.
따라서 `internal.adapter.in.web`에 둡니다.

## 모듈 내부 패키지

`domain`은 다음을 포함합니다.

- Domain Model
- Value Object
- Domain Policy
- Domain Exception
- Domain Event

`application`은 다음을 포함합니다.

- UseCase interface 또는 command handler
- Application Service
- Input Port
- Output Port
- Command와 Query
- 트랜잭션이 필요한 유스케이스 구현

`adapter.in.web`은 다음을 포함합니다.

- Controller
- Request DTO
- Response DTO
- Web 전용 Mapper
- API 예외 변환

`adapter.out.persistence`는 다음을 포함합니다.

- JPA Entity
- Spring Data Repository
- Persistence Adapter
- Entity/Domain Mapper
- Query Projection

`adapter.out.security`는 다음을 포함합니다.

- 인증 사용자 조회
- Security Principal 변환
- 인증/인가 Adapter

`adapter.out.external`, `adapter.out.storage`는 다음을 포함합니다.

- 외부 API client
- 파일 저장소 client
- 검색엔진 client
- 메시징 client
- 외부 예외 변환

`config`는 다음을 포함합니다.

- Spring Security 설정
- OpenAPI 설정
- WebMvc 설정
- Jackson 설정
- Bean wiring 설정

`shared`는 다음을 포함합니다.

- 여러 모듈에서 사용하는 기술 중립 유틸리티
- 공통 오류 응답 모델
- 시간, ID 생성, 검증 같은 공통 기반 타입

`shared`에는 특정 업무 규칙을 넣지 않습니다.
업무 규칙이 들어가기 시작하면 해당 업무 모듈로 이동합니다.

## 모듈 간 의존성 기준

기본 규칙은 다음과 같습니다.

- 다른 모듈의 `api`에는 의존할 수 있습니다.
- 다른 모듈의 `internal`에는 의존하지 않습니다.
- 순환 의존성을 만들지 않습니다.
- 모듈 간 협력은 공개 interface, 공개 DTO, 이벤트 계약으로 제한합니다.
- DB 테이블을 공유하는 방식으로 모듈 협력을 처리하지 않습니다.

현재 `account`와 `auth`가 `refresh_token`·`auth_event`를 서로 다른 JPA mapping으로 함께 쓰는 구현은 이 기준의 알려진 예외이지 권장 패턴이 아닙니다. 현재 사실과 제거 owner는 [모듈 지도의 Persistence 예외](../project/210-module-map.md#현재-persistence-예외)에서 확인합니다.

## 자동 검증 기준

모듈 경계와 계층 방향은 문서 규칙만으로 관리하지 않고 테스트로 검증합니다.
새 모듈, 새 공통 패키지, 새 adapter를 추가할 때는 `./gradlew check`에서 아키텍처 검증이 통과해야 합니다.

Spring Modulith 기준:

- Spring Boot 애플리케이션 루트의 직접 하위 패키지를 기본 애플리케이션 모듈로 봅니다.
- 모듈의 루트 패키지 또는 `@NamedInterface`로 공개한 패키지만 다른 모듈에서 참조할 수 있습니다.
- 다른 모듈의 `internal` 하위 패키지 참조는 금지합니다.
- 모듈 간 순환 의존은 금지합니다.
- 모듈별 허용 의존은 `@ApplicationModule(allowedDependencies = ...)`로 좁혀 선언합니다.

ArchUnit 기준:

- 다른 모듈의 `internal` 패키지 접근을 금지합니다.
- `shared`는 어떤 업무 애플리케이션 모듈에도 의존하지 않습니다.
- `domain`은 application, adapter, Spring, JPA, Servlet API에 의존하지 않습니다.
- `application`은 adapter 구현, Web API, JPA API, Servlet API에 의존하지 않습니다.
- persistence adapter의 JPA 구현 타입이 다른 계층으로 새어 나가지 않게 합니다.

검증 규칙을 바꿔야 할 때는 단순 테스트 수정이 아니라 아키텍처 결정 변경으로 봅니다.
작업 문서에 변경 이유와 영향 범위를 남기고, 관련 영구 문서를 함께 갱신합니다.

## 헥사고날 적용 강도

모듈 내부는 헥사고날 구조를 기본으로 합니다.
다만 모든 코드를 같은 강도로 나누지 않습니다.

강하게 적용하는 구조는 다음과 같습니다.

```text
adapter.in.web
  -> application.service
    -> domain
    -> application.port
      <- adapter.out.persistence
```

단순 조회에서 허용하는 구조는 다음과 같습니다.

```text
adapter.in.web
  -> application.query
    -> adapter.out.persistence projection
```

단순화해도 다음은 지킵니다.

- Controller에 비즈니스 규칙을 넣지 않습니다.
- JPA Entity를 API 응답으로 직접 노출하지 않습니다.
- 조회 전용 모델을 변경 유스케이스에 재사용하지 않습니다.
- 도메인 규칙이 생기면 Domain Model과 UseCase로 이동합니다.

## 네이밍 기준

- 모듈 이름은 업무 용어를 사용합니다.
- UseCase는 사용자 의도를 나타냅니다. 메서드는 `execute`를 기본값으로 사용합니다. 예: `Create<Aggregate>UseCase.execute(command)`
- Command는 변경 요청 입력을 나타냅니다. 예: `Create<Aggregate>Command`
- Query는 조회 요청 입력을 나타냅니다. 예: `Find<Aggregate>DetailQuery`
- Output Port는 필요한 능력이나 대상 경계를 나타내며, 타입명과 메서드명이 같은 동사를 반복하지 않게 합니다. 예: `<Aggregate>StorePort.save(...)`, `<Aggregate>LookupPort.find(...)`
- Adapter는 구현 기술이나 외부 시스템을 나타냅니다. 예: `<Aggregate>PersistenceAdapter`, `<Storage>FileStorageAdapter`
- Entity는 JPA 저장 구조를 나타냅니다. 예: `<Aggregate>JpaEntity`
- DTO, UseCase, Port, Entity factory의 상세 네이밍 기준은 [220-boundary-and-mapping-rules.md](220-boundary-and-mapping-rules.md)를 따릅니다.

## Spring Annotation 기준

- `domain`에는 Spring annotation을 두지 않습니다.
- `application`에는 가능한 한 Spring annotation을 줄입니다.
- UseCase 구현체에는 `@Service`를 둘 수 있습니다.
- 트랜잭션에는 `@Transactional`을 둘 수 있습니다.
- Controller, Repository, Configuration annotation은 Adapter와 Config 계층에 둡니다.
- Lombok annotation은 도메인 불변성과 JPA 제약을 해치지 않는 범위에서 제한적으로 사용합니다.

## 구조 재검토 조건

다음 신호가 생기면 패키지 구조를 다시 검토합니다.

- 특정 모듈이 지나치게 커져 내부 Bounded Context가 갈라집니다.
- 다른 모듈의 `internal` 참조가 반복적으로 필요해집니다.
- `shared`에 업무 규칙이 쌓입니다.
- 단순 조회 최적화 코드가 변경 유스케이스에 재사용됩니다.
- 모듈 간 순환 의존성이 생깁니다.
- 실제 프로젝트 구조 문서와 코드 구조가 달라집니다.

## 관련 참고

- [Spring Boot - Structuring Your Code](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)
- [Spring Modulith - Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)
- [Spring Modulith - Verifying Application Module Structure](https://docs.spring.io/spring-modulith/reference/verification.html)

## 관련 문서

- **용어·구조 원칙**: [아키텍처 개념](110-architecture-concepts.md), [헥사고날 아키텍처](200-hexagonal-architecture.md)
- **경계 변환 규칙**: [경계와 매핑 규칙](220-boundary-and-mapping-rules.md)
- **현재 프로젝트 적용**: [현재 프로젝트 아키텍처](../project/200-current-architecture.md)
- **기술 사용 기준**: [Spring Boot](../tech-stack/210-spring-boot.md)
