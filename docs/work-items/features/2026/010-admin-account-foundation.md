# 관리자 계정 기반

## 상태

Done

## 결과

- 운영 command로 최초 `ROLE_SUPER_ADMIN` account를 idempotent하게 생성합니다.
- 초기 비밀번호는 masked interactive input으로만 받고 원문을 저장·기록하지 않습니다.
- 초기 최고 관리자는 비밀번호를 바꾸기 전 `PUT /api/v1/auth/password` 외 일반·관리자 보호 API에 접근할 수 없습니다. `permitAll` public endpoint는 이 authority 차단과 별개입니다.
- password 변경 시 hash, 강제 변경 상태, refresh token과 auth event를 한 transaction에서 갱신합니다.
- 관리자 API에 full session, password 변경 완료와 role 조건을 적용했습니다.
- command, password change, Security, persistence와 integration test 및 최종 Gradle 검증을 통과했습니다.
- 전체 완료 여부: 예
- 남은 책임: 이 작업의 관리자 기반 완료 범위에는 없습니다. 현재 공용 `Clock` provider 위치와 command 다음 행동 문구는 각각 [공용 Clock bean의 auth 내부 소유](../../bugs/2026/003-shared-clock-bean-ownership.md), [Bootstrap 다음 행동 안내 범위 오류](../../bugs/2026/006-bootstrap-next-action-message.md)가 소유합니다.

## 범위

- 최초 최고 관리자 bootstrap
- interactive 초기 비밀번호 입력
- 강제 비밀번호 변경과 API 차단
- 관리자 role 기반 Security
- bootstrap·password change auth event

관리자 초대, 기존 회원 승격과 emergency recovery는 별도 작업입니다.

## 확정 결정 색인

[상세 설계](010-admin-account-foundation-design.md)는 다음 판단을 보존합니다.

- bootstrap command와 중복 실행 방지
- secret 입력과 console 실패 정책
- account·auth module, port와 adapter 경계
- role row lock, transaction과 rollback
- password change endpoint와 refresh token 폐기
- 관리자 API authority와 감사 이벤트

## 영구 SOT 승격

- [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- [Account 도메인](../../../domain/100-account.md)
- [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)

현재 production 실행 환경과 관리자 bootstrap 운영 절차는 확정되지 않았습니다. 기능
구현 결과를 실제 운영 가능 상태로 확대하지 않습니다.

## 관련 문서

- **결정 근거**: [상세 설계](010-admin-account-foundation-design.md)
- **후속 관리자 생성 흐름**: [관리자 초대](011-admin-invitations.md)
- **현재 구현 후속**: [공용 Clock owner](../../bugs/2026/003-shared-clock-bean-ownership.md), [Bootstrap 안내 문구](../../bugs/2026/006-bootstrap-next-action-message.md)
