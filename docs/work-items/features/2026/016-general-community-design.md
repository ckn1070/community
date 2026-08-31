# 차량과 분리해 시작하는 일반 커뮤니티 해결 설계

## 문서 경계

이 문서는 [016 차량 비연결 일반 커뮤니티](016-general-community.md)에서 확정한 12개 제품
결정을 구현 가능한 장기 기술 계약으로 변환합니다. 제품 범위, 제외 범위와 사용자 수용 기준은
일반 문서가 소유하고, 이 문서는 그 결과를 바꾸지 않는 module·domain·API·data·transaction·
security·media·notification·moderation·운영 구조만 소유합니다.

이 문서는 실행 task, 구현 순서, 일정과 실제 검증 실행을 소유하지 않습니다. 아직 구현되지
않은 구조를 현재 project 사실로 승격하지 않으며, 구현이 완료되면 영구 SOT와 schema 문서가
실제 source에 맞춰 현재 사실을 인수합니다.

## 입력과 현재 제약

- 현재 배포 구조는 단일 Spring Boot 애플리케이션과 PostgreSQL을 사용하는 modular
  monolith입니다.
- module 간 협력은 상대 module의 `api`만 사용하며 `internal` package나 상대 소유 table을
  직접 읽지 않습니다.
- 공개 읽기를 제외한 일반 쓰기는 `SESSION_FULL`과
  `PASSWORD_CHANGE_NOT_REQUIRED`를 모두 만족해야 합니다. 관리자 기능은 여기에
  `ROLE_ADMIN` 또는 `ROLE_SUPER_ADMIN`을 추가로 요구합니다.
- account의 외부 식별자는 UUID이고 표시 이름은 `account_profile`이 소유합니다. Community가
  account table을 직접 조회하거나 표시 이름 snapshot을 중복 저장하지 않습니다.
- 현재 `notification` module은 이메일 발송만 구현했습니다. 서비스 안 알림 저장소는 아직
  존재하지 않습니다.
- 현재 staging EC2 instance profile은 node의 ECR pull과 Load Balancer lifecycle만
  소유합니다. 애플리케이션의 일반 AWS 권한을 node role에 추가하지 않습니다.
- 현재 application은 replica `1`이고 edge proxy·WAF·공유 rate limiter가 없습니다.
- 기존 V7·V10을 직접 수정한 Auth Event migration 때문에 과거 checksum을 가진 지속 DB는
  Flyway validation과 기존 row 전환에 실패할 수 있습니다. Community migration은 이 결함을
  우회하거나 함께 고친 것으로 간주하지 않습니다.
- Community, media와 moderation은 아직 구현 module이나 영구 domain SOT가 아닙니다.

## 목표 불변식

1. 차량 기능이 없어도 게시판과 전체 대화가 독립적으로 동작합니다.
2. 현재의 단일 자유게시판, 1단 표시와 두 반응 종류는 저장 모델의 영구 상한이 아닙니다.
3. 게시글·댓글·답글의 소유자, 삭제, 숨김, 반응과 신고 상태는 동시 요청에도 모순되지
   않습니다.
4. 화면은 답글을 한 단계로만 표시하지만, 각 응답의 직접 대상과 최상위 대화 뿌리를 모두
   잃지 않습니다.
5. 원문과 작성자를 숨기는 두 원인인 작성자 삭제와 관리자 숨김을 서로 다른 사실로
   보존합니다.
6. 서비스 안 알림은 대화 생성과 함께 원자적으로 저장되고 자기 알림과 같은 수신자의 중복
   알림을 만들지 않습니다.
7. 업로드 원본은 공개하지 않으며, 검증을 통과한 정규화 이미지와 공개 게시글의 활성 사용
   관계만 외부에 노출합니다.
8. 외부 object storage 실패가 DB transaction을 열린 채로 붙잡지 않고, 재시도 가능한 상태로
   남습니다.
9. 공개 API 식별자와 module 공개 계약은 이후 카테고리, 차량 관계, 공개 profile, 새 반응,
   새 알림 채널과 더 깊은 대화가 추가돼도 유지할 수 있습니다.

## 대표 생명주기

```text
회원이 application multipart endpoint로 이미지 upload
  -> application이 제한된 임시 파일로 검증 입력을 만들고 private staging object 저장
  -> media 검증 job이 decode·제한 검사·재인코딩
  -> READY image를 본문 block으로 claim하면서 게시글 공개
  -> 다른 회원이 최상위 댓글 또는 특정 댓글·답글에 응답
  -> 같은 DB transaction에서 대상 사용자 inbox 알림 0개 또는 1개 저장
  -> 반응은 사용자·콘텐츠당 한 선택만 유지
  -> 신고는 moderation case로 접수
  -> 관리자가 숨기면 원문 노출과 media 사용을 즉시 중단하고 report를 종결
  -> 작성자가 삭제하면 별도 삭제 사실을 남기고 하위 대화 유무에 따라 tombstone 또는 목록 제외
```

이 흐름에서 Community DB 상태, inbox 알림과 media 사용 관계는 같은 PostgreSQL transaction에
참여합니다. S3 검증·복사·삭제처럼 외부 I/O가 필요한 효과는 media module의 DB job으로
기록한 뒤 transaction 밖 worker가 처리합니다.

## 확정 결정 요약

| 결정 | 확정 구조 | 보존하는 확장 경계 |
| --- | --- | --- |
| 업무 module | `community`, `media`, `moderation` 신설과 기존 `notification`, `account` 공개 계약 확장 | 각 생명주기의 독립 변경과 후속 제재·채널 확장 |
| 콘텐츠 공통 식별 | `community_content` 공통 root와 post/comment subtype | reaction·report·visibility의 단일 target 무결성 |
| 게시글 본문 | versioned typed JSON block, v1은 `TEXT`·`IMAGE` | 투표·임베드 등 새 block의 additive 도입 |
| 대화 | direct parent와 top-level root 동시 저장 | 현재 flat reply와 미래 다단 tree 공존 |
| 삭제·숨김 | `author_deleted_at`과 `moderator_hidden_at` 별도 보존 | 복구·이의제기 도입 시 원인과 선후 관계 유지 |
| 작성자 표시 | account 공개 batch lookup으로 현재 표시 이름 조회 | profile 링크 추가 시 콘텐츠 schema 변경 불필요 |
| 반응 | 콘텐츠·사용자 unique choice와 `LIKE`·`DISLIKE` | 새 reaction type 추가와 별도 ranking projection |
| 신고 | moderation 소유 case와 Community 숨김 공개 계약 | 후속 계정 제재·감사·appeal을 moderation에 추가 |
| 서비스 안 알림 | notification 소유 inbox row를 대화 transaction에서 동기 저장 | 이메일·push worker가 inbox source를 후속 소비 |
| 이미지 | private S3 staging/serving prefix, 비동기 검증·재인코딩·정리 job | storage adapter·delivery adapter와 media 종류 교체 |
| 조회 | read projection, opaque keyset cursor, author·반응 batch 조회 | 검색·인기·추천 projection을 core write와 분리 |
| 배포 | additive Flyway와 capability fail-close | application rollback 시 새 schema·object 보존 |

## 설계 결정

### D1. Module 소유권과 의존 방향

- 배경: 대화, 이미지, 운영 신고와 알림은 실패 방식과 확장 이유가 다릅니다. 하나의
  Community 내부에 모두 넣으면 S3·관리자 정책·알림 채널 변경이 대화 핵심을 함께 흔듭니다.
- 검토한 선택지: 하나의 `community` mega module, `community`에 media/moderation adapter만
  추가, 생명주기별 module 분리
- 선택: `community`, `media`, `moderation`을 독립 업무 module로 두고 기존 `notification`과
  `account`의 공개 계약을 확장합니다.
- 선택 근거: 현재 필요한 최소 기능만 각 module에 두면서도 media 정리, 신고 종결, 알림 읽음
  상태처럼 서로 다른 상태 전이를 독립적으로 확장할 수 있습니다.
