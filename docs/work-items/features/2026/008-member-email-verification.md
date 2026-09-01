# 일반 회원 이메일 인증

## 상태

Done

## 결과

- 이메일 인증 요청과 확인 public API를 구현했습니다.
- `account_verification_token`의 `EMAIL_VERIFICATION` purpose로 1회용 token을 관리합니다.
- 요청은 account 존재·상태와 무관하게 같은 public status/body를 반환하고, 확인 성공 시 account를 `PENDING`에서 `ACTIVE`로 전환합니다. 처리 시간 균일성은 현재 보장하지 않습니다.
- notification module과 Resend adapter를 분리하고 provider 일시 오류에 bounded immediate retry를 적용합니다.
- request·completion auth event, environment contract와 관련 test를 구현했습니다.
- schema, service, controller, notification, Security와 최종 Gradle 검증을 통과했습니다.
- 전체 완료 여부: 예
- 남은 책임: 이 작업의 이메일 인증 완료 범위에는 없습니다. 현재 notification logging, 공용 `Clock` owner와 delivery/audit 실패 의미 결함의 수정 상태는 [000 Bug 작업](../../bugs/2026/000-index.md)에서 각각 추적합니다.

## 범위

- 인증 메일 요청과 재요청
- raw token 비저장, hash·만료·소비·폐기
- backend `POST` token 소비
- account 활성화 transaction
- notification module, Resend와 감사 이벤트

outbox, durable retry, webhook, frontend 화면과 운영 rate limit은 제외했습니다.
배포 환경의 실제 base URL 입력과 frontend 인증 화면은 현재 backend 작업 범위 밖입니다.

## 확정 결정 색인

[상세 설계](008-member-email-verification-design.md)는 다음 판단을 보존합니다.

- purpose/channel 기반 verification token
- auth event 확장
- notification module과 provider 경계
- commit 이후 발송, 최대 2회 immediate retry와 수동 재요청
- frontend 화면 진입과 backend `POST` token 소비
- `PENDING`에서 `ACTIVE`로의 상태 전이

## 영구 SOT 승격

- [Account 도메인](../../../domain/100-account.md)
- [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- [Auth Event 정책](../../../domain/120-auth-event-policy.md)
- [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)
- [Account/Auth Schema Reference](../../../project/schema/100-account-auth.md)
- [로컬 개발 환경](../../../development/100-local-development.md)

## 관련 문서

- **결정 근거**: [상세 설계](008-member-email-verification-design.md)
- **선행 account 생성**: [일반 회원 자체 가입](002-member-self-sign-up-api.md)
- **공유 notification 후속 기능**: [계정 비밀번호 재설정](012-account-password-reset.md)
- **현재 notification·조립 후속**: [Provider logging](../../bugs/2026/001-notification-provider-exception-logging.md), [공용 Clock owner](../../bugs/2026/003-shared-clock-bean-ownership.md), [Delivery/audit 결과 분리](../../bugs/2026/005-notification-audit-result-separation.md)
- **현재 template 후속**: [Notification template 시간 기준 비결정성](../../bugs/2026/008-notification-template-clock.md)
- **현재 timing 후속**: [인증·복구 요청의 계정 열거 timing 차이](../../bugs/2026/009-authentication-enumeration-timing.md)
- **현재 설정 경계 후속**: [Notification 설정 properties 경계 정리](../../improvements/2026/006-notification-configuration-properties.md)
