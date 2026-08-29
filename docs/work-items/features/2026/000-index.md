# 2026 기능 작업 문서 목록

현재 등록된 2026 기능 중 Refresh Token 재발급과 관리자 초대/수락은 완료 당시 목표 미충족이 발견돼 다시 열렸습니다. 완료 뒤 독립 후속 결함만 별도 Bug가 소유합니다. 각 문서의 유효 범위는 `Proposed`·`Planned`이면 상태 아래 `달성할 결과`, 그 밖의 실행·완료 상태이면 `결과`에서 확인합니다.

| 번호 | 상태 | 작업 문서 | 상세 설계 | 현재 요약 |
| --- | --- | --- | --- | --- |
| 001 | Done | [Account 인증/인가 DB Schema](001-account-auth-db-schema.md) | [설계](001-account-auth-db-schema-design.md) | account/auth Flyway 기반 확정 |
| 002 | Done | [일반 회원 자체 가입 API](002-member-self-sign-up-api.md) | [설계](002-member-self-sign-up-api-design.md) | `POST /api/v1/members` 구현 |
| 003 | Done | [API 공통 기반](003-api-common-foundation.md) | [설계](003-api-common-foundation-design.md) | Problem Details와 Security baseline |
| 004 | Done | [API 로깅 기반](004-api-logging-foundation.md) | [설계](004-api-logging-foundation-design.md) | request id, MDC, structured logging 기반 |
| 005 | Done | [회원 로그인 API](005-member-login-api.md) | [설계](005-member-login-api-design.md) | password session과 token 발급 |
| 006 | Done | [인증 토큰 기반](006-auth-token-foundation.md) | [설계](006-auth-token-foundation-design.md) | auth module, JWT, Bearer 인증 |
| 007 | In Progress | [Refresh Token 재발급 API](007-refresh-token-reissue-api.md) | [설계](007-refresh-token-reissue-api-design.md) | reuse 우선순위 local 구현·검증 완료, remote gate 미확인 |
| 008 | Done | [일반 회원 이메일 인증](008-member-email-verification.md) | [설계](008-member-email-verification-design.md) | Resend 요청·확인 흐름 |
| 009 | Done | [인증 로그아웃 API](009-auth-logout.md) | [설계](009-auth-logout-design.md) | refresh token 단일 폐기 |
| 010 | Done | [관리자 계정 기반](010-admin-account-foundation.md) | [설계](010-admin-account-foundation-design.md) | 최고 관리자 bootstrap과 비밀번호 변경 강제 |
| 011 | In Progress | [관리자 초대/수락](011-admin-invitations.md) | [설계](011-admin-invitations-design.md) | local 구현 검증 완료, remote gate 대기 |
| 012 | Done | [계정 비밀번호 재설정](012-account-password-reset.md) | [설계](012-account-password-reset-design.md) | email reset token 기반 복구 |
| 013 | Done | [013 Staging application GitOps delivery](013-autoscaling-safe-application-delivery.md) | - | Repository와 live 기술 delivery 완료, 기능 qualification은 선택 runbook으로 분리 |
| 014 | Done | [014 Staging 외부 HTTPS ingress](014-staging-external-https-ingress.md) | [014 설계](014-staging-external-https-ingress-design.md) | LBC·ALB·ACM·Cloudflare DNS-only 외부 경로 구축 완료 |
| 015 | Done | [015 API 운영 추적성](015-api-operational-traceability.md) | - | 정상·거부·실패 API 완료 이벤트와 고객 상관관계 자동화 |
| 016 | In Progress | [016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md) | [016 신차 카탈로그 조회 기능 DB 설계](016-new-car-catalog-query-api-design.md) | V13·공개 전 검증·대표 fixture·실제 SELECT-only 권한 검증 완료, 조회 persistence 개발이 다음 단계 |
| 017 | Proposed | [017 자동차 카탈로그 초기 데이터 구축](017-car-catalog-initial-data.md) | - | 016 구현 뒤 U1 브랜드와 U2 과거 기간을 확정하고 공식 source inventory·mapping·data migration 수행 |
