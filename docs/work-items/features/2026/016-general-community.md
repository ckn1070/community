# 확장 가능한 차량 비연결 일반 커뮤니티 기반

## 상태

Planned

## 달성할 결과

차량 카탈로그가 없어도 회원이 게시판에서 일반 게시글과 댓글을 작성하고, 누구나 로그인 없이
목록·상세와 작성자 표시 이름을 볼 수 있는 첫 커뮤니티 기능을 만듭니다. 초기 화면에는
자유게시판 하나와 plain text 게시글·평면 댓글만 제공하지만, 게시글은 처음부터 안정된
`Board -> Post -> Comment` 구조에 속합니다.

확장성은 미래 기능을 미리 구현하는 방식으로 확보하지 않습니다. 대신 게시판, 콘텐츠 변경,
조회, 권한, 작성자 표현과 외부 소비 경계를 분리해 대댓글·반응·북마크·검색·알림·media·
moderation·차량 연결이 생길 때 기존 게시글 식별자와 핵심 생명주기를 교체하지 않고 새 관계·
정책·read model을 추가할 수 있게 합니다.

차량·차종 연결, 차종별 게시판과 오너 인증을 임시 문자열이나 임시 테이블로 대신하지
않습니다. 이후 카탈로그 연결은 기존 게시글 식별자와 콘텐츠를 유지한 채 별도 참조를
추가하는 작업으로 제한합니다.

공개 조회, 자유게시판 하나와 작성자 삭제 뒤 노출 방식은 사용자의 댓글로 확정됐습니다.
API·domain·schema·동시성·확장 경계를 상세 설계에서 exact 계약으로 동결했고, fresh 독립 계획
감사의 필수 결함 0건으로 통과했습니다. 검증된 checkpoint를 기록한 `Planned` 상태이며,
구현자는 제품 또는 기술 구조를 다시 선택하지 않습니다.

## 현재 구현할 핵심 범위

- 공개 게시판 목록·상세 조회와 자유게시판 한 row seed
- 일반 게시글 생성, 목록, 상세, 수정, 삭제
- 게시글의 평면 댓글 생성, 목록, 수정, 삭제
- account 공개 식별자와 현재 표시 이름을 이용한 작성자 표현
- 작성자 본인만 수정·삭제하는 현재 정책과 후속 권한을 수용할 module 내부 action policy
- cursor 기반 slice 목록, 입력 검증, 공통 Problem Details 오류와 OpenAPI 계약
- 콘텐츠 변경 모델과 조회 projection의 분리
- `community` 모듈, 작성자 삭제 상태를 보존하는 Flyway schema와 PostgreSQL 조회 인덱스
- Security·module boundary·domain/application·Web·persistence 회귀 테스트

## 지금 구현하지 않을 기능

- 차량·브랜드·모델·세대·트림 식별자, 차량 자유 입력 문자열과 차량 연결
- 차종별 라운지와 오너 인증·오너 배지
- 대댓글, 반응, 북마크, 구독, 알림과 실시간 갱신
- 이미지·첨부 파일, HTML·Markdown 편집기와 media 저장소
- 태그, 전문 검색, 추천·인기 점수, 별도 cache와 비동기 read model
- 신고·관리자 숨김·제재·복구 UI와 상세 운영 정책
- account 공개 profile 수정, 탈퇴·익명화 정책 변경
- 게시판 관리자 CRUD·권한 위임
- frontend 화면과 production 공개 rollout

위 기능을 구현하지 않는 것과 확장 경계를 설계하지 않는 것은 다릅니다. 상세 설계는 각 미래
기능이 어느 owner와 additive schema/API로 붙는지, 어떤 조건에서 event·outbox·cache·별도
module을 도입할지를 소유합니다. 사용하지 않는 generic content table, 미사용 event/outbox와
빈 확장 interface는 현재 코드에 만들지 않습니다.

제외 항목 중 신고·관리자 숨김·제재는 사용자 생성 콘텐츠를 production에 공개하기 전에
별도 기능과 운영 책임으로 확정해야 합니다. 이번 작업은 그 정책을 임시 관리자 삭제 권한으로
대신하지 않습니다.

## 요구사항

1. 외부 API와 DB에는 내부 account·board·post·comment PK를 노출하지 않고 UUID 공개
   식별자를 사용합니다.
2. 모든 게시글은 `community_board` 하나를 필수로 참조합니다. 초기 게시판이 하나여도
   `board_id`를 생략하거나 post의 category 문자열로 대신하지 않습니다.
3. 게시판의 공개 식별자, 변경되지 않는 code, 표시 이름과 `displayOrder`를 분리합니다. 초기
   seed는 migration에 literal UUID·code·order를 명시해 환경마다 같은 외부 identity와 순서를
   가집니다. 최초 row는 `public_id=54be05ef-b451-4865-b124-b7633d04511e`, `code=free`,
   `name=자유게시판`, `display_order=100`입니다. 현재에는 조회와 seed만 구현하고 비활성화·접근
   정책은 실제 관리 기능이 생길 때 additive 확장합니다.
4. 게시글은 제목 1~120자와 plain text 본문 1~10,000자를 받습니다. 댓글은 plain text 본문
   1~2,000자를 받으며 대댓글 관계를 만들지 않습니다.
5. 제목·본문·댓글은 올바른 surrogate pair로 표현되는 Unicode scalar 값만 허용하고
   `U+0000`과 고립 high·low surrogate를 DB 도달 전에 거부합니다. 허용 문자열의 Unicode
   code point 수로 길이를 계산하고, Unicode White_Space code point 집합만으로 된 입력을
   blank로 거부합니다. 같은 repertoire·정규화·길이 규칙을 Web, domain과 PostgreSQL 저장
   대조 test로 지킵니다.
6. 게시판 목록은 `(displayOrder ASC, publicId ASC)`, 게시글 목록은 최신순, 댓글은 작성순으로
   안정 정렬합니다. Cursor는 collection 종류와 board/post scope에 결속된 versioned opaque
   API 값이며 내부 PK가 아니라 `(createdAt, publicId)` 위치를 표현합니다.
