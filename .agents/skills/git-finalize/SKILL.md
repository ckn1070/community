---
name: git-finalize
description: "검증된 local 변경을 exact path의 Conventional Commit으로 기록하고, 별도 push 요청이 있으면 exact branch를 push한 뒤 해당 SHA의 required CI와 Sonar revision·Quality Gate·Open issues를 확인하는 Git orchestration adapter다. 사용자가 stage, commit, push 또는 Git 마무리 실행을 요청했을 때 사용한다. skill 이름이나 호출은 Git 권한을 만들지 않으며 질문·감사·상태 확인만 요청됐으면 read-only discovery만 수행한다. PR, history rewrite, image publish·ECR과 production 적용은 각각 별도 요청일 때만 범위에 포함한다."
---

# Git 마무리

공통 결과와 권한을 Git mechanics에 전달하고 실제 Git 명령의 증거를 다시 공통
closure로 반환한다. 이 skill은 권한·결과 단위·재시도·완료 알고리즘을 소유하지 않는
thin adapter다.

## 1. Canonical 계약 복원

실제 디스크에서 다음을 읽는다.

1. `AGENTS.md`
2. `docs/workflow/100-work-execution-workflow.md`
3. `docs/workflow/300-commit-workflow.md`
4. 변경 종류의 local 검증 owner
5. push 뒤 Sonar가 적용되면 `docs/development/100-local-development.md`

skill metadata와 명시 호출은 routing 정보일 뿐 stage, commit, push, history rewrite 또는
외부 발행 권한이 아니다. 작업 실행 workflow가 현재 요청에서 확정한 Git 효과만 operation
map에 넣는다. commit-only 요청에 push를 추가하거나 push 요청으로 새 commit을 만들지
않는다.

## 2. Read-only discovery와 후보 확인

커밋 workflow의 `Baseline과 dirty 보호` 명령으로 repository, branch, `HEAD`, index,
worktree와 unpushed range를 읽는다. 모든 path·hunk는 작업 실행 workflow에서 확정한 결과
단위 또는 제외 dirty에 연결한다. 판단 불가 상태와 기존·다른 작업을 자동 정리하지
않는다.

local commit 입력은 적용되는 source·test·build·문서 검증을 마친 후보여야 한다. 검증
인계에는 원래 결과 단위, 적용된 workflow의 포함·제외·부정 조건, exact diff·test·문서와
그 조건의 대조 결과와 `R/G/C/S0/P` identity가 있어야 한다. `P`의 전문 결과마다 candidate
반영 위치와 필수 후속 gate verdict가 없거나 generic review가 이를 대신했다면 stage하지
않고 원인 전문 phase로 반환한다. 이 adapter는 canonical control source를 실제로 다시 읽고,
결과를 소비하기 직전 target 상태 `S1`을 재수집한다. Control 기준선·요청 generation·candidate,
target 상태 또는 전문 결과 dependency가 검증 인계와 다르면 이전 검증을 승계하거나
stage하지 않고 후보를 `Invalidated`로 반환한다.

Candidate의 task-owned staged·tracked·untracked·외부 residual을 원래 기대 효과와 대조해
작업 실행 workflow의 `E/K/X/D` closure가 성립하는지도 확인한다. Clean status, staged 0이나
local commit 개수는 이 대조를 대신하지 않는다. Test 통과나 후보 문서끼리의 일치만 있고 이 source
대조가 없거나, 적용할 specialist 검토가 commit 뒤로 남아 있으면 미검증 후보로 관련
owner에 반환한다. 이 skill에서 공통 discovery·closure 횟수를 다시 정의하지 않는다.

설계 workflow가 적용된 candidate는 작업 실행 workflow의 목표 보존 identity와 설계 준비도
인계를 별도로 확인합니다. 목표 약화·미증명 항목이 있거나 필수 계획 검증이 없으면 stage하지
않습니다. 기존 committed 계약이 없던 도메인 소유권·모듈 경계·데이터 생명주기·공개 계약을
바꾸는 production patch라면 검증된 `Planned` checkpoint commit이 fixed-base `HEAD` 자체에
포함되고 첫 production commit보다 선행하는지 Git ancestry로 확인합니다. Checkpoint가
fixed base 자체인 경우도 유효하며, 같은 staged candidate나 뒤 commit의 plan·design은 선행
설계나 원래 목표 source로 인정하지 않고 `$dev-plan`으로 반환합니다.

## 3. Local Git orchestration

operation map에 stage·commit이 있으면 커밋 workflow를 순서대로 실행한다.

첫 Git mutation 전에 커밋 workflow의 분할 반증 gate를 실행한다. 예정 subject와 상위 작업명을
입력에서 제외하고 final diff의 실제 consumer·재시작·복구·revert 경계에서 가장 이른 유효
prefix를 먼저 찾으며, 각 prefix의 source·compile·직접 test 결과 또는 더 이른 prefix가
성립하지 않는 직접 dependency를 고정한다. 같은 요청·파일·공유 test, 전체 test 통과나
작성자의 불가분 선언은 이 증거가 아니다. History rewrite와 source만으로 분할 가능성이
닫히지 않는 일반 commit은 기존 dirty를 건드리지 않는 격리 candidate에서 prefix를 검증한다.

