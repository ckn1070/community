---
name: verify-documentation
description: "사용자 결정·단계 확정용 plan artifact의 상세 작성 전 중대한 동결 구조 map을 proposal로 감사하거나, 저장소 Markdown의 중대한 구조 proposal 또는 작성과 작성자 source 대조가 끝난 동결된 중대한 final 후보를 fresh reviewer가 독립 감사할 때 사용한다. Plan structure proposal에서는 hierarchy·first-screen·결정 권위·owner·보존 관계와 대표 골격을 추가로 감사한다. canonical SOT·workflow·router·runbook의 신설·대규모 재작성, 다문서 병합·분리·이동·삭제, owner·관계·번호·탐색 topology 변경과 전체 coverage 주장도 대상이다. routine 오탈자·링크·상태 동기화의 두 번째 대조에는 사용하지 않으며, 중대한 문서가 계획·개발 결과에 포함돼도 일반 verifier의 문서 축으로 대체하지 않는다."
---

# 문서 검증

작성자의 map·진단·완료 주장을 oracle로 사용하지 않고 원래 목적, actual source와 동결된
검증 대상에서 독립 감사한다. 문서를 수정하지 않으며 대상과 직접 영향 범위에 도달 가능한
audit 축을 모두 닫은 뒤 하나의 판정을 반환한다.

## 진입 조건

[문서화 워크플로우](../../../docs/workflow/150-documentation-workflow.md)가 다음 중 하나로
선택한 경우에만 시작한다.

- **proposal**: 작성 전의 중대한 구조·owner·topology 제안
- **final**: 작성과 작성자의 두 번째 source-first 대조가 끝나 더 이상 의도된 수정이
  없는 동결된 중대한 후보

routine 국소·일반 변경은 작성자의 두 번째 대조가 끝점이며 이 스킬을 자동으로 덧붙이지
않는다. 계획·개발 verifier가 routine 동반 문서를 감사하는 것은 이 phase와 중복되지
않습니다. 중대한 후보는 같은 reviewer가 관여해도 이 스킬의 독립 입력·동결 대상 감사 축·
`proposal` 또는 `final` verdict를 실제로 반환해야 하며 generic 판정으로 대체하지 않습니다.

진입 전에 작업 실행 workflow가 검증 계기, exact 대상, 직접 영향 범위와 제외를 동결했는지
확인합니다. 중대한 proposal이나 최초 final 후보는 승인된 중대한 문서 결과 전체를 검증할
수 있습니다. 일부 보완·correction 뒤 closure는 변경된 section·owner·관계와 그 변경 때문에
의미·탐색·실행 결과가 달라질 수 있는 직접 consumer만 검증합니다. 전체 corpus나 영향받지
않은 기준선으로 확대해야 한다고 판단하면 감사를 시작하지 않고 이유·exact 범위·기존
verdict 영향을 반환해 사전 보고와 사용자 허락을 요구합니다.

## 독립 입력 복원

fresh reviewer가 작성자의 coverage map·outline 근거·기대 답을 받지 않은 상태에서 다음을
실제 source로 대조하되, 전달받은 검증 대상을 임의로 넓히지 않는다.

1. 원래 요청의 문서 결과·비목표와 독자가 답해야 할 질문
2. `AGENTS.md`, [문서 목차](../../../docs/000-index.md), 관련 router
3. [작업 실행 워크플로우](../../../docs/workflow/100-work-execution-workflow.md)의 원래
   요청·의미 authority와 동결 후보 경계
4. [문서 정보 구조 가이드](../../../docs/100-documentation-guide.md)의 문서 유형·owner·
   보존·관계·번호 계약
5. 문서화 workflow가 정한 actual source·관련 문서 범위, 후보 artifact와
   inbound·outbound link

검토 중 후보나 source·관련 문서 범위가 바뀌면 그 snapshot에 판정을 내리지 않고 새로
고정된 후보를 요구한다. 작성자가 제안한 기술 상세나 해결 방향은 원래 요청 또는
authoritative source에 연결될 때만 기준이 된다.

## Proposal 감사 축

다음을 양방향으로 감사한다.

```text
독자 질문·source → 제안 정보와 owner: 누락 없음
제안 정보·artifact → 독자 필요·source·보존 이유: 임의 확장 없음
```

그 위에서 다음 축을 독립 복원한다.

- 문서 유형과 병합·분리 경계가 독자 행동, source, 갱신 주기와 보존 기간에 맞는지
  확인한다.
- 각 section이 하나의 독자 질문에 연결되고 관계를 보존하는 최소 표현을 사용하는지
  확인한다.
- canonical owner가 하나이며 상세 owner·router·consumer로 향하는 관계 방향이
  명확한지 확인한다.
