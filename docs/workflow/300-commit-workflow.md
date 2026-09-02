# 커밋 워크플로우

이 문서는 Git index·commit object·branch ref·remote ref의 의미와 안전한 exact
stage·commit·push·remote branch 삭제·history 복구, push한 exact SHA의 CI·Sonar 확인
절차만 소유합니다.
요청 의미, 상태 변경 권한, 공통 결과 단위·dependency·차단과 final
discovery·closure는 [작업 실행 워크플로우](100-work-execution-workflow.md)가
소유합니다.

## 1. 입력과 Git 효과

작업 실행 워크플로우에서 다음 입력을 확정한 뒤 이 절차를 시작합니다.

- exact repository, local branch·ref와 필요한 remote ref
- stage, commit, push, remote branch 삭제, history rewrite별 현재 권한
- 현재 작업 문서와 source에서 확정·검증된 의미 단위 지도
- Git에 반영할 결과 단위, exact path·hunk와 dependency 순서
- local 검증을 마친 후보와 push 뒤 적용할 terminal gate
- 제외할 기존·다른 작업의 staged·unstaged·untracked 상태

사용자가 파일·기능·변경 종류처럼 의미로 history rewrite 대상을 제한하면, 첫 Git topology
projection 전에 rewrite가 도달하는 각 commit·path·effect를 실제 patch와 사용자 원문에
대조해 다음 처분 manifest를 완성합니다.

```text
현재 사용자 원문의 exact target
→ 영향받는 commit·path·effect와 실제 patch
→ 직접 대상 / 불가피한 의존 / 무관 / 관계 미확정
→ 제거 / 보존 / 중단·질문
```

Checkpoint, parent SHA, 작성 시각과 descendant 관계는 조사 범위를 정하는 source이지
그 범위 전체가 제거 대상이라는 증거가 아닙니다. `X 시점부터 다시 시작`도 사용자가
`X 이후를 관련성과 무관하게 전부 제거`한다고 명시하지 않았다면 같은 방식으로
소비합니다. 무관한 descendant는 유지할 commit 순서에 넣고, 관계 미확정 항목이 하나라도
있으면 ref cutoff·재생 순서나 `branch → base`를 먼저 정하지 않고 작업 실행
워크플로우로 반환합니다. 반대로 사용자가 exact cutoff 뒤의 모든 commit 또는 exact
제거·보존 집합을 명시했다면 그 topology 지시가 처분 manifest를 직접 닫으므로 commit별
기능 관련성을 다시 묻지 않습니다.

첫 ref mutation 전에 현재 사용자 결정 source와 다음 Git topology를 한 record로 대조하고,
사용자 commentary에 그대로 투영합니다.

```text
push source commit/ref → remote target ref
+ remote 삭제이면 empty source → 삭제할 remote target ref와 expected SHA
+ 새 local·remote branch 생성 여부
+ PR 생성 여부
+ 각 효과의 현재 사용자 결정 source 또는 권한 없음
```

