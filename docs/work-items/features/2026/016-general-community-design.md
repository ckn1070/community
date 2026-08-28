# 차량 비연결 일반 커뮤니티 상세 설계

## 설계 상태와 경계

이 문서는 [016 차량 비연결 일반 커뮤니티](016-general-community.md)의 기술 계약과 대안
비교를 소유합니다. 사용자 경험을 바꾸는 조회 공개 범위, 게시판 형태와 삭제 방식은 본문의
결정 카드에서 아직 선택되지 않았으므로 조건부 설계를 유지합니다. 선택되지 않은 추천 분기를
확정 요구로 사용하지 않습니다.

이번 기능은 Carmunity 제품 SOT가 이미 포함한 커뮤니티 영역에서 차량 연결을 제외한 첫
기능입니다. Carmunity와 AutoSquare의 관계, 카탈로그 reference 단위와 차량 커뮤니티 방향을
결정하지 않으며, 그 후보를 현재 요구사항으로 가져오지 않습니다.

## 현재 source에서 파생되는 제약

| Source | 현재 사실 | 이번 설계에 미치는 제약 |
| --- | --- | --- |
| 제품 개요·기능 범위 | 자동차 정보와 연결된 커뮤니티가 제품 영역이지만 상세 기능은 미구현입니다. | 일반 게시글·댓글만 새 work item에서 구체화하고 차량 연결은 만들지 않습니다. |
| 프로젝트 아키텍처 | 단일 배포 modular monolith와 module 내부 hexagonal 구조, 단순 조회 projection을 허용합니다. | `community`를 업무 module로 만들고 변경과 조회 구조의 강도를 다르게 적용합니다. |
| 모듈 구조 가이드 | 다른 module의 internal·table을 직접 사용하지 않고 공개 API로 협력합니다. | 작성자 표시 이름은 `account.api`를 통하고 cross-module join·FK를 만들지 않습니다. |
| Account 도메인 | profile의 `display_name`은 커뮤니티 공개 정보이고 account public id가 외부 식별자입니다. | JWT `sub`와 author reference는 account public UUID, 화면 표시값은 account owner 계약을 사용합니다. |
| 인증/인가 정책 | 일반 `/api/**`는 full session·password-change-not-required가 기본이고 public endpoint만 allow-list에서 엽니다. | 쓰기는 기본 보호 계약을 유지하고 조회만 사용자 선택에 따라 공개 예외가 됩니다. |
| REST·JPA·Flyway 기준 | public id, DTO 분리, bounded pagination, projection, forward migration과 DB constraint를 요구합니다. | UUID, count 없는 slice response, read projection, 새 versioned migration과 실제 query index를 사용합니다. |

현재 JWT는 display name을 담지 않습니다. token 탈취 노출과 이름 변경 뒤 stale claim을 피하기
위한 기존 보안 결정이므로 community 요구 때문에 claim을 늘리지 않습니다.

## 용어와 책임

| 용어 | 의미 | 생명주기 owner |
| --- | --- | --- |
| 일반 게시글 | 차량·category reference 없이 제목과 plain text 본문을 가진 사용자 콘텐츠 | `community` |
| 댓글 | 한 게시글에 직접 속하는 평면 사용자 콘텐츠. 다른 댓글을 parent로 갖지 않습니다. | `community` |
| 작성자 public id | JWT `sub`와 같은 account UUID. 소유권 비교와 account 공개 표현 조회에 사용합니다. | 식별자 발급·상태는 `account`, 콘텐츠 참조는 `community` |
| 작성자 표현 | API에 노출하는 account public id와 현재 display name의 작은 표현 | 공개 값은 `account.api`, 응답 조립은 `community` |
| 내부 PK | table FK, 정렬 tie-breaker와 persistence에만 쓰는 `BIGINT` 값 | 각 table owner |

`Post`와 `Comment`는 수정·삭제 권한과 상태를 갖는 domain model입니다. 하나의 게시글이 많은
댓글 collection을 메모리에 소유하지 않습니다. `Post`와 `Comment`를 각각 변경 단위로 두고,
댓글은 같은 module의 post 내부 PK만 persistence FK로 참조합니다.

## 권위별 결정 색인

