---
name: verify-plan
description: "완성 후보인 개발 계획을 구현 전에 최초 사용자 목적, 확정 결정과 관련 코드·문서·설정·데이터·운영 source부터 독립 검증할 때 사용한다. 계획 수립·수정 자체, 구현 결과 검증이나 반복적인 아이디어 탐색에는 사용하지 않는다."
---

# 계획 검증

완성 후보 계획을 구현 형태가 아니라 최초 목적에서 다시 평가한다. 계획을 직접 수정하지
않고 검토 축 전체의 발견을 포화시킨 뒤 `통과`, `실패`, `판정 불가`를 결정한다. 후보
동결, 공통 audit phase·closure와 상태 전이는
[작업 실행 워크플로우](../../../docs/workflow/100-work-execution-workflow.md)가 소유한다.

## 1. 입력과 snapshot

`AGENTS.md`, 관련 workflow·router, 최초 요청과 확정 변경, work item·design, 관련 source와
[작업 문서 생명주기](../../../docs/work-items/100-work-item-lifecycle.md#계획-단계와-최소-artifact)를
읽는다. 계획이 문서·SOT·router를 만들거나 바꾸면 `docs/100-documentation-guide.md`와
`docs/workflow/150-documentation-workflow.md`도 읽는다. 최초 목적, 사용자 선택과 단계 확정을
복원할 authoritative input이 없으면 계획 문구로 추측하지 않고 판정 불가로 끝낸다. 후보
계획, 후보가 갱신한 SOT·router와 이전 verifier 결론은 사용자 선택이나 단계 확정 source가
아니다.

`git status --short --branch`, 계획 문서와 핵심 source 상태를 확인한다. 검증 중 계획이나 판단 근거가 바뀌면 기존 결론을 재사용하지 않는다.

후보의 `상태`와 `계획 단계`를 먼저 고정하고 lifecycle에서 허용된 artifact·section과 현재
closure만 감사 범위로 연다. 선행 단계가 닫히기 전에 생성된 dependent 다음 단계 문서·
section·후보 상세는 내용 품질과 무관한 필수 결함이다. 다음 단계에서만 필요한 검토 축은
현재 후보의 누락으로 판정하지 않고 `해당 없음`으로 닫는다.

### 실행 라운드 admission

Writer가 만든 후보의 discovery·closure로 위임받은 검증은 작업 실행 workflow의 review
state를 content audit보다 먼저 소비한다. 호출 입력에는 operation generation, round id,
`discovery|closure`, candidate identity·digest, 예정 reviewer identity, active writer 수,
앞선 reviewer terminal 상태, correction 처분과 이전 closure verdict가 있어야 한다. 현재
verifier는 자신의 identity와 실제 thread·handoff에서 이 값을 대조하고, 누락·불일치·stale을
호출자의 설명으로 보충하지 않는다.

- Closure verifier는 discovery verifier와 달라도 되지만 correction 뒤 새 candidate를 동결할
  때 예정 reviewer로 먼저 고정돼야 한다. Closure 시작 뒤 verifier 교체, `final-v2` 같은 이름
  변경이나 새 digest만으로 같은 round를 다시 열지 않는다.
- Active writer가 남았거나 review 중 candidate가 바뀌었으면 content finding을 새 후보에
  재사용하지 않고 `Invalidated`를 반환한다.
- Closure가 이미 실패했거나 수행됐다면 추가 축을 감사하거나 새 correction finding을 만들지
  않고 `Invalidated`와 원인 phase 인계만 반환한다. 새 round는 원인 재계획과 새 round id의
  `Frozen` 증거가 있을 때만 별도 discovery로 시작한다.
- 위 상태를 직접 대조할 trace가 없으면 `판정 불가`로 끝내며 후보 품질의 통과·실패와 섞지
  않는다.

사용자가 계획의 독립 read-only 감사 자체를 직접 요청했고 active writer round가 없으면
`standalone`으로 한 번 감사할 수 있다. 이 verdict는 계획 수정·correction·closure 횟수나
실행 라우팅 권한을 만들지 않는다.

## 2. 최초 목적 계약

계획 상세를 평가하기 전에 다음을 세션의 coverage map으로 만든다.

| 목적·제약 | 수용 기준 | 설계 결정·값 | 권위 source와 상태 | 구현 작업 | 검증 | 문서·SOT |
| --- | --- | --- | --- | --- | --- | --- |

정방향으로 모든 목적이 결정·작업·검증·문서에 연결되는지, 역방향으로 모든 추가 작업과 도구가 목적에 정당화되는지 확인한다.
결과 정의·해결 설계 단계에서는 아직 허용되지 않은 구현 작업·검증 열을 `해당 없음`으로 두며,
후보가 그 값을 미리 채웠으면 추적성 근거가 아니라 stage-ahead 결함으로 분류한다.

결과를 바꾸는 각 결정은 후보를 읽기 전에 원문과 독립 source에서 다음 중 하나로 분류한다.

- 사용자가 exact 차원과 값을 긍정적으로 선택함
- 외부 공식 계약·현재 저장소 source·사용자 상위 결정이 허용 값을 하나로 제한함
- source·대안·trade-off에 근거해 Agent가 선택한 기술값
- 프로젝트 외부 판단이 필요한 열린 사용자 결정
- source가 없어 권위 분류 불가

계획 작성, 상세화, 검증, commit·push 요청은 값 선택으로 해석하지 않는다. 부분 문자열이나
인접 승인도 다른 결정에 전파하지 않는다. 사용자가 exact 추천 묶음을 승인한 경우에는 그
묶음만 선택됨으로 기록한다.

`exact 값·결정 블록 → 사용자 값 확정 source`와 각 계획 단계의 `현재 단계 결과 후보 전체의
exact 범위 → 사용자 단계 확정 source → 다음 단계 전환`을 권위 범위별로 분리해 복원하되
서로 다른 메시지를 요구하지 않는다. 사용자 선택, Agent 기술 선택, source 제약과 verifier
판정은 단계 확정을 대신하지 않는다. 특정 결정에 직접 붙은 선택만 있으면 해당 값 밖으로
전파하지 않고, 같은 generation에 artifact 반영 요청이 없으면 writer mutation도 허용하지
않는다. 반대로 마지막 열린 값의 채택·반영을 긍정했고 현재 후보가 앞서 제시한
결과와 닫힌 source에서 하나로 복원되며 새 사용자 판단이 생기지 않았다면 같은 원문이 단계
확정도 소유한다. 이 조건이 애매하면 판정 불가 또는 열린 결정으로 반환하고, 모두 닫혔는데
추가 literal 확정 응답을 요구한 후보는 false-block 필수 결함이다.

## 3. dependency closure와 검토 축

계획의 파일 목록에 한정하지 않고 caller, 외부 계약, data, build, deployment, operations, test와 책임 문서를 추적한다. 다음 축을 `충족`, `미충족`, `미검증`, `해당 없음`으로 닫는다.

- 최초 목적·범위와 양방향 추적성
- 결정별 추천·선택 주체와 값, 확정 주체·범위·source 및 관련 문서 전체의 일치
- 정상·실패·경계 동작과 기존 회귀
- 구조·모듈·외부 계약
- data·transaction·동시성
- 보안·개인정보·신뢰 경계
- 안정성·복구·성능·자원
- 운영·rollout·rollback
- 기술·도구의 지원·비용·제거 조건
- 구현 순서와 독립 검증 가능성
- test·품질 gate의 충분성·중복·과잉
- 유지보수·소유권
- 문서·SOT·router·용어

구현 준비도와 `Planned` 자격은 `실행 계획` 또는 `계획 완료` 후보에서만 감사한다. 그때도
계획의 `Planned` 표기나 열린 결정을 올바르게 표시했다는 사실만으로 통과시키지 않는다.
작업 실행 workflow의 `설계 준비도와 구현 입장`을 기준으로 관련 설계 표면, 사용자 소유
결정, Agent 기술 계약, 독립 검증과 실제 상태 인계를 각각 대조한다. 결과 정의·해결 설계
후보는 현재 단계 품질을 통과할 수 있지만 구현 준비도와 `Planned` 자격은 없으며, 구현
후보를 현재·결정 직후의 실행 단위로 투영하면 stage-ahead 필수 결함이다.

구현 순서의 필수 선행 edge마다 `producer의 exact 결과 → 그 결과가 없으면 처음 시작하거나
완료할 수 없는 exact consumer 단계`를 계획과 독립 source에서 복원한다. 계획 번호,
`producer`·`public contract` 역할명이나 일반적인 provider-before-consumer 원칙만 있고 실제
소비 단계가 없으면 필수 선행으로 통과시키지 않는다. 독립 구현 가능한 단위, 최초 합류점부터
필요한 결과와 비용상 권장 순서를 구분하지 않은 경우에도 구현 순서 축의 결함으로 반환한다.

문서 결과가 포함되면 독자 질문·actual source→owner의 누락과 owner→독자 필요·source의 과잉을 양방향으로 대조한다. 실행 순서·분기·호출·값 전달·책임 같은 관계를 계획 문서만으로 하나의 의미로 복원할 수 있는지도 판정한다. 현재 사실은 current source와 일치해야 하고, 아직 구현되지 않은 목표 관계는 확정 요구·결정과 계획된 구현·검증에 연결돼야 한다. 목표 관계의 구현 후 source 일치는 `verify-dev`와 최종 문서 감사가 판정한다. 별도 `$verify-documentation final`을 중복 호출하지 않는다. 다문서 topology 자체가 아직 제안 단계이면 그 산출물만 `$verify-documentation proposal`에 인계한다.

사용자가 검토하고 선택하는 계획이면 첫 화면, heading과 결정 카드만 읽고도 무엇을 만들거나
선택하는지, 대안의 실제 차이와 영향을 일상어로 설명할 수 있는지 확인한다. 직접 선행 문맥에서
이해가 확인되지 않은 새 명사는 `최초 사용 문장 안의 일상어 설명 / 쉬운 표현으로 교체 / 기술
상세로 이동` 중 하나여야 한다. 뒤쪽 용어집이나 다른 미정의 영어·역할명은 통과 근거가 아니다.
이 공백 때문에 사용자가 결정 질문을 이해할 수 없으면 문서·용어 축의 필수 결함이다.

API path, HTTP method, field, enum과 표준 식별자처럼 exact 구현·검증 계약인 literal은 쉬운
문장 요구를 이유로 번역·삭제하지 않는다. 필요한 역할만 같은 단위에서 설명한다. 직접 선행
문맥에서 사용자가 뜻을 이해했음이 확인된 용어도 반복 설명을 강제하지 않으며, 한 용어의
이해를 인접한 다른 기술 명사로 전파하지 않는다.

후보가 열린 사용자 결정의 추천을 사용자 선택 없이 `확정 결정`, `남은 결정 없음`,
`Planned`, `계획 확정` 또는 동등 의미로 기록하면 기술적으로 완전해도 필수 결함이다. Work
item만이 아니라 design, router, product·project projection까지 같은 권위 상태인지 확인한다.
반대로 사용자가 exact 값을 선택한 항목은 일반적인 확인 금지 문구를 이유로 다시 열지 않는다.
Agent 소유 기술 선택은 source·대안·trade-off·재검토 조건과 독립 검증으로 감사하며, 사용자
선택으로 잘못 귀속하거나 기술 선택 자체를 선택 질문으로 넘기지 않는다. 이 기술 선택이나
검증만을 사용자 단계 확정으로 소비하면 필수 결함이다. 반대로 exact 후보에 대한 무모호한
채택·반영 원문이 있는데도 같은 내용을 다시 확인시키면 진행 계약의 필수 결함이다.
값 선택만 있는 논의에서 artifact를 변경한 후보도 권한 경계의 필수 결함이다.

제품 표면과 무관한 축은 제외 이유를 남긴다. Best Practice는 이름난 패턴이 아니라 예방하는 실패, trade-off와 현재 source에 연결한다.

## 4. 발견 포화와 판정

첫 결함에서 멈추지 않고 모든 축을 닫은 뒤 발견을 다음 관계로 정규화한다.

```text
최초 요구 → 기대 계획 → 실제 계획 → source 근거
→ 영향 → 필요한 수정·결정 → closure 조건
```

- **통과**: 모든 필수 요구와 축이 충족 또는 근거 있는 해당 없음이고 열린 결함·공백이 없습니다.
- **실패**: 목적, 안전, 문서 계약이나 실행 가능성을 깨는 필수 결함이 있습니다.
- **판정 불가**: authoritative input, 근거, 환경이나 사용자 결정이 없어 필수 항목을 닫을 수 없습니다.

선택적 개선은 통과를 막지 않는다. coverage JSON과 이를 검사하는 custom verifier는 만들지 않는다. 검증의 독립성은 최초 목적에서 다시 시작하고 source·공식 근거를 직접 읽는 방식으로 확보한다.

`통과`는 현재 artifact가 자신의 단계·상태 계약을 충족한다는 뜻이다. 열린 사용자 결정이
정확히 표시된 결과 정의 후보도 현재 단계 감사를 통과할 수 있지만 그 단계 closure나
`Planned` 자격은 없다. `Planned` 자격은 실행 계획 또는 계획 완료 후보에서 모든
사용자 소유 값의 사용자 선택 또는 독립 source 제약, Agent 소유 기술 선택의 근거·대안·
trade-off, 계획 품질 통과와 exact 실행 계획 후보에 대한 사용자 확정이 함께 있을 때만
별도로 반환한다. Verifier verdict 자체는 사용자 선택·단계 확정이나 상태 승격 권한이 아니다.

## 5. 판정 결과

발견은 모든 검토 축을 닫은 뒤 `기대 → 실제 → source 근거 → 영향 → 닫는 조건`으로
한 묶음에 제공한다. 계획 artifact·결정의 결함인지 authoritative input·환경 공백인지
구분하되, verifier가 계획을 수정하거나 새 결정을 만들지 않는다.

이 판정을 `$dev-plan`과 작업 실행 workflow에 인계한다. 보완 가능 여부, 후보 무효화,
closure 횟수, `Planned` 전환과 구현 routing은 작업 실행 workflow가 판정하며 이 skill은
그 lifecycle을 다시 정의하지 않는다.

[완료 및 작업 보고 워크플로우](../../../docs/workflow/140-completion-reporting-workflow.md)의
검증 경로에는 판정, 목적 대비 필수 발견·공백, 실제 검증 범위와 snapshot의 문서·Git
사실만 제공한다.
