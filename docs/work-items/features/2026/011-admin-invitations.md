# 관리자 초대/수락

## 상태

In Progress

## 결과

- 완료된 기능: 최고 관리자의 관리자 초대 생성·회수, public 수락 API, token hash·24시간 만료, 같은 email 재초대, `ACTIVE` 관리자 account 생성과 `ROLE_ADMIN` 부여를 구현했습니다.
- 완료된 검증: schema, service, controller와 integration test가 당시 통과했습니다.
- 현재 판정: 완료 당시 목표였던 “기존 open 초대 회수, 새 초대 저장과 `ADMIN_INVITATION_CREATED` event를 같은 transaction에 저장”을 실제 구현이 충족하지 않았고, 동시 수락·수락-vs-회수에서 단일 terminal state와 중복 account·role 0을 증명하는 동시성 검증도 누락돼 이 작업을 다시 열었습니다.
- 실제 구현: invitation transaction을 먼저 commit한 뒤 notification을 발송하고 생성 event를 별도 `REQUIRES_NEW` transaction에 저장합니다. event 저장 실패는 이미 commit된 invitation을 rollback하지 않습니다.
- 영향: 유효한 초대가 존재하지만 생성 감사 event가 누락될 수 있고, notification 성공 여부도 추적하지 못할 수 있습니다.
- 미완료: 상태와 감사 기록에 요구할 내구성 수준, notification 결과 기록 시점, event 저장 실패의 retry·복구 계약을 확정하고 구현해야 합니다. 생성·수락·회수 metadata의 exact key·값·actor/target 결속과 민감정보 비포함을 persistence/integration test로 고정하고, 동시 수락과 수락-vs-회수 경쟁의 terminal-state·account·role 불변식을 실제 동시 실행으로 검증해야 합니다.
- 다음 실행자: 개발 작업 권한을 받은 Codex가 [상세 설계](011-admin-invitations-design.md)의 초대 생성 transaction 결정을 재검토한 뒤 구현·test·SOT를 함께 갱신합니다.
- 완료 조건: invitation commit 직후·provider 실패·감사 저장 실패를 각각 재현하고, API 성공으로 생성된 invitation의 생성 감사 사실이 누락되지 않거나 durable하게 재시도·탐지됩니다. 생성 성공·발송 실패·재초대·수락·회수에서 canonical metadata의 exact key와 값, deterministic SHA-256 email hash, actor/target 결속과 raw email·token 비포함을 직접 검증합니다. 동시 수락과 수락-vs-회수에서 terminal state는 하나만 성립하고 account graph·role assignment가 중복 생성되지 않음을 검증한 뒤 전체 Gradle 검증을 통과합니다.

## 확정 범위

- 관리자 초대 생성, 수락, 회수
- invitation token lifecycle과 동시 상태 전이
- 관리자 account graph와 role assignment
- public·super-admin API 인가
- notification과 auth event

초대 목록·상세·재발송 전용 API와 기존 회원의 관리자 승격은 제외합니다.

## 확정 결정과 재검토

[상세 설계](011-admin-invitations-design.md)는 초대 권한, 허용 role, 기존 account·재초대 처리, account 상태, token과 terminal state, API와 auth event의 완료 당시 결정을 보존합니다.

초대 생성 event의 transaction 결정은 현재 구현·실패 경계와 맞지 않으므로 이 작업에서 다시 확정합니다. 새 notification tool을 먼저 도입하지 않고 같은 transaction, outbox, durable retry와 명시적 누락 탐지의 보장·비용·중복 처리 경계를 비교합니다.

## 영구 SOT 상태

- [Account 도메인](../../../domain/100-account.md)
- [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- [Auth Event 정책](../../../domain/120-auth-event-policy.md)은 현재 after-commit·`REQUIRES_NEW` 사실과 미완료 내구성 경계를 명시합니다.
- [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)
- [Account/Auth Schema Reference](../../../project/schema/100-account-auth.md)
- [로컬 개발 환경](../../../development/100-local-development.md)

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
