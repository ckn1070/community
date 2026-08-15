# 작업 실행 추적성과 회고 구조 개편

## 상태

In Progress

## 결과

현재 요청의 의미·권한·상태·완료·복귀점과 통제 변경 생애주기는
[100 작업 실행 워크플로우](../../../workflow/100-work-execution-workflow.md)가 소유합니다.
Request 해석을 외부 dispatcher·hook·reviewer에 넘기지 않고 현재 Agent가 직접 수행하며,
전문 skill은 그 결과를 각 domain의 실행 계약으로 소비합니다.

과거 `Done`은 작성자와 evaluator가 분리되지 않은 행동 검증, isolated runner 결과를 실제
actor invocation으로 확대한 판정과 복원할 수 없는 provenance에 의존했으므로 무효입니다.
현재 work item은 공통 통제의 의미를 정규화했지만 실제 phase별 호출 증거가 필요한 범위를
모두 복원하지 못해 `In Progress`입니다.

## 완료한 결과

- 사용자 원문의 대상·문장 기능·결과·권한을 원자로 분리하고, 후속 메시지가 명시적으로
  바꾼 의미만 갱신하는 공통 Bootstrap을 workflow에 정규화했습니다.
- Operation map은 완료·미완료·차단·제외 원자와 source coverage를 보존하며, 상태·다음 작업
  projection이 오래된 문서나 부재 검색으로 같은 thread의 직접 증거를 뒤집지 못하게 합니다.
- 개발·review·Git phase는 실제 control revision, request generation, exact candidate와 target
  상태를 handoff identity로 전달하고 결과 소비 직전에 drift를 다시 확인합니다.
- 실행 회고는 사건 artifact 복구·push와 통제 변경을 분리하고, 원래 acceptance oracle과
  specialist gate를 복귀 계약에 보존합니다.
- 회고 행동 검증은 작성자·fresh runner·독립 evaluator를 분리하고, 모델링된 통제 효과와
  실제 전이 invocation을 서로 다른 증거 등급으로 판정합니다.
- 선행 사용자 결정을 소비하는 구현·설명·검증·Git은 활성 값·권위·source·최신 명시 대체
  source를 복원하며, 후발 추천이나 일반 실행 승인이 기존 결정을 바꾸지 못하게 합니다.
- Specialist가 발견한 다른 owner의 결과는 현재 operation membership을 바꾸지 않습니다.
  현재 원문에 독립 실행 원자가 없으면 미충족 dependency나 비활성 finding으로 반환하고,
  개발·문서·Git 등 다른 primary 경로를 실행 약속으로 승격하지 않습니다.
- Reviewer finding은 사용자 목적과 원래 oracle에 먼저 대조합니다. 승인된 결과의 결함만
  수정하고, writer가 추가한 미승인 효과의 결함은 그 효과를 보강하지 않고 제거하며, 인접
  제안은 현재 mutation과 분리합니다.
- Writer 후보의 관찰 효과는 reviewer 호출 전에 mutation 전 operation map과 독립 source의
  입장 gate를 통과합니다. 후보가 만든 test·문서는 admission source가 아니며, 미승인 효과는
  상세 review와 보강 대상으로 보내지 않고 범위 복구 batch에서 제거합니다.
- 새 stage·commit 없이 기존 source를 push하는 Git phase는 CI·Sonar 검증의 동반 여부와
  무관하게 source commit/ref와 remote target을 먼저 고정합니다. 고정한 commit에 포함되지
  않은 다른 작업의 dirty는 제외 상태로 보존하고 기다리지 않으며, 고정한 source나 remote
  ref가 실제로 바뀐 경우에만 현재 충돌 처리 제약과 함께 반환합니다.
- 반복 consumer는 공유 계약·전체 inventory·대표 consumer·직접 oracle과 확장 조건을
  두 번째 mutation 전 admission 결과로 만듭니다. Proposal·outline·파일 생성과 final
  finding은 대표 결과를 대신하지 않으며, 공통 결함은 contract와 후보를 무효화해 대표
  단위부터 다시 닫습니다.

상세한 선택·대안·재검토 조건은
[003 작업 실행 추적성 설계](003-work-execution-traceability-design.md), Git만으로 복원하기
어려운 대표 재발과 남은 위험은
[003 작업 실행 추적성 History](003-work-execution-traceability-history.md)가 소유합니다.

## 미완료 결과와 사유

- `ca40631` 기록에서 검증 대상 develop skill blob과 `통제 효과 7/7`, `실제 invocation 7/7`
  주장은 복원했습니다. 그러나 그 기록의 fresh runner 역할 재현에는 장기 writer → reviewer
  → correction mutation의 actor별 trace가 없어 actual invocation 판정은 무효입니다.
