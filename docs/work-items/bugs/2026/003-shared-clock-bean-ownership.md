# 공용 Clock bean의 auth 내부 소유

## 상태

Done

## 달성할 결과

여러 module의 시간 기준을 중립적인 애플리케이션 조립 경계가 제공하고, production과 test가 같은 주입 계약을 사용하도록 바꿉니다.

## 기대 동작

`account`와 `auth`가 공용 UTC 시간 기준을 주입받되 어느 module도 다른 module의 internal configuration bean 존재에 의존하거나 production constructor에서 `Clock.systemUTC()`를 직접 만들지 않습니다. test는 같은 공개 조립 지점을 대체합니다.

## 완료 결과

- `config.TimeConfiguration`이 유일한 production `Clock.systemUTC()` provider입니다.
- account/auth/securityaudit consumer는 constructor로 같은 bean을 주입받습니다.
- `DddOwnershipSourceTest`와 context test가 provider 위치와 실제 조립을 검증합니다.

## 영향

`auth` configuration을 분리하면 실제 bean 소비자가 조립에 실패할 수 있고, 직접 UTC Clock을 만드는 service는 애플리케이션 전체의 시간 기준을 test나 운영 조립에서 일관되게 교체할 수 없습니다.

## 수정 계획

1. UTC `Clock` bean을 `config` 또는 명시적인 공용 조립 configuration으로 이동합니다.
2. JWT configuration은 공용 `Clock`을 소비하게 하고 더 이상 bean을 소유하지 않습니다.
3. service public constructor의 `Clock.systemUTC()` 생성을 제거하고 비밀번호 변경, 이메일 인증, 관리자 초대, 비밀번호 재설정과 `auth` token consumer가 같은 bean을 주입받게 합니다.
4. production context와 test override를 함께 검증하는 조립 test를 둡니다.
5. 모듈 지도에서 현재 예외를 제거하고 공용 기술 bean의 소유 기준만 필요한 만큼 남깁니다.

## 완료 조건

- `Clock @Bean`은 `auth.internal`에 존재하지 않습니다.
- `account`의 모든 Clock 소비자와 `auth` token consumer가 중립 조립 bean으로 생성되며 production constructor가 `Clock.systemUTC()`를 직접 만들지 않습니다.
- UTC production 기본값과 test 대체 가능성이 유지됩니다.
- 관련 context test와 `./gradlew check`가 통과합니다.

## 관련 문서

- **현재 구조와 예외**: [모듈 지도](../../../project/210-module-map.md)
- **일반 조립 기준**: [Spring Boot 모듈 구조](../../../architecture/210-spring-boot-module-structure.md)
- **현재 provider source**: `config.TimeConfiguration`
- **현재 account consumer source**: `ChangePasswordService`, `RequestMemberEmailVerificationService`, `ConfirmMemberEmailVerificationService`, `InviteAdminService`, `AcceptAdminInvitationService`, `RevokeAdminInvitationService`, `RequestPasswordResetService`, `ConfirmPasswordResetService`
- **관련 기능**: [인증 토큰 기반](../../features/2026/006-auth-token-foundation.md), [관리자 계정 기반](../../features/2026/010-admin-account-foundation.md), [이메일 인증](../../features/2026/008-member-email-verification.md), [다시 연 관리자 초대](../../features/2026/011-admin-invitations.md), [비밀번호 재설정](../../features/2026/012-account-password-reset.md)
