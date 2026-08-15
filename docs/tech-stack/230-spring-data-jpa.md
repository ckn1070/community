# Spring Data JPA 사용 가이드

이 문서는 Spring Data JPA, Repository, Transaction, Query, Projection 사용 기준을 정의합니다.

## 목적

- JPA를 영속성 어댑터로 사용하면서 도메인 규칙을 보호합니다.
- Repository와 Query를 목적에 맞게 설계합니다.
- 트랜잭션과 조회 성능 문제를 사전에 줄입니다.

## 기본 원칙

- JPA Entity는 DB 저장 구조를 표현합니다.
- Domain Model과 JPA Entity는 필요하면 분리합니다.
- Application은 Spring Data Repository 구현 세부사항에 직접 의존하지 않는 것을 기본값으로 둡니다.
- Repository method 이름이 복잡해지면 명시적 query나 query model을 검토합니다.
- 목록 조회는 필요한 필드만 가져오는 Projection을 검토합니다.
- 트랜잭션 경계는 Application UseCase 단위로 둡니다.

## Repository 기준

Spring Data JPA는 method name 기반 query derivation과 명시적 query를 지원합니다.

프로젝트 기준:

- 단순 조건은 derived query를 사용할 수 있습니다.
- 이름이 길어지거나 의미가 불명확하면 `@Query` 또는 별도 query adapter를 검토합니다.
- `JpaRepository`를 application/domain port로 그대로 노출하지 않습니다.
- Application port는 필요한 저장/조회 능력만 드러냅니다.
- 조회와 저장의 책임이 커지면 port를 분리합니다.

## Transaction 기준

- 변경 UseCase는 하나의 명확한 트랜잭션 경계를 갖습니다.
- Controller에 `@Transactional`을 두지 않습니다.
- Domain에 `@Transactional`을 두지 않습니다.
- 여러 repository 호출을 하나의 유스케이스로 묶을 때 Application service에 트랜잭션을 둡니다.
- 조회 전용 UseCase는 read-only 트랜잭션을 검토합니다.
- 외부 API 호출은 DB transaction 안에 오래 묶지 않습니다.

## Entity 설계 기준

- Entity는 protected no-args constructor를 둘 수 있습니다.
- 식별자 생성 전략은 DB와 운영 요구를 기준으로 선택합니다.
- 양방향 연관관계는 꼭 필요한 경우에만 둡니다.
- collection 연관관계는 캡슐화하고 외부에서 직접 수정하지 않게 합니다.
- cascade와 orphan removal은 삭제 영향 범위를 명확히 알고 사용합니다.
- equals/hashCode는 식별자와 영속성 생명주기를 고려해 신중히 작성합니다.

## Fetch 전략과 N+1

- 기본 fetch 전략은 보수적으로 설계합니다.
- 응답에 필요한 연관 데이터는 query에서 의도적으로 가져옵니다.
- N+1 위험이 있으면 fetch join, EntityGraph, batch size, projection, 별도 read query를 비교합니다.
- Open Session in View에 기대어 응답 직렬화 중 lazy loading을 발생시키지 않습니다.
- JPA Entity를 API 응답으로 직접 반환하지 않습니다.

## Projection 기준

Spring Data JPA는 interface projection, DTO projection, class-based projection을 지원합니다.

프로젝트 기준:

- 목록/검색 API는 필요한 필드만 선택하는 Projection을 우선 검토합니다.
- Projection은 read model로 보고 변경 로직에 재사용하지 않습니다.
- 복잡한 응답 조립은 query adapter나 application read service에서 명시적으로 처리합니다.
- nested projection은 조인 범위와 실제 SQL을 확인합니다.

## Query 기준

- 단순 query는 repository method로 둡니다.
- 복잡한 동적 조건은 별도 query component를 둡니다.
- pagination은 count query 비용을 고려합니다.
- 전체 count가 필요 없으면 `Slice` 또는 cursor 방식도 검토합니다.
- 대량 처리에는 stream 사용 시 resource close를 명확히 합니다.

## 아키텍처 경계 적용

- JPA Entity는 `adapter.out.persistence`에 둡니다.
- Spring Data Repository도 persistence adapter 내부에 둡니다.
- Application port는 domain/application 패키지에 둡니다.
- Persistence Adapter가 port를 구현하고 Entity/Domain mapping을 담당합니다.
- Domain은 `@Entity`, `@Column`, `LazyInitializationException` 같은 JPA 세부사항을 몰라야 합니다.

## 테스트 기준

- Domain/Application 로직은 JPA 없이 단위 테스트합니다.
- Repository query, mapping, migration 영향은 JPA slice 또는 통합 테스트로 검증합니다.
- PostgreSQL 고유 SQL, JSONB, index, constraint는 실제 PostgreSQL에 가까운 환경을 검토합니다.
- 테스트 데이터는 각 테스트가 독립적으로 이해되게 둡니다.

## 피해야 할 패턴

- Entity를 Request/Response DTO로 직접 사용합니다.
- Controller에서 Repository를 직접 호출합니다.
- Lazy loading에 의존해 응답을 조립합니다.
- 모든 query를 derived method name으로 해결하려고 합니다.
- pagination API에서 불필요한 count query를 반복합니다.
- 테스트가 H2에서만 통과하고 PostgreSQL에서 실패할 수 있는 SQL을 방치합니다.

## 관련 참고

- [Spring Data JPA - Defining Query Methods](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html)
- [Spring Data JPA - JPA Query Methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)
- [Spring Data JPA - Projections](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html)
- [Spring Data JPA - Transactionality](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)
- [Hibernate ORM User Guide](https://docs.jboss.org/hibernate/stable/orm/userguide/html_single/Hibernate_User_Guide.html)

## 관련 문서

- **database 계약**: [PostgreSQL](250-postgresql.md)
- **schema migration**: [Flyway](240-flyway.md)
- **persistence 경계**: [경계와 매핑 규칙](../architecture/220-boundary-and-mapping-rules.md)
