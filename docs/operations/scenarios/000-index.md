# 운영 시나리오 목차

이 디렉터리는 여러 운영 runbook을 하나의 실제 환경 목표로 조합하는 end-to-end
시나리오를 소유합니다. 각 시나리오는 전체 순서·의존 관계·재개 지점과 해당 환경의
현재 live 상태를 함께 관리하며, 상세 명령과 단계별 복구는 연결된 runbook에 맡깁니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | 환경 전체 최초 구축 시나리오 |

## 현재 시나리오

| 상황 | 시작 문서 |
| --- | --- |
| 서울 리전의 staging 단일 node 환경을 처음 구축하거나 마지막 확인 지점부터 재개 | [100 Staging 단일 node 전체 구축](100-staging-single-node-bootstrap.md) |

실행 주체·목표·순서·의존 관계·복구 경계·완료 조건이 실제로 달라질 때만 새 시나리오를
검토합니다. 환경 이름이나 값만 다르다는 이유로 문서를 미리 만들지 않습니다.

## 관련 문서

- **시나리오 작성 기준**: [100 문서 정보 구조 가이드](../../100-documentation-guide.md#operations-scenario)
- **세부 운영 절차**: [000 운영 문서 목차](../000-index.md)
- **영구 시스템 구조**: [100 Infrastructure overview](../../project/infrastructure/100-infrastructure-overview.md)
