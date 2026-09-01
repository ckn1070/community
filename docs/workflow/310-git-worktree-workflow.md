# Git Worktree 워크플로우

이 문서는 현재 checkout과 `main`을 보존하는 worktree 생성, branch 통합, conflict
처리와 안전한 worktree·branch 정리 mechanics만 소유합니다. 요청·권한·결과
dependency·차단·closure는 [작업 실행 워크플로우](100-work-execution-workflow.md),
stage·commit·push와 history 복구는
[커밋 워크플로우](300-commit-workflow.md)가 소유합니다.

## 1. 입력과 authoritative state

작업 실행 워크플로우에서 다음 입력을 확정한 뒤 mutation을 시작합니다.

- primary repository와 source·target worktree의 exact path
- source·target branch와 고정한 commit SHA
- worktree 생성, target ref 통합, conflict 내용 수정, merge commit과 remote push별 허용
  효과, 통합 완료 뒤 source worktree·local branch의 보존 요청 또는 cleanup 차단 조건
- 제외할 기존·다른 작업의 dirty·staged·untracked·ignored 상태
- 통합 전후 필요한 domain 검증과 완료 oracle

skill 이름, helper 출력과 branch metadata는 이 입력이나 권한을 만들지 않습니다. 표준
Git object·ref·index와 `git worktree` 등록 상태를 authoritative source로 사용하고 별도
snapshot, journal, metadata ledger와 custom guard를 만들지 않습니다.

worktree 하나에는 작업 실행 workflow가 확정한 독립 병렬 책임 하나와 branch 하나만
둡니다. 다른 책임을 덧붙이거나 branch 전환으로 같은 worktree를 재사용하지 않습니다.

변경 직전에 관련 worktree에서 다음을 다시 확인합니다.

```sh
git status --short --branch
git diff --cached --name-status
git worktree list --porcelain
git rev-parse HEAD
```

현재 worktree에서 branch를 전환하거나 기존 변경을 stash, reset, restore, clean,
commit하지 않습니다. 관련 path가 clean하지 않거나 진행 중인 Git operation이 있으면
이를 자동 복구하지 않고 실제 상태를 작업 실행 워크플로우에 반환합니다.

## 2. 병렬 worktree 생성 mechanics

### Naming과 배치

생성 입력은 `<type>`, lowercase kebab-case `<description>`과 local `<base-ref>`입니다.

- type: `feature`, `fix`, `docs`, `refactor`, `chore`, `spike` 중 하나
- branch: `<type>/<description>`
- target: primary repository sibling의
  `worktrees/<repository-name>-<description>`
- base 기본값: source worktree의 committed `HEAD`

base는 local exact commit으로 해석합니다. 명시 요청 없이 `fetch`, `pull`, branch 전환과
ref 갱신을 하지 않습니다. 같은 branch, remote-tracking branch, registered worktree 또는
filesystem path가 이미 있으면 재사용하거나 숫자 suffix를 붙이지 않습니다.

### Bundled helper

반복 가능한 executable은
`.agents/skills/parallel-start/scripts/parallel-start.sh`입니다. `$parallel-start`는 이
script를 호출하는 adapter이며, 수동 Git·copy 명령으로 절반만 재현하지 않습니다.

동일한 인자로 먼저 dry-run을 실행합니다.

```sh
bash <skill-directory>/scripts/parallel-start.sh \
  --dry-run \
  --type <type> \
  --description <description> \
  --base <base-ref>
```

preflight는 primary common Git directory, exact base, branch·target 부재, source
`.claude/` directory와 `.env` regular file, 두 local path의 untracked·ignored 상태,
필수 copy command를 검증해야 합니다. symlink, tracked path와 unignored path는
허용하지 않습니다. local file의 내용이나 secret은 출력·검색·비교하지 않습니다.

dry-run이 `PARALLEL_START_PREFLIGHT_OK`로 끝났을 때만 같은 인자에서 `--dry-run`을
제거해 한 번 실행합니다. helper가 다음을 모두 직접 수행하고
`PARALLEL_START_READY`를 출력해야 생성이 완료됩니다.