7. Board·Post·Comment의 모든 `GET`은 로그인 없이 공개합니다. 게시글·댓글 작성, 수정과 삭제는
   `SESSION_FULL`과 `PASSWORD_CHANGE_NOT_REQUIRED`를 충족하는 인증 계정만 수행합니다.
8. 변경 use case는 module 내부 `CommunityActionPolicy`에 actor, action과 resource 사실을
   전달합니다. 현재 정책은 작성자만 수정·삭제하며 `ROLE_ADMIN`이 이를 우회하지 않습니다.
   후속 moderator action은 작성자 삭제와 다른 action으로 추가합니다.
9. 작성자 표시 이름은 community가 `account` table을 직접 조회하거나 생성 시점 문자열로
   복제하지 않고 `account.api`의 batch 공개 profile 조회 계약으로 가져옵니다.
10. 목록 조회는 slice 하나의 작성자들을 한 번에 조회해 account profile N+1 호출을 만들지
    않습니다. 변경 transaction에서 DB lock을 유지한 채 account 조회를 호출하지 않습니다.
11. Post와 Comment는 분리된 변경 모델이며 조회 응답은 persistence projection으로 만듭니다.
    검색·인기·추천 feed가 생겨도 해당 read model을 변경 모델에 넣지 않습니다.
12. 작성자 삭제와 운영자 숨김·잠금은 다른 원인과 감사 책임을 가지므로 하나의 status 값이나
    관리자용 DELETE로 합치지 않습니다.
13. 차량·tag·media·reaction 같은 후속 관계는 안정된 board/post/comment 공개 identity를
    참조하는 association 또는 별도 aggregate로 추가합니다. 미사용 nullable field, JSONB
    확장 bag과 FK 없는 polymorphic target을 이번 schema에 넣지 않습니다.
14. 작성자가 Post를 삭제하면 row·제목·본문·작성자 reference를 DB에 보존하되 목록에서 제외하고
    상세와 모든 하위 Comment endpoint를 `404`로 숨깁니다. 작성자가 active Post의 Comment를
    삭제하면 row·본문·작성자 reference를 DB에 보존하되 목록에는 `삭제된 댓글입니다.`
    placeholder만 노출합니다. 두 resource 모두 작성자 삭제 상태와 삭제 시각을 기록하고
    moderation 상태와 합치지 않습니다.

## 수용 기준

- 미인증 사용자를 포함한 누구나 `GET /api/v1/boards`와 `GET /api/v1/boards/{boardId}`에서
  자유게시판을 발견하고, 응답의 board public id로 게시글 collection에 접근할 수 있습니다.
- Board collection은 seed의 `displayOrder ASC, publicId ASC` 순서를 재현하며 DB의 미지정
  반환 순서에 의존하지 않습니다.
- 유효한 인증 계정은 `POST /api/v1/boards/{boardId}/posts`로 게시글을 생성하고
  `201 Created`, 생성 리소스 `Location`과 post public id를 받습니다.
- 미인증 사용자를 포함한 누구나 `GET /api/v1/boards/{boardId}/posts`와
  `GET /api/v1/posts/{postId}`에서 작성자 표시 이름과 안정된 cursor slice 결과를 봅니다.
- 게시글 작성자만 제목·본문을 수정하거나 게시글을 삭제할 수 있고, 비작성자는 `403`,
  존재하지 않거나 노출되지 않는 리소스는 `404`를 받습니다.
- 유효한 인증 계정은 조회 visibility와 별개로 새 댓글을 수용하는 active 게시글에만 댓글을
  생성하고 `201 Created`와 생성 리소스 `Location`을 받습니다.
- 댓글 목록은 작성순이며, 댓글 작성자만 자신의 댓글을 수정·삭제할 수 있습니다.
- 작성자 삭제 Post는 DB 원문과 작성자 reference를 보존하지만 목록에서 제외되고 상세·하위
  Comment endpoint에서 `404`입니다. Active Post 아래 작성자 삭제 Comment는 목록의
  `ACTIVE`/`AUTHOR_DELETED` discriminator 중 삭제 variant로 자리를 유지하며 원문과 외부
  작성자 표현을 포함하지 않습니다.
- 작성자 삭제 Post와 active Post 아래 작성자 삭제 Comment는 같은 작성자의 반복 DELETE만
  `204`이고 다른 actor는 `404`입니다. 삭제 Post 아래 Comment는 actor와 무관하게 접근할 수
  없습니다. Post·Comment FK는 `RESTRICT`이고 author delete가 하위 row를 cascade 삭제하지
  않습니다.
- ASCII·Unicode 공백만 있는 입력, `U+0000`, 고립 high·low surrogate, Unicode code point
  길이 초과와 빈 `PATCH`는 `400` 공통 validation 응답을 반환하고 상태를 변경하지 않습니다.
  CRLF·CR 정규화, 정상 emoji surrogate pair와 결합 문자 경계도 HTTP JSON·domain·PostgreSQL
  저장에서 같은 결과를 냅니다.
- 동시에 같은 콘텐츠를 수정해 낙관적 잠금이 충돌하면 한 요청만 성공하고 다른 요청은
  `409 Conflict`를 받습니다.
- 같은 게시글의 댓글 생성끼리는 불필요하게 직렬화하지 않으면서, 댓글 생성과 게시글 삭제가
  경합할 때 삭제가 먼저 완료된 Post에는 새 Comment를 insert하지 않습니다. Comment가 먼저
  commit되면 해당 Comment를 보존하며, 뒤이어 Post가 삭제되면 parent visibility로 함께
  숨깁니다. Comment 수정·삭제도 active parent에 같은 `FOR KEY SHARE` lock을 사용해 Post
  삭제와 commit 순서를 고정하되 일반 Post PATCH와는 병렬로 진행합니다.
- post cursor slice 기본 20·최대 100, comment cursor slice 기본 50·최대 100을 지키고
  무제한 조회와 별도 total count query를 만들지 않습니다.