- 비용: module 공개 DTO와 transaction 협력 계약이 늘어납니다.
- 재검토 조건: 실제 규칙이 한 module을 독립 배포해야 할 만큼 분리되거나, 현재 public call로
  원자성을 유지하는 비용이 단일 DB 이점보다 커집니다.

의존 방향은 다음과 같습니다.

```text
community -> account.api
community -> media.api
community -> notification.api
moderation -> community.api

community -X-> account.internal / media.internal / notification.internal
moderation -X-> community.internal
media -X-> community
notification -X-> community
```

| module | 현재 범위의 소유 책임 |
| --- | --- |
| `community` | board, post, comment/reply, reaction, effective visibility와 public/write API |
| `media` | upload reservation, object key, 검증·정규화, usage, delivery, cleanup job |
| `moderation` | report 접수·중복 방지, 관리자 검토 목록, resolution lifecycle |
| `notification` | in-app inbox, unread/read 상태, recipient deduplication |
| `account` | account 쓰기 가능 상태와 현재 공개 표시 이름 batch 조회 |
| `config` | 공개 GET allow-list, 일반·관리자 접근 정책과 module 조립 |

`moderation`은 숨김을 결정하지만 Community row를 직접 갱신하지 않습니다. `community.api`의
멱등 숨김 계약을 호출하고 같은 database transaction에 참여합니다. `community`는 신고 table을
직접 읽지 않습니다.

### D2. 콘텐츠 공통 root와 공개 식별자

- 배경: 게시글·댓글·답글은 작성자, 수정, 삭제, 숨김, 반응과 신고 규칙을 공유합니다.
- 검토한 선택지: 대상별 중복 table, `(target_type, target_id)` polymorphic 참조만 사용,
  공통 root와 subtype table
- 선택: `community_content`가 공통 상태와 UUID `public_id`를 소유하고
  `community_post`, `community_comment`가 종류별 payload를 소유합니다.
- 선택 근거: reaction은 실제 FK 하나로 모든 콘텐츠를 참조하고, 공통 visibility 규칙을 한
  target에 적용할 수 있습니다. 게시글과 댓글의 다른 구조는 subtype에서 분리합니다.
- 비용: 저장·조회 mapping이 단일 table보다 늘고 subtype 일치 검증이 필요합니다.
- 재검토 조건: 콘텐츠 종류가 서로 다른 보존·identity 경계를 가져 공통 root가 의미를
  잃습니다.

내부 PK는 현재 schema 규칙대로 identity `BIGINT`, 외부 API와 module 계약은 UUID
`public_id`를 사용합니다. Community는 작성자 `account_public_id`를 UUID 값으로 보존하지만
account table에 cross-module FK를 만들지 않습니다. 계정 삭제도 row를 물리 삭제하지 않는 현재
account 정책과 공개 UUID가 콘텐츠 관계의 안정성을 보장합니다.

### D3. Board와 게시글 본문 표현

- 배경: 지금은 자유게시판 하나지만 이후 카테고리를 기존 게시글에 추가해야 하며, 텍스트와
  이미지의 순서를 잃지 않아야 합니다.
- 검토한 선택지: board column에 문자열 상수 저장, `community_board` 기준 table, 게시판별
  subtype; Markdown media token, text+attachment 목록, typed JSON block
- 선택: `community_board`에 안정된 slug `free`를 seed하고 모든 post가 board FK를 가집니다.
  본문은 schema version과 ordered JSON block으로 저장하며 v1 block은 `TEXT`와 `IMAGE`만
  허용합니다.
- 선택 근거: 새 board는 기준 row를 추가하는 방식이고, typed block은 사용자 HTML을 받지
  않으면서 텍스트·이미지 순서와 media UUID를 명시적으로 검증합니다.
- 비용: JSON validation과 block-to-response mapping이 필요하고 DB constraint만으로 전체 JSON
  shape를 보장할 수 없습니다.
- 배제한 대안: Markdown token은 parser와 escape 규칙을 공개 API에 영구 결속하고, 단순
  attachment 목록은 본문 내 이미지 위치를 잃습니다.
- 재검토 조건: 협업 편집, rich text mark, embed처럼 block 간 참조가 필요한 편집기가
  확정됩니다.

v1 본문 계약은 다음과 같습니다.

```json
{
  "version": 1,
  "blocks": [
    { "type": "TEXT", "text": "plain text" },
    { "type": "IMAGE", "mediaId": "uuid", "alt": "optional plain text" }
  ]
}
```

- HTML과 Markdown을 입력 형식으로 해석하지 않고 모든 text/alt는 plain text로 반환합니다.
- 빈 text block은 정규화 과정에서 제거하고, 게시글에는 공백이 아닌 TEXT가 하나 이상 있어야
  합니다. Image는 이 텍스트 본문에 함께 배치하는 선택 항목입니다.
- 현재 확정 제품 결과에 제목은 없으므로 별도 `title` 필드를 만들지 않습니다. 제목 기능이
  필요해지면 본문 schema를 암묵적으로 바꾸지 않고 별도 제품 결정으로 추가합니다.
- 목록 preview는 TEXT block에서 파생하며 별도 사용자 원문을 중복 저장하지 않습니다.

### D4. Comment tree와 1단 표시

- 배경: 현재 UI는 한 단계 답글만 보여 주지만 답글에 다시 답한 직접 대상은 표시해야 하고,
  향후 깊은 대화를 저장 불가능하게 만들면 안 됩니다.
- 검토한 선택지: `parent_id` 하나만 저장, top-level root만 저장, direct parent와 root를 함께
  저장
- 선택: top-level 댓글은 `parent_comment_id`, `thread_root_comment_id`가 모두 null이고,
  모든 reply는 direct parent와 top-level root를 함께 저장합니다.
- 선택 근거: root로 현재의 flat reply page를 효율적으로 조회하고 parent로 정확한 답변 대상과
  미래 tree를 보존합니다.
- 비용: 같은 post, root가 실제 top-level인지와 immutable parent를 application에서 먼저
  검증하고 database constraint trigger로 다시 차단해야 합니다.
- 재검토 조건: 대화를 별도 thread aggregate나 materialized path로 탐색해야 할 만큼 깊이·
  조회량이 증가합니다.

reply 생성 규칙은 다음과 같습니다.

- 요청은 같은 post에 속한 direct parent UUID를 받습니다.
- parent가 top-level이면 그 parent가 root입니다.
- parent가 reply이면 parent의 root를 그대로 복사합니다.
- parent와 root는 생성 후 변경하지 않습니다. 새 row만 기존 row를 parent로 삼으므로 cycle을
  만들지 않습니다.
- 삭제 또는 숨김 처리된 콘텐츠는 새 답변 대상이 될 수 없지만, 이미 존재하는 후손은
  유지합니다.
- 응답은 모든 reply를 root 아래 한 단계로 반환하고 direct parent의 공개 UUID와 표시 가능한
  대상 이름을 `replyTo`에 제공합니다. 대상이 삭제·숨김·탈퇴로 비공개이면 대상 이름은
  placeholder로 바꿉니다.

### D5. 수정, 작성자 삭제와 관리자 숨김

- 배경: 삭제와 숨김은 원문을 감추는 결과는 비슷하지만 주체와 미래 복구 정책이 다릅니다.
- 검토한 선택지: 단일 status enum, 물리 삭제, 원인별 timestamp 동시 보존
- 선택: `author_deleted_at`과 `moderator_hidden_at`을 독립 nullable timestamp로 보존하고
  effective visibility는 `MODERATOR_HIDDEN > AUTHOR_DELETED > VISIBLE` 우선순위로 계산합니다.
- 선택 근거: 두 사건이 모두 발생해도 사실을 덮어쓰지 않으며, 현재 복구를 제공하지 않더라도
  후속 정책이 원인을 식별할 수 있습니다.
