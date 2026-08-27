# 프로젝트 횡단 열린 결정 Backlog

## 상태

Open

## 배경

제품, 도메인, 프로젝트 구조 문서에는 확정된 현재 기준만 남겨야 합니다.
아직 착수하지 않은 제품, 도메인, 아키텍처 고민은 backlog에 모아두고 실제 작업을 시작할 때 검토합니다.

## Owner 경계

신차 탐색·카탈로그·커뮤니티·AI·대리점·관리자·이미지·데이터 출처·중고차·수익화의
제품/MVP 후보는 [AutoSquare MVP 후보](002-autosquare-mvp-candidate.md)가 단일 owner입니다.
이 문서는 그 항목을 다시 나열하지 않고, 여러 제품 기능에 공통으로 영향을 주는
architecture·privacy·운영 결정만 소유합니다.

## Backlog 항목

### 개인정보와 공통 데이터 수명

- 데이터 보존, 탈퇴, 익명화, 개인정보 처리 정책을 결정합니다.
- 예상 트래픽, 배포 환경, 운영 비용 제약을 결정합니다.

### 아키텍처와 기술 선택

- 별도 검색엔진 도입 조건을 결정합니다.
- 메시징 기반 이벤트 처리 도입 조건을 결정합니다.
- 마이크로서비스 분리 조건을 결정합니다.
- 중고차 매물 모듈의 상세 구조를 결정합니다.

### 횡단 운영 정책 경계

- 외부 연동 실패와 데이터 무결성 위험의 event 의미, alert threshold와 자동 대응 정책을
  결정합니다.
- 예상 traffic·운영 비용과 개인정보 보존 요구는 infrastructure 관측·로그 후보가 구체화될
  때 입력 조건으로 제공합니다.
- 중앙 수집·보존·검색, infrastructure alert 전달과 on-call 체계는
  [004 Infrastructure 진화 Backlog](004-infrastructure-evolution.md)가 소유합니다.
- Auth event의 보존·alert 의미와 로그인 abuse 자동 대응은
  [003 Account 인증 후속 후보](003-account-auth-follow-up.md)가 소유합니다.

## 전환 기준

- 실제 제품, 도메인, 아키텍처 작업을 시작할 때 관련 항목을 작업 문서로 옮깁니다.
- 옮기지 않는 항목은 폐기 사유를 남깁니다.
- 모든 항목이 옮겨졌거나 폐기되면 이 backlog 상태를 `Closed`로 바꿉니다.

## 생성된 작업 문서

- 제품/MVP 후보의 현재 owner로 [AutoSquare MVP 후보](002-autosquare-mvp-candidate.md)를
  분리했습니다. 이는 구현 작업 승격이 아니라 backlog owner 위임입니다.

## 폐기된 항목

아직 없습니다.

## 관련 문서

- **현재 구현 상태**: [프로젝트 개요](../../../project/100-project-overview.md), [현재 아키텍처](../../../project/200-current-architecture.md), [모듈 지도](../../../project/210-module-map.md)
- **제품 목표·범위**: [제품 개요](../../../product/100-product-overview.md), [기능 범위](../../../product/200-feature-scope.md)
- **업무 정책 router**: [도메인 문서](../../../domain/000-index.md)
- **Infrastructure 관측·운영 후보**: [004 Infrastructure 진화 Backlog](004-infrastructure-evolution.md)
