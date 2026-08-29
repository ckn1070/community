# 확장 가능한 차량 비연결 일반 커뮤니티 기반

## 상태

Proposed

## 달성할 결과

차량 카탈로그가 없어도 회원이 게시판에서 일반 게시글과 댓글을 작성하고, 허용된 사용자가
목록·상세와 작성자 표시 이름을 볼 수 있는 첫 커뮤니티 기능을 만듭니다. 초기 화면에는
소수의 게시판과 plain text 게시글·평면 댓글만 제공하지만, 게시글은 처음부터 안정된
`Board -> Post -> Comment` 구조에 속합니다.

확장성은 미래 기능을 미리 구현하는 방식으로 확보하지 않습니다. 대신 게시판, 콘텐츠 변경,
조회, 권한, 작성자 표현과 외부 소비 경계를 분리해 대댓글·반응·북마크·검색·알림·media·
moderation·차량 연결이 생길 때 기존 게시글 식별자와 핵심 생명주기를 교체하지 않고 새 관계·
정책·read model을 추가할 수 있게 합니다.

차량·차종 연결, 차종별 게시판과 오너 인증을 임시 문자열이나 임시 테이블로 대신하지
않습니다. 이후 카탈로그 연결은 기존 게시글 식별자와 콘텐츠를 유지한 채 별도 참조를
추가하는 작업으로 제한합니다.

현재 구현 가능한 기술 구조는 정리됐지만, 사용자가 보게 될 조회 범위·게시판 형태·삭제
방식 세 가지가 남아 있어 `Proposed`입니다. 아래 결정 카드에 답하면 상세 설계의 해당 분기를
고정하고 독립 검증한 뒤 `Planned` 후보로 전환할 수 있습니다.

## 현재 구현할 핵심 범위

- 게시판 목록·상세 조회와 선택한 초기 게시판 seed
- 일반 게시글 생성, 목록, 상세, 수정, 삭제
- 게시글의 평면 댓글 생성, 목록, 수정, 삭제
- account 공개 식별자와 현재 표시 이름을 이용한 작성자 표현
- 작성자 본인만 수정·삭제하는 현재 정책과 후속 권한을 수용할 module 내부 action policy
- cursor 기반 slice 목록, 입력 검증, 공통 Problem Details 오류와 OpenAPI 계약
- 콘텐츠 변경 모델과 조회 projection의 분리
- `community` 모듈, Flyway schema와 PostgreSQL 조회 인덱스
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
   가집니다. 현재에는 조회와 seed만 구현하고 비활성화·접근 정책은 실제 관리 기능이 생길 때
   additive 확장합니다.
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
7. 게시글·댓글 작성, 수정과 삭제는 `SESSION_FULL`과
   `PASSWORD_CHANGE_NOT_REQUIRED`를 충족하는 인증 계정만 수행합니다.
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

## 수용 기준

- 허용된 사용자는 `GET /api/v1/boards`와 `GET /api/v1/boards/{boardId}`에서 현재 게시판을
  발견하고, 응답의 board public id로 게시글 collection에 접근할 수 있습니다.
- Board collection은 seed의 `displayOrder ASC, publicId ASC` 순서를 재현하며 DB의 미지정
  반환 순서에 의존하지 않습니다.
- 유효한 인증 계정은 `POST /api/v1/boards/{boardId}/posts`로 게시글을 생성하고
  `201 Created`, 생성 리소스 `Location`과 post public id를 받습니다.
- 선택한 조회 정책에 따라 허용된 사용자는 `GET /api/v1/boards/{boardId}/posts`와
  `GET /api/v1/posts/{postId}`에서 작성자 표시 이름과 안정된 cursor slice 결과를 봅니다.
- 게시글 작성자만 제목·본문을 수정하거나 게시글을 삭제할 수 있고, 비작성자는 `403`,
  존재하지 않거나 노출되지 않는 리소스는 `404`를 받습니다.
- 유효한 인증 계정은 조회 visibility와 별개로 새 댓글을 수용하는 active 게시글에만 댓글을
  생성하고 `201 Created`와 생성 리소스 `Location`을 받습니다.
- 댓글 목록은 작성순이며, 댓글 작성자만 자신의 댓글을 수정·삭제할 수 있습니다.
- 선택한 삭제 방식의 Post·Comment 목록·상세·변경·반복 DELETE와 FK 효과가 결정 카드의 한
  행대로 동작합니다. 자리 유지 응답은 `ACTIVE`/`AUTHOR_DELETED` discriminator variant를
  사용하고 삭제 variant에 원문과 외부 작성자 표현을 포함하지 않습니다.
