# 차량과 분리해 시작하는 일반 커뮤니티 해결 설계

## 현재 적용 상태

| 항목 | 현재 상태 |
| --- | --- |
| 활성 계획 단계 | 실행 계획 작성 |
| 설계 전체 확정 상태 | 완료입니다. 기존 설계 기준선을 보존하고 D16·D18의 공개 보호·활성화 계약을 보완했습니다. |
| 결과 정의 | 게시글 제목, 목록 페이지 이동과 알림 수신자를 포함한 15개 제품 결정이 사용자 확정 상태로 닫혔습니다. |
| 개별 값 확정 상태 | D1~D19의 기술 계약은 Agent 기술 결정으로 닫혔습니다. D14·D19는 WebP를 첫 범위에서 제외하고 JDK ImageIO로 처리하는 JPEG·PNG·정적 GIF 입력 계약이며, D16·D18은 구현할 수 없던 replica 자동 판정과 `Recreate`를 제거한 계약입니다. |
| 기술 선택 기준선 | 제품 결과 15개는 사용자 확정값이고, 이를 실현하는 D1~D19는 source·대안·trade-off로 닫은 Agent 기술 결정입니다. 기술 선택을 사용자 확정으로 귀속하지 않습니다. |
| 보존과 보완 | 기존 결정과 영향받지 않은 설계는 유지하고, 제목·게시글 페이지 이동·대화 알림 수신자·Account 공개 UUID 관계와 첫 범위 미디어 3-table 결정에 직접 의존하는 계약만 보완했습니다. |
| 현재 확인 대상 | 사용자 확인 대상은 없습니다. 실행 계획은 명시적 replica `1`, `RollingUpdate`와 Pod별 local limiter 계약을 소비합니다. |
| 소비 gate | 해결 설계가 닫혔으므로 실행 계획을 작성할 수 있습니다. 구현은 검증된 `Planned` checkpoint 뒤에만 시작합니다. |

## 문서 경계

이 문서는 [016 차량 비연결 일반 커뮤니티](016-general-community.md)에서 확정한 15개 제품
결정을 구현 가능한 장기 기술 계약으로 변환합니다. 제품 범위, 제외 범위와 사용자 수용
기준은 일반 문서가 소유하고, 이 문서는 그 결과를 바꾸지 않는
module·domain·API·data·transaction·security·media·notification·moderation·운영 구조만
소유합니다.

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
  -> 같은 DB transaction에서 중복을 제거한 대상 사용자 inbox 알림 0개부터 2개 저장
  -> 반응은 사용자·콘텐츠당 한 선택만 유지
  -> 신고는 moderation case로 접수
  -> 관리자가 숨기면 원문 노출과 media 사용을 즉시 중단하고 report를 종결
  -> 작성자가 삭제하면 별도 삭제 사실을 남기고 하위 대화 유무에 따라 tombstone 또는 목록 제외
```

이 흐름에서 Community DB 상태, inbox 알림과 media 사용 관계는 같은 PostgreSQL transaction에
참여합니다. S3 검증·복사·삭제처럼 외부 I/O가 필요한 효과는 media module의 DB job으로
기록한 뒤 transaction 밖 worker가 처리합니다.

## 보존된 기술 선택 요약

아래 표는 Agent가 source·대안·trade-off로 닫은 기술 결정 기준선입니다. 제품 결과의 사용자
확정과 기술 선택을 구분합니다.

| 결정        | 기술 선택                                                                        | 보존하는 확장 경계                                    |
| --------- | ---------------------------------------------------------------------------- | --------------------------------------------- |
| 업무 module | `community`, `media`, `moderation` 신설과 기존 `notification`, `account` 공개 계약 확장 | 각 생명주기의 독립 변경과 후속 제재·채널 확장                    |
| 콘텐츠 공통 식별 | 게시글·댓글의 공통 상태 행과 종류별 본문 table 분리                                             | 반응·신고·숨김이 같은 content ID를 사용                   |
| 게시글 본문    | schema version이 있는 텍스트·이미지 JSON, v1은 `TEXT`·`IMAGE`                             | 서식·투표·embed를 새 version으로 도입하고 v1 reader 유지    |
| 대화        | 직접 답한 대상과 답글을 묶을 최초 댓글 동시 저장                                                 | 현재 1단 화면과 미래 다단 tree 공존                       |
| 삭제·숨김     | `author_deleted_at`과 `moderator_hidden_at` 별도 보존                             | 복구·이의제기 도입 시 원인과 선후 관계 유지                     |
| 작성자 표시    | account 공개 batch lookup으로 현재 표시 이름 조회                                        | profile 링크 추가 시 콘텐츠 schema 변경 불필요             |
| 반응        | 콘텐츠·사용자 unique choice와 `LIKE`·`DISLIKE`                                      | 새 reaction type 추가와 별도 ranking projection     |
| 신고        | moderation 소유 case와 Community 숨김 공개 계약                                       | 후속 계정 제재·감사·appeal을 moderation에 추가            |
| 서비스 안 알림  | notification 소유 inbox row를 대화 transaction에서 동기 저장하고 후보 수신자를 정책으로 정제        | 게시글별 알림 해제와 이메일·push를 후속 추가             |
| 이미지       | JPEG·PNG·정적 GIF 입력, JPEG·PNG 정규화, 미디어 3-table과 private S3·비동기 worker | 새 decoder·관리자 action·개별 정합성 case를 후속 추가 |
| 조회        | 게시글 번호 page와 대화·알림용 keyset cursor, author·반응 batch 조회                       | 검색·인기·추천 projection을 core write와 분리           |
| 배포 | additive Flyway, 기본 비활성 capability와 `replicas: 1`·`RollingUpdate` | application rollback 시 새 schema·object 보존, 상시 다중 replica 전 shared/edge limiter 재검토 |

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

### D2. 게시글과 댓글이 공유하는 식별 정보

게시글과 댓글은 본문 모양은 다르지만, 작성자·생성 시각·수정 여부·삭제·관리자 숨김·반응·
신고처럼 공통으로 관리할 정보가 많습니다. 그래서 화면에 보이는 콘텐츠 하나마다 먼저
`community_content` 행 하나를 만들고, 실제 본문만 종류에 맞는 table에 나눠 저장합니다.

```text
community_content #101 (종류: 게시글, 작성자·삭제·숨김 상태)
└── community_post #101 (게시글 본문)