- 변경이 없는 dataset에서 cursor를 이어 읽을 때 중복·누락 없이 고정된 정렬 방향을 유지하고,
  동시 추가는 이미 지난 경계 앞에 끼워 넣지 않습니다. 동시 삭제 Post는 visibility에서
  제외되고, 동시 삭제 Comment는 같은 위치의 placeholder로 남습니다. Malformed cursor는
  `400`을 반환합니다.
- community code는 `account.api`만 참조하고 `account.internal`·`account` table·JPA Entity를
  참조하지 않습니다.
- 게시글 schema에는 `board_id NOT NULL` FK가 있고, 초기 게시판 수가 바뀌어도 기존 post의
  category backfill이나 identity 교체가 필요하지 않습니다.
- 차량, reply, reaction, bookmark, report, media, search와 notification 전용 table·field·
  event/outbox가 최초 migration과 API 명세에 존재하지 않습니다.
- focused test, PostgreSQL migration·persistence 통합 test, Security·OpenAPI·architecture
  회귀 test와 최종 `./gradlew check`가 통과합니다.

## 확정 제품 결정

### 결정 1. Board·Post·Comment 조회는 공개합니다

- **사용자 결정**: 로그인하지 않아도 글을 읽을 수 있어야 합니다.
- **확정 계약**: Board 목록·상세, Post 목록·상세와 Comment 목록 `GET`은 anonymous 요청에
  공개합니다. 생성·수정·삭제는 `SESSION_FULL`, `PASSWORD_CHANGE_NOT_REQUIRED`를 충족하는
  인증 계정만 수행합니다.
- **영향·재검토**: Security와 OpenAPI는 동일한 public operation source를 사용합니다.
  표시 이름과 콘텐츠가 인터넷에 공개되므로 production 공개 전 moderation·rate-limit을 별도
  작업으로 확정해야 하지만, 이는 현재 조회 계약을 다시 미결정으로 만들지 않습니다.

### 결정 2. 자유게시판 하나로 시작합니다

- **사용자 결정**: 우선 자유게시판 하나만 만듭니다.
- **확정 계약**: migration은
  `54be05ef-b451-4865-b124-b7633d04511e`/`free`/`자유게시판`/`100` 한 row만 seed합니다.
  모든 Post는 이 Board를 포함해 `community_board` 한 row를 `board_id NOT NULL`로 참조합니다.
- **영향·재검토**: 게시판 추가는 새 Board row와 navigation을 더하는 additive 변경이며 기존
  Post backfill이 필요하지 않습니다. Board 관리·비활성화·이동은 실제 요구가 생길 때 별도
  action과 권한으로 추가합니다.

### 결정 3. 삭제 원문은 보존하고 Post와 Comment의 노출만 다르게 처리합니다

- **사용자 결정**: DB에는 데이터를 보존하고, 삭제 게시글은 보이지 않게 하며, 삭제 댓글은
  삭제됐다는 사실을 나타냅니다.
- **확정 Post 계약**: 작성자 DELETE는 `lifecycle_state=AUTHOR_DELETED`와 `deleted_at`을
  기록하되 row·title·body·author reference를 보존합니다. Post 목록에서 제외하고 상세 및 모든
  하위 Comment endpoint는 `404`입니다. 같은 작성자의 반복 DELETE는 `204`, 다른 actor는
  `404`입니다.
- **확정 Comment 계약**: Active Post 아래 작성자 DELETE는 row·body·author reference를
  보존하고 `AUTHOR_DELETED`와 `deleted_at`을 기록합니다. Comment 목록에는
  `state="AUTHOR_DELETED"`, `placeholder="삭제된 댓글입니다."`, `deletedAt`만 있는 variant를
  반환하며 body와 author field는 생략합니다. PATCH는 `404`, 같은 작성자의 반복 DELETE는
  `204`, 다른 actor는 `404`입니다.
- **하위 생명주기**: 삭제 Post의 Comment row는 변경하지 않지만 parent가 비노출이므로 모든
  하위 endpoint에서 접근할 수 없습니다. Comment FK는 `RESTRICT`이고 cascade 삭제하지 않습니다.
- **영향·재검토**: 원문과 작성자 reference 보존은 복구·감사 가능성을 남기지만 privacy·보존
  정책이 아직 없다는 위험도 남깁니다. Production 공개나 account 탈퇴·익명화 도입 전 별도
  privacy 작업에서 보존 기간·redaction·관리자 접근을 결정합니다. 운영자 hide·restore는
  작성자 삭제 상태와 합치지 않습니다.

## Agent 기술 결정 색인

[016 확장 가능한 일반 커뮤니티 상세 설계](016-general-community-design.md)는 다음 기술
결정을 보존합니다.

- `community` 독립 module과 `account.api` 단방향 협력
- 초기부터 존재하는 경량 Board와 `Board -> Post -> Comment` 필수 관계
- account public id 저장과 표시 이름 batch 조회
- Post·Comment 분리 aggregate와 generic content table 배제
- action authorization과 조회 visibility의 분리
- resource 중심 REST API와 `(createdAt, publicId)` cursor slice
- plain text의 Unicode scalar repertoire·정규화·blank·code point 길이 이중 검증
- 낙관적 수정과 `FOR KEY SHARE` comment-write/`FOR UPDATE` post-delete lock 조합
- 변경 모델과 조회 projection의 분리, 첫 durable consumer 전 event/outbox 도입 유예
- additive migration과 exact rollback image 자격·forward recovery 원칙
- 미래 기능별 association·read model·module 확장 경계

## 구현 진입 gate

이 계획에서는 **상세 설계가 독립 검증을 통과하기 전에 구현을 시작하지 않습니다.** 구현은
source code, test code, 설정, Flyway migration, module·package 껍데기, public API type,
adapter와 exploratory spike를 모두 포함합니다. Gate 전에는 read-only source 조사와 이
work item·상세 설계 Markdown 변경만 허용합니다.

구현 진입에는 다음 조건이 모두 필요합니다.

