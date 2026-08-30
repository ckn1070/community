# 아키텍처 판단 원칙

이 문서는 프로젝트에서 아키텍처를 선택하거나 변경할 때 사용할 공통 판단 기준을 정의합니다.

## 목적

- 기술적 완결성, 유지보수성, 확장성, 성능을 함께 고려합니다.
- Best Practice를 기본값으로 삼되, 프로젝트 상황과 비용을 함께 평가합니다.
- 명확한 선택지가 있으면 과도한 논의를 줄이고, 명확하지 않으면 대안을 비교합니다.

## 기본 원칙

- 기본 설계 접근은 DDD, 모듈러 모놀리스, 헥사고날 아키텍처의 실용적 조합입니다.
- DDD는 도메인 이해와 경계 설정의 기본 방식으로 적용합니다.
- 업무 모듈 경계를 계층 경계보다 우선합니다.
- 헥사고날 아키텍처는 모듈 내부 기본 구조로 사용하되, 단순 조회와 규칙 없는 CRUD에는 과한 추상화를 피합니다.
- 아키텍처 선택은 목표, 변경 가능성, 테스트 가능성, 운영 리스크를 기준으로 판단합니다.
- 프레임워크와 데이터베이스는 핵심 정책을 실행하기 위한 도구로 봅니다.
- 도메인 규칙과 유스케이스는 외부 기술 세부사항에서 최대한 분리합니다.
- 단순한 기능에 과도한 계층을 만들지 않습니다.
- 선택의 이득이 비용보다 명확하지 않으면 더 단순한 구조를 우선합니다.

## 평가 축

아키텍처 선택은 다음 축을 기준으로 비교합니다.

- 기술적 완결성: 의존성 방향, 책임 분리, 일관성, 오류 처리 방식이 명확한가.
- 유지보수성: 변경 영향 범위가 작고, 새 작업자가 구조를 추론하기 쉬운가.
- 확장성: 기능, 저장소, 외부 API, 인증 정책이 늘어날 때 구조가 버티는가.
- 테스트 가능성: 핵심 로직을 프레임워크 없이 테스트할 수 있는가.
- 성능: 계층 분리와 매핑 비용이 실제 병목이 될 가능성이 있는가.
- 개발 속도: 현재 단계에서 구조화 비용이 목표 달성을 방해하지 않는가.
- 운영 안정성: 장애 분석, 로그, 트랜잭션, 데이터 무결성 기준이 명확한가.

## 우선순위

기본 우선순위는 다음과 같습니다.

1. 데이터 무결성과 보안
2. 도메인 규칙의 명확성
3. 테스트 가능성과 회귀 방지
4. 유지보수성과 변경 용이성
5. 성능과 운영 비용
6. 구현 속도

단, 실제 장애 가능성이 높거나 성능 요구가 명확한 작업은 성능과 운영 안정성의 우선순위를 올립니다.

## 명확한 선택지

다음 경우에는 비교표 없이 바로 권장안을 제시하고 진행할 수 있습니다.

- 도메인 규칙이 프레임워크 코드에 섞이려는 경우에는 분리합니다.
- Controller에서 비즈니스 규칙이 커지면 Application UseCase로 이동합니다.
- JPA Entity를 API 응답으로 직접 노출하려는 경우에는 DTO를 둡니다.
- 인증/인가 정책이 여러 Controller에 흩어지면 정책 위치를 정리합니다.
- 테스트하기 어려운 외부 의존성이 핵심 로직 안으로 들어오면 Port로 분리합니다.

## 비교가 필요한 선택지

다음 경우에는 대안을 비교하고 우위를 설명합니다.

- 단순 CRUD에 UseCase, Port, Mapper를 모두 둘지 여부입니다.
- JPA Entity와 Domain Model을 분리할지 여부입니다.
- 조회 모델을 도메인 모델로 처리할지, 별도 Read Model/Projection으로 처리할지 여부입니다.
- SQL을 실행·매핑하는 기술을 ORM, Spring JDBC, SQL mapper, type-safe SQL DSL, reactive client,
  DB view 중 무엇으로 둘지 여부입니다.
- Repository 추상화를 application/domain에 둘지, Spring Data Repository를 직접 사용할지 여부입니다.
- 트랜잭션 경계를 UseCase에 둘지, 더 바깥 서비스 계층에 둘지 여부입니다.
- 캐시, 비동기, 이벤트를 도입할지 여부입니다.

## 조회 구현 방식 선택

Raw SQL이 필요하다는 사실만으로 실행 기술을 결정하지 않습니다. 후보를 비교할 때에는 제품명부터
고르지 않고 다음 계열을 현재 stack에서 실제 사용할 수 있는 구현과 연결합니다.

