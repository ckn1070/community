# Refresh Token 재발급 API

## 상태

In Progress

## 결과

- `POST /api/v1/auth/sessions/refresh` token rotation API를 구현했습니다.
- hash 기반 pessimistic lock으로 같은 token의 동시 rotation을 한 번만 허용합니다.
- 구현된 범위: refresh token rotation, pessimistic lock 기반 동시성 제어, account 상태·role 재조회와 동일 401 계약을 구현했습니다.
- 원래 목표 중 충족한 범위: 만료 전 revoked·replaced token reuse는 token family의 활성 token을 폐기하고 감사 이벤트를 남깁니다.
- 현재 판정: 원래 수용 기준은 revoked·replaced token 재사용이면 family 폐기와 event를 요구했지만, 실제 구현은 만료를 reuse보다 먼저 검사해 만료된 교체 token을 단순 만료로 종료합니다.
- account 최신 상태와 role을 다시 조회해 새 access token을 발급합니다.
- invalid·expired·revoked·replaced token은 동일한 401 계약으로 처리합니다.
- application, persistence, web, Security, concurrency integration test와 최종 Gradle 검증을 통과했습니다.
- 영향: 교체 token 탈취 신호가 만료 뒤 관찰되면 아직 활성인 replacement family가 유지되고 reuse event도 남지 않습니다.
- 미완료: expired·revoked·replaced 조합의 우선순위를 확정하고, 만료된 교체 token reuse의 family 폐기·감사와 단순 만료의 비폐기를 구현·검증해야 합니다.
- 다음 실행자: 개발 권한을 받은 Codex가 [상세 설계](007-refresh-token-reissue-api-design.md)의 원래 reuse 계약에 맞춰 service와 integration/concurrency test를 수정합니다.
- 완료 조건: `expired + replaced + active replacement`가 family 폐기·감사·동일 401을 만들고 단순 만료는 잘못된 reuse로 기록되지 않으며 `./gradlew check`가 통과합니다.

## 범위

- refresh token rotation
- reuse detection과 family 폐기
- account 상태·role 재조회
- refresh 성공·실패 auth event
- 원문 token과 hash 비노출

cookie 전달, 전체 기기 로그아웃, rate limit은 별도 작업입니다.

## 확정 결정 색인

[상세 설계](007-refresh-token-reissue-api-design.md)는 다음 판단을 보존합니다.

- public refresh endpoint와 오류 계약
- hash 조회, pessimistic lock과 transaction
- rotation·reuse·account 상태 거부 정책
- lifecycle store port의 책임
- access token subject 재조회와 module 경계

## 영구 SOT 승격

- [Account 도메인](../../../domain/100-account.md)
- [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)
- [Spring Security 기준](../../../tech-stack/220-spring-security.md)
- [로깅 기준](../../../tech-stack/280-logging.md)

## 관련 문서

- **결정 근거**: [상세 설계](007-refresh-token-reissue-api-design.md)
- **선행 token 발급**: [회원 로그인](005-member-login-api.md), [인증 토큰 기반](006-auth-token-foundation.md)
- **후속 폐기 흐름**: [로그아웃](009-auth-logout.md)
