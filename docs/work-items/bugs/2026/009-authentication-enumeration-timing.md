# 인증·복구 요청의 계정 열거 timing 차이

## 상태

Proposed

## 달성할 결과

로그인, 이메일 인증 요청과 비밀번호 재설정 요청에서 계정 존재·상태에 따른 처리 시간 차이가 실용적인 계정 열거 신호가 되지 않도록 보안 계약과 구현을 일치시킵니다.

## 기대 동작

공개 status와 body뿐 아니라 공격자가 반복 관찰할 수 있는 처리 비용도 계정 존재 여부를 유의미하게 구분하지 않아야 합니다. 정확한 방어 방식은 경로별 비용과 운영 영향을 측정한 뒤 가장 단순한 충분 통제로 결정합니다.

## 실제 동작과 근거

- 로그인은 계정이 없으면 감사 기록 후 즉시 실패하지만, 존재하면 Argon2 password 검증을 수행합니다.
- 이메일 인증과 비밀번호 재설정 요청은 대상이 없거나 부적격이면 즉시 반환하지만, 적격 대상은 DB token 처리와 commit 이후 동기 provider 호출·감사 저장까지 수행합니다.
- 각 API의 공개 status와 body는 같은 형태지만 처리 시간 균일성은 보장하지 않습니다.

## 영향

공격자가 충분한 반복 요청과 timing 측정을 통해 등록 계정 또는 특정 상태의 계정을 추정할 수 있습니다. provider 지연까지 동기 경로에 포함되어 복구 요청의 차이가 더 커질 수 있습니다.

## 결정과 수정 계획

1. 로그인은 존재하지 않는 계정에도 실제 설정과 동등한 password 검증 비용을 부과하는 방식과 rate limit을 비교합니다.
2. 복구 요청은 durable async handoff, 균일한 내부 작업, 의도적인 최소 지연과 rate limit의 효과·비용·실패 경계를 비교합니다.
3. 네트워크·DB·provider 변동을 포함한 반복 측정으로 선택한 통제가 실제 구분 가능성을 충분히 낮추는지 확인합니다.
4. 공개 응답, 감사와 notification 의미를 바꾸면 관련 SOT·OpenAPI·test를 함께 갱신합니다.

## 완료 조건

- 계정 없음·부적격·적격·잘못된 password 경로의 timing 위협 모델과 허용 기준이 명시됩니다.
- 선택한 최소 통제가 로그인과 두 복구 요청 경로에 구현됩니다.
- 공개 status/body의 동일 계약과 민감정보 비노출이 유지됩니다.
- 통제된 성능 test, 관련 service/Web MVC test와 `./gradlew check`가 통과합니다.

## 관련 문서

- **현재 정책과 한계**: [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- **관련 완료 기능**: [회원 로그인](../../features/2026/005-member-login-api.md), [이메일 인증](../../features/2026/008-member-email-verification.md), [비밀번호 재설정](../../features/2026/012-account-password-reset.md)
- **현재 source**: `CreatePasswordSessionService`, `RequestMemberEmailVerificationService`, `RequestPasswordResetService`