community_content #205 (종류: 댓글, 작성자·삭제·숨김 상태)
└── community_comment #205 (댓글 본문)
```

- 배경: 게시글·댓글·답글에 같은 반응·신고·삭제 규칙을 적용하면서도 서로 다른 본문 구조를
  섞지 않아야 합니다.
- 검토한 선택지: 게시글과 댓글 table에 공통 field를 반복 저장, 종류와 ID만 적는 느슨한
  참조, 공통 식별 행과 종류별 본문 table 분리
- 선택: `community_content`가 공통 식별자와 상태를 소유하고, `community_post`와
  `community_comment`가 각각 게시글과 댓글 본문을 소유합니다.
- 선택 근거: 좋아요·싫어요와 신고는 항상 `community_content.id` 하나를 가리키므로 대상
  종류마다 별도 참조 규칙을 만들지 않습니다. 게시글과 댓글의 본문 형식은 각 table에서
  독립적으로 바꿀 수 있습니다.
- 비용: 콘텐츠 하나를 저장하거나 읽을 때 공통 행과 본문 행을 함께 다뤄야 하며, 두 행의
  종류가 맞는지 DB가 검증해야 합니다.
- 재검토 조건: 미래 콘텐츠 종류가 작성자·삭제·숨김·반응 규칙을 전혀 공유하지 않아 공통
  식별 행의 의미가 사라집니다.

DB 내부 콘텐츠 연결에는 identity `BIGINT`를 사용하고, API에 노출하는 콘텐츠 ID에는 추측하기
어려운 UUID `public_id`를 사용합니다.

Account 관계는 다음처럼 결정합니다.

- 배경: Community가 Account의 내부 PK와 persistence 조회에 결합되면 안 되지만, 존재하지 않는
  account 식별자가 작성자·반응·알림 등에 저장되는 것도 막아야 합니다.
- 검토한 선택지: Account 내부 `BIGINT` FK, 공개 UUID만 저장하고 FK 생략, 공개 UUID FK
- 선택: 모든 account 관계는 공개 UUID를 저장하고 `account(public_id)`를 참조하는
  `ON DELETE RESTRICT` FK를 사용합니다.
- 선택 근거: JWT subject와 외부 식별자 경계를 유지하면서 같은 PostgreSQL이 제공하는 존재
  무결성을 포기하지 않습니다. Account 탈퇴는 row 삭제가 아니라 `WITHDRAWN` 상태로 남으므로
  콘텐츠 보존 정책과도 충돌하지 않습니다.
- 영향: Account와 각 module schema 사이에 물리 FK 의존은 생기지만, FK는 존재 확인만 담당합니다.
  Account 상태와 표시 이름은 계속 Account 공개 API로 조회하며 다른 module은 Account table을
  직접 읽지 않습니다.
- 배제한 대안: 내부 `BIGINT`는 Account persistence를 외부 module 계약으로 만들고, FK 없는
  UUID는 아직 필요하지 않은 database 분리를 위해 현재 데이터 무결성을 미리 포기합니다.
- 재검토 조건: Account와 Community가 별도 database/service로 실제 분리됩니다. 그때 FK를
  제거하고 검증된 identity 복제 또는 event 기반 무결성으로 전환합니다.

### D3. 자유게시판과 게시글 본문 저장 방식

자유게시판도 문자열 상수로 코드에 박아 두지 않고 `community_board`의 실제 행으로 만듭니다.
첫 행의 고정 주소는 `free`이고, 게시글은 이 행을 참조합니다. 나중에 게시판을 추가할 때는
기존 게시글을 옮기지 않고 board 행과 접근 정책만 추가합니다.

게시글 본문은 사용자가 작성한 순서대로 텍스트와 이미지를 배열한 JSON으로 저장합니다.
여기서 block은 본문 조각 하나를 뜻하며, v1에서는 `TEXT`와 `IMAGE` 두 종류만 허용합니다.
제목은 본문 schema version과 독립된 필수 plain-text scalar로 저장합니다. 목록의 본문
미리보기는 본문 JSON 자체를 매번 읽어 만드는 대신 게시글을 쓸 때 TEXT block에서 함께 만든
read projection으로 저장합니다.

- 배경: 현재에는 자유게시판 하나만 필요하지만 이후 게시판을 추가할 수 있어야 하고, 본문
  안에서 텍스트와 이미지가 놓인 순서를 잃지 않아야 합니다. 제목과 목록 미리보기는 body
  format이 바뀌어도 같은 API 역할을 유지해야 합니다.
- 검토한 선택지: 게시판 이름을 문자열 column에 반복 저장, board table 참조, 게시판마다
  별도 post table; Markdown 안에 이미지 token 삽입, 본문과 첨부 목록 분리, 순서가 있는
  typed JSON block; 제목을 body JSON 첫 block으로 저장, 별도 scalar로 저장; 목록 조회마다
  JSON을 파싱해 preview 생성, 쓰기 transaction에서 preview projection 저장
- 선택: `community_board`에 `free` 행을 만들고 모든 게시글이 board FK를 가집니다. 본문은
  schema version과 순서가 있는 typed JSON block으로 저장합니다. 제목은 별도 `title`에,
  목록 미리보기는 body에서 파생한 `body_preview`에 저장합니다.
- 선택 근거: board 행 추가만으로 새 게시판을 만들 수 있고, JSON block은 사용자 HTML을
  실행하지 않으면서 텍스트·이미지 순서와 media UUID를 명시적으로 검증할 수 있습니다.
  제목을 body version에서 분리하면 미래 rich body migration이 제목 계약을 흔들지 않고,
  write-side preview는 최신 목록이 최대 20,000자 JSON을 매 row마다 읽고 파싱하지 않게 합니다.
- 비용: application이 JSON 구조를 검증하고 API 응답으로 변환하며, body 수정 transaction에서
  `body_preview`를 반드시 함께 갱신해야 합니다.
- 배제한 대안: Markdown token은 parser 규칙을 공개 API에 오래 결속하고, 본문과 첨부 목록을
  분리하면 이미지가 본문 어디에 있었는지 잃습니다. 제목을 body block으로 저장하면 제목
  조회가 body version에 결속되고, read-time preview는 목록 비용과 body parser 의존을 키웁니다.
- 재검토 조건: 협업 편집, 글자별 서식이나 embed처럼 본문 조각 사이의 복잡한 관계가 제품
  범위로 확정되거나, 목록용 search/read store가 `body_preview`의 소유권을 인수합니다.

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
- v1 reader는 이후 본문 형식이 추가돼도 계속 유지합니다. 글자 서식은 v1 plain text를 새
  의미로 재해석하지 않고 별도 기술 검토를 거친 새 body schema version으로 추가합니다.
  Read path는 저장된 schema version으로 parser를 선택하고,
  기존 v1 row의 일괄 migration을 새 서식 출시의 선행 조건으로 삼지 않습니다.
- 구체적인 미래 서식 표현은 협업 편집, 글자별 서식이나 embed의 실제 제품 범위가 확정될 때
  비교합니다. 현재에는 v1 호환 reader와 기존 row의 지속 가독성만 장기 계약입니다.
- 제목은 모든 Unicode whitespace run을 U+0020 한 칸으로 바꾸고 앞뒤 공백을 제거한 뒤
  1~120 Unicode code point를 허용합니다. 대소문자와 문장부호는 보존하고 HTML·Markdown을
  해석하지 않습니다. 120자는 목록 탐색성을 해치지 않으면서 일반 게시글의 설명력을
  보존하는 초기 상한이며, 제목 중심 장문 콘텐츠가 별도 제품 형식으로 확정되면 형식별
  정책으로 재검토합니다.
- `body_preview`는 정규화된 TEXT block을 문서 순서대로 U+0020 한 칸으로 연결하고 앞뒤·연속
  whitespace를 정리한 뒤 앞 200 Unicode code point를 저장합니다. IMAGE의 `alt`는 이미지
  설명이지 본문 문장이므로 preview source에 넣지 않습니다. 본문에는 nonblank TEXT가 필수라
  preview도 항상 nonblank입니다.
- Preview는 body의 파생값이며 별도 수정 입력을 받지 않습니다. 생성과 body 수정은 같은
  transaction에서 body와 preview를 함께 저장하고, 제목만 수정하면 기존 preview를 유지합니다.
  향후 extractor가 달라지면 기존 body version reader로 재생성하는 additive backfill을
  사용하며 기존 body row를 새 format으로 강제 변환하지 않습니다.

### D4. 화면은 1단으로 유지하면서 실제 답변 대상을 보존하는 방법

사용자 화면에는 댓글 아래 답글을 한 단계로만 보여 줍니다. 다만 답글 B에 다시 답한 C가
누구에게 한 말인지는 잃으면 안 되므로, 각 답글에는 `직접 답한 대상`과 `화면에서 묶일 최초
댓글`을 둘 다 저장합니다.

```text
댓글 A
├── 답글 B  (직접 대상 A, 묶음 기준 A)
└── 답글 C  (직접 대상 B, 묶음 기준 A)  화면에는 "B에게 답글"로 표시
```

이 구조에서 세 관계의 역할은 다음과 같습니다.

- `post_content_id`: 댓글·답글이 속한 게시글입니다. 게시글 행에서는 null이고 모든 댓글·
  답글에서는 필수이며, 게시글별 대화 조회와 아래 두 관계가 같은 게시글에 속하는지 검증할
  때 사용합니다.
- `parent_comment_id`: 정확히 어느 댓글·답글에 직접 답했는지 나타냅니다. 최상위 댓글에서는
  null입니다.
- `thread_root_comment_id`: 현재 1단 화면에서 답글을 묶을 최초 댓글입니다. 최상위 댓글에서는
  null이고 모든 답글에서는 최초 댓글을 가리킵니다.

- 배경: 현재 UI는 1단만 보여 주지만 답글에 다시 답한 대상을 표시해야 하고, 미래에 다단
  대화를 제공할 가능성도 막지 않아야 합니다.
- 검토한 선택지: 직접 대상만 저장, 최초 댓글만 저장, 두 관계를 함께 저장
- 선택: 최초 댓글은 두 field가 모두 null이고, 모든 답글은 직접 대상과 최초 댓글을 함께
  저장합니다.
- 선택 근거: 현재 화면은 최초 댓글 기준으로 답글을 빠르게 묶고, 미래에는 직접 대상 관계를
  따라 실제 대화 tree를 구성할 수 있습니다.
- 비용: DB는 직접 대상과 최초 댓글이 같은 게시글에 속하고, 최초 댓글이 실제 최상위
  댓글인지 추가로 검증해야 합니다.
- 재검토 조건: 깊은 대화의 조회량이 커져 parent 관계만 따라가는 방식으로는 성능 목표를
  충족하지 못합니다.

답글 생성 규칙은 다음과 같습니다.

- 요청은 같은 게시글에 속한 직접 대상 댓글 UUID를 받습니다.
- 직접 대상이 최초 댓글이면 그 댓글을 묶음 기준으로 저장합니다.
- 직접 대상이 다른 답글이면 그 답글이 이미 가진 묶음 기준을 그대로 복사합니다.
- 직접 대상과 묶음 기준은 생성 뒤 바꾸지 않습니다.
- 삭제·숨김 콘텐츠에는 새 답글을 달 수 없지만, 이미 존재하는 하위 대화는 유지합니다.
- API는 모든 답글을 최초 댓글 아래 한 단계로 반환하고 `replyTo`에 직접 대상 UUID와 표시할
  이름을 넣습니다. 대상이 비공개이면 이름 대신 placeholder를 반환합니다.

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

| 상태 | 공개 사용자 작성 표현 | 공개 작성자 | 대화 표시 |
| --- | --- | --- | --- |
| visible | 현재 body와 제품이 선택한 그 밖의 작성 field | 현재 표시 이름 | 정상 콘텐츠 |
| author deleted | 미노출 | 미노출 | 공개 후손이 있으면 `삭제된 콘텐츠`, 없으면 목록·상세 조회에서 제외 |
| moderator hidden | 미노출 | 미노출 | 공개 후손이 있으면 `관리자에 의해 숨겨진 콘텐츠`, 없으면 목록·상세 조회에서 제외 |

- post가 tombstone이면 공개 후손을 읽을 수 있도록 detail route는 유지합니다. 공개 후손이
  하나도 없으면 public 조회는 `404`로 수렴합니다.
- comment/reply도 같은 규칙을 사용합니다. 삭제된 leaf는 page에서 빠지고, 공개 후손의 root
  또는 parent context로 필요한 row만 tombstone으로 남습니다.
- 수정은 게시글·댓글·답글에서 사용자가 작성하고 화면에 표시되는 field의 semantic 값이
  실제로 달라질 때만 `edited_at`을 설정합니다. 게시글은 정규화된 제목 또는 body의 semantic
  값 중 하나만 달라져도 같은 규칙을 사용합니다. 파생 `body_preview`만의 재생성은 사용자
  편집으로 표시하지 않으며 Revision 원문은 저장하지 않습니다.
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
  공개 계약을 추가하고 댓글·답글 생성 transaction 안에서 호출합니다. Community가 대화
  관계에서 후보 수신자를 계산하고, Notification의 수신 정책이 자기 알림·구독 해제·중복을
  제거한 뒤 inbox를 저장합니다.
- 선택 근거: 외부 provider 호출 없이 같은 PostgreSQL transaction으로 대화와 inbox row를
  원자적으로 저장하며 기존 notification 책임을 채널 중립적인 알림 lifecycle로 확장합니다.
- 비용: 현재 modular monolith transaction에 결합되고, 답글 하나가 최대 두 수신자의 inbox
  write를 만듭니다.
- 재검토 조건: module을 별도 database/service로 분리하거나 알림 throughput이 core write를
  방해합니다. 그때 transactional outbox와 idempotent consumer로 경계를 이동합니다.

수신자 계산은 다음과 같습니다.

| 생성 종류 | 후보 수신자 |
| --- | --- |
| 최상위 댓글 | post 작성자 |
| reply | post 작성자 + direct parent 작성자 |

- 후보 집합에서 actor 자신을 제거하고, 같은 account UUID를 하나로 합친 뒤 구독 정책을
  적용합니다. 첫 범위에는 구독 설정이 없으므로 모든 후보를 구독 중으로 간주합니다.
- 이후 게시글별 알림 해제를 추가할 때는 Notification이 `post UUID + recipient UUID`의
  preference를 소유하고 같은 수신 정책 단계에서 제외합니다. Community의 대화 관계와 inbox
  저장 계약은 바꾸지 않습니다.
- 동일 source comment UUID와 recipient UUID에는 inbox row 하나만 허용합니다. 따라서 post
  작성자와 direct parent 작성자가 같아도 같은 답글 알림은 하나만 생성됩니다.
- notification은 `COMMUNITY_COMMENT` 또는 `COMMUNITY_REPLY`, recipient, actor, source comment
  UUID, post UUID, `created_at`, nullable `read_at`만 저장합니다.
- Notification inbox에서 Community 콘텐츠를 가리키는 module 간 FK는 만들지 않습니다. 다만
  recipient와 actor의 Account 공개 UUID FK는 유지합니다. 숨김·삭제 뒤 inbox에는 일반화된
  문구와 stable post route만 남고, route가 더 이상 공개되지 않으면 detail은 `404`가 됩니다.
- 이메일·push 발송 상태를 inbox row에 섞지 않습니다. 후속 채널은 inbox 생성 사실을 source로
  별도 delivery lifecycle을 가집니다.

### D10. HTTP API와 접근 제어

Post 생성·수정·조회 payload는 D3의 필수 `title`과 파생 `preview` 계약을 사용합니다. 제목과
body만 write input이며 preview는 server가 생성하는 read-only field입니다.

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
| `GET` | `/api/v1/boards/free/posts?page={page}&size={size}` | 페이지 번호로 이동하는 최신 post page |
| `GET` | `/api/v1/posts/{postId}` | post detail과 count |
| `GET` | `/api/v1/posts/{postId}/comments` | 최신 top-level comment page |
| `GET` | `/api/v1/comments/{commentId}/replies` | root 아래 reply 시간순 page |
| `GET` | `/api/v1/media/images/{mediaId}/content` | 공개 usage가 있는 READY image를 application이 authorization 뒤 stream |

위 GET만 public allow-list에 명시합니다. 숨김·삭제되어 context가 없는 resource, 존재하지 않는
UUID와 비공개 media는 `404`로 통일합니다.

#### 일반 보호 쓰기

| Method | Path | 핵심 입력·결과 |
| --- | --- | --- |
| `POST` | `/api/v1/boards/free/posts` | `Idempotency-Key`, 필수 `title`과 v1 body block으로 생성, `201` |
| `PATCH` | `/api/v1/posts/{postId}` | owner가 `title` 또는 body를 부분 수정, `200` |
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

Post 생성의 `title`과 `body`는 모두 필수입니다. Post `PATCH`는 absent field를 유지로 해석하고
`title`이나 `body` 중 하나 이상을 요구하며 명시적 `null`은 `400`으로 거부합니다. 정규화 뒤
semantic 값이 모두 같으면 version·`edited_at`을 바꾸지 않고 현재 표현을 `200`으로 반환합니다.
제목은 D3의 whitespace 정규화 뒤 1~120 Unicode code point,
body는 D3의 v1 contract로 검증합니다. Request DTO는 비정상적으로 큰 정규화 전 입력을
막기 위해 title에 512 UTF-16 code unit 상한을 먼저 적용하고, domain value가 정규화 뒤
code point와 120자 규칙을 최종 검증합니다. Application command에는 raw Web DTO가 아니라
검증된 값만 전달합니다.

Post detail response는 visible일 때 필수 `title`과 body를 반환합니다. Post 목록 item은 필수
`title`과 `preview`를 반환하고 body JSON은 반환하지 않습니다. Tombstone detail은 대화
context에 필요한 ID·visibility·시간만 반환하며 title·body·preview·author를 모두 노출하지
않습니다. Tombstone post는 최신 목록에서 제외됩니다.

post/comment 수정은 response `ETag`의 version을 `If-Match`로 요구하고 stale version은
`412 Precondition Failed`로 처리합니다. 삭제는 body를 덮어쓰지 않고 독립
`author_deleted_at`만 설정하므로 `If-Match`를 요구하지 않습니다. 관리자 숨김 뒤의 작성자
삭제와 같은 작성자의 반복 DELETE도 `204`로 수렴합니다. Reaction, report와 notification read는
자체 unique·멱등 계약을 사용하므로 content version을 소비하지 않습니다.

Post·comment/reply 생성의 `Idempotency-Key`는 UUID이며 actor와 operation kind에 결속합니다.
Server는 정규화 request hash와 생성 content UUID를 영구 저장합니다. 같은 actor가 같은 key와
같은 payload를 재전송하면 최초 status와 resource를 반환하고, 다른 payload나 operation에 key를
재사용하면 `409`로 거부합니다. 따라서 response 유실 뒤 재시도도 content와 inbox 알림을 하나만
만듭니다. Post 생성 hash에는 정규화된 title과 canonical v1 body를 모두 포함해 같은 key로
제목이나 본문만 바꾼 재전송도 충돌로 식별합니다. Media upload도
owner·key·declared metadata·checksum hash를 같은 방식으로 보존하고
재시도는 media UUID의 현재 상태로 멱등 처리합니다. 이 key의 재사용 범위는 전역이 아니라
각 module의 resource·operation입니다. 따라서 Community content 생성과 Media upload는 서로
다른 저장 경계에서 같은 UUID 값을 사용할 수 있습니다.

#### 관리자

| Method | Path | 결과 |
| --- | --- | --- |
| `GET` | `/api/v1/admin/community/reports` | open/closed report cursor page |
| `GET` | `/api/v1/admin/community/reports/{reportId}` | report와 검토용 target snapshot |
| `PATCH` | `/api/v1/admin/community/reports/{reportId}` | `HIDE_CONTENT` 또는 `DISMISS` resolution |

관리자 report `PATCH`는 최신 응답 `ETag`를 `If-Match`로 제출해야 하고 stale version은
`412`로 거부합니다. 첫 범위에는 media job 재처리·강제 종결 API를 제공하지 않습니다.

모든 오류는 현재 RFC 9457 Problem Details와 project error code를 사용합니다. 외부 식별자 형식,
권한 부족, state conflict, media 미준비·소유 불일치와 stale version을 서로 다른 code로
구분하되 내부 DB id, object key와 account 상태를 노출하지 않습니다.

### D11. 페이지 번호 목록과 read model

Post 목록 projection은 `community_post.title`을 주표시로, 같은 행의 `body_preview`를
보조표시로 반환합니다. 목록 query는 body JSON을 select하거나 parse하지 않습니다. Preview는
D3의 write-side 파생 계약이 소유하고 목록은 값을 재해석하지 않습니다.

- 배경: 일반 게시판의 최신 글은 사용자가 원하는 페이지로 바로 이동할 수 있어야 합니다.
  반면 댓글·답글·알림·신고 목록은 현재 페이지 번호가 제품 결과가 아니며, 새 항목이 생겨도
  이어보기 경계가 흔들리지 않는 편이 중요합니다.
- 검토한 선택지: 모든 목록의 offset page, 모든 목록의 keyset cursor, 제품 동작에 따라
  게시글과 나머지 목록을 분리
- 선택: 게시글 최신 목록은 `page`·`size` offset pagination과 전체 건수로 페이지 번호 이동을
  제공합니다. Top-level comment, reply, notification과 report는 `(created_at, internal_id)`를
  tie-breaker로 하는 keyset cursor를 유지합니다. Post·top-level comment는 내림차순, reply는
  오름차순입니다.
- 선택 근거: 게시글에서는 전통적인 게시판 탐색을 직접 제공하고, 임의 페이지 이동이 확정되지
  않은 대화·inbox·관리 목록에는 insert·삭제에 강한 이어보기를 사용합니다.
- 비용: 게시글 목록은 새 글이 추가되면 같은 페이지의 항목이 뒤로 밀릴 수 있고, 깊은 page의
  `OFFSET`과 정확한 전체 건수 계산 비용이 커집니다. 서로 다른 목록이 pagination 형식을
  하나로 통일하지 않습니다.
- 재검토 조건: 게시글 수와 깊은 page 조회가 실제 성능 목표를 넘으면 번호 목록의 유지 범위와
  snapshot 기준을 제품에서 다시 정하고, 검색·추천 feed에는 별도 cursor 조회를 추가합니다.

게시글 API의 `page`는 0부터 시작하고 화면의 1페이지와 대응합니다. `page`를 생략하면 `0`,
`size`를 생략하면 `20`을 사용하며 `size`의 허용 범위는 `1..50`입니다. 음수 `page`, `0` 이하
또는 `50` 초과 `size`는 보정하지 않고 `400 Bad Request`와 `INVALID_PAGINATION_PARAMETER`
problem code로 거부합니다. 정렬은 `(created_at DESC, internal_id DESC)`로 고정해 client
`sort` 입력을 받지 않습니다. 응답은 `items`, `number`, `size`, `totalElements`, `totalPages`,
`first`, `last`를 포함하고 전체 page 범위를 넘는 유효한 양수 page는 빈 `items`와 현재 page
metadata를 반환합니다. 목록과 전체 건수는 모두 visible post 조건을 사용합니다.

Top-level comment·notification·report의 기본/최대 크기는 `20/50`, reply는 `50/100`으로
시작하며 server configuration이 소유합니다. Cursor는 정렬 종류, `created_at`, internal ID와
version을 서명한 URL-safe token으로 감쌉니다. 서명은 변조를 막고, opaque 계약은 client가
내부 field의 형식이나 의미에 의존하지 못하게 합니다. Query predicate와 index는 항상 같은
`(created_at, internal_id)` 순서를 사용합니다.

변경 use case는 Domain model과 Port를 사용하고, 공개 목록·상세는 필요한 column만 읽는
projection을 허용합니다. 한 page의 author display, 내 reaction과 reaction count는 각각 batch
조회하며 JPA lazy loading으로 response를 조립하지 않습니다. Post 목록 projection은 post UUID,
board slug, title, preview, visibility, `createdAt`, nullable `editedAt`, author display,
like/dislike count와 인증 사용자의 선택만 반환합니다. Public 최신 목록에는 visible post만
포함하므로 title과 preview는 항상 non-null입니다.

`body_preview` 중복 저장의 무결성은 post 생성·body 수정 use case가 body parser와 같은
extractor를 호출하는 domain/application test로 고정하고, persistence integration test에서
body와 preview가 같은 transaction에 commit되는지 확인합니다. 향후 별도 검색·feed read
store가 생기면 그 projection이 preview를 소비할 수 있지만 core write row의 title과 body는
계속 canonical source입니다.

### D12. 만들 DB table과 무결성

`community_post`는 필수 제목, 목록용 본문 미리보기와 canonical body를 함께 소유합니다.
제목과 preview는 자주 읽는 scalar이고 body는 versioned JSONB이므로 역할을 섞지 않습니다.

#### 첫 범위의 미디어 table 수

- **사용자 의견 요약**: 이미지 기능에 다섯 table을 두는 것은 첫 버전에 과하며,
  `media_job`이 실제로 언제·누가·왜 사용하는지 확인한 뒤 세 table만으로 충분하다고
  판단했습니다.
- **Agent 답변·판단 요약**: S3 작업은 PostgreSQL transaction과 함께 commit할 수 없으므로
  검증·삭제·재시도를 잃지 않는 `media_job`은 필요합니다. 반면 관리자 재처리 이력과 S3·DB
  불일치별 영구 case는 현재 제품·운영 범위가 아니고, 기존 asset/job 식별자와 상태를
  참조해 나중에 추가할 수 있으므로 선행 구현할 이유가 없습니다.
- **최종 반영**: 첫 범위는 `media_asset`, `media_usage`, `media_job`만 만듭니다. 관리자용
  `media_job_action`과 개별 `media_reconciliation_issue`는 만들지 않으며, job은 자동 재시도
  뒤 해결하지 못한 작업을 `DEAD`와 비민감 오류 code·log·metric으로 남깁니다.

- 배경: 핵심 업로드·게시글 연결·비동기 복구는 유지하면서 아직 없는 관리자 복구 화면과
  개별 장애 case lifecycle을 schema에 미리 고정하지 않아야 합니다.
- 검토한 선택지: 미디어 상태를 한 table에 합치기, asset·usage·job 세 table, 여기에 관리자
  action과 reconciliation issue를 더한 다섯 table
- 선택: asset·usage·job 세 table을 사용합니다.
- 선택 근거: 파일 상태, 게시글 사용 관계와 재시도할 외부 작업은 서로 다른 생명주기라
  분리가 필요하지만, 현재 자동 worker 밖의 사람 중심 처리 생명주기는 필요하지 않습니다.
- 영향: 첫 범위에는 `DEAD` job을 관리자가 requeue·강제 종결하는 API와 개별 불일치 목록·
  종결 이력이 없습니다. 운영자는 log·metric으로 실패를 발견하고 원인을 교정합니다.
- 배제한 대안: 한 table은 파일 사실·사용 관계·할 일을 섞어 삭제와 재시도 무결성을
  약화합니다. 다섯 table은 현재 수용 기준에 없는 운영 기능까지 구현하게 합니다.
- 재검토 조건: 관리자 재처리 화면이나 영구 감사가 필요하면 `media_job_action`을, 여러 S3·DB
  불일치를 각각 배정·종결·추적해야 하면 `media_reconciliation_issue`를 additive migration으로
  추가합니다.

이 설계에서 새로 만드는 table은 아래 10개입니다. 이 절은 각 table의 목적, column type,
필수·nullable 여부, PK·FK·unique와 상태 제약까지 확정합니다. Constraint·index의 물리 이름과
Flyway version 번호는 의미를 바꾸지 않는 실행 계획 값이므로 여기서 정하지 않습니다.
이 구조는 확정된 제품 결과와 D1~D9의 module·상태 소유권을 현재 PostgreSQL 규칙으로 변환한
목표 계약이며, 아직 구현된 현재 schema라는 뜻은 아닙니다.

```text
community_board
└── community_content (POST)
    ├── community_post
    ├── community_content (COMMENT, post_content_id로 게시글 연결)
    │   └── community_comment
    └── community_reaction