- ASCII·Unicode 공백만 있는 입력, `U+0000`, 고립 high·low surrogate, Unicode code point
  길이 초과와 빈 `PATCH`는 `400` 공통 validation 응답을 반환하고 상태를 변경하지 않습니다.
  CRLF·CR 정규화, 정상 emoji surrogate pair와 결합 문자 경계도 HTTP JSON·domain·PostgreSQL
  저장에서 같은 결과를 냅니다.
- 동시에 같은 콘텐츠를 수정해 낙관적 잠금이 충돌하면 한 요청만 성공하고 다른 요청은
  `409 Conflict`를 받습니다.
- 같은 게시글의 댓글 생성끼리는 불필요하게 직렬화하지 않으면서, 댓글 생성과 게시글 삭제가
  경합할 때 삭제가 먼저 완료된 Post에는 새 Comment를 insert하지 않습니다. Comment가 먼저
  commit되면 자리 유지·전체 숨김은 해당 Comment를 각 visibility 규칙대로 보존하고, 물리 삭제는
  cascade로 함께 제거합니다.
- post cursor slice 기본 20·최대 100, comment cursor slice 기본 50·최대 100을 지키고
  무제한 조회와 별도 total count query를 만들지 않습니다.
- 변경이 없는 dataset에서 cursor를 이어 읽을 때 중복·누락 없이 고정된 정렬 방향을 유지하고,
  동시 추가는 이미 지난 경계 앞에 끼워 넣지 않습니다. 동시 삭제는 선택에 따라 같은 위치의
  placeholder로 남거나 visibility에서 제외됩니다. Malformed cursor는 `400`을 반환합니다.
- community code는 `account.api`만 참조하고 `account.internal`·`account` table·JPA Entity를
  참조하지 않습니다.
- 게시글 schema에는 `board_id NOT NULL` FK가 있고, 초기 게시판 수가 바뀌어도 기존 post의
  category backfill이나 identity 교체가 필요하지 않습니다.
- 차량, reply, reaction, bookmark, report, media, search와 notification 전용 table·field·
  event/outbox가 최초 migration과 API 명세에 존재하지 않습니다.
- focused test, PostgreSQL migration·persistence 통합 test, Security·OpenAPI·architecture
  회귀 test와 최종 `./gradlew check`가 통과합니다.

## 남은 결정

### 결정 1. 로그인하지 않은 사용자도 글을 읽을 수 있게 할까요?

- **사용자 소유 근거**: 공개 범위는 유입·개인정보·운영 부담을 함께 정하는 제품 선택이며
  현재 제품 문서와 Security source가 한 값을 고정하지 않습니다.
- **배경과 제약**: 현재 `/api/**`는 기본 보호 상태이고 공개 API만 중앙 allow-list에서
  예외로 엽니다. 작성·수정·삭제는 어느 선택에서도 인증이 필요합니다.
- **대안 A — 공개 조회**: `GET` 게시글·댓글만 비로그인에게 열고, 쓰기는 인증 회원에게만
  엽니다. 검색·공유와 신규 유입에는 유리하지만 개인정보 노출과 운영 대응 범위가 큽니다.
- **대안 B — 회원 전용 조회**: 읽기와 쓰기 모두 정상 로그인 계정에만 엽니다. 초기 운영은
  단순하지만 가입 전 사용자가 커뮤니티 가치를 확인할 수 없습니다.
- **추천**: **A 공개 조회**입니다. 제품 목표가 정보 탐색과 커뮤니티 활동을 함께 제공하는
  것이고, 공개되는 account 정보도 이미 표시 이름과 public id로 제한돼 있기 때문입니다.
- **영향·위험·재검토**: 공개 조회를 선택하면 board/post/comment `GET` operation을
  Security·OpenAPI 공개 계약에 추가합니다. 공개 rollout 전에는 별도 moderation·rate-limit
  결정을 다시 확인합니다.
- **응답 형식**: `조회=공개` 또는 `조회=회원전용`

### 결정 2. 첫 화면에 게시판을 몇 개 제공할까요?

