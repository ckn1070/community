# DDD 개념과 적용 기준

이 문서는 특정 프로젝트와 무관하게 Domain-Driven Design의 전략적·전술적 개념과 적용
판정 기준을 소유합니다. DDD는 정해진 package 구조나 라이브러리가 아니라, 업무 문제와
업무 언어를 중심으로 소프트웨어의 경계·모델·소유권을 설계하는 접근입니다. 핵심 업무에
집중하고, 업무 전문가와 개발자가 함께 모델을 탐구하며, 명시적 Bounded Context 안에서
Ubiquitous Language와 모델을 지속적으로 정제하는 활동까지 포함합니다.

## DDD가 해결하는 문제

기능이 늘어날수록 같은 단어가 영역마다 다른 의미로 사용되고, 한 상태를 여러 코드와
팀이 함께 바꾸며, 업무 규칙이 Controller·Service·DB mapping에 흩어질 수 있습니다.
DDD는 다음 질문을 먼저 닫습니다.

- 어떤 업무 문제를 해결하는가
- 그 업무에서 사용하는 말은 무엇을 뜻하는가
- 어떤 모델이 어느 범위에서만 유효한가
- 규칙, 상태와 생명주기를 누가 소유하는가
- 일관성을 함께 지켜야 하는 변경 단위는 무엇인가
- 서로 다른 경계는 어떤 공개 계약으로 협력하는가
- 업무 전문가와 개발자가 어떤 근거로 모델을 함께 탐구·정제하는가

## 전략적 DDD

전략적 DDD는 코드 패턴보다 먼저 업무 영역, 언어와 소유권 경계를 결정합니다.

### Domain과 Subdomain

Domain은 해결하려는 업무 문제 전체입니다. Subdomain은 그 문제를 의미와 변경 이유가
응집된 더 작은 업무 영역으로 나눈 것입니다.

| 유형 | 의미 | 투자 기준 |
| --- | --- | --- |
| Core Domain | 제품의 차별성과 핵심 경쟁력을 만드는 영역 | 가장 정확한 모델과 지속적인 지식 정제에 집중합니다. |
| Supporting Subdomain | Core Domain을 지원하지만 일반 제품으로 대체하기 어려운 영역 | 필요한 업무 모델을 두되 Core보다 단순하게 유지할 수 있습니다. |
| Generic Subdomain | 여러 제품에서 공통으로 해결되는 영역 | 표준 제품·라이브러리·단순 구현을 우선 검토합니다. |

분류는 package 이름이 아니라 제품 가치와 업무 지식으로 결정합니다. 근거가 없으면 유형을
추측하지 않고 `미확정`으로 남깁니다.

### Ubiquitous Language

Ubiquitous Language는 업무 전문가와 개발자가 같은 경계 안에서 같은 의미로 사용하는
언어입니다. 문서, API, 코드와 테스트의 핵심 이름이 이 언어를 일관되게 반영해야 합니다.

- 같은 단어가 영역마다 다른 뜻이면 하나로 통일하기보다 경계 분리를 검토합니다.
- 코드 이름이 기술 용어만 드러내고 업무 의미를 숨기면 모델을 다시 봅니다.
- 확정되지 않은 업무 정책은 코드 구조로 먼저 고정하지 않습니다.

### Bounded Context

Bounded Context는 특정 모델과 언어가 일관된 의미를 갖는 명시적 경계입니다. 다음 신호를
함께 보고 식별합니다.

- 용어와 규칙이 내부에서 일관되는가
- 상태 생성·변경·삭제의 생명주기와 소유자가 하나인가
- 독립적으로 바뀌는 이유와 책임이 있는가
- 다른 경계가 내부 모델이나 table을 직접 알아야 하지 않는가
- 공개 계약으로 협력할 수 있는가

Java package, Gradle module, Spring Modulith module이나 database schema는 경계를 구현하는
수단입니다. 이 구조가 있다는 사실만으로 Bounded Context가 확정되지는 않습니다.

### Context Map

Context Map은 Bounded Context 후보 사이의 관계 유형, 필요한 경우의 의존 방향과 번역
경계를 나타냅니다.
최소한 다음을 복원할 수 있어야 합니다.

```text
Context A·B → 관계 유형 → 방향(관계가 방향성을 가질 때)
            → 공개 계약·번역 → 내부 모델·공유 저장소 우회 여부
```

Customer/Supplier처럼 방향이 있는 관계는 upstream과 downstream을 표시합니다.
Partnership과 Shared Kernel처럼 대칭적인 관계, Separate Ways처럼 의존하지 않는 관계에는
방향을 억지로 부여하지 않습니다. 관계를 판정할 때 공개 API, 이벤트, 번역 계층, 직접 table
접근과 공동 writer를 함께 확인합니다. Context 자체나 관계 유형이 합의되지 않았으면 확정된
이름을 붙이지 않고 `후보` 또는 `현재 미확정`으로 기록합니다.