moderation_report     -- content public UUID를 참조하되 module 간 DB FK는 없음
notification_inbox    -- content public UUID를 참조하되 module 간 DB FK는 없음

media_asset
├── media_usage
└── media_job
```

내부 PK는 `BIGINT GENERATED BY DEFAULT AS IDENTITY`, API에서 직접 주소로 쓰는 resource ID는
`UUID`, 시각은 `TIMESTAMPTZ`, 상태는 `VARCHAR + CHECK`를 사용합니다. 아래 `NULL` 표시는 해당
상태에서 값이 없을 수 있다는 뜻이고, 그 밖의 column은 `NOT NULL`입니다. 아래 code block은
실행할 SQL이 아니라 column과 관계를 한눈에 보는 schema 계약 표기입니다.

모든 `public_id`와 요청용 UUID는 application이 생성합니다. 단, Flyway가 만드는 기준 row의
공개 UUID는 migration SQL에 고정한 값을 사용합니다. `created_at`·`updated_at`은
`DEFAULT NOW()`, `version`은 `DEFAULT 0`으로 시작하고 `version >= 0`,
`updated_at >= created_at`을 검사합니다. `request_hash`와 checksum은 소문자 SHA-256 형식만
허용합니다. FK 삭제·갱신 동작은 현재 schema와 같이 PostgreSQL 기본 제한 동작을 사용하며,
업무 삭제는 row 물리 삭제가 아니라 아래 상태·시각 변경으로 처리합니다.

모든 account 공개 UUID column은 같은 PostgreSQL의 `account(public_id)`를
`ON DELETE RESTRICT`로 참조합니다. 이 FK는 존재하지 않는 account 식별자의 저장과 account
물리 삭제를 막는 무결성 경계일 뿐, 다른 module이 Account table을 조회하거나 상태 정책을
소유할 권한이 아닙니다. Account 상태·표시 이름은 각 module의 공개 API로 확인합니다.

#### Community 소유

```text
community_board(
  id BIGINT PK IDENTITY,
  public_id UUID UNIQUE,
  slug VARCHAR(50) UNIQUE,                 -- seed: free
  name VARCHAR(100),                       -- seed: 자유게시판
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ,
  version BIGINT
)

