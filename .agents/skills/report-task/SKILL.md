---
name: report-task
description: "사용자가 상태 변경 작업의 현재 위치나 확정한 결정이 모두 닫혔는지를 한 번에 판단하거나, 진행 중인 작업의 주체·범위·현재 행동에 이의를 제기해 전체 실제 상태를 다시 확인해야 할 때 사용한다. 작업 실행 workflow가 동결한 전체 operation map에서 완료·진행·차단·중단·범위 변경·검증·Git 상태와 이미 확정된 다음 책임을 사용자용 projection으로 보고한다. 전체 상태가 아니라 다음 작업·구축 순서·추천 자체만 묻는 질문에는 사용하지 않고 explain-tech를 사용한다. 계획·설명·검증·회고의 내용 자체는 해당 전문 skill이 소유하며, 상태·작업 범위를 다시 판정하거나 새 실행 권한을 만들지 않는다."
---

# 작업 상태 보고

[완료 및 작업 보고 워크플로우](../../../docs/workflow/140-completion-reporting-workflow.md)를
적용해 실제 상태를 간결한 사용자 보고로 투영합니다.

## 입력 확인

작업 실행 workflow와 전문 owner, 실제 artifact·검증·Git source에서 다음을 읽습니다.

- 전체 목표와 현재 결과 단위
- 동결된 operation map, 보존된 상태·증거와 현재 응답 범위의 활성 원자
- 현재 응답 범위에 남은 각 원자의 canonical owner source와 직접 상태 증거
- 완료·미완료·차단 판정과 직접 증거
- 남은 책임, 다음 실행자와 재개 조건
- 검증과 repository·Git·remote·live 상태

대화 요약과 이전 보고를 현재 사실로 사용하지 않습니다. 입력이 서로 다르면 상태를
임의로 다시 판정하지 않고 canonical owner의 불일치로 보고합니다.

Router, 이전 답변이나 현재 메시지의 요약만으로 각 원자의 상태를 대신하지 않습니다. 현재
응답 범위의 runnable·blocked 원자와 exact next를 투영하기 전에 각각의 canonical owner
source를 실제로 읽습니다.

사용자가 직전에 고른 짧은 option·값을 근거로 결정 완료 여부를 물으면, 그 문자열의 일반
뜻이나 현재 답변의 주변 주제로 결정 차원을 새로 만들지 않습니다. 직접 선행 원문과
canonical source가 하나의 설정 key·consumer·결정 차원으로 유일하게 결속하면 그 mapping과
활성 값을 먼저 복원하고, 이미 닫힌 값을 이름이 짧다는 이유로 다시 묻지 않습니다. 남은
결정은 그 활성 값과 별개의 차원만 source-backed inventory로 보고합니다.

짧은 상태 확인이나 작업 주체·범위·현재 행동에 대한 이의 제기도 이 skill의 진입점입니다.
답변 전에 작업 실행 workflow의 Bootstrap으로 돌아가 pending mutation을 멈추고, 동결된
상위 결과와 실제 artifact·diff·검증 상태를 다시 읽습니다. 직전 하위 행동이나 Agent가
추측한 원인부터 조각내어 답하지 않고 전체 결과, 닫힌 단위, 남은 단위와 현재 행동 이유를
한 projection에서 연결합니다. 원인 자체의 회고·설명이 요청됐으면 상태 projection 뒤 해당
전문 owner에 인계합니다.

## 다음 행동 입력 admission

다음 행동을 포함하는 보고는 답변을 쓰면서 새 next를 감지하는 방식으로 만들지 않습니다.
입력 확인 시점에 작업 실행 workflow의 operation map을 먼저 복원합니다.

- canonical map이 하나의 exact 다음 행동을 이미 확정했고 그 원자가 현재 응답 scope에도
  포함되면 그대로 투영하고 다음 admission을 열지 않습니다. 이번 답변에서 제외됐다면
  canonical map에는 유지하되 projection하지 않고 남은 현재 응답 범위로 계속합니다.
