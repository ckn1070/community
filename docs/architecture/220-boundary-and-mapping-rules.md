# 경계와 매핑 규칙

이 문서는 DTO, Domain, JPA Entity, Port, Adapter 사이의 경계와 매핑 규칙을 정의합니다.

## 목적

- 프레임워크와 저장소 모델이 도메인 규칙을 오염시키지 않게 합니다.
- 계층 간 데이터 이동 방식을 명확히 합니다.
- 매핑 비용과 구조적 이득 사이의 균형을 잡습니다.
- 경계 객체 이름이 호출부에서 같은 의미를 반복하지 않게 합니다.

## 기본 원칙

- 외부 경계의 객체는 내부 계층으로 그대로 전달하지 않습니다.
- API DTO, JPA Entity, Domain Model은 역할이 다릅니다.
- Domain은 비즈니스 의미와 규칙을 표현합니다.
- Entity는 DB 저장 구조와 ORM 요구사항을 표현합니다.
- DTO는 API 계약과 입출력 표현을 담당합니다.
- 매핑은 경계에서 수행합니다.

## 객체별 역할

Request DTO는 다음을 담당합니다.

- HTTP 요청 구조
- Web validation
- JSON 필드명
- API 문서화 annotation

Response DTO는 다음을 담당합니다.

- HTTP 응답 구조
- API 소비자에게 노출할 필드 선택
- 내부 모델 은닉

Domain Model은 다음을 담당합니다.

- 도메인 상태
- 불변조건
- 의미 있는 행위
- 도메인 예외

JPA Entity는 다음을 담당합니다.

- 테이블 매핑
- 연관관계 매핑
- 식별자 매핑
- 영속성 생명주기

Command/Query는 다음을 담당합니다.

- UseCase 입력
- Web 기술과 무관한 요청 의도
- 테스트하기 쉬운 입력 모델

Port는 다음을 담당합니다.

- Application이 외부에 기대하는 능력
- 저장소, 외부 API, 인증 정보 같은 외부 의존성 추상화

Adapter는 다음을 담당합니다.

- Port 구현
- 외부 기술 호출
- 외부 예외를 application/domain 의미로 변환

## 매핑 위치

권장 매핑 위치는 다음과 같습니다.

```text
Request DTO -> Command: Web Adapter
Command -> Domain: UseCase 또는 Domain factory
Domain -> JPA Entity: Persistence Adapter
JPA Entity -> Domain: Persistence Adapter
Domain -> Response DTO: Web Adapter 또는 Read Model Mapper
```

조회 전용이고 도메인 규칙을 거치지 않는 경우에는 Projection 또는 Read DTO를 사용할 수 있습니다.
단, 조회 모델이 도메인 정책을 우회해서 변경 로직에 재사용되면 안 됩니다.

## 경계 객체 네이밍 기준

이름은 선언 위치가 아니라 호출부 문장을 기준으로 판단합니다.
타입명, 변수명, 메서드명, 반환 타입에 같은 의미가 반복되면 더 짧고 안정적인 이름으로 단순화합니다.

기본 원칙은 다음과 같습니다.

- 한 호출식 안에서 같은 어간이 반복되면 먼저 이름을 줄일 수 있는지 확인합니다.
- 같은 뜻의 동사를 타입명과 메서드명에 동시에 넣지 않습니다.
- 계층별로 널리 쓰이는 매핑 이름은 유지하고, 모든 계층을 하나의 동사로 억지 통일하지 않습니다.
- 예외가 필요하면 작업 문서나 코드 가까이에 반복을 허용하는 이유를 남깁니다.

피해야 할 형태는 다음과 같습니다.

```java
registerMemberAccountUseCase.register(command);
SavedRegisteredMemberAccount savedAccount = saveRegisteredMemberAccountPort.save(account);
```

### UseCase

UseCase 이름은 사용자의 작업 의도를 나타내고, 실행 메서드는 `execute`를 기본값으로 사용합니다.

권장 형태:

```java
registerMemberAccountUseCase.execute(command);
findMemberProfileUseCase.execute(query);
```

피해야 할 형태:

```java
registerMemberAccountUseCase.register(command);
findMemberProfileUseCase.find(query);
```

UseCase가 둘 이상의 독립 작업을 노출해야 한다면 먼저 UseCase를 분리할 수 있는지 검토합니다.
분리하지 않는 선택은 기본값이 아니며, 여러 메서드를 둬야 하는 이유와 수용 기준을 먼저 명시합니다.

### DTO와 매핑 factory

DTO 경계에서는 이동 방향이 이름에 드러나야 합니다.

- Request DTO에서 application command로 변환할 때는 `toCommand()`를 사용합니다.
- Query DTO에서 application query로 변환할 때는 `toQuery()`를 사용합니다.
- Application result나 read model에서 Response DTO를 만들 때는 `from(source)`를 사용합니다.
- 이미 의미가 있는 여러 값을 단순 조립할 때만 `of(...)`를 사용합니다.

`from`과 `of`의 기준은 다음과 같습니다.

| 이름 | 사용 기준 | 예시 |
| --- | --- | --- |
| `from(source)` | 하나의 source model, raw value, 외부 표현을 현재 타입으로 변환합니다. | `EmailAddress.from(rawEmail)`, `MemberResponse.from(result)` |
| `of(values...)` | 이미 검증되거나 의미가 분명한 여러 값을 조립합니다. | `PageRange.of(start, size)` |
| 의미 있는 factory 이름 | 고정된 업무 의미나 기본값을 숨기지 않고 드러냅니다. | `AuthEventJpaEntity.signUpSuccess(accountId)` |

의미 있는 factory 이름은 `of`보다 길어도 허용합니다.
예를 들어 특정 이벤트 타입과 성공 결과를 함께 고정하는 factory는 `of(accountId)`보다 `signUpSuccess(accountId)`가 낫습니다.
다만 같은 계층의 다른 factory와 섞여 보이면 기준을 문서나 테스트로 고정합니다.

### Port

Port 이름은 Application이 외부에 기대하는 능력이나 대상 경계를 나타냅니다.
메서드 이름은 실제 요청 동작을 나타내되, Port 타입명과 같은 동사를 기계적으로 반복하지 않습니다.

권장 형태:

```java
memberAccountStorePort.save(account);
authRoleLookupPort.existsByCode(roleCode);
```

피해야 할 형태:

```java
SaveRegisteredMemberAccountPort.save(account);
LoadAuthRolePort.load(roleCode);
CheckAccountUniquenessPort.check(...);
```

Port 결과 타입도 호출부 반복을 기준으로 정합니다.
저장 결과를 의미할 때마다 `Saved...`를 붙이는 대신, 유스케이스가 이후 단계에서 사용하는 값의 역할을 드러냅니다.

예시:

```java
PersistedMemberAccount account = memberAccountStorePort.save(memberAccount);
MemberAccountRecord accountRecord = memberAccountStorePort.save(memberAccount);
```

### JPA Entity factory

JPA Entity factory는 저장 모델 생성 의도를 기준으로 이름을 고릅니다.

- Domain model이나 application model 하나를 저장 모델로 변환하면 `from(source)`를 사용합니다.
- 이미 의미가 분명한 여러 값을 저장 모델로 단순 조립하면 `of(...)`를 사용할 수 있습니다.
- 고정된 이벤트 타입, 결과, 기본값, 정책을 함께 설정하면 업무 의미가 드러나는 이름을 사용합니다.

단, 같은 패키지에서 `from`, `of`, 의미 있는 이름이 함께 쓰이면 각각의 사용 이유가 위 기준으로 설명 가능해야 합니다.
설명할 수 없으면 `from` 또는 `of`로 통일합니다.

## Entity와 Domain 분리 기준