community_content(
  id BIGINT PK IDENTITY,
  public_id UUID UNIQUE,
  kind VARCHAR(20) CHECK (kind IN ('POST', 'COMMENT')),
  account_public_id UUID FK -> account.public_id ON DELETE RESTRICT,
  board_id BIGINT NULL FK -> community_board.id,
  post_content_id BIGINT NULL FK -> community_content.id,          -- 댓글·답글이 속한 게시글
  parent_comment_id BIGINT NULL FK -> community_content.id,        -- 직접 답한 댓글·답글
  thread_root_comment_id BIGINT NULL FK -> community_content.id,   -- 1단 화면에서 묶을 최초 댓글
  creation_request_id UUID,
  creation_request_hash CHAR(64),
  edited_at TIMESTAMPTZ NULL,
  author_deleted_at TIMESTAMPTZ NULL,
  moderator_hidden_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ,
  version BIGINT,
  UNIQUE(account_public_id, creation_request_id)
)

community_post(
  content_id BIGINT PK FK -> community_content.id,
  title VARCHAR(120) NOT NULL,
  body_preview VARCHAR(200) NOT NULL,
  body_schema_version SMALLINT CHECK (body_schema_version = 1),
  body JSONB
)

community_comment(
  content_id BIGINT PK FK -> community_content.id,
  body TEXT CHECK (char_length(btrim(body)) BETWEEN 1 AND 2000)
)