| 결정 | 권위 | 상태 |
| --- | --- | --- |
| 차량과 무관한 게시글·댓글을 먼저 계획합니다. | 사용자 확정 요구 | 확정 |
| 차량 field·차종 게시판·오너 인증을 만들지 않습니다. | 사용자 확정 요구 | 확정 |
| account public id, display name 공개 의미와 기본 보호 API 조건 | 저장소 source | 확정 |
| `community` module, account batch API, API·schema·test 구조 | Agent 기술 결정 | 아래 대안 비교로 확정 |
| 조회 공개 범위, 게시판 형태, 삭제 방식 | 열린 사용자 결정 | 본문 결정 카드 대기 |

## Agent 기술 결정

### D1. 독립 `community` module

- **배경**: 게시글·댓글은 account/auth와 다른 업무 언어, 생명주기와 향후 moderation·media
  확장 이유를 가집니다.
- **검토한 선택지**: account module에 추가, 기술 계층 전역 package, 독립 community module
- **선택**: 루트 `community` module을 만들고
  `allowedDependencies = {"account :: api", "shared :: error"}`로 제한합니다.
- **선택 근거**: 현재 modular monolith를 유지하면서 콘텐츠 상태와 transaction을 account
  내부 구현에서 분리합니다.
- **영향**: REST Controller도 `community.internal.adapter.in.web`에 두며 `community.api`는
  다른 module consumer가 생기기 전에는 만들지 않습니다.
- **배제한 대안**: account 배치는 인증 주체와 콘텐츠 생명주기를 섞고, 전역 Controller·Service
  구조는 module verification으로 경계를 보호하기 어렵습니다.
- **재검토 조건**: moderation이나 media가 독립 생명주기와 다수 module consumer를 가져 별도
  bounded context가 필요해집니다.

### D2. 작성자 id는 저장하고 표시 이름은 account batch API로 조회

- **배경**: JWT에는 display name이 없고 community 목록은 여러 작성자 이름이 필요합니다.
- **검토한 선택지**: JWT claim 추가, community에 display name snapshot 저장, account table
  직접 join, account 공개 batch lookup
- **선택**: post/comment에는 `author_account_public_id UUID`만 저장합니다. `account.api`에
  `AccountPublicProfileLookup`과 `AccountPublicProfile(accountPublicId, displayName)`을 추가하고
  한 slice의 `Set<UUID>`를 한 query로 조회합니다.
- **선택 근거**: 이름 변경과 향후 account 공개 정책을 account owner 한 곳에서 반영하면서
  JWT stale data, cross-module table 의존과 N+1을 모두 피합니다.
- **영향**: account profile batch lookup이 post/comment query의 동기 dependency가 됩니다.
  현재 account는 탈퇴도 row 상태로 보존하므로 기존 author UUID가 사라지지 않습니다.
- **배제한 대안**: snapshot은 이름 변경 동기화가 필요하고, direct join·FK는 module 공개
  계약을 우회하며, JWT claim은 token TTL 동안 오래된 이름을 노출합니다.
- **재검토 조건**: account 물리 삭제·익명화, display name 변경 또는 비동기 read model이
  확정되면 공개 profile 계약의 누락·redaction·cache 정책을 함께 재설계합니다.

### D3. Post와 Comment를 분리한 변경 모델

- **배경**: 댓글 수가 커질 수 있어 Post aggregate가 전체 댓글 collection을 로드하면 변경과
  locking 비용이 커집니다.
- **검토한 선택지**: Post가 댓글 collection 소유, 규칙 없는 JPA CRUD, Post·Comment 분리
  domain model
- **선택**: `Post`와 `Comment`를 각각 domain 변경 모델로 두고 application use case가 parent
  상태와 transaction을 조정합니다. JPA Entity와 API DTO는 분리합니다.
- **선택 근거**: 작성자 소유권·삭제·수정 규칙을 Spring 없이 검증하면서 댓글 slice 조회와
  변경이 Post 전체 크기에 비례하지 않습니다.
- **영향**: comment 생성은 Post lookup/lock port와 Comment store port를 한 use case에서
  사용합니다. 목록은 별도 read projection입니다.
- **배제한 대안**: 댓글 collection aggregate는 무제한 성장하고, JPA CRUD만 사용하면 권한과
  삭제 규칙이 adapter에 흩어집니다.
