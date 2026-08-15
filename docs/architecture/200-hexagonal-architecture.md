# 헥사고날 아키텍처 적용 원칙

이 문서는 이 프로젝트에서 Hexagonal Architecture 또는 Ports and Adapters를 어떻게 적용할지 정의합니다.

## 목적

- 핵심 유스케이스와 도메인 규칙을 Web, DB, Security, 외부 API 같은 기술 세부사항에서 분리합니다.
- Port와 Adapter의 역할을 명확히 해 외부 기술 변경과 테스트 비용을 줄입니다.
- 헥사고날 아키텍처를 기본값으로 사용하되, 단순 조회와 규칙 없는 CRUD에는 과한 구조를 피합니다.

## 적용 범위

이 문서는 모듈 내부 구조를 설계하거나 구현할 때 적용합니다.

- UseCase를 둘지 판단할 때
- Port와 Adapter를 둘지 판단할 때
- JPA Repository를 직접 사용할지 Port 뒤로 감출지 판단할 때
- 외부 API, 파일 저장소, 검색엔진, 메시징, 캐시를 연동할 때
- 단순 조회를 별도 Read Model로 처리할지 판단할 때

## 개념 정의

헥사고날 아키텍처는 애플리케이션 내부와 외부 세계를 Port와 Adapter로 연결하는 구조입니다.

- 내부: Application, Domain, UseCase, Port
- 외부: Web, Persistence, Security, External API, Storage, Search
- Port: 내부가 외부에 기대하는 계약
- Adapter: 외부 기술로 Port를 구현하거나 외부 요청을 내부 UseCase로 변환하는 코드

이 문서에서 Domain Model은 비즈니스 규칙을 표현하는 객체 모델입니다.
Domain Model과 헥사고날 아키텍처는 같은 개념이 아닙니다.

## 기본 구조

모듈 내부의 기본 흐름은 다음과 같습니다.

```text
adapter.in.web
  -> application.usecase
    -> domain
    -> application.port.out
      <- adapter.out.persistence
      <- adapter.out.external
      <- adapter.out.storage
```

의존성 방향은 내부 정책을 향합니다.

```text
adapter -> application -> domain
adapter -> application.port
```

금지되는 방향은 다음과 같습니다.

```text
domain -> application
domain -> adapter
domain -> Spring/JPA/Web
application -> adapter implementation
application -> JPA Entity
application -> Controller DTO
```

## 계층별 책임

Domain은 다음을 담당합니다.

- 비즈니스 개념
- 상태와 불변조건
- 도메인 행위
- 도메인 예외
- 프레임워크 없는 단위 테스트 대상

Application은 다음을 담당합니다.

- 유스케이스 실행 순서
- 유스케이스 수준 검증
- 트랜잭션 경계
- Port 호출
- 도메인 객체 조합
- 도메인 이벤트 발행 판단

Inbound Adapter는 다음을 담당합니다.

- HTTP 요청 수신
- Request DTO validation
- Request DTO를 Command 또는 Query로 변환
- UseCase 호출
- 결과를 Response DTO로 변환
- HTTP 상태와 에러 응답 변환

Outbound Adapter는 다음을 담당합니다.

- Port 구현
- JPA Entity와 DB 접근
- 외부 API 호출
- 파일 저장소, 검색엔진, 메시징, 캐시 연동
- 외부 기술 예외를 application/domain 의미로 변환

Config는 다음을 담당합니다.

- Spring Boot 실행과 Bean 구성
- Security, OpenAPI, Jackson, WebMvc 설정
- Profile, secret, 외부 연동 설정

## Port 설계 기준

Port는 기술이 아니라 내부 유스케이스가 필요한 능력을 표현합니다.

좋은 예:

```text
PostLookupPort
PostStorePort
MemberPostingPermissionPort
CarModelLookupPort
ImageUploadPort
```

피해야 할 예:

```text
PostJpaRepositoryPort
CommonDatabasePort
CommunityPersistencePort
```

Port는 다음 기준으로 설계합니다.

- Application이 필요한 메서드만 노출합니다.
- Port 타입명과 메서드명이 같은 동사를 기계적으로 반복하지 않게 호출부 기준으로 점검합니다.
- `JpaRepository` 전체를 Port로 감싸지 않습니다.
- 저장 Port와 조회 Port는 필요하면 분리합니다.
- Query 조건에 JPA Specification 같은 구현 기술을 노출하지 않습니다.
- 다른 모듈의 내부 구현을 대신 노출하는 Port를 만들지 않습니다.

## Adapter 설계 기준

