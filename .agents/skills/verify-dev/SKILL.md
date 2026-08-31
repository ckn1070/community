---
name: verify-dev
description: "완성 후보인 개발 결과를 명시 작업 지시, Plan mode 결과, work item 또는 개발 계획의 최초 목적부터 독립 검증할 때 사용한다. 계획 수립·구현 자체, 단순 코드 설명이나 Codex 오류 회고에는 사용하지 않는다."
---

# 개발 결과 검증

완료 주장이나 구현 형태에 고정되지 않고 최초 목적부터 source·실행 증거·문서를 독립
대조하며 artifact를 직접 수정하지 않는다. 후보 동결, 공통 audit phase·closure와 상태
전이는 [작업 실행 워크플로우](../../../docs/workflow/100-work-execution-workflow.md)가 소유한다.

## 1. 기준과 snapshot

`AGENTS.md`, 관련 workflow·router, 원래 지시, 확정 계획·work item·design과 현재 source를 읽는다. 구현에 동반된 문서·SOT·router가 있으면 `docs/100-documentation-guide.md`와 `docs/workflow/150-documentation-workflow.md`도 읽는다. `git status --short --branch`, 비교 기준과 검증 환경을 확인한다. 작업 시작 전에 존재한 negative contract test·validator·정책 검사와 그 authoritative owner를 baseline snapshot에 포함한다. 주석 변경을 검증하면 작업 시작 baseline과 최종 후보를 모두 snapshot에 포함한다. 검증 중 범위 안 artifact가 바뀌면 이전 동적 증거를 재사용하지 않는다.

감사 입력이 계속 바뀌거나 열린 계획 결정 때문에 기대 계약을 확정할 수 없으면 현재
snapshot에서 판정할 수 없는 범위와 영향을 명시한다. verifier 안에서 설계를 만들거나
snapshot 변경을 추적하는 별도 lifecycle을 열지 않는다.

## 2. 기대 계약과 추적표

구현을 보기 전에 목적, 명시 요구, 파생 불변식, 포함·제외 범위와 정상·실패·경계 수용 기준을 복원한다.

| 요구·불변식 | 시나리오 | 호출 경로 | 구현 | test·실행 증거 | 주석 | 문서·SOT | 상태 |
| --- | --- | --- | --- | --- | --- | --- | --- |

정방향으로 모든 요구를 추적하고 역방향으로 모든 변경 artifact가 정당한 요구·결정에 연결되는지 확인한다.

동일 역할·동일 표면에 둘 이상의 artifact·이름·구조·기술 경로가 있으면 후보를 보기 전에
변경 전 SOT와 실제 source에서 전체 sibling inventory와 공통 선택 축을 독립 복원합니다.
모든 sibling이 같은 축을 소비하는지, 다른 축은 mutation 전 독립 source가 요구한 필수 차이와
영향·재검토 조건으로 닫혔는지 확인합니다. 후보가 함께 만든 test·work item·문서와 각 artifact의
국소 가독성은 선택 admission이나 예외 근거가 아닙니다. 역할이 다른 객체, sibling 없는 국소
선택과 source가 하나로 고정한 기계적 변환에는 동일 어휘·구조를 강제하지 않습니다.

Baseline과 후보 사이에서 변경·삭제된 test·validator·정책 검사는 assertion이 강화·보존·
완화됐는지 분류합니다. 기존 실패 조건을 허용하도록 assertion을 삭제·축소하거나 예외를
추가한 변경은, 그 불변식을 대체하는 더 최신 사용자 결정 또는 독립 authoritative source가
없으면 실패입니다. 구현과 함께 바꾼 test·문서의 최종 self-consistency는 이 source를
대신하지 않습니다.

### 후보 효과 입장 gate

Dependency closure와 보안·복구 상세 감사를 시작하기 전에 후보가 추가·변경한 관찰 효과를
mutation 전의 원래 결과·제약·oracle과 독립 authoritative source로 역추적한다. 같은 작업에서
후보가 만든 test·README·SOT·work item은 구현 evidence이지 요구 source가 아니다.

연결되지 않은 exact 감사·정규화·hardening·자동화가 있으면 그 효과의 내부 결함을 상세히
열거해 더 완성하도록 만들지 않는다. `미승인 후보 효과` 하나로 묶어 실제 효과·부재한
admission source·원래 결과에 미치는 영향·효과 전체 제거라는 닫는 조건만 반환하고, 승인된
효과만 아래 dependency closure와 발견 포화로 보낸다. 반대로 사용자가 exact hardening을
명시했거나 독립 source가 원래 결과에 필수로 요구하면 이를 미승인 효과로 제거하지 않고
정상적으로 끝까지 감사한다.

## 3. dependency closure

diff와 계획의 파일 목록을 넘어서 다음을 추적한다.

