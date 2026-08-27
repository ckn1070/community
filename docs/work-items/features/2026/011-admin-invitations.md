# 관리자 초대/수락

## 상태

In Progress

## 결과

- 같은 email의 기존 open 초대 회수, 새 `admin_invitation`과
  `ADMIN_INVITATION_CREATED`를 하나의 DB transaction에 저장합니다. 생성 이벤트 저장이
  실패하면 초대 변경 전체가 rollback되고 notification은 호출되지 않습니다.
- notification은 commit 이후에만 발송합니다. Provider 실패는 API의 `201 Created`, 이미
  저장된 초대와 생성 이벤트를 바꾸지 않으며 생성 metadata를 다시 쓰지 않습니다.
- 생성 이벤트 metadata는 `invitationPublicId`, `targetEmailHash`, `roleCode`,
  `previousInvitationRevoked`만 소유합니다. 수락·회수 metadata도 기존 exact key와 actor/
  target 결속을 유지하며 raw email·token·token hash·password·provider 응답을 저장하지
  않습니다.
- 실제 동시 수락과 수락-vs-회수는 pessimistic row lock 뒤 terminal state를 재확인해
  승자 하나와 그 결과에 해당하는 account graph·role·event만 남깁니다.
- public API, Security allow-list, token TTL, `admin_invitation` schema와 기존 event row는
  변경하지 않았습니다.

Local source·PostgreSQL 통합 test·전체 품질 검증과 영구 SOT 동기화는 완료했습니다. Push된
final exact SHA의 required CI와 Sonar revision·Quality Gate·Open issues를 확인하지 않았으므로
상태는 `In Progress`를 유지합니다.

## 남은 책임

- Push 권한을 가진 실행자가 final exact SHA를 remote branch에 반영합니다.
- 같은 SHA의 required CI 성공과 Sonar revision 일치, Quality Gate `OK`, overall open issues
  0건을 확인한 뒤에만 이 문서와 feature router를 `Done`으로 전환합니다.

## 확정 결정

[상세 설계](011-admin-invitations-design.md)는 기존 초대 계약과 다음 결과 경계를 보존합니다.

- 초대·생성 event의 transaction 원자성
- 생성 사실 event와 notification delivery 결과의 분리
- 생성·수락·회수 metadata allow-list와 민감정보 경계
- pessimistic row lock 기반 수락·회수 terminal-state 경쟁 계약
- outbox·delivery retry와 public API·schema 비변경 경계

## 영구 SOT

- [Auth Event 정책](../../../domain/120-auth-event-policy.md): 생성 event transaction과 metadata
  allow-list
- [Account 인증 데이터 모델](../../../project/220-account-auth-data-model.md): 관리자 초대 생성의
  invitation·event·notification transaction 경계
- [Notification 결과와 감사 저장 실패 혼합](../../bugs/2026/005-notification-audit-result-separation.md):
  생성 사실과 분리된 delivery 결과 책임

## 별도 후속 범위

아래 항목은 초대 생성 event 원자성의 완료 조건과 다른 독립 결함입니다.

- **notification**: [Provider logging](../../bugs/2026/001-notification-provider-exception-logging.md), [Delivery/audit 결과 분리](../../bugs/2026/005-notification-audit-result-separation.md)
- **조립**: [공용 Clock owner](../../bugs/2026/003-shared-clock-bean-ownership.md)
- **공통 API**: [Spring MVC framework 예외 분류](../../bugs/2026/007-spring-mvc-exception-classification.md)
- **설정 구조**: [Notification 설정 properties 경계 정리](../../improvements/2026/006-notification-configuration-properties.md)

## 관련 문서

- **결정 근거**: [상세 설계](011-admin-invitations-design.md)
- **선행 관리자 정책**: [관리자 계정 기반](010-admin-account-foundation.md)
- **수락 후 credential 흐름**: [계정 비밀번호 재설정](012-account-password-reset.md)
