# RESTful API 설계 가이드

이 문서는 프로젝트 전반에서 HTTP API를 RESTful하게 설계하기 위한 기준을 정의합니다.
단순히 Controller, DTO, OpenAPI annotation을 어떻게 작성할지가 아니라, API 표면을 어떤 리소스와 HTTP 의미로 모델링할지에 집중합니다.

## 상황별 읽기 위치

| 상황 | 확인할 위치 |
| --- | --- |
| API 설계 순서와 public 이름 검토 | [설계 순서](#설계-순서), [Public API 이름 검증](#public-api-이름-검증) |
| URI, query와 HTTP method 결정 | [URI 설계](#uri-설계), [Query Parameter 설계](#query-parameter-설계), [HTTP Method 기준](#http-method-기준) |
| 성공·실패 응답과 오류 형식 결정 | [성공 응답 Status Code](#성공-응답-status-code), [실패 응답 Status Code](#실패-응답-status-code), [오류 응답 형식](#오류-응답-형식) |
| 보안·멱등성·OpenAPI·테스트 기준 | [동시성, 재시도, 멱등성](#동시성-재시도-멱등성), [보안 설계](#보안-설계), [OpenAPI 문서화 기준](#openapi-문서화-기준), [테스트 기준](#테스트-기준) |
| 전체 설계 완료 전 점검 | [설계 체크리스트](#설계-체크리스트) |

## 목적

- API를 행위 중심 RPC가 아니라 리소스 중심 HTTP 인터페이스로 설계합니다.
- 클라이언트가 일관된 URI, HTTP method, status code, 오류 응답을 예측할 수 있게 합니다.
- API 계약이 DB 구조, JPA Entity, 내부 Domain Model에 직접 묶이지 않게 합니다.
- Spring MVC, OpenAPI, Security, 테스트 기준이 같은 API 설계 원칙을 따르게 합니다.

## 적용 범위

이 문서는 JSON 기반 HTTP API를 설계할 때 적용합니다.

- Public API
- 인증이 필요한 사용자 API
- 관리자 API
- 모듈 간 HTTP API
- OpenAPI 문서화 대상 API

다음에는 직접 적용하지 않습니다.

- 서버 내부 Java interface
- Domain event
- DB schema
- GraphQL, WebSocket, gRPC 같은 별도 통신 방식

단, 별도 통신 방식을 사용하더라도 리소스 경계, 오류 표현, 보안 노출 기준은 참고할 수 있습니다.

## REST와 프로젝트 기준

REST는 URL 네이밍 규칙이 아니라 분산 하이퍼미디어 시스템을 위한 아키텍처 스타일입니다.
핵심 제약은 client-server, stateless, cache, uniform interface, layered system, 선택적 code-on-demand입니다.

프로젝트의 HTTP API는 다음 수준을 기본 목표로 둡니다.

- REST의 핵심 제약을 따르는 resource-oriented HTTP API를 지향합니다.
- HTTP method, status code, cache header, content negotiation 같은 HTTP 의미를 존중합니다.
- 리소스는 DB table이 아니라 클라이언트가 이해하는 공개 개념으로 모델링합니다.
- HATEOAS는 기본 강제하지 않습니다.
- 링크 기반 탐색이 실제 클라이언트 복잡도를 줄이는 API에서만 hypermedia 적용을 검토합니다.

즉, 이 프로젝트에서 "RESTful"은 엄격한 REST 성숙도 점수 자체가 아니라, 리소스 중심 모델링과 HTTP 표준 의미를 일관되게 적용한다는 뜻입니다.

## 설계 순서

API는 다음 순서로 설계합니다.

1. 유스케이스의 결과로 생성되거나 조회되는 공개 리소스를 찾습니다.
2. 리소스의 소유 관계, 생명주기, 식별자를 정합니다.
3. 컬렉션과 단일 리소스 URI를 정합니다.
4. public API 이름이 일반적으로 쓰이는 형태인지, API 소비자가 내부 구현을 몰라도 이해할 수 있는지 확인합니다.
5. 표준 HTTP method로 표현 가능한지 먼저 판단합니다.
6. request/response 표현을 DTO로 정의합니다.
7. 성공 status code와 실패 status code를 정합니다.
8. 오류 응답 code, message, field error를 정합니다.
9. 인증, 인가, CSRF, CORS, rate limit 영향을 확인합니다.
10. 캐시, 동시성, idempotency가 필요한지 판단합니다.
11. OpenAPI 문서와 Controller 테스트 기준을 함께 정의합니다.

이 순서를 건너뛰고 Controller path부터 만들면 URI가 행위 중심으로 흐르기 쉽습니다.

## Public API 이름 검증

Public API 이름은 서버 내부 구현보다 API 소비자가 이해하는 공개 개념을 우선합니다.
REST 리소스 모델링이 가능하더라도 그 이름이 일반적으로 쓰이지 않고 내부 정책을 과하게 드러내면 기본 추천안으로 두지 않습니다.

endpoint 후보를 추천하기 전에 다음을 확인합니다.

| 검증 항목 | 기준 |
| --- | --- |
| 일반적 관용성 | 인증, 결제, OAuth, 파일 업로드처럼 익숙한 API 영역은 업계에서 많이 쓰는 형태를 먼저 후보로 둡니다. |
| 소비자 이해도 | 클라이언트 개발자가 내부 DB 구조, 보안 정책, 구현 usecase 이름을 몰라도 의미를 이해할 수 있어야 합니다. |
| 내부 정책 노출 | rotation, lock, projection, outbox, persistence 같은 내부 구현 정책은 외부 path에 드러내지 않는 편을 우선합니다. |
| 프로젝트 일관성 | 이미 정한 namespace와 리소스 흐름이 있으면 그 흐름을 우선합니다. |
| 비주류 후보 표시 | 일반적이지 않은 이름을 추천하려면 비주류라는 사실, 장점, 감수할 비용, 대안을 함께 문서화합니다. |

설계 문서에서 endpoint를 확정할 때는 후보 비교를 남깁니다.
비교 없이 최종 path만 제시하지 않습니다.

| 후보 분류 | 문서에 남길 내용 |
| --- | --- |
| 관용 후보 | 해당 API 영역에서 흔히 쓰이는 이름과 그 의미입니다. |
| 프로젝트 일관성 후보 | 기존 namespace, resource 흐름, 도메인 설계와 맞는 이름입니다. |
| 추천 후보 | 실제 추천할 endpoint와 추천 이유입니다. |
| 제외 후보 | 어색하거나 비주류인 endpoint와 제외 이유입니다. |

추천 후보는 관용 후보나 프로젝트 일관성 후보 중에서 고릅니다.
REST resource처럼 만들 수 있다는 이유만으로 소비자가 잘 쓰지 않는 복합 명사나 인위적인 collection 이름을 기본 추천안으로 두지 않습니다.
비주류 이름을 선택해야 한다면 추천 후보가 아니라 `비주류 후보`로 표시하고 사용자에게 비용을 먼저 설명합니다.

외부 endpoint 이름과 내부 구현 이름은 다를 수 있습니다.
예를 들어 refresh token 재발급은 내부적으로 refresh token rotation을 수행하더라도, public endpoint는 사용자에게 익숙한 session refresh나 token refresh 표현을 우선 검토합니다.
내부 usecase나 policy 이름은 rotation 정책을 정확히 드러내도 됩니다.

관용적인 인증 API 예시는 다음과 같습니다.

```text
POST /api/v1/auth/sessions
POST /api/v1/auth/sessions/refresh
POST /api/v1/auth/refresh
POST /api/v1/auth/tokens/refresh
POST /oauth/token
```

`POST /oauth/token`은 OAuth/OIDC 서버를 제공할 때 자연스럽고, 자체 session API를 설계하는 경우에는 프로젝트의 기존 auth namespace와 session 흐름을 우선합니다.
인증 API처럼 관용적 표현이 강한 영역에서는 순수한 resource-oriented 이름보다 소비자 이해도와 기존 관례를 우선할 수 있습니다.

이메일 인증처럼 목적과 전달 채널이 분리되는 API는 복합 명사를 억지로 만들기 전에 계층형 path를 먼저 검토합니다. 예를 들어 다음 계층은 목적과 채널을 분리해 읽을 수 있습니다.

```text
POST /api/v1/auth/verifications/email
POST /api/v1/auth/verifications/email/confirm
```

`/api/v1/auth/email-verifications` 같은 단일 복합 명사도 가능하므로 실제 프로젝트는 기존 namespace와 소비자 관용성을 함께 비교해 선택합니다.
`email-verification-confirmations`처럼 리소스처럼 보이지만 소비자에게 어색한 이름은 기본 추천안으로 두지 않습니다.
하이픈은 path segment 안의 여러 단어를 연결할 때 사용할 수 있지만, 도메인 축을 나눌 수 있으면 path 계층으로 분리하는 편을 먼저 검토합니다.

## 리소스 모델링

리소스는 API 소비자가 다루는 공개 개념입니다.
리소스는 반드시 DB table, JPA Entity, Domain class와 1:1로 대응하지 않습니다.

좋은 리소스 후보는 다음과 같습니다.

- Member
- Post
- Comment
- CarModel
- Attachment
- Notification
- Session
- RefreshToken

나쁜 리소스 후보는 다음과 같습니다.

- CreateMember
- ProcessSignUp
- GetPostList
- UpdateStatus
- ValidatePassword

리소스 모델링 기준은 다음과 같습니다.

- 명사로 이름 붙일 수 있어야 합니다.
- 클라이언트가 식별하거나 참조할 수 있어야 합니다.
- 생성, 조회, 수정, 삭제, 목록 조회 중 일부 표준 동작을 가질 수 있어야 합니다.
- 내부 DB id 대신 public id를 외부 식별자로 사용합니다.
- 한 리소스에는 가능한 한 하나의 canonical URI를 둡니다.
- 하위 리소스는 소유 관계나 생명주기가 상위 리소스에 강하게 묶일 때만 중첩합니다.

예시:

```text
회원가입
행위 표현: POST /api/v1/auth/sign-up
리소스 표현: POST /api/v1/members

회원가입 결과가 member 생성이라면 POST /api/v1/members가 기본 후보입니다.
```

## URI 설계

URI는 리소스의 위치와 계층을 표현합니다.
행위는 URI가 아니라 HTTP method로 표현합니다.

기본 형식은 다음과 같습니다.

```text
/api/v1/{resources}
/api/v1/{resources}/{resourceId}
/api/v1/{resources}/{resourceId}/{subResources}
/api/v1/{resources}/{resourceId}/{subResources}/{subResourceId}
```

예시:

```text
GET    /api/v1/members/{memberId}
GET    /api/v1/posts
POST   /api/v1/posts
GET    /api/v1/posts/{postId}
PATCH  /api/v1/posts/{postId}
DELETE /api/v1/posts/{postId}
GET    /api/v1/posts/{postId}/comments
POST   /api/v1/posts/{postId}/comments
```

URI 작성 기준은 다음과 같습니다.

- 컬렉션은 복수 명사를 사용합니다.
- path segment는 소문자와 hyphen을 사용합니다.
- path variable 이름은 코드와 문서에서 의미가 드러나게 작성합니다.
- 동사를 path에 넣지 않습니다.
- 파일 확장자를 붙이지 않습니다.
- trailing slash 없는 형태를 canonical URI로 둡니다.
- DB table명, column명, JPA 연관관계명을 그대로 노출하지 않습니다.
- 중첩은 최대 2단계 정도로 제한하고, 더 깊어지면 독립 리소스 URI를 검토합니다.

피해야 할 예:

```text
POST /api/v1/createMember
GET  /api/v1/getPosts
POST /api/v1/posts/{postId}/updateStatus
GET  /api/v1/tb_member/{id}
GET  /api/v1/posts/list
```

## Query Parameter 설계

조회 조건, 정렬, pagination은 query parameter로 표현합니다.

예시:

```text
GET /api/v1/posts?authorId={memberId}&status=PUBLISHED&page=0&size=20&sort=createdAt,desc
GET /api/v1/car/models?q=sonata&brandId={brandId}&page=0&size=20&sort=NAME_ASC
```

기준은 다음과 같습니다.

- filter 조건은 공개 API 용어를 사용합니다.
- SQL, JPA, DB column 이름을 query parameter로 노출하지 않습니다.
- pagination 기본값과 최대 size를 정합니다.
- 일반 목록은 `page`, `size`, `sort`를 우선합니다.
- 무한 스크롤이나 대용량 feed는 cursor 기반 pagination을 검토합니다.
- 정렬 가능한 필드는 allow-list로 제한합니다.
- 빈 검색 결과는 `200 OK`와 빈 목록을 반환합니다.

## HTTP Method 기준

HTTP method는 리소스에 대한 표준 동작을 표현합니다.

| Method | 의미 | 안전성 | 멱등성 | 사용 기준 |
| --- | --- | --- | --- | --- |
| GET | 조회 | 안전 | 멱등 | 리소스 또는 컬렉션 조회 |
| POST | 생성 또는 명령 처리 | 안전하지 않음 | 기본적으로 멱등 아님 | 컬렉션에 새 리소스 생성, 표준 method로 표현하기 어려운 command |
| PUT | 전체 교체 | 안전하지 않음 | 멱등 | 클라이언트가 리소스 전체 표현을 알고 교체할 때 |
| PATCH | 부분 수정 | 안전하지 않음 | 설계에 따라 다름 | 일부 필드 변경, 상태 변경 |
| DELETE | 삭제 | 안전하지 않음 | 멱등 | 리소스 삭제 또는 삭제 요청 |

적용 기준은 다음과 같습니다.

- `GET`은 서버 상태를 변경하지 않습니다.
- `GET` request body에 의미를 두지 않습니다.
- 생성은 `POST /collection`을 기본으로 합니다.
- 단일 리소스 전체 교체가 명확하지 않으면 `PUT`보다 `PATCH`를 우선 검토합니다.
- 부분 수정 request는 변경 가능한 필드만 받습니다.
- 삭제가 soft delete라면 응답과 이후 조회 정책을 함께 정의합니다.
- 재시도에 따른 중복 생성이 문제 되는 `POST`는 idempotency key를 검토합니다.

## Custom Action 기준

표준 method로 표현하기 어려운 도메인 행위가 있을 수 있습니다.
그 경우에도 먼저 리소스로 재해석할 수 있는지 확인합니다.

우선 검토 순서는 다음과 같습니다.

1. 새로운 리소스 생성으로 표현할 수 있는가
2. 기존 리소스의 상태 변경으로 표현할 수 있는가
3. 하위 리소스 생성으로 표현할 수 있는가
4. 그래도 어렵다면 `POST` 기반 custom action을 사용할 수 있는가

예시:

```text
좋음: POST /api/v1/posts/{postId}/likes
좋음: DELETE /api/v1/posts/{postId}/likes/{memberId}
검토 가능: POST /api/v1/posts/{postId}:publish
피함: POST /api/v1/publishPost
```

custom action을 사용할 때는 다음을 문서화합니다.

- 표준 method로 표현하지 않은 이유
- action이 변경하는 리소스
- 성공 status code
- 실패 status code
- 재시도와 멱등성 정책

## 성공 응답 Status Code

성공 응답은 HTTP 표준 의미와 body 유무를 함께 고려합니다.

| 상황 | Status | Body | 기준 |
| --- | --- | --- | --- |
| 단일 리소스 조회 | 200 OK | 있음 | 리소스 표현 반환 |
| 컬렉션 조회 | 200 OK | 있음 | 빈 결과도 200과 빈 목록 |
| 생성 성공 | 201 Created | 있음 | 생성된 리소스 표현 반환 |
| 비동기 작업 접수 | 202 Accepted | 선택 | 처리 완료가 아닌 접수 |
| 수정 성공 | 200 OK | 있음 | 수정된 리소스 표현 반환 |
| 수정 성공, 반환 없음 | 204 No Content | 없음 | 클라이언트가 응답 표현이 필요 없을 때 |
| 삭제 성공 | 204 No Content | 없음 | 삭제 후 반환할 표현이 없을 때 |

생성 응답은 가능하면 `Location` header에 선택한 primary resource identifier를 포함합니다. HTTP `Location`은 생성 resource의 식별자를 전달하며 그 URI에 대한 `GET` 지원까지 자동으로 요구하지 않습니다. 조회 가능성을 프로젝트 계약으로 요구하려면 생성 API 전체에 일관되게 적용하고 권한·개인정보·404 노출을 함께 설계합니다.

현재 회원가입은 `/api/v1/members/{memberId}`, 관리자 초대 생성은 `/api/v1/admin/invitations/{invitationId}`, 관리자 초대 수락은 `/api/v1/accounts/{accountId}`를 생성 resource identifier로 반환합니다. 세 path 모두 현재 같은 URI의 `GET` route를 제공하지 않습니다. 세 identifier의 resource 의미와 project-level 조회 정책은 [Account 후속 backlog](../work-items/backlog/2026/003-account-auth-follow-up.md#생성-resource-uri-정책)가 소유합니다.

`202 Accepted`는 서버가 요청을 접수했지만 해당 operation의 처리가 아직 끝나지 않았거나 최종 결과를 응답에서 확정하지 않는다는 뜻입니다. durable queue·outbox는 reliability 선택이지 status 자체의 필수 조건이 아닙니다. 현재 이메일 인증·비밀번호 재설정 요청은 response 전에 provider send API가 email id를 반환하지만 실제 delivery 성공·실패는 이후에 확정되므로 `202`를 사용합니다. provider 결과와 감사 저장의 내구성은 [Notification 결과와 감사 저장 실패 혼합](../work-items/bugs/2026/005-notification-audit-result-separation.md)이 별도로 소유합니다.

## 실패 응답 Status Code

실패 응답은 클라이언트가 후속 행동을 결정할 수 있게 구분합니다.

아래는 목표 계약입니다. 현재 path type mismatch와 unsupported HTTP method 일부가 공통 handler의 generic 500으로 분류될 수 있으며, 구현 보완과 회귀 검증은 [Spring MVC framework 예외 분류 누락](../work-items/bugs/2026/007-spring-mvc-exception-classification.md)이 소유합니다.

| 상황 | Status | 기준 |
| --- | --- | --- |
| JSON 형식 오류 | 400 Bad Request | 요청을 해석할 수 없음 |
| request validation 실패 | 400 Bad Request | 필수값, 형식, 길이 오류 |
| 인증 누락 또는 실패 | 401 Unauthorized | 인증하면 다시 시도 가능 |
| 권한 부족 | 403 Forbidden | 인증되었지만 허용되지 않음 |
| 리소스 없음 | 404 Not Found | 요청 리소스가 존재하지 않거나 노출 불가 |
| method 불일치 | 405 Method Not Allowed | 리소스는 있으나 method 미지원 |
| media type 불일치 | 415 Unsupported Media Type | 지원하지 않는 Content-Type |
| 동시성 조건 실패 | 412 Precondition Failed | If-Match 등 조건부 요청 실패 |
| 중복 또는 상태 충돌 | 409 Conflict | email 중복, 이미 처리된 상태 등 |
| 의미 검증 실패 | 422 Unprocessable Content | 400과 구분하기로 한 경우에만 사용 |
| rate limit 초과 | 429 Too Many Requests | 재시도 제한 |
| 서버 내부 오류 | 500 Internal Server Error | 예상하지 못한 서버 오류 |

프로젝트 기본값은 validation 실패에 `400 Bad Request`를 사용합니다.
`422 Unprocessable Content`는 형식 검증과 의미 검증을 API 계약에서 명확히 나누기로 결정한 경우에만 사용합니다.

오류 응답에는 다음을 포함하지 않습니다.

- stack trace
- 내부 DB id
- password 원문
- token 값
- SQL
- 내부 class/package 이름
- 권한 우회를 돕는 상세 정보

## 오류 응답 형식

공통 오류 응답은 RFC 9457 Problem Details를 우선 기준으로 설계합니다.
Spring의 `ProblemDetail`을 사용할 수 있으므로 Spring MVC 오류 변환과도 잘 맞습니다.

기본 필드는 다음과 같습니다.

| 필드 | 의미 |
| --- | --- |
| `type` | 오류 유형을 식별하는 URI |
| `title` | 사람이 읽을 수 있는 짧은 오류 제목 |
| `status` | HTTP status code |
| `detail` | 구체적인 오류 설명 |
| `instance` | 오류가 발생한 요청 또는 문제 인스턴스 식별자 |
| `code` | 클라이언트 분기를 위한 프로젝트 오류 코드 |
| `errors` | field validation 오류 목록 |

예시:

```json
{
  "type": "https://api.car-community.example/problems/email-already-used",
  "title": "Email already used",
  "status": 409,
  "detail": "이미 사용 중인 이메일입니다.",
  "instance": "/api/v1/members",
  "code": "EMAIL_ALREADY_USED"
}
```

validation 오류 예시:

```json
{
  "type": "https://api.car-community.example/problems/invalid-request",
  "title": "Invalid request",
  "status": 400,
  "detail": "요청 값이 올바르지 않습니다.",
  "instance": "/api/v1/members",
  "code": "INVALID_REQUEST",
  "errors": [
    {
      "field": "email",
      "code": "EMAIL_INVALID",
      "message": "올바른 이메일 형식이어야 합니다."
    }
  ]
}
```

오류 code는 HTTP status를 대체하지 않습니다.
HTTP status는 프로토콜 수준 의미를 표현하고, `code`는 프로젝트 도메인 또는 validation 오류를 세분화합니다.

현재 프로젝트의 실제 오류 owner·mapping과 endpoint별 code는 이 일반 가이드가 복제하지 않습니다. [API 공통 기반 결과](../work-items/features/2026/003-api-common-foundation.md)와 현재 Web source에서 확인합니다.

## Request와 Response 표현

API 표현은 DTO로 정의합니다.
JPA Entity와 Domain Model을 request/response body로 직접 사용하지 않습니다.

기준은 다음과 같습니다.

- JSON field는 `camelCase`를 사용합니다.
- Request DTO와 Response DTO를 분리합니다.
- 생성 request와 수정 request를 분리합니다.
- Response DTO에는 클라이언트에 노출 가능한 필드만 포함합니다.
- 내부 상태, 권한, 감사 필드는 기본적으로 숨깁니다.
- enum은 API 계약이므로 이름과 의미를 OpenAPI에 문서화합니다.
- nullable 여부를 명확히 합니다.
- 알 수 없는 값이 필요한 확장 지점은 enum보다 문자열 code를 검토합니다.
- 날짜와 시간은 ISO-8601 형식을 사용합니다.
- 서버 기준 시각은 timezone 또는 offset 의미를 명확히 합니다.

리소스 관계 표현은 다음 기준을 따릅니다.

- 목록 응답은 필요한 요약 정보만 포함합니다.
- 상세 응답은 클라이언트 화면과 권한에 필요한 범위까지만 확장합니다.
- 연관 객체 전체를 무조건 중첩하지 않습니다.
- 필요하면 id, display name, summary 같은 작은 참조 표현을 사용합니다.

## Versioning

API version은 breaking change를 관리하기 위한 계약입니다.

기본 기준은 다음과 같습니다.

- URI path versioning을 기본으로 합니다.
- 시작 version은 `/api/v1`을 사용합니다.
- breaking change는 새 major version에서 처리합니다.
- non-breaking change는 같은 version 안에서 처리합니다.
- version이 다른 API를 오래 병행해야 하면 deprecation 정책을 문서화합니다.

breaking change 예시는 다음과 같습니다.

- 기존 field 제거
- field 의미 변경
- field type 변경
- enum 값 제거 또는 의미 변경
- status code 의미 변경
- 인증 방식 변경
- pagination 응답 구조 변경

non-breaking change 예시는 다음과 같습니다.

- optional response field 추가
- 새 query parameter 추가
- 새 enum 값 추가
- 새 endpoint 추가
- 오류 detail 설명 개선

클라이언트가 enum exhaustiveness에 취약하면 새 enum 값 추가도 breaking change처럼 다룰 수 있습니다.

## Cache

REST의 중요한 제약 중 하나는 cache입니다.
모든 API를 캐시할 필요는 없지만, 조회 API는 캐시 가능성을 설계 단계에서 판단합니다.

기준은 다음과 같습니다.

- `GET` 응답은 cache 가능 여부를 검토합니다.
- 민감한 사용자별 응답은 `Cache-Control: no-store` 또는 private cache 정책을 검토합니다.
- 공개 조회 데이터는 `Cache-Control`, `ETag`, `Last-Modified`를 검토합니다.
- 조건부 요청이 유용하면 `If-None-Match`, `If-Modified-Since`를 지원할 수 있습니다.
- mutation 응답 이후 클라이언트 캐시 무효화 방식을 고려합니다.

캐시 정책이 정해지지 않은 상태에서 proxy나 browser가 민감한 응답을 저장하지 않게 주의합니다.

## 동시성, 재시도, 멱등성

분산 환경에서는 같은 요청이 여러 번 도착하거나 오래된 상태를 기준으로 수정될 수 있습니다.

기준은 다음과 같습니다.

- 중복 생성이 문제가 되는 `POST`는 idempotency key를 검토합니다.
- 결제, 주문, 초대, 메일 발송처럼 부작용이 큰 API는 재시도 정책을 명시합니다.
- 낙관적 동시성 제어가 필요한 수정 API는 `ETag`와 `If-Match`를 검토합니다.
- 조건부 수정 실패는 `412 Precondition Failed`를 우선 검토합니다.
- 도메인 상태 충돌은 `409 Conflict`를 사용합니다.
- 같은 `DELETE`를 반복 호출했을 때의 응답 정책을 문서화합니다.

멱등성은 클라이언트 재시도 안전성과 직접 연결되므로, API 계약에 드러나야 합니다.

## 보안 설계

RESTful URI와 method가 좋아도 보안 경계가 불명확하면 API 계약으로 부족합니다.

각 endpoint는 다음을 명확히 합니다.

- public API인지 인증 필요 API인지
- 필요한 role 또는 authority
- 인증 실패 시 `401`인지, 권한 부족 시 `403`인지
- CSRF 적용 또는 예외 여부
- CORS 허용 출처와 method
- rate limit 필요 여부
- 응답에 포함 가능한 개인정보 범위
- OpenAPI와 Swagger UI 노출 정책

기준은 다음과 같습니다.

- 임시 개발 편의를 위해 전체 `/api/**`를 `permitAll`로 열지 않습니다.
- public endpoint는 명시적으로 허용합니다.
- 인증이 필요한 API는 OpenAPI security scheme에 반영합니다.
- 오류 응답으로 사용자 존재 여부나 권한 구조를 과도하게 노출하지 않습니다.
- 비밀번호, 인증 코드와 server secret은 response body와 log에 남기지 않습니다.
- access·refresh token 원문은 로그인·재발급처럼 token 발급 자체가 목적인 성공 응답에만 계약으로 노출하고, 다른 응답이나 log에는 남기지 않습니다. refresh token의 server-side 저장은 원문이 아니라 hash를 사용합니다.

## OpenAPI 문서화 기준

OpenAPI는 구현 후 장식이 아니라 API 계약의 검증 가능한 산출물입니다.
이 section은 API를 설계하고 구현할 때 충족해야 할 기준을 소유합니다. 현재 구현 여부는 각 feature 결과와 [OpenAPI 현재 구현 경계](../tech-stack/260-openapi.md#현재-구현-경계)에서 확인하며, 기준 자체를 현재 완료 사실로 읽지 않습니다.

각 API는 다음을 문서화합니다.

- 리소스와 endpoint 목적
- HTTP method와 path
- request body schema
- response body schema
- 성공 status code
- 실패 status code와 오류 schema
- 인증과 권한 조건
- query parameter 의미, 기본값, 허용값
- pagination, sorting, filtering 규칙
- 예시 request/response

문서화 annotation은 Web Adapter DTO와 Controller에 둡니다.
Domain Model에 OpenAPI annotation을 추가하지 않습니다.

## 테스트 기준

API 설계는 테스트로 고정합니다.

우선 검증 대상은 다음과 같습니다.

- 올바른 요청의 성공 status code와 response body
- 생성 응답의 `Location` header
- request validation 실패
- 중복과 충돌 실패
- 인증 실패와 권한 부족
- 존재하지 않는 리소스
- 지원하지 않는 method
- pagination, sorting, filtering
- 오류 응답 공통 schema
- OpenAPI spec 생성 smoke test

Controller 계약은 Web MVC slice 테스트를 우선합니다.
Domain/Application 규칙은 Spring 없이 단위 테스트하고, Security wiring은 필요한 범위에서 별도 테스트합니다.

## 피해야 할 패턴

- URI에 동사를 넣습니다.
- `GET`으로 서버 상태를 변경합니다.
- 모든 실패를 `500` 또는 `400`으로만 반환합니다.
- HTTP status는 `200`으로 고정하고 body의 `success` 값으로만 성공/실패를 구분합니다.
- JPA Entity를 response body로 직접 반환합니다.
- 내부 DB id를 외부 식별자로 노출합니다.
- table 구조를 그대로 API URI에 반영합니다.
- 오류 응답 형식이 endpoint마다 다릅니다.
- 인증 실패와 권한 부족을 구분하지 않습니다.
- 임시로 모든 endpoint를 public으로 엽니다.
- pagination 없이 무제한 목록을 반환합니다.
- query parameter로 SQL/JPA 구현 세부사항을 노출합니다.
- HATEOAS를 사용하지 않으면서 링크 필드만 형식적으로 추가합니다.

## 설계 체크리스트

API 설계 전 확인합니다.

- 이 API의 주 리소스가 명사로 설명되는가
- public API 이름이 일반적으로 쓰이는 형태인지 확인했는가
- 내부 구현 정책이나 DB 구조가 path에 과하게 노출되지 않는가
- 외부 endpoint 이름과 내부 usecase/policy 이름을 분리해 검토했는가
- URI에 행위 동사가 들어가지 않았는가
- DB table이나 Entity 구조가 그대로 노출되지 않는가
- HTTP method가 안전성, 멱등성 의미와 맞는가
- 성공 status code와 response body 유무가 정해졌는가
- 실패 status code가 클라이언트 행동 기준으로 구분되는가
- 오류 응답이 공통 schema를 따르는가
- public id를 사용하는가
- request/response DTO가 분리되어 있는가
- 인증, 인가, CSRF, CORS 정책이 정해졌는가
- pagination, filtering, sorting의 기본값과 제한이 있는가
- cache, concurrency, idempotency 필요성을 검토했는가
- OpenAPI 문서와 테스트 기준이 함께 정해졌는가

## 관련 참고

- [Fielding Dissertation - Representational State Transfer](https://roy.gbiv.com/pubs/dissertation/rest_arch_style.htm)
- [RFC 9110 - HTTP Semantics](https://www.rfc-editor.org/rfc/rfc9110.html)
- [RFC 9457 - Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)
- [Google AIP-121 - Resource-oriented design](https://google.aip.dev/121)

## 관련 문서

- **구조·경계 기준**: [헥사고날 아키텍처](200-hexagonal-architecture.md), [Spring Boot 모듈 구조](210-spring-boot-module-structure.md), [경계와 매핑 규칙](220-boundary-and-mapping-rules.md)
- **구현 기술 기준**: [Spring Boot](../tech-stack/210-spring-boot.md), [Spring Security](../tech-stack/220-spring-security.md), [OpenAPI](../tech-stack/260-openapi.md)
- **현재 미구현 API 문서 계약**: [Account 인증/인가 후속 기능 Backlog](../work-items/backlog/2026/003-account-auth-follow-up.md#openapi-보호-api-계약)
- **개발 검증 절차**: [TDD 워크플로우](../workflow/210-tdd-workflow.md)
- **현재 오류 구현 결과**: [API 공통 기반](../work-items/features/2026/003-api-common-foundation.md)
