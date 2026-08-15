# 일반 회원 자체 가입 API

## 상태

Done

## 결과

- `POST /api/v1/members` 일반 회원 자체 가입 API를 구현했습니다.
- account, profile, password credential와 `ROLE_MEMBER` assignment를 한 transaction에서 생성합니다.
- account는 이메일 인증 전 `PENDING`으로 시작하고, 완료 당시 taxonomy의 `SIGN_UP/SUCCESS`로 가입 성공을 기록했습니다.
- `Account` aggregate가 생성 규칙과 domain event를 소유하도록 구조를 보정했습니다.
- 관련 domain, application, persistence, web와 integration test 및 최종 Gradle 검증을 통과했습니다.
- 전체 완료 여부: 예
- 남은 책임: 없음

## 범위

- 일반 회원 자체 가입
- email·display name 정규화와 중복 검증
- password policy와 Argon2id hashing
- account graph 저장과 가입 감사 이벤트

관리자 생성·초대, 로그인, refresh token과 이메일 인증 완료는 별도 기능 작업입니다.

## 확정 결정 색인

[상세 설계](002-member-self-sign-up-api-design.md)는 다음 판단을 보존합니다.

- 외부 `/members` resource와 내부 `account` 생명주기 경계
- `PENDING` 초기 상태
- password policy와 `DelegatingPasswordEncoder`·Argon2id
- email·display name 정규화와 재사용 정책
- aggregate, use case, persistence payload와 domain event 경계
- API 계약과 transaction 경계

## 영구 SOT 승격

- [Account 도메인](../../../domain/100-account.md)
- [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)
- [REST API 설계](../../../architecture/230-rest-api-design.md)
- [Spring Security 기준](../../../tech-stack/220-spring-security.md)

## 관련 문서

- **결정 근거**: [상세 설계](002-member-self-sign-up-api-design.md)
- **선행 데이터 계약**: [DB Schema](001-account-auth-db-schema.md)
- **공통 HTTP 계약**: [API 공통 기반](003-api-common-foundation.md)
- **후속 taxonomy 대체**: [Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md)
