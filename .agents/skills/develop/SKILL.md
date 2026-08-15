---
name: develop
description: "사용자가 기능, 버그 수정, 리팩터링, 아키텍처, 데이터베이스, 보안, 인프라, 운영 또는 외부 연동의 실제 변경·구축을 요청했거나, 직접 선행 문맥에서 확정된 이러한 개발 변경을 짧은 후속 표현으로 승인·계속하라고 했을 때 사용한다. 계획, 문서 자체의 변경, Git 마무리, 읽기 전용 검토·설명 또는 Codex 오류 회고가 결과인 경우에는 사용하지 않는다."
---

# 개발

확정된 결과를 production code, test, runtime 설정, data와 관련 문서의 실제 변경으로
연결하는 개발 orchestration을 수행한다. 요청·권한, 공통 결과 단위와 dependency 상태,
병렬화, 차단, final discovery·closure와 전체 완료 판정은
[작업 실행 워크플로우](../../../docs/workflow/100-work-execution-workflow.md)가 소유한다.
이 skill은 그 계약을 개발 source와 전문 workflow에 routing하며 다시 정의하지 않는다.

## 1. 실행 source와 개발 경로

실제 디스크에서 다음을 읽는다.

1. 저장소 `AGENTS.md`, 문서 router와 작업 실행 workflow
2. 현재 work item, 같은 번호의 `-design`과 확정 결정
3. 작업 성격에 맞는 설계·도구·개발·TDD·test·주석·품질 workflow
4. 현재 사실을 소유하는 domain·architecture·project·tech-stack·development·operations SOT

오래된 skill 경로보다 최신 router와 canonical owner를 우선한다. 작업 실행 workflow가
확정한 결과·범위·제약·권한·dirty 경계를 입력으로 받고 다음 개발 정보를 연결한다.

`진행해`, `계속해`, 짧은 동의처럼 직접 선행 문맥의 concrete 개발 변경을 승인하는 후속
표현도 이 skill의 실행 진입점이다. 이때 작업 실행 workflow의 Bootstrap과 실행 라운드로
다시 들어가 동결된 상위 결과와 남은 단위를 복원한다. 마지막으로 논의한 선택이나 방금
편집한 파일만 전체 결과로 축소하지 않는다. 문서·Git 등 다른 결과의 후속 승인은 해당
전문 owner로 돌아간다.

| 정보 | 개발 source |
| --- | --- |
| 목표와 구현 계약 | 확정된 work item·`-design`과 사용자 고정 조건 |
| 현재 정책·구조 | 책임 SOT |
| 현재 구현 | production source, 설정, schema, migration과 실행 결과 |
| 검증 계약 | test source와 development·tech-stack workflow |
| 운영 실행 | runbook. 절차 존재를 실행 완료 증거로 사용하지 않음 |

`Proposed`, backlog, 초안과 추천은 구현 계약으로 사용하지 않는다. `Planned`나
`In Progress`도 상태 문자열만 믿지 않고 현재 source와 열린 결정의 영향을 대조한다.
Plan mode 결과는 조사 입력일 뿐 현재 구현이나 영구 SOT가 아니다.

### 개발 라운드 호출 gate

첫 worker 위임·mutation·review 전에 작업 실행 workflow에서 다음 라운드 계약을 받아
세션 원장에 동결한다.

```text
상위 결과 → 이번 라운드의 concrete 결과·완료 oracle
→ 활성 결정과 source → 포함·제외·복귀점
→ 예정 artifact별 관찰 효과·실행 방식·blocking 여부·전문 owner 처분
→ 실제 control 기준선 R·요청 generation G·target candidate C·호출 전 상태 S0
→ 앞 phase의 전문 결과·candidate 제약·필수 후속 gate P
→ writer 단위·exact write set → 검토 phase와 현재 상태
```

예정 artifact는 각각 다음 세 가지를 따로 닫는다.

```text
어느 동결 결과·수용 기준을 직접 만드는가
→ 그 결과를 위해 어떤 보장은 필수인가
→ 그 보장을 이 exact artifact·topology로 만들어야만 하는가
```

현재 선택한 경로가 artifact를 caller로 참조한다는 사실은 첫 번째나 두 번째를 증명할 수
있지만 세 번째를 증명하지 않는다. 현재 구조를 대상으로 만든 test도 그 구조가 정확히
연결됨을 증명할 뿐 구조 자체의 필수성을 증명하지 않는다. 세 번째가 현재 source와 동결
결정에서 유일하게 닫히지 않으면 writer가 구조를 선택하지 않고 설계·계획 owner로 돌린다.