- 비용: 조회가 raw column 하나가 아니라 visibility resolver를 거쳐야 합니다.
- 재검토 조건: 법적 보존, 복구·appeal, revision history가 확정돼 별도 lifecycle aggregate가
  필요합니다.

| 상태 | 공개 body | 공개 작성자 | 대화 표시 |
| --- | --- | --- | --- |
| visible | 현재 body | 현재 표시 이름 | 정상 콘텐츠 |
| author deleted | 미노출 | 미노출 | 공개 후손이 있으면 `삭제된 콘텐츠`, 없으면 목록·상세 조회에서 제외 |
| moderator hidden | 미노출 | 미노출 | 공개 후손이 있으면 `관리자에 의해 숨겨진 콘텐츠`, 없으면 목록·상세 조회에서 제외 |

- post가 tombstone이면 공개 후손을 읽을 수 있도록 detail route는 유지합니다. 공개 후손이
  하나도 없으면 public 조회는 `404`로 수렴합니다.
- comment/reply도 같은 규칙을 사용합니다. 삭제된 leaf는 page에서 빠지고, 공개 후손의 root
  또는 parent context로 필요한 row만 tombstone으로 남습니다.
- 수정은 body의 semantic 값이 실제로 달라질 때만 `edited_at`을 설정합니다. revision 원문은
  저장하지 않습니다.
- 삭제·숨김 이후 수정, reaction, 새 reply와 새 report는 거부합니다. 다만 관리자가 먼저
  숨긴 콘텐츠를 작성자가 삭제하거나 작성자가 먼저 삭제한 콘텐츠의 open report를 관리자가
  숨김 종결하는 것은 허용해 두 원인의 사실을 모두 보존합니다. 기존 reaction과 report row는
  감사·중복 무결성을 위해 남기지만 공개 count에는 노출하지 않습니다.
- post에서 image를 제거하거나 작성자가 post를 삭제하면 같은 transaction에서 media usage를
  `RELEASED`로 전환하고 cleanup job을 저장합니다. 관리자 숨김은 usage를
  `HIDDEN_RETAINED`로 바꿔 공개만 즉시 차단하며 delivery object를 삭제하지 않습니다.
  숨김 뒤 작성자 삭제가 발생하면 `HIDDEN_RETAINED -> RELEASED`로 전환합니다. 따라서 현재
  appeal·숨김 취소 API를 만들지 않아도 미래 정책이 원본 없이 막히지 않습니다.

### D6. 작성자 표시 이름 경계

- 배경: 목록과 대화는 표시 이름이 필요하지만 public profile은 현재 범위가 아니며 Community가
  account persistence를 읽어서는 안 됩니다.
- 검토한 선택지: 작성 시 표시 이름 snapshot 저장, 요청마다 account internal table join,
  account 공개 batch lookup
- 선택: account가 `AccountPublicDisplayLookup` batch 계약을 제공하고 Community read service가
  한 page의 UUID 집합을 한 번에 조회합니다.
- 선택 근거: 현재 표시 이름 변경을 일관되게 반영하고 cross-module table 접근과 N+1 호출을
  피합니다.
- 비용: Community read가 account 공개 계약의 가용성에 의존합니다.
- 재검토 조건: 대규모 feed에서 batch lookup이 측정된 병목이 되거나 표시 이름의 과거 snapshot
  보존이 제품 요구가 됩니다.

반환 계약은 `DISPLAYABLE(displayName)` 또는 `ANONYMOUS`입니다. `WITHDRAWN` 등 공개 표시를
중단해야 하는 상태 판정은 account가 소유하고 Community는 `ANONYMOUS`를 `탈퇴한 사용자`로
표시합니다. Community 응답에는 account UUID, profile URL이나 활동 목록을 노출하지 않습니다.

### D7. Reaction 무결성과 count

- 배경: 한 사용자는 콘텐츠마다 좋아요 또는 싫어요 중 하나만 선택할 수 있고 변경·취소가
  동시에 발생해도 count가 중복되면 안 됩니다.
- 검토한 선택지: 별도 like/dislike table, counter column 선증가, unique choice row와 query
  aggregate
- 선택: `(content_id, account_public_id)` unique row 하나에 `LIKE` 또는 `DISLIKE`를 저장하고
  선택 변경은 upsert, 취소는 delete로 처리합니다. 초기 count는 read query가 집계합니다.
- 선택 근거: DB unique가 사용자당 한 선택을 보장하고 source row와 count drift를 만들지
  않습니다.
- 비용: 큰 feed에서는 aggregate query 비용이 증가할 수 있습니다.
- 재검토 조건: 실제 query latency와 write volume이 projection 또는 counter cache를 요구합니다.
  이때 source reaction row를 진실로 유지하고 projection rebuild 경로를 함께 둡니다.

반응 변경은 content visibility 확인과 upsert/delete를 한 transaction에서 수행합니다.
동시 최초 선택은 unique conflict를 재조회·갱신으로 수렴시키며, API는 최종 사용자 선택과 두
count를 반환합니다.

### D8. 신고와 관리자 처리

- 배경: 신고 접수와 콘텐츠 visibility는 다른 생명주기이며, 후속 제재가 Community 핵심을
  오염시키면 안 됩니다.
- 검토한 선택지: Community table에 `reported` flag, moderation case 별도 module, 외부 운영
  도구로만 처리
- 선택: moderation이 report case를 소유하고 Community public API를 통해 target 존재·표시
  가능 여부를 확인하고 숨깁니다.
- 선택 근거: 현재는 최소 `OPEN -> CONTENT_HIDDEN | DISMISSED`만 두되 후속 제재·appeal을 같은
  module에 추가할 수 있습니다.
- 비용: moderation과 Community의 원자적 public call 계약이 필요합니다.
- 재검토 조건: 전문 moderation SaaS, 자동 분류나 법적 case 보존이 도입됩니다.

- 같은 reporter와 target에는 open report 하나만 허용합니다. 종결 뒤에도 콘텐츠가 계속
  공개돼 있거나 수정됐다면 다시 신고할 수 있으므로 unique 범위는 open row에만 적용합니다.
- report에는 자유 입력 사유를 받지 않습니다. 내부 UUID, target kind, reporter UUID, 상태,
  생성·종결 시각, 처리 관리자 UUID와 resolution만 저장합니다.
- 관리자는 report를 `DISMISSED`로 종결하거나 `CONTENT_HIDDEN`으로 종결할 수 있습니다.
- `CONTENT_HIDDEN` resolution은 Community 숨김과 report 종결을 같은 DB transaction으로
  처리하고, target의 다른 open report도 같은 결과로 종결합니다.
- 관리자 응답은 원문을 검토할 수 있지만 application log와 일반 공개 API에는 report 원문과
  관리자 identity를 노출하지 않습니다.
- 숨김 취소, 계정 제재, 사유 taxonomy와 appeal은 현재 상태 기계에 넣지 않습니다.

### D9. 서비스 안 알림의 원자성과 중복 방지

- 배경: 댓글 저장은 성공했는데 알림이 사라지거나, 한 동작이 같은 수신자에게 두 알림을 만들면
  안 됩니다. 현재 email sender는 inbox를 소유하지 않습니다.
- 검토한 선택지: Community 내부 알림 table, 비동기 event만 발행, notification module의 동기
  inbox 공개 계약
- 선택: notification module에 inbox aggregate와 `CommunityConversationNotificationStore`
  공개 계약을 추가하고 댓글·답글 생성 transaction 안에서 호출합니다.
- 선택 근거: 외부 provider 호출 없이 같은 PostgreSQL transaction으로 대화와 inbox row를
  원자적으로 저장하며 기존 notification 책임을 채널 중립적인 알림 lifecycle로 확장합니다.
- 비용: 현재 modular monolith transaction에 결합됩니다.
- 재검토 조건: module을 별도 database/service로 분리하거나 알림 throughput이 core write를
  방해합니다. 그때 transactional outbox와 idempotent consumer로 경계를 이동합니다.

수신자 계산은 다음과 같습니다.