이 증거를 만든 뒤 예정 commit 순서를 사용자 commentary에 투영한다. 각 항목은 예정
subject, 원래 요구에서 동결한 하나의 결과, exact path·hunk와 필수 dependency, 적용된
specialist 검증과 같은 `R/G/C/P` identity를 포함한다. 또한 커밋 workflow가 요구하는
`변경 전 의도 → 변경 뒤 결과 → 선택 type`을 원래 요구·변경 전 source에 연결해 같은 항목에
표시한다. 결과 경계는 이름으로 선언하지 않고 다음 consumer, 실패 뒤 재시작 지점,
retry·복구 방법과 독립 revert 가능성을 대조한다. 하나라도 다르면 불가분 dependency가
증명되지 않는 한 별도 결과로 투영한다.

이 투영이 없거나 상위 목적 이름으로 서로 다른 경계를 합치거나, 한 subject가 독립적인
병렬 결과를 나열하거나, 그 commit만 revert했을 때 다른 결과의 코드·test·문서가 함께
사라지거나, 더 이른 유효 prefix가 있는데도 합치면 stage하지 않고 결과 단위 또는 specialist
검증으로 반환한다. 이 투영은 사용자에게 새 승인을 묻는 절차가 아니라 분할 반증 결과의
projection이다. 투영 자체를 source-backed 결과 단위 gate의 실행 증거로 다시 사용하지 않는다.

첫 Git mutation과 sandbox·tool approval 호출에는 operation map을 만든 exact 사용자 원문
원자, 적용한 workflow 상시 예외 또는 직접 Git 요청, exact 효과·path와 제외 효과를 함께
전달한다. 외부 gate가 repository source나 skill 결과 계약을 다시 발견할 것이라고 가정한
일반 승인 문구로 이 근거를 축약하지 않는다.

1. 첫 commit 전 `HEAD`를 fixed base로 고정한다.
2. 확정된 결과 단위·dependency 순서에서 commit patch와 subject를 만든다.
3. exact path만 stage하고 cached full patch·whitespace·제외 dirty를 확인한다.
4. Conventional Commit을 만든다.
5. commit object, parent, tree, full patch와 status를 표준 Git source에서 확인하고, 실제
   type이 mutation 전에 투영한 변경 전 의도·변경 뒤 결과와 일치하는지 다시 판정한다.
6. 모든 대상 commit 뒤 fixed base부터 cumulative diff와 `E/K/X/D` closure를 확인한다.

workflow의 명령과 금지를 그대로 실행하며 broad add, `commit -a`, `--no-verify`, stash,
destructive reset·clean·checkout 또는 custom receipt를 대체 경로로 만들지 않는다. same-file
hunk나 기존 staged 상태를 안전하게 분리할 수 없으면 stage하지 않고 정확한 discovery를
작업 실행 workflow로 반환한다.

history 복구가 operation map에 있으면 일반 commit 흐름으로 바꾸지 않고 커밋 workflow의
격리 candidate·compare-and-swap 절차를 적용한다. 첫 history rewrite 범위나 topology를
사용자에게 투영하기 전에도 커밋 workflow의 처분 manifest를 실제로 소비한다. 사용자
원문의 의미 target, 영향받는 각 commit·path·effect의 실제 patch와 `제거 / 보존 /
중단·질문` 처분이 모두 연결되기 전에는 checkpoint나 ancestry를 ref cutoff로 바꾸거나
`branch → base`를 출력하지 않는다. 무관한 descendant는 유지 순서로 전달하고, 관계가
미확정이면 topology 선택 전 작업 실행 workflow로 반환한다. 사용자가 exact cutoff 뒤
전체 제거 또는 exact 제거·보존 집합을 명시한 경우에는 그 지시를 다시 기능 관련성
질문으로 막지 않는다.

Git 명령의 sandbox·tool approval이 거절돼도 이미 확정된 operation map을 권한 없음으로
바꾸거나 사용자에게 같은 stage·commit·push 승인을 반복해서 묻지 않는다. 거절은 실행
가능성 증거로 작업 실행 workflow에 반환하고, 허용된 더 안전한 경로가 없으면 공통 차단
gate가 owner·막힌 효과·재개 조건을 판정하게 한다. 새 Git 효과나 사용자만 제공할 수 있는
입력이 실제로 필요한 경우에만 해당 새 결정으로 admission을 다시 연다.

## 4. Push와 exact SHA closure

별도 push 효과가 operation map에 있을 때만 커밋 workflow의 `Exact branch push`를
실행한다. live remote SHA와 fast-forward를 확인하고 exact refspec을 한 번 push한 뒤
`git ls-remote --refs`에서 expected SHA를 확인한다. force, 자동 merge·rebase와 history
rewrite를 사용하지 않는다.

repository 품질 계약이 적용되는 code push이면 같은 workflow의 `Pushed SHA의 CI와
Sonar`를 끝까지 실행한다. exact pushed SHA의 required CI가 성공한 뒤 repository
verifier로 Sonar revision·분석 시각·Quality Gate·Open issues를 확인한다. credential은
documented trusted local source에서 값 노출 없이 주입한다. 이전 SHA 결과, 분석 대기,
SHA 불일치와 API·인증 오류를 성공으로 바꾸지 않는다.

CI·Sonar 실패 뒤 candidate 재개, 새 commit·push 여부와 실제 차단은 증거를 작업 실행
workflow로 돌려보내 판정한다. image publish·ECR, PR과 production 적용을 자동으로
이어가지 않는다.

## 5. Evidence 반환

commit SHA·subject·parent·tree·exact path, fixed-base diff, 남은 dirty·staged·unpushed
상태, exact remote ref, CI URL·conclusion과 Sonar 결과를
`docs/workflow/140-completion-reporting-workflow.md`의 입력으로 반환한다. adapter의
명령 성공을 전체 결과의 완료로 선언하지 않는다.
