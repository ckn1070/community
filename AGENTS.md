# AGENTS.md

이 파일은 저장소 작업의 최상위 불변식과 진입점만 소유합니다. 요청 해석, 권한,
실행 단위와 완료 판정의 상세 절차는
[작업 실행 워크플로우](docs/workflow/100-work-execution-workflow.md)가 소유합니다.

## 시작

1. 이 파일과 [문서 목차](docs/000-index.md)를 읽습니다.
2. 새 사용자 메시지마다 첫 commentary를 포함한 답변·도구 호출·review·mutation 전에
   작업 실행 워크플로우의 Bootstrap으로 다시 진입합니다. Bootstrap 판정 전에는 과거
   작업이나 pending 효과의 재개·정리·조사·검증을 약속하거나 시작하지 않습니다.
3. 사용자가 사용할 수 있는 skill을 이름·tag·link로 명시하면, 첫 실질 답변·판단·계획
   전에 해당 `SKILL.md` 전체와 필수 공통 owner를 읽습니다. 그 전 안내는 읽을 source와
   source read가 선행한다는 사실만 말하며 specialist·mode·효과·terminal을 확정하지
   않습니다.
4. 메시지에 작업 주체·범위·권한·안전성·사실에 대한 이의와 상태 질문이 함께 있으면
   이의 대상의 pending 효과와 과거 실행 권한을 먼저 중단합니다. 그 상태 질문은 중단한
   권한을 유지·부활시키지 않으며, 같은 메시지의 독립된 명시 실행 요청만 따로 판정합니다.
5. 모든 사용자 요청은 작업 실행 워크플로우에서 대상·문장 기능·권한·완료 조건을 확정합니다.
6. 관련 그룹의 `000-index.md`에서 필요한 문서와 skill만 선택합니다.
7. 상태를 바꾸기 전에 기존 Git 변경과 이번 작업 범위를 분리합니다.

문서 구조 자체가 작업 대상일 때만 해당 corpus를 전수 조사합니다. 평상시에는 모든
workflow와 history를 기본으로 읽지 않습니다.

## 최상위 불변식

- 접근 가능한 원문과 authoritative source에서 정확한 대상을 먼저 고정합니다. 지시어,
  선택문이나 생략을 주변 주제·기억·비슷한 이름으로 채우지 않습니다.
- 복합 요청은 대상, 문장 기능, 결과, 제약, 권한과 순서가 독립적인 원자로 나눕니다.
  질문을 실행 요청으로 확대하거나 실행 요청을 질문으로 축소하지 않습니다.
- 사용자 요구, 제약·제외, 우려·평가 기준과 Agent의 추천을 권위별로 분리합니다.
  후속 메시지는 명시적으로 바꾼 의미만 갱신합니다.
- 과거 요청, 내부 plan, pending operation과 도구의 승인·거절은 현재 메시지의 상태 변경
  효과 권한으로 승계하지 않습니다. 현재 메시지가 독립적으로 명시했거나 작업 실행
  워크플로우가 현재 generation에서 허용한 효과만 실행합니다.
- skill 이름·tag·link는 절차를 선택할 뿐 상태 변경 권한을 만들지 않습니다.
- 현재 요청의 대상·문장 기능·결과·권한 판정은 현재 Agent가 원문에서 직접 수행하며,
  별도 reviewer·dispatcher에 이 기본 책임을 넘기지 않습니다. fresh 독립 검토는 관련
  domain workflow가 산출물의 종류와 위험에 따라 요구할 때만 적용하며, 사용자 권한을
  만들거나 현재 요청의 직접 판정을 대체하지 않습니다. 공통 권한·상태·완료 알고리즘은
  작업 실행 워크플로우 한 곳만 소유합니다.
- 큰 작업은 의존 순서가 있는 검증 가능한 결과 단위로 닫습니다. 공유 계약과 첫 대표
  단위가 검증되기 전에 그 계약을 소비하는 작업을 확장하지 않습니다.
- 병렬 작업은 입력·수용 기준·증거와 write set이 독립적일 때만 허용합니다. 공유 계약,
  producer와 consumer, 동결 후보를 다시 여는 검토는 직렬화합니다.
