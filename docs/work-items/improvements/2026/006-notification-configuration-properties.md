# Notification 설정 properties 경계 정리

## 상태

Done

## 달성할 결과

같은 `carmunity.notification.email.*` namespace의 Resend, 발신자와 기능별 base URL 설정을 검증 가능한 `@ConfigurationProperties` 계약으로 바인딩하고 중복 `@Value` 조립을 제거합니다.

## 범위

- notification email 설정 namespace와 properties 모델
- 필수·선택 값 validation과 profile별 wiring
- Resend gateway와 이메일 인증·관리자 초대·비밀번호 재설정 template 설정 소비
- configuration binding test와 환경변수 문서 동기화

provider 교체, secret manager 도입, 이메일 기능 정책 변경은 포함하지 않습니다.

## 현재 상태와 문제

`ResendEmailNotificationConfig`는 같은 설정 namespace의 여러 값을 7개 `@Value` parameter로 받아 `EmailNotificationProperties`와 `AdminInvitationNotificationProperties`를 수동 조립합니다. 런타임 오류 증거는 없지만, 설정 묶음은 `@ConfigurationProperties`가 소유한다는 현재 Spring Boot 기준과 다르고 두 properties가 api key와 from을 중복합니다.

## 구현 결과

- `carmunity.notification.email.*` 전체를 하나의 계층형 `EmailNotificationProperties`로
  bind하고 중복 `@Value` 조립과 관리자 초대 전용 중복 record를 제거했습니다.
- 다섯 값이 모두 비어 있으면 local·test에서 notification 비활성 상태를 허용합니다. 하나라도
  설정하면 Resend key, 발신자와 세 base URL을 모두 요구하고 URL·email 형식을 기동 시점에
  검증합니다.
- 오류에는 actual Resend key와 입력값을 넣지 않습니다. Focused context test가 complete,
  disabled와 partial 설정을 검증합니다.
- Staging은 Git-managed 공개 ConfigMap과 local `.env`에서 적용한 notification Secret으로
  다섯 값을 모두 주입합니다.

## 수용 기준

- notification email 설정 묶음이 `@ConfigurationProperties`로 바인딩됩니다.
- 중복 key가 여러 `@Value` parameter에 흩어지지 않습니다.
- 잘못된 필수 설정은 secret 값을 노출하지 않고 startup 또는 focused binding test에서 실패합니다.
- `.env.example`, 로컬 환경 문서와 application 설정 key가 일치합니다.
- 관련 configuration test와 `./gradlew check`가 통과합니다.

## 관련 문서

- **현재 기준과 예외**: [Spring Boot 사용 가이드](../../../tech-stack/210-spring-boot.md)
- **현재 source**: `ResendEmailNotificationConfig`, `EmailNotificationProperties`
- **관련 기능**: [이메일 인증](../../features/2026/008-member-email-verification.md), [다시 연 관리자 초대](../../features/2026/011-admin-invitations.md), [비밀번호 재설정](../../features/2026/012-account-password-reset.md)