| 후보 계열 | 대표 구현 | 먼저 확인할 적합성 |
| --- | --- | --- |
| ORM query·projection | Spring Data JPA derived query·JPQL·native projection, `EntityManager` | aggregate 탐색과 평면 projection, 기존 transaction 관례 |
| Spring JDBC | `JdbcTemplate`, `NamedParameterJdbcTemplate`, `JdbcClient` | 직접 SQL과 낮은 도입 비용, 수동 mapping 허용 범위 |
| SQL mapper | MyBatis | SQL 소유와 선언적 결과 mapping, XML·mapper 이중 source 비용 |
| Type-safe SQL DSL·code generation | jOOQ, QueryDSL SQL | DB 전용 SQL 표현력, schema drift 감지, build 생성 비용 |
| 경량 SQL object | Jdbi | SQL object·row mapper 단순성, 별도 persistence convention 비용 |
| Aggregate 중심 JDBC | Spring Data JDBC | 단순 aggregate 저장·조회이며 복합 read projection이 아닌지 |
| Reactive SQL | R2DBC | 전체 호출 stack과 transaction이 실제로 non-blocking인지 |
| DB 소유 read model | view, materialized view, function | 여러 consumer의 공통 계산, 배포·권한·refresh lifecycle |
| 별도 조회 저장소 | 검색엔진, cache, denormalized read model | 측정된 병목·검색 요구와 동기화·복구 비용 |

후보 이름이 목록에 있다는 이유로 모두 같은 깊이로 검증하지 않습니다. 먼저 현재 요구와 충돌하는
계열을 목적 불일치로 제외하되, 제외 근거를 남긴 뒤 남은 후보를 같은 평가 축으로 비교합니다.
평가 축은 최소한 다음을 포함합니다.

- Query 표현력: 필요한 join·CTE·window·집계·DB 전용 type을 손실 없이 표현하는가
- 결과 조립: 평면 projection과 중첩 graph를 어디서 어떤 타입으로 조립하는가
- Schema 안전성: table·column 변경을 compile·test·runtime 중 어느 시점에 탐지하는가
- 실행 통제: query 수, binding, transaction, timeout, 실행 계획과 관측을 통제할 수 있는가
- 기존 stack 정합성: 현재 MVC/reactive model, transaction과 build에 자연스럽게 참여하는가
- 도입 비용: dependency, code generation, 설정, 두 번째 source와 학습·upgrade 비용은 얼마인가
- 변경 축: 조회 복잡도·DB 종속성·consumer 증가 때 어느 코드와 문서가 함께 바뀌는가

단순 CRUD의 기본값과 복합 조회의 기본값을 하나로 통일하지 않습니다. 같은 기능 안에서도 단순
aggregate 변경은 JPA, 복합 read projection은 jOOQ처럼 다른 adapter 기술을 사용할 수 있지만,
application Port 밖으로 persistence type을 노출하거나 하나의 transaction 의미를 기술별로
분열시키면 안 됩니다. 새 기술을 선택하면 실제로 쓰는 장점, 감수하는 비용, 보호되지 않는 범위와
재검토 조건을 해당 `tech-stack` SOT와 작업 design에 함께 남깁니다.

## 과설계 방지 기준

다음 신호가 있으면 구조를 줄이거나 단계적으로 적용합니다.

- 계층을 추가해도 테스트나 변경 용이성이 개선되지 않습니다.
- 매핑 코드가 규칙보다 많아집니다.
- 아직 도메인 규칙이 없고 단순 저장/조회만 있습니다.
- 한 번 쓰고 버릴 실험성 기능입니다.
- 팀이 구조를 유지할 수 있는 문서와 테스트를 아직 갖추지 못했습니다.

## 결정 기록 기준

다음 변경은 관련 project 문서나 활성 work item의 결정 기록에 남깁니다.

- 패키지 구조 변경
- 계층 책임 변경
- Domain과 Entity 분리 여부 변경
- Repository/Port 경계 변경
- 트랜잭션 경계 변경
- 보안 정책 위치 변경
- 성능을 이유로 구조 원칙을 예외 처리한 경우

결정 기록은 다음 내용을 포함합니다.

1. 해결할 목표와 배경
2. 책임 경계
3. 검토한 선택지
4. 선택한 구조
5. 선택 근거와 감수할 비용
6. 배제한 대안
7. 구현과 운영에 미치는 영향
8. 재검토 조건

선택지가 명확하지 않으면 다음 순서로 판단합니다.

1. 목표와 변경 대상의 책임을 한 문장으로 정리합니다.
2. 기존 코드, project 결정, domain 정책을 확인합니다.
3. 해당 문제 계열의 후보군을 실제 사용할 수 있는 구현까지 빠짐없이 복원합니다.
4. 요구·현재 stack과 충돌하는 후보를 근거와 함께 제외한 뒤, 남은 shortlist를 만듭니다.
5. 이 문서의 평가 축으로 shortlist를 같은 깊이에서 비교합니다.
6. 추천안과 제외·배제한 대안의 이유를 설명합니다.
7. 선택에서 실제로 사용하는 장점, 보호하지 못하는 범위, 검증 방법과 재검토 조건을 남깁니다.

설계 확정과 문서화 절차는 [설계 워크플로우](../workflow/110-design-workflow.md)가 소유합니다.

## 관련 참고

- [Robert C. Martin - The Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Alistair Cockburn - Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)

## 관련 문서

- **용어 기준**: [아키텍처 개념 구분](110-architecture-concepts.md)
- **구조 적용 기준**: [헥사고날 아키텍처](200-hexagonal-architecture.md)
- **설계 실행 절차**: [설계 워크플로우](../workflow/110-design-workflow.md)
