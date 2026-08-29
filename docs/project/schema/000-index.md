# Project Schema Reference 목차

이 문서 그룹은 Flyway migration을 적용한 뒤의 현재 DB schema 최종 상태를 정리합니다.
Flyway migration 파일이 source of truth이고, 이 문서 그룹은 여러 migration에 걸친 최종 table 상태를 사람이 읽기 쉽게 모은 reference입니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | bounded context별 현재 물리 schema reference |

## 현재 문서

- [100-account-auth.md](100-account-auth.md): account/auth 관련 table, column, constraint, index, check value, seed data의 현재 최종 상태입니다.
- [110-car-catalog.md](110-car-catalog.md): 자동차 제품 계층·동력 장치·판매 구성·가격·옵션·인증 11개 테이블의 현재 최종 상태입니다.

## 작성 기준

- schema reference는 migration history가 아니라 현재 최종 상태를 소유합니다.
- table, column, constraint, index, enum-like check value, seed data 변경 migration이 추가되면 관련 reference도 같은 작업에서 갱신합니다.
- 아직 실제 schema가 없는 영역의 빈 문서는 만들지 않습니다.
- reference와 migration이 충돌하면 migration을 기준으로 보고 reference를 갱신합니다.

## 관련 문서

- **상위 프로젝트 router**: [프로젝트 문서 목차](../000-index.md)
- **논리 데이터 모델**: [Account 인증/인가 데이터 모델](../220-account-auth-data-model.md),
  [신차 카탈로그 DB 설계](../../work-items/features/2026/015-new-car-catalog-query-api-design.md)
- **migration 기준**: [Flyway](../../tech-stack/240-flyway.md)
