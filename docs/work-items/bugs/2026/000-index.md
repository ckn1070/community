# 2026 버그 작업 문서 목록

## 현재 작업

- [Notification provider exception logging 회귀](001-notification-provider-exception-logging.md): 이메일 인증·관리자 초대 발송 실패가 provider exception 원문과 stack trace를 기록할 수 있는 결함
- [Auth secret 분리 검증 누락](002-auth-secret-separation-validation.md): JWT 서명 secret과 로그인 identifier HMAC secret의 동일값을 application이 거부하지 않는 결함
- [공용 Clock bean의 auth 내부 소유](003-shared-clock-bean-ownership.md): Done — `config` 단일 UTC Clock provider와 constructor 주입으로 종결
- [5xx ApiException 공통 logging 누락](004-api-exception-5xx-logging.md): 내부 오류를 나타내는 `ApiException`이 공통 ERROR logging을 우회하는 결함
- [Notification 결과와 감사 저장 실패 혼합](005-notification-audit-result-separation.md): Done — 독립 감사 append의 `FAILED` 결과로 provider 의미와 분리
- [Bootstrap 다음 행동 안내 범위 오류](006-bootstrap-next-action-message.md): command가 public endpoint까지 사용할 수 없는 것처럼 과장해 안내하는 결함
- [Spring MVC framework 예외 분류 누락](007-spring-mvc-exception-classification.md): 잘못된 path type과 지원하지 않는 HTTP method가 4xx 대신 500으로 분류될 수 있는 결함
- [Notification template 시간 기준 비결정성](008-notification-template-clock.md): 이메일 남은 시간 문구가 주입된 Clock 대신 `Instant.now()`를 직접 사용해 비결정적인 결함
- [인증·복구 요청의 계정 열거 timing 차이](009-authentication-enumeration-timing.md): 같은 공개 응답에도 계정 존재·상태별 처리 비용 차이가 남는 결함

## 관련 문서

- **상위 router**: [버그 작업 문서 목록](../000-index.md)
- **상태·전환 기준**: [작업 문서 생명주기](../../100-work-item-lifecycle.md)
