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

## 아직 작업으로 확정되지 않은 영역

자동차 카탈로그와 커뮤니티 상세 기능, 신고·제재 정책, media 보관 정책, 중고차 확장 방식은 backlog 상태입니다.
이 항목은 영구 도메인 규칙이나 project module 결정으로 미리 승격하지 않습니다.

## 관련 문서

- **상위 제품 목표**: [제품 개요](100-product-overview.md)
- **Carmunity 적용 여부가 미확정인 제품 후보**: [AutoSquare 제품 후보](../work-items/backlog/2026/002-autosquare-mvp-candidate.md)
- **미확정 횡단 범위**: [프로젝트 횡단 열린 결정](../work-items/backlog/2026/001-project-open-decisions.md)
- **현재 구현 배치**: [모듈 지도](../project/210-module-map.md)
- **기능별 계획·결과**: [기능 작업 문서](../work-items/features/000-index.md)
