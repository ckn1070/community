# 확장 가능한 차량 비연결 일반 커뮤니티 상세 설계

## 설계 상태와 경계

이 문서는 [016 확장 가능한 차량 비연결 일반 커뮤니티 기반](016-general-community.md)의 기술 계약과 대안
비교를 소유합니다. 사용자는 Board·Post·Comment 공개 조회, 자유게시판 하나, 삭제 원문 DB
보존과 Post 완전 비노출·Comment placeholder를 확정했습니다. 본문은 이 한 계약만을 구현
대상으로 사용하며 선택되지 않은 과거 대안을 조건부 설계로 남기지 않습니다.

이번 기능은 Carmunity 제품 SOT가 이미 포함한 커뮤니티 영역에서 차량 연결을 제외한 첫
기능입니다. 현재에는 Board·Post·Comment만 구현하지만, 제품 범위에 이미 존재하는 반응·
북마크·신고·media와 일반적으로 뒤따를 검색·알림·대댓글이 기존 core를 교체하지 않고 붙을
수 있는 책임 경계를 설계합니다. Carmunity와 AutoSquare의 관계, 카탈로그 reference 단위와
차량 커뮤니티 방향은 결정하지 않으며, 그 후보를 현재 요구사항으로 가져오지 않습니다.

## 현재 source에서 파생되는 제약

| Source | 현재 사실 | 이번 설계에 미치는 제약 |
| --- | --- | --- |
| 제품 개요·기능 범위 | 자동차 정보와 연결된 커뮤니티, 댓글·반응·북마크·신고·media가 제품 영역이지만 상세 기능은 미구현입니다. | Board·Post·Comment core만 구현하고 후속 기능은 additive extension 경계로 분리합니다. |
| 프로젝트 아키텍처 | 단일 배포 modular monolith와 module 내부 hexagonal 구조, 단순 조회 projection을 허용합니다. | `community`를 업무 module로 만들고 변경과 조회 구조의 강도를 다르게 적용합니다. |
| 모듈 구조 가이드 | 다른 module의 internal·table을 직접 사용하지 않고 공개 API로 협력합니다. | 작성자 표시 이름은 `account.api`를 통하고 cross-module join·FK를 만들지 않습니다. |
| Account 도메인 | profile의 `display_name`은 커뮤니티 공개 정보이고 account public id가 외부 식별자입니다. | JWT `sub`와 author reference는 account public UUID, 화면 표시값은 account owner 계약을 사용합니다. |
| 인증/인가 정책 | 일반 `/api/**`는 full session·password-change-not-required가 기본이고 public endpoint만 allow-list에서 엽니다. | 쓰기는 기본 보호 계약을 유지하고 Board·Post·Comment GET을 공개 예외로 명시합니다. |
| REST·JPA·Flyway 기준 | public id, DTO 분리, bounded pagination, projection, forward migration과 DB constraint를 요구합니다. | UUID, count 없는 slice response, read projection, 새 versioned migration과 실제 query index를 사용합니다. |

현재 JWT는 display name을 담지 않습니다. token 탈취 노출과 이름 변경 뒤 stale claim을 피하기
위한 기존 보안 결정이므로 community 요구 때문에 claim을 늘리지 않습니다.

## 용어와 책임

| 용어 | 의미 | 생명주기 owner |
| --- | --- | --- |
| Board | 게시글이 모이는 안정된 컨테이너입니다. 초기에는 seed된 게시판만 조회하며 관리 생명주기는 구현하지 않습니다. | `community` |
| 일반 게시글 | Board 하나에 속하고 차량 reference 없이 제목과 plain text 본문을 가진 사용자 콘텐츠 | `community` |
| 댓글 | 한 게시글에 직접 속하는 평면 사용자 콘텐츠. 다른 댓글을 parent로 갖지 않습니다. | `community` |
| 작성자 public id | JWT `sub`와 같은 account UUID. 소유권 비교와 account 공개 표현 조회에 사용합니다. | 식별자 발급·상태는 `account`, 콘텐츠 참조는 `community` |
| 작성자 표현 | API에 노출하는 account public id와 현재 display name의 작은 표현 | 공개 값은 `account.api`, 응답 조립은 `community` |
| 내부 PK | table FK, 정렬 tie-breaker와 persistence에만 쓰는 `BIGINT` 값 | 각 table owner |

`Board`는 첫 slice에서 읽기 전용 reference model이고, `Post`와 `Comment`는 수정·삭제 권한과
상태를 갖는 domain model입니다. 하나의 게시글이 많은
댓글 collection을 메모리에 소유하지 않습니다. `Post`와 `Comment`를 각각 변경 단위로 두고,
Post는 같은 module의 Board 내부 PK, 댓글은 Post 내부 PK를 persistence FK로 참조합니다.

## 권위별 결정 색인

| 결정 | 권위 | 상태 |
| --- | --- | --- |
| 차량과 무관한 게시글·댓글을 먼저 계획합니다. | 사용자 확정 요구 | 확정 |
| 차량 field·차종 게시판·오너 인증과 미래 기능 구현을 현재 core에 넣지 않습니다. | 사용자 확정 요구 | 확정 |
| 미래 기능이 붙어도 core identity·경계를 교체하지 않는 구조를 사용합니다. | 사용자 확정 요구 | 확정 |
| account public id, display name 공개 의미와 기본 보호 API 조건 | 저장소 source | 확정 |
| `community` module, Board, account batch API, API·schema·test와 확장 경계 | Agent 기술 결정 | 아래 대안 비교로 확정 |
| Board·Post·Comment 공개 조회, 자유게시판 하나, Post 비노출·Comment 삭제 표시 | 사용자 확정 요구 | 확정 |

## 미래 기능 지도와 현재 경계

미래 기능 이름을 현재 schema field로 미리 예약하지 않습니다. 대신 아래에서 바뀌지 않아야 할
core와 첫 additive 연결 지점을 정합니다.

| 미래 기능군 | 예상 기능 | 처음 붙을 경계 | 현재 구현 |
| --- | --- | --- | --- |
| 탐색 단위 | 게시판 추가·정렬·비활성화, tag, 차량별 라운지 | Board lifecycle·Board association, Post-Tag association | Board identity와 Post FK만 구현 |
| 대화·관계 | 대댓글, 반응, 북마크, 구독 | Comment parent relation, target별 aggregate와 unique actor-target constraint | 평면 Comment와 안정된 public id만 구현 |
| 발견 | 통합 최신 feed, 전문 검색, 인기·추천 feed, 개인화 | 별도 query projection, 필요 시 변경 use case 뒤 event와 read model | 변경 모델과 Board-scoped projection만 분리 |
| 전달 | 알림, WebSocket/SSE 실시간 갱신 | transaction 완료 뒤 durable event consumer | consumer가 없으므로 event/outbox 미구현 |
| 콘텐츠 형식 | 이미지·첨부, Markdown·rich text, 투표형 글 | media 공개 API, Post-Attachment와 생명주기별 1:1 확장/별도 aggregate | bounded plain text만 구현 |
| 신뢰·안전 | 신고, 편집 이력, 운영자 숨김·잠금, 제재, spam·rate limit | 작성자 action과 분리된 revision·moderation action·restriction·audit | production 공개 선행 조건으로만 유지 |
| identity·권한 | 공개 profile 변경, 익명화, badge, board moderator | account 공개 표현 계약과 CommunityActionPolicy | 현재 display name batch 조회와 author action만 구현 |
| 규모·운영 | cache, counter, archive, partition, 장애 격리 | replaceable read projection, 측정된 query·lock·storage 지표 | bounded cursor와 현재 logging만 구현 |

확장 불변식은 다음과 같습니다.

