# Workflow 목차

이 그룹은 요청의 실행, 설계, 개발, 검증과 Git 절차를 소유합니다.
모든 요청의 의미·권한은 [작업 실행 워크플로우](100-work-execution-workflow.md)에서
확정하고, 상태를 바꾸는 작업은 같은 문서의 lifecycle을 적용합니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | 공통 실행·설계·도구·보고·문서화 workflow |
| `200`~`290` | 개발·TDD·주석·test·script engineering workflow |
| `300`~`390` | Git commit·push와 worktree workflow |

## 상황별 진입점

| 상황 | 문서 |
| --- | --- |
| 모든 요청의 의미·권한과 상태 변경 작업의 시작·진행·완료 | [100-work-execution-workflow.md](100-work-execution-workflow.md) |
| 설계와 기반 구조 판단 | [110-design-workflow.md](110-design-workflow.md) |
| 질문·반론·댓글 반영 | [120-design-feedback-workflow.md](120-design-feedback-workflow.md) |
| 외부 도구와 저장소 내부 script·verifier·hook의 도입·존치·제거 | [130-tool-adoption-workflow.md](130-tool-adoption-workflow.md) |
| 완료·진행·차단·중단·인계의 상태 보고 | [140-completion-reporting-workflow.md](140-completion-reporting-workflow.md) |
| 저장소 Markdown의 작성·재구성·검증 | [150-documentation-workflow.md](150-documentation-workflow.md) |
| 코드 변경의 공통 흐름 | [200-development-workflow.md](200-development-workflow.md) |
| 기능·버그의 TDD | [210-tdd-workflow.md](210-tdd-workflow.md) |
| 코드·설정·SQL 주석 | [220-code-commenting-guideline.md](220-code-commenting-guideline.md) |
| 테스트 선택과 작성 | [230-test-writing-guide.md](230-test-writing-guide.md) |
| 저장소 실행 script의 설계·구현·검증·운영 | [240-script-engineering-guideline.md](240-script-engineering-guideline.md) |
| 명시적 stage·commit·push | [300-commit-workflow.md](300-commit-workflow.md) |
| branch·worktree·병렬 작업 | [310-git-worktree-workflow.md](310-git-worktree-workflow.md) |

## 읽기 원칙

- 요청 의미나 상태 변경을 판정할 때 공통 필수 workflow는 `100` 하나입니다.
- 작업 성격에 맞는 상세 문서만 추가로 읽습니다.
- `140`은 최종 응답 직전, 진행·차단·중단 인계 또는 전체 상태 보고 시 읽습니다.
- 문서 변경은 `150`에서 [문서 정보 구조 가이드](../100-documentation-guide.md)와 관련
  source·topology를 사용해 정보 유형·owner·구조를 판정하고 작성·검증합니다.
- Git 문서는 Git 작업이 명시됐을 때만 읽습니다.
- 사건 회고와 work item history는 현재 실행 규칙의 필수 경로가 아닙니다.