- **사용자 소유 근거**: 사용자가 글을 어디에 쓰고 찾는지는 정보 구조와 UX 선택입니다.
  내부 schema는 어느 선택에서도 Board를 사용하므로 이 결정이 확장 가능한 기술 구조까지
  다시 선택하게 하지는 않습니다.
- **배경과 제약**: 차량별 게시판과 관리자 게시판 CRUD는 이번 범위가 아닙니다. 모든 선택에서
  게시글은 `community_board`를 필수 참조합니다.
- **대안 A — 일반 게시판 하나**: `일반` 게시판만 노출합니다. 현재 기능은 가장 단순하지만
  사용자가 글 성격을 화면에서 분류하지 못합니다. Seed 계약은 `general`/`일반`/order 100입니다.
- **대안 B — 고정 3개 게시판**: `자유`, `질문`, `정보공유` 게시판을 seed합니다. 탐색성은
  좋아지지만 이름 변경과 잘못 선택한 게시판의 이동 정책이 필요합니다. Seed 계약은
  `free`/`자유`/100, `questions`/`질문`/200, `information`/`정보공유`/300입니다.
- **대안 C — 관리형 게시판까지 구현**: 관리자가 게시판을 생성·정렬·비활성화합니다. 장기
  구조는 같지만 관리자 API·권한·상태 전이를 현재 범위에 추가해야 합니다.
- **추천**: **A 일반 게시판 하나**입니다. 게시판 identity와 FK는 처음부터 존재하므로 이후
  게시판을 늘려도 기존 게시글 backfill 없이 새 board row와 화면만 추가할 수 있습니다.
- **영향·위험·재검토**: A와 B는 seed 수만 다르고 API·Post 구조는 같습니다. C를 선택하면
  board 관리 기반을 별도 선행 결과로 분리합니다.
- **응답 형식**: `게시판=단일`, `게시판=고정3개` 또는 `게시판=관리형`

### 결정 3. 작성자가 글이나 댓글을 삭제하면 무엇을 남길까요?

- **사용자 소유 근거**: 삭제 뒤 다른 사용자의 댓글을 유지할지와 원문·작성자 연결을 얼마나
  보존할지는 사용자 경험과 개인정보 감수 수준을 함께 정하는 제품 결정입니다.
- **배경과 제약**: 물리 삭제는 복구가 어렵고 게시글과 댓글의 생명주기를 함께 지웁니다.
  반대로 원문을 계속 저장하면 사용자가 기대하는 삭제와 어긋날 수 있습니다. 운영자 숨김은
  어느 선택에서도 작성자 삭제와 별도 기능입니다.
- **대안 A — 원문 삭제·자리 유지**: 제목·본문 또는 댓글 원문과 외부 작성자 표현을 지우고
  `삭제된 글/댓글` 자리만 남깁니다. 삭제 Post는 목록·상세에 placeholder로 남고 기존 댓글은
  조회·각 작성자의 수정·삭제가 가능하지만 새 댓글과 Post 수정은 막습니다. FK는 하위 댓글을
  보존하며 같은 작성자의 반복 DELETE는 `204`입니다.
- **대안 B — 전체 숨김**: DB에는 원문을 남기되 삭제 게시글은 목록·상세와 모든 하위 댓글
  endpoint에서 `404`로 숨깁니다. 개별 삭제 댓글도 목록·변경에서 제외합니다. 삭제 Post와
  active Post 아래 개별 삭제 댓글은 같은 작성자의 반복 DELETE만 `204`이고, 삭제 Post 아래
  댓글과 다른 사용자의 요청은 `404`입니다. FK는 row를 보존합니다.
- **대안 C — 물리 삭제**: Post row와 모든 하위 Comment row를 `ON DELETE CASCADE`로 즉시
  지우고, 개별 Comment DELETE도 row를 지웁니다. 이후 목록·상세·변경과 반복 DELETE는 모두
  `404`입니다. 보존 데이터는 가장 적지만 복구가 불가능하고 다른 작성자의 댓글도 함께
  삭제됩니다.
- **추천**: **A 원문 삭제·자리 유지**입니다. 삭제한 사용자의 원문을 응답과 DB에서 제거하면서
  다른 사용자의 댓글과 대화 구조를 보존할 수 있습니다.
- **영향·위험·재검토**: A는 삭제 상태와 nullable content constraint, B는 active partial
  index, C는 cascade FK가 필요합니다. 내부 작성자 식별자의 보존 기간과 account 탈퇴·익명화는
  별도 privacy 작업에서 재검토합니다.