Adapter는 특정 기술과 특정 책임을 연결합니다.
하나의 Adapter에 모든 도메인을 몰아넣지 않습니다.

좋은 예:

```text
PostPersistenceAdapter
CommentPersistenceAdapter
CarModelPersistenceAdapter
S3ImageStorageAdapter
OpenSearchCatalogSearchAdapter
```

피해야 할 예:

```text
PersistenceAdapter
CommunityMegaAdapter
CommonExternalAdapter
```

Adapter 분리 기준은 다음과 같습니다.

- 같은 Aggregate나 같은 외부 시스템 책임은 하나의 Adapter가 맡을 수 있습니다.
- 서로 다른 도메인 모듈의 Repository를 함께 들고 있으면 분리합니다.
- Mapper, Entity, Repository는 Adapter 내부 구현으로 둡니다.
- 외부 예외는 Adapter에서 내부 의미의 예외로 변환합니다.

## 강하게 적용하는 경우

다음 경우에는 UseCase, Port, Adapter를 명시적으로 둡니다.

- 상태를 변경하는 유스케이스
- 도메인 규칙이 있는 생성, 수정, 삭제
- 권한과 소유자 검증이 필요한 작업
- 여러 Aggregate 또는 외부 시스템을 조합하는 작업
- 파일 저장소, 외부 API, 검색엔진, 메시징, 캐시 연동
- 실패 처리, 재시도, 보상 처리, 감사 로그가 필요한 작업

공통 신호는 다음과 같습니다.

- 도메인 규칙이 있습니다.
- 상태 전이가 있습니다.
- 외부 의존성이 있습니다.
- 권한 정책이 있습니다.
- 트랜잭션과 데이터 무결성이 중요합니다.
- 테스트에서 외부 의존성을 대체해야 합니다.

## 단순화하는 경우

다음 경우에는 헥사고날 구조를 가볍게 적용할 수 있습니다.

- 목록 조회
- 상세 조회
- 코드성 데이터 조회
- 내부 관리용 단순 검색
- 도메인 규칙 없는 단순 필터와 페이징

허용되는 구조 예시는 다음과 같습니다.

```text
Web Adapter -> Query Service -> Repository Projection -> Response DTO
```

단순화해도 다음은 지킵니다.

- JPA Entity를 API 응답으로 직접 노출하지 않습니다.
- 조회 전용 DTO를 변경 유스케이스에 재사용하지 않습니다.
- Controller에 비즈니스 규칙을 넣지 않습니다.
- 단순화한 이유와 나중에 분리할 조건을 남깁니다.

## Spring Boot 적용 기준

Spring은 바깥 계층의 조립 도구로 사용하고 domain을 framework에서 독립시킵니다. package별 Spring annotation 배치와 transaction annotation의 exact 기준은 [Spring Boot 모듈 구조](210-spring-boot-module-structure.md#spring-annotation-기준)가 단일 owner입니다.

## 좋은 신호

- 핵심 로직 테스트가 Spring Context 없이 실행됩니다.
- Controller를 바꿔도 UseCase와 Domain은 거의 변하지 않습니다.
- DB 스키마나 JPA 전략 변경이 Domain 규칙을 흔들지 않습니다.
- 외부 API 장애 처리 정책이 Adapter에 격리됩니다.
- UseCase 이름만 봐도 사용자의 의도가 드러납니다.
- 단순 조회는 과한 Port와 Mapper 없이 읽기 쉽게 유지됩니다.

## 나쁜 신호

- Controller에 비즈니스 규칙이 들어갑니다.
- Domain이 `@Entity`, `@Table`, `@Column`에 묶입니다.
- Application이 `JpaRepository`를 직접 사용합니다.
- API DTO가 Domain 내부에서 사용됩니다.
- 모든 단순 조회에 기계적으로 Port와 Mapper를 만듭니다.
- Adapter 하나가 여러 도메인의 Repository를 모두 알고 있습니다.
- 테스트가 대부분 `@SpringBootTest`에 의존합니다.
- 순환 의존성이 생기거나 Mapper가 양방향으로 참조합니다.

## 관련 참고

- [Alistair Cockburn - Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Robert C. Martin - The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Spring Boot - Structuring Your Code](https://docs.spring.io/spring-boot/reference/using/structuring-your-code.html)

## 관련 문서

- **용어 기준**: [아키텍처 개념 구분](110-architecture-concepts.md)
- **구현 구조**: [Spring Boot 모듈 구조](210-spring-boot-module-structure.md)
- **경계 변환 규칙**: [경계와 매핑 규칙](220-boundary-and-mapping-rules.md)