1. 사용자 제품 결정이 조건부 문구 없이 exact 값으로 고정돼 있습니다.
2. endpoint·request·response·오류·Security·cache 계약이 고정돼 있습니다.
3. domain 상태·action·visibility·commentability·삭제 lifecycle이 고정돼 있습니다.
4. table·column·type·nullability·default·FK·check·index·seed·migration·복구 계약이 고정돼
   있습니다.
5. transaction·lock·낙관적 충돌·반복 요청 결과가 고정돼 있습니다.
6. module·package·의존 방향과 account 협력 계약 및 최초 소비 시점이 고정돼 있습니다.
7. 입력 정규화·개인정보·logging·크기 제한이 고정돼 있습니다.
8. test·quality gate·영구 SOT 승격·rollout·recovery가 고정돼 있습니다.
9. 미래 기능의 additive 연결점과 재검토 신호가 현재 미구현 범위와 구분돼 있습니다.
10. `$verify-plan` 독립 감사에 필수 결함이 없고 문서가 `Planned` checkpoint로 commit돼
    있습니다.

구현 중 결과를 바꾸는 미결정이 발견되면 새 구현 mutation을 중단하고 영향을 받는 설계를
`Proposed`로 되돌려 수정·재검증합니다. 새 `Planned` checkpoint 전에는 구현을 재개하지
않습니다. `community` module 껍데기와 `account.api` batch 계약도 구현이므로 이 gate보다
먼저 만들 수 없습니다.

## 의미 단위 계획

### 작업 1. 제품·API·domain·schema 상세 설계를 함께 동결합니다

작업 목표: 사용자 댓글 세 개를 공개 조회, 자유게시판 seed와 Post/Comment 혼합 삭제
lifecycle에 exact 값으로 반영하고, 모든 구현 계약을 독립 감사 가능한 한 후보로 동결합니다.

작업 이유: module 구조만 먼저 만들면 schema와 domain 상태가 뒤늦게 그 구조를 밀어내고,
schema만 먼저 만들면 API visibility와 삭제 응답이 persistence에 맞춰 임의 결정될 수 있습니다.
구현 전에 전체 계약의 양방향 추적성이 닫혀야 합니다.

선행 조건: 사용자 댓글 세 개와 현재 authoritative source입니다. 구현 artifact는 선행 조건이
아닙니다.

수행 내용:

- 이 문서와 상세 설계에서 선택지·조건부 문구를 제거하고 한 계약만 남깁니다.
- API, domain, persistence, 동시성, module 경계, 보안·privacy, test, rollout·recovery와 미래
  additive 경로를 서로 대조합니다.
- `$verify-plan` fresh reviewer가 사용자 목적과 source부터 독립 감사합니다.
- 필수 결함을 닫은 뒤 `Planned` checkpoint만 local commit합니다.

검증과 완료 결과: 구현자가 제품·API·schema·module 순서를 새로 판단할 필요가 없고, 위 구현
진입 gate의 열 가지 조건이 모두 증거로 닫힙니다. 이 작업에서는 코드·test·migration·module
변경이 없습니다.

### 구현 입장 전이. 첫 mutation 전에 실행 상태를 동기화합니다

Hard edge는 `작업 1의 committed Planned checkpoint + 현재 generation의 명시 구현 요청 →
work item·year router In Progress 동기화 → 작업 2 첫 code/test/migration mutation`입니다.

- 새 구현 요청이 없으면 `Planned`에서 멈추고 작업 2를 시작하지 않습니다.
- 구현 요청이 확인되면 `$documentation`이 work item을 상단 `결과`, 완료·미완료 범위, 현재
  Agent 책임과 다음 결과를 가진 `In Progress` Status 구조로 전환하고 year router를 같은
  상태·요약으로 갱신합니다. 사용자 확정 결정과 design link는 보존합니다.
- 계획 전문은 In Progress 본문에 실행 일지로 남기지 않습니다. Committed Planned checkpoint와
  companion design을 구현 계약으로 사용하고, 현재 Status에는 다음 concrete 결과와 완료
  조건만 투영합니다.
- 이 상태 변경의 source·owner·router·link를 검증한 뒤에만 작업 2 writer와 첫 구현 mutation을
  시작합니다. Stage·commit 효과는 현재 원문의 별도 권한이 있을 때만 exact status path로
  Git phase에 넘깁니다.

### 작업 2. 첫 구현으로 community core와 exact schema를 함께 구축합니다

작업 목표: 빈 module 골격이 아니라 Board·Post·Comment 변경 core, action policy와 실제
PostgreSQL schema를 하나의 검증 가능한 기반으로 만듭니다.

작업 이유: domain state와 DB constraint는 같은 삭제·무결성 계약을 양쪽에서 보호합니다.
둘 중 하나를 먼저 완성된 기반처럼 만들면 다른 쪽에서 구조 재작업이 생깁니다.

선행 조건: 작업 1의 `Planned` checkpoint와 구현 진입 gate 통과가 필요합니다.

수행 내용:

- 실패하는 domain·PostgreSQL test로 Board reference, Post·Comment 상태 전이,
  `CommunityActionPolicy`, Unicode 계약과 optimistic conflict를 먼저 고정합니다.
- 위 core를 담는 최소 `community` module/package를 실제 domain·application·persistence
  구현과 함께 추가하고 `allowedDependencies = {"account :: api", "shared :: error"}`를
  검증합니다. 빈 확장 interface와 `community.api`는 만들지 않습니다.
- 현재 branch의 V12 다음인 `V13__create_community_core.sql` Flyway migration에
  `community_board`, `community_post`, `community_comment`, exact
  constraint·index·`RESTRICT` FK와 자유게시판 literal seed를 추가합니다.
- `author_account_public_id`는 account public UUID reference로 저장하되 schema/domain은
  표시 이름 lookup을 소비하지 않습니다. Cross-module FK·join도 만들지 않습니다.
- PostgreSQL Testcontainers로 migration, seed, FK, 상태/content check, cursor index와 lock
  전제를 검증합니다. Task 6이 V13 source와 검증 결과를 hash로 동결한 뒤 Task 7이 그 exact
  migration을 기준으로 `docs/project/schema/110-community.md`와 router를 작성합니다.