- **응답 형식**: `삭제=자리유지`, `삭제=전체숨김` 또는 `삭제=물리삭제`

세 결정을 한 줄로 함께 답할 수 있습니다. 예:
`조회=공개, 게시판=단일, 삭제=자리유지`

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
- 낙관적 수정과 공유 comment-create/exclusive post-delete lock 조합
- 변경 모델과 조회 projection의 분리, 첫 durable consumer 전 event/outbox 도입 유예
- additive migration과 exact rollback image 자격·forward recovery 원칙
- 미래 기능별 association·read model·module 확장 경계

## 의미 단위 계획

### 작업 1. 남은 제품 결정을 반영해 계약을 동결합니다

작업 목표: 세 사용자 선택을 API 공개 범위, 초기 board seed와 작성자 삭제 생명주기에 exact
값으로 반영합니다.

작업 이유: 제품 선택과 무관한 Board 구조는 이미 고정하지만, 노출 범위·초기 화면·삭제 결과는
Security, seed data와 응답을 바꾸므로 구현 중 임의 선택을 막아야 합니다.

선행 조건: `조회`, `게시판`, `삭제` 결정에 대한 사용자 답변이 필요합니다.

수행 내용:

- 이 문서와 상세 설계의 선택 분기를 하나로 고정하고 `Planned` 후보를 만듭니다.
- 선택하지 않은 분기와 조건부 API·schema 문구를 제거합니다.
- `$verify-plan` 독립 감사로 사용자 결정과 계획 값이 일치하는지 확인합니다.

검증과 완료 결과: 결정 카드의 exact 응답과 문서의 요구·수용 기준·설계 값이 양방향으로
일치하고, 구현자가 제품 선택을 새로 하지 않아도 됩니다.

### 작업 2. community 경계와 작성자 공개 계약을 먼저 세웁니다

작업 목표: `community`가 account 내부 구현 없이 인증 작성자와 표시 이름을 사용할 수 있게
합니다.

작업 이유: producer인 `account.api` 계약과 module boundary를 먼저 검증해야
board/post/comment consumer를 안전하게 추가할 수 있습니다.

선행 조건: 작업 1의 동결 결과가 필요합니다.

수행 내용:

- 실패하는 module verification과 public profile lookup 단위·persistence test를 먼저
  작성합니다.
- `account.api`에 public id 집합을 표시 이름 표현으로 batch 변환하는 최소 계약을 추가합니다.
- account internal adapter가 account/profile source를 한 query로 읽고, community가 해당
  named interface만 의존하게 합니다.
- `community` module package와 허용 의존을 추가하고 Spring Modulith·ArchUnit 경계를
  검증합니다.

검증과 완료 결과: batch lookup이 표시 이름을 정확히 반환하고, 다른 module internal 접근과
순환 의존 없이 module verification이 통과합니다.

복구: 공개 계약에 consumer가 붙기 전에는 추가한 type과 package를 함께 제거할 수 있습니다.

### 작업 3. Board와 콘텐츠 변경 계약을 구축합니다

작업 목표: 초기 게시판 수와 무관한 Board identity, Post·Comment 생명주기, action policy와
PostgreSQL 무결성을 먼저 구축합니다.

작업 이유: Board를 나중에 추가하면 기존 post backfill과 API 재구성이 필요하고, 권한·삭제를
Controller에 고정하면 moderation 확장 때 우회 경로가 생깁니다.

선행 조건: 작업 1의 삭제·초기 게시판 선택과 작업 2의 module 경계가 필요합니다.

수행 내용:

- Board reference, Post·Comment domain model, `CommunityActionPolicy`와 선택한 작성자 삭제
  규칙을 실패 단위 test부터 고정합니다.
- 새 versioned Flyway migration에 단수형 `community_board`, `community_post`,
  `community_comment` table, constraint와 실제 조회 패턴 기반 index를 추가합니다.
- 선택한 초기 board row의 literal UUID·code·display order를 migration에 seed하고 모든 post가
  `board_id NOT NULL` FK를 갖게 합니다.
- board/post/comment UUID public id, author account public id, 감사 시각과 변경 model의
  `@Version` 값을 저장합니다.
- generic content table, 미사용 moderation/reaction/media/event/outbox 구조는 만들지 않습니다.
- PostgreSQL Testcontainers로 migration, FK, Unicode 정규화·constraint와 optimistic
  conflict를 검증합니다.
