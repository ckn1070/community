#!/bin/sh
# 저장소 workflow가 확정한 branch와 worktree를 생성하고 격리 상태를 검증합니다.
set -eu

usage() {
  cat >&2 <<'EOF'
usage:
  parallel-start.sh [--dry-run] --type <type> \
    --description <short-kebab-description> [--base <ref>]

The default base is the committed HEAD of the current worktree.
EOF
}

fail() {
  echo "parallel-start: $*" >&2
  exit 1
}

mode="create"
branch_type=""
description=""
base_ref="HEAD"

while [ "$#" -gt 0 ]; do
  case "$1" in
    --dry-run)
      mode="dry-run"
      shift
      ;;
    --type)
      [ "$#" -ge 2 ] || {
        usage
        exit 2
      }
      branch_type="$2"
      shift 2
      ;;
    --description)
      [ "$#" -ge 2 ] || {
        usage
        exit 2
      }
      description="$2"
      shift 2
      ;;
    --base)
      [ "$#" -ge 2 ] || {
        usage
        exit 2
      }
      base_ref="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

[ -n "$branch_type" ] || fail "--type이 필요합니다"
[ -n "$description" ] || fail "--description이 필요합니다"
[ -n "$base_ref" ] || fail "--base는 비어 있을 수 없습니다"

case "$branch_type" in
  feature|fix|docs|refactor|chore|spike) ;;
  *) fail "허용되지 않은 branch type입니다: $branch_type" ;;
esac

printf '%s\n' "$description" |
  grep -Eq '^[a-z0-9]+(-[a-z0-9]+)*$' ||
  fail "description은 lowercase kebab-case여야 합니다: $description"

command -v git >/dev/null 2>&1 || fail "git을 찾을 수 없습니다"
[ -x /usr/bin/ditto ] || fail "/usr/bin/ditto를 실행할 수 없습니다"
[ -x /bin/cp ] || fail "/bin/cp를 실행할 수 없습니다"

source_root="$(git rev-parse --show-toplevel 2>/dev/null)" ||
  fail "현재 경로는 Git worktree가 아닙니다"
source_root="$(cd "$source_root" && pwd -P)"

common_dir="$(git -C "$source_root" rev-parse --path-format=absolute --git-common-dir)"
common_dir="$(cd "$common_dir" && pwd -P)"
[ "$(basename "$common_dir")" = ".git" ] ||
  fail "primary repository의 common Git directory를 복원할 수 없습니다"

primary_root="$(dirname "$common_dir")"
repository_name="$(basename "$primary_root")"
worktree_parent="$(dirname "$primary_root")/worktrees"
target_path="$worktree_parent/$repository_name-$description"
branch_name="$branch_type/$description"

git check-ref-format --branch "$branch_name" >/dev/null ||
  fail "유효하지 않은 branch 이름입니다: $branch_name"

base_commit="$(git -C "$source_root" rev-parse --verify "${base_ref}^{commit}" 2>/dev/null)" ||
  fail "local commit으로 해석할 수 없는 base입니다: $base_ref"

[ -d "$source_root/.claude" ] && [ ! -L "$source_root/.claude" ] ||
  fail "원본 .claude는 symlink가 아닌 directory여야 합니다"
[ -f "$source_root/.env" ] && [ ! -L "$source_root/.env" ] ||
  fail "원본 .env는 symlink가 아닌 regular file이어야 합니다"

tracked_local_paths="$(git -C "$source_root" ls-files -- .claude .env)"
[ -z "$tracked_local_paths" ] ||
  fail "원본 .claude 또는 .env가 Git 추적 대상입니다"

unignored_local_paths="$(
  git -C "$source_root" ls-files --others --exclude-standard -- .claude .env
)"
[ -z "$unignored_local_paths" ] ||
  fail "원본 local 실행 파일에 ignore되지 않은 path가 있습니다"

if git -C "$source_root" show-ref --verify --quiet "refs/heads/$branch_name"; then
  fail "branch가 이미 존재합니다: $branch_name"
fi

remote_branch_refs="$(
  git -C "$source_root" for-each-ref \
    --format='%(refname)' "refs/remotes/*/$branch_name"
)"
[ -z "$remote_branch_refs" ] ||
  fail "같은 이름의 remote-tracking branch가 이미 존재합니다: $branch_name"

if [ -e "$target_path" ] || [ -L "$target_path" ]; then
  fail "worktree target path가 이미 존재합니다: $target_path"
fi

registered_target="$(
  git -C "$source_root" worktree list --porcelain |
    sed -n 's/^worktree //p' |
    grep -Fx "$target_path" || true
)"
[ -z "$registered_target" ] ||
  fail "worktree target path가 Git에 이미 등록되어 있습니다: $target_path"

source_branch="$(git -C "$source_root" symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
source_head="$(git -C "$source_root" rev-parse HEAD)"
main_before="$(git -C "$source_root" rev-parse --verify refs/heads/main 2>/dev/null || true)"

temp_dir="$(mktemp -d)"
source_status_before="$temp_dir/source-status-before"
git -C "$source_root" status --porcelain=v1 -uall > "$source_status_before"

parent_created="false"
worktree_created="false"
ready="false"