- **재검토 조건**: 댓글 수 제한이나 게시글 단위 원자적 moderation처럼 aggregate 전체
  consistency가 필요한 규칙이 생깁니다.

### D4. Resource API와 count 없는 offset slice

- **배경**: 첫 일반 커뮤니티는 검색·추천 feed보다 기본 목록과 상세가 목표입니다.
- **검토한 선택지**: action URI, cursor pagination, 전체 건수 `Page`, resource URI와 count 없는
  offset `Slice`
- **선택**: `/api/v1/posts`와 하위 `/comments` resource를 사용하고 page/size 입력을 받는 offset
  `Slice`를 적용합니다. 외부 정렬 parameter는 열지 않고 server가 post 최신순·comment
  작성순을 고정합니다.
- **선택 근거**: 현재 규모와 UI가 확정되지 않은 단계에서 단순하고 검증 가능한 계약이며,
  내부 PK tie-breaker로 slice 내 정렬을 고정할 수 있습니다. 현재 요구에는 전체 글·댓글
  건수나 마지막 page 표시가 없으므로 `size + 1`행으로 `hasNext`만 판정해 별도 count query를
  피합니다.
- **영향**: post 기본 20·최대 100, comment 기본 50·최대 100입니다. 응답은 page index와
  `hasNext`를 제공하지만 `totalElements`·`totalPages`를 제공하지 않습니다.
- **배제한 대안**: action URI는 REST 기준과 어긋나고 cursor는 feed UX·부하 근거 없이 token
  계약과 방향별 query를 추가합니다. 전체 건수 `Page`는 제품 요구 없이 모든 목록에 count
  query를 계약화합니다.
- **재검토 조건**: 깊은 page의 latency·query plan 또는 무한 스크롤 요구가 offset 비용을
  정당화하지 못하면 `(createdAt, publicId)` cursor로 새 version 또는 호환 확장을 검토합니다.

### D5. Plain text와 저장 한계

- **배경**: media와 rich text 정책이 없지만 무제한 body는 request·DB·응답 자원 사용을
  통제하지 못합니다.
- **검토한 선택지**: 무제한 `TEXT`, Markdown/HTML, bounded plain text
- **선택**: 올바른 surrogate pair로 구성된 Unicode scalar 값만 허용하고 `U+0000`과 고립
  high·low surrogate를 `400`으로 거부합니다. 허용 문자열에 대해 title 1~120, post body
  1~10,000, comment body 1~2,000 **Unicode code point**의 plain text만 지원합니다. Java
  UTF-16 code unit 수나 화면 grapheme 수를 길이로 사용하지 않습니다.
- **정규화·blank 계약**: Unicode White_Space property의 고정 code point 집합
  `(U+0009~000D, U+0020, U+0085, U+00A0, U+1680, U+2000~200A, U+2028, U+2029,
  U+202F, U+205F, U+3000)`을 공통 상수로 사용합니다. CRLF와 lone CR은 LF로 바꾼 뒤 이
  집합을 양끝에서 제거합니다. 제목은 내부 연속 White_Space도 U+0020 하나로 접고 줄바꿈
  없는 한 줄로 저장하며, 본문·댓글은 내부 LF와 White_Space를 보존합니다. 정규화 뒤 code
  point가 0개면 blank입니다.
- **선택 근거**: 현재 도구 추가 없이 JSON 문자열로 안전하게 운반하고, Java
  `codePointCount`와 PostgreSQL `CHAR_LENGTH`가 emoji 같은 supplementary code point를 같은
  한 문자로 셉니다. 단, `codePointCount` 전에 UTF-16 sequence를 순회해 high surrogate는
  바로 뒤 low surrogate와 한 pair인지, low surrogate는 선행 high surrogate가 있는지와
  `U+0000` 부재를 검사합니다. 고정 whitespace 집합은 Java `isBlank()`와 DB locale별
  `[:space:]`의 차이를 피합니다.