1. exact base에서 branch와 worktree를 생성합니다.
2. source `.claude/`와 `.env`를 내용 노출 없이 복사하고 `.env` permission을
   보존합니다.
3. target local path가 Git에서 tracked 또는 unignored가 아닌지 확인합니다.
4. target branch·HEAD·clean 상태와 worktree 등록을 확인합니다.
5. source branch·HEAD·dirty·staged 상태와 `main` ref가 전후에 같은지 확인합니다.

Git hooks는 common Git directory를 공유하므로 생성 중 설치하거나 덮어쓰지 않습니다.
helper는 application, DB, container, daemon, dependency와 외부 서비스를 시작하지
않습니다.

생성 뒤 검증 실패 시 helper는 이번 실행이 새로 만든 target만 branch·HEAD와 예상 밖
path 부재를 확인한 뒤 rollback할 수 있습니다. helper의 제한된 `--force` remove와 임시
directory 삭제를 기존 worktree·branch·사용자 파일에 적용하거나 수동 복구 선례로
확대하지 않습니다. rollback이 불완전하면 부분 생성 상태를 보존하고 강제 삭제나 반복
생성을 하지 않습니다.

## 3. 통합 전 Git discovery

main과 source의 exact path에서 다음 사실을 읽고 source SHA를 고정합니다.

```sh
git worktree list --porcelain
git -C <main-path> status --short --branch
git -C <source-path> status --short --branch
git merge-base <main-sha> <source-sha>
git log --oneline <main-sha>..<source-sha>
git log --oneline <source-sha>..<main-sha>
git diff --stat <merge-base>..<source-sha>
git diff --stat <merge-base>..<main-sha>
```

양쪽 full diff와 관련 source를 domain owner에 전달해 API·schema·보안·운영·문서 계약의
양립 여부와 통합 뒤 검증을 확정합니다. Git conflict가 없다는 사실을 의미 호환성으로
확대하지 않습니다. 결과·dependency 판정은 이 문서에서 다시 정의하지 않습니다.

mutation 직전에 source branch가 고정한 SHA를 계속 가리키고 main·source가 clean하며
main worktree에 진행 중 Git operation이 없는지 다시 확인합니다.

## 4. Branch 통합 mechanics

### 이미 포함됐거나 fast-forward인 경우

`git merge-base --is-ancestor <source-sha> <main-sha>`가 성공하면 새 merge를 만들지
않습니다. main이 source의 조상이면 target ref 통합 권한 안에서 exact source SHA로
fast-forward합니다.

```sh
git -C <main-path> merge --ff-only <source-sha>
```

fast-forward는 새 commit object를 만들지 않지만 main ref를 바꾸는 mutation입니다.
실행 뒤 main `HEAD`가 source SHA이고 source가 main의 조상인지 확인합니다.

### Diverged history인 경우

diverged history는 merge commit을 만듭니다. target ref 통합과 local merge commit
권한이 모두 입력에 없으면 merge를 시작하지 않습니다. 둘 다 있으면 source SHA를
고정한 상태에서 자동 commit 없이 merge를 시작합니다.

```sh
git -C <main-path> merge --no-ff --no-commit <source-sha>
```

conflict가 없더라도 index 전체와 `MERGE_HEAD`를 확인하고, merge commit은 커밋
워크플로우의 exact patch·message·표준 object 검증으로 만듭니다. squash, 임의
cherry-pick과 history rewrite로 source topology를 조용히 바꾸지 않습니다.

## 5. Conflict 처리

conflict가 생기면 일부 파일부터 고치지 않고 전체 unmerged set을 먼저 수집합니다.

```sh
git -C <main-path> status --short
git -C <main-path> ls-files --unmerged
```

각 path의 merge base, ours, theirs와 관련 source·test·SOT를 읽고 확정된 양쪽 고유
요구를 보존합니다. `ours` 또는 `theirs` 전체를 임의 선택하지 않습니다. conflict
내용 수정 권한이 있는 exact path만 편집하고 다음처럼 개별 stage합니다.

```sh
git -C <main-path> add -- <exact-conflict-path>...
git -C <main-path> diff --cached --name-status
git -C <main-path> diff --cached --check
git -C <main-path> diff --cached
```

