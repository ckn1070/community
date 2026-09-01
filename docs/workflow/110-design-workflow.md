# 설계 워크플로우

이 문서는 장기 유지보수성, 데이터·보안·운영 계약과 되돌리기 비용에 의미 있는 영향을 주는 설계 작업의 판단 순서와 완료 게이트를 소유합니다. 도메인 모델, REST, module과 기술별 상세 기준은 관련 architecture·tech-stack 문서가 소유합니다. 요청·권한·공통 단위 상태·병렬·차단과 final discovery·closure는 [작업 실행 워크플로우](100-work-execution-workflow.md)를 따르며 이 문서에서 다시 정의하지 않습니다.

## 사용 시점

다음 중 하나라도 결과가 달라지면 이 workflow를 적용합니다.

- public API, event, file, CLI와 외부 계약
- DB schema, 데이터 생명주기, transaction과 migration
- 인증·인가, 개인정보, secret과 신뢰 경계
- 모듈·package·port/adapter와 공용 기반 구조
- 외부 시스템, executable, Action, SaaS와 운영 책임
- 성능·가용성·복구·배포·rollback에 큰 영향이 있는 선택
- 이후 변경 비용이 크거나 여러 기능의 기준이 되는 결정

오탈자, 기존 결정이 명확한 국소 구현과 쉽게 되돌릴 수 있는 작은 설정은 필요한 부분만 가볍게 적용합니다.

## 설계 입력

1. 작업 실행 workflow가 확정한 제품·기술 결과, 관찰 가능한 성공 조건과 범위를 입력으로 받습니다.
2. 현재 코드·설정·schema·운영 상태와 기존 결정을 조사합니다.
3. 결과에 영향을 주는 설계 표면과 근거 있는 제외 표면을 식별합니다.
4. 결과를 바꾸는 사용자 소유 결정과 저장소·공식 근거로 닫을 기술 판단을 분리합니다.

초기 가정이나 기존 문서를 정답으로 고정하지 않습니다. Best Practice는 후보를 좁히는 근거이며 현재 맥락의 trade-off를 대신하지 않습니다.

## 설계 표면

주제에 관련된 표면만 조사하되, 적용 여부를 근거와 함께 닫습니다.

| 표면 | 확인할 질문 |
| --- | --- |
| 사용자·업무 | 정상·실패·경계 흐름과 상태 전이는 무엇인가 |
| 외부 계약 | API·event·schema·CLI의 호환성과 오류는 무엇인가 |
| 도메인 | 불변식, Aggregate와 책임 경계는 어디인가 |
| 데이터 | constraint, transaction, 동시성, 보존·삭제는 어떻게 보장하는가 |
| 구조 | module·package·port/adapter의 소유권과 의존 방향은 무엇인가 |
| 보안 | 인증·인가, secret, 개인정보와 신뢰 경계는 무엇인가 |
| 품질 | test 가능성, 성능, 자원과 관측 가능성은 충분한가 |
| 운영 | 배포, migration, rollback, 장애 격리와 복구 책임은 누구에게 있는가 |
| 도구 | 무도구 대안, 독립 oracle, 생애주기 비용과 제거 조건은 무엇인가 |
| 문서 | 현재 사실·결정·계획·절차의 canonical owner는 어디인가 |

`미조사`, `대안 분석 중`, `열린 사용자 소유 결정`이 남은 표면이 있으면 설계를 완료하지 않습니다. 관련 없는 표면은 해당 없음의 이유를 설명할 수 있어야 합니다.

## 기반 구조 판단

공통 abstraction이나 기반 모듈을 추가하기 전에 다음 순서로 판단합니다.

1. 현재 요구를 기존 책임 안에서 단순하게 해결할 수 있는지 확인합니다.
2. 실제로 둘 이상의 소비자가 안정된 공통 계약을 필요로 하는지 확인합니다.
3. 공통화하지 않았을 때의 중복 비용과 공통화했을 때의 결합·migration 비용을 비교합니다.
4. public surface, owner, 확장 방식, 실패 격리와 제거 조건을 정합니다.
5. future-proofing만 남으면 현재 기능에 필요한 최소 구조를 선택합니다.

외부 executable·Action·SaaS와 repository 내부 실행 도구는 [도구 도입 워크플로우](130-tool-adoption-workflow.md)를 함께 적용합니다.

## 대안과 결정

결과에 의미 있는 차이가 있는 선택은 실제 채택 가능한 2~3개 대안으로 압축합니다.

결정마다 다음을 닫습니다.

1. 결정할 질문과 배경
2. 현재 제약과 authoritative 근거
3. 실행 가능한 대안
4. 정확성, 안전, 유지보수, 운영, 비용과 가역성 중 결론을 바꾸는 축
5. 추천안, 선택 주체와 선택 근거
6. 얻는 것, 포기하는 것과 남은 위험
7. 재검토 조건

제품 동작·범위·UX 우선순위, 법·개인정보 감수 수준, 예산·외부 계약·운영 책임과 출시
범위·일정처럼 프로젝트 외부 판단이 필요한 값은 사용자가 선택합니다. API·schema·module·
test 같은 기술값은 source·대안·trade-off로 Agent가 선택하고 독립 검증합니다. 기술안이 외부
판단을 함께 요구하면 그 값만 사용자 결정으로 분리하며, 변경 비용이 크다는 이유만으로 기술
선택 자체를 사용자에게 넘기지 않습니다.