검증과 완료 결과: domain과 DB가 동일한 identity·상태·삭제·Unicode 불변식을 지키며,
account batch API 없이도 변경 core와 schema가 독립적으로 통과합니다.

복구: 적용된 migration은 수정하지 않습니다. Exact compatibility image가 검증되지 않으면
raw 이전 image로 rollback하지 않고 current schema 위 forward fix를 사용합니다. Table 제거는
보존 결정을 거친 별도 forward migration으로만 수행합니다.

### 작업 3. 최초 조회 consumer 직전에 독립된 작성자 공개 batch 계약을 추가합니다

작업 목표: Board/Post 조회가 account 내부 구현을 보지 않고 현재 표시 이름을 한 번에 조립할
수 있는 최소 producer 계약을 만듭니다.

작업 이유: 이 계약의 최초 consumer는 schema나 domain core가 아니라 Post/Comment 조회
projection입니다. 작업 2와 기술적으로 독립이지만 unused producer의 체류 시간을 줄이기 위해
실행 배치만 작업 2 뒤, 실제 consumer 직전으로 권장합니다.

선행 조건: 기술적 Hard dependency는 작업 1의 `Planned` checkpoint뿐입니다. 공통 구현 입장
전이의 `In Progress` gate는 모든 구현 task에 적용됩니다. 작업 2 결과는 이 계약의 입력이
아니며 작업 4가 시작되기 전에 완료하면 됩니다.

수행 내용:

- `account.api`에 public id 집합을 `AccountPublicProfile(accountPublicId, displayName)`으로
  batch 변환하는 named interface를 실패 test부터 추가합니다.
- account internal adapter는 account/profile source를 한 query로 읽고 입력 public id별 현재
  공개 표현을 반환합니다. 찾지 못한 id는 결과 Map에 넣지 않습니다.
- account public 계약의 단위·PostgreSQL persistence test와 named interface visibility를
  검증합니다. Community port·adapter와 누락 결과의 application 오류 변환은 최초 consumer인
  작업 4에서 추가합니다.

검증과 완료 결과: account owner의 표시 이름 batch lookup이 독립적으로 통과하며, 이 계약이
작업 2의 schema 선행 조건이 아니라 작업 4 조회 consumer의 producer임이 드러납니다.

### 작업 4. Board와 게시글 API를 첫 세로 단위로 완성합니다

작업 목표: 공개 게시판 발견부터 인증 게시글 생성, 공개 cursor 목록·상세와 작성자
수정·삭제까지 첫 end-to-end vertical slice를 완성합니다.

작업 이유: 작업 2의 core·schema와 작업 3의 작성자 표현 계약을 실제 사용자 경로에서 처음
결합해야 다음 Comment slice가 검증된 기반을 소비합니다.

선행 조건: 작업 2의 `community` core·schema와 작업 3의 account batch 계약이 모두 필요합니다.
두 독립 결과가 처음 합류하는 단계입니다.

수행 내용:

- domain/application, Controller, persistence query 실패 test 순서로 Red를 확인한 뒤 최소
  구현과 refactor를 반복합니다.
- Board 목록·상세와 board-scoped Post command/query, 변경 use case와 조회 projection을
  분리해 구현합니다.
- Community의 `AuthorProfileLookupPort`와 `account.api` adapter를 추가하고, account가 반환하지
  않은 public id를 `COMMUNITY_AUTHOR_PROFILE_UNAVAILABLE`로 변환합니다.
- Spring Modulith·ArchUnit으로 `community -> account :: api` 단방향 의존과 internal/table
  접근 금지를 검증합니다.
- Post slice의 author public id를 모아 account batch API를 정확히 한 번 호출합니다.
- `(createdAt DESC, publicId DESC)` keyset cursor와 Post `ACTIVE` partial index를 사용합니다.
- 공개 GET, 보호 write, `201`·`Location`, `204`, validation·404·403·409, deleted Post 완전
  비노출과 `Cache-Control: no-store`를 Web·Security·OpenAPI에 고정합니다.

검증과 완료 결과: anonymous Board/Post 조회, 인증 생성·작성자 변경, cursor 연속성과 삭제
Post 비노출이 Web과 PostgreSQL 통합 test에서 관찰됩니다.

복구: endpoint를 닫는 forward fix는 새 table을 보존합니다. Image rollback은 작업 2의 exact
후보가 현재 Flyway history와 호환된다고 입증된 경우에만 사용합니다.

### 작업 5. 댓글 API를 게시글 계약 위에 완성합니다

작업 목표: Active Post에 대한 공개 댓글 목록, 인증 생성과 작성자 수정·삭제를 구현하고 삭제
Comment placeholder와 삭제 Post 하위 비노출을 고정합니다.

작업 이유: Comment는 Post의 commentability와 visibility에 의존하지만 동일 Post의 독립
생성끼리는 불필요하게 직렬화하지 않아야 합니다.

선행 조건: 작업 4의 게시글 lifecycle과 공개 detail 경로가 통과해야 합니다.

수행 내용:

- Comment 작성순 cursor, 생성·수정·삭제와 active/deleted response variant의 실패 test를
  먼저 작성합니다.
- Comment 변경 모델과 read projection을 분리하고 active 작성자만 batch 표시 이름을
  조립합니다.
- Comment 생성·수정·삭제는 PostgreSQL native parent lookup의 `FOR KEY SHARE`, Post 삭제는
  `FOR UPDATE`를 사용합니다. Comment write끼리와 일반 Post PATCH의 `FOR NO KEY UPDATE`는
  병렬로 진행하고 comment write-post delete만 직렬화합니다.
- 삭제 Comment는 같은 cursor 위치에서 placeholder로 반환하고 body·author를 생략합니다.
  삭제 Comment PATCH와 삭제 Post 아래 모든 Comment endpoint는 `404`로 고정합니다.
- Comment index는 active와 author-deleted row를 모두 조회하므로 partial predicate를 두지
  않습니다.