- 그렇지 않고 현재 응답 범위의 활성 원자가 둘 이상이거나 관계·추천 판정이 아직 없으면
  [기술 설명 skill](../explain-tech/SKILL.md)과
  [비교·추천 reference](../explain-tech/references/comparison-and-recommendation.md)를 끝까지
  읽고, `canonical map에 보존된 상위 결과 관련 원자·상태·증거 전체 = dependency context`,
  `현재 응답 scope의 활성 원자 = 추천·출력 후보`로 구분해 입력합니다. `Closed`·비활성 원자는
  선행 증거·충돌 판정에는 사용하되 추천·출력 후보로 되살리지 않습니다.
- Reference가 기존 원자를 언급하지 않아도 map에서 제거하지 않습니다. 현재 Agent가
  source로 확인된 관계만 operation map에 병합한 뒤 아래 Projection을 계속합니다.
- 현재 응답에서 명시적으로 제외된 원자는 그 답변에 다시 넣지 않지만 canonical map에서
  삭제하거나 영구 취소로 바꾸지 않습니다. 다만 포함된 원자의 hard edge를 판정하는
  dependency context에서는 제거하지 않습니다.
- `지금 진행할 수 있는 것`처럼 상태 조건으로 추천 후보를 고르는 질문은 같은 명시 범위의
  `Blocked` 원자를 제외하는 지시가 아닙니다. Runnable next와 직접 대비되는 blocker·재개
  조건은 한 줄로 함께 투영하되, blocked 원자를 runnable 추천 후보로 바꾸지 않습니다.
- 다음 행동이 필요하지 않으면 상태만 보고합니다.

이 skill은 operation membership, dependency edge나 추천을 직접 판정하지 않습니다.

## Projection

1. 첫 문장에 전체 판정과 가장 중요한 결과 또는 미완료 이유를 둡니다.
2. 완료·미완료 결과, 책임과 다음 행동을 같은 결과 기준으로 연결합니다.
3. 다음 행동이 있으면 현재 응답 범위로 동결된 map의 대기·독립·합류·리마인더·추천 관계를
   상세 명령보다 먼저 투영합니다. 현재 milestone 뒤에서 합류한다는 이유로 원자를
   생략하지 않습니다.
4. Runnable 결과를 묻는 답변도 같은 명시 범위의 non-runnable 원자가 있으면 exact blocker와
   재개 조건을 분리해 보여 줍니다. 명시적으로 제외된 원자는 이 비교를 위해 되살리지
   않습니다.
5. 검증은 성공·실패·미실행·검증 불가를 구분합니다.
6. Git과 외부 상태는 reporting workflow의 projection 경계를 유지합니다.
7. 작은 보고는 불필요한 heading과 해당 없는 항목을 생략합니다.

진척 요청은 이미 고정된 결과 단위와 terminal gate만 사용합니다. 임의 백분율이나
파일·줄·시간 같은 활동량을 진척으로 만들지 않습니다.

## 경계

- 계획·결정의 내용은 `$dev-plan` 또는 해당 설계 owner가 소유합니다.
- 기술 설명은 `$explain-tech`, 검증 판정은 해당 verify skill이 소유합니다.
- 회고의 인과와 통제 결과는 회고 skill이 소유합니다.
- 차단 여부, 실행 지속 여부와 권한은 작업 실행 workflow의 판정을 그대로 사용합니다.
- 보고 자체는 상태 변경, 질문, 승인 또는 새 작업을 만들지 않습니다.
- 새 다음 행동을 만들 필요가 없으면 확인된 상태에서 끝냅니다. 다음 행동을 제시할 때에도
  map에 없는 선택을 만들거나 국소 미완료를 전체 차단으로 확대하지 않습니다.

보고 뒤에도 현재 권한에서 계속할 필수 작업이 남아 있으면 terminal 상태로 닫지 않고
실행 owner에게 인계합니다.
