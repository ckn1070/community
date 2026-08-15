# 아키텍처 개념 구분

이 문서는 프로젝트에서 사용하는 아키텍처와 설계 개념을 정확히 구분합니다.
특정 기능, 모듈명, 도메인 정책 같은 프로젝트 결정은 이 문서가 아니라 `project`와 `domain` 문서에 둡니다.

## 목적

- DDD, Domain, Domain Model, Bounded Context, Modular Monolith, Hexagonal Architecture, Layered Architecture를 혼동하지 않게 합니다.
- 각 개념이 해결하는 문제가 무엇인지 분리합니다.
- 개념 문서와 프로젝트 결정 문서의 경계를 명확히 합니다.
- 이후 설계 논의에서 같은 용어를 같은 의미로 사용합니다.

## 적용 범위

이 문서는 다음 상황에서 먼저 확인합니다.

- 아키텍처 용어의 의미를 확인할 때
- 도메인 경계와 모듈 경계를 논의할 때
- 헥사고날과 레이어드 아키텍처의 적용 범위를 구분할 때
- Domain Model과 JPA Entity를 구분할 때
- 개념 문서에 실제 프로젝트 결정을 넣어도 되는지 판단할 때

## 문서 경계

이 문서는 방법과 개념을 설명합니다.
다음 내용은 이 문서에 넣지 않습니다.

- 실제 프로젝트 모듈 목록
- 실제 기능 범위
- 제품 로드맵
- 도메인별 상세 업무 규칙
- 특정 작업의 설계 결정 기록
- 버그 리포트와 수정 계획

위 내용은 다음 문서 그룹에 둡니다.

- 실제 프로젝트 구조와 현재 결정: `docs/project`
- 업무 지식과 도메인 규칙: `docs/domain`
- 제품 범위와 기획: `docs/product`
- 기능/버그/개선 작업 기록: `docs/work-items`

## 개념 요약

| 개념 | 의미 | 문서 체계에서의 위치 |
| --- | --- | --- |
| Domain | 해결하려는 업무 문제 영역입니다. | `domain` 문서에 구체적인 업무 지식을 기록합니다. |
| DDD | 도메인을 중심으로 소프트웨어를 설계하는 접근입니다. | 기본 설계 접근으로 사용합니다. |
| Bounded Context | 같은 용어와 모델이 일관된 의미를 갖는 경계입니다. | 모듈 경계를 찾는 기준으로 사용합니다. |
| Domain Model | 비즈니스 개념, 규칙, 상태 전이를 코드로 표현한 모델입니다. | 규칙이 있는 곳에 사용합니다. |
| Modular Monolith | 배포는 하나로 하되 내부 코드를 업무 모듈로 나누는 시스템 구조입니다. | 시스템 구조의 기본 선택지로 사용합니다. |
| Hexagonal Architecture | 내부 유스케이스와 외부 기술을 Port와 Adapter로 분리하는 구조입니다. | 모듈 내부 구조의 기본값으로 사용합니다. |
| Layered Architecture | Controller, Service, Repository 같은 계층으로 책임을 나누는 구조입니다. | 단순 흐름에서 제한적으로 차용할 수 있습니다. |
| Clean Architecture | 의존성이 안쪽 정책을 향해야 한다는 계층형 아키텍처 원칙입니다. | 배경 원칙으로 참고하되 헥사고날과 구분합니다. |
| CQRS | 변경 모델과 조회 모델을 분리하는 접근입니다. | 단순 조회 최적화에서 부분적으로 차용할 수 있습니다. |

## DDD

DDD는 도메인을 중심으로 소프트웨어를 설계하는 접근입니다.
DDD를 적용한다는 것은 모든 곳에 복잡한 패턴을 넣는다는 뜻이 아닙니다.
업무 언어, 경계, 규칙, 모델을 코드와 문서에서 일관되게 다룬다는 뜻입니다.

항상 적용하는 기준은 다음과 같습니다.

- 기능 이름과 패키지 이름은 업무 용어를 우선합니다.
- 같은 단어가 서로 다른 의미로 쓰이면 Bounded Context 분리를 검토합니다.
- 서로 다른 생명주기, 규칙, 소유권을 가진 모델은 분리합니다.
- 도메인 규칙은 구현 기술보다 업무 의미로 표현합니다.
- 도메인 정책이 불명확하면 추측하지 않고 사용자 확인을 받습니다.

전술 패턴은 필요할 때만 적용합니다.

- Aggregate는 일관성 경계와 트랜잭션 경계가 필요할 때 둡니다.
- Value Object는 값 자체의 의미, 검증, 불변성이 중요할 때 둡니다.
- Domain Event는 상태 변화가 다른 흐름의 후속 작업을 유발할 때 검토합니다.
- Repository 추상화는 Application이 저장 기술에서 분리될 필요가 있을 때 둡니다.

## Domain과 Domain Model

Domain은 문제 영역입니다.
Domain Model은 그 문제 영역의 개념과 규칙을 코드로 표현한 모델입니다.
두 개념은 같지 않습니다.