검증과 완료 결과: 공개 댓글 조회, 소유권, placeholder, parent visibility, cursor와 동시
comment write/Post delete terminal state가 모순 없이 동작하고 Comment write와 Post PATCH는
불필요하게 서로 대기하지 않습니다.

복구: Comment endpoint를 닫아도 Post API와 보존된 schema는 독립적으로 유지됩니다.

### 작업 6. 개발 통합·품질 후보를 동결합니다

작업 목표: 현재 최소 기능을 기존 Security, 오류와 OpenAPI에 통합하고, 미래 기능을 구현하지
않으면서 상세 설계의 확장 불변식을 실제 source와 test로 보호한 개발 후보를 만듭니다.

작업 이유: endpoint 구현만 통과하면 Board 우회, 내부 table 참조, 변경 모델에 조회 관심사
혼입, 관리자 DELETE 우회와 미래용 dead structure를 놓칠 수 있습니다. 반대로 중대한 Markdown
신설·router 변경을 개발 부속 작업으로 합치면 문서 owner와 검증 결과가 사라집니다.

선행 조건: 작업 4와 작업 5의 개발 후보가 동결돼야 합니다. 문서 결과는 이 작업의 write set이
아닙니다.

수행 내용:

- Security matcher와 OpenAPI public/protected 분류를 같은 source에서 검증합니다.
- request body·제목·댓글 원문과 JWT는 log·Problem Details에 포함하지 않습니다.
- `Post.boardId` 필수, Board 밖 Post 생성 금지, action policy 단일 진입과 read/write model
  분리를 architecture·application test로 검증합니다.
- notification/search/moderation/media가 community internal·table을 직접 소비할 수 없도록
  module boundary를 검증하되, 실제 consumer가 없는 `community.api`와 event/outbox는 만들지
  않습니다.
- OpenAPI 전체 path·response·cursor schema·security 회귀 test와 Spring Modulith·ArchUnit을
  갱신합니다.
- Docker daemon preflight 뒤 재사용 없는 PostgreSQL Testcontainers를 포함해
  `./gradlew check`를 실행하고 `$verify-dev`가 최초 목적부터 frozen 개발 후보를 독립
  검증합니다.

검증과 완료 결과: code·test·설정·migration·OpenAPI라는 개발 결과만의 frozen snapshot에서
전체 local quality gate와 `$verify-dev`가 통과합니다. 이 snapshot의 exact source path와
내용 hash, V13 schema, module, Security allow-list, endpoint와 test 결과를 작업 7의 producer
입력으로 인계합니다.

복구: exact rollback image의 migration 호환성이 검증된 경우에만 새 API를 image rollback으로
제거합니다. 그렇지 않으면 endpoint를 닫는 forward fix를 사용합니다. Production 공개 rollout은
moderation·rate-limit owner가 확정될 때까지 별도 작업으로 유지합니다.

### 작업 7. 검증된 구현 source에서 문서·SOT 결과를 독립 작성합니다

작업 목표: 주장 종류별 authoritative input을 분리해 새 community domain/schema owner와
기존 현재 상태 문서·router·README를 `$documentation` workflow로 동기화합니다.

작업 이유: 신규 canonical owner와 여러 router의 topology 변경은 구현 설명을 덧붙이는 부속
작업이 아니라 독립 owner·write set·검증 oracle을 가진 중대한 Markdown 결과입니다.

선행 조건: 다음 네 입력이 모두 필요합니다.

- 공개 조회·자유게시판·혼합 삭제와 제외 범위에는 사용자 확정 결정과 `Planned` design 계약
- 기존 문서 책임·현재 프로젝트 사실에는 변경 전 current SOT
- 실제 구현·schema·API·Security·test 사실에는 작업 6의 `$verify-dev` 통과 source·내용 hash
- work item 상태·남은 production·remote 책임에는 공통 completion map

미구현 계획 문구나 미래 목표를 repository 현재 사실의 source로 사용하지 않고, 구현 hash를
제품 결정이나 작업 상태의 권위로 확대하지 않습니다.

수행 내용:

- 아래 `영구 SOT 승격`의 exact path만 source→owner 책임에 따라 새로 작성하거나 갱신합니다.
- `docs/domain/130-community.md`와 `docs/project/schema/110-community.md`는 각각 업무 정책과
  V13 적용 뒤 물리 schema만 소유하고 서로의 사실을 복제하지 않습니다.
- Domain·schema·project router와 README에서 독자 질문→owner link를 연결하고 모든 local
  target·번호·표시 이름을 검사합니다.
- 작성자가 actual migration·package·Security allow-list·OpenAPI·test와 두 번째 source-first
  대조를 마친 뒤, fresh `$verify-documentation final`이 frozen Markdown 후보를 독립 감사합니다.
- 이미 `In Progress`인 work item과 year router에 공통 completion map의 local 구현·SOT 결과를
  갱신하고, 아직 확인하지 않은 branch push·PR·main 통합·CI·Sonar 책임을 분리합니다. 이 단계가
  최초 `In Progress` 전환은 아닙니다.

검증과 완료 결과: 구현 source→exact canonical owner와 router/README 독자→owner를 양방향으로
복원할 수 있고, `$verify-documentation final`에 필수 결함이 없습니다. 이 작업은 구현 source를
수정하지 않습니다.

복구: 문서가 source와 어긋나면 구현을 우회하거나 source를 문서에 맞추지 않고 문서 후보를
다시 열어 actual source에 맞춰 수정·재감사합니다.

### 작업 8. 검증된 개발·문서 결과를 Git·remote gate에 인계합니다

작업 목표: 서로 독립 검증된 작업 6의 개발 snapshot과 작업 7의 `In Progress` Markdown
snapshot만을 한 후보로 결합해 drift·누락 없이 Git과 remote gate 증거를 만듭니다.

작업 이유: 문서 작성 중 구현 source가 바뀌거나 개발 보완 뒤 문서가 그대로면 두 개의 통과
결론이 같은 최종 후보를 보장하지 않습니다.