community_reaction(
  id BIGINT PK IDENTITY,
  content_id BIGINT FK -> community_content.id,
  account_public_id UUID FK -> account.public_id ON DELETE RESTRICT,
  reaction_type VARCHAR(20) CHECK (reaction_type IN ('LIKE', 'DISLIKE')),
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ,
  UNIQUE(content_id, account_public_id)
)
```

`community_content`의 kind shape check는 POST일 때 board만, COMMENT일 때 post를 필수로 하고
top-level COMMENT의 parent/root는 모두 null, reply의 parent/root는 모두 non-null로 제한합니다.
Self FK는 parent/root/post row 존재를 보장합니다. Flyway가 만드는 deferrable constraint
trigger는 commit 시점에 post target이 실제 POST인지, parent/root가 같은 post의 COMMENT인지,
root가 top-level인지, `community_post`·`community_comment` subtype이 content kind와 정확히
일치하는지를 검증합니다. Application은 같은 규칙을 먼저 검증해 domain error를 반환하지만
trigger가 우회 write와 race의 최종 backstop입니다. Parent/root/post와 creation request 값은
생성 후 갱신하지 않습니다.

`community_post.title`은 `char_length(title) BETWEEN 1 AND 120`, 앞뒤 U+0020 공백 없음,
control character 없음과 연속 whitespace 없음 check를 둡니다. `body_preview`도
`char_length(body_preview) BETWEEN 1 AND 200`, 앞뒤 공백·control character·연속 whitespace
없음 check를 둡니다. Application은 모든 Unicode whitespace를 U+0020으로 정규화하고 Java
`codePointCount`로 같은 상한을 검증합니다. DB check는 우회 write의 nonblank·저장 상한과
control·앞뒤·연속 공백 오류를 방어하고, 전체 Unicode normalization의 단일 owner는 domain
value로 유지합니다.

`community_post.body`는 DB가 JSON object, `body_schema_version=1`과 `blocks` array 존재까지
확인합니다. 각 block의 허용 field, TEXT 전체 20,000자, IMAGE 최대 10개와 nonblank TEXT 최소
1개는 D3 계약을 읽는 application validator가 검증합니다. JSON 내부 전체와 preview 파생
알고리즘을 SQL trigger로 중복 구현하지 않습니다. Post 생성·body 수정 use case는 한
transaction에서 body와 `body_preview`를 항상 함께 갱신하고 persistence adapter는 둘 중 하나만
받는 write method를 공개하지 않습니다.

주요 index는 다음 query 모양을 소유합니다.

- visible post page·count: `(board_id, created_at DESC, id DESC)` where kind is POST and
  `author_deleted_at IS NULL AND moderator_hidden_at IS NULL`
- top-level comment: `(post_content_id, created_at DESC, id DESC)` where kind is COMMENT and parent is null
- reply: `(thread_root_comment_id, created_at ASC, id ASC)` where kind is COMMENT and root is not null
- reaction aggregate: `(content_id, reaction_type)`
- author-owned mutation lookup: `(account_public_id, public_id)`
- context 유지 판단: comment의 parent/root/post FK index

#### Moderation 소유

```text
moderation_report(
  id BIGINT PK IDENTITY,
  public_id UUID UNIQUE,
  target_content_public_id UUID,
  target_kind VARCHAR(20) CHECK (target_kind IN ('POST', 'COMMENT')),
  reporter_account_public_id UUID FK -> account.public_id ON DELETE RESTRICT,
  status VARCHAR(30) CHECK (status IN ('OPEN', 'CONTENT_HIDDEN', 'DISMISSED')),
  resolved_by_account_public_id UUID NULL FK -> account.public_id ON DELETE RESTRICT,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ,
  resolved_at TIMESTAMPTZ NULL,
  version BIGINT
)
```

같은 reporter/target의 open 중복은 `status = 'OPEN'` partial unique index로 막고, open report
조회는 `(status, created_at DESC, id DESC)` index를 사용합니다. `OPEN`이면 처리자·처리 시각이
null이고 종결 상태이면 둘 다 필수라는 check를 둡니다. 신고 대상 Community 콘텐츠를 가리키는
module 간 FK는 만들지 않지만 reporter와 처리자의 Account 공개 UUID FK는 유지합니다.

#### Notification 소유

```text
notification_inbox(
  id BIGINT PK IDENTITY,
  public_id UUID UNIQUE,
  recipient_account_public_id UUID FK -> account.public_id ON DELETE RESTRICT,
  actor_account_public_id UUID FK -> account.public_id ON DELETE RESTRICT,
  type VARCHAR(40) CHECK (type IN ('COMMUNITY_COMMENT', 'COMMUNITY_REPLY')),
  source_content_public_id UUID,
  post_public_id UUID,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ,
  read_at TIMESTAMPTZ NULL,
  version BIGINT,
  UNIQUE(recipient_account_public_id, source_content_public_id),
  CHECK(recipient_account_public_id <> actor_account_public_id)
)
```

recipient inbox index는 `(recipient_account_public_id, created_at DESC, id DESC)`이고 unread
query를 위해 `(recipient_account_public_id, created_at DESC, id DESC) WHERE read_at IS NULL`
partial index를 둡니다. Community content에는 module 간 FK를 만들지 않지만 recipient와 actor는
위 account 공개 UUID FK를 사용합니다.

#### Media 소유

```text
media_asset(
  id BIGINT PK IDENTITY,
  public_id UUID UNIQUE,
  owner_account_public_id UUID FK -> account.public_id ON DELETE RESTRICT,
  idempotency_key UUID,
  request_hash CHAR(64),
  status VARCHAR(30) CHECK (status IN ('RESERVED', 'UPLOADED', 'VERIFYING',
                                       'READY', 'REJECTED', 'DELETE_PENDING', 'DELETED')),
  staging_object_key VARCHAR(512),
  delivery_object_key VARCHAR(512) NULL,
  expected_media_type VARCHAR(50),
  detected_media_type VARCHAR(50) NULL,
  original_byte_size BIGINT NULL,
  delivery_byte_size BIGINT NULL,
  width INTEGER NULL,
  height INTEGER NULL,
  original_checksum_sha256 CHAR(64) NULL,
  delivery_checksum_sha256 CHAR(64) NULL,
  reservation_expires_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ,
  version BIGINT,
  UNIQUE(owner_account_public_id, idempotency_key)
)

media_usage(
  id BIGINT PK IDENTITY,
  asset_id BIGINT FK -> media_asset.id,
  consumer_type VARCHAR(30) CHECK (consumer_type IN ('POST_BODY')),
  consumer_public_id UUID,
  status VARCHAR(30) CHECK (status IN ('ACTIVE', 'HIDDEN_RETAINED', 'RELEASED')),
  hidden_at TIMESTAMPTZ NULL,
  released_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ,
  version BIGINT
)

