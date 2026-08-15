# Repository 품질 자동화

이 폴더는 repository·CI의 품질 판정 script와 contract test를 소유합니다. AWS·ECR·cluster
resource를 직접 변경하지 않습니다.

## Image 게시 변경 분류

| Script | Caller와 입력 | 결과와 경계 |
| --- | --- | --- |
| `classify-image-publishing-change.sh` | Main push `CI`가 push payload의 `before`, `after`, `github.run_attempt`, decision file path를 전달합니다. | `before..after` 전체 Git diff를 분류해 exact SHA·attempt·`publish` 세 line을 씁니다. Root push의 zero `before`는 empty tree로 바꿔 같은 경로 규칙을 적용합니다. |
| `validate-image-publishing-decision.sh` | `Publish image`의 non-privileged classify job이 trigger한 CI의 SHA·attempt, 내려받은 decision file과 `GITHUB_OUTPUT`을 전달합니다. | 일반 file·정확히 세 line·SHA·attempt·boolean을 fail-close 검증하고 `publish`만 job output으로 전달합니다. AWS credential은 사용하지 않습니다. |

Classifier는 rename을 추론하지 않고 old·new path를 각각 봅니다. 변경이 모두 `deploy/**`면
`publish=false`, 그 밖의 경로가 하나라도 있으면 `publish=true`입니다. 같은 CI run을
재실행해도 attempt가 다른 artifact를 만들고, 30일 보존 기간 안에서 exact attempt의
`Publish image` 재실행이 같은 결정을 다시 검증할 수 있습니다.

`tests/test-publish-image-workflow.sh`는 `.github/workflows/ci.yml`의 push 전체 변경 분류·
artifact 전달과 `.github/workflows/publish-image.yml`의 event·권한·native ARM64 runner·
Buildx build·QEMU 부재·immutable SHA tag·digest 출력 계약을 repository 안에서 검증합니다.
임시 Git repository에서 root deploy/source, deploy-only, source-only, multi-commit·rebase 형태,
merge, rename과 empty diff를 실행하고 SHA·attempt mismatch·추가 line·symlink도 거부하는지
확인합니다. ECR target을 구성하거나 image를 publish하지 않으므로 ECR bootstrap·verify가
아니라 repository quality test에 둡니다.

## Sonar 완료 점검

Push와 required CI가 끝난 뒤 작업자는 `verify-sonar-status.sh`로 SonarQube Cloud의 최신
`main` 분석을 **읽기 전용** 확인합니다. CI scanner·Quality Gate wait를 실행하거나 GitHub
check를 조회하는 script가 아닙니다.

## caller 선행 책임과 script 증명 범위

```text
작업자
  local 검증 → commit → main push → exact SHA의 required CI 완료 확인
  → verify-sonar-status.sh --expected-revision <same SHA>
  → PASS일 때만 Sonar 완료 조건을 충족했다고 판정
```

`main` push와 required CI 성공은 caller가 Git·GitHub에서 먼저 확인할 전제입니다. script는
branch·push·CI 상태를 조회하거나 추론하지 않습니다. script가 직접 증명하는 것은 조회
시점의 Sonar 최신 `main` analysis identity, expected SHA, Quality Gate와 Open issues뿐입니다.

## `verify-sonar-status.sh`

```bash
set -a
. ./.env
set +a
scripts/quality/verify-sonar-status.sh \
  --expected-revision <40자리-Git-SHA>
```