- Post는 처음부터 Board에 속하며 초기 게시판 수가 늘어도 기존 row를 재분류하지 않습니다.
- Post와 Comment의 public id와 작성자 reference는 후속 association의 안정된 연결점입니다.
- 작성자 수정·삭제, 조회 visibility와 운영자 조치는 서로 다른 action과 정책으로 유지합니다.
- 변경 model은 검색 문서, 인기 점수, 댓글 수 counter와 notification delivery 상태를 소유하지
  않습니다.
- 다른 module은 community table·internal package를 읽지 않고, 실제 consumer가 생길 때 만든
  최소 `community.api` 또는 event 계약을 사용합니다.
- 첫 durable async consumer 전에는 event/outbox를 만들지 않지만, 모든 변경은 application
  use case를 통과해 후속 event publication 지점이 흩어지지 않게 합니다.
- 후속 기능이 요구하는 schema는 nullable 만능 field나 JSONB bag이 아니라 owner가 분명한
  association·aggregate·read model로 추가합니다.

## 구현 진입 gate와 의존 순서

이 문서는 목표 구조를 설명하지만 문서에 package 그림이 있다는 사실은 package를 먼저 만들
권한이나 순서를 뜻하지 않습니다. `$verify-plan` 독립 감사에 필수 결함이 없고 work item이
`Planned` checkpoint로 commit되기 전에는 source code, test code, 설정, Flyway migration,
module·package 껍데기, public API type, adapter와 exploratory spike를 만들지 않습니다.
Read-only source 조사와 계획·상세 설계 Markdown만 gate 전에 허용합니다.

Gate는 다음 계약이 모두 exact일 때만 열립니다.

- 제품 결정, API·오류·Security·cache, domain 상태·action·visibility·commentability
- table·column·type·nullability·default·FK·check·index·seed·migration·recovery
- transaction·lock·idempotency, module·package·의존 방향과 account 협력
- validation·privacy·logging·limit, test·quality·SOT·rollout과 미래 additive 경계
- fresh `$verify-plan` 통과와 `Planned` checkpoint commit

Gate 뒤 첫 구현은 빈 module 골격이 아니라 `community` core와 exact schema를 함께 만드는
단위입니다. 작성자 batch 계약은 schema·domain 결과를 소비하지 않아 작업 2와 기술적으로
독립이지만, unused producer의 체류 시간을 줄이기 위해 core·schema 뒤, 첫 조회 API 직전에
배치하는 순서를 권장합니다. 두 결과의 첫 hard 합류점은 Board/Post 조회입니다.

```text
상세 설계 동결·독립 감사
  → Planned checkpoint + 새 구현 요청
  → work item·router In Progress projection
  ├─ community domain core + Flyway schema ─┐
  └─ account 공개 profile batch 계약 ──────┴→ Board/Post API
  → Comment API
  → 개발 통합·`verify-dev`
  → 문서 SOT·`verify-documentation`
  → Git·remote gate
  → work item·router 최종 상태 projection
```

현재 실행 권장 순서는 `core·schema → account batch → Board/Post API`이지만 앞의 두 결과 사이에는
hard dependency가 없습니다. Board/Post query service가 만드는 `AuthorProfileLookupPort` adapter가
account batch named interface를 처음 소비하므로, 작업 4는 두 결과 없이는 시작할 수 없습니다.

구현 중 결과를 바꾸는 미결정이 발견되면 새 mutation을 중단하고 영향을 받는 설계를
`Proposed`로 되돌려 수정·재검증합니다. 새 `Planned` checkpoint 전에는 구현을 재개하지
않습니다.

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
  다른 module consumer가 생기기 전에는 만들지 않습니다. 이 module은 목표 경계이며 설계
  gate 전에 만드는 선행 골격이 아닙니다. 첫 구현에서 Board·Post·Comment core와 Flyway
  schema를 담는 실제 code·test와 함께 생성합니다.
- **배제한 대안**: account 배치는 인증 주체와 콘텐츠 생명주기를 섞고, 전역 Controller·Service
  구조는 module verification으로 경계를 보호하기 어렵습니다.
- **재검토 조건**: moderation이나 media가 독립 생명주기와 다수 module consumer를 가져 별도
  bounded context가 필요해집니다.

### D2. 초기부터 존재하는 경량 Board

- **배경**: 게시판 없이 Post만 만들면 게시판·차량별 라운지·접근 범위를 처음 추가할 때 모든
  기존 글에 분류를 backfill하고 create/list API의 기준 resource를 바꿔야 합니다. 반대로
  관리 기능 전체를 미리 만들면 현재 core보다 Board lifecycle이 더 커집니다.
- **검토한 선택지**: Board 없는 단일 feed, Post의 고정 category code, 처음부터 완전한 관리형
  Board, identity와 Post FK만 가진 경량 Board
- **선택**: `community_board`에 내부 PK, UUID `public_id`, 변경되지 않는 `code`, 사용자 표시
  `name`, `display_order`와 감사 시각을 둡니다. 모든 Post는 `board_id NOT NULL` FK로 Board
  하나를 참조합니다. Board collection은 `(displayOrder ASC, publicId ASC)`로 정렬합니다.
  현재에는 `54be05ef-b451-4865-b124-b7633d04511e`/`free`/`자유게시판`/`100` 한 row와
  목록·상세 조회만 구현하고 create/update/reorder/archive 관리 API는 만들지 않습니다.
  초기 row의 public id와 code는 migration에 literal 값으로
  명시해 개발·검증·운영 환경에서 같은 외부 identity를 재현하며 DB UUID 생성 함수에
  의존하지 않습니다.
- **선택 근거**: 초기 한 게시판의 구현 비용은 작은 reference row와 FK뿐이지만, 이후 게시판을
  추가할 때 Post identity·schema·API를 교체하지 않습니다. Code는 운영 seed와 stable business
  key, public id는 외부 reference, name은 변경 가능한 표시값으로 책임을 분리합니다.
- **영향**: Post 생성·목록은 `/api/v1/boards/{boardId}/posts`로 Board에 scope됩니다. 초기
  order는 100 단위로 두어 새 Board를 사이에 넣을 수 있게 하고, 중복 order는 public id로
  결정적으로 정렬합니다. Post의 Board 이동은 현재 지원하지 않으며, 추후 필요할 때 별도
  action·권한·감사 정책으로 추가합니다.
- **배제한 대안**: Board 없는 feed는 미래 backfill을 만들고 category code는 관리 생명주기와
  차량별 association을 Post enum에 결합합니다. 완전한 관리형 Board는 현재 쓰지 않는 상태·
  정렬·권한 API를 먼저 구현합니다.
- **재검토 조건**: Board 생성·재정렬·비활성화, board별 접근 정책이나 차량별 Board가 실제
  범위가 되면 `status`, optimistic version, 접근 정책 또는 association을 새 migration으로
  추가하고 기존 `display_order`를 변경하는 Board aggregate로 승격합니다.

### D3. 작성자 id는 저장하고 표시 이름은 account batch API로 조회

- **배경**: JWT에는 display name이 없고 community 목록은 여러 작성자 이름이 필요합니다.
- **검토한 선택지**: JWT claim 추가, community에 display name snapshot 저장, account table
  직접 join, account 공개 batch lookup
- **선택**: post/comment에는 `author_account_public_id UUID`만 저장합니다. `account.api`에
  `AccountPublicProfileLookup`과 `AccountPublicProfile(accountPublicId, displayName)`을 추가하고
  community의 `AuthorProfileLookupPort` adapter가 한 slice의 `Set<UUID>`를 한 query로
  조회합니다. 입력은 중복 제거된 non-null public id 집합이고 빈 집합은 query 없이 빈 Map을
  반환합니다. 결과는 account public id를 key로 한 Map이며 요청 id 중 누락이 있으면
  `COMMUNITY_AUTHOR_PROFILE_UNAVAILABLE`로 전체 응답을 실패시킵니다. 삭제 Comment는 author를
  응답하지 않으므로 batch 입력에서 제외합니다.
