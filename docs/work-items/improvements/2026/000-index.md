# 2026 개선 작업 문서 목록

| 번호 | 상태 | 작업 문서 | 결정·사건 기록 | 현재 요약 |
| --- | --- | --- | --- | --- |
| 001 | Blocked | [Auth Event Taxonomy 재설계](001-auth-event-taxonomy-redesign.md) | [설계](001-auth-event-taxonomy-redesign-design.md) | Flyway checksum·기존 row 호환성 확인 대기 |
| 002 | Done | [도구 거버넌스와 검증 체계 단순화](002-tooling-governance-simplification.md) | [설계](002-tooling-governance-simplification-design.md), [History](002-tooling-governance-simplification-history.md) | native image 경로와 보상 도구의 선행 경로 심사 복구 |
| 003 | In Progress | [작업 실행 추적성과 회고 구조 개편](003-work-execution-traceability.md) | [설계](003-work-execution-traceability-design.md), [History](003-work-execution-traceability-history.md) | 회고 통제 closure의 독립 판정과 실제 routing 증거 복구 진행 |
| 004 | In Progress | [문서 정보 구조와 문서화 절차 개편](004-documentation-information-architecture.md) | [설계](004-documentation-information-architecture-design.md), [History](004-documentation-information-architecture-history.md) | Procedure 실행 계약 검증 완료, Application runbook 복구 진행 |
| 005 | In Progress | [문서 시스템과 실제 문서군 전면 이관](005-documentation-system-and-corpus-migration.md) | [설계](005-documentation-system-and-corpus-migration-design.md) | 현재 source와 문서 owner의 전체 corpus 대조 진행 |
| 006 | Done | [Notification 설정 properties 경계 정리](006-notification-configuration-properties.md) | - | notification email 설정을 단일 검증된 `@ConfigurationProperties`로 통합 |
| 007 | Done | [OpenAPI 실제 응답 계약 동기화](007-openapi-response-contract.md) | - | 실제 응답 schema·보안·공통 오류와 생성 OpenAPI 자동 동기화 |
| 008 | Done | [Staging Swagger 관리자 접근 보호](008-staging-swagger-admin-access.md) | [설계](008-staging-swagger-admin-access-design.md) | staging 전용 Swagger를 기존 관리자 계정 Form Login으로 보호 |

## 관련 문서

- **상위 router**: [개선 작업 문서 목록](../000-index.md)
- **번호·상태 기준**: [작업 문서 생명주기](../../100-work-item-lifecycle.md)
