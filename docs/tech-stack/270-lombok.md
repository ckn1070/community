# Lombok 사용 가이드

이 문서는 Lombok 사용 범위와 제한 기준을 정의합니다.

## 목적

- 반복 boilerplate를 줄이되 코드 의미와 도메인 규칙을 숨기지 않습니다.
- JPA Entity, Domain Model, DTO에서 Lombok 사용 기준을 구분합니다.
- 생성되는 코드가 테스트와 유지보수에 미치는 영향을 통제합니다.

## 기본 원칙

- Lombok은 편의 도구이지 설계 도구가 아닙니다.
- 기본 정책은 whitelist와 blacklist를 함께 사용합니다.
- whitelist에 있는 annotation도 허용 계층과 사용 조건을 만족할 때만 사용합니다.
- blacklist에 있는 annotation은 기본 금지이며, 예외가 필요하면 작업 문서에 이유와 검증 기준을 먼저 남깁니다.
- 도메인 의미가 중요한 생성자, factory, validation, 상태 변경 method는 직접 작성합니다.
- JPA Entity에서는 equals/hashCode, toString, setter, builder 자동 생성에 특히 주의합니다.
- DTO나 Command가 record로 충분하면 record를 우선 검토합니다.
- Spring bean, adapter, application service에서 생성자가 `final` 의존성 주입만 수행하면 `@RequiredArgsConstructor`를 기본값으로 사용합니다.

## 판단 순서

Lombok 사용 전에는 다음 순서로 판단합니다.

1. `record`, 명시적 생성자, 명시적 method로 더 명확한지 먼저 확인합니다.
2. Spring bean, adapter, application service의 생성자가 `final` 의존성 주입만 수행하면 `@RequiredArgsConstructor`를 우선 적용합니다.
3. blacklist에 해당하면 사용하지 않습니다.
4. whitelist에 있고 계층별 허용 조건을 만족하면 사용합니다.
5. 제한 사용 항목이면 작업 문서나 코드 가까이에 선택 이유가 남아야 합니다.
6. 판단이 애매하면 Lombok을 쓰지 않고 명시 코드로 작성합니다.

## Whitelist

다음 annotation은 조건을 만족할 때 사용할 수 있습니다.

| Annotation | 허용 위치 | 조건 |
| --- | --- | --- |
| `@Slf4j` | `config`, adapter, application service, test support | 실제 로그를 남기는 클래스에서 사용합니다. logger 이름을 커스텀해야 하거나 marker 정책이 필요한 경우에는 명시적 `LoggerFactory`를 사용합니다. |
| `@RequiredArgsConstructor` | Spring bean, adapter, application service | `final` 의존성 주입 생성자에는 기본값으로 사용합니다. 생성자 안에서 validation, normalization, 보안 정책을 실행해야 하면 직접 작성합니다. |
| `@Getter` | JPA Entity, 읽기 전용 model, record로 표현하기 어려운 DTO | 읽기 accessor만 줄일 때 사용합니다. 도메인 행위나 상태 변경을 getter/setter 조합으로 대체하지 않습니다. |
| `@NoArgsConstructor(access = AccessLevel.PROTECTED)` | JPA Entity | JPA proxy와 reflection 생성을 위한 보호 기본 생성자의 기본값으로 사용합니다. 직접 빈 생성자나 public no-args 생성자는 사용하지 않습니다. |
| `@Builder` | test fixture, test data builder | 테스트 데이터 가독성을 높이는 경우에만 사용합니다. production Domain, JPA Entity, Command에는 사용하지 않습니다. |

## Spring Bean 생성자 기준

- Spring MVC controller, `@Service`, `@Component`, adapter에서 생성자가 `final` field 대입만 한다면 명시 생성자를 작성하지 않고 `@RequiredArgsConstructor`를 사용합니다.
- 생성자에서 validation, normalization, 기본 구현체 조립, 보안 정책 선택, fallback 구성처럼 의도가 있는 로직을 수행하면 명시 생성자를 유지합니다.
- package-private class라도 Spring bean이고 생성자가 단순 의존성 주입만 담당하면 `@RequiredArgsConstructor` 적용 대상입니다.

## 제한 사용

다음 항목은 기본값으로 쓰지 않고, 선택 이유가 명확할 때만 사용합니다.