- **선택 근거**: 이름 변경과 향후 account 공개 정책을 account owner 한 곳에서 반영하면서
  JWT stale data, cross-module table 의존과 N+1을 모두 피합니다.
- **영향**: account profile batch lookup이 post/comment query의 동기 dependency가 됩니다.
  현재 account는 탈퇴도 row 상태로 보존하므로 기존 author UUID가 사라지지 않습니다.
  Community query service는 account API DTO에 직접 퍼지지 않고 port 결과만 소비하므로 이후
  bounded cache나 author read model을 adapter 교체로 비교할 수 있습니다. 이 계약은 schema나
  domain core가 소비하지 않습니다. Account producer 계약은 독립 구현할 수 있지만 권장 배치상
  Community core·schema 뒤 첫 Board/Post 조회 projection 직전에 만들고, community port·adapter는
  최초 consumer인 Board/Post query 구현에서 추가합니다.
- **배제한 대안**: snapshot은 이름 변경 동기화가 필요하고, direct join·FK는 module 공개
  계약을 우회하며, JWT claim은 token TTL 동안 오래된 이름을 노출합니다.
- **재검토 조건**: account 물리 삭제·익명화, display name 변경 또는 비동기 read model이
  확정되면 공개 profile 계약의 누락·redaction·cache 정책을 함께 재설계합니다.

### D4. Post와 Comment를 분리한 변경 모델

- **배경**: 댓글 수가 커질 수 있어 Post aggregate가 전체 댓글 collection을 로드하면 변경과
  locking 비용이 커집니다.
- **검토한 선택지**: Post가 댓글 collection 소유, Post·Comment 공통 `community_content`
  상속 table, 규칙 없는 JPA CRUD, Post·Comment 분리 domain model
- **선택**: `Post`와 `Comment`를 각각 domain 변경 모델로 두고 application use case가 parent
  상태와 transaction을 조정합니다. JPA Entity와 API DTO는 분리합니다.
- **선택 근거**: 작성자 소유권·삭제·수정 규칙을 Spring 없이 검증하면서 댓글 slice 조회와
  변경이 Post 전체 크기에 비례하지 않습니다.
- **영향**: Comment 생성은 Post lookup/lock port와 Comment store port를 한 use case에서
  사용합니다. 목록은 별도 read projection입니다. 현재 소비자가 없는 generic Content
  interface·table은 만들지 않습니다. 반응처럼 community 내부에서 target FK가 필요한 기능은
  Post·Comment별 association을 우선하고, moderation처럼 외부 module consumer가 생기면 그때
  public id 기반 최소 `ContentReference`를 `community.api`에 추가합니다.
- **배제한 대안**: 댓글 collection aggregate는 무제한 성장하고, JPA CRUD만 사용하면 권한과
  삭제 규칙이 adapter에 흩어집니다. 공통 content table은 두 row write와 inheritance mapping을
  모든 변경에 부과하며 아직 공통 persistence lifecycle consumer가 없습니다. FK 없는
  `(targetType, targetId)` polymorphic table은 DB 참조 무결성을 포기합니다.
- **재검토 조건**: Post·Comment 외 콘텐츠 유형과 둘 이상 cross-cutting consumer가 같은
  identity·lifecycle을 실제로 공유해 target별 association 비용이 공통 content registry 비용을
  넘습니다.

### D5. Board-scoped Resource API와 cursor slice

- **배경**: 게시판 feed는 새 글이 계속 추가되고 이후 search·popular feed가 별도 query로
  생길 가능성이 높습니다. Offset response를 public contract로 고정하면 깊은 page 비용과
  cursor 전환 시 response breaking change가 남습니다.
- **검토한 선택지**: action URI, 전체 건수 `Page`, count 없는 offset `Slice`, Board-scoped
  resource URI와 keyset cursor `Slice`
- **선택**: Board는 `/api/v1/boards`, 게시글 collection은
  `/api/v1/boards/{boardId}/posts`, 단일 Post와 하위 Comment는 `/api/v1/posts/{postId}`를
  기준으로 합니다. 목록은 `limit`과 optional opaque `cursor`를 받고, Post는
  `(createdAt DESC, publicId DESC)`, Comment는 `(createdAt ASC, publicId ASC)` 위치를 cursor에
  표현합니다. 내부 PK와 임의 sort parameter를 public contract에 넣지 않습니다. Cursor v1은
  UTF-8 JSON의 `v`, collection `kind`, board/post `scopeId`, `createdAt`, `publicId`를
  Base64URL without padding으로 인코딩합니다. API 소비자는 이를 해석하지 않는 opaque
  문자열로 취급합니다.
- **선택 근거**: public UUID를 tie-breaker로 사용하면 정렬이 안정되고 내부 PK를 노출하지
  않으면서 첫 구현부터 깊은 offset scan을 피합니다. `limit + 1`행으로 `hasNext`와
  `nextCursor`만 계산하므로 total count 요구도 만들지 않습니다.
- **영향**: Post 기본 20·최대 100, Comment 기본 50·최대 100입니다. 첫 요청은 cursor를
  생략하며 마지막 slice의 `nextCursor`는 null입니다. Cursor의 미지원 version, malformed
  encoding·JSON·UUID·timestamp, collection kind·path scope 불일치와 필수 값 누락은 `400`으로
  거부합니다. Token은 접근 권한이 아니고 public 값의 조회 위치일 뿐이므로 서명하지 않으며,
  요청마다 현재 조회 권한과 resource 노출 여부를 별도로 검사합니다. 새 글 추가가 이미 받은
  다음 cursor 앞에 생겨도 이어 읽는 구간의 중복을 만들지 않습니다. 이 cursor는 database
  snapshot을 고정하지 않습니다. 후속 요청은 경계 아래의 현재 visibility를 읽으므로 그 사이
  삭제된 Post는 사라지고 삭제된 Comment는 같은 위치에 placeholder로 남습니다. 정렬 key가
  변하지 않은 나머지 visible row는 중복·건너뜀 없이 이어집니다.
- **배제한 대안**: action URI는 REST 기준과 어긋나고 전체 건수 `Page`는 제품 요구 없이 count
  query를 계약화합니다. Offset `Slice`는 현재 구현은 단순하지만 미래 feed 성장과 API
  pagination 구조 변경 비용을 남깁니다.
- **재검토 조건**: 번호 기반 page UX가 확정되면 별도 query endpoint나 count contract를
  추가하고, 검색·추천 정렬은 기존 cursor 의미를 바꾸지 않고 해당 read model의 독립 cursor를
  사용합니다. 같은 collection에 새 filter를 추가할 때는 cursor version과 정규화한 filter
  fingerprint를 함께 검토해 다른 query에서 token이 재사용되지 않게 합니다.

### D6. Plain text와 저장 한계

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

### D7. 인증, action authorization과 visibility의 분리

- **배경**: URL 보호만으로는 어느 계정이 특정 row를 수정할 수 있는지 판단할 수 없고,
  작성자 삭제와 운영자 숨김을 같은 status·DELETE로 처리하면 감사 이유와 복구 권한이
  뒤섞입니다.
- **검토한 선택지**: Controller role 조건만 사용, DB query에 author 조건만 사용, 범용 ACL
  rule engine 선도입, Security 인증 + module 내부 action policy + 조회 visibility 분리