- **영향**: Bean Validation `@Size`만으로 길이를 판정하지 않고 Web/domain이 같은 canonical
  normalizer를 사용합니다. JSON parser가 escaped 고립 surrogate를 Java `String`으로
  전달해도 domain 생성 전에 거부하며 persistence driver의 replacement character 변환에
  기대지 않습니다. PostgreSQL UTF-8 저장 repertoire와 `U+0000` 제한은 HTTP→domain→실제 DB
  통합 test로 확인하고, DB check는 고정 White_Space 집합, CR 부재, canonical trim과
  `CHAR_LENGTH` 한계를 mirror합니다. HTML을 server에서 렌더링·신뢰하지 않으며 frontend는
  text로 escape해야 합니다. request body나 콘텐츠 원문을 log·Problem Details에 넣지
  않습니다.
- **배제한 대안**: rich text는 sanitization·media·preview 계약을 요구하고 무제한 payload는
  abuse와 성능 경계를 만들지 못합니다. Java `String.length()`·`String.isBlank()`와 SQL
  `BTRIM()` 기본값 조합은 supplementary 문자와 Unicode whitespace에서 경계가 달라집니다.
- **재검토 조건**: 실제 작성 데이터의 길이 분포, Markdown 또는 media 요구가 확인됩니다.

### D6. Security filter와 domain 소유권의 이중 방어

- **배경**: URL 보호만으로는 어느 계정이 특정 row를 수정할 수 있는지 판단할 수 없습니다.
- **검토한 선택지**: Controller role 조건만 사용, DB query에 author 조건만 사용, 공통 인증과
  application/domain 소유권 검사
- **선택**: SecurityFilterChain은 session 접근을 막고, Web adapter가 JWT `sub`를 UUID actor로
  변환하며, domain/application이 저장된 author UUID와 다시 비교합니다.
- **선택 근거**: 인증 여부와 객체 소유권을 각각 책임 있는 경계에서 방어합니다.
- **영향**: 비작성자 수정·삭제는 `403`, parent mismatch와 비노출 row는 `404`입니다.
  `ROLE_ADMIN`·`ROLE_SUPER_ADMIN` 우회는 만들지 않습니다.
- **배제한 대안**: Controller-only 검사는 다른 inbound adapter가 생기면 우회되고, author
  조건 query만 사용하면 미존재와 권한 거부 정책을 명시적으로 test하기 어렵습니다.
- **재검토 조건**: 관리자 hide·복구, 공동 작성자 또는 세분화 permission 모델이 확정됩니다.

### D7. 낙관적 수정과 댓글 생성-게시글 삭제 직렬화

- **배경**: 같은 콘텐츠의 동시 수정과 댓글 생성 중 parent 삭제가 data loss·중간 상태를
  만들 수 있습니다.
- **검토한 선택지**: last-write-wins, 모든 변경 pessimistic lock, `@Version`과 필요한 parent
  row lock 조합
- **선택**: Post·Comment에 version column과 JPA optimistic lock을 사용하고 conflict를
  `409`로 변환합니다. 댓글 생성은 노출 가능한 Post row를 `FOR UPDATE`로 잠근 뒤 insert합니다.
- **선택 근거**: 일반 수정은 lock 대기 없이 충돌을 감지하고, parent 삭제와 comment 생성의
  정확한 race만 짧게 직렬화합니다.
- **영향**: long transaction과 외부 account lookup을 lock 구간에 넣지 않습니다. author
  표시 조립은 read transaction 밖 또는 read-only 경계에서 수행합니다.
- **배제한 대안**: last-write-wins는 동시 수정 손실을 숨기고 모든 row lock은 읽기·수정
  contention을 불필요하게 키웁니다.
- **재검토 조건**: stale client update가 실제 문제로 관찰되면 ETag·`If-Match`와 `412` 계약을
  추가합니다.

### D8. Additive migration과 exact rollback image 자격

- **배경**: 새 기능 rollback 때문에 이미 적용된 Flyway migration을 수정하거나 table을
  즉시 drop할 수 없습니다.
- **검토한 선택지**: down migration 자동화, 기존 migration 수정, raw 이전 image, 새 migration
  source를 포함한 exact compatibility image, current schema 위 forward fix
- **선택**: 새 versioned migration으로 table·constraint·index를 추가합니다. Staging rollout을
  별도 승인받으면 baseline application 동작과 새 migration source를 함께 가진 exact rollback
  image를 준비하고, 새 migration이 적용된 DB snapshot에서 그 image의 migration process가
  현재 Flyway history·checksum을 통과할 때만 rollback 후보로 인정합니다. 후보가 없으면
  current schema 위 forward fix로 복구합니다.
