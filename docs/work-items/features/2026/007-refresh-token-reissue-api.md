# Refresh Token 재발급 API

## 상태

In Progress

## 결과

- public API, schema와 persistence 계약은 이미 구현돼 있으며 변경하지 않습니다.
- 폐기·교체된 refresh token을 만료 여부와 관계없이 reuse로 판정하도록 application
  우선순위와 회귀 테스트를 보완했습니다.
- 단순 만료 token은 family와 감사 이벤트를 변경하지 않고 기존
  `401 INVALID_REFRESH_TOKEN` 응답을 유지합니다.
- local 전체 검증은 통과했으며, push와 exact SHA의 required CI·Sonar gate가 확인되기
  전까지 이 작업은 완료되지 않습니다.

## 범위

- `RotateRefreshTokenService`의 reuse·만료 판정 우선순위
- application 단위 테스트의 token 상태 조합 회귀 계약
- 실제 PostgreSQL을 사용하는 refresh API 통합 테스트의 family 폐기·감사 결과
- 기존 rotation·동시성·account 상태 거부 회귀 검증
- 구현 뒤 Account 도메인·인증/인가 정책의 현재 한계 제거

## 제외 범위

- public API 요청·응답과 Security allow-list 변경
- refresh token schema, migration, port·adapter 계약과 새 정책 type 추가
- cookie 전달, 전체 기기 로그아웃, rate limit
- 이미 `REUSE_DETECTED`로 폐기된 family의 자동 복구

## 완료·미완료 결과

- 완료: refresh API, rotation, row lock, family 폐기와 감사 이벤트 저장 기반
- 완료: reuse와 단순 만료의 처분 정책 및 구현 가능한 계획 검증
- 완료: `revoked_at` 또는 `replaced_by_token_id`를 `expires_at`보다 먼저 판정하는
  application 변경
- 완료: 만료된 reuse token의 family 폐기·감사 결과와 단순 만료의 무변경 결과를 고정하는
  단위·통합 회귀 테스트
- 완료: Account 도메인 SOT 동기화와 local 전체 Gradle 검증
- 미완료: push된 final exact SHA의 required CI·Sonar terminal gate

## 수용 기준

- `expired + replaced + active replacement` 요청은 `401 INVALID_REFRESH_TOKEN`을 반환하고,
  활성 replacement를 `REUSE_DETECTED`로 폐기하며 reuse 감사 이벤트를 정확히 하나 남깁니다.
- `expired + revoked` 요청도 application 단위에서 reuse 경로를 선택합니다.
- `expired only` 요청은 `401 INVALID_REFRESH_TOKEN`을 반환하되 family 폐기와 reuse 감사
  이벤트를 만들지 않습니다.
- 만료 전 replaced token reuse, 정상 rotation과 동일 token 동시 요청의 기존 테스트가
  계속 통과합니다.
- Docker daemon을 확인한 환경에서 관련 test와
  `./gradlew check --no-daemon --rerun-tasks --console=plain`이 통과합니다.

## 남은 책임

- push 권한을 별도로 받으면 final exact SHA를 push한 뒤 required CI와 Sonar revision·
  Quality Gate·Open issues를 확인합니다.
- 다음 실행자는 remote gate가 모두 통과한 뒤 본문과 router를 `Done` Result 구조로 전환합니다.

시작 조건은 검증된 계획 checkpoint이며, local 결과는 수용 기준과 전체 Gradle 검증을
통과했습니다. 전체 완료 조건은 push된 final exact SHA의 remote terminal gate입니다.

## 확정 결정 색인

[상세 설계](007-refresh-token-reissue-api-design.md)는 다음 판단을 보존합니다.

- public refresh endpoint와 동일 오류 계약
- hash 조회, pessimistic lock과 transaction
- reuse 판정을 만료 판정보다 먼저 수행하는 상태 우선순위
- rotation·reuse·account 상태 거부 정책
- lifecycle store port의 책임과 별도 정책 type 비도입
- access token subject 재조회와 module 경계

## 검증·전환·복구

- 좁은 검증은 `RotateRefreshTokenServiceTest`, `RefreshTokenReissueApiIntegrationTest`,
  `RefreshTokenPersistenceAdapterTest`에서 통과했습니다.
- `docker info` 뒤 Java 21로 실행한
  `./gradlew check --no-daemon --rerun-tasks --console=plain`이 통과했습니다.
- schema·설정·API migration은 없습니다. Application source를 main에 반영한 뒤
  [400 GitHub ECR image 게시](../../../operations/400-github-ecr-image-publishing-runbook.md)로
  exact digest를 확보하고,
  [420 GitOps application 배포와 rollback](../../../operations/420-gitops-deployment-and-rollback-runbook.md)으로
  승인된 staging 전환과 live digest를 확인합니다.
- runtime 긴급 rollback은 420 runbook에 따라 이전 digest를 가리키는 revert PR로 수행합니다.
  구현 자체를 되돌리는 전체 rollback이면 service·test를 이전 source revision으로 복구하고,
  007을 `In Progress`로 다시 열어 router와 `100 Account`·`110 Account 인증/인가 정책`에
  만료 reuse 미지원 한계를 함께 복원합니다.
- 이미 `REUSE_DETECTED`로 폐기된 family와 남은 감사 이벤트는 자동 복원하지 않으며, 해당
  사용자는 다시 로그인해 새 session을 만듭니다.
- push 뒤 전체 코드 작업 완료를 판정할 때는 final exact SHA의 required CI와 Sonar revision·
  Quality Gate·Open issues를 확인합니다.

## 영구 SOT 승격

- reuse·만료 event 처분은 [120 Auth Event 정책](../../../domain/120-auth-event-policy.md)을
  유지합니다.
- [100 Account 도메인](../../../domain/100-account.md)과
  [110 Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)을 만료와 무관한
  reuse 우선 계약으로 갱신했습니다.
- refresh token 구조는
  [220 Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)을 유지하며,
  이 문서가 금지한 새 writer나 직접 table read를 추가하지 않습니다.
- 구현 결과가 현재 정책과 다르면 SOT를 구현에 맞춰 바꾸지 않고 007의 계획·구현 단계로
  돌아갑니다.

## 관련 문서

- **결정 근거**: [상세 설계](007-refresh-token-reissue-api-design.md)
- **감사 이벤트 계약**: [120 Auth Event 정책](../../../domain/120-auth-event-policy.md)
- **선행 token 발급**: [회원 로그인](005-member-login-api.md), [인증 토큰 기반](006-auth-token-foundation.md)
- **후속 폐기 흐름**: [로그아웃](009-auth-logout.md)
