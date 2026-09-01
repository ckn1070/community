# 제품 개요

이 문서는 Carmunity의 제품 목표와 현재 제품 범위를 소유합니다.
프로젝트의 현재 기술 상태와 구현 module은 `project` 문서에서 확인합니다.

## 제품 목표

- 사용자가 자동차 정보를 탐색할 수 있게 합니다.
- 초기 제품은 신차 정보를 중심으로 시작합니다.
- 자동차 정보와 연결된 대화와 커뮤니티 활동을 제공합니다.
- 서비스가 성장하면 상용화 가능성을 고려합니다.
- 중고차 영역은 향후 정보 제공 또는 매물 기능으로 확장할 수 있습니다.

## 현재 제품 범위

- 자동차 정보 제공
- 자동차 정보와 연결되는 커뮤니티
- 회원과 인증 기반
- 운영과 신고를 확장할 수 있는 관리자 기반

현재 저장소에는 제품 기능 전체보다 account/auth 기반이 먼저 구현되어 있습니다.
구현 상태는 [프로젝트 개요](../project/100-project-overview.md), 기능별 결과는 [기능 작업 문서](../work-items/features/2026/000-index.md)에서 확인합니다.

## 현재 제외 범위

- 중고차 매물의 상세 거래 흐름
- 결제, 광고, 유료 구독
- 자동차 데이터 수집 provider 확정
- 검색엔진과 메시징 도입
- 신고·제재 운영 정책의 상세

제외 항목은 폐기한 기능이 아니라 아직 상세 제품 결정과 작업 범위가 확정되지 않은 영역입니다.
현재 Carmunity와 관계가 미확정인 별도 제품 후보는
[002 AutoSquare 제품 후보](../work-items/backlog/2026/002-autosquare-mvp-candidate.md), 횡단
architecture·privacy·운영 질문은
[001 프로젝트 횡단 열린 결정](../work-items/backlog/2026/001-project-open-decisions.md)에서 추적합니다.
확정된 현재 제품·architecture 기준은 각각의 영구 SOT에만 반영합니다.

## 관련 문서

- **제품 범위**: [기능 범위](200-feature-scope.md)
- **현재 구현 상태**: [프로젝트 문서](../project/000-index.md)
- **업무 정책**: [도메인 문서](../domain/000-index.md)
- **관계가 미확정인 제품 후보**: [AutoSquare 제품 후보](../work-items/backlog/2026/002-autosquare-mvp-candidate.md)
- **미확정 횡단 범위**: [프로젝트 횡단 열린 결정](../work-items/backlog/2026/001-project-open-decisions.md)
