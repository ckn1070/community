# 테스트 작성 가이드

이 문서는 테스트 코드를 작성할 때의 구조, 테스트 선택 기준, 커버리지 해석과 충분성 점검 기준을 정의합니다.
테스트 도구별 사용 기준은 [../tech-stack/300-testing-stack.md](../tech-stack/300-testing-stack.md)를 따르고, 이 문서는 작업자가 실제 테스트를 어떤 모양으로 작성하고 충분성을 어떻게 판단할지에 집중합니다.

## 상황별 읽기 위치

| 상황 | 확인할 위치 |
| --- | --- |
| 변경에 맞는 테스트 유형 선택 | [테스트 선택 매트릭스](#테스트-선택-매트릭스), [도메인 규칙 테스트 범위](#도메인-규칙-테스트-범위) |
| 테스트 구조와 이름 결정 | [단위 테스트 코드 구조](#단위-테스트-코드-구조), [테스트 이름 규칙](#테스트-이름-규칙), [AAA와 Given-When-Then 기준](#aaa와-given-when-then-기준) |
| 주석·중첩·parameterized test 판단 | [테스트 주석 점검 기준](#테스트-주석-점검-기준), [`@Nested`와 Parameterized Test 기준](#nested와-parameterized-test-기준) |
| fixture와 test double 설계 | [Fixture Helper와 Test Data Builder 기준](#fixture-helper와-test-data-builder-기준), [Mock, Stub, Fake 기준](#mock-stub-fake-기준) |
| 충분성 점검 | [테스트 격리와 결정성](#테스트-격리와-결정성), [커버리지 정책](#커버리지-정책), [테스트 충분성 체크리스트](#테스트-충분성-체크리스트) |

## 목적

- 테스트 코드를 작업자마다 다른 암묵지에 맡기지 않고 일관된 구조로 작성합니다.
- 변경 위험에 맞는 가장 작은 테스트를 먼저 선택합니다.
- 커버리지와 테스트 비율을 절대 목표가 아니라 테스트 구조를 점검하는 신호로 사용합니다.
- 기능 완료 전에 테스트가 충분한지 확인할 수 있는 기준을 제공합니다.

## 적용 범위

- 기능 추가와 버그 수정에 동반되는 테스트
- 도메인 규칙, 유스케이스, API 계약, 보안 규칙, persistence 규칙 테스트
- TDD 사이클에서 작성하는 실패 테스트와 회귀 테스트
- 테스트 fixture, helper, fake, mock, stub 작성
- 완료 전 테스트 충분성 점검

성능 테스트, 부하 테스트, 프론트엔드 E2E 테스트, consumer-driven contract 테스트, mutation testing은 이 문서의 기본 범위가 아닙니다.
해당 테스트가 필요하면 별도 작업 기준을 먼저 정합니다.

## 핵심 원칙

- 테스트는 구현 세부사항보다 관찰 가능한 동작을 검증합니다.
- 가장 빠르고 좁은 테스트로 규칙을 고정하고, 프레임워크와 외부 시스템 경계는 필요한 만큼만 보강합니다.
- 테스트 실패 이유는 한눈에 이해할 수 있어야 합니다.
- 테스트 데이터는 테스트 안에서 의미를 알 수 있어야 합니다.
- 커버리지는 충분성의 보조 신호이며, 커버리지 수치만으로 테스트 품질을 판단하지 않습니다.
- 테스트 구조가 복잡해지면 production 코드의 책임 분리 문제를 먼저 의심합니다.

## 테스트 선택 매트릭스

변경이 여러 성격을 동시에 가지면 가장 좁은 테스트를 먼저 작성하고, 위험 경계에 필요한 테스트를 추가합니다.

| 변경 대상 | 기본 테스트 | 보강 테스트 | 완료 기준 |
| --- | --- | --- | --- |
| Value Object, Domain Entity, Policy | 단위 테스트 | 없음 | 의미 있는 모든 도메인 규칙의 성공, 실패, 경계, 불변 조건이 Spring 없이 검증됩니다. |
| Application UseCase 결정 로직 | 단위 테스트 | Transaction, persistence, security 조합이 핵심이면 통합 테스트 | fake나 stub으로 외부 의존을 통제하고 유스케이스 결과와 저장 효과를 검증합니다. |
| Controller 요청/응답 계약 | Web MVC slice 테스트 | 공통 예외, security filter 조합이 핵심이면 통합 테스트 | status, response body, validation 오류 형식이 고정됩니다. |
| Security URL 인가 규칙 | Security slice 또는 MockMvc 테스트 | handler나 filter가 MVC를 거치지 않으면 직접 단위 테스트 | 인증 성공, 미인증, 권한 부족이 구분됩니다. |
| 공통 예외 응답 | MVC slice 테스트 | Security handler처럼 MVC 밖이면 직접 단위 테스트 | status, code, detail, field error 노출 범위가 고정됩니다. |
| Repository query, JPA mapping | Data JPA slice 테스트 | PostgreSQL dialect, migration, constraint가 핵심이면 PostgreSQL 통합 테스트 | query 결과, mapping, constraint 의존 지점이 실제 DB에 가깝게 검증됩니다. |
| Flyway migration, DB constraint, index | PostgreSQL 통합 테스트 | 없음 | schema, seed data, unique/check/index 같은 DB 보장이 검증됩니다. |
| 설정과 Bean wiring | Smoke 테스트 또는 좁은 slice 테스트 | 외부 설정 조합이 핵심이면 통합 테스트 | context 기동 또는 필요한 bean wiring이 검증됩니다. |
| 모듈 경계와 계층 의존성 | ArchUnit 또는 Spring Modulith 테스트 | 없음 | 의존 방향, 모듈 경계, internal 접근 금지가 자동 검증됩니다. |
| 버그 수정 | 버그를 재현하는 가장 좁은 실패 테스트 | 재발 위험 경계에 맞는 slice/integration 테스트 | 수정 전 실패 이유가 버그와 일치하고 수정 후 통과합니다. |

## 도메인 규칙 테스트 범위

도메인 코드는 테스트 비용이 가장 낮고 변경 위험이 가장 큰 영역입니다.
따라서 도메인은 line coverage 100%가 아니라 business rule coverage 100%를 목표로 봅니다.
의미 있는 도메인 규칙, 분기, 상태 전이, 불변 조건은 모두 단위 테스트로 직접 드러나야 합니다.

도메인 테스트 목록은 다음 분류로 작성합니다.

| 분류 | 의미 | 예시 |
| --- | --- | --- |
| 성공 케이스 | 유효한 입력이나 상태에서 의도한 결과를 만드는 규칙 | 이메일을 소문자로 정규화합니다. |
| 실패 케이스 | 잘못된 입력이나 허용되지 않은 상태를 거부하는 규칙 | 빈 이메일, 중복 이메일, 허용되지 않은 상태 전이를 거부합니다. |
| 경계/엣지 케이스 | 규칙이 바뀌는 최소/최대/동등성 경계 | 최대 길이, 최소 길이, 대소문자, 공백, 시간 만료 경계를 검증합니다. |
| 불변 조건 | 객체가 생성되거나 변경된 뒤 항상 유지해야 하는 조건 | 활성 역할 중복 금지, 생성 시간이 수정 시간보다 늦지 않음을 검증합니다. |
| 회귀 케이스 | 실제로 발견된 결함이 다시 발생하지 않게 고정하는 규칙 | 버그를 재현하는 입력과 기대 실패 이유를 검증합니다. |

모든 public method마다 기계적으로 성공, 실패, 경계 테스트를 하나씩 만들지는 않습니다.
테스트 단위는 method가 아니라 도메인 규칙입니다.
하나의 public method가 여러 규칙을 대표하면 규칙별로 테스트를 나누고, 여러 method가 같은 규칙을 공유하면 가장 자연스러운 public 동작에서 검증합니다.

다음 코드는 억지로 테스트하지 않습니다.

- 단순 getter, setter, record accessor
- Lombok이나 framework가 생성하는 코드
- public 동작으로 이미 검증되는 private helper
- 도메인 규칙 없이 값을 운반하는 DTO

## 단위 테스트 코드 구조

단위 테스트는 Spring Context 없이 JUnit과 AssertJ를 기본으로 작성합니다.
테스트 대상과 필요한 test double만 직접 생성합니다.

```java
class RegisterMemberUseCaseTest {

  private FakeAccountRepository accounts;
  private RegisterMemberUseCase useCase;

  @BeforeEach
  void setUp() {
    accounts = new FakeAccountRepository();
    useCase = new RegisterMemberUseCase(accounts);
  }

  @Test
  void registersMemberWithNormalizedEmail() {
    var command = command("USER@example.com");

    var result = useCase.execute(command);

    assertThat(result.email()).isEqualTo("user@example.com");
    assertThat(accounts.savedMembers()).hasSize(1);
  }

  @Test
  void rejectsDuplicatedEmail() {
    accounts.save(member("user@example.com"));

    assertThatThrownBy(() -> useCase.execute(command("user@example.com")))
        .isInstanceOf(ApiException.class);
  }

  private RegisterMemberCommand command(String email) {
    return new RegisterMemberCommand(email, "password123!");
  }

  private Member member(String email) {
    return Member.create(email);
  }
}
```

구조 기준:

- 테스트 클래스 이름은 `테스트대상명Test`를 사용합니다.
- 필드는 테스트 대상과 테스트 대상 생성에 필요한 의존성만 둡니다.
- `@BeforeEach`는 테스트마다 새 상태를 만드는 용도로만 사용합니다.
- helper method는 테스트 하단에 두고, `command`, `member`, `storedAccount`처럼 도메인 의미가 드러나는 이름을 사용합니다.
- 단위 테스트에 `@SpringBootTest`, `MockMvc`, `Testcontainers`를 넣지 않습니다.
- 테스트가 production method의 내부 호출 순서를 검증하기 시작하면 구현 세부사항에 결합한 것인지 점검합니다.

## 테스트 이름 규칙

- 테스트 메서드 이름은 영어 camelCase 동작문을 사용합니다.
- production method 이름만 반복하지 않고 검증하는 결과를 드러냅니다.
- 성공 케이스와 실패 케이스가 모두 중요하면 이름에서도 차이를 드러냅니다.
- `should`, `when` 같은 접두어는 일관되게 읽히면 사용할 수 있지만 필수는 아닙니다.
- `@DisplayName`은 한글 설명이 실패 리포트 이해에 실제로 도움이 될 때만 사용합니다.

좋은 이름:

- `registersMemberWithNormalizedEmail`
- `rejectsDuplicatedEmail`
- `returnsProblemDetailForRequestValidationFailure`
- `preventsDuplicateActiveRoleAssignmentAndAllowsReassignmentAfterRevocation`

피할 이름:

- `testRegister`
- `registerMember_success`
- `shouldWork`
- `save`

## AAA와 Given-When-Then 기준

기본 구조는 `Arrange -> Act -> Assert` 또는 같은 의미의 `Given -> When -> Then`입니다.
일반 테스트에서는 주석보다 빈 줄로 세 단계를 구분합니다.

```java
@Test
void lowercasesEmail() {
  var email = "USER@example.com";

  var normalized = Email.normalize(email);

  assertThat(normalized).isEqualTo("user@example.com");
}
```

예외 테스트는 실행과 검증을 `assertThatThrownBy` 안에 함께 둘 수 있습니다.

```java
@Test
void rejectsBlankEmail() {
  assertThatThrownBy(() -> Email.of(" "))
      .isInstanceOf(IllegalArgumentException.class);
}
```

세부 기준:

- 하나의 테스트는 하나의 핵심 동작을 검증합니다.
- 같은 동작을 설명하는 여러 assertion은 한 테스트에 둘 수 있습니다.
- assertion이 없는 테스트는 만들지 않습니다.
- `given`, `when`, `then` 주석은 테스트가 길고 단계 경계가 실제로 헷갈릴 때만 사용합니다.
- Act 단계가 여러 개 필요하면 하나의 테스트가 너무 많은 동작을 검증하는지 먼저 확인합니다.

## 테스트 주석 점검 기준

테스트 주석은 테스트 이름을 자연어로 반복하지 않고, 테스트가 고정하는 정책, 계약, fixture 전제를 설명합니다.
테스트를 작성하거나 의미 있게 수정하면 [220-code-commenting-guideline.md](220-code-commenting-guideline.md)의 테스트 주석 기준을 함께 적용합니다.

주석이 필요한 대표 상황:

| 대상 | 주석에 담을 내용 | 권장 위치 |
| --- | --- | --- |
| 보안, 예외, 아키텍처 테스트 | 어떤 정책을 깨뜨리지 않기 위한 테스트인지, 실패 시 어떤 경계가 무너지는지 | test class 또는 test method 바로 위 |
| persistence, migration, DB constraint 테스트 | 어떤 DB 보장을 application 정책이나 오류 코드가 의존하는지 | test class 또는 constraint를 검증하는 test method 위 |
| 통합 테스트 | 실제로 함께 검증하는 계층, slice 테스트가 맡지 못하는 wiring이나 rollback 경계 | test class 위, 핵심 scenario method 위 |
| controller, handler 테스트 | request validation, 예외 매핑, 내부 정보 미노출, usecase 호출 여부 같은 HTTP contract | test method 위 |
| fixture, fake, stub, helper | 실제 구현인지 테스트 대역인지, 어떤 side effect나 호출 차단을 관찰하기 위한 것인지 | fixture class 또는 helper method 위 |
| domain 정책 테스트 | 이름만으로 드러나지 않는 정규화 소유권, 저장 한계, 상태 전이, 보안 정책 | test class 또는 정책별 test method 위 |

주석을 생략할 수 있는 경우:

- 테스트명, 입력값, assertion만으로 정책과 실패 의미가 바로 드러나는 단순 값 변환 테스트입니다.
- 같은 class의 class Javadoc이 모든 test method의 전제를 충분히 설명하고, 개별 method에 숨은 정책 차이가 없습니다.
- `given`, `when`, `then`처럼 단계만 반복하는 주석을 넣어야 할 정도로 테스트가 길다면 먼저 테스트 분리를 검토했습니다.

주석 기준 미충족:

- 보안, 예외, 아키텍처, persistence, migration, 복잡한 fixture 테스트에 주석이 없는데 테스트명만으로 충분하다고 처리하지 않습니다.
- 테스트 주석 누락이 발견되면 해당 파일만 보지 않고 같은 변경 범위의 테스트 전체를 다시 점검합니다.
- 주석이 `저장한다`, `검증한다`처럼 구현 한 줄을 번역하는 수준이면 주석을 추가한 것으로 보지 않습니다.

## `@Nested`와 Parameterized Test 기준

`@Nested`는 같은 테스트 대상 안에서 맥락이 반복될 때 사용합니다.
단순한 테스트 두세 개를 모으기 위해 과하게 중첩하지 않습니다.

```java
@Nested
class WhenEmailAlreadyExists {

  @BeforeEach
  void setUpDuplicatedEmail() {
    accounts.save(member("user@example.com"));
  }

  @Test
  void rejectsRegistration() {
    assertThatThrownBy(() -> useCase.execute(command("user@example.com")))
        .isInstanceOf(ApiException.class);
  }
}
```

Parameterized test는 같은 규칙을 여러 입력에 반복 적용할 때 사용합니다.
입력마다 기대 이유가 다르면 별도 테스트로 분리합니다.

```java
@ParameterizedTest
@ValueSource(strings = {"", " ", "not-email"})
void rejectsInvalidEmail(String email) {
  assertThatThrownBy(() -> Email.of(email))
      .isInstanceOf(IllegalArgumentException.class);
}
```

기준:

- `@Nested` 이름은 상황이나 public 동작을 드러냅니다.
- 2단계 이상 중첩은 기본적으로 피합니다.
- parameterized test의 입력과 기대값은 테스트 본문만 봐도 의미를 알 수 있어야 합니다.
- 실패했을 때 어떤 입력이 실패했는지 리포트에서 구분되어야 합니다.

## Fixture Helper와 Test Data Builder 기준

Fixture는 테스트가 같은 상황을 반복 재현하도록 미리 고정한 입력 데이터나 환경입니다.
정상 입력과 반드시 거부돼야 할 실패 입력을 모두 포함할 수 있으며, mock처럼 의존 객체의 행동을 대신하는 개념과 구분합니다.

테스트 데이터는 해당 테스트에서 중요한 값과 중요하지 않은 값을 구분해 보여야 합니다.

- 테스트 본문에는 검증 의미가 있는 값만 직접 드러냅니다.
- 반복되는 객체 생성은 helper method로 숨깁니다.
- helper method 이름은 생성 절차가 아니라 의미를 설명합니다.
- 여러 테스트에서 같은 기본값이 필요하면 test data builder를 사용할 수 있습니다.
- 전역 fixture는 의미가 안정적이고 여러 테스트에서 같은 도메인 개념을 나타낼 때만 사용합니다.
- helper가 production 로직을 재구현하면 안 됩니다.

예시:

```java
private RegisterMemberCommand command(String email) {
  return new RegisterMemberCommand(email, "password123!");
}
```

```java
private Member activeMember(String email) {
  return Member.create(email);
}
```

피할 패턴:

- 모든 테스트가 같은 거대한 fixture를 공유합니다.
- 테스트 본문에서 중요하지 않은 필드까지 길게 나열합니다.
- helper 이름이 `createData1`, `makeObject`, `fixture`처럼 의미를 드러내지 않습니다.
- 기대값을 만들기 위해 production 코드와 같은 계산을 helper에 다시 씁니다.

## Mock, Stub, Fake 기준

Test double은 외부 의존을 통제하기 위한 도구입니다.
구현 내부 호출 순서를 고정하기 위한 기본 수단으로 사용하지 않습니다.

| 종류 | 의미 | 기본 사용처 |
| --- | --- | --- |
| Stub | 정해진 값을 반환하는 단순 대역 | clock, id generator, password encoder 결과처럼 출력만 필요한 의존성 |
| Fake | 동작하는 단순 구현체 | usecase 단위 테스트의 repository, event publisher, in-memory store |
| Mock | 호출 여부와 인자를 검증하는 대역 | 외부 side effect, notification, audit event처럼 interaction 자체가 결과인 의존성 |

기준:

- Domain 객체, value object, command 객체는 mock하지 않습니다.
- UseCase 단위 테스트에서는 repository mock보다 fake repository를 우선 검토합니다.
- 외부 API, 메시지 발행, 메일 발송처럼 호출 자체가 계약이면 mock으로 interaction을 검증할 수 있습니다.
- mock 검증은 public 결과를 설명하는 데 필요한 호출만 대상으로 합니다.
- private method 호출, 내부 helper 호출, repository 호출 순서처럼 구현 세부사항에 가까운 검증은 피합니다.
- Spring slice 테스트에서 `@MockBean`을 쓰면 Controller 계약을 고정하기 위한 의존성 대체인지 확인합니다.

## 테스트 격리와 결정성

테스트는 실행 순서와 실행 환경에 의존하지 않아야 합니다.

- 테스트 간 공유 mutable state를 두지 않습니다.
- 테스트 순서에 의존하지 않습니다.
- 현재 시간이 규칙에 영향을 주면 고정된 `Clock`이나 stub을 사용합니다.
- 랜덤 값은 실패 재현을 해치지 않도록 seed를 고정하거나 의미 있는 고정 값을 사용합니다.
- thread sleep, 실제 시간 경과, 외부 네트워크, 로컬 파일 시스템 상태에 의존하지 않습니다.
- DB 통합 테스트는 테스트마다 데이터 격리, rollback, cleanup, 고유 key 중 하나로 충돌을 막습니다.
- 이 저장소의 PostgreSQL Testcontainers 테스트는 Docker가 없는 환경에서 skip하지 않고 실패합니다.
- Docker 없이 실행 가능한 계약 테스트가 공통 Testcontainers 기반의 `disabledWithoutDocker = false`를 고정합니다.
- PostgreSQL Testcontainers 테스트는 직접 또는 간접으로 공통 기반을 상속해야 하며 별도 `PostgreSQLContainer`·`@Testcontainers` 구성으로 이 정책을 우회하지 않습니다.
- 공통 기반 밖의 직접 Testcontainers 의존은 기존 Java 계약 테스트의 ArchUnit 규칙으로 차단합니다.
- Docker preflight와 Gradle `check`의 실행·판정 계약은 [코드 품질 도구](../tech-stack/310-code-quality-tools.md)가 소유합니다.

테스트가 간헐적으로 실패하면 production 코드보다 테스트 결정성 문제를 먼저 의심합니다.
간헐 실패를 재시도나 wait 증가만으로 덮지 않습니다.

## 테스트 비율 기준

테스트 비율은 강제 수치가 아니라 테스트 구조가 뒤집혔는지 확인하는 신호입니다.
기본 방향은 단위 테스트가 가장 많고, slice 테스트가 그다음이며, 통합 테스트는 핵심 흐름과 위험 경계에 제한적으로 둡니다.

권장 출발점:

- 단위 테스트: 60~70%
- Slice 테스트: 20~30%
- 통합, smoke, architecture 테스트: 5~10%

이 비율은 테스트 개수나 코드 줄 수를 정확히 맞추기 위한 목표가 아닙니다.
다음 상황이면 테스트 구조를 재검토합니다.

- 대부분의 규칙이 `@SpringBootTest`로만 검증됩니다.
- 도메인이나 유스케이스 규칙이 있는데 단위 테스트가 거의 없습니다.
- Controller 테스트가 business rule까지 길게 검증합니다.
- DB 통합 테스트가 순수 계산이나 상태 전이를 검증합니다.
- mock 기반 단위 테스트가 production 내부 호출 순서에 과하게 묶여 있습니다.

## 커버리지 정책

커버리지는 테스트 품질을 대신하지 않습니다.
커버리지 수치는 누락된 테스트를 찾는 보조 지표로 사용합니다.

기준:

- 전체 line coverage보다 변경분 coverage와 branch coverage를 우선 봅니다.
- Domain의 의미 있는 규칙, 실패 분기, 경계값, 상태 전이, 불변 조건은 모두 단위 테스트로 직접 드러나야 합니다.
- Application의 새 결정 규칙과 실패 분기는 직접 테스트로 드러나야 합니다.
- Controller, Security, Persistence는 단위 커버리지보다 계약과 통합 경계 검증을 우선합니다.
- getter, 단순 DTO, framework wiring만 있는 코드를 커버리지 수치 때문에 억지로 테스트하지 않습니다.
- 커버리지가 높아도 assertion이 약하거나 구현 세부사항만 검증하면 충분하지 않습니다.
- 커버리지가 낮아도 변경 위험이 낮은 단순 설정이면 다른 검증으로 충분할 수 있습니다.

현재 저장소에는 별도 coverage plugin 기반의 자동 차단 gate가 없습니다.
따라서 커버리지 수치는 자동화된 test 품질 gate가 아니라 테스트 충분성 점검 기준입니다.
coverage gate를 CI에 추가하려면 build 설정, 제외 범위, 초기 기준, 실패 처리 방식을 별도 작업으로 결정합니다.

## 테스트 충분성 체크리스트

테스트를 작성하거나 변경할 때 다음 충분성 기준을 확인합니다.

- 변경 성격에 맞는 테스트 종류를 선택했습니다.
- 가장 좁고 빠른 테스트에서 핵심 규칙을 먼저 검증했습니다.
- 도메인 규칙은 성공, 실패, 경계, 불변 조건, 회귀 케이스 관점에서 누락을 점검했습니다.
- 실패 테스트가 기대한 이유로 실패하는 것을 확인했습니다.
- 성공, 실패, 경계값, 권한, 데이터 무결성 중 현재 변경의 위험 경계를 검증했습니다.
- 테스트 이름만 읽어도 깨진 동작을 추정할 수 있습니다.
- 위험 경계 테스트는 `220-code-commenting-guideline.md` 기준으로 어떤 정책과 fixture 전제를 고정하는지 코드 가까이에 드러납니다.
- 테스트 데이터에서 중요한 값과 중요하지 않은 값이 구분됩니다.
- mock, stub, fake 사용 이유가 테스트 목적과 맞습니다.
- 테스트가 시간, 랜덤, 실행 순서, 외부 상태에 의존하지 않습니다.
- Spring Context는 slice나 통합 테스트가 필요한 경우에만 사용했습니다.
- 커버리지와 테스트 비율 관점에서 테스트 구조가 한쪽으로 뒤집히지 않았습니다.
- 작성한 test가 저장소의 표준 test·quality entrypoint에서 실행 가능하고 임의 skip이나
  별도 우회 경로에 의존하지 않습니다.

## 관련 문서

- **작성 순서**: [TDD 워크플로우](210-tdd-workflow.md)
- **test 주석 기준**: [코드와 설정 주석 가이드](220-code-commenting-guideline.md)
- **공통 완료 lifecycle**: [작업 실행 워크플로우](100-work-execution-workflow.md)
- **Git 완료 경로**: [커밋 워크플로우](300-commit-workflow.md)
- **test 기술 기준**: [테스트 스택](../tech-stack/300-testing-stack.md)
- **quality gate**: [코드 품질 도구](../tech-stack/310-code-quality-tools.md)
