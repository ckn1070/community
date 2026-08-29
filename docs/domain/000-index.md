# 도메인 문서 목차

이 문서 그룹은 현재 확정된 업무 용어, 상태, 정책, 상태 전이를 소유합니다.
일반 DDD 개념은 `architecture`, 물리 DB 구조는 `project/schema`, 미확정 영역은 product와 backlog에서 확인합니다.

## 번호 그룹

| 번호대 | 책임 |
| --- | --- |
| `100`~`190` | 현재 domain 개념과 account·auth 정책 |

## 현재 도메인

현재 구현과 영구 정책이 있는 영역은 account, auth와 자동차 카탈로그입니다.

- [100-account.md](100-account.md): account, profile, credential, role, refresh token, verification token의 업무 의미
- [110-account-auth-policy.md](110-account-auth-policy.md): account status, session access, password 변경 상태, role별 API 접근 정책
- [120-auth-event-policy.md](120-auth-event-policy.md): 인증·인가 감사 이벤트의 생성 조건, 결과, reason, metadata 정책
- [130-car-catalog.md](130-car-catalog.md): 자동차 제품·기술·판매 정보의 개념, 관계, 상태와 조회 의미

## 제품 범위와 미래 영역

커뮤니티, 공개 member profile, moderation, media, 중고차 listing은 현재 확정된 상세 도메인 문서가 없습니다.
제품 방향은 [제품 개요](../product/100-product-overview.md), 기능 범위와 제품 후보는
[기능 범위](../product/200-feature-scope.md)와
[AutoSquare 제품 후보](../work-items/backlog/2026/002-autosquare-mvp-candidate.md), 횡단
architecture·privacy·운영 결정은
[프로젝트 횡단 열린 결정](../work-items/backlog/2026/001-project-open-decisions.md)에서 확인합니다.

미래 영역 이름을 영구 도메인 문서나 디렉터리로 미리 만들지 않습니다.
실제 작업에서 용어, 생명주기, 상태 전이가 확정될 때 관련 문서를 추가합니다.

## 작성 기준

- 업무 의미와 정책만 기록합니다.
- 구현 class와 package는 정책을 설명하는 데 필요한 최소 범위만 언급합니다.
- table, column, constraint의 정확한 구조는 project schema reference가 소유합니다.
- 확인되지 않은 정책은 추측하지 않고 work item의 남은 결정에 둡니다.
- 작업 기록, 회고, 진행률, 특정 commit 상태는 두지 않습니다.

## 관련 문서

- **현재 구현 상태**: [프로젝트 문서](../project/000-index.md)
- **제품 목표·범위**: [제품 문서](../product/000-index.md)
- **용어 경계**: [아키텍처 개념](../architecture/110-architecture-concepts.md)
- **변경 계획·결과**: [작업 문서](../work-items/000-index.md)