원래 요구와 authoritative source로 이 계약을 유한하게 채우지 못하면 개발을 시작하지 않고
Bootstrap 또는 실제 결정 owner로 돌아간다. 라운드 계약의 field 의미, 새 발견·차단 판정,
후보 상태와 discovery·correction·closure lifecycle은 작업 실행 workflow만 소유하며 이
skill이 다시 정하지 않는다.

라운드 중 처음 드러난 필수 보장이 동결 artifact만으로 만들어지지 않으면 해당 단위를 더
구현하지 않고 설계 단계로 돌린다. 이때 새로 필요한 사용자 소유 결정은 항목별로 즉시 묻지
않고, 남은 결과를 바꾸는 전체 결정·대안·추천·영향을 작업 실행 workflow의 다음 결정
라운드에 한 번에 넘긴다. Source로 유일하게 닫히는 불가피한 선행 관계는 사용자 결정으로
바꾸지 않되 선택 이유와 artifact 영향을 라운드 계약에 남긴다.

Artifact의 path·확장자나 `test`·`spec` 같은 이름으로 작업 실행 workflow가 판정한 전문
owner를 다시 정하지 않는다. 특히 독립 실행되어 source나 설정을 해석하고 진행을 차단하는
artifact는 test directory에 있어도 도구 처분이 없는 write set으로 writer에게 넘기지 않는다.

현재 Agent와 각 actor는 라운드 계약에 적힌 canonical control source를 실제로 읽고 그
immutable identity를 반환해야 한다. Main이나 다른 worktree에 새 control commit이 있다는
사실만으로 현재 actor가 그 revision을 실행했다고 판정하지 않는다. Target ref를 바꿀 권한이
없으면 exact control source를 격리 입력으로 전달하며, 최신 규칙을 읽히기 위해 feature ref를
임의 merge·reset하지 않는다.

병렬 worker에게는 같은 상위 계약, `writer` 역할, 독립된 write set과 local oracle을
전달한다. Writer는 범위·결정을 다시 정하거나 후속 개선을 현재 요구로 받아들이지 않는다.
Reviewer는 모든 writer가 멈추고 snapshot이 동결되기 전에는 시작하지 않으며 파일을
수정하지 않는다. 공유 계약의 producer·consumer, active writer와 그 후보를 다시 열 수
있는 review는 병렬 작업으로 나누지 않는다.

Writer는 종료할 때 변경한 각 관찰 효과를 mutation 전 라운드 계약의 사용자 결과,
authoritative source 또는 불가피한 선행 관계에 역추적한다. 같은 작업에서 만든 test·README·
SOT·work item은 구현 evidence이지 새 효과의 admission source가 아니다. 이 연결이 없는
exact 감사·정규화·hardening·자동화는 reviewer에게 완성도를 묻기 전에 후보에서 제거한다.
제거에 사용자 소유 결정이 필요하면 구현하지 않고 원래 결과와 분리해 다음 결정 라운드로
반환한다.

동결 reviewer의 반환도 새 요구나 mutation 명령으로 소비하지 않고 원래 라운드 계약과 함께
작업 실행 workflow에 넘긴다. 그 workflow가 판정한 현재 결과·제외·상태와 다음 phase만
다시 같은 writer 계약으로 운반한다. 따라서 develop adapter의 성공은 더 많은 finding을
수집하는 것이 아니라 상위 계약이 writer와 reviewer 경계에서 바뀌지 않았다는 것이다.

Reviewer 입력에는 입장 gate를 통과한 관찰 효과와 그 효과별 독립 source를 포함한다. 후보가
만든 계약은 구현·test·문서 정합성의 검토 대상일 뿐 요구 source가 아니며, reviewer가
입장되지 않은 효과를 발견하면 그 내부 결함을 열거해 보강하지 않고 하나의 미승인 효과로
반환한다.

설계·문서 구조 proposal·도구 처분·전문 검증이 owner·topology·write set이나 필수 후속
gate를 확정하면 그 결과 identity와 제약을 `P`로 writer·reviewer·Git 인계까지 보존한다.
Writer 종료 시 각 전문 결과가 candidate 어디에 반영됐는지 대조하고, 누락·충돌 또는
필수 specialist phase의 generic review 대체가 있으면 candidate와 이후 evidence를
무효화해 그 전문 phase로 돌아간다. Reviewer를 호출했다는 사실이나 link·test의 내부
일치는 전문 결과를 소비했다는 증거가 아니다.

