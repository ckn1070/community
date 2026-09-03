# OpenAPI 문서화 가이드

이 문서는 springdoc-openapi를 사용한 API 문서화 기준과 현재 구현 경계를 정의합니다.

## 목적

- API 계약을 코드와 함께 유지합니다.
- 클라이언트와 서버가 같은 계약을 기준으로 협업할 수 있게 합니다.
- 보안, 오류 응답, validation을 API 문서에 드러냅니다.

## 기본 원칙

- API 문서는 실제 Controller와 DTO를 기준으로 생성합니다.
- OpenAPI annotation은 문서 품질을 보완하는 목적으로 사용합니다.
- 문서가 구현과 다르면 구현 또는 문서를 즉시 맞춥니다.
- 내부 도메인 모델을 API schema로 직접 노출하지 않습니다.
- 인증이 필요한 API는 security scheme과 response를 명시합니다.

## 현재 구현 경계

현재 애플리케이션은 `springdoc-openapi-starter-webmvc-ui`와 Controller·DTO annotation을
사용합니다. production profile에서는 API docs와 Swagger UI를 비활성화하고, staging
application은 `production,staging` profile 합성으로 문서를 다시 활성화합니다.

확인 경로와 접근 조건은 다음과 같습니다.

| 환경 | Swagger UI | OpenAPI JSON | 접근 조건 |
| --- | --- | --- | --- |
| local 기본 실행 | `http://localhost:9097/swagger-ui.html` | `http://localhost:9097/v3/api-docs` | local 개발 경계에서 별도 문서 로그인 없음 |
| staging | `https://stg-api.example.com/swagger-ui.html` | `https://stg-api.example.com/v3/api-docs` | 기존 `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN` account의 Form Login |
| production | 비활성 | 비활성 | endpoint를 생성하지 않음 |

YAML 명세는 각 환경의 OpenAPI JSON 경로 대신 `/v3/api-docs.yaml`에서 확인할 수 있으며
staging에서는 같은 Form Login session으로 보호합니다.

Staging 로그인은 `ACTIVE`, full session, password-change-not-required 조건도 함께 요구합니다.
Spring Security 기본 로그인 화면을 사용하며 성공한 browser session은 Swagger 경로에서만
소비합니다. `/api/**`는 계속 무상태 Bearer JWT 경계이므로 Swagger의 Try it out으로 보호
API를 호출하려면 문서 로그인과 별도로 유효한 access token을 `Authorize`에 입력해야 합니다.
현재 staging replica는 1개이므로 in-memory `HttpSession`을 사용하며 replica를 늘릴 때 sticky
session 또는 공유 Spring Session 저장소를 재검토합니다.

생성 명세에는 다음 공통 계약을 중앙에서 적용합니다.

- 실제 Security public allow-list와 같은 source로 각 operation을 public 또는 protected로
  분류합니다.
- 보호 operation에는 Bearer JWT security requirement와 `401`·`403` Problem Details를
  추가합니다.
- request body가 있는 operation에는 실제 MVC 공통 응답인 `400`과 `415`를 추가하고, 모든
  operation에는 `500`을 추가합니다.
- 모든 오류 response는 실제 JSON의 `type`, `title`, `status`, `detail`, `instance`, `code`와
  validation 실패 시의 `errors`를 표현하는 공통 schema를 사용합니다.
- Controller annotation은 operation 고유 성공·업무 오류와 `Location` 같은 고유 header를
  소유하고, 중앙 구성은 공통 오류·보안 계약을 소유합니다.

생성된 `/v3/api-docs` 계약 테스트는 전체 operation 수와 path, 실제 response code 집합,
public·protected 분류, 모든 operation의 비어 있지 않은 `summary`·`description`, 성공 response
필수 field, `Location` header와 공통 오류 schema를 검증합니다. 기능 flag로 조건부 등록되는
Controller도 테스트 context에서 활성화해 전체 집합에 포함합니다. 새 API나 응답 변경이 이
집합을 바꾸거나 설명을 빠뜨리면 표준 Gradle `test`와 `check`가 실패하므로 실제 Controller·
Security·오류 처리와 함께 명세 기대값을 검토해야 합니다.

