---
name: parallel-start
description: "현재 checkout과 main ref를 바꾸지 않고 별도 Git worktree와 local 실행 설정을 준비하는 adapter다. 사용자가 새 작업 branch 생성, 브랜치를 파서 작업, 병렬 작업 시작, 독립 작업 공간, 새 worktree 또는 다른 작업과 분리된 local 환경 생성을 요청했을 때 사용한다. workflow가 정한 branch·path·base를 bundled script에 전달해 `.claude/`와 `.env`를 내용 노출 없이 복사하고 등록·clean·ignore·원본 보존 상태를 확인한다. 기존 branch의 조회·통합·삭제·이름 변경, 실제 기능 구현, commit, push나 기존 worktree 정리만 요청한 경우에는 사용하지 않는다."
---

# 병렬 작업 시작

[Git Worktree 워크플로우](../../../docs/workflow/310-git-worktree-workflow.md)의 생성
계약을 bundled script에 전달한다. 이 skill은 생성 mechanics나 공통 권한·완료 판정을
복제하지 않는 thin adapter다.

## 1. 입력 확정

실제 디스크에서 다음을 읽는다.

1. `AGENTS.md`
2. `docs/workflow/100-work-execution-workflow.md`
3. `docs/workflow/310-git-worktree-workflow.md`
4. 후속 작업의 local 실행에 직접 필요한 development owner

작업 실행 workflow에서 worktree 생성 효과, 목표와 다음 입력을 확정한다. worktree
workflow에 따라 다음 세 값을 만든다.

- `<type>`: `feature`, `fix`, `docs`, `refactor`, `chore`, `spike` 중 목표에 맞는 값
- `<description>`: 목표 전체를 나타내는 lowercase kebab-case 이름
- `<base-ref>`: 사용자가 지정하지 않으면 source worktree의 committed `HEAD`

skill 호출, dry-run 성공과 helper metadata는 생성 권한이나 후속 구현·Git 권한을 만들지
않는다. 명시 요청 없이 `fetch`, `pull`, ref 갱신과 기존 branch·path 재사용을 하지
않는다.

새 작업 branch 요청은 branch 이름만 언급하고 worktree를 생략했어도 이 adapter로
처리한다. 현재 checkout에서 수동 Git 명령으로 branch를 생성·전환하거나 이름을 바꾸어
대체하지 않는다. Preflight가 실패하면 workflow에 blocker를 반환한다.

## 2. Script preflight

로드한 이 `SKILL.md`의 directory를 `<skill-directory>`로 사용한다. 현재 worktree에
같은 상대 경로가 있다고 추정하거나 helper mechanics를 수동 Git·copy 명령으로
재구현하지 않는다.

```sh
bash <skill-directory>/scripts/parallel-start.sh \
  --dry-run \
  --type <type> \
  --description <description> \
  --base <base-ref>
```

`PARALLEL_START_PREFLIGHT_OK`와 출력된 exact source, branch, base commit, target을
worktree workflow의 입력에 대조한다. preflight는 read-only discovery이며 실패 원인을
임의 suffix·빈 local file·다른 base로 우회하지 않는다.

## 3. 생성 실행

preflight와 같은 인자에서 `--dry-run`만 제거해 script를 한 번 실행한다.

```sh
bash <skill-directory>/scripts/parallel-start.sh \
  --type <type> \
  --description <description> \
  --base <base-ref>
```

script가 `PARALLEL_START_READY`를 출력하고 worktree workflow의 생성 postcondition을 모두
통과했을 때만 준비 완료 evidence를 반환한다. script가 rollback했으면 생성되지 않은
상태로, rollback이 불완전하면 출력된 branch·path·등록 상태를 보존한 부분 생성 상태로
반환한다. 강제 삭제, 수동 보정과 반복 생성을 시작하지 않는다.

## 4. Evidence 인계

다음을 작업 실행 workflow와 상태 projection에 전달한다.

- source worktree path·branch·HEAD
- 새 branch, exact base commit과 target 절대 경로
- `.claude/`·`.env` copy·type·permission·ignore 확인
- target clean·등록 상태
- source dirty·staged 상태와 `main` ref 보존 확인
- `cd <target-absolute-path>` handoff

stage, commit, merge, push, 실제 구현과 기존 worktree 정리를 이 adapter에서 이어서
수행하지 않고 application, DB, container, daemon, dependency와 외부 서비스를 시작하지
않는다. 같은 요청에 후속 구현이 포함됐다면 `PARALLEL_START_READY` 뒤 target path를
다음 실행 단위의 작업 root로 인계한다.
