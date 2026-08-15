# 프로젝트 문서 목차

이 문서 그룹은 현재 저장소에 실제 적용된 영구 구조와 기술 계약을 소유합니다.
제품 목표는 `product`, 일반 원칙은 `architecture`와 `tech-stack`에서 확인합니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | 프로젝트 전체 현황 |
| `200`~`290` | 현재 적용 아키텍처, module·data 구조 지도 |
| `300`~`390` | 현재 infrastructure와 delivery system 개념·생애주기 지도 |

## 현재 문서

- [100-project-overview.md](100-project-overview.md): Repository 구현 단계와 실행 가능한 기능 기반
- [200-current-architecture.md](200-current-architecture.md): 현재 적용 아키텍처와 결정 근거
- [210-module-map.md](210-module-map.md): 실제 Java package 기준 모듈과 책임
- [220-account-auth-data-model.md](220-account-auth-data-model.md): account/auth 데이터 모델 결정
- [schema/000-index.md](schema/000-index.md): Flyway migration 기반 물리 schema reference
- [300-infrastructure-system.md](300-infrastructure-system.md): AWS부터 Kubernetes·image·GitOps까지의 전체 시스템 관계
- [infrastructure/000-index.md](infrastructure/000-index.md): 최초 구축·반복 delivery·변경과 복구별 infrastructure 개념

## 읽는 상황

| 확인할 내용 | 문서 |
| --- | --- |
| Repository에 지금 무엇이 구현되어 있는가 | `100-project-overview.md` |
| 왜 modular monolith와 hexagonal 구조를 쓰는가 | `200-current-architecture.md` |
| 기능을 어느 module에 배치하는가 | `210-module-map.md` |
| 인증 데이터의 구조적 결정 | `220-account-auth-data-model.md` |
| 정확한 table, column, constraint, index | `schema/100-account-auth.md` |
| AWS부터 Kubernetes·delivery까지 전체 흐름과 책임 경계 | `300-infrastructure-system.md` |
| VPC·EC2·ECR/IAM·RDS·Kubernetes·Argo CD의 현재 선택과 이유 | `infrastructure/000-index.md`의 `최초 구축` |
| image publish·pull과 GitOps 배포가 평소 어떻게 반복되는가 | `infrastructure/000-index.md`의 `반복 delivery` |
| bootstrap 취소·admin access·credential 교체의 원리와 경계 | `infrastructure/000-index.md`의 `변경과 복구` |

진행 중 작업과 미래 후보는 이 그룹에 두지 않습니다.
외부 환경의 현재 live 상태와 재개 위치는
[Staging 단일 node 전체 구축 시나리오](../operations/scenarios/100-staging-single-node-bootstrap.md)가
소유합니다.
