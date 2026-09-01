# 차량과 분리해 시작하는 일반 커뮤니티

## 상태

In Progress

## 현재 위치와 다음 작업

U1~U6와 I1의 repository 후보를 구현했고 전체 local gate를 통과했습니다.

| 구분 | 요약값 |
| --- | --- |
| 제품 결정 | 사용자 확정 15개 |
| 기술 결정 | Agent 결정 D1~D19 |
| 계획 검증 | 통과 |
| 현재 실행자 | 개발 Agent |
| 현재 작업 | Local 검증 완료 후보의 기능별 Git 기록 |
| 사용자 작업 | 현재 local 후보 검토 외 즉시 필요한 결정 없음 |
| 외부 gate | A1 전에 별도 live 권한·Auth Event migration·AWS Block Public Access 대조 필요 |

### 실행 계획의 핵심 입력

- 배포: replica `1`과 `RollingUpdate`
- 공개 보호: Pod별 local limiter
- Rollout 비용: 최대 두 Pod의 일시적 상한 증가
- 상세 계약: [018 일반 커뮤니티 해결 설계](018-general-community-design.md)의 D16·D18

### 보존된 핵심 기준

- 일반 커뮤니티가 차량 기능 없이 독립적으로 존재하고 차량 관계는 후속 module이 게시글
  공개 식별자를 참조해 추가합니다.
- 현재 화면은 1단 답글만 보여 주지만 직접 응답 대상과 최초 댓글을 함께 보존해 미래의 더
  깊은 대화를 막지 않습니다.
- 제목은 본문 version과 분리된 필수 plain text이고, 목록 미리보기는 본문의 TEXT block에서
  만든 별도 read projection입니다.
- 게시글 목록은 페이지 번호로 이동하고, 답글 알림은 원글 작성자와 직접 답변 대상을 함께
  고려하되 자기 알림과 같은 수신자의 중복을 제거합니다.
- 작성자 삭제·관리자 숨김, 반응·신고·서비스 안 알림과 이미지 생명주기는 각각의 원인과
  module 소유권을 잃지 않습니다.
- 게시글 이미지는 JPEG·PNG·정적 GIF 입력만 받아 안전한 JPEG·PNG로 정규화합니다. WebP는
  첫 범위에서 제외하고, 새 형식은 기존 본문·media 식별자를 바꾸지 않는 decoder 경계로
  추가합니다.
- API·DB·동시성·복구·migration 계약이 현재 핵심 기능과 후속 확장을 함께 보호합니다.

## 전체 진행 지도

| 단계 | 상태 | 결과와 전환 조건 |
| --- | --- | --- |
| 1. 결과 정의 | 완료 | 15개 제품 결정과 수용 기준을 사용자가 확정했습니다. |
| 2. 해결 설계 | 완료 | D1~D19의 module·domain·API·DB·보안·운영 계약을 Agent 기술 결정으로 닫았습니다. |
| 3. 실행 계획 | 완료 | 의미 단위, producer·consumer 의존성, TDD·전환·복구와 영구 SOT가 독립 검증을 통과했습니다. |
| 4. 개발 | 완료 후보 | U1~U6와 I1 repository source를 구현했고 자동 만료 삭제는 별도 파괴적 승인 전까지 제외했습니다. |
| 5. 결과 검증·SOT | Local 통과 | 전체 396-test gate, module·format·coverage, script와 rendered manifest 검증을 통과했습니다. |
| 6. Git·remote gate | 후속 | 별도 Git 권한에 따라 commit·push하고 exact SHA의 CI·Sonar를 확인합니다. |
| 7. 기반 배포 | 조건부 | 명시적 live 변경 권한과 Auth Event migration gate를 충족한 뒤 capability off로 배포합니다. |
| 8. 기능 활성화 | 조건부 | storage·OIDC·proxy·limiter·rollout live 검증 뒤 별도 GitOps 변경으로 전체 기능을 함께 엽니다. |
| 9. 종료 | 후속 | 제품 수용 기준의 실제 동작, 복구 경계와 영구 SOT가 모두 닫히면 `Done`으로 전환합니다. |

ServiceAccount, projected web identity token, media bucket·role·trusted proxy staging 설정은
repository manifest에 반영했습니다. 실제 bucket·OIDC provider·IAM role·cluster issuer 생성과
capability 활성화는 live 권한과 A1 검증이 필요한 별도 후속입니다. 외부 AWS·Kubernetes 상태
변경과 push는 현재 실행 범위에 포함하지 않습니다.

## 결과 정의 종료와 설계 인계

결정 색인의 15개 제품 값과 수용 기준은 모두 사용자 확정 상태이며 서로 모순되지 않습니다.
현재 활성 사용자 결정이 없으므로 결과 정의는 닫혀 있고, 이 확정 결과 전체가 해결 설계의
입력입니다.

[018 일반 커뮤니티 해결 설계](018-general-community-design.md)는 기존 제품 결과와 영향받지
않은 기술 기준선을 보존하면서 D16·D18을 보완했습니다. 해결 설계는 닫혔고 전체 계약이
현재 실행 계획의 입력입니다.

## 달성할 결과

차량 관련 기능이나 데이터가 없어도 독립적으로 사용할 수 있는 일반 커뮤니티의 첫 제품
범위를 정합니다. 사용자는 게시글을 중심으로 댓글과 1단 답글을 주고받을 수 있습니다.

