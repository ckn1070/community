# Spring Boot 사용 가이드

이 문서는 Spring Boot와 Spring Web MVC를 사용할 때의 기본 원칙을 정의합니다.

## 목적

- Spring Boot의 자동 구성과 starter 생태계를 활용합니다.
- Spring을 핵심 도메인에 침투시키지 않고 바깥 계층의 조립 도구로 사용합니다.
- Web MVC 기반 API를 일관되게 설계하고 테스트합니다.

## 기본 원칙

- Spring Boot가 관리하는 의존성 버전을 우선 사용합니다.
- 직접 버전을 지정해야 하는 경우 이유를 문서화합니다.
- `@SpringBootApplication`은 루트 패키지에 유지합니다.
- 설정은 `@ConfigurationProperties`를 우선하고, 단발성 값만 `@Value`를 사용합니다.
- Profile은 환경 차이를 표현하고, 비즈니스 분기를 profile로 처리하지 않습니다.
- Controller는 얇게 유지하고 UseCase 호출에 집중합니다.

## 의존성 관리

Spring Boot 공식 문서는 각 릴리스가 지원하는 의존성 목록을 제공하고, Boot 업그레이드 시 의존성이 일관되게 업그레이드된다고 설명합니다.

프로젝트 기준:

- Spring Boot starter와 Spring 관련 라이브러리는 Boot dependency management를 따릅니다.
- Spring Framework 버전을 직접 지정하지 않습니다.
- springdoc처럼 Boot BOM 밖 라이브러리는 호환 버전을 명시하고 주기적으로 확인합니다.
- 취약점 대응이 필요한 경우에만 개별 dependency override를 검토합니다.

## Web MVC 기준

- REST API는 Controller, Request DTO, Response DTO를 사용합니다.
- Request validation은 Web Adapter에서 먼저 수행합니다.
- 유스케이스 수준 검증은 Application에서 수행합니다.
- HTTP status와 error body는 일관된 규칙으로 변환합니다.
- Controller에서 JPA Entity를 반환하지 않습니다.
- Controller에서 비즈니스 규칙을 직접 실행하지 않습니다.

## Validation 기준

Spring Framework는 MVC method validation과 Bean Validation을 지원합니다.

프로젝트 기준:

- Request DTO에는 `jakarta.validation` annotation을 사용합니다.
- 입력 형식 검증은 DTO에 둡니다.
- 도메인 규칙 검증은 Domain 또는 Application에 둡니다.
- Controller method parameter validation은 의도를 명확히 할 때 사용합니다.
- validation error 응답은 일관된 형식을 사용합니다.

## Configuration 기준

- `application.yaml`에는 로컬 기본값과 non-secret 설정만 둡니다.
- credential, token, password는 환경 변수나 안전한 secret store로 분리합니다.
- 새 환경 변수 key와 `.env.example` 반영은 [../development/100-local-development.md](../development/100-local-development.md)의 값 주입 프로세스를 따릅니다.
- 설정 묶음은 `@ConfigurationProperties` 클래스로 바인딩합니다.
- 현재 notification email 설정은 동일 namespace의 여러 값을 `@Value`로 수동 조립하는 예외입니다. 이 문서가 `@ConfigurationProperties` 기준과 현재 예외를 소유하고, 정리 범위와 완료 조건은 [006 Notification 설정 properties 경계 정리](../work-items/improvements/2026/006-notification-configuration-properties.md)에서 추적합니다.
- 외부 설정 우선순위를 고려해 profile별 값을 설계합니다.
- 테스트 설정은 운영 설정과 분리합니다.

## 아키텍처 경계 적용

- Spring Bean은 Adapter, Application 구현체, Config에 제한합니다.
- Domain은 Spring 없이 생성하고 테스트할 수 있어야 합니다.
- Application UseCase는 인터페이스와 구현을 분리할 수 있습니다.
- Adapter에서 Request DTO를 Command/Query로 변환합니다.
- Persistence Adapter에서 JPA Entity와 Domain을 변환합니다.

## 테스트 기준

- Controller 계약은 Web MVC slice 테스트를 우선합니다.
- 전체 wiring이 중요한 경우에만 `@SpringBootTest`를 사용합니다.
- Application/Domain 로직은 Spring Context 없이 테스트합니다.
- Security가 걸린 API는 인증 성공, 인증 실패, 권한 부족을 함께 테스트합니다.

## 피해야 할 패턴

- `@SpringBootTest`를 모든 테스트의 기본값으로 사용합니다.
- `@Value`를 여러 클래스에 흩뿌립니다.
- Controller가 EntityManager, Repository, Entity에 직접 의존합니다.
- Profile로 비즈니스 규칙을 바꿉니다.
- 설정 파일에 secret을 커밋합니다.

## 관련 참고

- [Spring Boot Gradle Plugin](https://docs.spring.io/spring-boot/gradle-plugin/)
- [Spring Boot - Packaging OCI Images](https://docs.spring.io/spring-boot/gradle-plugin/packaging-oci-image.html)
- [Spring Boot - Developer Tools](https://docs.spring.io/spring-boot/reference/using/devtools.html)
- [Spring Boot - Build Systems](https://docs.spring.io/spring-boot/reference/using/build-systems.html)
- [Spring Boot - Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring Framework - Spring Web MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html)
- [Spring Framework - Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)

## 관련 문서

- **현재 version owner**: [현재 기술 스택](100-current-stack.md)
- **test 기준**: [테스트 스택](300-testing-stack.md)
- **모듈 배치 기준**: [Spring Boot 모듈 구조](../architecture/210-spring-boot-module-structure.md)