media_job(
  id BIGINT PK IDENTITY,
  public_id UUID UNIQUE,
  asset_id BIGINT NULL FK -> media_asset.id,
  type VARCHAR(40) CHECK (type IN ('VERIFY', 'DELETE_STAGING', 'DELETE_DELIVERY', 'RECONCILE')),
  status VARCHAR(30) CHECK (status IN ('READY', 'RUNNING', 'RETRY', 'DONE', 'DEAD')),
  attempt_count INTEGER DEFAULT 0 CHECK (attempt_count >= 0),
  lease_owner VARCHAR(200) NULL,
  lease_until TIMESTAMPTZ NULL,
  next_attempt_at TIMESTAMPTZ NULL,
  last_error_code VARCHAR(100) NULL,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ,
  version BIGINT
)
```

Object key는 API·log에 노출하지 않고 asset UUID와 무관한 random component를 포함합니다.
`media_job` worker는 `FOR UPDATE SKIP LOCKED`로 replica 간 job claim을 직렬화합니다. Media claim은
asset row를 lock하고 non-released usage partial unique index가 동시 이중 claim을 최종
차단합니다. 이 index는 `(asset_id) WHERE status <> 'RELEASED'`이고, 같은 consumer의 중복
연결은 `(consumer_type, consumer_public_id, asset_id)` unique로 막습니다.

Media 상태 check는 다음을 함께 보장합니다.

- `media_asset.UPLOADED|VERIFYING|READY|REJECTED`에는 original byte size·checksum이 필수이고,
  `READY`에는 delivery key·detected type·크기·pixel·delivery checksum도 필수입니다.
  `DELETE_PENDING|DELETED`는 upload 전 만료와 delivery 생성 뒤 삭제를 모두 표현하므로 original
  metadata 묶음과 delivery metadata 묶음을 각각 전부 null 또는 전부 non-null로 제한합니다.
  저장된 byte size·width·height는 모두 양수이고 D14의 상한을 넘을 수 없습니다.
- `media_usage.ACTIVE`에는 hidden/released 시각이 없고, `HIDDEN_RETAINED`에는 hidden 시각,
  `RELEASED`에는 released 시각이 필수입니다.
- `media_job.RUNNING`에만 lease owner·expiry가 필요하고, `RETRY`에는 next attempt와
  `last_error_code`, `DEAD`에는 `last_error_code`가 필요합니다. `DONE|DEAD`는 worker가 다시
  claim하지 않는 terminal 상태입니다. VERIFY·DELETE job은 asset FK가 필수이고 전체 prefix를
  대조하는 RECONCILE job만 asset FK가 null일 수 있습니다.
- Worker는 `READY -> RUNNING -> DONE` 또는 실패 시 `RUNNING -> RETRY -> RUNNING`을 반복하고,
  최대 attempt를 넘기면 `DEAD`로 전환합니다. claim한 worker가 종료되면 lease 만료 뒤 다른
  worker가 같은 job을 다시 claim하며, 별도 action row 없이 job version과 조건부 update로
  중복 완료를 막습니다.

Worker와 cleanup 조회를 위한 주요 index는 `media_asset(status, reservation_expires_at, id)`,
`media_job(status, next_attempt_at, created_at, id) WHERE status IN ('READY', 'RETRY')`,
`media_job(lease_until, id) WHERE status = 'RUNNING'`입니다.
Job을 만들 때는 `(asset_id, type) WHERE asset_id IS NOT NULL AND status IN
('READY', 'RUNNING', 'RETRY')` partial unique index로 같은 asset·type의 미종결 job
중복을 막습니다. `type WHERE type = 'RECONCILE' AND asset_id IS NULL AND status IN
('READY', 'RUNNING', 'RETRY')` partial unique index로 전체 prefix RECONCILE job도 동시에
하나만 열리게 합니다.

### D13. Transaction과 concurrency

| 경계 | 같은 DB transaction에 반드시 포함되는 효과 |
| --- | --- |
| post 생성·수정 | content/post 저장, READY media 소유·중복·usage claim 검증, 제거한 usage를 `RELEASED`로 전환하고 cleanup job insert |
| comment/reply 생성 | content/comment 저장, parent/root 검증, recipient 계산, inbox insert |
| content 수정·삭제 | 수정은 owner와 version, 삭제는 owner·현재 삭제 사실 검증, edited/delete timestamp, 작성자 삭제의 media usage `RELEASED` 전환과 cleanup job insert |
| reaction | target visibility 확인, unique choice upsert/delete |
| report | target visibility 확인, duplicate 방지 report insert |
| report resolution | 관리자·report version 검증, Community hide, 관련 open report 종결, media usage를 `HIDDEN_RETAINED`로 전환 |
| media job claim | `READY|RETRY` 또는 lease가 만료된 `RUNNING` job을 조건부 version update로 `RUNNING` 전환하고 새 lease owner·expiry 기록. 첫 VERIFY claim은 `UPLOADED -> VERIFYING`, 재획득은 기존 `VERIFYING`을 요구합니다. 첫 DELETE_DELIVERY claim은 non-released usage가 없음을 확인해 `READY -> DELETE_PENDING`, 재획득은 기존 `DELETE_PENDING`을 요구합니다. |
| VERIFY 성공 | deterministic delivery object의 checksum을 확인한 뒤 같은 DB transaction에서 lease owner·job version·`VERIFYING` asset을 검증하고 asset을 `READY`, VERIFY job을 `DONE`으로 전환하며 `DELETE_STAGING` job 하나를 저장 |
| VERIFY 영구 거부 | 같은 검증 경계에서 asset을 `REJECTED`, VERIFY job을 `DONE`으로 전환하고 `DELETE_STAGING` job 하나를 저장 |
| DELETE_STAGING 성공 | S3 delete가 성공하거나 이미 object가 없음을 확인한 뒤 lease owner·job version을 검증해 job만 `DONE`으로 전환하고 asset의 논리 staging key와 상태는 바꾸지 않음 |
| DELETE_DELIVERY 성공 | S3 delete가 성공하거나 이미 object가 없음을 확인한 뒤 lease owner·job version과 `DELETE_PENDING` asset을 검증해 asset을 `DELETED`, job을 `DONE`으로 함께 전환 |
| RECONCILE 종료 | 안전한 object 수렴 뒤 lease owner·job version을 검증해 `DONE`, 자동 복구할 수 없는 불일치가 남으면 aggregate error code와 함께 `DEAD`로 전환 |

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
  checksum·HEAD·object 부재 확인으로 idempotent하게 재실행합니다. 외부 효과 뒤 DB transaction이
  실패하면 job은 기존 `RUNNING` 상태와 lease를 유지하고, 만료 뒤 새 worker가 같은 외부 효과를
  재확인한 다음 위 조건부 transaction을 다시 시도합니다. 이전 lease owner의 늦은 완료는
  owner·version 조건이 맞지 않아 asset이나 job을 갱신하지 못합니다.

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

- **사용자 의견 요약**: JPEG와 PNG 두 형식만 허용하면 지나치게 제한적이라는 우려에 따라
  일반적인 정적 형식은 보완하되, WebP는 첫 범위에서 제외하기로 확정했습니다.
- **Agent 답변·판단 요약**: JDK ImageIO가 기본 지원하는 형식 중 웹 게시글 입력으로 실익이
  큰 정적 GIF만 추가하면 새 외부 decoder 없이 현재 Java 21·ARM64 배포 경계를 유지하면서
  JPEG·PNG보다 입력 범위를 넓힐 수 있습니다. BMP·TIFF는 웹 입력 실익보다 대용량·다중 image
  처리 표면이 크고, HEIC/HEIF·AVIF는 현재 JVM decoder 경계에서 검증된 지원이 없으며, SVG는
  active content 보안 경계가 달라 제외합니다.
- **최종 반영**: v1 입력은 JPEG/JPG, PNG와 단일 frame GIF를 허용하고, 모두 metadata를
  제거해 JPEG 또는 PNG delivery object로 정규화합니다. WebP, animated GIF, SVG, BMP, TIFF,
  HEIC/HEIF, AVIF, 영상과 일반 파일은 거부합니다. 새 형식은 MIME allow-list만 늘리지 않고
  D19의 decoder admission을 통과해 추가합니다.
- client file name, extension과 `Content-Type`은 신뢰하지 않습니다. 실제 byte signature와
  decode 결과를 기준으로 판정합니다.
- 한 image 최대 10 MiB, 24 megapixel, 한 post 최대 10개, 전체 TEXT 20,000자, comment/reply
  2,000자로 시작합니다. 값은 configuration과 API validation이 같은 source를 사용합니다.
- Server는 multipart part를 Pod의 size-bounded `/tmp` file로 받으면서 10 MiB를 넘는 즉시
  중단하고 실제 byte length와 SHA-256을 계산합니다. 그 뒤 known-length file을 S3
  `PutObject`의 SHA-256 checksum과 DB metadata에 결속하고 `finally` 경계에서 temporary file을
  삭제합니다. Declared file name과 `Content-Type`은 검증 근거가 아닙니다.
- worker는 bounded concurrency `1`로 header·size·pixel·frame 수 제한을 먼저 확인하고
  decode합니다. Orientation을 적용한 뒤 metadata를 버리고 alpha channel이 필요하면 PNG,
  그렇지 않으면 JPEG로 다시 encode합니다.
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

#### 공개 보호 방식 재검토

- **발견한 공백**: 기존 문장은 local limiter를 사용하면서 ready application replica가 하나가
  아니면 capability가 자동으로 닫힌다고 했지만, 현재 application은 Kubernetes replica 수를
  읽는 권한·Port·health indicator가 없고 Deployment도 replica와 rollout 전략을 명시하지
  않습니다. 따라서 그 자동 차단은 구현 가능한 계약이 아니었습니다.
- **현재 상태**: Community와 limiter는 아직 구현되지 않았습니다. 현재 Deployment는 Kubernetes
  기본 replica `1`과 `RollingUpdate`를 사용하므로 평상시에는 Pod 하나가 실행되고 rollout 중에는
  새 Pod가 준비될 때까지 기존 Pod와 잠시 함께 실행될 수 있습니다.
- **보존하는 목표**: 공개 읽기와 image stream에는 각 요청을 실제 처리하는 Pod에서
  client·동시성·전달량 보호가 항상 적용돼야 합니다. 첫 범위는 배포 중의 짧은 중복보다 기존
  전체 API 가용성을 우선하고, 상시 다중 replica의 정확한 전체 상한은 공유 보호 계층 도입
  조건으로 분리합니다.

| 후보 | 장점 | 비용·제외 이유 |
| --- | --- | --- |
| 명시적 `replicas: 1` + `RollingUpdate`와 Pod별 local limiter | 평상시 Pod 하나를 유지하면서 rollout 중 기존 API 가용성을 보존하고 새 DB·cluster API 권한이 필요 없음 | rollout 중에는 최대 두 Pod가 각자 상한을 계산하므로 전체 이론 상한이 잠시 두 배가 됨 |
| 명시적 `replicas: 1` + `Recreate` | rollout 중에도 local limiter 인스턴스를 하나로 제한 | Community뿐 아니라 인증을 포함한 application 전체가 중단되고 재시작 때 limiter 상태도 초기화됨 |
| PostgreSQL 공유 limiter | RollingUpdate와 다중 replica에서도 전체 상한을 공유 | 모든 anonymous read에 DB write·정리 생명주기를 추가하고 image stream semaphore가 DB pool을 점유함 |

- **Agent 기술 결정**: 첫 범위에는 Deployment의 `spec.replicas: 1`,
  `spec.strategy.type: RollingUpdate`, `maxSurge: 1`, `maxUnavailable: 0`을 명시합니다. HPA는
  두지 않고 local limiter는 Pod별 보호 장치로 정의합니다. 이 값은 GitOps desired state와
  manifest contract test가 소유합니다.
- **선택 근거**: rollout 중 한도가 잠시 늘어날 가능성보다 배포할 때마다 기존 API 전체를
  중단하는 영향이 더 큽니다. `RollingUpdate`는 새 Pod가 Ready인 동안만 최대 한 개를 추가해
  가용성을 보존하고, 별도 공유 저장 생명주기나 Kubernetes 조회 권한을 미리 만들지 않습니다.
- **감수하는 비용**: rollout 중 최대 두 Pod가 요청을 나눠 받으면 client별 요청량과 process별
  이미지 상한의 이론적 합이 잠시 두 배가 됩니다. 이 구간은 현재 허용하며 deployment 전체의
  정확한 상한이라고 표현하지 않습니다.
- **배제한 대안**: `Recreate`는 짧은 중복 상한을 피하려고 application 전체를 중단시키면서도
  새 Pod 시작 때 local limiter 상태가 초기화되므로 채택하지 않습니다. PostgreSQL 공유
  limiter는 현재 단일 replica 범위에 비해 저장·정리·DB pool 비용이 큽니다.
- **재검토 조건**: 상시 replica `2` 이상, HPA, local limiter 우회 abuse, 이미지 비용·자원
  압박 또는 deployment 전체의 정확한 상한 요구 중 하나가 생기면 같은 Port 뒤의 shared/edge
  limiter를 먼저 설계합니다.

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
- Anonymous read와 image stream은 Community capability가 켜질 때 비활성화할 수 없는
  application origin 보호 baseline을 사용합니다. 각 Pod에서 client key당
  일반 공개 GET `60/minute`·burst `20`,
  image GET `10/minute`·burst `4`, Pod 전체 image stream 동시 `8`개와 전달량
  `64 MiB/minute`를 상한으로 두고 초과 요청은 `Retry-After`가 있는 `429`로 거부합니다.
  Client key는 exact ALB ingress에서 append된 trusted `X-Forwarded-For` chain과 직접 peer를
  함께 검증해 만들며 임의 요청 header를 그대로 신뢰하지 않습니다.
- 이 limiter는 공개 read use case 앞의 Port로 두어 delivery adapter를 교체할 수 있게 합니다.
  현재 local limiter는 deployment 전체가 아니라 Pod별 상한입니다. `replicas: 1`·
  `RollingUpdate`에서는 평상시 위 수치가 전체 처리 상한과 같고, rollout 중 최대 두 Pod가
  겹치면 이론적으로 일반 GET `120/minute`·burst `40`, image GET `20/minute`·burst `8`,
  image stream 동시 `16`개와 `128 MiB/minute`까지 잠시 늘 수 있습니다. Application에
  Kubernetes list/watch 권한을 주거나 구현되지 않은 replica 자동 판정을 주장하지 않습니다.
  Manifest contract test는 exact desired replica·strategy와 HPA 부재를 검증하고, 공개 전 live
  preflight는 rollout이 끝난 steady state에서 Ready endpoint가 하나인지 확인합니다. 조건이
  다르면 Community capability를 켜는 GitOps 변경을 진행하지 않습니다.
- 승인된 활성화 뒤 replica·strategy·selector drift는 Argo CD self-heal과 운영 검증의 실패로
  취급하고 Community capability를 끄는 forward fix 대상입니다. Cluster 관리자 권한으로
  desired state 밖의 matching Pod를 만드는 행위까지 application limiter가 방어한다고
  확대하지 않습니다.
- 상시 다중 replica나 HPA 전환은 같은 Port 뒤에 공유 또는 edge limiter를 두고 deployment
  전체 상한과 origin 우회 차단을 검증한 뒤에만 허용합니다. 이 전환 후보는
  [004 Infrastructure 진화 backlog](../../backlog/2026/004-infrastructure-evolution.md)에 남지만
  현재 공개의 선행 owner로 사용하지 않습니다. 현재 비용 상한은 위 요청·동시성·전달량
  제한이 소유하며 현재 activation은 별도 비용 결정에 의존하지 않습니다.
- Account UUID는 내부 협력에만 사용하고 public read response에는 표시 이름 외 작성자
  identifier를 내보내지 않습니다.

### D17. Failure, recovery와 운영 관측

- DB 저장 실패는 해당 use case 전체를 rollback합니다. S3 실패는 Community transaction을
  rollback시키지 않고 media job을 `RETRY`로 남깁니다.
- Worker는 exponential backoff와 최대 attempt 뒤 `DEAD`로 전환합니다. Media module이
  job 상태와 `last_error_code`의 canonical owner이며 `DEAD`를 일반 API 성공이나 완료
  metric으로 숨기지 않습니다. 첫 범위에는 `DEAD`를 application API로 다시 여는 전이가
  없습니다.
- READY DB update 전에 delivery put이 성공해 orphan object가 생겨도 deterministic key로 retry가
  수렴하고 periodic reconciliation이 DB asset/usage/job과 staging/delivery prefix 차이를
  탐지합니다. 참조가 없는 object 삭제처럼 안전한 수렴은 같은 `RECONCILE` job이 수행하고
  `DONE`으로 닫습니다. Missing object나 metadata mismatch처럼 자동 복구가 안전하지 않은
  불일치가 하나라도 남으면 job을 `DEAD`로 전환하고 비민감 aggregate error code와 건수만
  structured event·metric으로 남깁니다. Object key는 log에 기록하지 않습니다.
- Verify 입력이 영구적으로 잘못됐으면 asset을 `REJECTED`로 전환하고 staging cleanup job을
  저장한 뒤 해당 VERIFY job을 `DONE`으로 닫습니다. 일시적 S3·decode 실패만 `RETRY`를 거쳐
  최대 attempt 뒤 `DEAD`가 됩니다.
- 운영자는 `DEAD` metric·event를 통해 원인을 조사하고 object·설정 같은 외부 원인을
  교정합니다. 현재에는 관리자 requeue·강제 종결과 개별 불일치 관리 화면을 제공하지 않으며,
  새 domain event나 다음 주기 reconciliation이 만드는 새 job은 이전 `DEAD` 기록을 덮어쓰지
  않습니다.
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
  Seed는 `public_id=ed710d1c-91f1-4266-a035-bba33a7f52cb`, `slug=free`, `name=자유게시판`을
  고정해 환경마다 같은 공개 식별자와 표시 이름을 만듭니다.
  기존 account/auth table은 변경하지 않고, 새 table 쪽에서 이미 unique인
  `account(public_id)`를 참조하는 FK만 추가합니다.
- Runtime role에는 새 table DML만 부여하고 schema DDL은 기존 migration job이 소유합니다.
- Community capability는 명시적 `carmunity.community.enabled=false`를 기본값으로 둡니다.
  이 값이 false이면 Community·Media·Moderation·inbox HTTP route를 활성화하지 않고 media
  worker도 새 job을 실행하지 않습니다. true인데 필수 bucket·region·role ARN·projected token
  path, decoder와 limiter 설정이 빠졌으면 application 시작을 실패시킵니다.
- 최초 공개는 두 GitOps 변경으로 나눕니다. 기반 변경은 schema·code·private bucket·OIDC
  identity·worker·metric과 명시적 `replicas: 1`·`RollingUpdate(maxSurge=1,
  maxUnavailable=0)`를 capability off 상태로 배포합니다.
  이후 migration·runtime smoke, OIDC overlap/rollback, `DEAD` metric·structured event,
  rendered manifest와 rollout이 끝난 live Deployment·HPA·Service endpoint를 검증한 activation 변경만
  `carmunity.community.enabled=true`로 전환합니다. Image 없는 부분 기능을 먼저 공개해 현재
  제품 수용 기준을 축소하지 않습니다.
- Runtime에서 저장소·decoder·필수 내부 health가 준비되지 않으면 Community route는
  `503 Service Unavailable`과 비민감 `COMMUNITY_CAPABILITY_UNAVAILABLE` problem code로
  fail-close합니다. Auth 등 기존 비Community route까지 Community 외부 의존 실패로 함께
  내리지 않습니다. Replica·rollout 수는 application이 추측하지 않고 위 GitOps·live
  activation gate가 소유합니다.
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
- JPEG·PNG·단일 frame GIF 검증과 JPEG·PNG 재인코딩은 JDK ImageIO의 실제 decode 결과를
  사용합니다. Format별 byte signature, frame 수, pixel 상한과 corrupt corpus를 검증하고
  runtime reader 등록을 fail-close로 확인합니다.
- 새 format은 decoder 보안, Java 21·ARM64 호환, resource 상한과 정규화 출력의 golden corpus를
  통과한 별도 adapter 결정 없이 MIME allow-list만 늘리지 않습니다. Native library가 필요한
  decoder는 별도 배포·취약점·성능 계약을 먼저 결정해야 합니다.
- 현재 범위에는 Redis, message broker, search engine, CDN, antivirus SaaS와 별도 worker
  deployment를 도입하지 않습니다. DB job과 bounded worker가 현재 신뢰성 요구를 충족하지
  못한다는 측정값이 생기면 각각 독립적으로 재검토합니다.

## API payload 핵심 계약

### Post write

Post 생성 request는 필수 `title`과 body를 받습니다.

```json
{
  "title": "주말 드라이브 코스를 추천해 주세요",
  "body": {
    "version": 1,
    "blocks": [
      { "type": "TEXT", "text": "본문" },
      { "type": "IMAGE", "mediaId": "c860...", "alt": "설명" }
    ]
  }
}
```

Post 수정 request는 변경할 field만 보냅니다. 다음 예시는 제목만 수정하며, body만 보내거나
두 field를 함께 보내는 것도 같은 endpoint에서 처리합니다.

```json
{
  "title": "서울 근교 주말 드라이브 코스를 추천해 주세요"
}
```

Visible post detail response는 post UUID, board slug, 필수 `title`, body, effective visibility,
`createdAt`, nullable `editedAt`, author display, like/dislike count와 인증 사용자의 선택을 분리해
반환합니다. Public response는 internal PK와 account UUID를 포함하지 않습니다.

Post 목록 item은 다음 핵심 projection을 사용합니다.

```json
{
  "id": "post-uuid",
  "board": "free",
  "title": "주말 드라이브 코스를 추천해 주세요",
  "preview": "이번 주말에 서울에서 출발해 반나절 정도 다녀올 코스를 찾고 있습니다.",
  "visibility": "VISIBLE",
  "createdAt": "2026-09-01T10:00:00Z",
  "editedAt": null
}
```

작성자 표시와 reaction field는 위 예시에 생략했지만 D11 projection에 포함합니다. Preview는
write input이 아니며 `body_preview`를 `preview`로 반환합니다. Tombstone detail은 `title`,
`body`, `preview`와 author를 반환하지 않습니다.

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
| 글자 서식 | 새 body schema version과 parser·preview extractor 추가 | 제목 scalar와 v1 reader를 유지하고 기존 row를 강제 migration하지 않음 |
| 추가 정적 이미지 형식 | format별 decoder adapter·signature·resource corpus를 추가하고 JPEG/PNG로 정규화 | 기존 IMAGE block과 media UUID·delivery 계약 유지 |
| 게시글별 알림 해제 | Notification에 post·recipient preference를 추가해 후보 수신자를 filter | Community의 수신 후보와 기존 inbox row를 유지 |
| 검색·인기·추천 | versioned body reader와 reaction source를 소비하는 cursor read projection 추가 | Core write와 현재 번호 page 계약 변경 불필요 |
| 영상·파일 | media type별 검증 adapter와 새 body block 추가 | 기존 IMAGE asset과 v1 post block 해석 유지 |
| 관리자 media job 재처리·감사 | `media_job_action`을 추가해 job·request·actor·상태 전이를 보존 | 기존 `media_job.public_id`와 terminal 상태를 참조해 additive 도입 가능 |
| S3·DB 불일치 개별 추적 | `media_reconciliation_issue`를 추가해 발견·배정·종결 lifecycle을 소유 | 기존 RECONCILE job과 asset public UUID를 source로 사용 가능 |
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
  일치합니다. Local limiter의 경계값과 trusted client key를 검증하고, rendered Deployment가
  exact `replicas: 1`·`RollingUpdate(maxSurge=1,maxUnavailable=0)`이며 HPA가 없음을 확인합니다.
  Cluster integration은 steady state의 Ready endpoint가 하나이고 rollout 중에는 old/new Pod가
  최대 둘까지 겹치며 각 Pod의 limiter와 합산 이론 상한이 계약 범위를 넘지 않음을 확인합니다.
- Media: JPEG·PNG·정적 GIF의 정상·손상 corpus, 위조 MIME, oversized/pixel bomb, WebP,
  animated GIF, SVG·BMP·TIFF·HEIC/HEIF·AVIF, checksum mismatch, 검증 전 원본과
  `HIDDEN_RETAINED|RELEASED` usage가 계약대로 허용 또는 차단됩니다.
- Recovery: worker 중단·재시작, S3 일시 실패와 중복 job 실행이 object/DB 상태를 잃거나 중복
  공개하지 않습니다. Reconciliation의 안전한 자동 복구, 해결 불가능한 `DEAD`·error
  code·metric과 terminal job 비재실행을 검증합니다.
- Activation: capability 기본값이 false이고 필수 설정·storage·decoder·limiter health가
  준비되지 않으면 Community route와 worker가 성공 동작하지 않습니다. 기반 배포와 activation
  변경을 분리하고 live replica·strategy·HPA·Service endpoint preflight가 실패하면 activation을
  중단합니다.
- Query: 실제 PostgreSQL에서 게시글 page 기본값·음수 page·size 하한/상한·전체 건수·전체 범위
  밖의 유효 page와 나머지 cursor 경계, 정렬, tombstone과 batch 조회가 N+1 없이 계약대로
  동작합니다.
- Notification: 답글의 post 작성자·direct parent 조합, 동일 수신자, actor 일치와 inbox insert
  실패를 검증해 수신자 0~2개·자기 알림 제외·중복 방지·transaction rollback을 증명합니다.
- Account relation: 모든 account 공개 UUID FK가 존재하지 않는 식별자와 account 물리 삭제를
  막고, Account table 직접 조회 없이 공개 API로 표시 정책을 적용합니다.
- Post 표현: Unicode 제목 경계값·whitespace 정규화, title-only/body-only PATCH, body와 preview의
  원자적 갱신, 목록의 body JSON 미조회와 tombstone field 비노출을 각각 검증합니다.
- Extension: 새 board, 새 reaction type과 vehicle relation spike가 기존 row migration이나
  public identifier 교체 없이 가능함을 schema/API review로 확인할 수 있습니다.

## 적용 설계 표면 판정

| 설계 표면 | 판정 | 닫힌 위치 |
| --- | --- | --- |
| 사용자·업무 | 적용 | 확정된 15개 제품 결과, 목표 불변식과 visibility·알림·신고 계약에 적용 |
| API | 적용 | D10과 API payload 핵심 계약의 title·preview·tombstone projection |
| Domain | 적용 | D2~D9 |
| Data | 적용 | D12의 10개 table, 미디어 3-table 범위와 무결성, D13 transaction |
| 구조 | 적용 | D1, D19 |
| Security·privacy | 적용 | D14~D16 |
| 품질·성능 | 적용 | D11의 게시글 offset page, 나머지 keyset·batch 조회와 검증 가능성 계약 |
| 운영·복구 | 적용 | D17, D18 |
| Tool·외부 시스템 | 적용 | D14, D15, D19 |
| 문서 | 적용 | 일반 문서는 제품 결과, 이 문서는 기술 목표, 구현 뒤 project/domain/schema SOT가 현재 사실을 각각 소유 |

활성 제품 값 결정은 없습니다. 제품 결과 15개는 사용자 확정 상태이고 D1~D19와 API·DB·검증
계약은 Agent 기술 결정으로 닫혔습니다. API class·method 이름, Flyway version, exact IAM
resource name과 환경별 값은 실행 계획이 source와 함께 배치할 구현 단위이며 제품 결과를
다시 여는 질문이 아닙니다.

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
  [Kubernetes Deployment 전략](https://kubernetes.io/docs/concepts/workloads/controllers/deployment/#strategy),
  [S3 PutObject와 checksum](https://docs.aws.amazon.com/AmazonS3/latest/API/API_PutObject.html),
  [S3 lifecycle](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lifecycle-mgmt.html),
  [JDK ImageIO 표준 format plugin](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/imageio/package-summary.html)
