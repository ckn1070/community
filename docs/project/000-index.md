# 프로젝트 문서 목차

이 문서 그룹은 현재 저장소에 실제 적용된 영구 구조와 기술 계약을 소유합니다.
제품 목표는 `product`, 일반 원칙은 `architecture`와 `tech-stack`에서 확인합니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | 프로젝트 전체 현황 |
| `200`~`290` | 현재 적용 아키텍처, module·data 구조 지도 |
| `infrastructure/100` | Infrastructure 구조와 구성요소별 상세 SOT |
| `infrastructure/200` | CI/CD 전체 흐름과 image·GitOps 상세 SOT |
| `infrastructure/300` | Infrastructure 변경·복구 원칙 |

## 현재 문서

- [100-project-overview.md](100-project-overview.md): Repository 구현 단계와 실행 가능한 기능 기반
- [200-current-architecture.md](200-current-architecture.md): 현재 적용 아키텍처와 결정 근거
- [210-module-map.md](210-module-map.md): 실제 Java package 기준 모듈과 책임
- [220-account-auth-data-model.md](220-account-auth-data-model.md): account/auth 데이터 모델 결정
- [schema/000-index.md](schema/000-index.md): Flyway migration 기반 물리 schema reference
- [infrastructure/000-index.md](infrastructure/000-index.md): Infrastructure·CI/CD·변경과 복구 설명의 질문별 router
- [infrastructure/100-infrastructure-overview.md](infrastructure/100-infrastructure-overview.md): 전체 topology와 component·trust boundary
- [infrastructure/200-ci-cd-pipeline.md](infrastructure/200-ci-cd-pipeline.md): Push부터 CI·image publish·GitOps 배포까지의 설명 SOT

## 읽는 상황

| 확인할 내용 | 문서 |
| --- | --- |
| Repository에 지금 무엇이 구현되어 있는가 | `100-project-overview.md` |
| DDD를 어느 수준까지 적용했고 왜 modular monolith와 hexagonal 구조를 쓰는가 | [200 현재 프로젝트 아키텍처](200-current-architecture.md) |
| 기능을 어느 module에 배치하는가 | `210-module-map.md` |
| 인증 데이터의 구조적 결정 | `220-account-auth-data-model.md` |
| 정확한 table, column, constraint, index | `schema/100-account-auth.md` |
| 자동차 카탈로그의 현재 개념·관계·상태 | `../domain/130-car-catalog.md` |
| 자동차 카탈로그의 table 분리 이유와 정확한 물리 구조 | `schema/110-car-catalog.md` |
| AWS부터 Kubernetes까지 전체 구조와 책임 경계 | `infrastructure/100-infrastructure-overview.md` |
| Push부터 CI·image publish·GitOps 배포까지 전체 흐름 | `infrastructure/200-ci-cd-pipeline.md` |
| VPC·EC2·ECR/IAM·RDS·Kubernetes·Argo CD·외부 HTTPS ingress의 현재 선택과 이유 | `infrastructure/000-index.md`의 `Infrastructure 구조와 구성요소` |
| Image publish·pull과 GitOps의 상세 계약 | `infrastructure/000-index.md`의 `CI/CD 설명` |
| bootstrap 취소·admin access·credential 교체의 원리와 경계 | `infrastructure/000-index.md`의 `변경과 복구` |
| 아직 착수하지 않은 infrastructure 확장·관측·보안·자동화 후보 | [004 Infrastructure 진화 Backlog](../work-items/backlog/2026/004-infrastructure-evolution.md) |

진행 중 작업과 미래 후보는 이 그룹에 두지 않습니다.
외부 환경의 현재 live 상태와 재개 위치는
[Staging 단일 node 전체 구축 시나리오](../operations/scenarios/100-staging-single-node-bootstrap.md)가
소유합니다.
