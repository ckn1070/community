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
- 복합 실행 batch는 raw 항목별 문장 기능·효과·positive 권한 source 또는 미확정 사유를
  첫 writer 전에 완전히 대조합니다. 하나라도 질문·비교·미확정 항목이면 승인된 다른 항목도
  부분 실행하지 않고 논의를 닫은 새 generation에서만 batch를 재개합니다.
- 실행 회고의 행동 verdict는 통제 처분·canonical owner 효과·증거 등급·Git 자격과 전체
  상태를 하나의 closure identity로 Git과 보고 phase에 전달합니다. History·상태 문서와
  commit은 canonical 통제 적용이나 actual invocation 공백을 대신하지 않습니다.
- 반복 이의 뒤 기술 설명은 선택 span과 원래 질문 축을 exact section owner·정보 수명·
  빈 환경 재생 효과에 결속합니다. 복합 artifact 전체의 참인 책임이나 인접한 사실로 선택
  문장의 위치·중복·필요성 질문을 바꾸어 방어하지 않습니다.
- Local unpushed 기존 commit 안의 문제·누락 수정 요청은 `확인해서`가 포함돼도 해당 patch와
  후속 topology를 대상으로 보존합니다. 현재 코드·새 commit을 별도 target으로 명시하거나
  읽기 전용인 경우와 공유 history의 안전 경계는 분리합니다.
- 삽입 회고 뒤에는 원래 artifact의 내용과 요청된 Git 효과를 같은 활성 원자로 복원합니다.
  통제 commit이나 same-file overlap은 원래 commit 완료가 아니며, worktree-only artifact는
  실제 commit 또는 명시된 blocker가 생길 때까지 `Active`로 유지합니다.
- 완료 보고는 삽입 결과보다 원래 artifact의 상태를 먼저 투영합니다. 요청된 Git object에
  artifact가 없으면 첫 화면에서 `수정됨·미커밋`과 전체 미완료를 직접 밝히고, 통제 commit을
  그 결과의 대체 완료로 나열하지 않습니다.
- 결과를 바꾸는 값을 처음 선택·유지·교체하는 실질 projection도 writer와 같은 결정 입장
  gate를 소비합니다. 사용자 선택, source 제약, Agent 책임인 기술 추천, 프로젝트 외부 판단이
  필요한 열린 사용자 결정과 미증명을 분리합니다. 열린 값을 질문 전에 확정하거나 반영을
  약속하지 않되, API·schema 같은 기술 추천을 근거 없이 사용자 선택으로 넘기지도 않습니다.

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
- 이번 staging HTTPS ingress 사건은 인접 승인을 DNS 선택 질문에 전파하고, batch coverage가
  닫히기 전에 개발 writer를 시작한 실제 invocation 실패를 확인했습니다. 현재 후보는 그
  writer 입구를 강화하지만, 미래 복합 요청에서 같은 coverage identity가 실제로 전달되는지는
  후속 live invocation 증거가 필요합니다.
- 한 번짜리 Super Admin Application command에 별도 문서와 600줄대 shell 체계를 추가한
  사건에서는 기존 tool-adoption admission의 live invocation이 끊겼습니다. 불필요한 artifact를
  제거하는 과정에서 password lifecycle의 Procedure owner까지 소거하고 후보에 맞춰 one-shot
  예외를 추가한 후속 실패가 발생했습니다. 해당 완료 효력을 무효화하고 짧은 Procedure와
  end-to-end 운영자 walkthrough 경계로 교체했습니다.
- 이 실행 회고에서는 actual invocation `검증 불가`를 받은 뒤 History·상태 문서만
  commit하고 회고를 완료로 보고했습니다. 증거 분리 oracle은 작동했지만 closure·Git·report
  consumer가 verdict를 보존하지 못한 재발이므로, 해당 topology의 이전 완료 범위는
  무효화하고 공통 closure identity를 강화했습니다.
- 재사용 Scenario의 중복 전달 항목에 대한 반복 이의에서는 explain-tech의 반론 재평가
  통제를 실제로 읽고도 원래 질문 축을 `같은 상태 전이를 왜 두 번 두는가`에서 `Scenario가
  live 상태를 소유하는가`로 바꿨습니다. 현재 후보는 선택 span·원래 질문·하위 owner·수명
  identity를 강화하지만, 미래 설명 actor의 실제 호출은 후속 live 증거로만 판정합니다.
- `748a0bfe` commit 수정 요청을 현재 content correction과 새 commit 추가로 바꾼 사건에서는
  사용자 target identity가 Git 효과 판정 전에 소실됐습니다. 현재 후보는 기존 commit을
  mutation target으로 고정하는 admission을 강화하지만, 공유 remote history에는 기존 안전
  경계 없이 rewrite를 확대하지 않습니다.
- Scenario correction과 commit을 요구한 원래 작업은 통제 commit 두 개 뒤에도 artifact가
  worktree에만 남았습니다. 기존 복귀점 통제가 있었지만 복귀 consumer가 원래 Git 효과와
  실제 commit object를 대조하지 않아, same-file overlap이 제외 처분처럼 소비됐습니다.
- 같은 상태를 보고할 때에는 통제 commit 완료를 첫 판정으로 두고 Scenario 미커밋을 뒤의
  caveat에 묻었습니다. 기존 first-screen 통제가 있었지만 projection root가 삽입 결과로
  바뀌어 원래 사용자 결과의 material incomplete가 우회됐습니다.
- 카탈로그 계획에서는 main의 최신 `dev-plan`이 추천의 사용자 결정 승격을 차단했지만 target
  feature branch가 이전 skill blob을 유지했고, 계획 후보는 API·DB·초기 데이터와 운영 값을
  사용자 선택 없이 `Planned`로 올렸습니다. 후속 댓글 처리에서는 최신 통제를 실제로 읽고도
  첫 실질 commentary 전에 결정 입장 결과를 만들지 않아 API 기술 추천을 직접 재검토해
  답하지 않았고, 정보 범위와 초기 데이터 운영 책임도 사용자에게 묻지 않은 채 반영 대상으로
  선언했습니다. 현재 후보는 공통 owner의 첫 projection 입장과 exact control locator 결속을
  강화하고, 같은 권위 계약을 소비하는 설계 workflow·work item lifecycle·`dev-plan`·
  `verify-plan`을 동기화합니다. 모델링된 행동 검증과 이번 evaluator·runner invocation 외의
  미래 plan writer 호출은 계속 미검증입니다.

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
