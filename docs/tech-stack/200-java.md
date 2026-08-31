# Java 21 사용 가이드

이 문서는 Java 21을 사용할 때의 기본 원칙과 주의점을 정의합니다.

## 목적

- Java 21의 안정 기능을 활용해 명확하고 테스트하기 쉬운 코드를 작성합니다.
- 최신 기능을 무조건 사용하지 않고, 읽기 쉬움과 유지보수성을 기준으로 선택합니다.
- Spring Boot와 아키텍처 경계를 해치지 않는 코딩 스타일을 유지합니다.

## 기본 원칙

- 명확한 도메인 개념은 class, record, enum, value object로 표현합니다.
- 프로젝트가 소유한 타입은 `Component`, `Service`, `Configuration`처럼 framework·표준
  library와 쉽게 충돌하는 범용 이름 대신 실제 도메인 역할이 드러나는 이름을 사용합니다.
  프로젝트 타입 때문에 import 충돌이 생기면 annotation이나 framework 타입을 완전 수식명으로
  우회하기 전에 프로젝트 타입을 구체화합니다. 양쪽 이름을 바꿀 수 없는 외부 계약이 있고
  별칭을 지원하지 않을 때만 완전 수식명을 예외로 사용합니다.
- 불변 객체를 우선하고, 변경 가능한 상태는 필요할 때만 둡니다.
- Null을 정상 흐름으로 사용하지 않습니다.
- Optional은 반환 타입에서 의미가 있을 때 사용하고, field나 parameter에는 신중히 사용합니다.
- 예외는 기술 예외와 도메인 예외를 구분합니다.
- Stream은 가독성이 좋은 경우에 사용하고, 복잡한 흐름은 명시적 반복문을 사용합니다.

## 권장 사용

- DTO, Command, Query처럼 단순 데이터 전달 객체에는 `record`를 우선 검토합니다.
- 값 객체는 불변성을 유지하고 생성 시 검증합니다.
- enum은 고정된 도메인 상태와 정책 분기에 사용합니다.
- switch expression은 enum/상태 분기에서 가독성이 높을 때 사용합니다.
- sealed type은 상태/결과 타입의 하위 종류가 닫혀 있을 때만 사용합니다.

## Virtual Threads

Java 21은 virtual thread를 정식 기능으로 제공합니다.
OpenJDK JEP 444는 virtual thread가 thread-per-request 스타일의 서버 애플리케이션 확장성을 높이는 것을 목표로 한다고 설명합니다.

이 프로젝트의 기본값은 다음과 같습니다.

- virtual thread는 성능 요구와 blocking I/O 특성을 확인한 뒤 도입합니다.
- 기존 Spring MVC 요청 처리에 무조건 적용하지 않습니다.
- ThreadLocal, transaction context, security context, JDBC driver 동작을 검토합니다.
- 병목이 DB connection pool이면 virtual thread만으로 해결되지 않습니다.
- 도입 전후 부하 테스트와 observability 기준을 먼저 정합니다.

## 예외 처리

- 도메인 예외는 도메인 언어로 이름을 짓습니다.
- Application 예외는 유스케이스 실패 의미를 담습니다.
- Adapter에서는 외부 기술 예외를 내부 의미로 변환합니다.
- Controller Advice는 예외를 HTTP 응답으로 변환합니다.
- checked exception을 무의미하게 wrapping하지 않습니다.

## Collection 기준

- 외부로 노출하는 collection은 가능한 불변 view 또는 복사본을 사용합니다.
- 순서가 의미 있으면 `List`, 중복 제거가 의미 있으면 `Set`을 사용합니다.
- key lookup이 핵심이면 `Map`을 사용하되 key 의미를 문서화합니다.
- 반환 컬렉션이 `null`이면 안 됩니다. 비어 있으면 empty collection을 반환합니다.

## 테스트 기준

- 순수 Java 도메인 로직은 Spring 없이 단위 테스트합니다.
- 값 객체는 정상 생성, 경계값, 실패 케이스를 테스트합니다.
- enum 정책 분기는 모든 의미 있는 값을 테스트합니다.
- 시간 의존 로직은 `Clock` 등으로 현재 시간을 주입 가능하게 만듭니다.

## 피해야 할 패턴

- 도메인 내부에서 Spring annotation에 의존합니다.
- record를 JPA Entity로 사용합니다.
- `Map<String, Object>`로 도메인 구조를 표현합니다.
- 모든 예외를 `RuntimeException` 하나로 처리합니다.
- 성능 근거 없이 parallel stream 또는 virtual thread를 도입합니다.

## 관련 참고

- [OpenJDK - JDK 21](https://openjdk.org/projects/jdk/21/)
- [OpenJDK - JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Oracle - Java SE 21 Documentation](https://docs.oracle.com/javase/21/)

## 관련 문서

- **현재 version owner**: [현재 기술 스택](100-current-stack.md)
- **test 사용 기준**: [테스트 스택](300-testing-stack.md)
- **구조 경계 기준**: [경계와 매핑 규칙](../architecture/220-boundary-and-mapping-rules.md)
