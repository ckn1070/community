# 프로젝트 개요

이 문서는 현재 저장소의 구현 단계와 검증 가능한 기술 상태를 요약합니다.
제품 목표와 미래 범위는 [제품 개요](../product/100-product-overview.md)가 소유합니다.

## 현재 단계

Carmunity backend는 프로젝트 골격을 만드는 초기 상태를 지나 account/auth 기반 기능이 구현된 단계입니다.
[016 신차 카탈로그 schema·조회 API](../work-items/features/2026/016-new-car-catalog-query-api.md)는
주요 자동차 정보·과거 차량·관계형 저장과 schema 우선 개발 요구를 브랜드·모델·세대·연식,
동력계통·복수 장치·기술 변형·판매 구성·가격 이력·옵션과 조건별 인증을 담는
11개 테이블 및 공개 조회 계약으로 구체화한 `In Progress` 작업입니다. 현재 `catalog` 모듈 공개
enum, Flyway V13~V17의 11개 테이블, 실제 PostgreSQL schema·대표 fixture와
runtime statement 기반 SELECT-only 권한 검증이 구현됐습니다. 다음 단계인 조회 persistence와
API는 아직 구현되지 않았습니다. 실제 브랜드·과거
범위와 production data는
[017 자동차 카탈로그 초기 데이터 구축](../work-items/features/2026/017-car-catalog-initial-data.md)의
별도 `Proposed` 작업이며 U2-C를 택하면 discovery 뒤 exact 범위의 2차 사용자 결정이 필요합니다.
커뮤니티 제품 기능도 아직 backlog입니다. 회원과 관리자 인증·인가
기반은 실행 가능한 코드와 migration, test로 존재합니다.

## 구현된 기능 기반

- account, profile, password credential, role assignment
- 일반 회원 가입과 이메일 인증
- 비밀번호 로그인, JWT access token과 opaque refresh token lifecycle
- refresh token rotation, reuse detection, logout
- 비밀번호 변경과 비밀번호 재설정
- 최초 최고 관리자 bootstrap
- 관리자 초대 생성, 수락, 회수
- 인증·인가 감사 이벤트
- 공통 API 오류 응답, Security baseline, request id와 structured logging

기능별 결과는 [2026 기능 작업 문서](../work-items/features/2026/000-index.md)에서 확인합니다.

## 현재 기술 상태

- Java 21과 Spring Boot 4 기반 단일 애플리케이션
- `account`, `auth`, `notification`, `catalog`, `shared`, `config` package
- Spring Modulith와 ArchUnit module boundary 검증
- PostgreSQL과 Flyway V1~V17 schema
- Spring Security Resource Server 기반 stateless Bearer JWT
- Resend 기반 이메일 발송 adapter
- Gradle check, Spotless, JaCoCo, SonarQube Cloud 품질 흐름
- pull request와 `main` push에서 application 품질만 검증하는 최소 GitHub Actions CI와,
  성공한 main CI 중 `deploy/**` 전용 변경을 제외하고 실행되는 별도 privileged image
  publish workflow
- Terraform은 없으며 AWS resource를 수동으로 구축합니다. Ubuntu host부터 Kubernetes
  `1.36.2` control plane·Calico `3.32.1` network, application DB role·Secret bootstrap과
  staging workload manifest를 구현했습니다.
- 성공한 `main` CI revision 중 `deploy/**` 전용 변경을 제외해 private ECR `car-community`의
  `linux/arm64` image로 게시하는 GitHub workflow와 EC2 instance profile을 사용하는 kubelet
  ECR credential provider·exact digest smoke script가 구현됐습니다.
- Argo CD Core `3.5.0` 설치와 현재 private `car-community` repository의
  `deploy/overlays/stg`를 read-only GitHub App으로 연결하는 script·runbook, 같은 immutable
  image를 사용하는 단일 migration PreSync Job과 Deployment·Service manifest가 구현됐습니다.
- Staging public API를 위한 AWS Load Balancer Controller `3.5.0` bootstrap과
  `stg-api.example.com`의 NodePort·Ingress desired state가 구현됐습니다. Internet-facing ALB,
  ACM certificate와 Cloudflare DNS-only CNAME을 포함한 실제 환경의 현재 상태와 검증 결과는
  이 Reference/SOT에 복제하지 않고 Staging 전체 구축 시나리오에서 확인합니다.
- 공개 DB URL·RDS CA·notification 설정은 Git/Kustomize가, runtime·migrator·auth·notification
  actual Secret은 local `.env` 기반 bootstrap이 소유합니다. 구축 후 Resend key 교체와 새 Pod
  rollout operation source도 구현했습니다.

미구현 제품 영역과 우선순위는 이 문서에 중복하지 않고 [제품 기능 범위](../product/200-feature-scope.md)에서 확인합니다.

## 관련 문서

- **현재 구조**: [프로젝트 아키텍처](200-current-architecture.md), [모듈 지도](210-module-map.md)
- **제품 범위**: [기능 범위](../product/200-feature-scope.md)
- **기술 기준**: [현재 기술 스택](../tech-stack/100-current-stack.md)
- **현재 infrastructure 개념과 선택**: [100 Infrastructure overview](infrastructure/100-infrastructure-overview.md),
  [Infrastructure 개념 목차](infrastructure/000-index.md)
- **실제 구축·반복 점검·변경·복구 절차**: [운영 문서 목차](../operations/000-index.md)
- **Staging 전체 구축의 현재 live 상태와 재개 위치**:
  [Staging 단일 node 전체 구축 시나리오](../operations/scenarios/100-staging-single-node-bootstrap.md)