| 질문 | 공개 계약 |
| --- | --- |
| 누가 언제 호출하는가 | 작업자가 exact SHA의 push·required CI 완료 뒤 작업 완료를 판정할 때 local shell에서 호출합니다. CI job·Git hook은 호출하지 않습니다. |
| 입력·전제 | CLI 인자는 `--expected-revision` 하나입니다. `SONAR_TOKEN`, `SONAR_PROJECT_KEY`, curl, jq가 필요하고 `SONAR_HOST_URL` 기본값은 `https://sonarcloud.io`입니다. |
| 조회 순서 | 최신 analysis의 key·revision·시각을 읽고, 필수·참고 metric을 조회한 뒤 최신 identity를 다시 읽습니다. 앞뒤 identity가 같고 revision이 expected SHA와 exact match여야 metric을 판정합니다. |
| 필수 판정 | `alert_status == OK`, `open_issues == 0`을 요구합니다. coverage·duplication·Security Hotspot review 비율은 참고 출력이며 threshold는 Sonar Quality Gate가 소유합니다. |
| 외부 효과 | Sonar Web API를 읽기만 하며 analysis·issue 상태를 바꾸지 않습니다. polling, GitHub status 조회, Accepted·False Positive 처리를 하지 않습니다. |

### exit 의미

| exit | 결과 | 의미 |
| --- | --- | --- |
| `0` | `PASS` | 조회 전후 같은 최신 analysis가 expected revision이고 Gate `OK`, Open issues `0`임 |
| `1` | `FAIL` | exact revision은 확인했지만 Gate가 `OK`가 아니거나 Open issue가 남음 |
| `2` | `NOT VERIFIED` | 입력·도구·credential·API·응답·필수 metric 문제, revision 불일치 또는 조회 중 analysis 변경으로 판정 불가 |

`NOT VERIFIED`를 성공으로 취급하지 않습니다. `FAIL`은 문제를 고쳐 새 commit·push·분석이
끝난 뒤 새 SHA로 다시 확인하고, `NOT VERIFIED`는 identity·credential·API 원인을 먼저
복구합니다.

## token 전달 경계

`SONAR_TOKEN`은 이 script의 CLI 인자나 정상 출력에 넣지 않습니다. 하지만 구현은 curl을
실행할 때 `--header "Authorization: Bearer <token>"`을 **curl child process의 argv**로
전달합니다. 따라서 “process argument에 노출되지 않는다”고 보장하지 않습니다. shell
trace·process inspection·CI log 환경을 신뢰할 수 없는 곳에서 실행하지 않고 token을
evidence나 문서에 기록하지 않습니다.

## test가 보장하는 범위

`tests/test-verify-sonar-status.sh`는 script를 source하고 Web API 호출 없이
`evaluate_sonar_status` 함수만 fixture로 검증합니다. PASS·FAIL·NOT VERIFIED, revision
불일치, 조회 중 identity 변경과 필수 metric 결손을 검사하지만 다음은 증명하지 않습니다.

자동 caller는 없습니다. 판정 함수나 fixture 의미를 바꾼 작업자가 local shell에서 직접
실행하며, CI나 실제 Sonar API가 이 test를 호출한다고 가정하지 않습니다.

- CLI argument·환경변수 validation과 실제 `main` 함수 전체
- curl request, authorization header, Sonar endpoint·credential
- GitHub required CI 또는 실제 project metric

작업 완료 절차는 [커밋 워크플로우](../../docs/workflow/300-commit-workflow.md), 품질 기준은
[코드 품질 도구](../../docs/tech-stack/310-code-quality-tools.md)가 소유합니다. CLI·API 경계를
바꾸면 source, test, 두 문서와 이 script를 호출하는 실제 완료 절차를 함께 대조합니다.

## Local 검증

```bash
bash -n \
  scripts/quality/classify-image-publishing-change.sh \
  scripts/quality/validate-image-publishing-decision.sh \
  scripts/quality/verify-sonar-status.sh \
  scripts/quality/tests/test-verify-sonar-status.sh \
  scripts/quality/tests/test-publish-image-workflow.sh

shellcheck -x -P SCRIPTDIR \
  scripts/quality/classify-image-publishing-change.sh \
  scripts/quality/validate-image-publishing-decision.sh \
  scripts/quality/verify-sonar-status.sh \
  scripts/quality/tests/test-verify-sonar-status.sh \
  scripts/quality/tests/test-publish-image-workflow.sh

scripts/quality/tests/test-verify-sonar-status.sh
scripts/quality/tests/test-publish-image-workflow.sh
```
