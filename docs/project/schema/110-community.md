# Community 관련 Schema Reference

이 문서는 Flyway `V20`~`V23`을 적용한 뒤의 일반 커뮤니티·알림·moderation·media 물리
schema를 요약합니다.
실행 source of truth는 migration SQL입니다.

## 소유 경계

| 소유 모듈 | 테이블 | 생명주기 |
| --- | --- | --- |
| `community` | `community_board`, `community_content`, `community_post`, `community_comment`, `community_reaction` | 게시 공간, 콘텐츠 identity·visibility, subtype과 반응 |
| `notification` | `notification_inbox` | 서비스 안 알림과 읽음 상태 |
| `moderation` | `moderation_report` | 신고 접수와 관리자 종결 |
| `media` | `media_asset`, `media_usage`, `media_job` | private object 검증, 게시글 사용 관계와 외부 작업 재시도 |

모든 외부 resource는 UUID `public_id`를 사용하고 내부 조인과 정렬에는 identity `BIGINT`를
사용합니다. Account 관계는 `account(public_id)`를 `ON DELETE RESTRICT`로 참조합니다.

## Community

- `community_board`의 `free` 기준 row UUID는
  `ed710d1c-91f1-4266-a035-bba33a7f52cb`입니다.
- `community_content`는 `POST|COMMENT`, 작성자, post·parent·thread root 관계, 생성 멱등성,
  수정·작성자 삭제·관리자 숨김의 독립 시각과 version을 소유합니다.
- `community_post`는 제목, 목록 preview, schema version `1`과 canonical JSONB body를 저장합니다.
- `community_comment`는 trim 기준 1~2,000자의 plain text를 저장합니다.
- `community_reaction`은 content·account당 한 `LIKE|DISLIKE` 선택을 저장합니다.

Deferred trigger가 post/comment subtype, 같은 post의 direct parent와 top-level root를 최종
검증합니다. 공개 post page는 visible partial index, 대화는 top-level 내림차순·reply 오름차순
index를 사용합니다.

## Notification과 Moderation

`notification_inbox`는 알림 종류, recipient, actor, source comment와 post UUID, nullable
`read_at`을 저장합니다. 자기 알림을 막고 recipient·source당 한 row만 허용합니다.

`moderation_report`는 target UUID·kind, 신고자, `OPEN|CONTENT_HIDDEN|DISMISSED`, 처리자와 처리
시각을 저장합니다. 같은 신고자·대상의 open row는 하나만 허용합니다.

## Media

`media_asset`은 예약·업로드·검증·공개·삭제 상태, private object key, 형식, 크기, checksum,
pixel과 만료 시각을 소유합니다. `media_usage`는 게시글과 asset의
`ACTIVE|HIDDEN_RETAINED|RELEASED` 관계를 소유합니다. `media_job`은 외부 작업의 lease, 최대 8회
retry와 `DONE|DEAD` terminal 결과를 보존합니다.

게시글에서 제거하거나 작성자가 삭제한 `RELEASED` usage는 delivery 삭제 job을 만듭니다.
예약 만료와 24시간 미사용 READY asset을 시간 경과만으로 자동 삭제하는 정책은 schema가 표현할
수는 있지만, 파괴적 lifecycle의 별도 승인과 운영 검증 전에는 worker가 실행하지 않습니다.

## 관련 문서

- [일반 커뮤니티 결과](../../work-items/features/2026/018-general-community.md)
- [일반 커뮤니티 해결 설계](../../work-items/features/2026/018-general-community-design.md)
- [Account 인증/인가 Schema](100-account-auth.md)