새 사용자 메시지가 현재 라운드의 주체·범위·권한을 이의 제기하거나 라운드를 중단·대체하면,
현재 Agent는 다음 답변·도구·review·mutation 전에 해당 라운드의 live writer와 reviewer를
실제 orchestration 상태에서 열거한다. 영향받은 actor를 중단하고 모두 terminal인 것을 확인한 뒤
Git ref·index·worktree와 관련 외부 상태를 다시 읽는다. 중단 요청·응답을 artifact 무변경의
증거로 쓰지 않고, 권한 무효화 뒤 도착한 이전 actor의 결과는 해당 후보의 검증·Git 입력에서
제외한다.

Reviewer의 partial message는 미분류 evidence로만 보존한다. 예정한 discovery reviewer가 모두 terminal이고
각 reviewer가 같은 후보 식별자를 읽었음을 확인한 뒤에만 complete finding set을 한 번 동결해
작업 실행 workflow로 반환한다. Partial finding을 writer에게 전달하거나 분류·수정을 시작하지
않으며, 대기 중 후보·ref·index·worktree가 바뀌면 기존 review를 새 후보에 승계하지 않는다.

각 actor 결과에는 입력받은 `R/G/C/S0/P`를 귀속한다. 결과 소비 직전에 `HEAD`·index·tracked·
untracked content와 관련 외부 상태를 다시 읽어 `S1`을 만들고, identity가 다르거나 설명되지
않은 drift가 있으면 그 결과와 candidate를 무효화한다. Known actor가 모두 terminal이라는
사실은 artifact 불변 증거가 아니며, late result와 이전 generation 결과를 현재 review·Git
입력으로 사용하지 않는다.

Writer 종료 뒤 동결하는 `C`에는 원래 기대 효과와 candidate 밖 task-owned residual을 함께
연결한다. 작업 실행 workflow의 `E/K/X/D` closure가 성립하지 않으면 commit 수나 clean status와
무관하게 개발 결과를 Git phase로 넘기지 않는다.

## 2. 개발 dependency closure

각 수용 기준에서 실제로 영향을 받는 closure를 추적한다.

1. entry point에서 domain·application·port·adapter·persistence와 외부 side effect까지
   정상 호출 흐름을 따른다.
2. validation, 권한 거부, error mapping, transaction·rollback, 동시성, idempotency,
   retry·timeout과 부분 실패 경로를 확인한다.
3. schema·migration·호환성·보존, 설정·profile·build·packaging을 확인한다.
4. unit·slice·integration·architecture·migration test와 fixture가 고정한 계약을 읽는다.
5. log·health·배포·rollback·runbook과 현재 기준 문서의 영향을 확인한다.
6. caller·consumer와 관련 문서를 따라 새 경계가 나오면 필요한 범위만 확장한다.

검색 결과, 파일명, 주석, test 이름, stub이나 fixture만으로 동작을 추정하지 않는다.
편집할 파일과 의미를 결정하는 인접 source는 전체 문맥을 읽고 현재 동작·목표 동작·간극을
authoritative evidence에 연결한다.

## 3. 전문 결정 owner로 routing하기

public API, DB schema·migration, 인증·인가, transaction, 도메인 모델, module·package·
port/adapter, 공용 기반 구조와 운영·복구 비용이 큰 선택은
[설계 워크플로우](../../../docs/workflow/110-design-workflow.md)에 맡긴다. 현재 source와 확정
결정만으로 닫히지 않는 설계 판단은 영향받는 계획 표면을 `$dev-plan`에 인계한다.

외부 executable·Action·SaaS·package manager뿐 아니라 repository script·verifier·hook·
installer·dispatcher의 도입·변경·존치·제거는
[도구 도입 워크플로우](../../../docs/workflow/130-tool-adoption-workflow.md)를 적용한다.
복합 executable은 독립적으로 차단하는 관찰 책임마다 필요성·oracle·caller·제거 가능성을
판정하고 형제 책임이나 self-test의 근거를 빌리지 않는다.

확정 설계와 현재 source가 충돌하면 국소 workaround로 숨기지 않고 충돌의 owner와 영향
범위를 반환한다. 어느 결과 단위를 재개·보류·무효화할지는 작업 실행 workflow가 판정한다.

## 4. 개발 workflow를 실행하기

[개발 작업 워크플로우](../../../docs/workflow/200-development-workflow.md)를 상위 절차로 사용한다.
기능, 버그, 도메인, API 동작과 회귀 가능 refactor는
[TDD 워크플로우](../../../docs/workflow/210-tdd-workflow.md)와
[테스트 작성 가이드](../../../docs/workflow/230-test-writing-guide.md)를 적용한다.