| 생성 종류 | 후보 수신자 |
| --- | --- |
| 최상위 댓글 | post 작성자 |
| reply | direct parent 작성자 |

- actor와 candidate가 같으면 만들지 않습니다.
- 동일 source comment UUID와 recipient UUID에는 inbox row 하나만 허용합니다. 따라서 direct
  parent 작성자와 post 작성자가 같은 사용자여도 같은 생성 동작으로 중복되지 않습니다.
- notification은 `COMMUNITY_COMMENT` 또는 `COMMUNITY_REPLY`, recipient, actor, source comment
  UUID, post UUID, `created_at`, nullable `read_at`만 저장합니다.
- Community table FK는 만들지 않습니다. 숨김·삭제 뒤 inbox에는 일반화된 문구와 stable post
  route만 남고, route가 더 이상 공개되지 않으면 detail은 `404`가 됩니다.
- 이메일·push 발송 상태를 inbox row에 섞지 않습니다. 후속 채널은 inbox 생성 사실을 source로
  별도 delivery lifecycle을 가집니다.

### D10. HTTP API와 접근 제어

- 배경: 공개 읽기와 보호 쓰기를 같은 resource path에 제공하면서 향후 board를 추가해야
  합니다.
- 검토한 선택지: `/community` command endpoint, board를 query로만 표현, board/post/comment
  resource URI
- 선택: 아래 resource-oriented `/api/v1` 계약을 사용합니다.
- 선택 근거: 현재 `free` board identity를 보존하고 post/comment lifecycle, reaction과 report를
  각 target에 명시합니다.
- 재검토 조건: API 전체 versioning 또는 GraphQL/BFF가 공개 resource owner를 인수합니다.

#### 공개 읽기

| Method | Path | 결과 |
| --- | --- | --- |
| `GET` | `/api/v1/boards/free/posts` | 최신 post page |
| `GET` | `/api/v1/posts/{postId}` | post detail과 count |
| `GET` | `/api/v1/posts/{postId}/comments` | 최신 top-level comment page |
| `GET` | `/api/v1/comments/{commentId}/replies` | root 아래 reply 시간순 page |
| `GET` | `/api/v1/media/images/{mediaId}/content` | 공개 usage가 있는 READY image를 application이 authorization 뒤 stream |

위 GET만 public allow-list에 명시합니다. 숨김·삭제되어 context가 없는 resource, 존재하지 않는
UUID와 비공개 media는 `404`로 통일합니다.

#### 일반 보호 쓰기

| Method | Path | 핵심 입력·결과 |
| --- | --- | --- |
| `POST` | `/api/v1/boards/free/posts` | `Idempotency-Key`와 v1 body block으로 post 생성, `201` |
| `PATCH` | `/api/v1/posts/{postId}` | owner가 body 수정, `200` |
| `DELETE` | `/api/v1/posts/{postId}` | owner 삭제, 반복 호출 포함 `204` |
| `POST` | `/api/v1/posts/{postId}/comments` | `Idempotency-Key`, `parentCommentId` nullable, comment/reply 생성, `201` |
| `PATCH` | `/api/v1/comments/{commentId}` | owner가 text 수정, `200` |
| `DELETE` | `/api/v1/comments/{commentId}` | owner 삭제, 반복 호출 포함 `204` |
| `PUT` | `/api/v1/posts/{postId}/reaction` | `LIKE` 또는 `DISLIKE` 선택·변경, `200` |
| `DELETE` | `/api/v1/posts/{postId}/reaction` | 내 선택 취소, `204` |
| `PUT` | `/api/v1/comments/{commentId}/reaction` | comment/reply 선택·변경, `200` |
| `DELETE` | `/api/v1/comments/{commentId}/reaction` | 내 선택 취소, `204` |
| `POST` | `/api/v1/posts/{postId}/reports` | report 접수, `201` |
| `POST` | `/api/v1/comments/{commentId}/reports` | report 접수, `201` |
| `POST` | `/api/v1/media/images` | `Idempotency-Key`와 bounded multipart image stream 접수, `202` |
| `GET` | `/api/v1/media/images/{mediaId}` | 본인 image 검증 상태 조회, `200` |
| `GET` | `/api/v1/notifications` | 내 inbox page |
| `PATCH` | `/api/v1/notifications/{notificationId}` | 내 notification `read=true`, `200` |

post/comment 수정은 response `ETag`의 version을 `If-Match`로 요구하고 stale version은
`412 Precondition Failed`로 처리합니다. 삭제는 body를 덮어쓰지 않고 독립
`author_deleted_at`만 설정하므로 `If-Match`를 요구하지 않습니다. 관리자 숨김 뒤의 작성자
삭제와 같은 작성자의 반복 DELETE도 `204`로 수렴합니다. Reaction, report와 notification read는
자체 unique·멱등 계약을 사용하므로 content version을 소비하지 않습니다.

Post·comment/reply 생성의 `Idempotency-Key`는 UUID이며 actor와 operation kind에 결속합니다.
Server는 정규화 request hash와 생성 content UUID를 영구 저장합니다. 같은 actor가 같은 key와
같은 payload를 재전송하면 최초 status와 resource를 반환하고, 다른 payload나 operation에 key를
재사용하면 `409`로 거부합니다. 따라서 response 유실 뒤 재시도도 content와 inbox 알림을 하나만
만듭니다. Media upload도 owner·key·declared metadata·checksum hash를 같은 방식으로 보존하고
재시도는 media UUID의 현재 상태로 멱등 처리합니다.

#### 관리자