- **선택 근거**: Staging full sync는 같은 image로 PreSync migration Job을 먼저 실행합니다.
  Runtime이 새 table을 무시한다는 사실만으로, 새 migration을 모르는 raw 이전 image의
  validation 호환성이 보장되지 않습니다.
- **영향**: rollback/forward fix 뒤 community data는 남습니다. table 제거는 데이터 보존과
  재배포 여부를 판단한 별도 forward migration입니다. Rollback qualification test는 fresh
  DB에 새 migration을 적용한 뒤 exact 후보 image의 migration 전용 명령을 같은 history에서
  실행해 validation 성공과 migration 0건을 oracle로 삼습니다.
- **배제한 대안**: 적용된 migration 수정은 checksum 계약을 깨고 자동 drop은 사용자 콘텐츠를
  복구 불가능하게 지웁니다.
- **재검토 조건**: 기존 table 변경이나 대규모 backfill이 필요한 후속 기능이 생깁니다.

## 조건부 제품 결정의 기술 영향

### 조회 공개 범위

| 선택 | Security | OpenAPI·test | Data·운영 영향 |
| --- | --- | --- | --- |
| 공개 | 세 `GET` path를 중앙 public operation source에 추가 | GET은 security requirement·401·403이 없고 anonymous test가 성공 | 표시 이름·콘텐츠가 인터넷 공개이며 rollout 전 moderation·rate-limit 재검토 |
| 회원 전용 | 기본 `/api/**` 보호 규칙 유지 | GET에도 Bearer security와 401·403 계약 | 미인증 노출은 없지만 가입 전 탐색 불가 |

쓰기는 두 선택 모두 `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`를 요구합니다.

### 게시판 형태

| 선택 | API | Schema | 구현 영향 |
| --- | --- | --- | --- |
| 단일 | category request/filter 없음 | category column/table 없음 | 가장 작은 post slice |
| 고정 3개 | create/update에 `categoryCode`, list filter 추가 | `category_code`와 허용값 constraint | 기존 분류명 변경은 API·migration 변경 |
| 관리형 | board resource와 `boardId` 필요 | `community_board`와 post FK 필요 | 관리자 CRUD·정렬·비활성화가 별도 선행 기능 |

관리형을 선택하면 현재 작업 하나로는 목표가 둘이 되므로 board 관리 기반과 일반 콘텐츠를
별도 work item으로 분리한 뒤 의존 순서를 다시 검증합니다.

### 삭제 방식

| 선택 | 노출 동작 | Schema·transaction | 복구·privacy 영향 |
| --- | --- | --- | --- |
| 자리 유지 | 원문·외부 작성자 표현을 지우고 삭제 placeholder와 다른 댓글 유지 | 삭제 상태·시각, nullable content와 상태별 check. 내부 author UUID는 보존 | 원문은 복구 불가, 대화 구조 유지 |
| 전체 숨김 | 삭제 post·댓글 API 조회 불가 | soft delete 상태와 hidden filter | application 복구 가능하지만 원문을 계속 저장 |
| 물리 삭제 | row와 선택된 하위 row가 즉시 사라짐 | FK cascade/restrict와 delete transaction | 복구 불가, 다른 작성자 댓글 삭제 위험 |

## 목표 API 계약

조회 endpoint의 public/protected 여부와 category field는 사용자 선택에 따라 확정합니다.

| Method | Path | Request | Success | 권한 |
| --- | --- | --- | --- | --- |
| `POST` | `/api/v1/posts` | title, body, 선택 시 categoryCode | `201`, body, `Location` | 인증 쓰기 |
| `GET` | `/api/v1/posts?page=0&size=20` | query | `200` post slice | 조회 선택 |
| `GET` | `/api/v1/posts/{postId}` | - | `200` detail | 조회 선택 |
| `PATCH` | `/api/v1/posts/{postId}` | title 또는 body, 선택 시 categoryCode | `200` | 작성자 |
| `DELETE` | `/api/v1/posts/{postId}` | - | `204` | 작성자 |
| `POST` | `/api/v1/posts/{postId}/comments` | body | `201`, body, `Location` | 인증 쓰기 |
| `GET` | `/api/v1/posts/{postId}/comments?page=0&size=50` | query | `200` comment slice | 조회 선택 |
| `PATCH` | `/api/v1/posts/{postId}/comments/{commentId}` | body | `200` | 작성자 |
| `DELETE` | `/api/v1/posts/{postId}/comments/{commentId}` | - | `204` | 작성자 |