현재에는 대화·참여·운영에 필요한 핵심만 포함합니다. 이후 게시판 카테고리, 차량 관계와
추가 커뮤니티 기능을 붙일 때 이미 제공한 일반 커뮤니티를 폐기하거나 차량 전용 커뮤니티로
교체하지 않습니다.

## 범위

- 차량과 연결하지 않고 먼저 제공할 일반 커뮤니티의 제품 범위
- 하나의 자유게시판에서 게시글·댓글·1단 답글로 이어지는 첫 대화 경험
- 비회원도 읽을 수 있는 최신 게시글 목록·상세와 댓글·답글
- 게시글·댓글·답글의 작성·수정·삭제, 좋아요·싫어요, 서비스 안 알림
- 게시글 본문의 이미지, 작성자 표시 이름, 사용자 신고와 관리자 숨김
- 이후 게시판 카테고리, 차량 관계와 커뮤니티 기능을 기존 핵심에 추가하는 제품 경계

## 제외 범위

- 현재 출시 범위의 차량·브랜드·모델·세대·트림 연결 기능
- 무제한 대댓글과 화면에 2단 이상 중첩되는 답글
- 게시글 북마크, 검색·인기·추천 탐색
- 태그, 투표, 팔로우, 개인 메시지, 임시 저장, 게시글별 알림 구독·해제 화면, 실시간 채팅과
  게시물 고정
- 댓글·답글의 이미지, 영상과 일반 파일
- 수정 전 원문을 사용자가 조회하는 편집 이력과 별도 공개 profile
- 이메일·문자·push 알림
- 신고 사유 체계, 제재 수위, 복구·이의제기와 보존 기간 같은 상세 운영 정책

제외한 기능은 영구 폐기하지 않습니다. 실제 제품 필요가 생기면 기존 게시글·대화와 함께
존재하는 후속 기능으로 추가합니다.

## 확정된 제품 결과

### 차량과 커뮤니티 확장 경계

- 현재 일반 커뮤니티는 차량 관련 기능과 데이터에 의존하지 않습니다.
- 미래의 차량 연결은 일반 커뮤니티를 대체하지 않고 기존 핵심에 관계를 추가하는 방식이어야
  합니다.
- 현재에는 핵심 기능만 포함하되, 이후 기능 추가 때문에 먼저 제공한 핵심 사용자 경험을
  폐기하지 않습니다.

### 콘텐츠와 대화

- 첫 콘텐츠 계층은 게시글·댓글·1단 답글입니다.
- 현재 화면에는 2단 이상의 답글 계층을 표시하지 않습니다. 이 표시 제한은 미래의 더 깊은
  대화까지 영구 금지하는 규칙이 아닙니다.
- 답글에 다시 답하면 더 깊게 들여쓰지 않고 같은 최상위 댓글의 1단 답글 묶음에 추가하며,
  누구에게 한 답인지 표시합니다.

### 공개 범위와 게시 공간

- 로그인하지 않은 방문자도 게시글 목록·상세와 댓글·답글을 모두 읽을 수 있습니다.
- 작성과 그 밖의 참여 기능은 현재 account 쓰기 정책을 만족하는 계정에만 허용합니다.
- 첫 게시 공간은 자유게시판 하나입니다. 이후 게시판 카테고리를 추가해도 기존 자유게시판과
  게시글을 그대로 유지합니다.

### 기본 탐색

- 첫 탐색 기능은 최신 게시글 목록과 게시글 상세입니다.
- 모든 게시글은 제목을 가지며, 최신 게시글 목록에서는 제목을 주표시로, 본문에서 만든 짧은
  미리보기를 보조로 보여 줍니다.
- 게시글 상세에서는 제목과 본문을 함께 보여 줍니다.
- 최신 게시글 목록에는 페이지 번호를 표시하고 사용자가 원하는 페이지로 바로 이동할 수
  있습니다.
- 게시글은 최신순, 최상위 댓글은 최신순, 각 댓글의 답글은 오래된 것부터 표시합니다.
- 현재에는 사용자가 정렬 방식을 바꾸는 화면을 제공하지 않습니다.
- 검색·인기·추천 탐색은 현재 범위에 포함하지 않지만, 이후 기존 게시글을 소비하는 별도
  탐색·정렬 기능으로 추가할 수 있습니다.

### 작성·수정과 삭제

- 사용자는 제목과 본문을 입력해 게시글을 작성하고, 자신의 게시글·댓글·답글을
  수정·삭제할 수 있습니다.
- 수정된 콘텐츠에는 `수정됨`을 표시하고, 수정 전 원문을 사용자가 보는 편집 이력은 현재
  제공하지 않습니다.
- 게시글의 제목이나 본문을 수정해도 `수정됨`을 표시합니다.
- 작성자가 게시글·댓글·답글을 삭제하면 제목을 포함한 원문과 작성자 표시는 보여 주지
  않습니다.
- 하위 대화가 있으면 `삭제된 글/댓글` 자리와 남은 대화를 유지하고, 하위 대화가 없으면
  화면에서 제거합니다.
- 작성자 삭제와 관리자의 숨김은 서로 다른 제품 동작입니다.

### 참여와 결과 표시

- 사용자는 게시글·댓글·답글에 좋아요 또는 싫어요를 선택하고 취소할 수 있습니다.
- 좋아요와 싫어요 수는 하나의 점수로 합치지 않고 각각 표시합니다.
- 여러 감정 반응과 이 반응을 이용한 추천 점수는 현재 범위에 포함하지 않되, 후속 반응
  종류를 기존 대화와 함께 추가할 수 있어야 합니다.