- **선택**: SecurityFilterChain은 session 접근을 막고 Web adapter가 JWT `sub`를 UUID actor로
  변환합니다. Application은 `CommunityActionPolicy`에 `CREATE_POST`, `UPDATE_POST`,
  `DELETE_POST`, `CREATE_COMMENT`, `UPDATE_COMMENT`, `DELETE_COMMENT`와 필요한 resource 사실을
  전달합니다. 현재 `CREATE_POST`는 유효한 actor와 visible Board를 요구하고,
  `CREATE_COMMENT`는 visible 여부와 별개인 `commentable` Post를 요구합니다. `commentable`은
  author lifecycle이 `ACTIVE`이고 공개 조회에 노출돼 새 댓글을 받을 수 있는 상태입니다.
  Update/delete action은 저장된 author UUID 일치를 요구합니다. 조회 query는 확정된 작성자
  삭제 결과와 Board 존재를 visibility predicate로 적용합니다. Post는 `ACTIVE`만 visible하고,
  active Post의 Comment는 `ACTIVE`와 `AUTHOR_DELETED`가 모두 목록에 visible합니다.
- **선택 근거**: 인증, resource action 권한과 조회 노출을 각 책임 경계에서 검증하면 미래
  `HIDE`, `RESTORE`, `LOCK`, `MOVE`를 author DELETE 우회가 아니라 별도 action으로 추가할 수
  있습니다. 현재 필요 없는 동적 permission schema나 rule DSL은 만들지 않습니다.
- **영향**: 비작성자 수정·삭제는 `403`, parent mismatch와 비노출 row는 `404`입니다.
  `ROLE_ADMIN`·`ROLE_SUPER_ADMIN`이라는 이유만으로 author action을 우회하지 않습니다.
  Moderation이 생기면 restriction와 audit owner를 별도로 추가하고 query visibility가 author
  lifecycle, Board policy와 restriction을 조합합니다.
- **배제한 대안**: Controller-only 검사는 다른 inbound adapter가 생기면 우회되고, author
  조건 query만 사용하면 미존재와 권한 거부 정책을 명시적으로 test하기 어렵습니다. 범용 ACL은
  아직 존재하지 않는 board role·resource hierarchy와 persistence 비용을 현재 core에 부과합니다.
- **재검토 조건**: 관리자 hide·복구, board moderator, 공동 작성자 또는 resource별 permission이
  실제 요구가 되면 action set과 policy input을 확장하고, 규칙이 data-driven이어야 할 때만
  assignment schema를 도입합니다.

현재 state/action matrix는 다음과 같습니다.

| Resource state | Public 조회 | Create child | Author PATCH | Author DELETE | 다른 actor 변경 |
| --- | --- | --- | --- | --- | --- |
| Post `ACTIVE` | 목록·상세 `200` | Comment 생성 가능 | `200` | `AUTHOR_DELETED` 전이 후 `204` | `403` |
| Post `AUTHOR_DELETED` | 목록 제외, 상세·하위 `404` | `404` | `404` | 반복 `204` | `404` |
| Active Post의 Comment `ACTIVE` | 원문 `200` | 해당 없음 | `200` | `AUTHOR_DELETED` 전이 후 `204` | `403` |
| Active Post의 Comment `AUTHOR_DELETED` | placeholder `200` | 해당 없음 | `404` | 반복 `204` | `404` |
| Deleted Post의 모든 Comment | `404` | 해당 없음 | `404` | `404` | `404` |

`ROLE_ADMIN`·`ROLE_SUPER_ADMIN`도 위 author action을 우회하지 않습니다. 같은 작성자의 반복
DELETE는 이미 저장된 author reference와 lifecycle state를 읽어 판단하며 version을 다시
증가시키거나 `deleted_at`을 바꾸지 않습니다.

### D8. 낙관적 수정과 공유 댓글 변경-배타 게시글 삭제 lock

- **배경**: 같은 콘텐츠의 동시 수정과 Comment 변경 중 parent 삭제가 data loss·중간 상태를
  만들 수 있습니다. 모든 Comment 변경에 `FOR UPDATE`를 쓰면 같은 인기 Post의 독립 Comment
  변경끼리도 직렬화됩니다.
- **검토한 선택지**: last-write-wins, 모든 변경 pessimistic lock, Comment 변경·Post 삭제 모두
  `FOR UPDATE`, `@Version`과 `FOR SHARE`/`FOR UPDATE`, `FOR KEY SHARE` comment write와
  `FOR UPDATE` post delete 조합
- **선택**: Post·Comment에 version column과 JPA optimistic lock을 사용하고 conflict를
  `409`로 변환합니다. Comment 생성·수정·삭제는 `ACTIVE`인 Post row를 PostgreSQL
  native `SELECT ... FOR KEY SHARE`로 읽고, 생성은 추가로 `commentable`을 검사한 뒤 child를
  변경합니다. JPA `PESSIMISTIC_READ`의 `FOR SHARE`로 대체하지 않습니다. Post 삭제는 같은 row를
  명시적 `FOR UPDATE`로 잠근 뒤 `AUTHOR_DELETED` 상태와 `deleted_at`을 기록합니다.
- **선택 근거**: 일반 Post PATCH의 non-key UPDATE가 취득하는 `FOR NO KEY UPDATE`와 Comment의
  `FOR KEY SHARE`는 호환되고, 명시적 Post delete `FOR UPDATE`와는 충돌합니다. 따라서 서로 다른
  Comment 변경과 일반 Post PATCH는 병렬로 진행하고 parent 삭제와의 정확한 race만 짧게
  직렬화합니다. Transaction isolation은 현재 Spring/PostgreSQL 기본인
  `READ_COMMITTED`를 사용합니다. Lock 대기 뒤 같은 statement가 최신 committed Post state를
  검사하므로 non-active parent를 이전 snapshot의 active 값으로 처리하지 않습니다.
- **영향**: long transaction과 외부 account lookup을 lock 구간에 넣지 않습니다. author
  표시 조립은 read transaction 밖 또는 read-only 경계에서 수행합니다. Persistence 통합
  test는 독립 Comment 변경의 동시 진행과 각 Comment write/Post delete 두 commit 순서의
  terminal state를 실제 PostgreSQL에서 검증합니다. Post delete가 먼저 commit되면 기다리던
  Comment write는 Post의 non-active 상태를 다시 읽어 `404`로 끝납니다. Comment write가 먼저
  commit되면 변경 row를 보존하고, 뒤이어 Post가 삭제되면 parent visibility로 모든 하위
  endpoint에서 함께 숨깁니다.
- **배제한 대안**: last-write-wins는 동시 수정 손실을 숨기고 모든 row lock은 읽기·수정
  contention을 불필요하게 키웁니다. `FOR SHARE`는 Post PATCH의 `FOR NO KEY UPDATE`와도
  충돌하고, Comment write의 `FOR UPDATE`는 hot Post의 독립 변경까지 직렬화합니다.
- **재검토 조건**: lock wait가 실제 병목이면 Post lifecycle coordination을 별도 row/guard로
  분리하거나 queueing 전략을 비교합니다. stale client update가 실제 문제로 관찰되면 ETag·
  `If-Match`와 `412` 계약을 추가합니다.

### D9. Additive migration과 exact rollback image 자격

- **배경**: 새 기능 rollback 때문에 이미 적용된 Flyway migration을 수정하거나 table을
  즉시 drop할 수 없습니다.
- **검토한 선택지**: down migration 자동화, 기존 migration 수정, raw 이전 image, 새 migration
  source를 포함한 exact compatibility image, current schema 위 forward fix
- **선택**: 현재 branch의 V12 다음인 `V13__create_community_core.sql`로 table·constraint·
  index·자유게시판 seed를 추가합니다. 구현 시작 전 main에 새 V13이 생겼다면 code를 만들기
  전에 번호만 다음 미사용 version으로 재동결합니다. Staging rollout을
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

### D10. 변경 core와 후속 read model·event의 단계적 분리

- **배경**: 검색, 인기 feed, 알림과 실시간 전달은 같은 콘텐츠 변경을 소비하지만 각각 조회·
  재시도·장애 생명주기가 다릅니다. Consumer가 없는데 event/outbox를 먼저 만들면 유지할
  계약과 실패 surface만 늘어납니다.
