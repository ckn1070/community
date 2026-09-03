# 현재 기술 스택

이 문서는 완료된 application 기준선에 실제 적용된 기술과 역할을 요약합니다.
정확한 working tree 선언은 `build.gradle`, Gradle Wrapper와 application 설정이 소유하고, 진행 중 work item의 변경은 완료 시 이 문서에 승격합니다.

## Runtime과 Build

| 기술 | 현재 역할 |
| --- | --- |
| Java 21 | application과 test toolchain |
| Gradle Wrapper | project가 소유하는 Gradle 실행 version |
| Spring Boot Gradle Plugin 4.0.6 | application build와 실행 |
| Dependency Management 1.1.7 | Spring 관리 dependency version |
| Spotless 8.6.0 | google-java-format 적용 |
| JaCoCo 0.8.13 | test coverage report |
| SonarScanner 7.2.3.7755 | SonarQube Cloud 분석 |

## Application

| 기술 | 현재 역할 |
| --- | --- |
| Spring Web MVC | REST API |
| Spring Security | stateless API 보안 |
| OAuth2 Resource Server | Bearer JWT 검증 |
| Spring Data JPA | persistence adapter |
| jOOQ 3.19.32 | catalog 복합 조회 실행, record mapping과 Flyway 기반 schema code generation |
| Flyway | PostgreSQL schema migration |
| Spring Modulith 2.1.0 | module boundary model과 test 검증 |
| springdoc-openapi 3.0.2 | 로컬 API 문서 |
| Resend Java v4.15.0 | 이메일 provider adapter |
| Bouncy Castle 1.81 | Argon2 password hashing provider |
| Lombok | 제한된 compile-time boilerplate 제거 |
| Spring Boot DevTools | local development only |

## Database

- PostgreSQL JDBC driver
- PostgreSQL용 Flyway database module
- Flyway V1~V23 account/auth·catalog·community·notification·moderation·media schema

## Test

- Spring Boot JPA, Flyway, Security, Web MVC test starter
- Spring Modulith test
- ArchUnit JUnit 5 1.4.2
- Testcontainers 2.0.5 BOM, JUnit Jupiter, PostgreSQL
- JUnit Platform Launcher

## 품질 기준

- 실제 Gradle version은 Wrapper가 소유합니다.
- Spring이 관리하는 dependency는 임의 override하지 않습니다.
- 직접 version을 지정한 dependency는 공식 release와 호환성을 확인합니다.
- module 경계는 Spring Modulith와 ArchUnit test로 검증합니다.
- formatter, coverage, test는 Gradle `check` 흐름에 연결합니다.
- SonarQube Cloud는 별도 원격 분석이며 local test 성공을 대신하지 않습니다.

## 관련 문서

- **application framework**: [Spring Boot](210-spring-boot.md)
- **인증·인가 구현**: [Spring Security](220-spring-security.md)
- **영속성 구현**: [Spring Data JPA](230-spring-data-jpa.md)
- **catalog 조회 구현**: [jOOQ](235-jooq.md)
- **test 기준**: [테스트 스택](300-testing-stack.md)
- **quality gate**: [코드 품질 도구](310-code-quality-tools.md)
