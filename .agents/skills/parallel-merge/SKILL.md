---
name: parallel-merge
description: "완료된 병렬 branch·worktree의 main 통합 요청에서 standard Git 상태, merge topology, conflict와 cleanup 가능 경로를 발견해 worktree workflow로 전달하는 adapter다. 사용자가 병렬 작업 병합·통합·마무리, 완료 worktree 반영이나 conflict 가능성이 있는 branch 검토를 요청했을 때 사용한다. 검증된 통합은 명시적 보존 요청이나 안전성 차단 사유가 없으면 source worktree와 local branch cleanup까지 기본 terminal 결과로 포함한다. 이 metadata는 read-only discovery만 라우팅하며 main ref 변경, conflict 편집, merge commit, cleanup 또는 push 권한을 독자적으로 만들지 않는다. 병렬 작업 시작·구현만 요청됐거나 일반 commit·push만 요청됐거나 source worktree의 미커밋 변경을 보존·이동해야 하면 사용하지 않는다."
---

# 병렬 작업 병합

표준 Git discovery를 공통 권한 판정에 전달하고, 허용된 효과만
[Git Worktree 워크플로우](../../../docs/workflow/310-git-worktree-workflow.md)로 실행한다.
이 skill은 metadata나 branch 이름을 실행 권한으로 바꾸지 않는 thin adapter다.

## 1. Read-only discovery

실제 디스크에서 다음을 읽는다.

1. `AGENTS.md`
2. `docs/workflow/100-work-execution-workflow.md`
3. `docs/workflow/310-git-worktree-workflow.md`
4. `docs/workflow/300-commit-workflow.md`
5. 양쪽 변경의 의미와 검증을 소유하는 domain source

처음에는 mutation 없이 worktree workflow의 `입력과 authoritative state`, `통합 전 Git
discovery`를 수행한다. main·source path, branch·HEAD, merge base, 양방향 commit·diff,
dirty·staged·untracked 상태와 진행 중 Git operation을 실제 source에서 고정한다.

Git conflict 부재를 의미 호환성으로 사용하지 않는다. full diff와 관련 SOT를 domain
owner에 전달해 통합 가능성과 final-tree 검증을 확정한다.

## 2. Operation map 인계

discovery 결과를 작업 실행 workflow에 전달해 다음 효과를 확정한다.

- main ref 통합
- conflict path 내용 수정
- diverged history의 local merge commit
- source worktree 제거
- local source branch 삭제
- remote push

완료된 source branch·worktree를 main에 병합·통합·마무리하라는 현재 사용자 원자는 main
통합뿐 아니라 검증 성공 뒤 source worktree와 local branch를 제거하는 기본 terminal
결과의 positive source다. 사용자가 source 보존을 명시하면 cleanup을 제외하고, 조건이
충족되지 않으면 강행하지 않는다. Conflict 수정, diverged history의 local merge commit과
remote push처럼 통합 요청만으로 확정되지 않는 효과에는 현재 사용자 메시지 generation의
독립적인 positive source가 있어야 한다.

과거 병합 요청, 내부 plan과 이전 turn의 pending merge는 현재 효과의 권한 source가 아니며,
현재 메시지의 새 실행 원자나 작업 실행 workflow가 허용한 continuation으로 다시 결속되지
않으면 dependency context로만 남긴다.

상위 Agent의 task·handoff는 현재 사용자 generation `G`와 source-backed effect set을
명시적으로 전달할 때만 그 권한을 운반한다. Parent task, 내부 `NEW_TASK`나 그 안의 실행
동사 자체를 사용자 원문 또는 독립적인 positive authority로 승격하지 않는다.

skill 이름, frontmatter description, `default_prompt`, branch·worktree metadata와 사용자의
read-only discovery 요청은 어느 효과도 허용하지 않는다.

## 3. 허용된 merge mechanics 실행

operation map에 main ref 통합이 있으면 worktree workflow의 branch 통합 mechanics를
그대로 실행한다.

- source가 이미 포함됐으면 새 merge를 만들지 않는다.
- fast-forward이면 exact source SHA에 `--ff-only`를 적용한다.
- diverged history이면 main ref 통합과 local merge commit 권한이 모두 있을 때만
  `--no-ff --no-commit`으로 시작한다.
- conflict는 허용된 exact path만 수정·stage하고 전체 unmerged set과 cached diff를
  확인한다.
- merge commit은 커밋 workflow의 message·exact patch·표준 object 검증으로 만든다.

local merge commit 권한이 없으면 diverged merge를 시작하거나 metadata를 근거로 commit
하지 않는다. conflict를 source에서 확정할 수 없으면 임의 ours/theirs 선택, squash,
cherry-pick, reset과 강제 checkout으로 우회하지 않고 actual operation 상태를 공통 판정에
반환한다.

dirty target, 진행 중 Git operation, conflict나 도구 거절은 discovery·precondition
결과이지 새 commit, stash, reset, rebase나 대체 merge의 권한 source가 아니다. 해당
precondition이 실패하거나 한 효과가 거절되면 같은 범위의 sibling Git 효과도 실행하지
않고 현재 operation map으로 돌아간다. 최초 Git mutation 전에는 현재 generation의
source-backed effect set을 `$git-finalize`에 인계하고, 인계되지 않은 정리·복구 효과를
병합 mechanics 안에서 만들지 않는다.

## 4. 검증과 기본 cleanup

main final tree에서 공통·domain owner가 정한 검증을 실행하고 source SHA 포함 관계와 Git
상태를 worktree workflow로 확인한다. 검증 항목, closure 횟수와 실패 뒤 재개 전략을 이
adapter에서 만들지 않는다.

사용자가 보존을 명시하지 않았으면 source worktree와 local branch를 workflow의 조건·순서로
제거해 통합의 기본 terminal 결과를 닫는다. Worktree 제거와 branch 삭제는 서로 다른
mutation이므로 각 precondition과 실행 결과는 따로 확인한다. 보존할 untracked·ignored
file, 다른 process 사용 또는 포함 관계 공백이 있으면 force remove, `rm`, `branch -D`로
지우지 않고 exact blocker와 남은 대상을 반환한다.

push 효과가 별도로 있으면 cleanup과 합치지 않고 커밋 workflow의 exact branch push와
exact SHA 원격 검증으로 인계한다.

## 5. Evidence 반환

main·source의 시작·최종 SHA와 topology, conflict와 resolution, final 검증, source 포함
관계, worktree·branch 유지·제거, 남은 dirty와 push 상태를 작업 실행 workflow와
`docs/workflow/140-completion-reporting-workflow.md`에 반환한다. adapter 실행 성공을
공통 완료 판정으로 사용하지 않는다.
