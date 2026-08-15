# 개발 환경 목차

이 문서 그룹은 개발자가 로컬에서 프로젝트를 실행하고 검증하기 위한 준비 절차를 소유합니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | 일반 로컬 개발 환경과 project 검증 |

## 상황별 진입점

| 상황 | 문서 |
| --- | --- |
| `.env`, PostgreSQL·Gradle 검증과 push 후 Sonar 완료 점검 | [100-local-development.md](100-local-development.md) |
| 기술별 품질 기준 | [코드 품질 도구](../tech-stack/310-code-quality-tools.md) |
| commit과 push 절차 | [커밋 워크플로우](../workflow/300-commit-workflow.md) |

기술 선택의 이유는 `tech-stack`, 작업 절차는 `workflow`, 실제 외부 환경 구축 명령은
`operations`가 소유합니다.