- 기존 `RET-RETURN-REG`는 원래 oracle을 후속 actor에게 복원해야 한다는 모델링된 통제
  효과만 증명했습니다. Writer·fresh final·Git의 실제 invocation `PASS`와 그 의존 완료는
  무효이며, 원래 Application artifact 복구도 별도 복귀점에 남아 있습니다.
- 공통 통제의 현재 후보가 올바른 답을 생성한다는 것과 실제 장기 actor·다른 branch·live
  환경에서 영향 전에 호출된다는 것은 별도 증거입니다. 관찰하지 않은 전이는 `검증 불가`로
  유지합니다.
- 반복 consumer 통제는 독립 evaluator가 먼저 동결한 두 사건의 regression·renamed transfer와,
  독립 source·oracle을 가진 실제 다중 consumer false-block을 통과했습니다. 분리된
  producer→대표 reviewer→expansion actor fixture에서도
  같은 control·request·source·candidate identity가 보존됐습니다. 이 verdict는 해당 fixture의
  invocation만 증명하며 미래 writer invocation으로 확대하지 않습니다.
- 문서 writer가 실행 진입점 공백을 반환한 뒤 실제 미래 작업에서 개발 owner를 호출하지
  않는 cross-specialist invocation은 아직 관찰하지 않았습니다. 이번 행동 검증은 해당
  통제의 모델링된 효과와 현재 회고 원자의 분리만 판정하며 미래 invocation 완료로 쓰지
  않습니다.
- Reviewer 뒤 finding 처분만 강화한 `c763c15`는 확대된 후보를 review 입력으로 보내는 더
  이른 단절을 닫지 못했습니다. 현재 후보는 미승인 효과 입장 gate를 review 전으로 이동했고,
  regression·renamed transfer·false-block과 실제 producer → reviewer 인계를 독립
  검증했습니다. 이 판정은 해당 통제 효과와 인계만 증명하며, 실제 Application·DB
  산출물과 관찰하지 않은 다른 phase의 완료로 확대하지 않습니다. Exact control local
  commit은 Git phase의 남은 책임입니다.

## 남은 책임과 재개 조건

- 실행 회고를 수행하는 현재 Agent는 후보 작성 전에 독립 evaluator의 case·transition
  contract를 받고, fresh runner의 모델링 결과와 실제 actor invocation evidence를 분리해
  최종 verdict를 받아야 합니다.
- 여러 phase의 완료를 주장하면 producer·consumer actor, control·generation·candidate,
  소비한 oracle과 handoff 또는 terminal 효과를 전이별로 직접 대조해야 합니다. 하나라도
  미관찰이면 전체 invocation 완료로 합산하지 않습니다.
- 실제 다중 phase가 없는 읽기 전용·단일 actor 결과에는 존재하지 않는 writer·reviewer·Git
  evidence를 강제하지 않습니다.
- 이 work item의 `Done`은 현재 통제 후보의 독립 행동 검증뿐 아니라 실제 invocation을
  주장한 범위의 transition coverage가 복원된 뒤에만 다시 판정합니다. 사용자에게 추가
  runtime이나 외부 dispatcher 구현을 요구하지 않습니다.

## 확정 결정

1. 요청·권한·상태·완료와 통제 lifecycle은 workflow/100이 단일 owner입니다.
2. 현재 Agent가 요청 의미를 직접 판정하고 specialist·reviewer는 사용자 권한을 만들지
   않습니다.
3. 영구 SOT는 현재 규칙, design은 고유 결정, History는 대표 사건과 남은 위험, Git은 변경
   순서와 실행 기록을 소유합니다.
4. Custom dispatcher·hook·제품별 registry·대화 원장으로 자연어 의미와 회고 closure를
   강제하지 않습니다.
5. 통제 효과와 live invocation은 별도 증거 등급이며 closure는 필요한 전이 중 가장 낮은
   증거 등급보다 넓을 수 없습니다.

## 영구 SOT

- **공통 실행과 통제 생애주기**:
  [100 작업 실행 워크플로우](../../../workflow/100-work-execution-workflow.md)
- **상태 보고**:
  [140 완료 및 작업 보고 워크플로우](../../../workflow/140-completion-reporting-workflow.md)
- **개발 phase**:
  [200 개발 워크플로우](../../../workflow/200-development-workflow.md)
- **Git phase**:
  [300 커밋 워크플로우](../../../workflow/300-commit-workflow.md)
- **문서 정보 구조**:
  [100 문서 정보 구조 가이드](../../../100-documentation-guide.md)
