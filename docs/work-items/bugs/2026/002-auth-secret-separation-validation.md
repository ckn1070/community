# Auth secret 분리 검증 누락

## 상태

Proposed

## 달성할 결과

JWT HS256 서명 secret과 로그인 실패 identifier HMAC secret이 같은 값이면 애플리케이션 시작을 거부해, 서로 다른 회전 주기와 신뢰 경계를 실제 설정 계약으로 강제합니다.

## 기대 동작

두 secret은 각각 32 bytes 이상이어야 하며 서로 다른 값이어야 합니다. 같은 값이면 어느 consumer도 시작하기 전에 안전한 설정 오류로 실패하고 secret 원문은 로그에 남기지 않습니다.

## 실제 동작과 근거

- `JwtTokenProperties`는 JWT secret 자체의 32 bytes 최소 길이만 검사합니다.
- `LoginIdentifierHashProperties`는 identifier HMAC secret 자체의 32 bytes 최소 길이만 검사합니다.
- 두 properties를 함께 비교하는 startup validation과 동일값 거부 test가 없습니다.
- 따라서 운영 문서가 요구한 분리 원칙은 현재 application에서 강제되지 않습니다.

## 영향

동일 secret을 잘못 주입해도 시작되므로 JWT key rotation과 감사 식별자 hash rotation이 결합되고, 한 용도의 secret 노출이 다른 용도의 신뢰 경계까지 넓힐 수 있습니다.

## 수정 계획

1. 두 properties를 함께 받는 application 조립 경계에서 동일값을 거부하는 최소 startup validation을 둡니다.
2. 같은 값은 시작 실패, 서로 다른 유효 값은 시작 성공, 각 값의 길이 위반은 기존처럼 실패하는 test를 둡니다.
3. 오류와 test output에 secret 원문이 포함되지 않는지 확인합니다.
4. 구현 후 환경변수 SOT를 “운영 원칙”에서 “application이 강제하는 계약”으로 올리고, 향후
   rotation 운영 절차에도 같은 계약을 반영합니다.

## 완료 조건

- 동일한 두 32-byte 이상 secret으로 context가 시작되지 않습니다.
- 서로 다른 두 유효 secret으로 context가 시작됩니다.
- 실패 메시지는 property 역할만 식별하고 값은 노출하지 않습니다.
- 관련 configuration test와 `./gradlew check`가 통과합니다.

## 관련 문서

- **현재 환경변수 경계**: [로컬 개발 환경](../../../development/100-local-development.md)
- **운영 회전 절차**: 현재 secret rotation 절차 미확정
- **보안 의도**: [Account 도메인](../../../domain/100-account.md)
- **설정 source**: `JwtTokenProperties`, `LoginIdentifierHashProperties`
- **관련 완료 기능**: [회원 로그인](../../features/2026/005-member-login-api.md), [인증 토큰 기반](../../features/2026/006-auth-token-foundation.md)
