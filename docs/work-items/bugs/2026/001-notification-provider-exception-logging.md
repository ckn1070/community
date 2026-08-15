# Notification provider exception logging 회귀

## 상태

Proposed

## 달성할 결과

이메일 인증, 관리자 초대와 비밀번호 재설정의 notification provider 실패가 같은 안전한 구조화 logging 계약을 사용하고, provider exception message·recipient·token·full stack trace를 애플리케이션 로그에 남기지 않습니다.

## 기대 동작

외부 provider 실패는 공통 `system`, `operation`, `result`, 안전한 `exceptionType` 또는 error category를 기록합니다. provider adapter가 알 수 있는 `endpointAlias`, `durationMs`, `retryCount`, fallback 여부와 final result도 기록하되, 현재 Resend adapter가 제공하지 않는 값은 임의로 만들지 않고 adapter 계약에서 확보하거나 명시적으로 `not_available`로 분류합니다. 하위 provider가 반환한 exception 원문과 stack trace는 degraded 결과를 처리하는 application 경계에서 반복 기록하지 않습니다.

## 실제 동작과 재현 조건

- `RequestMemberEmailVerificationService.sendEmailAndRecordEvent`는 발송 `RuntimeException` 객체를 `log.warn`의 마지막 인자로 넘깁니다.
- `InviteAdminService.sendInvitationAndRecordEvent`도 같은 방식으로 exception을 기록합니다.
- SLF4J는 이 인자를 throwable로 처리하므로 provider message와 full stack trace가 출력될 수 있습니다.
- `RequestPasswordResetService`는 같은 실패에서 exception class 이름만 기록하므로 세 notification 흐름의 계약도 일치하지 않습니다.

## 영향 범위

provider SDK나 하위 adapter의 exception message에 recipient, endpoint query, provider response 또는 credential 관련 값이 포함되면 로그로 전파될 수 있습니다. 동일 실패의 stack trace 반복으로 운영 신호도 커집니다.

## 원인

공통 logging 기반 이후 추가된 notification 기능들이 영구 logging SOT를 적용했지만, 신규 application-service 로그가 provider throwable을 직접 전달하지 않는다는 회귀 검증은 추가하지 않았습니다.

## 범위와 제외 범위

- 포함: 이메일 인증·관리자 초대 실패 로그, 비밀번호 재설정과의 공통 계약, 관련 테스트와 정적 검증 범위
- 제외: notification retry/outbox 정책, provider 교체, 감사 이벤트 taxonomy와 발송 성공률 metric

## 수정 계획

1. 세 흐름의 중복 로그 owner를 정하고, application과 provider adapter가 각각 소유할 field를 나눕니다.
2. 공통 최소 field인 `system`, `operation`, `result`, 안전한 `exceptionType`/category를 동일한 이름으로 기록합니다.
3. provider adapter가 측정할 수 있는 `endpointAlias`, `durationMs`, `retryCount`, fallback 여부와 final result를 구조화 field로 제공합니다. retry/fallback이 없으면 각각 `0`/`false`로 기록하고, 현재 호출 경계에서 측정할 수 없는 값은 source 근거와 함께 `not_available`로 분류합니다.
4. 이메일 인증·관리자 초대에서 throwable 직접 전달을 제거하고, 비밀번호 재설정까지 같은 field contract를 사용합니다.
5. provider exception message·recipient·raw token·full URL query·stack trace가 남지 않는 회귀 테스트를 추가합니다.
6. 관련 notification test와 `./gradlew check`를 통과한 뒤 영구 logging SOT와 결과를 다시 대조합니다.

## 검증·복구

- 각 실패 경로에서 감사 이벤트의 실패 결과는 유지되고 API의 enumeration 방지 응답이 바뀌지 않아야 합니다.
- log capture에서 `system`, `operation`, `result`, error category, `endpointAlias`, `durationMs`, `retryCount`, fallback과 final result의 적용값 또는 명시적 `not_available` 판정이 존재하고, 금지 값과 throwable stack trace는 없어야 합니다.
- 변경이 진단 가능성을 낮추면 exception 원문 대신 안정된 provider error category를 adapter 계약에 추가합니다.

## 영구 SOT 승격

새 일반 규칙은 필요하지 않습니다. 현재 [로깅 기준](../../../tech-stack/280-logging.md)을 구현과 회귀 테스트에 적용합니다.

## 관련 문서

- **위반한 현재 기준**: [로깅 기준](../../../tech-stack/280-logging.md)
- **선행 기반의 유효 범위**: [API 로깅 기반](../../features/2026/004-api-logging-foundation.md)
- **관련 기능 결과**: [이메일 인증](../../features/2026/008-member-email-verification.md), [관리자 초대](../../features/2026/011-admin-invitations.md), [비밀번호 재설정](../../features/2026/012-account-password-reset.md)
