# Bootstrap 다음 행동 안내 범위 오류

## 상태

Proposed

## 달성할 결과

최초 최고 관리자 bootstrap command의 다음 행동 안내가 실제 Security 계약과 같은 범위인 “일반·관리자 보호 API 사용 전 비밀번호 변경”을 정확히 전달합니다.

## 기대 동작

운영자는 초기 비밀번호 상태에서 일반·관리자 보호 API가 차단되고 password 변경 API는 허용되며, public allow-list는 별도라는 사실을 command 출력만으로 오해 없이 알 수 있어야 합니다.

## 실제 동작과 근거

`BootstrapSuperAdminCommandRunner`는 `NEXT_ACTION change the initial password before using any API`를 출력합니다. 실제 `ApiSecurityConfig`는 signup, login, refresh, logout, 이메일 인증, password reset과 관리자 초대 수락 등 public allow-list를 먼저 허용하므로 “any API”는 차단 범위를 과장합니다.

## 수정 계획

1. 출력 문구를 보호 API 범위로 좁힙니다.
2. command output test가 exact 의미를 고정하되 Security matcher 문자열 전체를 중복하지 않게 합니다.
3. command output과 현재 보안 정책 문서의 의미를 함께 갱신합니다.

## 완료 조건

- command 출력이 public endpoint까지 차단된다고 읽히지 않습니다.
- 초기 비밀번호 상태의 실제 보호 API 차단 의미가 유지됩니다.
- command test와 `./gradlew check`가 통과합니다.

## 관련 문서

- **현재 보안 계약**: [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- **선행 기능 결과**: [관리자 계정 기반](../../features/2026/010-admin-account-foundation.md)
