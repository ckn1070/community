# 기술 스택 목차

이 문서 그룹은 현재 사용하는 기술과 기술별 사용 기준을 소유합니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | 전체 기술 stack과 역할 |
| `200`~`290` | runtime, framework, persistence, API와 library 사용 기준 |
| `300`~`390` | test와 application 품질 기준 |

| 상황 | 문서 |
| --- | --- |
| 전체 dependency와 역할 확인 | [100-current-stack.md](100-current-stack.md) |
| Java 21 | [200-java.md](200-java.md) |
| Spring Boot와 Web MVC | [210-spring-boot.md](210-spring-boot.md) |
| Spring Security와 JWT | [220-spring-security.md](220-spring-security.md) |
| JPA와 transaction | [230-spring-data-jpa.md](230-spring-data-jpa.md) |
| Flyway migration | [240-flyway.md](240-flyway.md) |
| PostgreSQL | [250-postgresql.md](250-postgresql.md) |
| springdoc-openapi | [260-openapi.md](260-openapi.md) |
| Lombok | [270-lombok.md](270-lombok.md) |
| structured logging과 민감정보 | [280-logging.md](280-logging.md) |
| test stack | [300-testing-stack.md](300-testing-stack.md) |
| Gradle, Spotless, JaCoCo, Sonar, Modulith, ArchUnit | [310-code-quality-tools.md](310-code-quality-tools.md) |

새 외부 도구나 설치 방식은 [도구 도입 워크플로우](../workflow/130-tool-adoption-workflow.md)를 먼저 적용합니다.