업무 규칙이 Domain Model을 필요로 하는지는
[120 DDD 개념과 적용 기준](120-domain-driven-design.md#domain-model)이 소유합니다.
이 절은 Domain Model을 사용하기로 한 뒤, persistence 요구와 업무 표현의 차이에 따라
JPA Entity를 별도 모델로 둘지와 어느 경계에서 매핑할지를 판단합니다.

분리를 우선하는 경우는 다음과 같습니다.

- JPA annotation, proxy, 기본 생성자나 변경 감지가 Domain Model의 불변성을 약화합니다.
- table·연관관계 구조와 Domain Model의 객체 경계가 다릅니다.
- 하나의 Domain Model을 여러 persistence 표현으로 저장해야 합니다.
- 저장 전략 변경이 Domain Model의 공개 계약까지 바꾸면 안 됩니다.
- persistence 없이 Domain Model을 생성·검증해야 하는데 Entity 제약이 이를 방해합니다.

합쳐도 되는 경우는 다음과 같습니다.

- 선택한 Domain Model과 table 구조가 거의 같은 경계를 표현합니다.
- JPA 요구가 생성 규칙과 불변조건을 깨뜨리지 않습니다.
- 별도 persistence model과 매핑을 추가해도 독립성이나 변경 격리가 개선되지 않습니다.
- persistence 표현이 달라질 때 분리할 조건을 알고 있습니다.

합치는 경우에도 API DTO로 Entity를 직접 노출하지 않습니다.

Port의 목적형 이름, 최소 method 노출, 조회·저장 분리와 persistence 기술 비노출 기준은 [헥사고날 아키텍처의 Port 설계 기준](200-hexagonal-architecture.md#port-설계-기준)이 단일 owner입니다. 이 문서는 Port를 통과하는 boundary model과 매핑만 소유합니다.

## Mapper 기준

- 매핑은 단순하고 명시적으로 작성합니다.
- 복잡한 비즈니스 규칙을 Mapper에 넣지 않습니다.
- 양방향 Mapper가 순환 참조를 만들지 않게 합니다.
- 반복이 작으면 수동 매핑을 우선합니다.
- 매핑 라이브러리는 반복이 충분히 크고 규칙을 문서화할 수 있을 때 도입합니다.

## 예외 변환 기준

- 외부 기술 예외를 그대로 Domain 바깥으로 퍼뜨리지 않습니다.
- Adapter에서 기술 예외를 application/domain 의미의 예외로 변환합니다.
- Controller Advice는 HTTP 상태와 응답 형식으로 변환합니다.
- 보안 예외는 인증 실패와 권한 부족을 구분합니다.

## 트랜잭션 경계

- 트랜잭션은 기본적으로 Application UseCase 단위로 둡니다.
- Controller에는 트랜잭션을 두지 않습니다.
- Domain에는 트랜잭션을 두지 않습니다.
- 조회 전용 작업은 read-only 트랜잭션을 검토합니다.
- 외부 API 호출과 DB 트랜잭션을 한 트랜잭션에 묶는 것을 피합니다.

## 성능 고려

- 매핑 비용은 일반적으로 DB I/O보다 작지만, 대량 조회에서는 중요할 수 있습니다.
- 목록 조회는 필요한 필드만 반환하는 Projection/Read Model을 검토합니다.
- N+1 위험이 있으면 fetch join, entity graph, batch size, 별도 query model을 비교합니다.
- API 응답용으로 연관관계를 열어두는 방식은 피합니다.

## 관련 참고

- [Spring Data JPA - Projections](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html)
- [Spring Data JPA - Query Methods](https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html)
- [Spring Framework - Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)

## 관련 문서

- **구조 원칙**: [헥사고날 아키텍처](200-hexagonal-architecture.md)
- **Domain Model 적용 기준**: [120 DDD 개념과 적용 기준](120-domain-driven-design.md#domain-model)
- **모듈 배치**: [Spring Boot 모듈 구조](210-spring-boot-module-structure.md)
- **영속성 구현 기준**: [Spring Data JPA](../tech-stack/230-spring-data-jpa.md)
