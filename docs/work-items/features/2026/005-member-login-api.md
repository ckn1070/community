# 회원 로그인 API

## 상태

Done

## 결과

- `POST /api/v1/auth/sessions` 비밀번호 로그인 API를 구현했습니다.
- `ACTIVE`는 `FULL`, `PENDING`은 `LIMITED` session을 발급하고 `SUSPENDED`·`WITHDRAWN`은 거부합니다.
- access token에는 session access를, refresh token은 원문 1회 응답과 SHA-256 hash 저장을 사용합니다.
- 성공·실패 상태 변경과 auth event를 account 저장 transaction에 연결했습니다.
- 존재하지 않는 identifier는 HMAC hash만 감사 이벤트에 남깁니다.
- 계정 없음과 password 불일치는 같은 오류 응답을 사용하지만 Argon2 수행 여부가 달라 처리 시간 균일성은 현재 보장하지 않습니다.
- 관련 application, Security, persistence와 integration test 및 최종 Gradle 검증을 통과했습니다.
- 전체 완료 여부: 예
- 남은 책임: 이 작업의 비밀번호 로그인 완료 범위에는 없습니다. 두 HMAC 설정의 동일값 검증은 [Auth secret 분리 검증 누락](../../bugs/2026/002-auth-secret-separation-validation.md), timing 방어는 [인증·복구 요청의 계정 열거 timing 차이](../../bugs/2026/009-authentication-enumeration-timing.md)가 소유합니다.

## 범위

- password credential 검증
- account status별 session access
- access·refresh token 최초 발급
- 로그인 성공·실패 상태와 감사 이벤트
- 동일 오류 응답과 identifier HMAC을 통한 직접적인 identifier·민감정보 노출 방지

rotation, logout, password reset과 rate limit은 별도 기능 작업입니다.

## 확정 결정 색인

[상세 설계](005-member-login-api-design.md)는 다음 판단을 보존합니다.

- login endpoint와 request·response 계약
- `FULL`·`LIMITED` session 정책
- refresh token 원문·hash·family·만료 계약
- 성공·실패 transaction과 auth event
- unknown identifier HMAC 처리
- account와 auth module 협력 경계

## 영구 SOT 승격

- [Account 도메인](../../../domain/100-account.md)
- [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)
- [Spring Security 기준](../../../tech-stack/220-spring-security.md)
- [로컬 개발 환경](../../../development/100-local-development.md)

## 관련 문서

- **결정 근거**: [상세 설계](005-member-login-api-design.md)
- **공통 token 기반**: [인증 토큰 기반](006-auth-token-foundation.md)
- **후속 session lifecycle**: [Refresh Token 재발급](007-refresh-token-reissue-api.md)
- **현재 설정 검증 후속**: [Auth secret 분리 검증 누락](../../bugs/2026/002-auth-secret-separation-validation.md)
- **현재 timing 후속**: [인증·복구 요청의 계정 열거 timing 차이](../../bugs/2026/009-authentication-enumeration-timing.md)