카탈로그의 세 GET도 같은 전체 operation 집합에 포함합니다. 모델 목록 parameter에는 첫 진입
기본값(`ON_SALE`, `NAME_ASC`, page `0`, size `20`, 서울 현재 날짜)을 설명하고, 목록·상세
response의 선택 값은 OpenAPI 3.1 union type에 `null`을 포함해 실제 JSON의 명시적 `null`과
구분이 생기지 않게 합니다. 공개 조회의 고유 오류인 `400`·`404`·`405`는 Controller가,
공통 `500`과 public 분류는 중앙 구성이 소유합니다.

## springdoc 기준

springdoc-openapi 공식 문서는 Spring Boot 프로젝트의 설정, class 구조, annotation을 분석해 OpenAPI 문서를 생성한다고 설명합니다.
Spring Boot 4 지원은 springdoc v3 라인을 기준으로 확인합니다.

프로젝트 기준:

- dependency는 `springdoc-openapi-starter-webmvc-ui`를 사용합니다.
- 기본 문서 endpoint와 Swagger UI 노출 정책을 환경별로 검토합니다.
- 운영 환경에서 문서 UI 공개 여부를 보안 정책과 함께 결정합니다.
- API 그룹이 늘어나면 group별 OpenAPI 구성을 검토합니다.

## Controller 문서화 기준

- 모든 endpoint의 목적을 비어 있지 않은 summary로 설명합니다.
- 모든 endpoint에 API 소비자가 대상·행동·핵심 조건을 이해할 수 있는 description을 추가합니다.
- request/response DTO field description을 필요한 범위에서 작성합니다.
- 오류 응답은 공통 error schema와 HTTP status를 명시합니다.
- 인증/인가 조건이 있으면 문서에 드러냅니다.
- pagination, sorting, filtering parameter는 의미와 기본값을 적습니다.

## DTO 기준

- Request DTO는 validation annotation과 문서 설명이 일치해야 합니다.
- Response DTO는 클라이언트에 노출 가능한 필드만 포함합니다.
- nullable 여부를 명확히 합니다.
- enum 값은 의미가 드러나게 이름 짓고 설명합니다.
- 예시가 필요한 API는 example을 추가합니다.

## 아키텍처 경계 적용

- OpenAPI annotation은 Web Adapter DTO에 둡니다.
- Domain Model에 API 문서화 annotation을 두지 않습니다.
- API 계약 변경은 UseCase 입력/출력 변경과 함께 검토합니다.
- Response DTO는 Domain 또는 Read Model에서 변환합니다.

## 보안 기준

- Swagger UI와 `/v3/api-docs` 접근 정책을 Spring Security 설정과 함께 관리합니다.
- 운영 환경에서 공개하면 민감한 schema나 내부 endpoint가 노출되지 않는지 확인합니다.
- 인증이 필요한 API는 중앙 구성에서 security scheme과 401/403 응답을 적용합니다.

## 테스트 기준

- 주요 API는 문서화된 request/response와 실제 Controller 테스트가 일치해야 합니다.
- validation 실패 응답도 API 계약으로 관리합니다.
- 생성된 OpenAPI spec의 전체 path·response·schema·security 계약을 읽는 회귀 테스트를 둡니다.

## 피해야 할 패턴

- 문서를 위해 Domain Model에 annotation을 추가합니다.
- 실제 validation과 다른 schema 설명을 둡니다.
- 오류 응답을 문서화하지 않습니다.
- 운영에서 Swagger UI를 무조건 공개합니다.
- API DTO 없이 Entity를 그대로 노출합니다.

## 관련 참고

- [springdoc-openapi v3 Documentation](https://springdoc.org/v4/)
- [OpenAPI Specification](https://spec.openapis.org/oas/latest.html)

## 관련 문서

- **web 구현 기반**: [Spring Boot](210-spring-boot.md)
- **security scheme 기준**: [Spring Security](220-spring-security.md)
- **DTO·domain 경계**: [경계와 매핑 규칙](../architecture/220-boundary-and-mapping-rules.md)
- **응답 계약 구현 기록**: [007 OpenAPI 실제 응답 계약 동기화](../work-items/improvements/2026/007-openapi-response-contract.md)
- **staging 접근 보호 작업 기록**: [008 Staging Swagger 관리자 접근 보호](../work-items/improvements/2026/008-staging-swagger-admin-access.md)
