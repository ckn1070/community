# OpenAPI 실제 응답 계약 동기화

## 상태

Done

## 결과

모든 현재 API의 생성 OpenAPI 문서를 실제 Controller·공통 예외 처리·Security 응답과
동일하게 만들고, 이후 API 변경에서도 같은 계약이 자동 검증되도록 개선했습니다.

현재 구현·검증한 결과는 다음과 같습니다.

- Bearer JWT security scheme과 operation별 public·protected 분류를 생성 명세에 적용했습니다.
- 보호 API의 실제 `401`·`403`과 공통 `400`·`415`·`500` 응답을 중앙에서 operation에
  합성합니다.
- 실제 Problem Details의 `code`와 조건부 `errors` extension을 공통 schema로 고정했습니다.
- 성공 response의 필수 field와 실제 `201 Location` header를 명시했습니다.
- 생성된 `/v3/api-docs`를 읽는 테스트가 애플리케이션의 모든 Controller를 자동 발견하고,
  전체 operation의 path·response·security·schema 집합을 검증합니다.

새 Controller를 임시 추가한 실패 증명에서 전체 operation 불변식 위반을 탐지했고,
임시 source를 제거한 뒤 focused OpenAPI 테스트와 표준 전체 검증이 통과했습니다. 독립
closure 검토에서도 blocking finding이 없음을 확인했습니다.

## 범위

- 현재 Controller가 노출하는 모든 `/api/**` operation의 성공·업무 오류 응답 보존
- 실제 MVC·Security Problem Details와 동일한 공통 오류 schema
- Security allow-list와 일치하는 public·protected OpenAPI 분류
- 보호 API의 Bearer JWT, `401`, `403`과 공통 `415`, `500` 응답
- 생성된 `/v3/api-docs`의 전체 operation 불변식과 대표 응답 schema 자동 검증

운영 profile의 Swagger UI·API docs 비활성화 정책은 유지합니다. API 업무 동작, 기존
Security 접근 정책과 실제 오류 payload는 변경하지 않습니다.

## 완료 판정

- 전체 완료 여부: 예
- 남은 책임: 없음
- 검증: 전체 Controller 자동 발견의 실패 증명, focused OpenAPI 테스트, 표준 전체 검증,
  독립 closure 검토 통과

## 영구 SOT 반영 상태

- [OpenAPI 문서화 가이드](../../../tech-stack/260-openapi.md): 중앙 합성 규칙과 자동 검증
  계약을 반영했습니다.
- [Spring Security 사용 가이드](../../../tech-stack/220-spring-security.md): 현재 public
  allow-list를 OpenAPI 분류와 함께 소비하는 단일 source 계약을 반영했습니다.

## 관련 문서

- **현재 OpenAPI 기준**: [OpenAPI 문서화 가이드](../../../tech-stack/260-openapi.md)
- **실제 인증·인가 기준**: [Spring Security 사용 가이드](../../../tech-stack/220-spring-security.md)
- **공통 오류 기반**: [API 공통 기반](../../features/2026/003-api-common-foundation.md)
