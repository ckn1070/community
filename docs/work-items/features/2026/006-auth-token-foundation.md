# 인증 토큰 기반

## 상태

Done

## 결과

- 독립 `auth` module과 account 공개 API 협력 경계를 구현했습니다.
- Spring Security Resource Server 기반 HS256 access token 발급·검증을 구현했습니다.
- 완료 당시 account public id를 principal(`sub`)로 사용하고 role claim을 authority로 변환하는 JWT 기반을 구현했습니다.
- JWT HMAC secret과 issuer·TTL 환경변수 계약을 추가했습니다.
- token, Security, module boundary test와 최종 Gradle 검증을 통과했습니다.
- 전체 완료 여부: 예
- 남은 책임: 이 작업의 최초 token 기반 범위에는 없습니다. 현재 secret 동일값 검증과 공용 `Clock` bean 소유 결함의 수정 상태는 각각 [002 Auth secret 분리 검증 누락](../../bugs/2026/002-auth-secret-separation-validation.md), [003 공용 Clock bean의 auth 내부 소유](../../bugs/2026/003-shared-clock-bean-ownership.md)에서 추적합니다.

## 범위

- access token 발급과 검증
- `auth` module과 `account.api` 경계
- JWT claim·authority 변환
- issuer, 15분 TTL과 HMAC secret 설정
- Bearer 인증 Security 연결

비밀번호 로그인과 refresh token lifecycle은 이 기반을 사용하는 별도 기능입니다.

## 확정 결정 색인

[상세 설계](006-auth-token-foundation-design.md)는 다음 판단을 보존합니다.

- `auth` module 분리 시점과 account 공개 계약
- HS256과 secret 주입 방식
- 완료 당시의 `sub`, `roles`, `iss`, `iat`, `exp` claim
- Resource Server와 authentication converter 구조
- token 발급 port와 Security adapter 경계

## 영구 SOT 승격

- [현재 모듈 지도](../../../project/210-module-map.md)
- [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)
- [Spring Security 기준](../../../tech-stack/220-spring-security.md)
- [로컬 개발 환경](../../../development/100-local-development.md)

## 관련 문서

- **결정 근거**: [상세 설계](006-auth-token-foundation-design.md)
- **첫 발급 소비자**: [회원 로그인](005-member-login-api.md)
- **회전 소비자**: [Refresh Token 재발급](007-refresh-token-reissue-api.md)
- **후속 claim 확장**: [회원 로그인](005-member-login-api.md)의 `session_access`, [관리자 계정 기반](010-admin-account-foundation.md)의 `account_status`·`password_change_required`
- **현재 전체 계약**: [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- **현재 구현 후속**: [Auth secret 분리 검증 누락](../../bugs/2026/002-auth-secret-separation-validation.md), [공용 Clock bean의 auth 내부 소유](../../bugs/2026/003-shared-clock-bean-ownership.md)