- entry point부터 domain·application·port·adapter·persistence와 side effect
- validation, 권한 거부, error mapping과 wiring
- transaction, rollback, 동시성, retry·timeout과 부분 실패
- schema·migration·호환성·보존
- 설정·build·packaging·profile
- 로그·health·배포·rollback·runbook
- 관련 unit·slice·integration·architecture test와 fixture
- 현재 기준을 소유하는 문서

외부·내부 실행 도구가 범위에 있으면 실제 executable과 caller에서 독립적으로 차단하는
관찰 책임을 복원한다. 파일·toolchain·도구군의 통합 label을 coverage로 사용하지 않고,
각 책임을 [도구 도입과 생애주기 워크플로우](../../../docs/workflow/130-tool-adoption-workflow.md)의
도입·존치 결정으로 역추적한다.

## 4. 발견 포화

첫 결함에서 수정으로 넘어가지 않고 다음 축을 `충족`, `미충족`, `미검증`, `해당 없음`으로 닫는다.

- 목적·범위·계획 충분성
- 동작·wiring·외부 계약
- 구조·모듈·data·transaction
- 보안·개인정보·안정성·복구
- 성능·자원·운영 가능성
- 의존성·도구·code 품질
- test의 증명력·중복·과잉·결정성
- 주석의 필요성·정확성·과장
- 문서·SOT·runbook·router
- 표준 검증, dirty 범위와 Git 상태

각 발견을 기대 → 실제 → 재현 근거 → 영향 → 닫는 조건으로 연결한다. 선택적 개선을 필수 결함으로 가장하지 않는다.

## 5. 동적 검증

현재 snapshot에서 가장 좁은 test, 필요한 통합·보안·data 검증, 표준 build·quality, source·문서 전체 재읽기, `git diff --check`와 Git 상태를 순서대로 확인한다. test 존재나 과거 로그를 현재 성공으로 사용하지 않는다.

도구의 self-test는 구현 동작의 증거로만 사용한다. 도구의 필요성·독립 oracle·순효과
또는 형제 책임의 존치를 증명한 것으로 확대하지 않으며, 타당한 표준 검사와 같은
executable에 있다는 이유로 custom parser·allow-list·문자열 assertion을 통과시키지 않는다.

주석과 문서는 verifier 통과가 아니라 실제 source와 의미를 대조한다. 독자 질문·actual source→owner의 누락과 owner→독자 필요·source의 과잉을 양방향으로 확인하고, 실행 순서·분기·호출·값 전달·책임 관계를 문서만으로 하나의 의미로 복원할 수 있으며 구현과 일치하는지 판정한다.

주석이 수정·삭제·병합된 작업은 현재 source에서 최종 후보의 설명 충분성을 찾는 감사와,
작업 시작 baseline의 각 주석 정보가 최종 구조·같은 의미 범위의 주석·authoritative
source에 근거한 교정으로 남았는지 찾는 감사를 별도로 수행한다. 검토자는 작성자의
분류나 기대 결론을 입력으로 사용하지 않고 baseline에서 actor, artifact, 적용 범위와
lifecycle, 행위·결과와 관계·조건을 독립적으로 복원한다. local syntax와 사용 구조만으로
복원되지 않는 외부 계약은 공식 source를 확인하며, 대응되지 않은 정보가 하나라도 있으면
실패로 판정한다. comment-only diff, syntax 검사와 동작 불변은 의미 보존의 증거가 아니다.

개발 결과의 일반 동반 문서는 이 검증이 primary owner이므로 별도 감사를 중복하지 않습니다.
다만 canonical SOT·workflow·runbook의 중대한 변경이나 다문서 구조 개편은 상위 문서 계약에
따라 작성자와 분리된 `$verify-documentation final`까지 통과해야 합니다. 이 검증의 문서 축,
generic final이나 일부 finding closure를 그 specialist verdict로 바꾸어 쓰지 않습니다.
custom audit JSON과 self-consistency verifier를 만들지 않는다.

## 6. 판정 결과

- **통과**: 모든 필수 요구·축과 동적 검증이 충족됐습니다.
- **실패**: 하나 이상의 필수 요구나 축이 미충족입니다.
- **판정 불가**: 필수 근거·환경·결정이 없어 미검증입니다.

발견은 모든 검토 축을 닫은 뒤 `기대 → 실제 → 재현 근거 → 영향 → 닫는 조건`으로 한
묶음에 제공한다. 국소 구현 결함인지 제품·API·data·security·운영·module 결정이 필요한
계획 결함인지 분류하되 verifier가 수정이나 결정을 수행하지 않는다.

이 판정을 작업 실행 workflow에 인계한다. `$develop`·`$dev-plan` routing, 보완 가능 여부,
후보 무효화와 closure 횟수는 작업 실행 workflow가 판정하며 이 skill은 그 lifecycle을
다시 정의하지 않는다.

[완료 및 작업 보고 워크플로우](../../../docs/workflow/140-completion-reporting-workflow.md)의
검증 경로에는 판정, 요구별 필수 발견·공백, 실제 실행 검증 범위와 snapshot의 문서·SOT·
Git 사실만 제공한다.