| Method | Path | 결과 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/community/reports` | open/closed report cursor page |
| `GET` | `/api/v1/admin/community/reports/{reportId}` | report와 검토용 target snapshot |
| `PATCH` | `/api/v1/admin/community/reports/{reportId}` | `HIDE_CONTENT` 또는 `DISMISS` resolution |
| `PATCH` | `/api/v1/admin/media/jobs/{jobId}` | `DEAD` job을 `RETRY` 또는 object 부재 확인 뒤 `RESOLVED`로 종결 |

모든 오류는 현재 RFC 9457 Problem Details와 project error code를 사용합니다. 외부 식별자 형식,
권한 부족, state conflict, media 미준비·소유 불일치와 stale version을 서로 다른 code로
구분하되 내부 DB id, object key와 account 상태를 노출하지 않습니다.

### D11. Pagination과 read model

- 배경: post, comment, reply, notification과 report는 무제한 목록을 반환할 수 없고 grouped
  reply의 정렬을 안정적으로 유지해야 합니다.
- 검토한 선택지: offset page, 하나의 거대한 nested response, resource별 keyset cursor
- 선택: `(created_at, internal_id)`를 tie-breaker로 하는 opaque keyset cursor를 사용합니다.
  post/top-level comment는 내림차순, reply는 오름차순입니다.
- 선택 근거: 앞쪽 insert와 삭제가 있어도 offset drift를 줄이고 현재 고정 정렬을 직접
  표현합니다.
- 비용: 임의 page number 이동을 제공하지 않습니다.
- 재검토 조건: 관리자 임의 page 탐색이나 검색 engine이 별도 pagination 계약을 요구합니다.

기본/최대 page size는 post·top-level comment·notification·report `20/50`, reply `50/100`으로
시작하며 server configuration이 소유합니다. Cursor는 정렬 종류, `created_at`, internal ID와
version을 서명한 opaque token으로 감싸 client가 내부 ID를 보거나 의존하지 못하게 합니다.
Query predicate와 index는 항상 같은 `(created_at, internal_id)` 순서를 사용합니다.

변경 use case는 Domain model과 Port를 사용하고, 공개 목록·상세는 필요한 column만 읽는
projection을 허용합니다. 한 page의 author display, 내 reaction과 reaction count는 각각 batch
조회하며 JPA lazy loading으로 response를 조립하지 않습니다.

### D12. Database schema와 무결성

새 table은 단수형, identity internal PK, UUID `public_id`, `TIMESTAMPTZ`, `VARCHAR + CHECK`와
명시적 FK/index라는 현재 PostgreSQL 규칙을 따릅니다.

#### Community 소유

| table | 핵심 column·constraint |
| --- | --- |
| `community_board` | `id`, `public_id`, unique `slug`, `name`, `created_at`; `free` seed |
| `community_content` | `id`, `public_id`, `kind POST|COMMENT`, `account_public_id`, `board_id`, `post_content_id`, `parent_comment_id`, `thread_root_comment_id`, immutable `creation_request_id`·`creation_request_hash`, `edited_at`, `author_deleted_at`, `moderator_hidden_at`, `created_at`, `updated_at`, `version`; kind별 nullable shape와 timestamp check, `(account_public_id, creation_request_id)` unique |
| `community_post` | `content_id` PK/FK, `body_schema_version=1`, `body JSONB`; payload만 소유 |
| `community_comment` | `content_id` PK/FK, `body`; payload만 소유 |
| `community_reaction` | `content_id` FK, `account_public_id`, `reaction_type`, timestamp; `(content_id, account_public_id)` unique |

`community_content`의 kind shape check는 POST일 때 board만, COMMENT일 때 post를 필수로 하고
top-level COMMENT의 parent/root는 모두 null, reply의 parent/root는 모두 non-null로 제한합니다.
Self FK는 parent/root/post row 존재를 보장합니다. Flyway가 만드는 deferrable constraint
trigger는 commit 시점에 post target이 실제 POST인지, parent/root가 같은 post의 COMMENT인지,
root가 top-level인지, `community_post`·`community_comment` subtype이 content kind와 정확히
일치하는지를 검증합니다. Application은 같은 규칙을 먼저 검증해 domain error를 반환하지만
trigger가 우회 write와 race의 최종 backstop입니다. Parent/root/post와 creation request 값은
생성 후 갱신하지 않습니다.

주요 index는 다음 query 모양을 소유합니다.

- post: `(board_id, created_at DESC, id DESC)` where kind is POST
- top-level comment: `(post_content_id, created_at DESC, id DESC)` where kind is COMMENT and parent is null
- reply: `(thread_root_comment_id, created_at ASC, id ASC)` where kind is COMMENT and root is not null
- reaction aggregate: `(content_id, reaction_type)`
- author-owned mutation lookup: `(account_public_id, public_id)`
- context 유지 판단: comment의 parent/root/post FK index

#### Moderation 소유

| table | 핵심 column·constraint |
| --- | --- |
| `moderation_report` | `public_id`, `target_content_public_id`, `target_kind`, `reporter_account_public_id`, `status OPEN|CONTENT_HIDDEN|DISMISSED`, `resolved_by_account_public_id`, `created_at`, `resolved_at`, `version` |

같은 reporter/target의 open 중복은 `status = 'OPEN'` partial unique index로 막고, open report
조회는 `(status, created_at, id)` index를 사용합니다. Community table FK를 만들지
않습니다.

#### Notification 소유

| table | 핵심 column·constraint |
| --- | --- |
| `notification_inbox` | `public_id`, `recipient_account_public_id`, `actor_account_public_id`, `type`, `source_content_public_id`, `post_public_id`, `created_at`, `read_at`; `(recipient_account_public_id, source_content_public_id, type)` unique |

recipient inbox index는 `(recipient_account_public_id, created_at DESC, id DESC)`이고 unread
query를 위해 `read_at IS NULL` partial index를 둡니다.

#### Media 소유

| table | 핵심 column·constraint |
| --- | --- |
| `media_asset` | `public_id`, owner UUID, immutable idempotency key·request hash, `status`, staging/delivery key, expected/detected media type, byte/pixel/checksum metadata, reservation expiry, timestamps, version; `(owner, idempotency_key)` unique |
| `media_usage` | asset FK, `consumer_type POST_BODY`, consumer public UUID, `status ACTIVE|HIDDEN_RETAINED|RELEASED`, timestamps; `status <> 'RELEASED'`인 asset당 하나의 partial unique index |
| `media_job` | asset FK, `type VERIFY|DELETE_STAGING|DELETE_DELIVERY|RECONCILE`, `status READY|RUNNING|RETRY|DONE|DEAD|RESOLVED`, attempt, lease, next attempt, resolution, resolved actor/time, non-sensitive error code, timestamps, version |
| `media_reconciliation_issue` | object/asset reference, issue type, `OPEN|RESOLVED`, first/last seen, resolution과 actor/time; 같은 불일치의 open 중복 방지 |

Object key는 API·log에 노출하지 않고 asset UUID와 무관한 random component를 포함합니다.
`media_job` worker는 `FOR UPDATE SKIP LOCKED`로 replica 간 job claim을 직렬화합니다. Media claim은
asset row를 lock하고 non-released usage partial unique index가 동시 이중 claim을 최종
차단합니다.

### D13. Transaction과 concurrency

| 경계 | 같은 DB transaction에 반드시 포함되는 효과 |
| --- | --- |
| post 생성·수정 | content/post 저장, READY media 소유·중복·usage claim 검증, 제거한 usage를 `RELEASED`로 전환하고 cleanup job insert |
| comment/reply 생성 | content/comment 저장, parent/root 검증, recipient 계산, inbox insert |
| content 수정·삭제 | 수정은 owner와 version, 삭제는 owner·현재 삭제 사실 검증, edited/delete timestamp, 작성자 삭제의 media usage `RELEASED` 전환과 cleanup job insert |
| reaction | target visibility 확인, unique choice upsert/delete |
| report | target visibility 확인, duplicate 방지 report insert |
| report resolution | 관리자·report version 검증, Community hide, 관련 open report 종결, media usage를 `HIDDEN_RETAINED`로 전환 |

DB transaction 안에서는 S3, email, push와 HTTP 같은 외부 I/O를 호출하지 않습니다.
Notification inbox 저장은 외부 전송이 아닌 같은 datasource의 DB insert이므로 transaction에
참여합니다.

- content 수정과 report resolution은 optimistic version을 사용합니다. Author delete와
  moderator hide는 서로 다른 timestamp를 row lock 아래 멱등 설정해 어느 순서에서도 두 사실을
  보존합니다.
- reaction unique conflict, notification dedupe conflict와 report duplicate는 무결성 위반을
  public idempotent 결과 또는 명시적 `409`로 변환합니다.
- parent/root와 hide target은 읽고 검증하는 동안 필요한 row를 lock해 삭제·숨김과 새 reply가
  서로 통과하지 않게 합니다.
- job lease가 만료되면 다른 worker가 다시 claim할 수 있고, S3 put/delete는 deterministic key와
  상태 확인으로 idempotent하게 재실행합니다.

### D14. Image upload, 검증과 전달

- 배경: direct presigned upload는 application binary traffic을 줄이지만 최초 범위에서 업로드
  크기를 object storage 수신 전에 강제하기 어렵고 bearer URL·CORS·별도 completion 계약을
  추가합니다. Public bucket은 검증 전 원본과 삭제 책임을 노출합니다.
- 검토한 선택지: application multipart proxy, public S3 upload, private direct upload와
  staged verification
- 선택: 인증된 사용자의 최대 10 MiB multipart body를 application이 메모리에 전부 적재하지
  않고 bounded stream으로 받아 private S3 staging key에 씁니다. 그 뒤 DB job이 검증·
  재인코딩한 delivery object만 사용합니다. Public delivery도 application-authorized streaming을
  사용합니다.
- 선택 근거: 첫 범위의 작은 상한에서는 request 시작부터 byte 수를 차단하고 client에게 S3
  credential·bearer URL·object key와 CORS를 노출하지 않는 이득이 application network 비용보다
  큽니다. Storage Port는 후속 direct upload adapter를 허용합니다.
- 비용: upload와 download byte가 application Pod를 통과하므로 concurrency, timeout과
  bandwidth를 제한해야 합니다.
- 재검토 조건: 이미지 처리량이 application replica 자원을 방해해 별도 worker가 필요하거나
  CDN이 latency·비용 목표를 갖습니다.

#### 허용 형식과 안전 제한

- v1은 static JPEG와 PNG만 허용합니다. GIF, SVG, WebP, HEIC, 영상과 일반 파일은
  거부합니다.
- client file name, extension과 `Content-Type`은 신뢰하지 않습니다. 실제 byte signature와
  decode 결과를 기준으로 판정합니다.
- 한 image 최대 10 MiB, 24 megapixel, 한 post 최대 10개, 전체 TEXT 20,000자, comment/reply
  2,000자로 시작합니다. 값은 configuration과 API validation이 같은 source를 사용합니다.
- Server는 multipart part를 Pod의 size-bounded `/tmp` file로 받으면서 10 MiB를 넘는 즉시
  중단하고 실제 byte length와 SHA-256을 계산합니다. 그 뒤 known-length file을 S3
  `PutObject`의 SHA-256 checksum과 DB metadata에 결속하고 `finally` 경계에서 temporary file을
  삭제합니다. Declared file name과 `Content-Type`은 검증 근거가 아닙니다.
- worker는 bounded concurrency `1`로 header·size·pixel 제한을 먼저 확인하고 decode합니다.
  Orientation을 적용한 뒤 metadata를 버리고 JPEG/PNG로 다시 encode합니다.
- staging 원본은 절대 public read 대상이 아니며 검증 성공·실패 뒤 삭제 job 대상입니다.

#### Media 상태

```text
RESERVED -> UPLOADED -> VERIFYING -> READY -> DELETE_PENDING -> DELETED
                    \-> REJECTED --------------/
