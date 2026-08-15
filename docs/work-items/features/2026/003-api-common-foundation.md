# API 공통 기반: 오류 응답/예외 처리와 Security baseline

## 상태

Done

## 결과

- 공통 `ProblemDetail` 오류 계약과 예외 변환 구조를 구현했습니다.
- validation, JSON parse, 업무 예외와 예상하지 못한 예외 응답을 검증했습니다.
- Security filter 단계의 401·403도 같은 오류 schema를 사용합니다.
- 공개 endpoint를 명시하고 나머지 `/api/**`를 기본 보호하는 baseline을 만들었습니다.
- `./gradlew test`와 `./gradlew check`를 통과했습니다.
- 전체 완료 여부: 예
- 남은 책임: 이 작업의 완료 당시 공통 기반 범위에는 없습니다. 현재 Spring MVC path type·method 예외 분류 누락은 [Spring MVC framework 예외 분류 누락](../../bugs/2026/007-spring-mvc-exception-classification.md)이 소유합니다.

## 범위

- `shared.error` 공통 오류 type
- MVC 예외 처리와 Security 오류 응답
- public allow-list와 기본 보호 규칙
- 공통 기반의 module·package 위치와 자동 검증

기능별 업무 오류, JWT 발급, role 정책, CORS와 rate limit은 각 기능 작업이 소유합니다.

## 확정 결정 색인

[상세 설계](003-api-common-foundation-design.md)는 다음 판단을 보존합니다.

- Problem Details 기반 오류 응답
- error code, field error와 예외 변환 책임
- MVC와 Security 응답 schema 통일
- `shared.error`와 root `config`의 기반 구조 위치
- 공개 API allow-list와 기본 보호 정책

## 영구 SOT 승격

- [REST API 설계](../../../architecture/230-rest-api-design.md)
- [Spring Security 기준](../../../tech-stack/220-spring-security.md)
- [현재 모듈 지도](../../../project/210-module-map.md)

## 관련 문서

- **결정 근거**: [상세 설계](003-api-common-foundation-design.md)
- **첫 소비 기능**: [일반 회원 자체 가입](002-member-self-sign-up-api.md)
- **현재 framework 예외 후속**: [Spring MVC framework 예외 분류 누락](../../bugs/2026/007-spring-mvc-exception-classification.md)