rollback_created_worktree() {
  if [ "$worktree_created" != "true" ]; then
    return
  fi

  rollback_safe="true"
  target_head="$(git -C "$target_path" rev-parse HEAD 2>/dev/null || true)"
  target_branch="$(git -C "$target_path" symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
  unexpected_paths="$(
    git -C "$target_path" status --porcelain=v1 -uall -- \
      . ':(exclude).claude' ':(exclude).env' 2>/dev/null || true
  )"

  [ "$target_head" = "$base_commit" ] || rollback_safe="false"
  [ "$target_branch" = "$branch_name" ] || rollback_safe="false"
  [ -z "$unexpected_paths" ] || rollback_safe="false"

  if [ "$rollback_safe" != "true" ]; then
    echo "parallel-start: 안전 조건을 만족하지 않아 부분 생성 상태를 보존합니다: $target_path" >&2
    return
  fi

  if git -C "$source_root" worktree remove --force "$target_path"; then
    git -C "$source_root" update-ref -d "refs/heads/$branch_name" "$base_commit" ||
      echo "parallel-start: rollback 중 branch ref 제거에 실패했습니다: $branch_name" >&2
    echo "parallel-start: 생성 뒤 검증 실패로 새 worktree를 rollback했습니다" >&2
  else
    echo "parallel-start: worktree rollback에 실패해 부분 생성 상태를 보존합니다: $target_path" >&2
  fi
}

cleanup() {
  exit_status=$?
  trap - EXIT HUP INT TERM

  if [ "$exit_status" -ne 0 ] && [ "$ready" != "true" ]; then
    rollback_created_worktree
  fi

  rm -rf "$temp_dir"
  if [ "$parent_created" = "true" ] && [ -d "$worktree_parent" ]; then
    rmdir "$worktree_parent" 2>/dev/null || true
  fi

  exit "$exit_status"
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

echo "source=$source_root"
echo "branch=$branch_name"
echo "base_ref=$base_ref"
echo "base_commit=$base_commit"
echo "target=$target_path"

if [ "$mode" = "dry-run" ]; then
  echo "PARALLEL_START_PREFLIGHT_OK"
  exit 0
fi

if [ ! -d "$worktree_parent" ]; then
  mkdir -p "$worktree_parent"
  parent_created="true"
fi

git -C "$source_root" worktree add "$target_path" -b "$branch_name" "$base_commit"
worktree_created="true"

/usr/bin/ditto "$source_root/.claude" "$target_path/.claude"
/bin/cp -p "$source_root/.env" "$target_path/.env"

[ -d "$target_path/.claude" ] && [ ! -L "$target_path/.claude" ] ||
  fail "새 worktree의 .claude type 검증에 실패했습니다"
[ -f "$target_path/.env" ] && [ ! -L "$target_path/.env" ] ||
  fail "새 worktree의 .env type 검증에 실패했습니다"

source_env_mode="$(stat -f '%Lp' "$source_root/.env")"
target_env_mode="$(stat -f '%Lp' "$target_path/.env")"
[ "$source_env_mode" = "$target_env_mode" ] ||
  fail ".env 권한 보존 검증에 실패했습니다"

target_tracked_paths="$(git -C "$target_path" ls-files -- .claude .env)"
[ -z "$target_tracked_paths" ] ||
  fail "새 worktree의 local 실행 파일이 Git 추적 대상입니다"

target_unignored_paths="$(
  git -C "$target_path" ls-files --others --exclude-standard -- .claude .env
)"
[ -z "$target_unignored_paths" ] ||
  fail "새 worktree의 local 실행 파일에 ignore되지 않은 path가 있습니다"

target_ignored_paths="$(
  git -C "$target_path" ls-files --others -i --exclude-standard -- .claude .env
)"
printf '%s\n' "$target_ignored_paths" | grep -Fx '.env' >/dev/null ||
  fail "새 worktree의 .env ignore 상태를 확인할 수 없습니다"

target_branch="$(git -C "$target_path" symbolic-ref --quiet --short HEAD)"
[ "$target_branch" = "$branch_name" ] ||
  fail "새 worktree branch가 예상과 다릅니다: $target_branch"

target_head="$(git -C "$target_path" rev-parse HEAD)"
[ "$target_head" = "$base_commit" ] ||
  fail "새 worktree HEAD가 base commit과 다릅니다"

target_status="$(git -C "$target_path" status --short)"
[ -z "$target_status" ] ||
  fail "새 worktree가 clean 상태가 아닙니다"

git -C "$source_root" worktree list --porcelain |
  awk -v expected_path="$target_path" -v expected_branch="refs/heads/$branch_name" '
    function finish_record() {
      if (path == expected_path && branch == expected_branch) {
        found = 1
      }
      path = ""
      branch = ""
    }
    /^worktree / {
      finish_record()
      path = substr($0, 10)
      next
    }
    /^branch / {
      branch = substr($0, 8)
      next
    }
    /^$/ {
      finish_record()
    }
    END {
      finish_record()
      exit(found ? 0 : 1)
    }
  ' ||
  fail "Git worktree 등록 상태가 예상과 다릅니다"

source_branch_after="$(git -C "$source_root" symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
source_head_after="$(git -C "$source_root" rev-parse HEAD)"
main_after="$(git -C "$source_root" rev-parse --verify refs/heads/main 2>/dev/null || true)"
source_status_after="$temp_dir/source-status-after"
git -C "$source_root" status --porcelain=v1 -uall > "$source_status_after"

[ "$source_branch_after" = "$source_branch" ] ||
  fail "원본 worktree branch가 변경되었습니다"
[ "$source_head_after" = "$source_head" ] ||
  fail "원본 worktree HEAD가 변경되었습니다"
[ "$main_after" = "$main_before" ] ||
  fail "main branch ref가 변경되었습니다"
cmp -s "$source_status_before" "$source_status_after" ||
  fail "원본 worktree의 dirty 또는 staged 상태가 변경되었습니다"

ready="true"
echo "local_paths=.claude,.env"
echo "source_preserved=true"
echo "main_preserved=true"
echo "target_clean=true"
echo "target_registered=true"
echo "PARALLEL_START_READY"
