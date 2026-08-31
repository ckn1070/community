# Notification 결과와 감사 저장 실패 혼합

## 상태

Done

## 달성할 결과

이메일 인증 요청과 비밀번호 재설정 요청에서 notification provider 결과와 auth event
persistence 결과를 서로 다른 실패 경계로 처리해 감사 데이터가 실제 업무 결과와 발송
결과를 왜곡하지 않게 합니다. 관리자 초대 생성 event의 원자성과 provider 결과 분리는
Feature 011이 소유합니다.

## 기대 동작

흐름별 event 의미를 다음처럼 분리합니다.

- 이메일 인증·비밀번호 재설정 요청은 provider 성공/실패가 각각 request event의 `result=SUCCESS/FAILURE`를 결정하며 현재 metadata는 `{}`입니다.
- 관리자 초대는 Feature 011에서 invitation과 `ADMIN_INVITATION_CREATED.result=SUCCESS`를 같은
  transaction에 저장하고 provider 결과를 생성 event metadata에서 제거합니다. Provider
  성공/실패를 장기 보존하려면 생성 사실과 다른 delivery 결과로 설계합니다.
- 어느 흐름에서도 성공 의미의 event 저장 실패를 provider 실패로 다시 분류하지 않습니다. 감사 저장 실패는 별도 진단·재시도·복구 계약으로 처리합니다.

## 완료 결과

- 이메일 인증·비밀번호 재설정 요청은 provider 결과를 독립 append 계약에 전달합니다.
- `IndependentSecurityAuditAppender`는 저장 예외를 `FAILED`로 바꾸므로 provider 성공을
  실패 event로 다시 분류하지 않습니다.
- append 실패는 구조화 로그로 관찰하고 source 유무에 따른 복구는 550 runbook이 소유합니다.
- 관리자 초대는 invitation과 `ADMIN_INVITATION_CREATED`를 같은 transaction에 저장하고
  commit 이후 notification만 호출합니다. Provider 실패는 생성 event를 다시 쓰거나
  `notificationResult` metadata를 만들지 않습니다.

## 영향

감사 저장 장애는 notification provider 결과와 분리되며 audit gap 신호로만 분류됩니다.

## 수정 계획

1. provider 호출 결과를 먼저 확정하고 감사 저장을 별도 예외 경계로 분리합니다.
2. 이메일 인증·비밀번호 재설정은 provider 결과를 request event `result`에 기록합니다.
   관리자 초대의 생성 `SUCCESS`는 provider 결과와 분리하고, delivery 결과를 보존할 필요와
   taxonomy·저장 owner를 이 작업에서 결정합니다.
3. 감사 저장 실패가 위 업무·delivery 의미를 바꾸지 않도록 세 service의 공통 예외 불변식을 맞춥니다.
4. 이메일 인증·비밀번호 재설정 event 저장 실패의 관찰과 복구 owner를 정합니다. 관리자 초대는 Feature 011이 선택한 내구성 mechanism을 중복 결정하지 않고 그 결과 의미만 따릅니다.
5. 각 의미 계열에서 provider 성공+event 실패, provider 실패+event 성공, 양쪽 실패를 test합니다.
6. 공개 API의 동일 응답과 token 저장 transaction 경계를 유지합니다.

## 완료 조건

- provider 성공 뒤 event 저장 실패가 notification 실패를 뜻하는 event를 만들지 않습니다.
- provider 실패 시 이메일 인증·비밀번호 재설정 request event는 `result=FAILURE`로 기록됩니다.
- provider 실패가 관리자 초대 생성 event의 `result`나 metadata를 바꾸지 않습니다. 별도
  delivery 결과를 도입하면 실제 provider 결과만 기록합니다.
- 이메일 인증과 비밀번호 재설정의 감사 저장 실패를 탐지·복구할 owner와 관찰 신호가 존재합니다.
- 관리자 초대는 Feature 011이 보장한 생성 event 내구성을 유지하면서 같은 provider 결과 의미를 사용합니다.
- 관련 service/persistence test와 `./gradlew check`가 통과합니다.

## 관련 문서

- **현재 event 의미와 gap**: [Auth Event 정책](../../../domain/120-auth-event-policy.md)
- **관리자 초대 내구성 경계**: [다시 연 관리자 초대/수락](../../features/2026/011-admin-invitations.md)
- **현재 실행 source**: `RequestMemberEmailVerificationService`, `RequestPasswordResetService`, `InviteAdminService`
- **감사 누락 복구**: [Security Audit 장애 복구](../../../operations/550-security-audit-failure-recovery-runbook.md)
- **관련 기능**: [이메일 인증](../../features/2026/008-member-email-verification.md), [다시 연 관리자 초대](../../features/2026/011-admin-invitations.md), [비밀번호 재설정](../../features/2026/012-account-password-reset.md)