- 구현 schema가 고정되면 새 community schema reference를 함께 작성합니다.

검증과 완료 결과: 게시판이 하나여도 post는 안정된 board identity를 가지며, 잘못된 길이·
상태·참조가 DB와 application 양쪽에서 거부됩니다.

복구: migration은 되돌려 수정하지 않습니다. 새 migration이 적용된 DB에서 raw 이전 image는
해당 migration을 알지 못해 rollback 후보가 아닙니다. staging rollout을 별도 승인받으면
baseline application 동작과 새 migration source를 함께 가진 exact rollback image를 만들고,
현재 DB history·checksum snapshot에서 migration process가 통과하는지 먼저 검증합니다. 그
후보가 없으면 application rollback을 주장하지 않고 current schema 위의 forward fix로
복구합니다. 미사용 table 제거는 보존 결정을 거친 별도 forward migration으로 처리합니다.

### 작업 4. Board와 게시글 API를 첫 세로 단위로 완성합니다

작업 목표: 게시판 발견부터 게시글 생성·cursor 목록·상세·수정·삭제까지 첫 end-to-end
vertical slice를 완성합니다.

작업 이유: Board와 Post의 실제 consumer 경로가 확장 계약을 사용해야 댓글과 후속 기능이
안전한 기반을 소비할 수 있습니다.

선행 조건: 작업 2와 작업 3이 통과해야 합니다.

수행 내용:

- domain/application 실패 test, Controller test, persistence query test 순서로 Red를
  확인한 뒤 최소 구현과 refactor를 반복합니다.
- `(displayOrder ASC, publicId ASC)` Board 목록·상세 read model과 board-scoped Post
  command/query를 구현합니다.
- Web DTO를 command/query로 변환하고 Post 변경 use case와 조회 projection을 분리합니다.
- post slice마다 author public id를 모아 account batch API를 한 번 호출합니다.
- `(createdAt DESC, publicId DESC)` keyset query와 collection 종류·board scope에 결속된
  versioned opaque `nextCursor`를 구현하고 내부 PK를 cursor에 넣지 않습니다.
- 생성 `201`·`Location`, 조회 `200`, 수정 `200`, 삭제 `204`, validation·404·403·409
  Problem Details와 선택한 공개 조회 정책을 OpenAPI에 반영합니다.

검증과 완료 결과: 게시판을 발견해 글을 만들고 다음 cursor를 이어 읽는 정상 경로와
validation·소유권·미존재·동시 수정 계약이 Web과 PostgreSQL 통합 test에서 관찰됩니다.

복구: endpoint mapping을 제거한 forward fix는 새 table을 그대로 두고 기존 account/auth
동작을 보존합니다. Image rollback은 작업 3에서 현재 DB history와 호환성을 입증한 exact
후보가 있을 때만 사용합니다.

### 작업 5. 댓글 API를 게시글 계약 위에 완성합니다

작업 목표: 조회 가능성·새 댓글 수용 가능성을 분리하고, commentable한 active 게시글에 대한
평면 댓글 생성과 visible 댓글의 cursor 목록·수정·삭제를 완성합니다.

작업 이유: 댓글은 post 존재·삭제 상태에 의존하지만 같은 인기 글의 댓글 생성끼리는 병렬
처리될 수 있어야 합니다.

선행 조건: 작업 4의 게시글 lifecycle과 detail 조회가 통과해야 합니다.

수행 내용:

- 댓글 작성순 cursor, 생성, 수정과 삭제의 실패 test를 먼저 작성합니다.
- comment command와 read projection을 분리하고 author 표시 이름은 같은 batch API로
  조립합니다.
- 댓글 생성은 parent post에 PostgreSQL `FOR SHARE`, post 삭제는 `FOR UPDATE`를 사용해
  create-create는 공유하고 create-delete만 직렬화합니다.
- 조회 가능한 자리 유지 Post도 author-deleted 상태이면 `commentable=false`로 새 댓글을
  `404` 처리합니다. Parent 불일치·그 밖의 삭제 상태도 `404`, 비작성자 변경은 `403`, 동시
  수정 conflict는 `409`로 고정합니다.
- 선택한 post 삭제 방식이 comment 조회·수정·삭제에 미치는 동작을 통합 test로 고정합니다.