- **검토한 선택지**: 모든 후속 값을 Post aggregate에 저장, 처음부터 event/outbox·검색 문서
  구축, 실제 consumer가 생길 때 use case 뒤 공개 event와 durable publication 추가
- **선택**: 현재 변경은 community application use case만 통과하고 목록·상세는 replaceable
  projection이 담당합니다. 실제 첫 consumer 전에는 `community.api` event, outbox와 message
  broker를 만들지 않습니다. 검색·알림처럼 durable consumer가 확정되면 use case transaction
  결과에 public board/post/comment id와 event id·occurredAt을 가진 최소 event를 추가하고,
  요구 신뢰도에 따라 Spring Modulith event publication 또는 explicit outbox를 비교합니다.
- **선택 근거**: mutation 진입점과 public identity가 이미 안정돼 있어 event emission을 한
  경계에 추가할 수 있고, 현재에는 dead infrastructure가 없습니다. Consumer는 community
  table을 읽지 않고 event 또는 `community.api` 계약으로 own read model을 만듭니다.
- **영향**: 현재 댓글 수·반응 수·인기 점수는 Post row에 넣지 않습니다. 화면 요구가 생기면
  실시간 grouped query, transaction counter, eventual read model의 정확성·쓰기 경합·복구를
  비교한 뒤 owner를 정합니다. Event schema는 내부 Entity snapshot이 아니라 consumer가 필요한
  최소 공개 사실만 가집니다.
- **배제한 대안**: Post aggregate의 파생값 누적은 변경 이유와 hot row contention을 키우고,
  consumer 없는 outbox는 cleanup·재처리·관측 책임을 근거 없이 도입합니다.
- **재검토 조건**: notification·search·analytics 중 첫 durable consumer, cross-module 후속 작업,
  또는 동기 query로 충족할 수 없는 측정된 latency가 확정됩니다.

## 확정 제품 결정의 기술 영향

### 공개 조회와 보호 쓰기

Board·Post·Comment의 모든 `GET` operation은 중앙 public operation source에 추가합니다.
OpenAPI GET operation에는 Bearer security requirement와 `401`·`403` response를 넣지 않고
anonymous Web·Security test를 통과시킵니다. 생성·수정·삭제는 기존 기본 계약인
`SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`를 요구합니다. 공개되는 값은 콘텐츠,
account public id와 display name뿐이며 내부 PK·email·account 내부 상태를 노출하지 않습니다.

표시 이름과 콘텐츠가 인터넷에 공개되므로 production rollout 전 moderation·rate-limit을 별도
작업으로 확정합니다. 이 운영 선행 조건은 API 구현 완료와 production 공개 준비 완료를
구분하며, 현재 공개 조회 계약을 조건부로 되돌리지 않습니다.

### 자유게시판 seed

최초 migration은 다음 한 row만 literal로 삽입합니다.

| Public UUID | Code | Name | Display order |
| --- | --- | --- | --- |
| `54be05ef-b451-4865-b124-b7633d04511e` | `free` | `자유게시판` | `100` |

Board identity와 Post `board_id NOT NULL` FK는 게시판이 하나여도 유지합니다. 후속 Board는 새
row를 추가하고 navigation에 노출하는 additive 변경이며 기존 Post backfill이 없습니다.
Board create/update/reorder/archive와 글 이동은 현재 구현하지 않습니다.

### 작성자 삭제의 혼합 visibility

Post와 Comment 모두 row·원문·`author_account_public_id`를 DB에 보존하고
`lifecycle_state VARCHAR(20) NOT NULL`과 `deleted_at TIMESTAMPTZ NULL`로 작성자 삭제를
표현합니다. 작성자 삭제는 moderation restriction과 다른 lifecycle입니다.

| Resource | DELETE 뒤 조회 | 변경·반복 DELETE | Schema·index·FK |
| --- | --- | --- | --- |
| Post | Board 목록에서 제외, 상세와 모든 하위 Comment endpoint `404` | PATCH와 새 Comment 생성 `404`; 같은 작성자 반복 DELETE `204`, 다른 actor `404` | 원문·author 보존, `ACTIVE` partial cursor index, Comment FK `RESTRICT` |
| Active Post의 Comment | 목록에서 `AUTHOR_DELETED` placeholder, body·author field 생략 | PATCH `404`; 같은 작성자 반복 DELETE `204`, 다른 actor `404` | 원문·author 보존, active/deleted 모두 포함하는 cursor index |

삭제 Post의 하위 Comment row는 상태를 일괄 변경하지 않습니다. Parent visibility predicate가
모든 하위 endpoint를 숨기므로 하위 Comment author도 조회·수정·삭제할 수 없습니다. 삭제
Comment 응답은 `placeholder="삭제된 댓글입니다."`와 `deletedAt`만 추가하며 원문과 account
표현을 노출하지 않습니다. 보존 기간·privacy redaction·관리자 열람은 account 탈퇴·익명화나
production 운영 전에 별도 privacy 작업으로 결정합니다.

## 목표 API 계약

모든 GET은 public이고 모든 write는 인증·session 상태와 resource action policy를 적용합니다.

| Method | Path | Request | Success | 권한 |
| --- | --- | --- | --- | --- |
| `GET` | `/api/v1/boards` | - | `200` Board collection | 공개 |
| `GET` | `/api/v1/boards/{boardId}` | - | `200` Board 상세 | 공개 |
| `POST` | `/api/v1/boards/{boardId}/posts` | title, body | `201`, body, `Location` | 인증 쓰기 |
| `GET` | `/api/v1/boards/{boardId}/posts?limit=20&cursor=...` | query | `200` post cursor slice | 공개 |
| `GET` | `/api/v1/posts/{postId}` | - | `200` detail | 공개 |
| `PATCH` | `/api/v1/posts/{postId}` | title 또는 body | `200` | 작성자 |
| `DELETE` | `/api/v1/posts/{postId}` | - | `204` | 작성자 |
| `POST` | `/api/v1/posts/{postId}/comments` | body | `201`, body, `Location` | 인증 쓰기 |
| `GET` | `/api/v1/posts/{postId}/comments?limit=50&cursor=...` | query | `200` comment cursor slice | 공개 |
| `PATCH` | `/api/v1/posts/{postId}/comments/{commentId}` | body | `200` | 작성자 |
| `DELETE` | `/api/v1/posts/{postId}/comments/{commentId}` | - | `204` | 작성자 |

모든 request·response body는 `application/json`입니다. `CreatePostRequest`는 required `title`,
`body`, `UpdatePostRequest`는 optional `title`, `body` 중 하나 이상을 받습니다.
`CreateCommentRequest`와 `UpdateCommentRequest`는 required `body`를 받습니다. 알 수 없는 field는
무시하고 domain·저장·응답에 전달하지 않습니다. Null은 required field 누락 또는 빈 PATCH로
`400`입니다.

Post 생성은 active detail body와 `/api/v1/posts/{postId}` `Location`, Comment 생성은 active
comment body와 `/api/v1/posts/{postId}/comments/{commentId}` `Location`을 반환합니다. PATCH도
변경 뒤 active detail/comment body를 반환합니다. DELETE `204`에는 body가 없습니다. 모든 UUID
path parameter가 문법적으로 잘못됐으면 `400`, 올바르지만 없거나 비노출이면 아래 resource별
`404`를 반환합니다.

최초 범위의 Board·Post·Comment 응답은 공개 조회에서도 `Cache-Control: no-store`를
사용합니다. 현재 display name·작성자 삭제·visibility가 즉시 바뀔 수 있는데 validator와
무효화 계약은 없기 때문입니다. HTTP cache가 실제 요구가 되면 ETag·재검증과 삭제·moderation
무효화 경로를 함께 설계하고 이 값을 완화합니다.

### 응답 표현

`BoardResponse`:

- `boardId`: UUID
- `code`: stable string
- `name`: 사용자 표시 이름
- `displayOrder`: integer