여러 결정을 분석하고 설계를 닫는 순서는 의존성이 가장 큰 것부터 진행합니다. 다만 첫 실질
projection 전에 발견한 관련 열린 사용자 결정은 작업 실행 workflow에 따라 한 질문 라운드로
모읍니다. 결정되지 않은 사용자 소유 추천을 확정 설계나 구현 계획으로 승격하지 않습니다.
Agent가 기술 기준으로 선택한 값도 사용자 확정으로 표시하지 않습니다. 설계 후보에 모든
선택과 source 제약을 반영·검증한 뒤 그 exact 현재 단계 결과를 사용자가 확정해야 다음 계획
단계로 전환합니다.

## 전문 기준 routing

| 설계 대상 | 먼저 볼 기준 |
| --- | --- |
| 용어, DDD와 모델 경계 | [아키텍처 개념](../architecture/110-architecture-concepts.md), [헥사고날 원칙](../architecture/200-hexagonal-architecture.md) |
| Spring module·package | [Spring Boot 모듈 구조](../architecture/210-spring-boot-module-structure.md) |
| request·response·port·entity 경계 | [경계와 매핑 규칙](../architecture/220-boundary-and-mapping-rules.md) |
| REST resource·URI·status·error | [REST API 설계](../architecture/230-rest-api-design.md) |
| DB migration과 schema | [Flyway](../tech-stack/240-flyway.md), [PostgreSQL](../tech-stack/250-postgresql.md) |
| 인증·인가 | [Spring Security](../tech-stack/220-spring-security.md), 관련 domain 정책 |
| test 전략 | [테스트 작성 가이드](230-test-writing-guide.md) |
| 외부·내부 도구 | [도구 도입 워크플로우](130-tool-adoption-workflow.md) |
| 문서 구조 | [문서화 워크플로우](150-documentation-workflow.md) |

Workflow는 위 상세 규칙을 다시 설명하지 않습니다.

## 검증

첫 설계를 방어하지 말고 다음 방향에서 반례를 찾습니다.

- 목표와 모든 고정 제약이 설계 계약에 연결되는가
- 모든 새 구조·도구·복잡성이 요구나 위험에 정당화되는가
- 정상 흐름과 함께 잘못된 입력, 권한 거부, 중복, 동시성, 부분 실패가 닫혔는가
- data constraint와 transaction이 애플리케이션 설명만으로 대체되지 않았는가
- 보안, 개인정보와 외부 신뢰 경계가 명시됐는가
- rollout, rollback, 장애 진단과 owner가 실행 가능한가
- test가 구현 세부가 아니라 중요한 계약과 실패 경계를 증명하는가
- 현재 source와 전환 경로가 호환되며 되돌릴 수 있는가
- 확정 사실, 결정, 계획과 절차가 올바른 문서 owner에 배치됐는가

검증되지 않은 가정과 외부 환경 공백은 성공으로 추정하지 않습니다.

## 산출물

설계 결과는 목적에 맞게 분리합니다.

- 현재 사실과 반복 규칙: 책임 SOT
- 결정 이유와 목표 계약: [작업 문서 생명주기](../work-items/100-work-item-lifecycle.md#해결-설계)의
  조건을 통과한 work item `-design`
- 구현 순서와 검증: 해결 설계 closure 뒤 Plan 구조의 work item
- 실제 실행 명령: workflow 또는 operations runbook
- 현재 사용자에게 필요한 결정·상태: 목적별 채팅 보고

대화 원문, 내부 사고 과정, 후보의 전체 나열과 반복 검증 기록은 저장하지 않습니다. 문서 작성·검증은 [문서화 워크플로우](150-documentation-workflow.md)를 적용합니다.

## 완료 조건

- 목표와 수용 기준이 확정된 선행 단계 결과에 근거합니다.
- 결과에 영향을 주는 설계 표면이 권위에 맞게 선택·source 제약 또는 근거 있는 해당
  없음입니다.
- 중대한 결정이 배경·대안·선택 주체·영향·재검토 조건과 함께 복원됩니다.
- 구현자가 제품·기술 결정을 새로 만들지 않고 실행할 수 있습니다.
- 실패·보안·데이터·운영·전환과 검증 책임이 명확합니다.
- 문서 유형과 canonical owner가 확정됐습니다.
- 위 결과를 반영·검증한 해결 설계 후보의 exact 범위를 사용자가 확정했습니다.

이 목록은 work item의 계획 단계를 열거나 rollback하는 계약이 아닙니다. 현재 단계와 artifact
수는 작업 문서 생명주기에서 판정하며, 결과 정의가 닫히기 전에는 이 workflow의 설계 표면
inventory를 `-design`이나 구현 계획으로 보존하지 않습니다.

미충족 설계 표면은 정확한 결정·근거·환경 공백과 영향을 설계 결과에 남깁니다. 그 결과가 후속 구현을 실제로 막는지와 재개 조건은 작업 실행 workflow가 판정합니다.

## 관련 문서

- **공통 실행 lifecycle**: [작업 실행 워크플로우](100-work-execution-workflow.md)
- **설계 피드백**: [설계 피드백 워크플로우](120-design-feedback-workflow.md)
- **도구 결정**: [도구 도입 워크플로우](130-tool-adoption-workflow.md)
- **개발 실행**: [개발 작업 워크플로우](200-development-workflow.md)
- **문서 구조**: [문서화 워크플로우](150-documentation-workflow.md)
- **계획 단계와 artifact**: [작업 문서 생명주기](../work-items/100-work-item-lifecycle.md#계획-단계와-최소-artifact)
