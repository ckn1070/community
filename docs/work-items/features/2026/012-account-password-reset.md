# 계정 비밀번호 재설정

## 상태

Done

## 결과

- 비로그인 사용자의 비밀번호 재설정 요청·확인 public API를 구현했습니다.
- `PASSWORD_RESET` verification token purpose와 auth event를 추가했습니다.
- `ACTIVE`와 `PENDING` account를 지원하고 요청의 public status/body에서 account 존재와 상태를 숨깁니다. 처리 시간 균일성은 현재 보장하지 않습니다.
- token 소비, password hash 변경, 강제 변경 해제, refresh token 폐기와 auth event를 한 transaction으로 처리합니다.
- Resend reset notification과 `PASSWORD_RESET_BASE_URL` 계약을 추가했습니다.
- 관련 focused test와 최종 Gradle 검증을 통과했습니다.
- 전체 완료 여부: 예
- 남은 책임: 이 작업의 비밀번호 재설정 완료 범위에는 없습니다. 현재 notification logging, 공용 `Clock` owner와 delivery/audit 실패 의미 결함의 수정 상태는 [000 Bug 작업](../../bugs/2026/000-index.md)에서 각각 추적합니다.

## 범위

- `POST /api/v1/auth/password-resets`
- `POST /api/v1/auth/password-resets/confirm`
- reset token 발급·cooldown·만료·소비·폐기
- password와 session credential 갱신
- notification과 감사 이벤트

로그인 상태 password change, 관리자 초대 수락, MFA와 frontend 화면은 제외했습니다.
배포 환경의 실제 base URL 입력과 frontend reset 화면은 현재 backend 작업 범위 밖입니다.

## 확정 결정 색인

[상세 설계](012-account-password-reset-design.md)는 다음 판단을 보존합니다.

- 별도 reset resource API
- verification token table 재사용
- 대상 account 상태와 enumeration 방지
- cooldown, 만료와 token 소비
- password·refresh token·auth event transaction
- notification 경계와 환경변수

## 영구 SOT 승격

- [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- [Auth Event 정책](../../../domain/120-auth-event-policy.md)
- [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)
- [Account/Auth Schema Reference](../../../project/schema/100-account-auth.md)
- [로컬 개발 환경](../../../development/100-local-development.md)

## 관련 문서

- **결정 근거**: [상세 설계](012-account-password-reset-design.md)
- **공유 검증 token·notification 기반**: [이메일 인증](008-member-email-verification.md)
- **관리자 초대 수락과의 credential 경계**: [관리자 초대](011-admin-invitations.md)
- **현재 notification·조립 후속**: [Provider logging](../../bugs/2026/001-notification-provider-exception-logging.md), [공용 Clock owner](../../bugs/2026/003-shared-clock-bean-ownership.md), [Delivery/audit 결과 분리](../../bugs/2026/005-notification-audit-result-separation.md)
- **현재 template 후속**: [Notification template 시간 기준 비결정성](../../bugs/2026/008-notification-template-clock.md)
- **현재 timing 후속**: [인증·복구 요청의 계정 열거 timing 차이](../../bugs/2026/009-authentication-enumeration-timing.md)
- **현재 설정 경계 후속**: [Notification 설정 properties 경계 정리](../../improvements/2026/006-notification-configuration-properties.md)
