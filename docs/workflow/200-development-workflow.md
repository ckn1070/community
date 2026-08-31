# 개발 작업 워크플로우

이 문서는 production 코드, test와 런타임 설정을 변경하는 개발 절차를 소유합니다.
공통 실행과 완료 판정은 [작업 실행 워크플로우](100-work-execution-workflow.md)를 따릅니다.

## 적용 범위

- 기능 추가와 버그 수정
- 도메인, use case, controller와 adapter 변경
- persistence와 외부 연동 변경
- 런타임 동작을 바꾸는 설정
- 개발에 필요한 test와 작업 문서

문서만 바꾸는 작업은 [문서 정보 구조 가이드](../100-documentation-guide.md)와 [문서화 워크플로우](150-documentation-workflow.md)를 사용합니다. 개발에 동반되는 문서도 같은 기준으로 실제 구현과 의미를 동기화합니다.

## 기본 절차

1. 작업 실행 workflow가 확정한 결과 계약, 허용 표면과 dirty 경계를 입력으로 받습니다.
2. 관련 코드, test와 현재 SOT를 읽고 호출 흐름과 책임 경계를 파악합니다.
3. 설계 또는 기반 구조가 포함되면 [설계 워크플로우](110-design-workflow.md)를 먼저 통과합니다.
4. 외부 도구나 저장소 내부 실행 script·verifier·hook의 도입·존치·제거, caller,
   보장 범위, 비용 또는 dependency·lifecycle이 바뀌면
   [도구 도입과 생애주기 워크플로우](130-tool-adoption-workflow.md)를 적용합니다.
5. 저장소 실행 script를 추가·변경하면
   [스크립트 엔지니어링 가이드](240-script-engineering-guideline.md)로 책임 경계, interface,
   안전성, 검증과 운영 계약을 확정합니다.
6. 기능, 버그와 도메인 변경은 [TDD 워크플로우](210-tdd-workflow.md)로 가장 작은 실패 test부터 시작합니다.
7. 기존 구조와 naming을 따라 확정된 목표를 충족하는 가장 작은 단위로 구현합니다.
8. 구현 중 새 설계나 계획 기반 결함이 드러나면 봉합하지 않고 관련 plan·design owner에
   인계하며, 영향 단위의 상태 전이는 작업 실행 workflow를 따릅니다.
9. 변경 지점에 가까운 test부터 실행하고 완료 전 전체 검증 범위를 판단합니다.
10. 코드·설정·SQL 주석과 환경변수 계약을 대조합니다.
11. 실제 구현 결과, work item과 영구 SOT를 동기화합니다.

## 구현 전 확인