| Annotation | 제한 기준 |
| --- | --- |
| `@EqualsAndHashCode` | DTO처럼 값 동등성이 명확하고 record를 쓸 수 없을 때만 사용합니다. JPA Entity에는 사용하지 않습니다. |
| `@ToString` | 민감정보와 lazy association이 없고, 출력 범위가 명확할 때만 사용합니다. JPA Entity에는 사용하지 않습니다. |
| `@Builder` production 사용 | 외부 API payload나 설정 객체처럼 생성 규칙이 단순하고 불변조건을 숨기지 않을 때만 별도 결정 후 사용합니다. |
| `@Setter` field-level 사용 | framework binding처럼 setter가 필요한 필드에 한정합니다. class-level `@Setter`는 사용하지 않습니다. |

## Blacklist

다음 annotation과 사용 방식은 기본 금지입니다.

| 대상 | 금지 이유 |
| --- | --- |
| `@Data` | getter, setter, equals/hashCode, toString을 한 번에 열어 도메인 의미, JPA proxy, 민감정보 노출 위험을 통제하기 어렵습니다. |
| class-level `@Setter` | 모든 필드 변경 가능성을 열어 aggregate와 Entity의 상태 변경 규칙을 우회합니다. |
| Domain Model의 `@AllArgsConstructor`, `@RequiredArgsConstructor`, `@Builder` | 생성자와 factory에서 수행해야 하는 불변조건과 validation을 숨기거나 우회할 수 있습니다. |
| JPA Entity의 `@Data`, `@Builder`, `@AllArgsConstructor`, `@EqualsAndHashCode`, `@ToString` | 식별자, proxy, lazy association, 순환 참조, 민감정보 노출 문제가 생길 수 있습니다. |
| `@SneakyThrows` | checked exception 계약을 숨겨 public contract와 테스트 의도를 흐립니다. |
| `@Cleanup`, `@Synchronized` | 자원 관리와 동시성 경계를 annotation 뒤에 숨기므로 명시 코드로 작성합니다. |
| `@UtilityClass` | 생성자, static 설계, 테스트 가능성을 숨기므로 명시적 final class와 private 생성자를 사용합니다. |
| `val`, `var` | Java 표준 `var` 또는 명시 타입을 사용합니다. Lombok 전용 문법은 쓰지 않습니다. |
| `@FieldDefaults`, `@Accessors`, experimental annotation | 프로젝트 전체 스타일을 암묵적으로 바꾸거나 IDE/정적 분석 예측 가능성을 낮춥니다. |

## 아키텍처 경계 적용

- Domain은 명시적 생성자, 정적 factory, 행위 method를 우선합니다.
- Domain은 Lombok 없이 생성하고 테스트할 수 있는 형태를 기본값으로 둡니다.
- Controller, adapter, application service의 단순 의존성 주입은 `@RequiredArgsConstructor`를 기본값으로 사용합니다.
- Adapter DTO는 record 또는 명시적 DTO를 우선 검토합니다.
- JPA Entity는 ORM 제약을 고려해 Lombok 사용을 최소화합니다.
- JPA Entity의 보호 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`로 표현하고, 의미 있는 생성자와 factory는 직접 작성합니다.
- 로깅이 필요한 config, adapter, application service는 `@Slf4j`를 기본 선택지로 사용할 수 있습니다.

## 테스트 기준

- Lombok이 생성한 단순 getter/setter 자체를 테스트하지 않습니다.
- Lombok annotation 때문에 도메인 불변조건 테스트가 우회되지 않는지 확인합니다.
- Entity equals/hashCode가 필요한 경우 영속화 전후 동작을 검토합니다.
- JPA Entity 보호 기본 생성자 관례는 소스 관례 테스트로 검증합니다.
- blacklist 예외를 허용한 경우에는 왜 안전한지 테스트나 작업 문서로 검증합니다.

## 예외 허용 기준

Whitelist에 없는 Lombok 사용이 필요하면 다음을 먼저 남깁니다.

- 왜 명시 코드나 record보다 Lombok이 더 나은지
- 어떤 계층과 파일에 한정되는지
- 도메인 불변조건, JPA proxy, 민감정보 노출, exception contract에 영향이 없는지
- 어떤 테스트나 정적 분석으로 회귀를 막는지

위 항목을 설명할 수 없으면 Lombok을 사용하지 않습니다.

## 관련 참고

- [Project Lombok - Features](https://projectlombok.org/features/)
- [Project Lombok - Constructor](https://projectlombok.org/features/constructor)
- [Project Lombok - @Data](https://projectlombok.org/features/Data)
- [Project Lombok - @Builder](https://projectlombok.org/features/Builder)

## 관련 문서

- **언어 기준**: [Java](200-java.md)
- **persistence 사용 경계**: [Spring Data JPA](230-spring-data-jpa.md)
- **모델 매핑 경계**: [경계와 매핑 규칙](../architecture/220-boundary-and-mapping-rules.md)