### 응답 표현

`PostSummaryResponse`:

- `postId`: UUID
- `title`: string
- `author`: `{ accountId, displayName }`
- 선택 시 `categoryCode`
- `createdAt`, `updatedAt`: UTC 의미의 ISO-8601 timestamp

`PostDetailResponse`는 summary field와 `body`를 포함하고 comment 목록은 중첩하지 않습니다.
댓글은 별도 slice endpoint가 소유합니다.

`CommentResponse`:

- `commentId`, `postId`: UUID
- `body`: string
- `author`: `{ accountId, displayName }`
- `createdAt`, `updatedAt`

삭제 자리 유지 선택에서는 삭제 표현이 원문·account id·display name을 노출하지 않고
`deleted=true`와 삭제 placeholder만 제공합니다. exact nullable schema는 선택 반영 때
동결합니다.

Post와 Comment slice는 다음 field를 갖는 module-local response를 각각 사용합니다.

- `items`
- `page`, `size`
- `hasNext`

조회 query는 `size + 1`행을 읽고 초과 한 행을 응답에서 제거해 `hasNext`를 계산합니다.
아직 다른 module의 안정된 consumer가 없으므로 범용 `shared` slice DTO를 먼저 만들지 않습니다.

### Validation과 오류

| 상황 | Status | Code |
| --- | --- | --- |
| field 누락·Unicode blank·code point 길이·page 범위·빈 PATCH | `400` | 공통 `INVALID_REQUEST`와 field detail |
| 인증 누락·invalid token | `401` | 기존 공통 Security code |
| session·password 상태 불충족 또는 비작성자 | `403` | 기존 접근 오류 또는 `COMMUNITY_CONTENT_FORBIDDEN` |
| post 미존재·비노출 | `404` | `COMMUNITY_POST_NOT_FOUND` |
| comment 미존재·parent 불일치·비노출 | `404` | `COMMUNITY_COMMENT_NOT_FOUND` |
| optimistic lock conflict | `409` | `COMMUNITY_CONTENT_CONFLICT` |
| unsupported content type | `415` | 기존 공통 media type 오류 |

삭제 반복 호출은 선택한 삭제 모델을 고정할 때 exact contract를 정합니다. 자리 유지 추천안은
같은 작성자의 반복 DELETE를 `204`, 다른 actor와 외부 조회에는 `404`로 처리할 수 있도록
내부 author UUID를 보존합니다.

## 목표 persistence 계약

사용자 선택 전에도 공통으로 필요한 column은 다음과 같습니다.

### `community_post`

| Column | Type | 의미 |
| --- | --- | --- |
| `id` | `BIGINT IDENTITY` | 내부 PK |
| `public_id` | `UUID` | API 공개 식별자, unique |
| `author_account_public_id` | `UUID` | JWT `sub`와 비교할 작성자 reference |
| `title` | `VARCHAR(120)` | 게시글 제목. 삭제 선택에 따라 nullable 여부 확정 |
| `body` | `TEXT` | 최대 10,000자 plain text. 삭제 선택에 따라 nullable 여부 확정 |
| `version` | `BIGINT` | optimistic lock |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | 감사 시각 |
| 조건부 column | - | category 또는 삭제 상태·시각은 사용자 선택 뒤 추가 |

### `community_comment`

| Column | Type | 의미 |
| --- | --- | --- |
| `id` | `BIGINT IDENTITY` | 내부 PK |
| `public_id` | `UUID` | API 공개 식별자, unique |
| `post_id` | `BIGINT` | 같은 module `community_post.id` FK |
| `author_account_public_id` | `UUID` | JWT `sub`와 비교할 작성자 reference |
| `body` | `TEXT` | 최대 2,000자 plain text. 삭제 선택에 따라 nullable 여부 확정 |
| `version` | `BIGINT` | optimistic lock |
| `created_at`, `updated_at` | `TIMESTAMPTZ` | 감사 시각 |
| 조건부 column | - | 삭제 상태·시각은 사용자 선택 뒤 추가 |

