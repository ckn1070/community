# 문서 시스템과 실제 문서군 전면 이관

## 상태

In Progress

## 결과

- 문서 유형별 canonical owner, source↔문서 양방향 대조와 중대한 topology 변경의 fresh
  proposal·final 감사 기준은 영구 SOT에 반영했습니다.
- Git으로 복원 가능한 작업 inventory·삭제 이력·검증 반복 기록을 영구 문서에 두지 않는
  경계를 유지합니다.
- 배포 인프라 제거에 맞춰 script router와 배포 README·현재 구조 지도·빈 runbook을
  제거했고, 남은 `scripts/quality` 계약은 실제 완료 caller와 일치시켰습니다.
- 현재 요청은 제거된 배포 문서군의 source·router·link closure를 닫지만, 애플리케이션·
  domain·architecture를 포함한 전체 corpus의 독립 의미 감사는 아직 완료하지 않았습니다.

## 미완료 결과

| 남은 결과 | 미완료 사유 | 완료 조건 |
| --- | --- | --- |
| 전체 corpus의 현재 source 일치 | 이번 변경은 제거된 배포 문서군과 직접 consumer만 닫음 | 나머지 owner 단위를 actual source에서 다시 대조하고 fresh final 감사 통과 |

## 범위

- `AGENTS.md`, 문서 가이드와 작성·보고·개발 workflow
- product, project, architecture, domain, development, operations, tech-stack와 work item 문서군
- router, canonical owner, inbound link와 번호 topology

애플리케이션 기능 구현과 실제 AWS·GitHub 외부 상태 변경은 이 문서 작업의 범위가 아닙니다.

## 확정 결정 색인

[상세 설계](005-documentation-system-and-corpus-migration-design.md)는 다음 판단을 보존합니다.

- 독자 질문과 정보 관계에서 문서 유형·표현을 선택하는 기준
- 작업 inventory와 영구 owner를 분리하는 기준
- 중대한 topology proposal과 동결 후보의 fresh 감사 경계
- 삭제·이동 시 source, owner, router, inbound link와 번호를 함께 닫는 기준

## 영구 SOT 승격

- [문서 정보 구조 가이드](../../../100-documentation-guide.md)
- [문서화 워크플로우](../../../workflow/150-documentation-workflow.md)
- [작업 문서 생명주기](../../100-work-item-lifecycle.md)
- [작업 실행 워크플로우](../../../workflow/100-work-execution-workflow.md)
- [완료 및 작업 보고 워크플로우](../../../workflow/140-completion-reporting-workflow.md)

## 관련 문서

- **결정 근거**: [상세 설계](005-documentation-system-and-corpus-migration-design.md)
- **선행 문서 구조 결정**: [문서 정보 구조 개편](004-documentation-information-architecture.md)
- **현재 문서 router**: [문서 목차](../../../000-index.md)