활성 결정이 direct push이면 새 branch·PR을 안전한 기본값이나 repository 관행으로
대체하지 않습니다. 새 branch가 승인됐다면 생성 전에 이름을
[Git Worktree 워크플로우](310-git-worktree-workflow.md#naming과-배치)의 허용 type과
형식에 대조합니다. 이 projection이 누락됐거나 활성 결정과 다르면 ref mutation을 시작하지
않고 작업 실행 워크플로우의 결정 기준선으로 돌아갑니다.

이 문서는 그 입력을 넓히거나 새 권한을 만들지 않습니다. 특히 skill 호출, 작업 완료,
diff 확인과 tool approval은 Git 권한이 아닙니다. 각 Git 효과는 다음처럼 서로 다릅니다.

| 효과 | 바뀌는 상태 |
| --- | --- |
| stage | worktree 내용을 index에 기록 |
| commit | index tree로 commit object를 만들고 현재 local ref를 이동 |
| push | exact local ref의 commit 반영 또는 승인된 remote ref 삭제 |
| history rewrite | 기존 commit topology와 local ref를 교체 |

commit 권한은 push나 history rewrite 권한을 포함하지 않고, push 권한은 새 commit이나
history rewrite 권한을 포함하지 않습니다. 권한·결과·공통 상태에 새 발견이 생기면 이
문서에서 차단이나 재시도 횟수를 정하지 않고 작업 실행 워크플로우로 돌려보냅니다.

사용자가 local unpushed 기존 commit을 식별하고 `확인해서 문제 부분 수정`, `고쳐`,
`그 변경 묶음에 넣어`처럼 그 commit 안의 문제나 누락을 바로잡으라고 요청하면 대상은
현재 worktree 내용이나 새 보상 commit이 아니라 해당 commit의 patch와 그 뒤 local
topology입니다. `확인해서`를 조사 source로만 축소하거나 이 표현을 일반 `commit` 권한과
새 commit 추가로 바꾸지 않습니다. 사용자가 `현재 코드`, `새 commit`처럼 별도 mutation
target을 명시하면 그 값을 따르고, 읽기 전용 설명이면 Git mutation을 만들지 않습니다.
대상 commit이 이미 공유됐거나 pushed됐으면 자동 rewrite하지 않고 현재 문제 수정과 Git
효과를 분리해 안전 경계로 반환합니다. 그 밖에 commit이 조사 source인지 mutation target인지
원문에서 하나로 닫히지 않을 때에만 첫 Git mutation 전에 그 차이를 질문합니다. Mutation
target이 닫혔으면 5절의 history 복구 절차로 인계하고, 예정 결과를 다음처럼 고정합니다.

```text
식별된 기존 commit의 변경 뒤 patch
+ 유지할 후속 commit 순서
+ 제거할 임시·보상 commit
+ 최종 branch tree와 제외 dirty
```

이 결과가 검증되기 전에는 편의상 새 commit을 먼저 만들거나 이를 안전한 중간 결과로
보고하지 않습니다.

사용자가 검증된 concrete 결과를 `commit`하라고 명시하면, 그 결과의 exact path·hunk를
index에 올리는 stage는 commit을 만들기 위한 불가피한 선행 효과로 operation map에 함께
결속하고 stage 권한을 다시 질문하지 않습니다. 이 연결은 귀속이 끝나지 않은 hunk, 제외
dirty와 commit 뒤 index에만 남길 변경까지 stage할 권한이 아닙니다. 반대로 stage만 요청한
경우에는 commit 권한이 생기지 않습니다.

작업 실행 워크플로우의 영구 규칙 변경 상시 예외가 적용되면, 검증된 영구 owner와 필수
consumer 변경의 stage·local commit을 operation map에 결속합니다. 별도 `commit` 표현을 다시
요구하지 않지만 push·새 branch·PR과 다른 작업 변경에는 이 예외를 확대하지 않습니다.

### 변경 처분과 recovery state

Git 상태를 깨끗하게 보이게 만드는 것과 작업 결과를 마무리하는 것은 다릅니다. 현재
작업에 속한 각 dirty path·hunk는 Git mutation 전에 다음 중 하나의 관찰 결과에 연결합니다.

| 처분 | 관찰 결과 |
| --- | --- |
| 결과에 포함 | 검증된 결과 단위의 exact commit에 기록됨 |
| 명시적으로 폐기 | 사용자 권한과 source로 폐기 대상이 확정되고 worktree에서 제거됨 |
| 기존·다른 작업으로 보존 | baseline과 같은 index·worktree 상태로 유지됨 |
| 임시 중단 | 사용자가 요청한 recovery state에 보존되고 현재 작업은 완료가 아니라 중단 상태로 남음 |

`마무리`, `정리`, `clean`이라는 표현만으로 특정 변경의 폐기나 임시 중단을 선택하지
않습니다. Stash·backup patch·임시 branch는 내용을 복구 가능하게 보존하는 recovery
artifact이며, 변경의 의미·귀속·최종 처분을 해결하지 않습니다. 따라서 task가 만든
recovery artifact와 clean `git status`를 함께 관찰해도 commit·폐기 결과나 작업 완료의
증거가 아닙니다.

계획한 삭제·복구가 tool 또는 approval gate에서 거부되면 stash처럼 더 가역적인 Git
효과를 같은 결과로 간주하지 않습니다. 원래 처분, 새 효과의 권한과 완료 oracle을 작업
실행 workflow에서 다시 판정하기 전에는 다른 Git mutation을 호출하지 않습니다.

## 2. Baseline과 dirty 보호

Git 상태를 바꾸기 직전에 표준 Git source를 다시 읽습니다.

```sh
git status --short --branch
git diff --name-status
git diff --cached --name-status
git log --oneline --decorate -n 20
git stash list
git worktree list --porcelain
```

첫 대상 commit 전의 `HEAD`를 `<fixed-base>`로 고정하고, 모든 dirty path·hunk를 확정된
결과 단위 또는 제외 상태에 연결합니다. 이 대응은 세션에만 두며 receipt, snapshot,
journal이나 custom guard로 저장하지 않습니다.

Git phase는 작업 실행 workflow와 specialist가 동결한 `R/G/C/S0/P` identity를 그대로
소비합니다. `P`에 있는 전문 결과의 candidate 반영 위치와 필요한 후속 gate verdict가
하나라도 없거나 generic review가 이를 대신했다면 stage하지 않습니다. Stage 직전 target
상태를 다시 읽어 `S1`을 만들고 control 기준선·request generation·candidate, target 상태
또는 전문 결과 dependency가 달라졌으면 이전 검증과 commit 계획을 승계하지 않습니다.
Main에 control commit이 있거나 known actor가 terminal이라는 사실은 같은 candidate와
전문 결과가 유지됐다는 증거가 아닙니다.

- 기존 staged 변경은 현재 결과 단위와 exact path·hunk가 모두 같을 때만 사용합니다.
- 판단할 수 없거나 제외된 staged 변경을 unstage해 index를 다시 만들지 않습니다.
- 다른 작업의 staged·unstaged·untracked·ignored 파일을 읽어 의미를 추측하거나 stash,
  restore, clean, reset하지 않습니다.
- `git reset --hard`, 강제 checkout, `git clean`과 임의 directory 삭제를 복구 수단으로
  사용하지 않습니다.
- stage·commit·push 전마다 baseline과 제외 dirty가 그대로인지 다시 확인합니다.
- 원래 기대 효과, final candidate에 실제 포함된 효과, 유효하게 취소·제외된 효과와
  candidate 밖 task-owned residual을 작업 실행 workflow의 `E/K/X/D`로 대조합니다. Clean
  status나 residual의 소실은 누락된 효과가 final artifact에 포함됐다는 증거가 아닙니다.
- 기존 stash는 ref와 이름만 baseline으로 기록하고 현재 작업의 변경으로 추측하지 않습니다.
  현재 작업이 새 stash나 다른 recovery ref를 만들었다면 명시된 임시 중단 결과가 아닌 한
  완료 판정을 중단하고 원래 변경 처분으로 돌아갑니다.

현재 Git mutation이 새 stage·commit 없이 기존 source를 push하는 경우에는 필수 CI·Sonar
검증의 동반 여부와 무관하게 worktree·index보다 먼저 `push source commit/ref`와 remote
target ref를 고정합니다. 고정한 commit에 포함되지 않은 staged·unstaged·untracked 변경은
push payload가 아니므로 제외 dirty로 보존합니다. 그 변경의 존재나 다른 actor의 진행 상태를
기다릴 이유로 사용하지 않습니다. 같은 repository나 branch 이름을 공유한다는 사실만으로
충돌을 만들지 않습니다.

고정 뒤 source ref가 다른 commit으로 이동했거나 remote target ref가 달라졌다면 실제 Git
충돌 후보입니다. Push하지 않고 이전·현재 ref와 commit, 달라지는 결과 및 현재 요청의 충돌
처리 제약을 작업 실행 workflow에 반환합니다. 현재 요청이 실제 충돌에서 사용자 결정을
요구하거나 이전 commit과 새 ref 중 대상을 유일하게 정하지 않으면, push 전에 정확한 차이를
보고하고 사용자 결정을 받습니다. 다른 작업이 끝나기를 관찰하거나 새 commit을 암묵적으로
payload에 포함해 이 충돌을 해소하지 않습니다.

같은 파일에 대상과 제외 hunk가 섞였으면 자동 partial stage하지 않습니다. source에서
안전한 분리 방법이 확정될 때까지 그 파일을 stage하지 않고 발견을 작업 실행
워크플로우로 반환합니다.

## 3. Commit patch와 subject

작업 실행 워크플로우에서 확정한 결과 단위와 dependency 순서를 그대로 commit 경계에
투영합니다. 파일 수나 요청 문장 수로 새 결과 단위를 만들지 않습니다.

Stage 전에 현재 작업 문서의 의미 단위 지도와 최종 candidate를 다시 읽고, 각 단위의
`관찰 결과·consumer·복구·검증 oracle·독립 revert → exact path·hunk` 대응을 먼저
완성합니다. `애플리케이션`, `인프라`, `문서`, module 이름이나 상위 feature 이름처럼
artifact 종류·배치 위치만 나타내는 묶음은 그 안의 모든 결과가 같은 의미 경계를 가진다는
증거가 없으면 commit 단위가 아닙니다. 이 대응 없이 broad subject를 먼저 정하거나 전체
diff를 한 commit으로 stage하지 않습니다.

1. 한 commit은 하나의 유효한 결과 단위와 그 결과에 필수인 구현·호출·test·문서를
   포함합니다.
2. exact path·hunk 전부를 그 결과에 연결하고, 반대로 결과에 필요한 hunk가 빠지지
   않았는지 확인합니다.
3. 같은 상위 목적이어도 다음 consumer, 실패 뒤 재시작 지점, retry·복구 방법 또는
   독립 revert 가능성이 다르면 별도 결과 단위입니다. 파일을 나눴다는 사실만으로
   분리하지 않지만, 이 경계가 다른 결과를 상위 목적 이름으로 다시 합치지 않습니다.
4. 독립적으로 제거했을 때 유지하려는 다른 결과가 깨지는 commit은 분리하지 않습니다.
   반대로 한 결과의 제거·복구가 다른 결과까지 되돌리게 만드는 commit은 합치지 않습니다.
5. subject는 작업 활동이 아니라 staged full patch 뒤 남는 동작·계약·구조·설명 결과를
   표현합니다.
6. subject의 각 의미 구절과 staged hunk를 양방향으로 대조합니다. `A와 B`, `A·B`,
   `A 및 B`처럼 독립 결과를 병렬 나열해야 하면 확정된 결과 단위로 돌아갑니다.
7. 각 commit의 직접 test와 문서는 그 commit이 만든 결과를 증명·설명해야 합니다. 뒤의
   거대한 통합 commit이나 최종 build가 앞 commit의 누락된 oracle을 대신하지 않습니다.

영구 규칙 변경도 파일 종류나 `규칙 변경`이라는 상위 이름으로 한 commit에 합치지 않습니다.
각 규칙의 적용 조건·관찰 결과, 최초 consumer, 실패 뒤 재시작·복구 지점과 독립 revert
경계로 나누고, 같은 규칙을 함께 성립시키는 canonical owner·adapter·skill metadata는 그
규칙의 한 결과 단위에 둡니다. 서로 독립적으로 유지·되돌릴 수 있는 기존 작업과 새 규칙은
같은 요청에서 함께 commit하더라도 별도 commit으로 기록합니다.

### 분할 반증 gate

첫 Git mutation 전에 예정 subject나 상위 작업명을 보지 않은 상태에서 final diff와 실제
source·consumer·test로 만들 수 있는 가장 이른 유효 prefix를 먼저 찾습니다. 각 변경은
독립적으로 호출·소비되는 결과, 실패 뒤 재시작·복구 지점과 revert 영향이 달라지는 지점에서
분할 후보가 됩니다. 그다음에만 예정 commit 순서와 다음 증거를 대조합니다.

```text
예정 commit
→ 하나의 관찰 결과와 exact path·hunk
→ 다음 consumer·재시작·복구·revert 경계
→ 그 commit 직후 prefix의 source·compile·직접 test 검증
→ 인접 분할 후보를 합쳤다면 더 이른 prefix가 유효할 수 없는 직접 증거
```

인접 후보를 합치는 쪽이 그 불가분성을 증명합니다. 같은 사용자 요청·상위 기능·파일,
공유 port·fixture·test 파일, 전체 test 통과와 한 문장짜리 계획은 직접 증거가 아닙니다.
더 이른 prefix가 compile·직접 test를 통과하거나 이후 결과를 제거해도 앞 결과의 consumer가
유지되면 별도 commit으로 분리합니다. 같은 파일에 순차 hunk가 있어도 격리 candidate에서
각 prefix를 재구성할 수 있으면 파일 단위로 합치지 않습니다.

History rewrite는 격리 worktree에서 모든 replacement prefix를 실제로 만들고 검증합니다.
일반 commit에서 분할 가능성이 source만으로 닫히지 않으면, 기존 dirty를 건드리지 않는 격리
candidate로 더 이른 prefix를 검증합니다. 안전하게 재구성할 수 없으면 불가분으로 추정하지
않고 더 작은 보수적 경계를 선택하거나 정확한 same-file 분리 blocker를 작업 실행
워크플로우에 반환합니다.

사용자 commentary의 예정 순서는 이 gate의 결과 projection입니다. 작성자가 그 projection을
만들었다는 사실, Conventional Commit 형식과 최종 tree의 정합성은 위 prefix 증거를 대신하지
않습니다.

Commit의 결과는 그 commit 직후의 tree입니다. Rewrite 시작 전에 이미 결함으로 확정된
상태를 중간 commit에서 다시 만든 뒤 후속 commit으로 고치는 순서는, 최종 tree가 옳고
각 subject가 서로 달라도 유효한 결과 분리가 아닙니다. 후속 요구가 새로 생겼거나
호환성·backfill처럼 중간 상태 자체가 유효한 독립 배포 결과라는 source가 없다면, 해당
수정은 최초로 그 결과를 소유하는 replacement commit에 포함합니다.

message는 최근 repository 이력을 대조한 Conventional Commit 형식을 사용합니다. type과
선택적 scope는 표준 식별자를 유지하고, subject는 한국어 명사구로 작성합니다. `검토`,
`확인`, `검증` 같은 일시적 활동은 그 규칙이나 도구 자체가 영속적으로 바뀐 경우가
아니면 subject로 사용하지 않습니다.

Type은 변경된 파일 종류나 변경 전 값이 동작하지 않았다는 사실만으로 고르지 않습니다.
Staged patch를 만들기 전에 원래 요구와 변경 전 source에서 기존 상태의 의도를 복원하고,
그 상태와 변경 뒤 결과의 관계를 분류합니다. `fix`는 기존 동작·계약이 authoritative
requirement와 어긋난 의도하지 않은 결함이고 patch가 그 결함을 복구할 때만 사용합니다.

의도적으로 실패하도록 둔 sentinel, 아직 입력이 확정되지 않은 placeholder와 비활성
기본값은 그 자체가 결함이라는 독립 근거가 없습니다. 이를 승인된 값으로 교체할 때에는
최근 repository 이력과 변경 뒤 결과에 맞는 다른 type을 선택하며, 실행 가능해졌다는
사실만으로 `fix`를 선택하지 않습니다. 반대로 기존 값이 원래 계약과 어긋났다는 source가
있으면 같은 파일·값 변경도 `fix`가 될 수 있습니다.

첫 Git mutation 전에 각 예정 subject와 `변경 전 의도 → 변경 뒤 결과 → 선택 type`을
사용자 commentary에 함께 투영합니다. 이 관계는 원래 요구와 변경 전 authoritative source에
연결해야 하며, 파일 종류·patch의 보안 효과·추가·삭제 같은 겉모양으로 채우지 않습니다.
하나의 관계로 type이 결정되지 않으면 `fix`를 기본값으로 사용하지 않고 결과 분류로
돌아갑니다. Commit 뒤 full patch를 읽을 때도 subject뿐 아니라 type이 이 관계와 일치하는지
다시 확인합니다.

## 4. Exact stage와 local commit

각 commit을 다음 순서로 만듭니다.

```sh
git add -- <exact-path>...
git diff --cached --name-status
git diff --cached --check
git diff --cached
git commit -m "<Conventional Commit message>"
```

`git add .`, `git add -A`, `git add -u`와 `git commit -a`를 사용하지 않습니다.
stage 뒤에는 cached full patch가 예정한 한 결과와 같고, 대상 path에 의도하지 않은
unstaged residual이 없으며 제외 dirty가 유지됐는지 확인합니다. commit hook을 우회하는
`--no-verify`도 사용하지 않습니다.

commit 뒤에는 self-report가 아니라 표준 Git object와 parent patch를 직접 읽습니다.

```sh
commit="$(git rev-parse HEAD^{commit})"
git cat-file -t "$commit"
git rev-parse "$commit^{tree}"
git show --stat --oneline --decorate --no-renames "$commit"
git diff-tree --no-commit-id --name-status -r --no-renames "$commit"
git show --format= --no-ext-diff --binary "$commit"
git status --short --branch
```

message, parent, tree, exact path·hunk와 제외 dirty를 예정 결과에 대조합니다. 여러 commit을
만들면 각 parent patch를 순서대로 확인하고, 모두 만든 뒤에만 전체 후보를 한 번
확인합니다.

merge commit은 single-parent `diff-tree`의 빈 출력으로 합격시키지 않습니다. parent
순서와 각 parent 기준 full patch를 추가로 읽어 source 포함과 conflict resolution을
구분합니다.

```sh
git rev-list --parents -n 1 <merge-commit>
git diff --no-ext-diff --binary <merge-commit>^1 <merge-commit>
git diff --no-ext-diff --binary <merge-commit>^2 <merge-commit>
git diff-tree -m --no-commit-id --name-status -r --no-renames <merge-commit>
```

```sh
git diff --no-ext-diff --binary <fixed-base>..HEAD
```

cumulative diff는 최초 전체 exact path·hunk 대상과 같아야 합니다. 같은 파일의 순차
patch를 단순 합집합으로 취급하거나 최종 tree 동등성만으로 중간 commit 경계를
합격시키지 않습니다.

## 5. 내용 복구와 history 복구

복구 전에 작업 실행 워크플로우에서 원하는 관찰 결과와 `worktree 내용 변경`, `새
commit`, `history rewrite` 권한을 각각 확정합니다. 표현이 모호하면 선호하는 Git 명령으로
결과를 정하지 않습니다.

### 내용만 복구

파일·hunk의 내용만 이전 상태로 바꾸는 요청은 worktree 변경입니다. 별도 commit 권한이
없으면 보상 commit을 만들지 않습니다. 현재 dirty와 겹치지 않는 exact patch로 복구하고
나머지 index·worktree·ref는 보존합니다.

### Local unpushed topology 교체

history에서 commit 흔적을 제거하되 최종 tree를 유지하는 history-only 요청은 다음
절차를 사용합니다.

1. 대상 branch의 시작 SHA, 재생 base, 제거 대상과 유지할 commit 순서를 고정합니다.
2. Rewrite 시작 전에 확정된 요구·결함과 현재 final artifact에서 다음 manifest를
   고정합니다.

   ```text
   제거할 결함·보존할 결과
   → 그 결과를 처음 도입할 replacement commit
   → final path·hunk·tree에서의 관찰값
   → 뒤 commit이 제공하는 독립 결과 또는 앞 commit의 보상 여부
   ```

   `기존 내용을 먼저 그대로 재생`하는 작업 순서나 원본 commit의 파일 묶음을 결과
   경계로 사용하지 않습니다. Rewrite 전에 이미 final 값과 결함이 알려졌다면 최초 owner
   commit이 곧바로 final 값을 도입해야 합니다.
3. 기존 dirty worktree를 건드리지 않고 임시 detached worktree에서 base부터 유지할
   commit만 순서대로 재생합니다.
4. Candidate를 최종 tree뿐 아니라 각 first-parent prefix에서 검증합니다. Rewrite 전에
   알려진 요구가 적용되는 prefix가 그 요구를 만족하는지 확인하고, 뒤 commit이 앞
   commit의 알려진 결함만 제거한다면 이를 독립 결과가 아닌 보상 commit으로 분류합니다.
   보상 commit이 하나라도 남으면 ref를 바꾸지 않고 candidate를 폐기해 2번 manifest부터
   다시 구성합니다. 새 요구, 유효한 호환성 구간이나 backfill처럼 중간 tree 자체의
   consumer·복구 경계가 source로 증명된 경우만 후속 commit으로 유지합니다.
5. Candidate의 parent·patch 순서와 최종 tree가 요구 결과와 같고, history-only 작업이면
   재작성 전 최종 tree와도 같은지 확인합니다. 최종 tree 동일성은 4번 prefix 검증을
   대신하지 않습니다.
6. target branch ref와 remote ref가 조사 때와 같은지 다시 읽습니다.
7. 현재 branch ref가 시작 SHA일 때만 `git update-ref <ref> <candidate> <start-sha>`의
   compare-and-swap으로 교체합니다.
8. 대상 commit이 branch log와 branch를 가리키는 local ref에서 사라지고, 유지 commit,
   최종 tree, 기존 dirty·index와 remote ref가 보존됐는지 확인합니다.
9. 임시 worktree가 clean하고 candidate가 target ref에 도달 가능한 뒤 표준 worktree
   remove로 정리합니다.

이 저장소의 history 복구에는 `git revert`를 사용하지 않습니다. `reset --hard`, 자동
rebase와 현재 dirty worktree에서의 강제 ref·file 교체도 사용하지 않습니다. 이미
pushed됐거나 공유된 history는 자동으로 재작성하거나 보상 commit을 만들지 않고, 실제
remote topology와 영향을 작업 실행 워크플로우에 반환합니다.

## 6. Exact branch push

push 입력이 있을 때 다음 순서로 remote mutation을 수행합니다.

1. 1절의 Git topology projection과 현재 활성 결정 source가 계속 일치하는지 확인합니다.
   새 branch·PR이 `권한 없음`이면 그 효과를 만들지 않습니다.
2. upstream, local ahead·behind, unpushed commit과 `git ls-remote --refs`의 live remote
   SHA를 확인합니다.
3. push할 source commit/ref와 remote branch를 exact하게 고정합니다. Branch tip 전체가
   대상이면 local branch ref를, 이미 확정한 commit만 대상이면 그 commit object를 source로
   사용합니다. Remote branch 생성도 입력에 포함된 경우에만 수행합니다.
4. remote tip object를 local에서 확인할 수 있으면 `git merge-base --is-ancestor`로
   고정한 source commit까지 fast-forward인지 검증합니다. 확인할 수 없는 상태를 성공으로
   추정하지 않습니다.
5. remote에 포함될 모든 unpushed commit의 message와 full patch를 읽고, 명시적으로
   제외된 결과나 보안·무결성 위반이 없는지 확인합니다. push는 이번 turn의 commit만이
   아니라 고정한 source가 도달 가능하게 만드는 commit 전체를 remote ref에 반영합니다.
6. Push 직전에 source ref와 live remote ref를 다시 읽습니다. 고정 뒤 어느 ref든 바뀌면
   이전 검증을 승계하지 않고 2절의 실제 충돌 경계로 돌아갑니다.
7. exact source와 target을 명시한 refspec으로 한 번 push합니다.

```sh
git push <remote> <source-commit-or-ref>:refs/heads/<branch>
```

`--force`, `--force-with-lease`, `--no-verify`, 자동 merge·rebase를 사용하지 않습니다.
remote가 diverged했거나 exact branch push가 제외 범위와 충돌하면 push하지 않고 그
Git 사실을 작업 실행 워크플로우에 반환합니다.

push 뒤에는 live remote ref를 고정한 expected source commit과 직접 대조합니다.

```sh
git ls-remote --refs <remote> refs/heads/<branch>
```

### Exact remote branch 삭제

Remote source branch 삭제는 해당 효과가 현재 operation map에 있고, branch 사용 종료와
안전 조건을 소유하는 [Git Worktree 워크플로우](310-git-worktree-workflow.md#6-통합-검증과-cleanup)가
모두 닫혔을 때만 수행합니다. 일반 push나 local cleanup 권한으로 이 효과를 만들지 않습니다.

1. 삭제할 `<remote>`와 `refs/heads/<source-branch>`, live remote tip의 40자리
   `<expected-sha>`를 고정합니다.
2. 고정한 SHA가 검증된 target branch에 포함됐고, source branch를 head로 사용하는 열린
   pull request나 현재 operation map의 다른 consumer·보존 요청이 없는지 확인합니다.
3. 삭제 직전에 `git ls-remote --refs`로 live remote tip이 `<expected-sha>`와 같은지 다시
   확인합니다. Ref가 없으면 already-absent로 종료하고, SHA가 다르면 삭제하지 않습니다.
4. Expected SHA를 lease로 건 exact refspec을 한 번 push합니다.

```sh
git push \
  --force-with-lease=refs/heads/<source-branch>:<expected-sha> \
  <remote> \
  :refs/heads/<source-branch>
```

이 `--force-with-lease`는 history 갱신을 강제하는 일반 push에 허용하는 옵션이 아니라,
확인 뒤 remote branch가 다른 SHA로 이동했으면 삭제를 거부하는 compare-and-swap 보호입니다.
Lease 실패를 새 조회 없이 재시도하거나 `--force`, lease 없는 `--delete`로 우회하지 않습니다.

삭제 뒤 다음 명령이 아무 ref도 반환하지 않아야 완료입니다.

```sh
git ls-remote --refs <remote> refs/heads/<source-branch>
```

Local `refs/remotes/<remote>/<source-branch>`가 남아 있으면 live 부재 확인 뒤 표준 prune으로
정리하고, local branch·worktree·다른 remote ref가 유지됐는지 확인합니다.

## 7. Pushed SHA의 CI와 Sonar

repository 품질 계약이 적용되는 code push이면 remote ref가 가리키는 40자리 exact SHA를
고정하고 다음 terminal evidence를 확인합니다. 이전 SHA나 branch 이름만 같은 실행을
현재 결과로 사용하지 않습니다.

1. exact SHA를 검증하는 required CI run과 job을 terminal 상태까지 확인하고 conclusion과
   URL을 기록합니다.
2. required CI가 성공한 뒤
   `scripts/quality/verify-sonar-status.sh --expected-revision <SHA>`를 실행합니다.
3. Sonar latest revision과 입력 SHA의 일치, 분석 시각, Quality Gate `OK`, Open issues
   `0`을 모두 확인합니다.

credential 보관·주입은 [로컬 개발 환경](../development/100-local-development.md),
CI·Sonar 판정 의미는 [코드 품질 도구](../tech-stack/310-code-quality-tools.md)와
[품질 완료 점검 스크립트](../../scripts/quality/README.md)를 따릅니다. trusted local
`.env`를 사용하면 값과 파일 내용을 출력하지 않고 격리된 subshell에만 주입합니다.

분석 대기, SHA 불일치, API·인증·응답 오류와 필수 metric 누락은 성공이 아니라 `검증
불가`입니다. Gate 실패나 Open issue는 `실패`입니다. 이 문서는 실패 뒤의 공통 발견,
후보 무효화, 재실행과 차단 횟수를 정하지 않고 증거를 작업 실행 워크플로우에
반환합니다. release, image publish, ECR과 production 적용은 별도 외부 효과이며 Git
push의 후속 단계로 자동 실행하지 않습니다.

## 8. Git 결과 인계

Git 결과는 정확성 검증과 사용자 표시를 한 record의 서로 다른 field로 인계합니다.

- `internal identity`: commit별 full SHA, parent, tree와 exact path입니다. Commit object와
  cumulative diff를 검증하는 내부 evidence이며 terminal 문장에 직접 사용하지 않습니다.
- `user receipt`: 다음 명령이 만든 `short SHA — subject — KST 시각` 한 줄입니다. 완료 보고
  workflow가 hash를 포함한 사용자 문장을 만들 때 사용하는 유일한 field입니다.

```bash
git log --format='%h — %s — %cd' --date=format-local:'%Y-%m-%d %H:%M:%S KST' <범위>
```

두 field에는 같은 commit record identity를 붙여 [완료 보고
워크플로우](140-completion-reporting-workflow.md)에 함께 인계합니다. `user receipt`를 만들기
위해 full SHA·parent·tree를 삭제하거나 줄이지 않고, 반대로 `internal identity`를 사용자
receipt로 직렬화하지 않습니다. 그 밖에 다음 Git 사실을 인계합니다.

- fixed base부터의 cumulative diff와 남은 staged·unstaged·untracked 상태
- baseline 이후 생성·변경된 stash·backup branch 등 recovery state와 그 명시된 처분
- push하지 않은 local ref 또는 exact remote·branch·SHA
- exact SHA의 required CI conclusion·URL
- Sonar revision·분석 시각·Quality Gate·Open issues 또는 검증 불가 근거

이 record와 증거는 공통 완료 판정을 대신하지 않습니다. 수행하지 않은 단계는 권한과 적용
계약에 따라 `미수행`, 실패한 단계는 `실패`, 판정할 수 없는 단계는 `검증 불가`로
남깁니다.

## 관련 문서

- **요청·권한·공통 lifecycle**: [작업 실행 워크플로우](100-work-execution-workflow.md)
- **상태 projection**: [완료 보고 워크플로우](140-completion-reporting-workflow.md)
- **branch·worktree mechanics**: [Git Worktree 워크플로우](310-git-worktree-workflow.md)