선행 조건: 작업 6 `$verify-dev`와 작업 7 `$verify-documentation final`이 각각 통과해야 합니다.

수행 내용:

- 작업 7이 소비한 개발 source hash와 최종 개발 후보가 같은지 확인합니다. Drift가 있으면
  영향받은 개발 검증과 문서 source 대조를 다시 엽니다.
- 전체 diff에서 계획한 code·test·migration·문서 path만 존재하고 기존 dirty 변경이 섞이지
  않았는지 확인합니다.
- 동일한 개발 snapshot의 `./gradlew check` 영수증, 문서 link·router 감사와 production 공개
  보류 조건을 최종 완료 map에 결속합니다.
- 현재 구현 요청 원문에서 stage·commit·push 권한을 각각 다시 확인하고 첫 Git mutation 전에
  결과 단위별 exact path와 예정 commit 순서를 투영합니다.
- 권한이 있는 효과만 `$git-finalize`로 실행합니다. Feature branch push는 exact remote ref
  생성·갱신 결과일 뿐 CI·Sonar terminal evidence가 아닙니다.
- Same-repository PR 생성, review 뒤 `main` merge는 branch push와 다른 외부 효과이므로 각각
  현재 원문의 positive 권한을 확인합니다. PR 권한이 있으면 PR event의 CI·Sonar를 후보
  검증으로 확인하지만 이를 전체 완료 gate로 확대하지 않습니다.
- `main` 통합 직전 최신 main과 feature candidate의 source drift, V13 migration 번호 충돌과
  merge conflict를 다시 확인합니다. Schema·API·Security·문서 계약을 바꾸는 drift가 있으면
  관련 `$verify-dev`·문서 source 대조를 무효화하고 보완한 새 후보부터 다시 검증합니다.
- 별도 merge 권한으로 PR이 main에 통합된 뒤에만 **merge된 exact main SHA**의 main-push
  required CI를 확인하고, 최신 main Sonar revision이 같은 SHA인지와 Quality Gate `OK`, overall
  open issues 0건을 확인합니다.

검증과 완료 결과: 개발·문서 양쪽 검증이 동일 source를 가리키고 feature ref, PR, main merge와
main SHA terminal gate의 실제 성공·실패·미실행 상태가 공통 completion map에 분리됩니다.
PR·main merge 권한이 없거나 main SHA gate가 닫히지 않으면 `In Progress`와 exact 다음 owner를
유지하며, 이 단계는 `Done`을 미리 기록하지 않습니다.

복구: 합류 뒤 drift나 누락이 발견되면 통과 영수증을 재사용하지 않고 영향받은 독립 결과부터
다시 검증합니다. Git history rewrite나 production rollout은 별도 권한 없이는 수행하지 않습니다.

### 작업 9. Terminal 결과를 work item·router 상태에 최종 투영합니다

작업 목표: 작업 8의 실제 Git·remote 결과를 공통 완료 판정에 넣어 work item, companion design과
year router를 `In Progress` 또는 `Done`의 정확한 현재 구조로 바꾸고 별도 검증·Git phase로
기록합니다.

작업 이유: Remote gate 전에 `Done`을 쓰면 미검증을 숨기고, gate가 끝난 뒤에도 계획 구조를
남기면 실제 완료 상태를 숨깁니다. 상태 문서 commit 자체가 final ref를 바꾸므로 그 뒤 gate도
다시 확인해야 합니다.

선행 조건: 작업 8의 local commit, feature push, PR, main merge 수행 여부와 merge된 exact main
SHA의 CI·Sonar 결과가 실제 source로 확정돼야 합니다.

수행 내용:

- Local-only·feature push·PR-only처럼 main merge 전인 경로는 해당 local/feature source의
  본문과 router를 `In Progress`로 유지하고, main은 미통합·불변으로 기록합니다. 상단 `결과`에는
  local 완료 범위·미완료 main 통합 책임·다음 owner와 재개 조건을 투영합니다.
- Main merge는 완료됐지만 merge된 main SHA의 required CI·Sonar가 미확정·실패인 경로는 main에
  통합된 본문과 router를 `In Progress`로 유지하고 exact terminal failure·다음 owner를
  투영합니다.
- Merge된 exact main SHA의 required CI 성공, 최신 main Sonar revision 일치, Quality Gate
  `OK`, overall open issues 0건과 현재 work item 범위의 모든 terminal 책임이 닫혔을 때만 공통
  완료 판정이 `Done` 후보를 허용합니다. 본문은 Result 구조로 압축하고 companion design에는
  장기 결정만 남깁니다.
- `$documentation`이 상태·본문 구조·SOT link·router 요약을 다시 대조하고, 중대한 Done 전환은
  fresh `$verify-documentation final`로 감사합니다.
- 상태 projection은 merge된 최신 main에서 시작하는 별도 status-only branch/commit/PR/main
  merge 효과입니다. 새 branch 생성, stage, commit, push, PR과 main merge 권한을 각각
  재확인하고 exact status path만 별도 Git phase로 기록합니다. 어느 권한도 앞 단계에서
  승계하지 않습니다.
- Status-only PR의 main merge 직전에 현재 main의 community source·SOT·V13과 작업 8 terminal
  source/hash·completion map을 다시 대조합니다. 관련 drift가 있으면 기존 Done 자격과
  `$verify-documentation final`을 무효화하고 최신 main에서 status candidate를 다시 작성·검증한
  뒤에만 별도 권한으로 merge합니다.
- 상태 PR이 main에 merge되면 **새 exact main SHA**의 main-push required CI와 최신 main Sonar를
  다시 확인합니다. Feature branch나 PR head SHA의 gate를 terminal evidence로 사용하지 않습니다.
- 상태 merge의 main SHA gate가 실패하거나 미확정이면 `Done`을 완료로 보고하지 않습니다.
  공통 completion map을 `In Progress`로 되돌려 상태 문서를 다시 검증하고, 권한이 있는 별도
  status correction branch/PR/main merge로 교정합니다.