unmerged entry가 0개이고 cached tree가 의도한 통합 결과와 같은지 확인한 뒤에만 merge
commit 절차로 넘어갑니다. 의미를 source에서 확정할 수 없으면 operation 상태와 exact
conflict를 보존하고 공통 차단 판정으로 반환합니다.

merge를 중단할 권한과 안전한 clean baseline이 있을 때만 `git merge --abort`를
사용합니다. 중단 뒤 ref·index·worktree가 pre-merge baseline으로 돌아왔는지 확인합니다.
`reset --hard`, 강제 checkout과 임의 directory 삭제로 복구하지 않습니다.

## 6. 통합 검증과 cleanup

main final tree에서 작업 실행 워크플로우와 domain owner가 정한 test·build·문서 검증을
실행합니다. 이 문서는 검증 항목, 공통 closure와 재시도 횟수를 만들지 않습니다. Git
mechanics는 다음으로 확인합니다.

```sh
git -C <main-path> merge-base --is-ancestor <source-sha> <main-head>
git -C <main-path> status --short --branch
git -C <main-path> rev-parse HEAD
git worktree list --porcelain
```

완료된 source branch·worktree를 main에 병합·통합·마무리하라는 요청은 사용자가 보존을
명시하지 않는 한, 검증 성공 뒤 source worktree와 local source branch를 제거하는
cleanup까지 하나의 terminal 결과로 포함합니다. 이 기본값은 remote branch 삭제 권한을
만들지 않습니다. Cleanup은 merge 전에 시작하지 않고 다음 조건을 모두 만족할 때만
수행합니다.

- source SHA가 검증된 main HEAD에 포함됐습니다.
- source worktree가 clean하고 진행 중 Git operation이 없습니다.
- 보존할 untracked·ignored file과 다른 process의 사용이 없습니다.
- 제거할 exact path가 `git worktree list --porcelain`의 source record와 같습니다.

```sh
git worktree remove <source-path>
git worktree list --porcelain
```

filesystem path가 사라지고 다른 worktree record가 유지됐는지 확인합니다. stale
directory가 남아도 소유자와 보존 필요성을 확인하지 않은 `rm`이나 forced remove를
사용하지 않습니다.

local source branch 삭제는 worktree 제거 뒤 수행하는 별도 ref mutation이지만, 위 통합
요청의 기본 terminal 결과에 함께 포함됩니다. source SHA가 main에 포함됐고 어느
worktree에도 checkout되지 않았을 때만 safe delete를 사용합니다.

```sh
git branch -d -- <source-branch>
```

`-D`로 포함 관계 검증을 우회하지 않습니다. push가 입력에 있으면 여기서 수행하지
않고 커밋 워크플로우의 exact branch push와 exact SHA 원격 검증으로 인계합니다.

사용자가 source 보존을 명시했거나 cleanup 조건 중 하나라도 충족하지 못하면 worktree와
branch를 그대로 두고 exact 대상과 차단 사유를 반환합니다. 이때 main 통합은 완료일 수
있지만 병합·통합·마무리 요청의 기본 terminal 결과는 cleanup까지 닫히지 않은 상태입니다.

## 7. Mechanics 결과 인계

다음을 공통 closure와 상태 projection에 인계합니다.

- source·main의 시작·최종 SHA와 merge base
- fast-forward, merge commit 또는 already-contained 관계
- conflict set과 확정한 resolution
- main final tree의 domain 검증과 source 포함 관계
- source worktree·branch의 유지 또는 제거 사실
- 남은 dirty·Git operation과 push 미수행·수행 상태

helper 출력, branch 이름과 cleanup 성공은 결과 전체의 완료나 다른 Git 권한을 증명하지
않습니다.

## 관련 문서

- **요청·권한·공통 lifecycle**: [작업 실행 워크플로우](100-work-execution-workflow.md)
- **stage·commit·push·history**: [커밋 워크플로우](300-commit-workflow.md)
- **로컬 실행 환경**: [로컬 개발 환경](../development/100-local-development.md)
