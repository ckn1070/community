# 아키텍처 개념 구분

이 문서는 프로젝트에서 사용하는 아키텍처와 설계 개념이 각각 어떤 문제를 해결하는지와
서로 어떤 관계인지 비교합니다. 특정 개념의 상세 적용 기준과 현재 프로젝트 결정은 해당
canonical owner로 연결합니다.

## 개념별로 해결하는 문제

| 개념 | 해결하는 문제 | 상세 기준 |
| --- | --- | --- |
| Domain-Driven Design | 업무 언어, 규칙과 소유권을 중심으로 경계를 찾고 모델을 설계합니다. | [120 DDD 개념과 적용 기준](120-domain-driven-design.md) |
| Bounded Context | 같은 용어와 모델이 일관된 의미를 갖는 범위를 정합니다. | [120 DDD 개념과 적용 기준](120-domain-driven-design.md#전략적-ddd) |
| Modular Monolith | 하나로 배포하면서 내부 코드를 독립적인 업무 모듈로 나눕니다. | [210 Spring Boot 모듈 구조](210-spring-boot-module-structure.md) |
| Hexagonal Architecture | 유스케이스와 외부 기술을 Port와 Adapter로 분리합니다. | [200 헥사고날 아키텍처](200-hexagonal-architecture.md) |
| Layered Architecture | Controller, Service, Repository 같은 기술 계층으로 책임을 나눕니다. | 단순 흐름에서 제한적으로 차용합니다. |
| Clean Architecture | 의존성이 바깥 기술에서 안쪽 정책을 향하도록 제한합니다. | 이 저장소에서는 Hexagonal 의존 방향의 배경 원칙으로 사용합니다. |
| CQRS | 변경 모델과 조회 모델을 분리합니다. | 규칙을 우회하지 않는 조회 최적화에서 부분적으로 차용합니다. |

Domain은 해결하려는 업무 문제 영역이고 Domain Model은 그 문제의 개념과 규칙을 코드로
표현한 모델입니다. 두 용어의 상세 구분과 Domain Model 적용 여부는
[120 DDD 개념과 적용 기준](120-domain-driven-design.md#domain-model)을 따릅니다.

## 관계

이 개념들은 서로 대체하지 않습니다.

```text
DDD                    업무 의미와 소유권을 찾음
  └─ Bounded Context   모델이 유효한 경계를 정함

Modular Monolith       하나의 배포 안에서 module을 격리함
Hexagonal Architecture module 내부의 의존 방향을 정함
Layered Architecture   단순한 기술 계층 분리 방식을 제공함
```

- Bounded Context와 Java package 또는 Spring Modulith module은 자동으로 1:1이 아닙니다.
- Modular Monolith의 module 경계는 DDD로 찾은 업무 경계를 코드에서 보호할 수 있지만,
  package를 나눴다는 사실만으로 Bounded Context가 확정되지는 않습니다.
- Hexagonal Architecture는 Domain Model을 외부 기술에서 분리할 수 있지만, Port와 Adapter가
  있다는 사실만으로 DDD가 적용되지는 않습니다.
- Layered Architecture는 단순 CRUD·조회에서 사용할 수 있지만 업무 소유권을 결정하지
  않습니다.
- 전술 패턴 수가 많다는 사실은 전략적 경계가 올바르다는 증거가 아닙니다.

## 피해야 할 혼동

- DDD와 Hexagonal Architecture를 같은 말로 쓰지 않습니다.
- Domain과 Domain Model을 같은 말로 쓰지 않습니다.
- Java module을 Bounded Context라고 자동 판정하지 않습니다.
- JPA Entity가 존재한다는 사실을 Domain Entity가 존재한다는 뜻으로 해석하지 않습니다.
- Modular Monolith를 Microservices의 미완성 단계로 보지 않습니다.
- 조회 모델과 변경 모델을 분리했다는 사실만으로 전체 CQRS를 도입했다고 쓰지 않습니다.

## 문서 경계

- **DDD 상세 기준**: [120 DDD 개념과 적용 기준](120-domain-driven-design.md)
- **현재 프로젝트 적용**: [200 현재 프로젝트 아키텍처](../project/200-current-architecture.md)
- **모듈 내부 구조**: [200 헥사고날 아키텍처](200-hexagonal-architecture.md)
- **현재 package 책임**: [210 모듈 지도](../project/210-module-map.md)
- **업무 의미 owner**: [000 도메인 문서 목차](../domain/000-index.md)