- 이동·삭제되는 고유 정보의 새 owner와 old path의 대체 관계를 확인한다.
- 번호 class·scope, actual sibling·router group map 또는 work item body·companion의
  현재 순서, before/after topology와 move map이 일치하는지 확인한다.
- 새 Procedure 의미가 원래 요청·authoritative source·확정 design 없이 만들어지지
  않았는지 확인한다.
- 전체 흐름·이해 가능성이 결과이면 제안이 같은 품질 차원의 전체 관계 골격과 대표
  source를 검증하며 특정 세부 절차가 결과를 대체하지 않는지 확인한다.

### Plan structure proposal 추가 감사 축

`proposal`이 작업 실행 workflow의 plan-structure round에서 호출됐으면 위 공통 Proposal
축과 함께 다음 구조 관계를 양방향으로 감사한다. 별도 mode나 verdict를 만들지 않으며,
Content의 정확성·추천 결론·기술 대안 자체를 재판정하지 않는다.

- lifecycle이 허용한 artifact·section과 금지한 다음 단계 section
- heading별 독자 질문, 상세 owner와 실제 읽기 순서
- 첫 화면의 두 열 field·순서, 안정 handoff actor·상태와 live receipt의 분리
- 결정 identity별 사용자 요구·source 제약·Agent 기술 선택·사용자 값 확정·단계 확정 권위,
  활성 질문과 닫힌 결정의 배타성
- 활성 카드 7개 field와 모든 카드를 위한 단일 결합 응답 형식 owner
- body·`-design`·router·rollback에서 보존할 정보와 canonical owner
- representative template이 같은 구조로 closed-only와 active-decision 상태를 모두 표현하는지

Map·candidate digest가 없거나 stale하거나, 구조가 content 값을 미리 확정해야만 성립하면
`판정 불가` 또는 `실패`로 반환한다. 통과 verdict는 content 품질이나 사용자 결정을 대신하지
않는다.

## Final 감사 축

먼저 동결한 대상의 실제 의미 효과가 원래 결과와 비목표 안에 있는지 확인한 뒤, 그 대상과
직접 영향 범위에서 네 방향을 감사한다.

| 방향 | 실패 조건 |
| --- | --- |
| source·독자 질문 → 후보 | 필요한 현재 사실이나 답이 빠짐 |
| 후보 section·예시 → source·독자 필요 | 근거 없는 주장, 반복·과잉 상세 또는 새 의미가 있음 |
| router → canonical owner → 상세 source | 잘못된 owner로 이어지거나 탐색이 끊김 |
| 현재 사실 → 관련 문서 | 중복 owner, 폐기 결론 또는 현재성 충돌이 남음 |

후보만으로 핵심 개념·순서·분기·계층·호출·값 전달·상태를 다시 그린 뒤 actual source의
구성요소·edge·조건·전달값과 비교한다. 이어서 다음을 확인한다.

- 첫 화면의 목적·책임·다음 탐색 경로와 문서 유형에 맞는 정보 순서
- 다른 owner를 복제하지 않는 관계 link와 local target·anchor의 존재
- actual sibling·router group map 또는 work item body·companion에서 다시 계산한 번호
- 이동·삭제의 모든 inbound link, 대체 owner와 최종 directory topology
- repository source·공식 upstream 계약·live 관찰·미실행 외부 책임의 증거 경계
- 전체·특정 집합 적용 주장에 포함된 변경하지 않은 artifact 또는 명시된 제외·남은
  이관 범위

형식, line 수와 선택적 문장 취향만으로 실패시키지 않는다. 반대로 link 존재만으로
의미·owner·관계가 맞다고 판정하지 않는다.

## 판정

- **통과**: 선택한 phase에서 동결한 대상에 도달 가능한 모든 감사 축이 원래 목적·source·
  문서 계약과 일치한다.
- **실패**: 필수 정보 누락, source 충돌, 임의 의미, 중복 owner, 잘못된 관계·topology,
  owner 없는 이관 또는 근거 없는 전체 적용 주장이 있다.
- **판정 불가**: fresh 독립성, 동결 후보, authoritative source나 필수 결정이 없어 감사
  축을 닫을 수 없다.

동결한 대상에서 도달 가능한 모든 축을 조사한 뒤 발견을 `기대 → 실제 → source 근거 →
영향 → 닫는 조건`으로 한 묶음에 정리해 문서화 workflow에 반환한다. 대상 밖에서 우연히
관찰한 문제는 후보 실패나 수정 요구로 만들지 않고 별도로 반환한다. 보완·재검증·상태
전이와 최종 closure는
[작업 실행 워크플로우](../../../docs/workflow/100-work-execution-workflow.md)가 소유하며,
이 스킬은 별도 반복 규칙·완료 알고리즘·custom 의미 verifier를 만들지 않는다.