검증과 완료 결과: Local-only·feature-only·PR-only 경로는 `In Progress`와 exact 다음 owner를,
상태 projection까지 main에 통합되고 그 새 main SHA terminal gate까지 닫힌 경로만 `Done`과
남은 책임 없음을 표시합니다. 채팅 보고는 최종 main 문서·Git·remote actual source와 같은
판정을 전달합니다.

복구: 잘못된 상태 projection은 history rewrite로 숨기지 않고 actual terminal 결과에 맞는
현재 상태로 교정합니다. 교정 commit과 push에도 각각 별도 권한과 final gate를 적용합니다.

## 검증·전환·복구

- 구현은 각 의미 단위에서 Red → Green → Refactor를 적용하고 실패 이유를 확인합니다.
- Domain/Application은 Spring 없는 단위 test, Web은 MVC slice, query·constraint·migration은
  PostgreSQL Testcontainers 통합 test를 사용합니다.
- Security는 공개 GET과 보호 write별 미인증·정상 인증·비작성자 거부를 분리해 검증합니다.
- Cursor test는 첫 page, 다음 page, 동률 timestamp, malformed cursor와 현재 collection scope가
  다른 token을 포함합니다. 동시 추가는 이미 받은 경계 앞에 다시 나타나지 않습니다. 동시
  삭제 Post는 visibility에서 제외되고 동시 삭제 Comment는 같은 위치의 placeholder로 남는
  non-snapshot continuation 의미도 검증합니다.
- 기존 account/auth·notification API와 migration V1~V12 회귀를 `./gradlew check`로 함께
  확인합니다.
- schema 변경은 새 forward migration만 사용하며 이미 적용된 migration을 수정하거나 자동
  down migration을 만들지 않습니다.
- 새 API와 table은 additive이지만 raw 이전 application image가 Flyway rollback 후보라는 뜻은
  아닙니다. Rollback은 새 migration이 적용된 DB에서 migration process·history·checksum
  호환성을 입증한 exact image로만 수행하고, 후보가 없으면 forward fix로 복구합니다. 새 table
  삭제는 데이터 보존과 재배포 가능성을 확인한 별도 작업에서만 수행합니다.
- 구현 완료 판정은 local test나 feature branch push로 닫지 않습니다. PR·main merge 권한이
  있을 때 main에 통합된 exact SHA의 main-push required CI와 최신 main Sonar gate까지
  확인합니다.
- 개발 후보의 `$verify-dev` 통과 뒤에만 actual source를 문서 작업으로 인계하고, 중대한
  Markdown 후보는 `$documentation` 작성자 대조와 `$verify-documentation final`을 별도로
  통과합니다.
- 최종 합류에서는 문서가 소비한 개발 source hash와 최종 개발 후보를 대조합니다. 어느 쪽이든
  drift가 있으면 영향받은 독립 검증을 다시 실행합니다.
- Git·remote 결과 뒤 work item과 router를 현재 completion map에 맞춰 다시 투영하고, 상태
  문서 변경을 별도 documentation 검증과 Git phase로 닫습니다. 상태 commit이 final SHA를
  바꾸면 별도 PR/main merge 뒤 새 main SHA의 remote gate를 다시 확인합니다.

## 영구 SOT 승격

구현과 검증이 완료될 때만 다음 exact owner를 실제 source에 맞춰 승격합니다.

- `docs/domain/130-community.md`: Board·Post·Comment 용어, action policy와 혼합 삭제 lifecycle
- `docs/project/schema/110-community.md`: V13 적용 뒤 community table·column·constraint·index·seed
- `docs/domain/000-index.md`, `docs/project/schema/000-index.md`: 두 새 owner를 현재 문서와 독자
  질문에 연결
- `docs/project/100-project-overview.md`: community 구현 범위와 Flyway V1~V13 현재 상태
- `docs/project/200-current-architecture.md`: 실제 최상위 `community` package와 책임 경계
- `docs/project/210-module-map.md`: `community` 책임과 `community -> account :: api` 협력
- `docs/project/000-index.md`: community module·schema owner로 가는 현재 router와 읽는 상황
- `docs/tech-stack/100-current-stack.md`: 실제 module·migration V1~V13 stack inventory
- `docs/tech-stack/220-spring-security.md`: Board·Post·Comment public GET exact allow-list와 보호 write
- `docs/domain/110-account-auth-policy.md`: 공개 조회와 보호 쓰기 접근 매트릭스
- `docs/product/200-feature-scope.md`: 실제 구현된 일반 커뮤니티 범위와 계속 제외된 차량 연결
- `README.md`: 현재 application 설명, architecture diagram과 module boundary 표
- 이 work item과 `docs/work-items/features/2026/000-index.md`: 구현 결과·검증·남은 production
  책임과 최종 상태

각 owner는 migration·package·Security allow-list·OpenAPI·test라는 actual source에서 정방향으로
추적하고, router와 README의 독자 질문에서 owner로 역방향 탐색되는지 link·내용 감사를 합니다.

계획 단계에서는 위 문서를 미래 사실로 미리 바꾸지 않습니다.

## 관련 문서

- **상세 기술 결정과 확장 지도**:
  [016 확장 가능한 일반 커뮤니티 상세 설계](016-general-community-design.md)
- **제품 목표·기능 영역**: [100 제품 개요](../../../product/100-product-overview.md),
  [200 기능 범위](../../../product/200-feature-scope.md)
- **현재 구현 구조**: [100 프로젝트 개요](../../../project/100-project-overview.md),
  [210 모듈 지도](../../../project/210-module-map.md)
- **현재 account 공개 정보·접근 정책**: [100 Account 도메인](../../../domain/100-account.md),
  [110 Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)
- **구조·API 기준**: [210 Spring Boot 모듈 구조](../../../architecture/210-spring-boot-module-structure.md),
  [230 RESTful API 설계](../../../architecture/230-rest-api-design.md)
- **관계가 미확정인 별도 제품 후보**:
  [002 AutoSquare 신차 탐색 및 커뮤니티 후보](../../backlog/2026/002-autosquare-mvp-candidate.md)