`BoardCollectionResponse`는 bare array가 아니라 `{ items: BoardResponse[] }` envelope를
사용합니다. 현재 endpoint는 화면 navigation에 노출할 seed Board의 bounded collection이며
total count를 제공하지 않습니다. 이후 차량별 수천 개 Board 탐색이 필요하면 이 endpoint에
전부 섞지 않고 차량 scope/filter가 명시된 별도 cursor query를 추가합니다.

Post 응답은 visible `ACTIVE` row만 표현합니다. Summary는 `postId`,
`board: { boardId, code, name, displayOrder }`, `state="ACTIVE"`, `title`,
`author: { accountId, displayName }`, `createdAt`, `updatedAt`을 가집니다. Detail은 summary에
`body`를 추가합니다. `AUTHOR_DELETED` Post는 response variant가 아니라 목록에서 제외되고
상세·하위 endpoint가 `404`이므로 OpenAPI에 deleted Post schema를 만들지 않습니다. Comment
목록은 Post detail에 중첩하지 않고 별도 slice endpoint가 소유합니다.

Comment 응답도 `state` discriminator를 사용합니다. 공통 field는 `commentId`, `postId`,
`state`, `createdAt`, `updatedAt`입니다.

- `ActiveCommentResponse`: `state="ACTIVE"`, `body`,
  `author: { accountId, displayName }`
- `AuthorDeletedCommentResponse`: `state="AUTHOR_DELETED"`,
  `placeholder="삭제된 댓글입니다."`, `deletedAt`; `body`, `author`는 field 자체를 생략합니다.

Comment OpenAPI schema는 두 variant의 `oneOf`이며 discriminator property는 `state`입니다.
Web·OpenAPI test는 variant별 required/omitted field, discriminator, placeholder literal과
삭제 variant의 account·원문 비노출을 고정합니다.

Post와 Comment slice는 다음 field를 갖는 module-local response를 각각 사용합니다.

- `items`
- `limit`
- `hasNext`
- `nextCursor`: 다음 slice가 없으면 null인 opaque string

조회 query는 `limit + 1`행을 읽고 초과 한 행을 응답에서 제거해 `hasNext`와 `nextCursor`를
계산합니다.
아직 다른 module의 안정된 consumer가 없으므로 범용 `shared` slice DTO를 먼저 만들지 않습니다.

### Validation과 오류

| 상황 | Status | Code |
| --- | --- | --- |
| field 누락·Unicode blank·code point 길이·limit 범위·cursor 형식·빈 PATCH | `400` | 공통 `INVALID_REQUEST`와 field detail |
| 인증 누락·invalid token | `401` | 기존 공통 Security code |
| session·password 상태 불충족 또는 비작성자 | `403` | 기존 접근 오류 또는 `COMMUNITY_CONTENT_FORBIDDEN` |
| post 미존재·비노출 | `404` | `COMMUNITY_POST_NOT_FOUND` |
| board 미존재·비노출 | `404` | `COMMUNITY_BOARD_NOT_FOUND` |
| comment 미존재·parent 불일치·비노출 | `404` | `COMMUNITY_COMMENT_NOT_FOUND` |
| optimistic lock conflict | `409` | `COMMUNITY_CONTENT_CONFLICT` |
| unsupported content type | `415` | 기존 공통 media type 오류 |
| 저장된 active 작성자의 공개 profile 누락 | `500` | `COMMUNITY_AUTHOR_PROFILE_UNAVAILABLE` |

반복 DELETE는 Post와 active Post 아래 Comment 모두 내부 author UUID가 같은 actor에게만
`204`, 다른 actor에게 `404`입니다. 삭제 Post 아래 Comment endpoint는 parent visibility로
actor와 무관하게 `404`입니다.

작성자 profile 누락 오류에는 account id·content 원문을 넣지 않고 request id와 고정 code만
노출합니다. 운영 log에는 content public id와 누락된 account public id를 구조화 field로
기록하되 email·JWT·본문은 기록하지 않습니다.

## 목표 persistence 계약

첫 migration의 table·column 계약은 다음과 같습니다. UUID는 application에서 생성하므로 DB
default를 두지 않고, 감사 시각은 저장소 관례대로 `NOW()`를 기본값으로 둡니다.

### `community_board`

| Column | Type | Nullable | Default | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` | no | identity | 내부 PK |
| `public_id` | `UUID` | no | 없음 | API 공개 식별자, unique |
| `code` | `VARCHAR(50)` | no | 없음 | 변경되지 않는 seed·business key, unique |
| `name` | `VARCHAR(80)` | no | 없음 | 사용자 표시 이름 |
| `display_order` | `INTEGER` | no | 없음 | 0 이상 navigation 정렬값. 중복 허용, public id tie-break |
| `created_at` | `TIMESTAMPTZ` | no | `NOW()` | 생성 시각 |
| `updated_at` | `TIMESTAMPTZ` | no | `NOW()` | 마지막 갱신 시각 |

현재 Board는 seed와 조회만 지원하므로 version·status를 미리 넣지 않습니다. 관리 기능이
생길 때 실제 상태 전이에 맞춰 additive column을 추가하고 기존 `display_order`를 사용합니다.
`54be05ef-b451-4865-b124-b7633d04511e`/`free`/`자유게시판`/`100`을 migration에
삽입하고, 환경별 fixture가 다른 identity·순서를 만들지 않게 합니다.

### `community_post`

| Column | Type | Nullable | Default | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` | no | identity | 내부 PK |
| `public_id` | `UUID` | no | 없음 | API 공개 식별자, unique |
| `board_id` | `BIGINT` | no | 없음 | `community_board.id` FK |
| `author_account_public_id` | `UUID` | no | 없음 | JWT `sub`와 비교할 작성자 reference |
| `title` | `VARCHAR(120)` | no | 없음 | 삭제 뒤에도 보존하는 canonical 제목 |
| `body` | `TEXT` | no | 없음 | 삭제 뒤에도 보존하는 최대 10,000 code point plain text |
| `lifecycle_state` | `VARCHAR(20)` | no | `'ACTIVE'` | `ACTIVE` 또는 `AUTHOR_DELETED` |
| `deleted_at` | `TIMESTAMPTZ` | yes | 없음 | 작성자 삭제 시각 |
| `version` | `BIGINT` | no | `0` | optimistic lock |
| `created_at` | `TIMESTAMPTZ` | no | `NOW()` | 생성 시각 |
| `updated_at` | `TIMESTAMPTZ` | no | `NOW()` | 마지막 갱신 시각 |

### `community_comment`