검증과 완료 결과: 댓글 API의 parent·cursor·소유권·삭제 경계가 게시글과 모순 없이
동작하고, 동시 댓글 생성은 서로 막지 않으면서 post 삭제 race의 terminal state가 안전합니다.

복구: comment endpoint를 제거해도 post API와 schema는 독립적으로 유지됩니다.

### 작업 6. 확장 경계와 최종 품질 gate를 닫습니다

작업 목표: 현재 최소 기능을 기존 Security, 오류, OpenAPI, 문서와 통합하고 미래 기능을
구현하지 않으면서도 상세 설계의 확장 불변식을 실제로 지키는지 검증합니다.

작업 이유: endpoint 구현만 통과하면 Board 우회, 내부 table 참조, 변경 모델에 조회 관심사
혼입, 관리자 DELETE 우회와 미래용 dead structure를 놓칠 수 있습니다.

선행 조건: 작업 4와 작업 5의 후보가 동결돼야 합니다.

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
- `domain`에는 확정된 Board·Post·Comment 정책, `project`에는 module map·현재 schema,
  work item에는 구현 결과와 남은 외부 책임만 승격합니다.
- Docker daemon preflight 뒤 재사용 없는 PostgreSQL Testcontainers를 포함해
  `./gradlew check`를 실행합니다. 별도 push 권한이 있는 구현 작업에서는 final SHA의 required
  CI와 Sonar revision·Quality Gate·Open issues도 확인합니다.

검증과 완료 결과: 현재 API가 작아도 Board·권한·조회·변경 경계가 source와 test로 보호되고,
후속 기능은 상세 설계의 additive 경로를 사용할 수 있습니다. 전체 local quality gate가
통과하며 push 권한이 없으면 local 후보와 remote gate 미완료를 구분합니다.

복구: exact rollback image의 migration 호환성이 검증된 경우에만 새 API를 image rollback으로
제거합니다. 그렇지 않으면 endpoint를 닫는 forward fix를 사용합니다. production 공개 rollout은
moderation·rate-limit owner가 확정될 때까지 별도 작업으로 유지합니다.

## 검증·전환·복구

- 구현은 각 의미 단위에서 Red → Green → Refactor를 적용하고 실패 이유를 확인합니다.
- Domain/Application은 Spring 없는 단위 test, Web은 MVC slice, query·constraint·migration은
  PostgreSQL Testcontainers 통합 test를 사용합니다.
- Security는 공개/보호 선택별 미인증·정상 인증·비작성자 거부를 분리해 검증합니다.
- Cursor test는 첫 page, 다음 page, 동률 timestamp, malformed cursor와 현재 collection scope가
  다른 token을 포함합니다. 동시 추가는 이미 받은 경계 앞에 다시 나타나지 않고 동시 삭제는
  선택에 따라 같은 위치의 placeholder로 남거나 visibility에서 제외되는 non-snapshot
  continuation 의미도 검증합니다.
- 기존 account/auth·notification API와 migration V1~V12 회귀를 `./gradlew check`로 함께
  확인합니다.
- schema 변경은 새 forward migration만 사용하며 이미 적용된 migration을 수정하거나 자동
  down migration을 만들지 않습니다.
- 새 API와 table은 additive이지만 raw 이전 application image가 Flyway rollback 후보라는 뜻은
  아닙니다. Rollback은 새 migration이 적용된 DB에서 migration process·history·checksum
  호환성을 입증한 exact image로만 수행하고, 후보가 없으면 forward fix로 복구합니다. 새 table
  삭제는 데이터 보존과 재배포 가능성을 확인한 별도 작업에서만 수행합니다.
- 구현 완료 판정은 local test만으로 닫지 않고, push 권한이 있는 경우 exact final SHA의
  required CI와 Sonar gate까지 확인합니다.

## 영구 SOT 승격

구현과 검증이 완료될 때만 다음 현재 사실을 영구 문서에 승격합니다.

- `docs/domain`: Board·Post·Comment 용어, action policy와 선택한 삭제 생명주기
- `docs/project/210-module-map.md`: `community` 책임과 `community -> account :: api` 협력
- `docs/project/schema`: community 최종 table·constraint·index reference
- `docs/product/200-feature-scope.md`: 실제 구현된 일반 커뮤니티 범위와 계속 제외된 차량 연결
- `docs/domain/110-account-auth-policy.md`: 선택한 공개 조회와 보호 쓰기 접근 매트릭스

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
