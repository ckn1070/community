# 아키텍처 목차

이 문서 그룹은 일반 구조 원칙과 설계 방법을 소유합니다.
현재 프로젝트에 실제 적용된 구조는 `../project`에서 확인합니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | 구조 원칙, 용어와 개념 |
| `200`~`290` | 구현 경계, module·mapping·API 설계 방법 |

## 상황별 진입점

| 상황 | 문서 |
| --- | --- |
| 구조 대안과 tradeoff 비교 | [100-architecture-principles.md](100-architecture-principles.md) |
| DDD와 다른 아키텍처 개념 비교 | [110 아키텍처 개념 구분](110-architecture-concepts.md) |
| DDD의 전략·전술 개념과 적용 판정 | [120 DDD 개념과 적용 기준](120-domain-driven-design.md) |
| Port와 Adapter 적용 | [200-hexagonal-architecture.md](200-hexagonal-architecture.md) |
| Spring Boot package와 module 구조 | [210-spring-boot-module-structure.md](210-spring-boot-module-structure.md) |
| DTO, Domain, Entity, Port 매핑과 이름 | [220-boundary-and-mapping-rules.md](220-boundary-and-mapping-rules.md) |
| REST resource, URI, method, status 설계 | [230-rest-api-design.md](230-rest-api-design.md) |

설계 확정 절차는 [설계 워크플로우](../workflow/110-design-workflow.md)가 소유합니다.
향후 검토할 아키텍처 후보는 [프로젝트 횡단 열린 결정](../work-items/backlog/2026/001-project-open-decisions.md)에 둡니다.
