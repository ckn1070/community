# 인증 로그아웃 API

## 상태

Done

## 결과

- `POST /api/v1/auth/logout` public API를 구현했습니다.
- 전달된 활성 refresh token 하나를 `LOGOUT` 사유로 폐기합니다.
- token이 없거나 invalid·expired·이미 폐기된 상태여도 멱등 `204 No Content`를 반환합니다.
- 실제 활성 token을 폐기한 경우에만 `LOGOUT/SUCCESS` auth event를 저장합니다.
- 최종 Gradle 검증을 통과했습니다.
- 전체 완료 여부: 예
- 남은 책임: 없음

## 범위

- 단일 refresh token 로그아웃
- public endpoint와 request validation
- 멱등 응답과 token oracle 방지
- 성공 auth event와 transaction

access token blacklist, 전체 기기 로그아웃과 family 전체 폐기는 제외했습니다.

## 확정 결정 색인

[상세 설계](009-auth-logout-design.md)는 다음 판단을 보존합니다.

- public logout endpoint 선택
- refresh token 단일 폐기와 멱등 204
- invalid token의 정보 비노출
- auth event 생성 조건
- persistence transaction과 module 경계

## 영구 SOT 승격

- [Account 도메인](../../../domain/100-account.md)
- [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)
- [Spring Security 기준](../../../tech-stack/220-spring-security.md)

## 관련 문서

- **결정 근거**: [상세 설계](009-auth-logout-design.md)
- **선행 session 생성**: [회원 로그인](005-member-login-api.md)
- **공유 refresh token lifecycle**: [Refresh Token 재발급](007-refresh-token-reissue-api.md)
