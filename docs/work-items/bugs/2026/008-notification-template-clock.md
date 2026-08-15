# Notification template 시간 기준 비결정성

## 상태

Proposed

## 달성할 결과

이메일 인증과 비밀번호 재설정 template의 남은 시간 계산이 주입된 `Clock`을 사용하고 고정 시각 test로 재현됩니다.

## 기대 동작

시간 의존 로직은 호출자가 제공한 기준 시각으로 계산되며 같은 입력과 Clock에서 같은 subject/body를 만듭니다. 만료 경계와 최소 표시값도 test로 고정합니다.

## 실제 동작과 근거

- `VerificationEmailTemplate`과 `PasswordResetEmailTemplate`은 남은 시간을 계산할 때 `Instant.now()`를 직접 호출합니다.
- template 계약을 fixed Clock으로 검증하는 test가 없습니다.
- 다른 account/auth application service는 test 가능한 `Clock` 주입 기준을 사용합니다.

## 영향

만료 경계 부근 test가 비결정적일 수 있고, 호출 흐름의 기준 시각과 이메일 표시 시간이 달라질 수 있습니다.

## 수정 계획

1. 두 template 또는 이를 조립하는 notification 경계에 `Clock`을 주입합니다.
2. 만료 전, 경계, 이미 지난 시각의 표시 규칙을 fixed Clock test로 고정합니다.
3. email body에 raw token 외 민감정보를 추가하지 않고 기존 link 계약을 유지합니다.

## 완료 조건

- production은 UTC Clock을 사용하고 test는 fixed Clock을 주입합니다.
- 같은 입력과 Clock에서 template 결과가 결정적입니다.
- 이메일 인증·비밀번호 재설정 template test와 `./gradlew check`가 통과합니다.

## 관련 문서

- **시간 처리 기준**: [Java 기준](../../../tech-stack/200-java.md), [테스트 스택](../../../tech-stack/300-testing-stack.md)
- **관련 완료 기능**: [이메일 인증](../../features/2026/008-member-email-verification.md), [비밀번호 재설정](../../features/2026/012-account-password-reset.md)
- **현재 source**: `VerificationEmailTemplate`, `PasswordResetEmailTemplate`