```

- 첫 짧은 transaction은 idempotent `RESERVED` row와 random staging key를 만듭니다. Request
  stream의 S3 write는 transaction 밖에서 수행하고, 성공 뒤 두 번째 transaction이 object
  metadata/checksum을 대조해 `UPLOADED`와 VERIFY job을 기록한 후 `202`를 반환합니다. S3 성공
  뒤 DB 실패로 생긴 object는 reconciliation이 같은 reservation에 수렴시킵니다.
- post는 owner가 같고 `READY`, non-released usage가 없는 media만 claim할 수 있습니다.
- 미완료 예약은 15분, READY 미사용 asset은 24시간 뒤 정리합니다. Staging prefix에는 DB worker가
  실패해도 장기 orphan을 남기지 않는 2일 lifecycle expiration을 둡니다.
- 공개 media endpoint는 매 요청마다 READY와 `ACTIVE` public usage를 확인한 뒤 private delivery
  object를 application이 bounded stream으로 전달합니다. Response는 `Cache-Control: no-store`를
  사용하고 S3 URL과 object key를 노출하지 않습니다. Post JSON에는 이 stable application URL을
  넣습니다.
- post에서 image가 제거되거나 작성자가 삭제하면 usage를 `RELEASED`로, 관리자가 숨기면
  `HIDDEN_RETAINED`로 먼저 바꾸므로 그 뒤 시작하는 public 요청은 즉시 `404`가 됩니다. 이미
  client가 내려받은 byte까지 회수할 수 있다는 의미로 확대하지 않습니다.
- `RELEASED` usage만 delivery cleanup 대상입니다. `HIDDEN_RETAINED` object는 private 상태로
  보존하고 현재 범위에서는 다시 공개하지 않습니다. 후속 appeal·보존 기간 정책은 이 상태에서
  복원 또는 파기를 결정할 수 있으며, 별도 정책 전에는 숨김을 영구 삭제로 확대하지 않습니다.
- delivery prefix는 lifecycle로 임의 만료하지 않고 DB usage와 DELETE job만 owner로 둡니다.
  Bucket versioning은 현재 끄고 server-side encryption과 public access block을 요구합니다.

### D15. S3 workload identity와 권한

- 배경: 현재 node instance profile은 node platform 권한만 소유하고 IMDS hop limit `2` 때문에
  Pod가 node credential에 접근할 가능성이 있습니다. 여기에 S3 권한을 추가하면 모든 workload의
  권한 표면이 넓어집니다.
- 검토한 선택지: node role에 S3 추가, long-lived access key를 Kubernetes Secret에 저장,
  Kubernetes ServiceAccount OIDC와 AWS STS web identity
- 선택: self-managed Kubernetes의 ServiceAccount issuer를 IAM OIDC provider로 등록하고,
  application 전용 projected token이 `AssumeRoleWithWebIdentity`로 media 전용 IAM role의 임시
  credential을 얻습니다.
- 선택 근거: node ECR/LB 권한과 application object 권한을 분리하고 장기 AWS access key를
  만들지 않습니다.
- 비용: 공개 HTTPS OIDC discovery/JWKS, issuer key rotation, STS network와 projected token
  운영이 새 infrastructure 계약이 됩니다.
- 재검토 조건: EKS Pod Identity/IRSA를 제공하는 managed cluster로 이동하거나 AWS가 아닌
  storage provider를 선택합니다.

보안 계약은 다음과 같습니다.

- trust policy는 exact issuer, `aud=sts.amazonaws.com`,
  `sub=system:serviceaccount:car-community:car-community-application`만 허용합니다.
- Pod 전체 자동 mount는 계속 끄고 media adapter container에 audience·TTL이 제한된 projected
  token만 mount합니다.
- SDK는 default chain으로 node instance profile에 fallback하지 않고 web identity token file과
  role ARN을 명시한 provider를 사용해 설정 오류를 fail-close합니다.
- role permission은 exact media bucket의 staging/delivery prefix에 필요한 object read/write/
  delete와 prefix가 제한된 reconciliation list만 허용합니다. Bucket policy·lifecycle·KMS/IAM
  관리 권한은 application role에 주지 않습니다.
- Local·test credential은 production role trust에 들어오지 않으며 secret 값을 repository,
  log, API와 문서에 남기지 않습니다.
- OIDC issuer, IAM role과 private bucket 경계가 검증되기 전에는 Community capability를 public으로
  열지 않습니다.

Issuer lifecycle은 다음을 고정합니다.

- Media bucket과 분리된 versioned identity-metadata S3 bucket의 virtual-hosted HTTPS URL을
  stable issuer로 사용합니다. Bucket name과 region은 issuer가 활성인 동안 바꾸지 않습니다.
  Public bucket policy는 `/.well-known/openid-configuration`과 `/openid/v1/jwks` 두 object의
  `GetObject`만 허용하고 list·write는 운영 identity에 한정합니다. Account-level public block과
  bucket-level 설정은 이 두 공개 object만 실제로 읽을 수 있게 대조하며, private signing key는
  node PKI 밖으로 내보내지 않습니다.
- 기존 issuer는 즉시 교체하지 않습니다. Kubernetes API server는 old/new issuer를 함께
  검증하는 overlap 상태로 먼저 들어가고 new issuer를 발급 issuer로 전환한 뒤, 최대 projected
  token TTL과 STS 허용 clock skew가 지난 뒤에만 old issuer를 제거합니다.
- Signing key 회전도 new public key를 JWKS와 API server verifier에 먼저 게시하고, 새 key로
  발급을 전환하고, 기존 token 최대 TTL 이후 old key를 제거하는 순서를 지킵니다. JWKS는
  rotation 동안 두 key를 함께 제공합니다.
- Discovery/JWKS 또는 STS가 실패하면 SDK가 node credential로 fallback하지 않고 media 예약·
  검증·stream을 실패시킵니다. Community capability health는 이 실패를 공개 준비 상태로
  간주하지 않습니다.
- Rollback은 old issuer를 발급 우선순위로 되돌리되 new issuer, key와 IAM provider를 new token
  최대 TTL 전에는 삭제하지 않습니다. OIDC resource 제거는 application rollback과 분리합니다.

### D16. Security, privacy와 abuse 경계

- 공개 GET과 image stream만 anonymous 접근을 허용하고 나머지 API는 현재 account policy를
  그대로 사용합니다.
- owner 변경은 JWT `sub`의 account public UUID와 content owner를 비교하고 request body의
  account ID를 신뢰하지 않습니다.
- 관리자 report API는 현재 admin 공통 접근 정책을 사용합니다. 일반 사용자가 content UUID를
  알아도 숨김 상태와 report 처리 정보를 조회할 수 없습니다.
- plain text와 typed block만 허용하고 response를 HTML로 조립하지 않습니다. SVG와 검증 전
  object를 제공하지 않습니다.
- Body, 표시 이름, report 대상 원문, checksum, object key와 AWS credential은
  application log에 남기지 않습니다. 운영 log는 request ID, actor hash 또는 public UUID,
  use case, result, error code와 duration만 사용합니다.
- Anonymous read와 image stream은 비활성화할 수 없는 application origin 보호 baseline을
  사용합니다. 현재 replica `1`에서는 client key당 일반 공개 GET `60/minute`·burst `20`,
  image GET `10/minute`·burst `4`, process 전체 image stream 동시 `8`개와 전달량
  `64 MiB/minute`를 상한으로 두고 초과 요청은 `Retry-After`가 있는 `429`로 거부합니다.
  Client key는 exact ALB ingress에서 append된 trusted `X-Forwarded-For` chain과 직접 peer를
  함께 검증해 만들며 임의 요청 header를 그대로 신뢰하지 않습니다.
- 이 limiter는 공개 read use case 앞의 Port로 두어 delivery adapter를 교체할 수 있게 합니다.
  현재 local limiter가 deployment 전체 상한이라는 전제는 replica `1`일 때만 참이므로
  capability는 ready application replica가 하나가 아니면 public endpoint를 fail-close합니다.
  다중 replica 전환은 같은 Port 뒤에 공유 또는 edge limiter를 두고 deployment 전체 상한과
  origin 우회 차단을 검증한 뒤에만 허용합니다. Edge 후보는
  [004 Infrastructure 진화 backlog](../../backlog/2026/004-infrastructure-evolution.md)에 남지만
  현재 공개의 선행 owner로 사용하지 않습니다. 현재 비용 상한은 위 요청·동시성·전달량
  제한이 소유하며 현재 activation은 별도 비용 결정에 의존하지 않습니다.
- Account UUID는 내부 협력에만 사용하고 public read response에는 표시 이름 외 작성자
  identifier를 내보내지 않습니다.

### D17. Failure, recovery와 운영 관측

- DB 저장 실패는 해당 use case 전체를 rollback합니다. S3 실패는 Community transaction을
  rollback시키지 않고 media job을 `RETRY`로 남깁니다.
- Worker는 exponential backoff와 최대 attempt 뒤 `DEAD`로 전환합니다. Media module이
  `DEAD`와 reconciliation issue의 canonical owner이며, 일반 API 성공이나 완료 metric으로
  숨기지 않습니다.
- READY DB update 전에 delivery put이 성공해 orphan object가 생겨도 deterministic key로 retry가
  수렴하고 periodic reconciliation이 DB asset/usage/job과 staging/delivery prefix 차이를
  탐지하고 durable `media_reconciliation_issue`를 생성·갱신합니다.
- 관리자는 최신 job `ETag`를 `If-Match`로 제출할 때만 `DEAD -> RETRY`로 requeue할 수
  있습니다. Delete/reconcile job은 object HEAD/LIST로
  부재를 확인한 경우에만 `RESOLVED`로 종결할 수 있고, non-released usage가 있거나 object
  존재를 확인하지 못하면 종결을 거부합니다. Verify job의 비복구 입력은 asset을 `REJECTED`로 전환하고
  staging cleanup job까지 저장한 뒤 `RESOLVED`로 닫습니다.
- Requeue·resolution은 actor UUID, 이전/새 상태, 시각과 non-sensitive resolution code를
  남기며 같은 요청을 멱등 처리합니다. Open reconciliation issue는 대응 job이 `DONE` 또는
  검증된 `RESOLVED`가 될 때만 함께 닫습니다.
- Inbox insert가 실패하면 comment/reply도 rollback되므로 알림 없는 성공 대화를 만들지
  않습니다.
- 표시 이름 batch 조회나 DB가 실패하면 content를 임의 `익명`으로 성공 응답하지 않고 공통
  server error로 실패시킵니다. `ANONYMOUS`는 account가 명시적으로 반환한 공개 상태에만
  사용합니다.

기존 Actuator/Micrometer와 structured logging 기반에 다음 신호를 추가합니다.

| 영역 | metric·event |
| --- | --- |
| Community | use case latency/result, post/comment/reply create, reaction conflict, visibility transition |
| Media | asset state count, verify duration/result, retry/dead job, orphan reconciliation, upload/stream failure |
| Notification | inbox insert/dedup, unread query latency |
| Moderation | open report count/age, resolution result |

현재 중앙 metric 수집·alert가 구현됐다고 가정하지 않습니다. `DEAD` job과 오래된 open report를
무인 운영에서 자동 통지하려면 infrastructure 관측성 backlog와 합류해야 합니다.

### D18. Migration, 공개 전환과 rollback

- 새 table, constraint, index와 `free` board seed는 append-only Flyway migration으로 만듭니다.
  기존 account/auth table을 Community 편의를 위해 변경하거나 공유 FK를 추가하지 않습니다.
- Runtime role에는 새 table DML만 부여하고 schema DDL은 기존 migration job이 소유합니다.
- Community endpoint는 schema, media bucket, OIDC identity·overlap/rollback, worker와 DEAD
  recovery, replica 수와 anonymous rate·egress 상한 capability가 모두 준비됐을 때만 활성화합니다.
  Image 없는 부분 기능을 먼저 공개해 현재 제품 수용 기준을 축소하지 않습니다.
- Community migration 전에 대상 DB의 V7·V10 checksum과 `auth_event.event_type` 값 분포가
  현재 source와 일치해 기존 결함의 영향을 받지 않음을 증명하거나,
  [001 Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md)의
  지속 DB migration 호환성 완료 조건을 충족해야 합니다. 과거 checksum이나 legacy event가
  있으면 PreSync validation을 우회·repair하지 않고 Community rollout을 중단합니다.
- Application rollback은 새 table과 object를 삭제하지 않습니다. 이전 image는 현재 schema와
  Flyway history를 소비할 수 있음이 검증된 경우에만 rollback 후보입니다. 그렇지 않으면
  Community capability와 worker mutation을 끈 채 forward fix하며, 재배포가 기존 job을
  idempotent하게 이어받습니다.
- Migration rollback을 위해 이미 저장된 콘텐츠나 object를 자동 삭제하지 않습니다. Schema
  제거는 데이터 영향과 보존 결정을 가진 별도 destructive migration으로만 수행합니다.
- OIDC·IAM·bucket resource는 application rollback만으로 삭제하지 않습니다. Resource 제거는
  active media usage와 object inventory가 0임을 별도로 증명해야 합니다.

### D19. 도구와 외부 시스템 경계

- 현재 PostgreSQL, Flyway, Spring Data JPA, Spring Security, Modulith, Actuator와 Kubernetes
  deployment model을 유지합니다.
- S3 adapter는 AWS SDK for Java 2.x의 S3 client와 STS web identity 지원을 사용합니다. SDK
  타입은 `media.internal.adapter.out.storage` 밖으로 노출하지 않습니다.
- JPEG/PNG 검증·재인코딩은 Java ImageIO의 실제 decode 결과를 사용합니다. 새 format은 decoder
  보안·ARM64 호환·resource 상한을 검증한 별도 adapter 결정 없이 MIME allow-list만 늘리지
  않습니다.
- 현재 범위에는 Redis, message broker, search engine, CDN, antivirus SaaS와 별도 worker
  deployment를 도입하지 않습니다. DB job과 bounded worker가 현재 신뢰성 요구를 충족하지
  못한다는 측정값이 생기면 각각 독립적으로 재검토합니다.

## API payload 핵심 계약

### Post write

```json
{
  "body": {
    "version": 1,
    "blocks": [
      { "type": "TEXT", "text": "본문" },
      { "type": "IMAGE", "mediaId": "c860...", "alt": "설명" }
    ]
  }
}
```

Response는 post UUID, board slug, body, effective visibility, `createdAt`, nullable `editedAt`,
author display, like/dislike count와 인증 사용자의 선택을 분리해 반환합니다. Public response는
internal PK와 account UUID를 포함하지 않습니다.

### Comment write

```json
{
  "body": "plain text",
  "parentCommentId": "nullable direct parent UUID"
}
```

`parentCommentId=null`이면 top-level, 값이 있으면 reply입니다. Response는 `threadRootCommentId`,
`parentCommentId`와 표시 가능한 `replyTo`를 포함하되 UI depth는 항상 `0` 또는 `1`입니다.

### Reaction write

```json
{ "type": "LIKE" }
```

`PUT`은 같은 값 재요청에도 최종 상태를 동일하게 유지합니다. `DISLIKE`로 바꾸면 row를 추가하지
않고 선택을 교체합니다.

### Moderation resolution

```json
{ "resolution": "HIDE_CONTENT" }
```

현재 다른 허용값은 `DISMISS`뿐이며 자유 입력 사유·제재 수위는 받지 않습니다.

## 확장 증명

| 후속 기능 | 현재 구조에서의 추가 방식 | 기존 계약을 교체하지 않는 이유 |
| --- | --- | --- |
| 게시판 카테고리 | `community_board` row와 접근 정책 추가 | 기존 `free` slug와 post FK 유지 |
| 차량 연결 | 미래 차량 module이 post public UUID를 참조하는 relation을 소유 | Community core와 현재 post schema가 차량 FK에 의존하지 않음 |
| 다단 대화 UI | 저장된 parent chain으로 tree projection 추가 | 기존 root 기반 1단 projection을 계속 제공 가능 |
| 공개 profile | account가 author 표시 DTO에 public profile link를 additive 제공 | Content는 같은 account 관계와 표시 batch 계약 유지 |
| 새 reaction | reaction type과 별도 count/ranking projection 추가 | 사용자·target unique source row 유지 |
| 검색·인기·추천 | post TEXT와 reaction source를 소비하는 read projection 추가 | Core write와 현재 latest cursor 변경 불필요 |
| 영상·파일 | media type별 검증 adapter와 새 body block 추가 | 기존 IMAGE asset과 v1 post block 해석 유지 |
| 이메일·push | inbox source를 소비하는 delivery lifecycle 추가 | Community transaction과 기존 in-app read state 유지 |
| 계정 제재·appeal | moderation case에 actor·decision·appeal lifecycle을 추가하고 `HIDDEN_RETAINED` media를 복원 또는 정책에 따라 파기 | Community는 effective hide와 private 원본 관계를 유지 |
| CDN | stable media application URL 뒤 delivery/cache adapter 추가 | Post payload와 media UUID 유지. 숨김 직후 차단 요구를 유지하려면 cache purge·TTL 계약을 함께 재결정 |

차량 관계는 대상 vehicle identity와 관계 종류가 아직 확정되지 않았으므로 지금 nullable
`vehicle_id`나 범용 relation row를 미리 만들지 않습니다. 확장 가능성은 미사용 column이 아니라
안정된 post UUID, module 비의존성과 별도 relation owner로 보존합니다.

## 검증 가능성 계약

해결 설계가 구현 결과로 인정되려면 다음 관찰이 각각 독립적으로 증명 가능해야 합니다. 이
목록은 실행 순서가 아니라 최종 계약의 검증 축입니다.

- Module: Spring Modulith/ArchUnit에서 새 module cycle, 상대 `internal` 참조와 table 공유가
  없습니다.
- Domain: reply-to-reply가 direct parent와 root를 보존하면서 depth 1 응답으로 투영됩니다.
- Data: unique/FK/check, deferrable constraint trigger와 partial unique index가 reaction 중복,
  다른 post parent·invalid root, subtype 불일치, media 이중 usage와 notification 중복을
  차단합니다.
- Transaction: comment와 inbox, hide와 report resolution, post와 media usage 중 하나만
  commit되는 부분 성공이 없습니다.
- Concurrency: stale edit, 동시 reaction 교체, 동시 report와 reply/hide가 정해진 결과로
  수렴합니다.
- Security: anonymous는 공개 GET만 접근하고 full session/member/admin 경계가 현재 policy와
  일치합니다.
- Media: 위조 MIME, oversized/pixel bomb, SVG/GIF, checksum mismatch, 검증 전 원본과
  `HIDDEN_RETAINED|RELEASED` usage가 노출되지 않습니다.
- Recovery: worker 중단·재시작, S3 일시 실패와 중복 job 실행이 object/DB 상태를 잃거나 중복
  공개하지 않습니다.
- Query: 실제 PostgreSQL에서 cursor 경계, 정렬, tombstone과 batch 조회가 N+1·offset drift 없이
  동작합니다.
- Extension: 새 board, 새 reaction type과 vehicle relation spike가 기존 row migration이나
  public identifier 교체 없이 가능함을 schema/API review로 확인할 수 있습니다.

## 적용 설계 표면 판정

| 설계 표면 | 판정 | 닫힌 위치 |
| --- | --- | --- |
| 사용자·업무 | 적용 | 목표 불변식, visibility, 알림·신고 계약 |
| API | 적용 | D10, API payload 핵심 계약 |
| Domain | 적용 | D2~D9 |
| Data | 적용 | D12, D13 |
| 구조 | 적용 | D1, D19 |
| Security·privacy | 적용 | D14~D16 |
| 품질·성능 | 적용 | D11, D13, 검증 가능성 계약 |
| 운영·복구 | 적용 | D17, D18 |
| Tool·외부 시스템 | 적용 | D14, D15, D19 |
| 문서 | 적용 | 일반 문서는 제품 결과, 이 문서는 기술 목표, 구현 뒤 project/domain/schema SOT가 현재 사실을 각각 소유 |

적용 설계 표면에 활성 사용자 결정은 없습니다. API field 이름, class·method 이름, Flyway version,
exact IAM resource name과 환경별 값은 이 기술 계약을 소비하는 실행 계획이 source와 함께
결정할 구현 단위이며 제품 결과를 다시 여는 질문이 아닙니다.

## 관련 문서와 근거

- **제품 결과**: [016 차량 비연결 일반 커뮤니티](016-general-community.md)
- **제품 경계**: [100 제품 개요](../../../product/100-product-overview.md),
  [200 기능 범위](../../../product/200-feature-scope.md)
- **구조 기준**: [100 아키텍처 판단 원칙](../../../architecture/100-architecture-principles.md),
  [210 Spring Boot 모듈 구조](../../../architecture/210-spring-boot-module-structure.md),
  [220 경계와 매핑 규칙](../../../architecture/220-boundary-and-mapping-rules.md)
- **API 기준**: [230 REST API 설계](../../../architecture/230-rest-api-design.md),
  [110 Account 인증·인가 정책](../../../domain/110-account-auth-policy.md)
- **현재 project 사실**: [200 현재 프로젝트 아키텍처](../../../project/200-current-architecture.md),
  [210 모듈 지도](../../../project/210-module-map.md),
  [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md)
- **Data·운영 기준**: [250 PostgreSQL](../../../tech-stack/250-postgresql.md),
  [240 Flyway](../../../tech-stack/240-flyway.md),
  [280 Logging](../../../tech-stack/280-logging.md),
  [001 Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md)
- **외부 공식 근거**:
  [Kubernetes ServiceAccount issuer discovery](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/),
  [AWS IAM OIDC provider](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_providers_create_oidc.html),
  [AWS SDK for Java credential chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html),
  [S3 PutObject와 checksum](https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObject.html),
  [S3 lifecycle](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lifecycle-mgmt.html)
