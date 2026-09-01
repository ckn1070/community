# 문서 목차

이 문서는 저장소 문서의 최상위 router입니다.
상세 기준을 반복하지 않고, 현재 상황에서 어느 문서를 먼저 읽어야 하는지만 안내합니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | 저장소 문서 정보 구조와 공통 기준 |

## 공통 시작

- AI 작업 규칙: [AGENTS.md](../AGENTS.md)
- 요청 의미·권한과 상태 변경 lifecycle: [workflow/100-work-execution-workflow.md](workflow/100-work-execution-workflow.md)
- 문서 유형·위치·책임 판단: [100-documentation-guide.md](100-documentation-guide.md)
- 문서 작성·재구성·검증: [workflow/150-documentation-workflow.md](workflow/150-documentation-workflow.md)

## 상황별 진입점

| 상황 | 먼저 볼 문서 | 확인할 내용 |
| --- | --- | --- |
| 현재 프로젝트의 repository 구현 상태 확인 | [project/000-index.md](project/000-index.md) | 적용 아키텍처, 모듈, DB 모델과 구현된 기술 계약 |
| 업무 의미와 인증·인가 정책 확인 | [domain/000-index.md](domain/000-index.md) | 도메인 용어, 상태 전이, 정책 |
| 제품 목표와 기능 범위 확인 | [product/000-index.md](product/000-index.md) | 사용자 가치, 범위, backlog 경계 |
| 아키텍처나 API 경계 설계 | [architecture/000-index.md](architecture/000-index.md) | 구조 원칙과 설계 기준 |
| Spring, JPA, Security 등 기술 기준 확인 | [tech-stack/000-index.md](tech-stack/000-index.md) | 현재 기술과 사용 원칙 |
| 로컬 실행과 환경변수 설정 | [development/000-index.md](development/000-index.md) | 개발 환경 준비와 명령 |
| 작업 시작, 개발, 완료, Git 절차 | [workflow/000-index.md](workflow/000-index.md) | 상황별 workflow |
| Feature·Bug·Improvement·Spike·Backlog의 목표·상태·결과와 다음 task 확인 | [work-items/000-index.md](work-items/000-index.md) | Open은 `목적/배경`, Proposed·Planned는 `달성할 결과`, In Progress·Blocked·Done은 `결과`, Closed backlog는 처분 `결과` |
| 실제 환경 구축·복구·점검 | [operations/000-index.md](operations/000-index.md) | 해당 runbook의 상황별 절 |
| Staging 전체 구축의 현재 live 상태와 재개 위치 확인 | [operations/scenarios/100-staging-single-node-bootstrap.md](operations/scenarios/100-staging-single-node-bootstrap.md) | 확인한 단계, 다음 행동, blocker와 전체 checklist |

## 문서 그룹 책임

| 그룹 | 소유하는 내용 |
| --- | --- |
| `architecture` | 일반 구조 원칙과 설계 방법 |
| `tech-stack` | 기술별 사용 기준과 Best Practice |
| `project` | 현재 프로젝트 repository에 적용된 영구 구조와 기술 계약 |
| `domain` | 업무 의미와 도메인 정책 |
| `product` | 제품 목표와 기능 범위 |
| `development` | 로컬 설치, 실행, 환경변수 주입 |
| `workflow` | 작업을 수행하는 절차 |
| `work-items` | 작업별 결과, 결정 과정, 설계 과정과 진행 상태; 현재 개념은 영구 SOT로 연결 |
| `operations` | 실제 환경의 실행·복구 절차와 scenario별 현재 live 상태 |

문서 책임의 상세 기준은 [문서 정보 구조 가이드](100-documentation-guide.md)가 소유합니다.

## 읽기 원칙

- 이 문서와 관련 그룹의 `000-index.md`에서 필요한 문서만 고릅니다.
- Repository의 현재 구조·계약은 영구 SOT, 개발 작업 상태는 work item, 실제 명령은
  runbook, 외부 환경의 현재 live 상태와 재개 위치는 해당 operations scenario에서
  확인합니다.
- 링크의 관계가 필요하면 현재 기준, 결정 근거, 구현 source, 작업 상태와 실행 절차 표시를 확인합니다.
- 현재 구조·정책·제품 범위와 그 이유는 먼저 해당 영구 SOT에서 확인합니다. 작업 당시의
  선택 과정·배제 대안·trade-off가 필요할 때만 work item의 `-design`을 읽습니다.
- 과거 실패 증거가 필요할 때만 선택형 `-history`를 읽습니다.
- 지시 원문, 승인 표현과 내부 사고 과정은 문서 탐색 대상이 아니며 repository에 저장하지 않습니다.