- 가장 작은 관찰 가능 동작과 위험 경계의 실패 test로 Red 이유를 고정한다.
- 통과시키는 최소 production 변경 뒤 관련 test를 Green으로 만든다.
- Green에서만 중복과 책임 문제를 refactor하고 회귀 범위를 확인한다.
- 버그는 수정 전 재현 test로 실제 실패 원인을 고정한다.
- TDD가 부적절하면 구현 전에 문서·설정·build·spike·외부 환경에 맞는 대체 검증을 정한다.
- 기존 naming과 dependency 방향을 따르고 관련 없는 refactor·이동·포맷을 섞지 않는다.

첫 구현 변경 전에 기존 work item이 있으면 `$documentation`으로 실제 실행 상태와 계획
inventory를 동기화한다. 완료하지 않은 구현을 결과로 쓰거나 일회성 실행 기록을 위해 새
문서를 만들지 않는다.

## 5. 주석과 문서를 구현 산출물로 연결하기

변경 전후에 [코드와 설정 주석 가이드](../../../docs/workflow/220-code-commenting-guideline.md)를
적용한다. 변경한 production code, test, 설정, build, script, workflow와 SQL뿐 아니라 이번
변경으로 계약 설명이 달라진 caller·port·adapter·helper를 검토한다.

- 비자명한 정책·계약·경계·위험과 변경 조건을 의미 범위의 시작 가까이에 설명한다.
- 이름·타입·구조로 충분한 번역 주석과 실제 보장보다 넓은 설명을 추가하지 않는다.
- 현재 source의 설명 충분성과 작업 시작 baseline 주석의 정보 보존을 별도 축으로 대조한다.
- 주석의 필요성·위치·정확성을 regex나 작성자의 자기 선언에 맡기지 않는다.

문서 변경에는 [`$documentation`](../documentation/SKILL.md)을 사용한다.

- work item 결과와 상태를 실제 구현에 맞추고 `-design`에는 확정 결정만 보존한다.
- 책임 SOT에는 source와 검증이 뒷받침하는 현재 정책·구조만 승격한다.
- runbook에는 확정된 시스템의 실행·검증·복구 절차만 둔다.
- 대체된 결론·경로·용어를 관련 문서에서 제거하고 router·링크를 동기화한다.
- 진행률, dirty 상태, 반복 명령과 commit 기록은 영구 문서에 넣지 않는다.

## 6. 개발 검증 evidence를 만들기

작업 위험에 맞게 다음 evidence를 만든다.

1. 변경 지점의 가장 좁은 test
2. API·security·persistence·migration·transaction·외부 연동의 slice·integration test
3. architecture·formatting·static analysis와 저장소 표준 build·quality gate
4. 변경 path의 주석 충분성·정보 보존과 전체 파일 수동 의미 대조
5. 변경 문서의 source·owner·링크·현재 명령 대조
6. 전체 diff, `git diff --check`와 Git 상태
7. 수용 기준별 구현·test·문서 evidence

Java/Gradle 최종 명령, Docker preflight와 Sonar 기술 판정은
[코드 품질 도구](../../../docs/tech-stack/310-code-quality-tools.md)를 그대로 사용한다.
코드 작업은 [커밋 워크플로우](../../../docs/workflow/300-commit-workflow.md)가 정한 순서로
final exact SHA의 required CI 성공 뒤 Sonar 완료 점검을 수행하고, revision 일치·Quality
Gate `OK`·overall `open_issues` 0건 계약을 바꾸거나 축약하지 않는다. push 권한이 없거나
실행할 수 없는 gate는 성공으로 추정하지 않고 local 결과와 전체 미완료를 구분한다.

구현과 local evidence가 준비된 후보는 [`$verify-dev`](../verify-dev/SKILL.md)에 원래 목적,
확정 계획, 작업 시작 baseline, 현재 source와 실행 증거를 제공해 독립 감사한다. 후보 동결,
final discovery·closure 횟수, 발견 보완 뒤 상태 전이와 전체 완료 판정은 작업 실행
workflow가 소유한다.

## 7. 결과 인계

개발 결과에는 다음을 연결한다.

- 수용 기준을 구현한 code·test·설정·data 변경
- 설계·도구·TDD·주석·문서 owner가 제공한 판정과 evidence
- 표준 local quality 결과와 적용되는 exact SHA CI·Sonar 결과
- `$verify-dev`의 독립 감사 판정
- 동기화한 work item·SOT·runbook과 Git 실제 상태

전체 상태와 남은 책임의 채팅 projection은
[완료 및 작업 보고 워크플로우](../../../docs/workflow/140-completion-reporting-workflow.md)에
맡긴다. Git 상태 변경은 작업 실행 workflow가 확정한 권한과 Git workflow에만 따른다.