공통 constraint와 index:

- `public_id` unique, 시간 역전 방지와 canonical Unicode White_Space trim·CR 부재·
  `CHAR_LENGTH` code point 길이 check
- comment `post_id` FK와 `(post_id, created_at ASC, id ASC)` 조회 index
- post `(created_at DESC, id DESC)` 목록 index
- 선택한 soft-delete model은 같은 query의 active predicate를 반영한 partial index를 검토
- `author_account_public_id`에는 account cross-module FK를 만들지 않음

목록 응답에는 최초 범위에 없는 댓글 수 집계를 넣지 않습니다. 댓글 수가 실제 화면 요구로
확정되면 상세 계산·목록 grouped query·비정규화 counter의 비용과 삭제 댓글 포함 의미를 별도
결정으로 비교합니다.

## Transaction과 동시성

| Use case | Transaction 경계 | 동시성·실패 처리 |
| --- | --- | --- |
| Post 생성 | public id 생성, domain validation, post insert | unique·check 실패 rollback |
| Post 수정 | post load, author 검사, domain update, save | version conflict 409 |
| Post 삭제 | post write lock, author 검사, 선택한 삭제 효과 | comment 생성과 순서 직렬화 |
| Comment 생성 | post `FOR UPDATE` 상태 검사, comment insert | 삭제 완료 post에는 insert 불가 |
| Comment 수정·삭제 | comment load, parent·author 검사, 상태 변경 | version conflict 409 |
| 목록·상세 | read-only query와 transaction 밖/bounded account batch 조립 | account lookup N+1 금지 |

account batch lookup은 community 변경 transaction 안에서 DB row lock을 유지한 채 호출하지
않습니다. account 공개 profile이 구조적으로 누락된 경우 조용히 임의 이름을 만들지 않고
application 오류와 운영 log로 드러내며, 향후 account 익명화 정책이 생기면 account owner가
공개 표현을 반환하도록 계약을 확장합니다.

## 목표 package와 경계

```text
community
└─ internal
   ├─ domain
   ├─ application
   │  ├─ command / query / result
   │  ├─ port.in / port.out
   │  └─ service
   └─ adapter
      ├─ in.web
      └─ out.persistence

community -> account :: api
community -> shared :: error
community -X-> account.internal / account table
```

- 변경: UseCase → Domain → store/lookup Port → persistence adapter
- 조회: Controller → application query service → persistence projection → account batch API → DTO
- Web Request는 `toCommand()`/`toQuery()`, Response는 `from(...)` 경계 factory를 사용합니다.
- JPA Entity와 projection은 persistence adapter 밖으로 노출하지 않습니다.

## Test 전략과 품질 gate

### Domain·Application 단위 test

- `U+0000`, 고립 high·low surrogate 거부와 정상 emoji surrogate pair 허용
- title/body/comment의 CRLF·CR 정규화, ASCII/Unicode White_Space blank와 양끝 trim
- BMP 문자, 정상 supplementary emoji와 결합 문자의 Unicode code point 최대 길이 경계
- author 일치 수정·삭제 성공과 비작성자 거부
- 선택한 삭제 상태 전이와 반복 삭제
- parent post 상태별 comment 생성 허용·거부
- account batch 결과를 post/comment slice에 누락 없이 조립

### Web·Security test

- 모든 endpoint 성공 status, body, `Location`
- request validation, 빈 PATCH, malformed UUID, parent mismatch
- escaped `U+0000`·고립 high/low surrogate의 `400`과 정상 emoji pair 성공
- 선택한 조회 정책의 anonymous·full session·limited/password-change-required matrix
- 비작성자 403, 미존재·비노출 404와 공통 Problem Details
- OpenAPI 전체 path·response·schema·public/protected 분류

### PostgreSQL 통합 test

- 최신 Flyway migration 전체 적용과 JPA mapping
- UUID unique, content check, comment FK와 삭제 선택별 constraint
- post 최신순·comment 작성순 slice, `size + 1` hasNext와 tie-breaker, 별도 total count 부재
- slice당 account batch lookup 1회, query N+1 부재
- Java canonical normalizer와 PostgreSQL constraint가 CRLF·Unicode whitespace·emoji 경계에서
  같은 값을 허용·거부하는 대조 test
