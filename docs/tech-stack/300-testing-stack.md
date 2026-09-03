# 테스트 스택 가이드

이 문서는 JUnit, AssertJ, Spring Boot Test, Spring Security Test를 사용하는 기준을 정의합니다.

## 목적

- TDD 흐름을 지원하는 빠르고 신뢰할 수 있는 테스트를 작성합니다.
- 테스트 범위를 변경 위험에 맞게 선택합니다.
- Spring Context 비용을 필요한 곳에만 사용합니다.

## 기본 원칙

- 기능 추가, 버그 수정, 도메인 로직 변경은 TDD를 기본 방식으로 진행합니다.
- 테스트는 관찰 가능한 동작을 검증합니다.
- 구현 세부사항에 과하게 결합된 테스트를 피합니다.
- 빠른 단위 테스트를 우선하고, 필요한 곳에 slice/integration 테스트를 추가합니다.
- 테스트가 실패하면 실패 이유가 명확해야 합니다.
- PostgreSQL·Kafka Testcontainers 테스트는 Docker 부재를 skip으로 처리하지 않고 전체 검증을 실패시킵니다.
- Kafka 전달 테스트는 실제 broker protocol, DLT, offset, broker 중단과 topic·group data loss를 receipt·inbox 결과와 함께 검증합니다.
- 공통 지원 클래스의 계약 테스트는 `disabledWithoutDocker = false`와 고정 PostgreSQL image를 검사하고, ArchUnit으로 지원 클래스 밖의 직접 Testcontainers 사용을 금지합니다.
- 최종 실행과 성공·실패 판정은 Docker daemon preflight 뒤 재사용 없이 실행하는 Gradle `check`와 Testcontainers가 소유합니다.

## 테스트 종류

단위 테스트:

- Spring Context 없이 실행합니다.
- Domain, value object, policy, usecase decision logic을 검증합니다.
- TDD의 기본 사이클에서 가장 먼저 고려합니다.

Slice 테스트:

- Web MVC, Data JPA, Security 등 특정 Spring slice만 로딩합니다.
- Controller contract, Repository query, Security rule을 검증합니다.
- 전체 애플리케이션 context보다 빠르게 실행하는 것을 목표로 합니다.

통합 테스트:

- 여러 계층 wiring, transaction, DB, migration, security 조합을 검증합니다.
- 실제 PostgreSQL에 가까운 환경이 필요한 경우 검토합니다.
- 비용이 높으므로 핵심 흐름 위주로 둡니다.

Smoke 테스트:

- 애플리케이션 context가 뜨는지 확인합니다.
- 설정, Bean wiring, profile 충돌을 빠르게 감지합니다.

## JUnit 기준

JUnit 공식 문서는 JUnit Platform, JUnit Jupiter, JUnit Vintage를 구분합니다.
프로젝트는 JUnit Platform과 Jupiter 기반 테스트를 기본값으로 둡니다.

기준:

- 테스트 이름은 동작을 설명합니다.
- `@Nested`는 시나리오 구조를 드러낼 때 사용합니다.
- parameterized test는 입력 조합이 많고 규칙이 같은 경우 사용합니다.
- 테스트 순서에 의존하지 않습니다.
- disabled test는 이유와 재활성화 조건을 남깁니다.

## AssertJ 기준

AssertJ는 fluent assertion과 읽기 쉬운 실패 메시지를 제공합니다.

기준:

- assertion은 AssertJ를 우선 사용합니다.
- collection, exception, extracting 검증은 AssertJ의 표현력을 활용합니다.
- assertion message는 실패 원인을 더 명확히 할 때만 추가합니다.
- 여러 assertion이 하나의 동작을 설명하면 한 테스트 안에 둘 수 있습니다.

## Spring Boot Test 기준

Spring Boot 공식 문서는 테스트 유틸리티와 여러 test slice를 제공합니다.

기준:

- `@SpringBootTest`는 전체 wiring이 필요한 경우에만 사용합니다.
- Controller는 Web MVC slice 테스트를 우선합니다.
- Repository는 Data JPA slice 또는 실제 DB 통합 테스트를 검토합니다.
- Flyway와 JPA mapping이 함께 중요하면 통합 테스트를 사용합니다.
- 테스트 context가 느려지면 slice 분리를 우선 검토합니다.

## Security Test 기준

- 인증 성공, 미인증, 권한 부족을 구분해 테스트합니다.
- URL authorization은 MockMvc와 security test support로 검증합니다.
- CSRF가 필요한 요청은 token 유무에 따른 결과를 검증합니다.
- Domain 권한 정책은 Spring Security 없이 단위 테스트합니다.

## TDD와 테스트 선택

TDD 시작점은 다음 순서로 고릅니다.

1. Domain 단위 테스트
2. Application UseCase 단위 테스트
3. Adapter slice 테스트
4. 통합 테스트

처음부터 통합 테스트만 작성하면 TDD 사이클이 느려질 수 있습니다.
하지만 DB constraint, migration, security filter chain처럼 통합 동작이 핵심이면 통합 테스트를 먼저 둘 수 있습니다.

테스트 코드 내부 구조, test double 선택, 테스트 비율, 커버리지와 완료 전 체크리스트는 [../workflow/230-test-writing-guide.md](../workflow/230-test-writing-guide.md)를 따릅니다.

## 테스트 데이터 기준

- 테스트 데이터는 테스트 안에서 의미를 알 수 있게 만듭니다.
- 전역 fixture는 의미가 분명하고 안정적인 경우에만 사용합니다.
- 테스트 간 공유 상태를 피합니다.
- 날짜/시간은 고정된 clock을 사용합니다.
- 랜덤 값은 실패 재현 가능성을 해치지 않게 사용합니다.

## 피해야 할 패턴

- 모든 테스트가 `@SpringBootTest`입니다.
- assertion 없이 context만 띄웁니다.
- mock이 구현 세부사항을 과도하게 고정합니다.
- 테스트 이름이 구현 method 이름만 반복합니다.
- 테스트 데이터가 너무 복잡해 기대값을 이해하기 어렵습니다.
- 실패 테스트를 확인하지 않고 구현부터 작성합니다.

## 관련 참고

- [JUnit User Guide](https://docs.junit.org/6.1.0/overview.html)
- [AssertJ Documentation](https://assertj.github.io/doc/)
- [Spring Boot - Testing](https://docs.spring.io/spring-boot/reference/testing/index.html)
- [Spring Boot - Testing Spring Boot Applications](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)
- [Spring Security - Testing](https://docs.spring.io/spring-security/reference/servlet/test/index.html)

## 관련 문서

- **개발 절차**: [TDD 워크플로우](../workflow/210-tdd-workflow.md)
- **test 작성 기준**: [테스트 작성 가이드](../workflow/230-test-writing-guide.md)
- **계층별 기술 기준**: [Spring Boot](210-spring-boot.md), [Spring Security](220-spring-security.md), [Spring Data JPA](230-spring-data-jpa.md)