| Column | Type | Nullable | Default | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT GENERATED BY DEFAULT AS IDENTITY` | no | identity | 내부 PK |
| `public_id` | `UUID` | no | 없음 | API 공개 식별자, unique |
| `post_id` | `BIGINT` | no | 없음 | `community_post.id` FK |
| `author_account_public_id` | `UUID` | no | 없음 | JWT `sub`와 비교할 작성자 reference |
| `body` | `TEXT` | no | 없음 | 삭제 뒤에도 보존하는 최대 2,000 code point plain text |
| `lifecycle_state` | `VARCHAR(20)` | no | `'ACTIVE'` | `ACTIVE` 또는 `AUTHOR_DELETED` |
| `deleted_at` | `TIMESTAMPTZ` | yes | 없음 | 작성자 삭제 시각 |
| `version` | `BIGINT` | no | `0` | optimistic lock |
| `created_at` | `TIMESTAMPTZ` | no | `NOW()` | 생성 시각 |
| `updated_at` | `TIMESTAMPTZ` | no | `NOW()` | 마지막 갱신 시각 |

Constraint와 index는 다음 이름과 의미를 사용합니다.

- 각 table의 `pk_community_*` PK, `uk_community_*_public_id` unique와 Board의
  `uk_community_board_code` unique
- `fk_community_post_board`: `board_id -> community_board(id) ON DELETE RESTRICT`
- `fk_community_comment_post`: `post_id -> community_post(id) ON DELETE RESTRICT`
- `chk_community_*_updated_at`: `updated_at >= created_at`
- Post·Comment의 `chk_community_*_lifecycle_state`: `ACTIVE`, `AUTHOR_DELETED`만 허용
- Post·Comment의 `chk_community_*_deleted_at`: `ACTIVE`면 `deleted_at IS NULL`,
  `AUTHOR_DELETED`면 `deleted_at IS NOT NULL AND deleted_at >= created_at AND
  deleted_at <= updated_at`
- `chk_community_post_title`, `chk_community_post_body`, `chk_community_comment_body`는
  canonical Unicode White_Space trim·CR 부재·
  `CHAR_LENGTH` code point 범위를 D6과 같은 고정 식으로 검사합니다. 삭제돼도 원문이 남으므로
  content column과 content check는 모든 상태에서 동일하게 적용됩니다.
- `chk_community_board_display_order`: `display_order >= 0`
- `chk_community_board_code`: `code ~ '^[a-z][a-z0-9-]{0,49}$'`
- `chk_community_board_name`: canonical trim 뒤 1~80 code point
- `chk_community_post_version`, `chk_community_comment_version`: `version >= 0`
- `idx_community_board_order`: `(display_order ASC, public_id ASC)`
- `idx_community_post_board_cursor_active`:
  `(board_id, created_at DESC, public_id DESC) WHERE lifecycle_state = 'ACTIVE'`
- `idx_community_comment_post_cursor`:
  `(post_id, created_at ASC, public_id ASC)`; 삭제 Comment placeholder도 읽으므로 partial
  predicate를 두지 않습니다.
- `author_account_public_id`에는 account cross-module FK를 만들지 않습니다.

목록 응답에는 최초 범위에 없는 댓글 수 집계를 넣지 않습니다. 댓글 수가 실제 화면 요구로
확정되면 상세 계산·목록 grouped query·비정규화 counter의 비용과 삭제 댓글 포함 의미를 별도
결정으로 비교합니다.

## Transaction과 동시성

| Use case | Transaction 경계 | 동시성·실패 처리 |
| --- | --- | --- |
| Post 생성 | Board 존재·허용 확인, public id 생성, domain validation, post insert | Board FK·unique·check 실패 rollback |
| Post 수정 | post load, author 검사, domain update, save | version conflict 409 |
| Post 삭제 | post `FOR UPDATE`, action policy, `AUTHOR_DELETED`·`deleted_at` update | comment 생성과 순서 직렬화, 같은 작성자 반복 `204` |
| Comment 생성 | active post native `FOR KEY SHARE`, commentable 검사, comment insert | 다른 comment write·Post PATCH와 공유, post delete `FOR UPDATE`와 충돌 |
| Comment 수정·삭제 | active post native `FOR KEY SHARE`, comment load, author 검사, 수정 또는 `AUTHOR_DELETED`·`deleted_at` update | 다른 comment write·Post PATCH와 공유, post delete와 충돌, 삭제 parent 아래 모두 404, 삭제 Comment PATCH 404, 같은 작성자 반복 DELETE 204, version conflict 409 |
| 목록·상세 | cursor read query와 transaction 밖/bounded account batch 조립 | count·account lookup N+1 금지 |

account batch lookup은 community 변경 transaction 안에서 DB row lock을 유지한 채 호출하지
않습니다. account 공개 profile이 구조적으로 누락된 경우 조용히 임의 이름을 만들지 않고
application 오류와 운영 log로 드러내며, 향후 account 익명화 정책이 생기면 account owner가
공개 표현을 반환하도록 계약을 확장합니다.

## 목표 package와 경계

아래 그림은 `Planned` 이후 첫 core·schema 구현에서 만들어질 목표 경계입니다. 설계 gate 전에
빈 package나 module을 만드는 순서를 뜻하지 않습니다.

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

- 변경: UseCase → CommunityActionPolicy → Domain → store/lookup Port → persistence adapter
- 조회: Controller → application query service → visibility predicate를 적용한 persistence projection
  → account batch API → DTO
- Web Request는 `toCommand()`/`toQuery()`, Response는 `from(...)` 경계 factory를 사용합니다.
- JPA Entity와 projection은 persistence adapter 밖으로 노출하지 않습니다.

## Test 전략과 품질 gate

### Domain·Application 단위 test

- Board 밖 Post 생성 거부와 Post의 immutable Board reference
- `U+0000`, 고립 high·low surrogate 거부와 정상 emoji surrogate pair 허용
- title/body/comment의 CRLF·CR 정규화, ASCII/Unicode White_Space blank와 양끝 trim
- BMP 문자, 정상 supplementary emoji와 결합 문자의 Unicode code point 최대 길이 경계
- action policy의 author 일치 수정·삭제 성공, 비작성자 거부와 admin 우회 부재
- 혼합 삭제 상태 전이, parent 상태별 action과 actor별 반복 DELETE
- parent post 상태별 comment 생성 허용·거부
- account batch 결과를 post/comment slice에 누락 없이 조립
- account batch 빈 입력은 query 없이 빈 결과, active 작성자 누락은 redacted
  `COMMUNITY_AUTHOR_PROFILE_UNAVAILABLE`

### Web·Security test

- Board 목록·상세와 모든 Post·Comment endpoint 성공 status, body, `Location`
- 자유게시판 literal seed와 `(displayOrder ASC, publicId ASC)` Board collection 순서
- 삭제 Post 비노출, 삭제 Comment placeholder와 parent별 변경 visibility
- request validation, 빈 PATCH, malformed UUID·cursor, parent mismatch
- escaped `U+0000`·고립 high/low surrogate의 `400`과 정상 emoji pair 성공
- 공개 GET과 보호 write의 anonymous·full session·limited/password-change-required matrix
- 모든 community 응답의 `Cache-Control: no-store`
- 비작성자 403, 미존재·비노출 404와 공통 Problem Details
- active 작성자 profile 누락의 500 고정 code와 응답·log 개인정보 비노출
- OpenAPI 전체 path·response·schema·public/protected 분류

### PostgreSQL 통합 test

- 최신 Flyway migration 전체 적용과 JPA mapping
- Board literal seed·UUID/code unique·display order, Post Board FK, content check, Comment
  `RESTRICT` FK와 상태·삭제시각 constraint
- Board-scoped Post 최신순·Comment 작성순 cursor slice, `limit + 1`, public UUID tie-breaker,
  고정 dataset의 다음 cursor 중복·누락 부재와 별도 total count 부재
- collection kind·path scope가 다른 cursor 거부와 동시 추가·삭제 시 D5의 non-snapshot
  continuation 의미
- slice당 account batch lookup 1회, query N+1 부재
- Java canonical normalizer와 PostgreSQL constraint가 CRLF·Unicode whitespace·emoji 경계에서
  같은 값을 허용·거부하는 대조 test
- HTTP JSON→domain→PostgreSQL 경로가 `U+0000`·고립 surrogate를 저장·치환하지 않고 거부하며
  정상 surrogate pair를 원형대로 저장하는 대조 test
- optimistic update conflict
- 독립 comment write와 Post PATCH의 병렬 진행, 각 comment write-post delete 경합의 두 terminal state

### 전체 gate

- Spring Modulith `ApplicationModules.verify()`
- ArchUnit module/internal/domain/application/persistence 경계
- Docker daemon preflight와 PostgreSQL Testcontainers
- `./gradlew check`
- Frozen 개발 후보의 `$verify-dev` 독립 검증
- 검증된 개발 source를 입력으로 한 `$documentation` SOT 작성과 frozen Markdown 후보의
  `$verify-documentation final` 독립 감사
- 최종 합류 시 문서가 소비한 개발 source hash와 최종 개발 후보의 drift 부재
- Git·remote actual 결과에 따른 work item·router의 `In Progress`/`Done` 재투영과 상태
  Markdown의 별도 documentation 검증·Git phase
- Feature push, same-repository PR, review·main merge를 별도 권한과 효과로 분리
- 상태-only commit도 별도 PR로 main에 통합한 뒤 새 exact main SHA의 required CI와 최신 main
  Sonar 재확인; 실패·미확정이면 `Done` 금지
- push가 별도 승인된 구현에서는 final SHA required CI와 Sonar revision·Quality Gate·Open issues

## 후속 기능별 additive 확장 경로

아래는 현재 구현 목록이 아니라 후속 기능이 core를 소비하는 방향입니다. 실제 schema·event·
module은 해당 기능의 제품 의미와 운영 책임이 확정된 work item에서 결정합니다.

### 게시판 관리, tag와 차량별 라운지

- Board 관리가 필요하면 `status`, optimistic version과 관리자 action을 `community_board`에
  추가하고 기존 `display_order`를 변경합니다. 기존 seed Board와 Post FK는 유지합니다.
- Tag는 `community_tag`와 `community_post_tag` association으로 추가하고 Post 본문이나 JSONB에
  문자열 배열로 넣지 않습니다.
- 차량별 라운지는 Board를 복제하거나 Post category enum을 늘리기보다, 카탈로그의 안정된
  public reference가 확정된 뒤 `community_board_vehicle_scope` association을 추가합니다.

### 대댓글, 반응, 북마크와 구독

- 대댓글은 제품이 한 단계 reply인지 임의 깊이 thread인지 결정한 뒤 nullable
  `parent_comment_id` 또는 별도 relation을 새 migration으로 추가합니다. 기존 평면 Comment는
  parent가 없는 root로 그대로 유효합니다. 현재 작성순 flat endpoint의 의미를 바꾸지 않고
  thread 전개 규칙을 가진 별도 query/response를 추가합니다.
- 반응은 허용 reaction 종류와 취소 의미를 정한 뒤 Post·Comment별 target aggregate와
  `(target, actor, reaction)` unique를 둡니다. Generic polymorphic FK로 참조 무결성을 포기하지
  않습니다.
- 북마크·구독은 actor 소유의 별도 aggregate이며 Post row의 boolean·account 목록으로 넣지
  않습니다. User별 목록 query와 privacy·삭제 처리를 해당 owner가 소유합니다.

### 검색, 인기·추천과 count

- 여러 Board를 합친 최신 feed는 Post 변경 모델을 합치지 않고 Board visibility를 적용한
  별도 query projection으로 추가합니다. 인기·추천으로 정렬 의미가 달라지면 전용 endpoint와
  cursor를 사용합니다.
- 단순 필터·작은 규모 검색은 PostgreSQL query/index를 먼저 검토하고, 전문 검색·ranking·
  개인화가 확정되면 D10의 event를 소비하는 별도 read model을 만듭니다.
- 댓글 수·반응 수는 화면 정확성 요구와 쓰기 경합을 확인하기 전 Post row에 선반영하지
  않습니다. grouped query, transaction counter, eventual projection을 같은 의미·복구 기준으로
  비교합니다.
- 각 read model은 자체 cursor와 rebuild 방법을 소유하며 기존 Post cursor 의미를 바꾸지
  않습니다.

### 알림과 실시간 전달

- 첫 알림 consumer가 확정되면 `PostCreated`, `CommentCreated` 같은 consumer가 실제로 필요한
  최소 event만 public 계약으로 추가합니다. Event에는 내부 PK·본문 원문·account 내부 정보를
  넣지 않습니다.
- Durable delivery가 필요하면 Spring Modulith publication 또는 explicit outbox의 transaction,
  retry, idempotency, retention과 replay owner를 함께 결정합니다.
- WebSocket/SSE는 같은 event의 전달 adapter이며 community 변경 transaction을 network session과
  묶지 않습니다.

### Media와 rich text

- Media 저장·검사·보존은 별도 lifecycle owner가 공개 attachment reference를 제공하고,
  community는 Post-Attachment association과 노출 순서만 소유합니다.
- Markdown·rich text는 content type, sanitization, link·preview와 과거 plain text 호환성이
  확정된 뒤 새 representation으로 추가합니다. 기존 body 의미를 조용히 바꾸지 않습니다.
- 투표처럼 Post와 생성·삭제 lifecycle을 공유하는 특수 콘텐츠는 Post public identity를
  참조하는 1:1 확장 aggregate와 option row로 추가합니다. 독립 lifecycle이면 별도 resource로
  두며, Post에 유형별 nullable column과 만능 type enum을 누적하지 않습니다.

### 신고, moderation과 제재

- 신고는 신고자·대상·사유·중복·상태를 가진 별도 aggregate입니다. 운영자 숨김·잠금·복구는
  작성자 삭제와 다른 action, restriction record와 audit를 사용합니다.
- 편집 이력이 제품·moderation 요구가 되면 application 변경 use case가 public content id를
  참조하는 append-only revision을 기록합니다. 현재 optimistic `version`을 이력 저장소로
  오해하거나 원문 snapshot을 Post row에 누적하지 않습니다.
- Account 제재는 account owner의 상태·role 정책과 협력하고 community table을 직접 수정해
  account를 제재하지 않습니다.
- Query visibility는 author lifecycle, Board access와 moderation restriction의 결과를 조합하되
  원문 보존·운영자 열람·복구 기간은 moderation/privacy work item이 결정합니다.

### 미래 차량 카탈로그 연결과 재작업 경계

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
- 조회는 공개하지만 쓰기는 정상 full session만 허용합니다.
- rate limiting, 신고·관리자 hide와 sanctions가 없으므로 이 core API의 기술 완료를 production
  공개 운영 준비 완료로 보고하지 않습니다.
- 새 table과 endpoint는 기존 account/auth operation을 변경하지 않는 additive rollout입니다.
  다만 raw 이전 image의 Flyway compatibility를 뜻하지 않으며 exact rollback image 검증 또는
  forward recovery가 필요합니다.
- community 조회 latency, 4xx·5xx와 conflict 비율은 기존 API 완료 event/logging으로
  관찰하되 콘텐츠 원문을 metadata에 추가하지 않습니다.

### 확장 재검토 신호

| 관찰 신호 | 먼저 비교할 확장 |
| --- | --- |
| account batch lookup 지연·누락이 community 조회 availability를 지배함 | bounded cache, redacted fallback 계약, author read model |
| Post row lock wait가 Comment write latency를 지배함 | lifecycle guard 분리, lock 범위 축소, hot Post 전용 처리 |
| Cursor query plan·p95가 목표를 벗어나거나 table/index가 운영 한계를 넘음 | index 재검증, archive·partition, read replica/read model |
| 목록에서 댓글·반응 집계가 반복 query 병목이 됨 | grouped query, transaction counter, eventual projection 비교 |
| 검색·추천·알림 consumer가 확정됨 | D10의 최소 event와 durable publication·rebuild 계약 |
| community module 변경 이유와 팀 ownership이 moderation·media와 분리됨 | public API를 먼저 고정한 뒤 별도 module 분리 |

수치 임계값은 제품 SLO와 실제 baseline 없이 임의로 만들지 않습니다. 각 후속 work item은
관찰한 query plan, latency, lock wait, storage와 정확성 요구를 입력으로 임계값과 전환 oracle을
고정합니다.

## 관련 문서

- **사용자 결정과 구현 계획**:
  [016 확장 가능한 차량 비연결 일반 커뮤니티 기반](016-general-community.md)
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
  [250 PostgreSQL](../../../tech-stack/250-postgresql.md),
  [PostgreSQL 16 explicit locking](https://www.postgresql.org/docs/16/explicit-locking.html)