### 이미지

- 게시글은 텍스트 본문에 이미지를 함께 사용할 수 있습니다.
- 댓글과 답글은 텍스트만 작성합니다.
- 영상과 일반 파일은 현재 지원하지 않습니다. 이후 지원 종류를 추가해도 기존 게시글과
  이미지를 교체하지 않습니다.
- 이미지의 안전한 업로드·노출, 게시글 연결, 미사용 이미지 정리와 게시글 삭제 뒤 처리는
  보존된 해결 설계가 닫은 책임이며 현재 제목 제품 값과 독립적으로 유효합니다.

### 게시글 표현의 확장 경계

- 이후 글자 서식을 추가하더라도 기존 게시글이 사라지거나 새 형식으로 강제 재작성되지 않고
  계속 읽을 수 있어야 합니다.

### 서비스 안 알림

- 사용자는 자신의 게시글에 달린 최상위 댓글과 모든 답글을 서비스 안에서 확인할 수
  있습니다.
- 답글의 직접 답변 대상도 해당 답글을 서비스 안에서 확인할 수 있습니다.
- 자신이 만든 대화에는 자신에게 알리지 않습니다.
- 하나의 작성 동작으로 같은 사용자가 여러 알림 조건에 해당해도 알림은 하나만 만듭니다.
- 현재에는 게시글별 알림 구독·해제 화면을 제공하지 않지만, 이후 원글 작성자가 특정
  게시글의 후속 알림을 끌 수 있도록 수신자 계산과 구독 정책을 분리할 수 있어야 합니다.
- 이메일·문자·push는 현재 제공하지 않으며, 전달 방식은 기존 대화와 별도로 확장합니다.

### 작성자 표시

- 게시글·댓글·답글에는 작성자의 표시 이름만 보여 줍니다.
- 표시 이름을 누르는 별도 공개 profile이나 다른 사용자의 활동 목록은 제공하지 않습니다.
- 현재 profile 화면이 없더라도 콘텐츠와 작성자의 안정된 관계를 유지해 후속 공개 profile을
  추가할 수 있어야 합니다.

### 최소 운영 기능

- 쓰기 가능한 사용자는 게시글·댓글·답글을 신고할 수 있습니다.
- 관리자는 신고를 확인하고 대상 콘텐츠를 숨길 수 있습니다.
- 숨긴 콘텐츠는 일반 열람 결과에서 원문을 노출하지 않으며, 남은 대화의 맥락을 끊지 않는
  표시 계약은 해결 설계에서 작성자 삭제와 분리해 닫습니다.
- 계정 제재, 신고 사유 체계, 복구·이의제기, 보존 기간은 별도 제품·운영 작업입니다.

## 확정 결정 색인

| 결정 | 확정값 | 제품 결과 |
| --- | --- | --- |
| 공개 | A | 비회원도 게시글과 전체 대화를 읽음 |
| 게시공간 | A | 자유게시판 하나로 시작 |
| 작성 | A | 작성·수정·삭제와 `수정됨` 표시 |
| 재응답 | A | 같은 1단 묶음에 추가하고 대상 표시 |
| 참여 | C | 좋아요와 싫어요 제공 |
| 반응표시 | A | 좋아요·싫어요 수를 각각 표시 |
| 미디어 허용 위치 | B | 게시글 본문에만 미디어 허용 |
| 미디어종류 | A | 이미지만 허용 |
| 대화순서 | B | 최신 댓글부터, 답글은 시간순 |
| 알림 | B | 서비스 안 알림 |
| 알림 수신 | A | 원글 작성자는 전체 대화, 직접 답변 대상은 해당 답글 알림 수신 |
| 프로필 | A | 표시 이름만 노출 |
| 운영 | A | 사용자 신고와 관리자 숨김 |
| 제목 | A | 제목 필수, 목록 제목 주표시와 본문 미리보기 보조 표시 |
| 목록 이동 | A | 최신 게시글 목록에서 페이지 번호 이동 제공 |

위 15개 결정은 사용자 확정 상태이며 결과 정의 단계가 닫혔습니다. 현재 열린 제품 결정은
없습니다. 글자 서식은 현재
범위에 추가할 제품 결정이 아니라, 향후 추가하더라도 기존 게시글을 계속 읽을 수 있어야
한다는 확장성 질문이며 해결 설계의 본문 호환성 계약에서 다룹니다.

## 게시글 제목을 포함한 근거

게시글 제목을 필수로 받는 방식과 별도 제목 없이 본문의 첫 문장을 목록 대표 문구로 쓰는
방식을 비교했습니다. 제목을 받으면 작성 입력이 하나 늘지만, 사용자가 최신 글 목록을 빠르게
훑을 수 있고 본문 첫 부분을 수정해도 글의 대표 이름이 뜻하지 않게 바뀌지 않습니다. 따라서
첫 범위에는 필수 제목을 포함하고, 목록에서는 제목과 본문 미리보기의 역할을 분리합니다.

짧은 한 줄 게시처럼 제목 입력 비용이 대화 참여를 방해하는 별도 게시 형식이 제품 범위로
확정되면 그 형식의 제목 정책을 다시 검토합니다. 현재 일반 게시글의 제목 필수 계약을
제목 없는 다른 형식까지 영구 강제하지 않습니다.

