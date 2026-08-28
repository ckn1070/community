# 기능 범위

이 문서는 제품 관점의 기능 영역과 현재 우선순위 경계를 정리합니다.
구현 계획과 진행률은 work item이 소유합니다.

## 제품 기능 영역

- 자동차 브랜드, 모델, 세대, 트림, 제원, 옵션, 가격 조회
- 자동차 상세 정보
- 자동차 정보와 연결된 게시글
- 게시글 목록과 상세
- 댓글, 반응, 북마크
- 회원 공개 profile
- 신고, 숨김, 제재
- 이미지 업로드와 보관
- 인증, 계정 복구, 관리자 운영 기반

## 현재 구현된 기반

현재 backend에는 다음 제품 기반이 구현되어 있습니다.

- 일반 회원 가입과 이메일 인증
- 비밀번호 로그인, refresh, logout
- 비밀번호 변경과 재설정
- 최초 최고 관리자 bootstrap
- 관리자 초대 생성, 수락, 회수
- role과 session 기반 API 접근
- 인증·인가 감사 이벤트

상세 결과는 [2026 기능 작업 문서](../work-items/features/2026/000-index.md)를 확인합니다.

## 설계 검토와 미확정 영역

자동차 카탈로그는
[016 신차 카탈로그 schema·조회 API](../work-items/features/2026/016-new-car-catalog-query-api.md)에서
로그인 없는 읽기 전용 조회, 모델 기준 목록과 검색어·브랜드·차체·동력 방식·연료·가격대·
판매 상태·기준일·정렬의 선택
입력을 기능 범위로 유지하고 있습니다. 사용자는 주요 자동차 정보를 폭넓게 보존하고 과거
차량도 조회 가능하게 하며, `/api/v1/car/...` 아래 공개 API와 관계형 DB를 사용하고, schema와
조회 기능을 실제 초기 데이터 수집보다 먼저 개발해 달라고 요청했습니다. 상세 설계는 이
요구를 브랜드·모델·세대·모델 연식, 동력계통과 복수 장치, 차체·인승·휠별 기술 변형, 실제
판매 구성, 적용 시작일별 가격, 유상 옵션과 조건별 인증으로 구체화했습니다. 물리 설계는
자동차 제품 구조를 표현하는 11개 테이블이며 색상은
유상 여부와 configuration 관계를 option 행으로 보존하고 표시 전용 기본 품목·보증·안전도·
media만 제한된 JSONB에 둡니다. 현재 016은 `In Progress`이며 catalog 공개 enum, 11개 테이블의
Flyway V13~V17, PostgreSQL 제약·대표 fixture와 runtime 실제 statement 기반
SELECT-only 권한 검증까지 구현됐습니다.
조회 persistence·공개 API와 production 데이터는 아직 구현되지 않았습니다.

실제 production catalog data는
[017 자동차 카탈로그 초기 데이터 구축](../work-items/features/2026/017-car-catalog-initial-data.md)이
소유합니다. 최초 브랜드·시장 범위 U1과 과거 기간 U2는 이 후속 작업의 제품 결정이며, U2-C는
discovery 뒤 exact 기간을 다시 확정해야 합니다. 이 범위 트랙은 016의 schema·조회 API 개발을
막지 않습니다. 017은 아직 개발 전이므로 설계 문서의 production data 목표를 현재 구현 사실로
다루지 않습니다.

커뮤니티 상세 기능, 신고·제재 정책, media 보관 정책과 중고차 확장 방식은 backlog 상태입니다.
AutoSquare MVP 후보는 Carmunity 적용 여부가 미확정인 별도 제품 후보로 유지합니다.

## 관련 문서

- **상위 제품 목표**: [제품 개요](100-product-overview.md)
- **Carmunity 적용 여부가 미확정인 제품 후보**: [AutoSquare 제품 후보](../work-items/backlog/2026/002-autosquare-mvp-candidate.md)
- **미확정 횡단 범위**: [프로젝트 횡단 열린 결정](../work-items/backlog/2026/001-project-open-decisions.md)
- **현재 구현 배치**: [모듈 지도](../project/210-module-map.md)
- **기능별 계획·결과**: [기능 작업 문서](../work-items/features/000-index.md)