- 현재 코드의 소유 모듈, 호출 흐름과 의존 방향을 확인했습니다.
- 기능 구현, 작은 기반, 큰 기반 또는 혼합 작업으로 분류했습니다.
- public API, DB, 보안, transaction과 외부 연동 설계가 필요한지 확인했습니다.
- 모든 변경 module에서 업무 언어·규칙·모델·상태와 생명주기·소유권·경계의 DDD 영향을
  판정했습니다. 영향이 없으면 actual source에 근거한 `해당 없음`으로 닫고, 영향이 있거나
  새 module이면 [110 설계 워크플로우의 DDD 판정 gate](110-design-workflow.md#설계-입력)를
  통과했습니다.
- TDD 적용 여부와 예외 시 대체 검증을 정했습니다.
- 변경 파일의 주석, 환경변수와 영구 문서 영향을 확인했습니다.
- 작업 실행 workflow가 확정한 dirty 경계와 이번 변경이 분리돼 있습니다.

## 구현 원칙

- 기존 패턴과 책임 경계를 우선합니다.
- 추상화는 실제 복잡도나 중복을 줄일 때만 추가합니다.
- 관련 없는 refactor, 이동과 포맷 변경을 포함하지 않습니다.
- 보안, 인증, 데이터 변경은 완화 기본값보다 명시적 실패를 우선합니다.
- 임시 구조가 필요하면 범위, 제거 조건과 확정 여부를 먼저 정합니다.

## 표면별 기준

| 표면 | 기준 |
| --- | --- |
| 설계, API, DB, transaction, security | `110-design-workflow.md` |
| 기능·버그·도메인 변경 | `210-tdd-workflow.md`, `230-test-writing-guide.md` |
| 코드·설정·SQL 주석 | `220-code-commenting-guideline.md` |
| 저장소 실행 script | 도입·lifecycle은 `130-tool-adoption-workflow.md`, 구현은 `240-script-engineering-guideline.md` |
| 경계와 mapping 이름 | `../architecture/220-boundary-and-mapping-rules.md` |
| 로그와 진단 | `../tech-stack/280-logging.md` |
| 환경변수와 Spring 설정 | `../development/100-local-development.md`, `../tech-stack/210-spring-boot.md` |
| 품질 도구와 최종 Gradle 검증 | `../tech-stack/310-code-quality-tools.md` |
| 문서와 영구 SOT | `../100-documentation-guide.md`, `150-documentation-workflow.md` |

## 검증

- 가장 좁고 빠른 test에서 변경 동작을 먼저 확인합니다.
- API, Security, persistence와 migration은 실제 위험 경계에 맞는 slice 또는 integration test로 보강합니다.
- Java/Gradle 최종 검증은 `../tech-stack/310-code-quality-tools.md`의 명령 계약을 사용합니다.
- 코드 작업은 final exact SHA의 required CI와 Sonar 완료 점검을 terminal gate로
  유지하고, [코드 품질 도구](../tech-stack/310-code-quality-tools.md)의 판정 계약과
  [커밋 워크플로우](300-commit-workflow.md)의 실행 순서를 그대로 적용합니다. push
  권한이 없으면 local 검증 결과와 전체 코드 작업의 미완료를 구분합니다.
- 실행하지 못한 검증은 이유와 남은 위험을 구분합니다.
- ad hoc 명령 실패를 표준 최종 명령으로 재현하지 않고 프로젝트 실패로 단정하지 않습니다.

동결 후보의 final discovery·closure 횟수, 실패 뒤 상태 전이와 전체 완료 판정은 작업 실행
workflow가 소유합니다. 이 문서는 그 판정에 필요한 코드·test·품질 gate 증거만 제공합니다.

## 문서와 SOT

- work item 결과와 실제 구현 상태를 일치시킵니다.
- 새 장기 정책, 구조와 운영 절차는 책임 SOT에 승격합니다.
- design에는 구현 결과, commit, dirty 상태와 임시 경로를 남기지 않습니다.
- 환경변수 계약 변경은 `.env.example`과 로컬 개발 문서를 함께 확인합니다.

## 완료 확인

- 확정된 수용 기준을 모두 충족했습니다.
- TDD 적용 또는 예외 근거와 대체 검증이 명확합니다.
- 관련 test와 적용되는 Java/Gradle 최종 검증을 실행했습니다.
- 변경 Java, 설정과 SQL의 주석 필요성을 실제 파일에서 검토했습니다.
- work item, SOT와 운영 문서를 실제 결과에 맞췄습니다.
- 모든 변경 module의 DDD 영향 판정 또는 `해당 없음` 근거가 실제 package·모델·의존
  경계와 일치합니다.
- 코드 작업이면 exact SHA의 required CI 성공과 Sonar의 revision 일치·Quality Gate
  `OK`·overall `open_issues` 0건을 확인했습니다. push 권한이 없거나 이 gate를 확인하지
  못했으면 local 구현 결과만 닫고 전체 코드 작업은 미완료로 유지했습니다.

위 code completion gate의 적용 가능성과 전체 결과 상태·외부 책임·Git 상태는 작업 실행
workflow와 완료 보고 workflow가 판정·투영합니다.

## 관련 문서

- **공통 lifecycle**: [작업 실행 워크플로우](100-work-execution-workflow.md)
- **선행 결정 절차**: [설계 워크플로우](110-design-workflow.md)
- **구현·test 순서**: [TDD 워크플로우](210-tdd-workflow.md)
- **주석 기준**: [코드와 설정 주석 작성 가이드](220-code-commenting-guideline.md)
- **test 구조 기준**: [테스트 작성 가이드](230-test-writing-guide.md)
- **script 설계·구현·검증**: [스크립트 엔지니어링 가이드](240-script-engineering-guideline.md)
- **Git 완료 경로**: [커밋 워크플로우](300-commit-workflow.md)