## 원글 작성자에게 답글도 알리는 근거

최상위 댓글만 원글 작성자에게 알리는 방식과, 원글의 답글까지 모두 알리는 방식을
비교했습니다. 후자는 대화가 활발할 때 알림이 많아질 수 있지만 원글 작성자가 자신의 글에서
이어지는 대화를 놓치지 않습니다. 따라서 첫 범위에서는 원글 작성자에게 전체 대화를 알리고,
답글의 직접 대상도 함께 알립니다. 두 조건이 같은 사용자를 가리키면 알림 하나로 합치고
작성자 자신의 동작은 제외합니다.

알림량을 사용자가 조절할 필요가 생기면 게시글별 알림 해제를 추가합니다. 현재에는 그 화면을
미리 만들지 않지만, 수신자 계산과 구독 여부를 분리해 기존 inbox와 대화 관계를 바꾸지 않고
추가할 수 있어야 합니다.

## 게시글 목록에 페이지 번호를 제공하는 근거

다음 글 묶음을 계속 불러오는 방식은 새 글이 추가돼도 이어보기 경계가 안정적이지만 원하는
목록 위치로 바로 이동하기 어렵습니다. 일반 자유게시판에서는 사용자가 여러 페이지를
오가며 과거 글을 찾는 동작이 더 익숙하므로 첫 범위의 게시글 목록은 페이지 번호를
제공합니다.

새 글이 추가되면 기존 페이지의 글이 뒤로 밀릴 수 있고 매우 뒤쪽 페이지의 조회 비용이
커질 수 있습니다. 실제 규모에서 이 비용이 문제가 되면 번호 목록의 유지 범위를 다시
정하고, 검색·추천처럼 연속 탐색이 필요한 별도 목록에는 cursor 방식을 추가합니다.

## 1단 답글을 선택한 근거