Domain Model을 우선하는 경우는 다음과 같습니다.

- 상태 전이가 있습니다.
- 생성, 수정, 삭제에 업무 규칙이 있습니다.
- 값 검증과 불변조건이 중요합니다.
- 여러 필드가 함께 의미를 이룹니다.
- 테스트로 고정해야 할 정책이 있습니다.

Domain Model을 생략하거나 가볍게 둘 수 있는 경우는 다음과 같습니다.

- 조회 전용 데이터입니다.
- 단순 코드성 데이터입니다.
- 업무 규칙 없이 저장과 표시만 합니다.
- 외부 응답을 임시로 저장하거나 전달합니다.

## Bounded Context

Bounded Context는 같은 용어와 모델이 일관된 의미를 갖는 경계입니다.
패키지 구조를 먼저 나눈 뒤 이름을 붙이는 것이 아니라, 업무 의미와 생명주기를 먼저 보고 경계를 찾습니다.

분리 신호는 다음과 같습니다.

- 같은 단어가 영역마다 다른 의미로 쓰입니다.
- 데이터의 생성, 변경, 삭제 생명주기가 다릅니다.
- 권한과 운영 정책이 다릅니다.
- 변경 이유가 다릅니다.
- 한쪽 모델의 상세 구조를 다른 쪽이 알기 시작합니다.
- 나중에 독립적으로 분리하거나 교체할 가능성이 있습니다.

## Modular Monolith

Modular Monolith는 하나의 애플리케이션으로 배포하면서 내부 코드를 업무 모듈로 나누는 시스템 구조입니다.
마이크로서비스와 달리 배포, 운영, 트랜잭션, 로컬 개발은 단순하게 유지합니다.
대신 코드 내부의 모듈 경계를 엄격하게 관리합니다.

좋은 신호는 다음과 같습니다.

- 모듈 하나를 이해할 때 관련 코드가 한곳에 모여 있습니다.
- 다른 모듈의 내부 구현을 직접 참조하지 않습니다.
- 공개 계약을 통해 모듈 간 협력이 이루어집니다.
- 순환 의존성이 없습니다.
- 나중에 일부 모듈을 물리적으로 분리할 가능성이 열려 있습니다.

## Hexagonal Architecture

Hexagonal Architecture는 내부 유스케이스와 외부 기술을 Port와 Adapter로 분리합니다.
목표는 핵심 정책이 Web, DB, Security, 외부 API 같은 기술에 직접 묶이지 않게 하는 것입니다.

이 개념은 Domain Model과 같지 않습니다.
헥사고날 구조 안에 풍부한 Domain Model이 있을 수도 있고, 단순 유스케이스 중심 코드만 있을 수도 있습니다.

## Layered Architecture

Layered Architecture는 Controller, Service, Repository처럼 기술적 계층으로 책임을 나누는 구조입니다.
단순한 CRUD나 조회 흐름에서는 이해하기 쉽고 빠릅니다.
하지만 규칙, 상태 전이, 외부 의존성이 늘어나면 Service가 너무 많은 책임을 갖기 쉽습니다.

따라서 이 프로젝트에서는 기본 철학이 아니라 제한적으로 차용할 수 있는 단순화 방식으로 봅니다.

## 피해야 할 혼동

- DDD와 헥사고날 아키텍처를 같은 말로 쓰지 않습니다.
- Domain과 Domain Model을 같은 말로 쓰지 않습니다.
- Domain Model이 있다고 해서 자동으로 헥사고날 구조가 되는 것은 아닙니다.
- 헥사고날 구조를 쓴다고 해서 반드시 풍부한 Domain Model이 필요한 것은 아닙니다.
- Modular Monolith는 배포와 모듈 구조에 대한 선택이고, Bounded Context는 도메인 경계 개념입니다.
- Layered Architecture는 단순 구현 방식으로 차용할 수 있지만 기본 철학은 아닙니다.
- Clean Architecture는 의존성 방향에 대한 배경 원칙으로 참고하되, 프로젝트 문서에서는 헥사고날과 구분해서 씁니다.

## 관련 참고

- [Domain-Driven Design Reference](https://www.domainlanguage.com/ddd/reference/)
- [Martin Fowler - Bounded Context](https://martinfowler.com/bliki/BoundedContext.html)
- [Alistair Cockburn - Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)
- [Martin Fowler - Service Layer](https://martinfowler.com/eaaCatalog/serviceLayer.html)
- [Spring Modulith - Fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html)

## 관련 문서

- **판단 원칙**: [아키텍처 판단 원칙](100-architecture-principles.md)
- **구조 적용**: [헥사고날 아키텍처](200-hexagonal-architecture.md), [Spring Boot 모듈 구조](210-spring-boot-module-structure.md), [경계와 매핑 규칙](220-boundary-and-mapping-rules.md)
- **현재 프로젝트 적용**: [현재 프로젝트 아키텍처](../project/200-current-architecture.md)
- **업무 의미 owner**: [도메인 문서 목차](../domain/000-index.md)