| 관계 | 의미와 방향 | 통합·번역 방식 | 적용 신호 |
| --- | --- | --- | --- |
| Partnership | 두 Context가 서로의 성공에 의존하고 함께 계획하는 대칭 관계입니다. | 변경 일정과 통합 test를 양쪽이 함께 조정합니다. | 두 팀이 상호 의존을 감수하고 긴밀하게 협력할 수 있습니다. |
| Shared Kernel | 두 Context가 모델·코드의 작은 일부를 공동 소유하는 대칭 관계입니다. | 공유 범위를 명시하고 변경을 양쪽 합의와 test로 보호합니다. | 중복보다 공동 소유 이득이 크고 공유 범위를 작게 유지할 수 있습니다. |
| Customer/Supplier Development | upstream이 제공하고 downstream이 소비하되 downstream 요구를 계획에 반영하는 방향 관계입니다. | downstream 수용 test와 협의로 upstream 계약을 발전시킵니다. | upstream이 downstream의 우선순위를 실제로 협상할 수 있습니다. |
| Conformist | 영향력이 없는 downstream이 upstream 모델을 그대로 따르는 방향 관계입니다. | 별도 번역 모델을 만들지 않고 upstream 언어를 수용합니다. | 번역 비용이 이득보다 크고 upstream을 바꿀 수 없습니다. |
| Anticorruption Layer | downstream이 자기 모델을 보호하는 방향 관계입니다. | adapter·translator가 upstream 모델을 downstream 언어로 변환합니다. | 외부·legacy 모델이 내부 업무 언어를 오염시킬 위험이 큽니다. |
| Open Host Service | 한 Context가 여러 소비자에게 안정된 통합 protocol을 공개합니다. | 공개 service·protocol을 기본 경로로 제공하고 특수 요구만 별도 처리합니다. | 유사한 consumer integration이 반복됩니다. |
| Published Language | 여러 Context가 교환 의미를 공유할 수 있도록 문서화된 언어를 사용합니다. | schema·표준 문서·공개 message 의미를 안정된 교환 계약으로 사용합니다. | producer와 consumer가 내부 모델 대신 공통 교환 언어가 필요합니다. |
| Separate Ways | 두 Context가 통합하지 않고 독립적으로 문제를 해결합니다. | 공유 계약 없이 필요한 기능을 각자 유지합니다. | 통합 이득보다 결합·조정 비용이 큽니다. |
| Big Ball of Mud | 경계와 모델이 일관되지 않은 기존 영역을 사실대로 표시합니다. | 새 모델로 가장하지 않고 명확한 Context 바깥에 격리합니다. | 즉시 정리할 수 없는 legacy 혼합 영역이 존재합니다. |

관계 이름은 코드 의존 방향만 보고 붙이지 않습니다. 팀 협력 방식, 모델 번역, 공개 계약과
실제 데이터 소유권이 해당 패턴의 의미를 함께 충족할 때만 사용합니다.

### 모델을 유지하는 실천

- Model-Driven Design은 분석 모델과 구현 모델을 분리하지 않고 코드가 업무 모델을 직접
  표현하게 합니다.
- Hands-on Modelers는 모델을 정하는 사람이 구현과 단절되지 않게 해 설계 피드백을 모델에
  반영합니다.
- Continuous Integration은 같은 Bounded Context 안의 모델·언어 분열을 빠르게 발견하고
  통합합니다.
- Refactoring Toward Deeper Insight는 새 업무 지식이 생길 때 언어, 경계와 모델을 함께
  다시 다듬습니다.

## 전술적 DDD

전술적 DDD는 Bounded Context 내부의 복잡한 업무 규칙과 일관성 경계를 코드로 표현하는
패턴입니다. 모든 패턴을 모든 module에 넣는 것이 목적은 아닙니다.

### Domain Model

Domain은 문제 영역이고 Domain Model은 그 영역의 개념, 상태, 규칙과 행위를 코드로 표현한
모델입니다. Domain Model은 다음 신호가 있을 때 필요합니다.

- 의미 있는 상태 전이와 금지 전이가 있습니다.
- 여러 값이 함께 만족해야 하는 불변조건이 있습니다.
- 생성·변경·삭제에 업무 규칙이 있습니다.
- 정책을 framework나 persistence 없이 테스트해야 합니다.
- 여러 유스케이스가 같은 업무 언어와 규칙을 공유합니다.

조회 전용 데이터, 단순 코드성 데이터, 업무 규칙 없는 저장·전달, 외부 응답의 임시 표현은
Domain Model을 생략하거나 가볍게 둘 수 있습니다. 생략은 DDD 위반이 아니며 규칙이 생기면
다시 모델링합니다.