특정 댓글에 직접 응답하는 경험은 첫 범위에 포함하되, 무제한 중첩으로 생기는 가독성과 운영
복잡도는 현재 범위에 넣지 않습니다. 유명 서비스도 한 방식만 사용하지 않습니다. YouTube는
[최상위 댓글과 답글](https://developers.google.com/youtube/v3/docs/commentThreads)을 한
스레드로 묶고, Reddit은 댓글이
[게시글 또는 다른 댓글을 부모로 가질 수 있는 구조](https://developers.reddit.com/docs/api/redditapi/models/classes/Comment)를
사용합니다. GitHub Discussions는
[댓글과 답글 스레드](https://docs.github.com/en/graphql/guides/using-the-graphql-api-for-discussions)를
구분하고, Discourse는 평면 대화와 함께
[중첩 답글 보기](https://meta.discourse.org/t/introducing-nested-replies/402812)를
제공합니다. 따라서 서비스 규모를 모방하지 않고 현재 대화 목적에 맞춰 1단을 선택했습니다.

## 현재 source가 고정한 제약

- 쓰기 기능은 현재 account 정책상 `ACTIVE` account의 full session이며 필수 비밀번호 변경이
  남아 있지 않은 계정만 사용할 수 있습니다. 일반 회원은 이메일 소유권 확인으로 `ACTIVE`가
  되고, 초대 관리자는 초대 token을 수락해 초대된 이메일의 소유권을 확인한 뒤 `ACTIVE`가
  됩니다. 최초 최고 관리자는 이메일 소유권 확인 없이 운영 명령으로 `ACTIVE` 생성되지만
  최초 비밀번호 변경을 마치기 전에는 일반 쓰기 기능을 사용할 수 없습니다. 정지·탈퇴
  계정이나 제한 session의 접근 정책을 이 작업에서 우회하지 않습니다.
- 별도 공개 profile은 현재 제품 범위가 아닙니다. 작성자 표시 이름을 얻기 위해 account의
  내부 구현이나 table을 다른 module이 직접 참조하지 않습니다.

## 수용 기준

- 차량 데이터가 없어도 사용자가 일반 커뮤니티의 최신 게시글 목록과 상세에서 대화를
  시작하고, 게시글 아래에 댓글과 1단 답글을 작성할 수 있습니다.
- 로그인하지 않은 방문자도 목록·상세와 전체 대화를 읽을 수 있고, 쓰기·반응·신고는 현재
  account 쓰기 정책을 만족한 사용자만 할 수 있습니다.
- 화면에는 게시글·댓글·1단 답글만 나타나고 2단 이상의 답글 들여쓰기는 나타나지 않습니다.
- 답글에 다시 답해도 같은 1단 묶음에 대상과 함께 나타납니다.
- 게시글·댓글·답글을 수정하면 `수정됨`이 보이고, 삭제한 원문은 보이지 않습니다. 하위
  대화가 있으면 삭제 자리를 통해 남은 맥락을 읽을 수 있습니다.
- 게시글·댓글·답글에는 좋아요와 싫어요 수가 각각 보이며, 한 사용자가 자신의 선택을
  바꾸거나 취소해도 각 수치가 중복 반영되지 않습니다.
- 게시글 본문에는 안전하게 노출되는 이미지를 함께 넣을 수 있고 댓글·답글은 텍스트만
  작성합니다.
- 내 게시글의 새 댓글·답글과 나를 직접 대상으로 한 답글은 서비스 안 알림으로 확인할 수
  있고, 두 조건의 수신자가 같으면 하나만 생기며 내 동작에 대한 자기 알림은 생기지 않습니다.
- 모든 콘텐츠에는 표시 이름만 나타나고 공개 profile은 열리지 않습니다.
- 사용자는 콘텐츠를 신고할 수 있고 관리자는 신고를 확인해 원문을 숨길 수 있으며, 관리자
  숨김을 작성자 삭제로 기록하거나 노출하지 않습니다.
- 현재 기능과 나중에 추가할 게시판 카테고리·차량 관계·부가 기능을 구분할 수 있으며,
  후속 확장이 기존 게시글과 대화 경험을 교체한다는 전제가 없습니다.
- 게시글 작성에는 제목과 본문이 필요하며, 목록에는 제목이 주표시로, 본문에서 만든 짧은
  미리보기가 보조로 나타나고 상세에는 제목과 본문이 함께 나타납니다.
- 최신 게시글 목록에서 페이지 번호를 선택해 원하는 페이지로 바로 이동할 수 있습니다.
- 게시글의 제목이나 본문을 수정하면 `수정됨`이 보이며, 삭제되거나 관리자에게 숨겨진
  게시글의 제목과 본문은 일반 열람 결과에 나타나지 않습니다.
- 15개 제품 결정과 수용 기준이 서로 모순 없이 정렬되고 결과 정의 단계가 닫혀 있습니다.
- 해결 설계는 제품 확정 기준선과 영향받지 않은 기술 결정을 보존하고, 구현 불가능한 replica
  자동 판정과 `Recreate`를 D16·D18의 명시적 singleton `RollingUpdate`·activation gate로
  국소 보완했습니다.

## 요구사항과 실행 결과 연결

| 결과 ID | 관찰할 제품 결과 | 구현·검증 owner |
| --- | --- | --- |
| R1 | 차량 데이터 없이 자유게시판이 독립적으로 존재합니다. | U1 schema·module, U2 게시글 slice |
| R2 | 제목·본문 게시글의 번호 page, 상세, 수정·삭제와 tombstone이 계약대로 동작합니다. | U2 게시글 slice |
| R3 | 1단 표시 대화와 직접 응답 대상이 보존되고 inbox 알림이 대화와 원자적으로 생성됩니다. | U3 대화·알림 |
| R4 | 반응 선택과 신고·관리자 숨김이 중복·동시 요청에도 정해진 상태로 수렴합니다. | U4 참여·운영 |
| R5 | 안전한 게시글 이미지만 private S3 생명주기와 결속되고 검증 전·숨김 이미지는 노출되지 않습니다. | U5 media, I1 storage·identity |
| R6 | anonymous read 보호, capability 전환, migration·rollback과 후속 확장 경계가 기존 API를 훼손하지 않습니다. | U6 통합, V1 검증, A1·A2 전환 |

## 구현·검증 실행 계획

공통 계약과 이를 처음 소비하는 대표 결과를 먼저 검증한 뒤 나머지 기능을 확장합니다.
U3와 U4는 U2 뒤 논리적으로 독립적이지만 자동 병렬 실행을 허용한다는 뜻은 아닙니다. U5의
asset·worker core는 U2 뒤 시작할 수 있어도 관리자 숨김과 media usage 결속까지 완료하려면
U4의 visibility 계약이 먼저 필요합니다. 실제 write set이 겹치면 같은 worktree에서 직렬로
구현합니다.

```text
U1 공통 경계·schema -> U2 게시글 대표 slice -> {U3 대화·알림, U4 참여·운영}
U2 + U4 ---------------------------------------> U5 media 완료
I1 storage·workload identity ----------------------------------------------┐
U3 + U5 ------------------------------------------------------------------> U6 통합·보호
U6 -> V1 전체 검증·영구 SOT -> Git·remote gate -> A1 capability-off 기반 -> A2 활성화
```

### U1. Module 경계와 additive schema

- **만들 결과**: `community`, `media`, `moderation` module과 기존 `notification`, `account`
  공개 계약의 compile-time 경계를 만들고, Community core 5개 table과 고정 `free` board seed를
  additive migration으로 제공합니다. Community capability는 계속 기본 `false`입니다.
- **Producer → consumer**: Account의 쓰기 가능 상태·표시 이름 batch API, Media claim API와
  Notification inbox store 계약이 producer입니다. U2가 Account·Media 계약을, U3가 Notification
  계약을 처음 소비합니다.
- **변경 경계**: D1의 bounded context·aggregate 방향, D2·D12의
  `community_board|content|post|comment|reaction`, module `package-info`, 공개 DTO·Port만 만듭니다.
  상대 module `internal`이나 table을 읽지 않습니다.
- **Migration 규칙**: 구현 시점 HEAD에서 다음 비어 있는 Flyway version을 사용하고 이미 적용된
  versioned file을 수정하지 않습니다. UUID Account FK, subtype·parent/root·reaction·visibility
  check와 index를 실제 PostgreSQL이 최종 강제합니다.
- **TDD·검증**: 먼저 Modulith/ArchUnit 경계 실패와 PostgreSQL migration·constraint 실패
  사례를 만들고 최소 구현으로 통과시킵니다. Capability off에서 새 HTTP route와 worker가
  생성되지 않는 context test도 이 단위가 소유합니다.
- **복구·관찰**: 실패 시 capability off와 additive table을 보존하고 forward migration으로만
  수정합니다. 완료 뒤 clean PostgreSQL에 5개 table·seed가 생기며 잘못된 subtype·부모·Account
  참조를 DB가 거부합니다.

### U2. 제목·본문 게시글 대표 vertical slice

- **만들 결과**: 제목과 versioned body를 가진 게시글 작성, 최신 번호 page, 상세, ETag 기반
  수정과 멱등 삭제를 Domain → UseCase → Port → Web/persistence adapter 한 경로로 완성합니다.
- **의존성**: U1의 Community schema, Account 공개 API, Media claim 계약을 소비합니다. Media
  adapter가 아직 준비되지 않은 test에서는 text-only 본문 또는 명시적 fake Port를 사용하며,
  이 중간 결과를 public으로 활성화하지 않습니다.
- **변경 경계**: `Idempotency-Key`, canonical body hash·preview, `If-Match`, 공개 UUID,
  anonymous GET·full-session write, author·reaction batch projection과 page `0..N` 계약을
  구현합니다. 차량·검색·추천 관계를 추가하지 않습니다.
- **TDD·검증**: 제목 Unicode·whitespace, body v1, 같은/다른 idempotency payload, stale ETag,
  tombstone, 음수·범위 밖 page, 고정 정렬, 목록 body JSON 미조회와 N+1 부재를 domain,
  application, MVC, repository·PostgreSQL 순서로 검증합니다.
- **복구·관찰**: Post 저장과 preview·media claim 중 하나만 commit되는 부분 성공을 허용하지
  않습니다. 완료 뒤 내부 test profile에서 text-only 게시글의 전체 왕복이 처음 관찰되지만
  capability off이므로 staging 사용자에게는 노출되지 않습니다.

### U3. 댓글·답글과 서비스 안 알림

- **만들 결과**: 최상위 댓글과 직접 parent/root를 보존하는 답글, 댓글 cursor 조회, inbox
  목록·읽음 처리를 완성하고 `notification_inbox` migration을 추가합니다.
- **의존성**: U2의 post/content identity와 U1의 Notification 공개 store 계약을 소비합니다.
  Community가 수신 후보를 계산하고 Notification이 inbox aggregate·table을 소유합니다.
- **변경 경계**: 답글은 화면 depth `1`로 투영하되 reply-to-reply의 direct parent를 잃지
  않습니다. 최상위 댓글은 원글 작성자, 답글은 원글 작성자와 direct parent 작성자를 후보로
  삼고 actor·중복을 제거합니다.
- **TDD·검증**: 다른 post parent/root 거부, reply 정렬, 수신자 `0..2`, 같은 수신자, 자기 알림,
  재시도와 inbox unique를 먼저 실패시킵니다. Inbox insert가 실패하면 comment도 rollback되는
  실제 PostgreSQL transaction test를 필수로 둡니다.
- **복구·관찰**: 대화와 inbox를 별도 보상 작업으로 맞추지 않습니다. 완료 뒤 댓글 하나가
  저장되면 필요한 inbox row만 같은 transaction에서 관찰됩니다.

### U4. 반응·신고·관리자 숨김

- **만들 결과**: 좋아요·싫어요 선택·교체·취소, 사용자 신고, 관리자 report 조회·resolution과
  Community visibility 전환을 완성하고 `moderation_report` migration을 추가합니다.
- **의존성**: U2의 content identity·visibility 공개 계약을 소비합니다. Moderation은 Community
  공개 hide 계약을 호출하고 상대 repository를 사용하지 않습니다.
- **변경 경계**: 사용자·content당 reaction 하나, 동일 사용자의 open report 하나,
  `HIDE_CONTENT|DISMISS`, report ETag, 작성자 삭제와 관리자 숨김의 독립 사실을 구현합니다.
- **TDD·검증**: 동시 reaction upsert/delete, 중복 report, stale resolution, 이미 삭제·숨김된
  대상, hide와 open report 종결의 원자성, 일반 사용자와 admin 권한 분리를 실제 DB와 MVC에서
  검증합니다.
- **복구·관찰**: 숨김 실패 시 report만 종결되지 않으며, 이미 성공한 숨김을 작성자 삭제로
  되돌리지 않습니다. 완료 뒤 일반 조회의 tombstone과 관리자 report 사실이 동시에 보존됩니다.

### U5. Media lifecycle와 post 결속

- **만들 결과**: `media_asset`, `media_usage`, `media_job` migration, multipart reservation·upload,
  JDK ImageIO 검증·정규화, private S3 adapter, bounded worker, 공개 image stream과 post
  claim/release/hide를 완성합니다.
- **의존성**: U1의 Media 공개 API와 U2의 post transaction을 소비해 asset·worker core를
  시작하고, U4의 visibility transaction이 준비된 뒤 hide usage 결속까지 완료합니다. AWS SDK
  for Java 2.x의 S3·STS web identity 타입은 storage adapter 안에만 둡니다.
- **변경 경계**: JPEG·PNG·정적 GIF만 받아 JPEG·PNG로 재인코딩하고 WebP를 포함한 제외 형식,
  10 MiB·24 MP·10 images 상한, checksum, deterministic delivery key, lease와 8회 retry,
  `ACTIVE|HIDDEN_RETAINED|RELEASED` usage를 구현합니다.
- **TDD·검증**: 정상·손상·위조·pixel bomb·animated corpus, bounded `/tmp` cleanup, S3 성공 뒤 DB
  실패, lease 만료·늦은 worker, 중복 job, orphan reconciliation, 숨김 직후 404와 공개 stream
  동시성·전달량을 검증합니다. 외부 S3 없이 domain·worker·SDK request 계약을 먼저 닫고 실제
  STS/S3는 A1 live qualification이 소유합니다.
- **복구·관찰**: 외부 I/O 중 DB transaction을 열지 않고 job 상태로 재시도합니다. Object를
  자동 공개하거나 rollback 때 삭제하지 않습니다. 완료 뒤 fake storage와 PostgreSQL에서
  upload부터 READY·claim·stream·release까지 수렴합니다.

### I1. Private storage와 workload identity 기반

- **만들 결과**: D15의 media bucket, 별도 public OIDC metadata bucket, IAM OIDC provider와
  media role, application ServiceAccount·projected token, cursor secret 전달과 issuer 전환·회전
  절차를 repository source로 만듭니다.
- **Resource identity**: bucket은 각각
  `car-community-stg-media-<AWS_ACCOUNT_ID>-ap-northeast-2`와
  `car-community-stg-oidc-<AWS_ACCOUNT_ID>-ap-northeast-2`, role은
  `car-community-stg-media-role`, ServiceAccount는
  `car-community/car-community-application`, token path는
  `/var/run/secrets/aws/serviceaccount/token`, token·STS session은 `3,600초`를 사용합니다.
  Account ID·ARN·VPC CIDR은 live source에서 파생하며 사용자 선택값으로 만들지 않습니다.
- **도구·문서 경계**: 현재 공식 AWS CLI·kubeadm·kubectl과 GitOps manifest를 사용하고 새 IaC
  framework를 도입하지 않습니다. 최초 AWS·issuer mutation, 기존 cluster의 multi-issuer
  overlap, key rotation은 목적·권한·복구가 다른 재사용 runbook으로 분리하고, 반복 검증만 좁은
  verifier/script가 정당한 경우에 둡니다.
- **검증**: Manifest contract는 automount off, exact audience·TTL·path·ServiceAccount,
  prefix 최소 권한과 node credential fallback 금지를 확인합니다. Media bucket의 전체 public
  차단·SSE-S3와 identity bucket의 두 metadata object만 공개되는 policy를 분리합니다. Runbook/source
  검증은 live 성공을 대신하지 않으며 Organizations·Account·bucket Block Public Access,
  discovery/JWKS HTTPS, STS subject/audience와 S3 prefix 접근은 A1에서 확인합니다.
- **권한·복구**: 이 계획은 AWS·control-plane 변경 권한이 아닙니다. Live 실행 전 exact target과
  old/new issuer, token TTL, 복귀 순서를 제시해 명시적 허락을 받습니다. 첫 범위는 new issuer로
  발급하면서 old issuer 검증을 계속 유지합니다. 상위 Block Public Access가 public policy를
  막으면 이를 낮추지 않고 D15를 다시 엽니다. 실패하면 old issuer를 발급 우선으로 복귀시키되
  new issuer·key·provider는 token TTL과 실제 consumer 확인 전에 삭제하지 않습니다.

### U6. Capability, 보안, 관측과 GitOps 합류

- **만들 결과**: U2~U5와 I1의 contract를 한 application에 조립하고 공개 GET allow-list,
  full-session/admin 정책, signed cursor, trusted proxy client key, bounded local limiter,
  health·metric·structured event와 capability off/on 설정을 완성합니다.
- **설정 계약**: `COMMUNITY_ENABLED=false`가 기본이며 cursor HMAC secret, media bucket·region·
  role ARN·token path, trusted proxy CIDR, limiter·worker 값은 validated
  `@ConfigurationProperties`로 한 번 binding합니다. Cursor secret은 JWT·identifier secret과
  다른 `car-community-community` Secret이 소유합니다. `.env.example`·`application.yaml`,
  staging ConfigMap·Secret producer와 Deployment env consumer를 같은 변경에서 동기화하고,
  Migration Job은 capability 기본값 false에서 media secret 없이도 계속 기동돼야 합니다.
- **배포 계약**: Base Deployment에 `replicas: 1`, `RollingUpdate(maxSurge=1,
  maxUnavailable=0)`, exact ServiceAccount·projected token을 명시하고 HPA를 두지 않습니다.
  Capability가 false이면 route·worker가 비활성이고, true인데 필수 dependency가 없으면
  Community만 `503`으로 fail-close합니다. Community dependency health는 route·worker와 별도
  Actuator component가 사용하되 production readiness group에는 넣지 않아 기존 Auth API와 Pod
  readiness를 함께 내리지 않습니다.
- **TDD·검증**: spoofed/valid XFF, token bucket 경계·10,000 key 상한·TTL·동시 update,
  image semaphore·byte budget, 429 `Retry-After`, cursor tamper·cross-Pod shared secret,
  capability context와 rendered manifest를 검증합니다. Staging remote address·append chain은
  source만으로 확정하지 않고 A1 read-only qualification에서 확인합니다.
- **복구·관찰**: 통합 실패 시 capability를 false로 유지하는 forward fix를 사용합니다. 완료 뒤
  모든 local 기능은 하나의 검증 후보가 되지만 public activation은 아직 하지 않습니다.

### V1. 전체 검증과 영구 SOT 승격

- **Local gate**: 각 단위의 가장 좁은 test 뒤 Docker daemon preflight와 재사용 없는
  `./gradlew check --no-daemon --rerun-tasks --console=plain`을 실행합니다. 변경한 Shell이 있으면
  exact path의 `bash -n`, pinned ShellCheck와 contract test를 추가하고, Kustomize render·link·
  `git diff --check`를 별도로 확인합니다.
- **독립 검증**: 구현 Agent의 설명이 아니라 최초 목적, 이 문서의 R1~R6, D1~D19와 실제 code·
  migration·manifest·test를 다시 읽어 module, data, transaction, concurrency, security, media,
  recovery와 activation 축을 독립 감사합니다. 미충족 항목은 영향 단위만 다시 열고 전체
  문서를 삭제하지 않습니다.
- **영구 SOT**: 실제 구현이 생긴 뒤 `domain`에는 Community·Media·Moderation·Notification의
  업무 상태와 전이를 목적별 문서로, `project/schema`에는 bounded-context별 최종 10-table
  schema를, project module map·current architecture에는 실제 공개 의존 방향을 반영합니다.
  Storage·OIDC·GitOps 현재 사실은 infrastructure 문서가, 최초 구축·rotation·qualification의
  exact 실행은 각각 operations runbook이 소유합니다.
- **Git gate**: 검증 후보를 동결한 뒤 구현·migration, infrastructure·operations, SOT 동기화를
  의존 가능한 commit 경계로 나눕니다. Push는 별도 사용자 권한이 있을 때만 수행하고 exact SHA의
  required CI·Sonar Quality Gate·open issue를 확인하기 전에는 전체 완료로 보고하지 않습니다.

### A1. Capability-off 기반 배포

- **선행 gate**: 별도 live 변경 권한, exact image SHA, I1 resource 준비와
  `flyway_schema_history` V7·V10 checksum·기존 `auth_event` 값의 read-only 대조가 필요합니다.
  AWS Organizations·Account·identity bucket의 effective Block Public Access가 두 metadata
  object 공개 policy를 허용하는지도 mutation 전에 확인합니다. 상위 정책이 막으면 account
  guard를 자동 완화하지 않고 D15 대안 검토로 돌아갑니다.
  현재 source와 일치하지 않으면 repair·validation 우회를 금지하고
  [001 Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md)의
  호환성 완료까지 중단합니다.
- **전환**: schema·code·identity·bucket·worker·metric과 rollout 계약을
  `COMMUNITY_ENABLED=false`로 먼저 배포합니다. 기존 Auth/API smoke, migration runtime 권한,
  discovery/JWKS·STS·S3 prefix, 3,600초 projected token 갱신, worker off,
  rendered/live replica·strategy·HPA를 확인합니다. S3·STS를 의도적으로 실패시킨 좁은 검증에서도
  Community만 unavailable이고 기존 Auth 요청과 전역 readiness가 유지돼야 합니다.
- **복구**: 실패하면 capability off를 유지하고 schema·object·OIDC resource를 삭제하지
  않습니다. 이전 image가 새 schema를 소비할 수 있음이 검증되지 않으면 image rollback 대신
  forward fix를 사용합니다.

### A2. 전체 기능 활성화와 제품 수용

- **선행 gate**: A1 전체 성공, steady Ready endpoint `1`, rollout 중 최대 Pod `2`, 실제
  ALB→NodePort remote address/XFF 신뢰 경계, limiter와 이미지 stream 상한, DEAD signal,
  anonymous·member·admin smoke를 충족해야 합니다.
- **전환**: 별도 GitOps 변경 하나가 `COMMUNITY_ENABLED=true`로 바꾸며 게시글·대화·반응·신고·
  inbox·이미지를 함께 엽니다. Image 없는 축소 기능을 먼저 공개하지 않습니다.
- **수용·복구**: R1~R6의 실제 HTTP 결과와 DB·S3 상태를 확인합니다. 실패하면 activation
  변경만 되돌려 capability를 끄고 data·object를 보존한 채 원인을 수정합니다. 상시 replica
  `2` 이상이나 HPA가 필요해지면 활성화를 강행하지 않고 shared/edge limiter 설계를 먼저
  엽니다.

## 현재 남은 결정과 실행 권한

- 제품 결정과 Agent 기술 결정은 모두 닫혀 있으며 구현을 막는 미결정은 없습니다.
- AWS account ID, ARN, bucket 존재 상태, trusted proxy의 live remote address와 Flyway checksum은
  선택 질문이 아니라 A1에서 authoritative source로 확인할 환경 값입니다.
- 현재 요청은 구현·검증·연관 문서 동기화와 기능별 local commit까지입니다. Push와
  AWS·Kubernetes·staging mutation 권한은 포함하지 않습니다.

## 관련 문서

- **상위 제품 목표**: [100 제품 개요](../../../product/100-product-overview.md)
- **현재 제품 범위 경계**: [200 기능 범위](../../../product/200-feature-scope.md)
- **현재 account 정책**: [100 Account](../../../domain/100-account.md),
  [110 Account 인증·인가 정책](../../../domain/110-account-auth-policy.md)
- **현재 module 상태**: [210 모듈 지도](../../../project/210-module-map.md)
- **보존된 기술 기준선**: [018 일반 커뮤니티 해결 설계](018-general-community-design.md)
- **계획 단계와 artifact 기준**: [100 작업 문서 생명주기](../../100-work-item-lifecycle.md#계획-단계와-최소-artifact)