- 기존 dirty 변경과 다른 작업자의 변경을 되돌리거나 이번 결과에 포함하지 않습니다.
- 운영 안내에 사용하는 runbook은 현재 authoritative source를 반영한 재사용 가능한 실행
  문서여야 합니다. Source 대조로 runbook 자체의 결함을 직접 확정하면 어떤 mutation이나
  우회 명령보다 먼저 별도 commentary에서 exact 결함·관찰 결과·중단 지점을 사용자에게
  알립니다. Source를 읽겠다는 사전 안내는 이 통지로 계산하지 않습니다. 결함 발견이나
  수정 금지의 부재는 상태 변경 권한이 아닙니다. 현재 원문이 canonical runbook 수정을
  명시적으로 요청해 작업 실행 워크플로우의 권한 gate를 통과한 경우에만
  [150 문서화 워크플로우의 runbook 결함 복구 gate](docs/workflow/150-documentation-workflow.md#runbook-결함-복구-gate)로
  canonical 원문을 수정·검증한 뒤 수정된 실행 단위를 그대로 인용합니다. 질문·설명·검토
  요청이면 결함과 안전한 후속만 답하고 mutation하지 않습니다.
- 실패나 누락을 발견하면 사건의 명사를 규칙으로 추가하지 않습니다. 작업 실행
  워크플로우의 통제 생애주기로 첫 단절과 기존 방어를 먼저 판정합니다.
- 완료·검증된 방어 범위의 재발은 [심화 회고](.agents/skills/deep-retrospective/SKILL.md),
  그 밖의 지시 위반·잘못된 판단은 [회고](.agents/skills/retrospective/SKILL.md)로
  조사합니다. 문제 제기와 회고 뒤에는 저장한 원래 작업의 복귀점으로 돌아갑니다.

## 언어

- 사용자 응답과 저장소 문서는 기본적으로 한국어와 존댓말을 사용합니다.
- 코드, 설정, SQL과 script 주석은 한국어로 작성합니다.
- 식별자, 표준 용어, 명령과 경로는 원래 표기를 유지합니다.

## 작업별 진입점

| 작업 | canonical source |
| --- | --- |
| 요청·권한·실행·완료 | [작업 실행 워크플로우](docs/workflow/100-work-execution-workflow.md) |
| 설계와 기반 구조 | [설계 워크플로우](docs/workflow/110-design-workflow.md) |
| 피드백 의미 반영 | [설계 피드백 워크플로우](docs/workflow/120-design-feedback-workflow.md) |
| 외부·내부 도구 도입과 제거 | [도구 도입 워크플로우](docs/workflow/130-tool-adoption-workflow.md) |
| 상태·완료 보고 | [완료 및 작업 보고 워크플로우](docs/workflow/140-completion-reporting-workflow.md) |
| 문서 구조와 작성 | [문서 정보 구조 가이드](docs/100-documentation-guide.md), [문서화 워크플로우](docs/workflow/150-documentation-workflow.md) |
| 기능·버그·도메인 개발 | [개발 워크플로우](docs/workflow/200-development-workflow.md), [TDD](docs/workflow/210-tdd-workflow.md) |
| 코드·설정·SQL 주석 | [주석 작성 가이드](docs/workflow/220-code-commenting-guideline.md) |
| 테스트 선택과 작성 | [테스트 작성 가이드](docs/workflow/230-test-writing-guide.md) |
| 저장소 실행 script 설계·구현·검증·운영 | [스크립트 엔지니어링 가이드](docs/workflow/240-script-engineering-guideline.md) |
| stage·commit·push | [커밋 워크플로우](docs/workflow/300-commit-workflow.md) |
| branch·worktree | [Git Worktree 워크플로우](docs/workflow/310-git-worktree-workflow.md) |

실제 운영 명령은 `docs/operations` runbook이 소유합니다. 환경변수 계약을 바꾸면
`.env.example`과 `docs/development/100-local-development.md`를 함께 확인합니다.
Domain에는 업무 규칙과 불변식을 소유하는 모델만 두며 request, response와 persistence
payload를 두지 않습니다.

## Skill과 문서

- 저장소 코드·설정·실행 흐름의 뜻·차이·lifecycle을 설명하거나, 이전 설명의
  누락·모순 때문에 같은 질문을 다시 받으면 `$explain-tech`를 사용합니다.
- 저장소 Markdown을 변경하면 `$documentation`을 사용하고 문서 정보 구조와 문서화
  워크플로우를 적용합니다.
- `.agents/skills/**`를 변경하면 `$skill-creator`를 적용합니다. 변경한 각
  `SKILL.md` 전체와 대응 `agents/openai.yaml`의 trigger·description·형식을
  대조하고, 공통 validator는 변경한 skill에만 실행합니다.
- router는 진입점만, 영구 SOT는 현재 규칙과 사실만 소유합니다. 대화 원문, 내부 추론,
  dirty snapshot과 Git으로 복원 가능한 변경 기록을 저장소 문서에 복사하지 않습니다.
- Markdown 변경의 source·owner·링크 감사와 fresh 독립 검토 범위는 문서화
  워크플로우를 따릅니다.

## Git과 완료

- Git 상태 변경은 작업 실행 워크플로우에서 권한을 확정한 뒤 커밋 워크플로우로
  수행합니다. stage, commit과 push 권한은 서로 대체하지 않습니다.
- 구현·검증 phase는 Git index·ref를 직접 변경하지 않습니다. 같은 요청에 commit이
  포함돼 있어도 후보를 동결한 뒤 별도 Git phase로 전환해
  [Git 마무리](.agents/skills/git-finalize/SKILL.md)를 호출합니다.
- 첫 local·remote ref mutation 또는 pull request API 호출 전에는 현재 사용자 원문의
  positive source를 [커밋 워크플로우의 Git topology projection](docs/workflow/300-commit-workflow.md#1-입력과-git-효과)에
  결속해 `push source → remote target`, 새 branch와 PR의 생성 여부·권한을 사용자
  commentary에 먼저 투영합니다. `commit`·`push`, runbook의 운영 정책, 도구의 기본 prefix는
  새 branch나 PR 권한을 만들지 않으며, 이 projection이 없으면 관련 도구를 호출하지 않습니다.
  승인된 새 branch는 raw SHA의 remote ref 생성도 포함해
  [Git Worktree 워크플로우의 Naming과 배치](docs/workflow/310-git-worktree-workflow.md#naming과-배치)에
  이름을 대조한 뒤에만 생성합니다.
- 첫 `git add`·`git commit`과 같은 generation에서 커밋 워크플로우와 Git 마무리를 실제로
  읽고, 현재 final diff의 결과 단위를 다음 consumer·복구·revert 경계로 다시 분류합니다.
  그 결과로 예정 commit 순서와 exact path·hunk 귀속을 사용자 commentary에 투영합니다.
  과거 turn의 read·plan, 상위 작업명, 전체 diff label과 test 통과는 현재 Git phase의
  invocation이나 commit 경계 증거가 아닙니다. 이 phase 인계와 투영이 없으면 Git mutation을
  호출하지 않습니다.
- 도구의 승인·중단·취소 상태는 artifact의 실제 변경 상태가 아닙니다. 변경 여부를
  보고하기 전에 Git ref·index·worktree·외부 상태의 authoritative source를 읽고,
  확인하지 못한 상태를 변경 없음으로 확정하지 않습니다.
- 검증은 실제 source와 실행 결과를 사용합니다. 실행하지 못한 검증을 성공으로
  추정하거나 하위 결과를 전체 완료로 보고하지 않습니다.
- 코드 작업은 push 요청 여부와 무관하게 final exact SHA의 required CI와 Sonar
  revision·Quality Gate·Open issues gate까지 전체 완료 조건으로 유지합니다. push
  권한이 없으면 local 결과와 전체 미완료를 구분합니다.
- 현재 권한과 환경에서 안전하게 계속할 필수 작업이 남으면 중간 보고로 종료하지
  않습니다. 최종 상태와 사용자 책임은 완료 및 작업 보고 워크플로우로 투영합니다.