JPA Entity는 table mapping과 persistence 생명주기를 표현하고 Domain Model은 업무 의미와
규칙을 표현합니다. 두 역할을 분리할지와 경계에서 어떻게 매핑할지는
[220 경계와 매핑 규칙](220-boundary-and-mapping-rules.md#entity와-domain-분리-기준)이
소유합니다.

### 전술 패턴

| 패턴 | 책임 | 적용 신호 |
| --- | --- | --- |
| Entity | 식별자가 유지되는 업무 객체와 그 생명주기 | 속성이 바뀌어도 같은 대상으로 추적해야 합니다. |
| Value Object | 값의 의미, 검증과 불변성 | 식별자보다 값 전체가 의미를 가지며 교체가 자연스럽습니다. |
| Aggregate | 함께 일관성을 지켜야 하는 객체 집합 | 한 transaction에서 반드시 보호할 불변조건이 있습니다. |
| Aggregate Root | Aggregate 외부가 접근하는 유일한 진입점 | 내부 객체 변경을 Root가 통제해야 합니다. |
| Domain Service | 한 Entity나 Value Object에 자연스럽게 속하지 않는 업무 규칙 | 여러 모델이 참여하는 순수한 업무 연산이 있습니다. |
| Domain Event | 이미 발생한 의미 있는 업무 사실 | 상태 변화가 후속 정책이나 다른 경계의 반응을 유발합니다. |
| Module | 모델의 개념적 묶음과 내부 응집도를 드러내는 경계 | 함께 이해·변경되는 모델을 업무 언어로 묶어야 합니다. |
| Repository | Aggregate를 저장·복원하는 도메인 관점의 collection 계약 | 저장 기술과 무관하게 Aggregate 생명주기를 다뤄야 합니다. |
| Factory | 복잡한 객체·Aggregate 생성 규칙과 유효한 초기 상태를 캡슐화합니다. | 호출자가 내부 조립과 불변조건을 알아서는 안 됩니다. |

Application Service는 유스케이스 순서, transaction과 외부 협력을 조율합니다. 업무 규칙을
Application Service에 둘 수밖에 없는지 먼저 확인하고, 모델 자체가 지켜야 하는 불변조건은
Domain Model로 이동합니다.

## 전략과 전술의 관계

```text
전략적 DDD: 업무 언어·소유권·Bounded Context·Context Map 결정
    ↓ 각 경계 내부에서 복잡한 규칙이 확인됨
전술적 DDD: Domain Model·Aggregate·Value Object 등으로 규칙 표현
```

전략적 경계 없이 전술 패턴만 추가하면 서로 다른 업무 의미가 한 모델에 섞일 수 있습니다.
반대로 전략적 경계가 명확해도 규칙이 단순한 영역에는 풍부한 전술 모델이 필요하지 않을 수
있습니다.

## 적용 판정 기준

DDD 적용 여부는 전략과 전술을 나누어 판정합니다.

### 전략적 DDD 판정

- Core Domain에 집중할 근거와 다른 Subdomain을 구분한 근거가 있습니다.
- 업무 전문가와 개발자가 모델을 함께 탐구하고 언어·모델을 정제한 근거가 있습니다.
- 업무 용어와 책임이 문서·코드에서 일치합니다.
- Subdomain과 Bounded Context 후보의 근거가 있습니다.
- 상태·생명주기·업무 규칙의 owner가 하나입니다.
- Context 사이의 관계 유형, 해당할 때의 의존 방향과 공개 계약을 Context Map으로 설명할
  수 있습니다.
- 다른 Context의 내부 모델·table을 직접 조작하지 않습니다.
- 확정되지 않은 경계와 관계를 현재 사실처럼 쓰지 않습니다.

경계와 공개 계약이 일부 존재하지만 공동 writer, 직접 table read, 소유권 분할이나 미확정
Context가 남아 있으면 `부분 적용`입니다.

### 전술적 DDD 판정

- 복잡한 업무 규칙이 있는 곳에서 Domain Model이 규칙을 소유합니다.
- Aggregate의 일관성 경계와 transaction 경계가 충돌하지 않습니다.
- Value Object와 Entity가 식별자·값 의미에 맞게 구분됩니다.
- 업무 규칙이 Controller, JPA Entity, 설정 코드나 Mapper에 흩어지지 않습니다.
- 단순 조회·전달 영역에 불필요한 전술 패턴을 강제하지 않습니다.

필요한 영역에만 전술 패턴을 사용하면 `선택 적용`으로 판정할 수 있습니다. 패턴 수가 적다는
이유만으로 미적용이라고 판정하지 않고, 복잡한 규칙이 있는데도 소유 모델 없이 Application
Service와 persistence에 흩어졌는지를 확인합니다.

## 관련 문서

- **다른 아키텍처와의 비교**: [110 아키텍처 개념 구분](110-architecture-concepts.md)
- **모듈 구현 기준**: [210 Spring Boot 모듈 구조](210-spring-boot-module-structure.md)
- **Domain/JPA 매핑 방법**: [220 경계와 매핑 규칙](220-boundary-and-mapping-rules.md)
- **현재 프로젝트 판정**: [200 현재 프로젝트 아키텍처](../project/200-current-architecture.md)
- **업무 의미 owner**: [000 도메인 문서 목차](../domain/000-index.md)

## 관련 참고

- [Domain-Driven Design Reference](https://www.domainlanguage.com/wp-content/uploads/2016/05/DDD_Reference_2015-03.pdf)
- [Martin Fowler - Bounded Context](https://martinfowler.com/bliki/BoundedContext.html)