- HTTP JSON→domain→PostgreSQL 경로가 `U+0000`·고립 surrogate를 저장·치환하지 않고 거부하며
  정상 surrogate pair를 원형대로 저장하는 대조 test
- optimistic update conflict
- comment create와 post delete 경합의 terminal state

### 전체 gate

- Spring Modulith `ApplicationModules.verify()`
- ArchUnit module/internal/domain/application/persistence 경계
- Docker daemon preflight와 PostgreSQL Testcontainers
- `./gradlew check`
- push가 별도 승인된 구현에서는 final SHA required CI와 Sonar revision·Quality Gate·Open issues

## 미래 차량 카탈로그 연결과 재작업 경계

이 작업은 차량을 모르는 게시글 identity와 lifecycle만 만듭니다. 이후 차량 카탈로그가
확정되면 다음처럼 별도 작업으로 연결합니다.

1. 카탈로그가 공개하는 안정된 model·trim reference 단위를 먼저 확정합니다.
2. `community_post`에 임시 차명 문자열을 해석해 덮어쓰지 않습니다.
3. 하나의 게시글이 차량 하나만 참조한다는 제품 규칙이 확정되면 nullable public reference,
   여러 차량을 허용하면 `community_post_vehicle_reference` association을 새 migration으로
   추가합니다.
4. create/update request에 선택 field, detail response에 작은 차량 reference, 목록에 filter를
   non-breaking 확장으로 추가합니다.
5. 차량이 없는 기존 일반 글은 그대로 유효하며 backfill을 강제하지 않습니다.

따라서 일반 커뮤니티 선행으로 생기는 필수 재작업은 기존 게시글·댓글의 재설계가 아니라
차량 reference 저장, 검증, 응답·filter 추가입니다. 임시 차량 문자열, 임시 차종 게시판과
가짜 오너 배지를 만들지 않는 것이 이 경계를 지키는 핵심입니다.

## 운영·보안 고려

- API와 application log에 제목·본문·댓글 원문을 기록하지 않습니다.
- account 내부 id, email, profile 내부 row와 JWT 원문을 응답·오류에 노출하지 않습니다.
- 공개 조회를 선택해도 쓰기는 정상 full session만 허용합니다.
- rate limiting, 신고·관리자 hide와 sanctions가 없으므로 이 core API의 기술 완료를 production
  공개 운영 준비 완료로 보고하지 않습니다.
- 새 table과 endpoint는 기존 account/auth operation을 변경하지 않는 additive rollout입니다.
  다만 raw 이전 image의 Flyway compatibility를 뜻하지 않으며 exact rollback image 검증 또는
  forward recovery가 필요합니다.
- community 조회 latency, 4xx·5xx와 conflict 비율은 기존 API 완료 event/logging으로
  관찰하되 콘텐츠 원문을 metadata에 추가하지 않습니다.

## 관련 문서

- **사용자 결정과 구현 계획**: [016 차량 비연결 일반 커뮤니티](016-general-community.md)
- **제품 현재 범위**: [100 제품 개요](../../../product/100-product-overview.md),
  [200 기능 범위](../../../product/200-feature-scope.md)
- **현재 구조**: [200 프로젝트 아키텍처](../../../project/200-current-architecture.md),
  [210 모듈 지도](../../../project/210-module-map.md)
- **작성자 identity·표시 이름**: [100 Account 도메인](../../../domain/100-account.md),
  [100 Account/Auth Schema Reference](../../../project/schema/100-account-auth.md)
- **인증·권한 기준**: [110 Account 인증/인가 정책](../../../domain/110-account-auth-policy.md),
  [220 Spring Security](../../../tech-stack/220-spring-security.md)
- **구조·data·API 기준**: [200 헥사고날 아키텍처](../../../architecture/200-hexagonal-architecture.md),
  [210 Spring Boot 모듈 구조](../../../architecture/210-spring-boot-module-structure.md),
  [220 경계와 매핑](../../../architecture/220-boundary-and-mapping-rules.md),
  [230 RESTful API](../../../architecture/230-rest-api-design.md),
  [230 Spring Data JPA](../../../tech-stack/230-spring-data-jpa.md),
  [240 Flyway](../../../tech-stack/240-flyway.md),
  [250 PostgreSQL](../../../tech-stack/250-postgresql.md)
