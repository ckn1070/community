# API 로깅 기반

## 상태

Done

## 결과

- 모든 API 요청에 request id를 부여하고 응답 header와 MDC에 연결했습니다.
- 예외 처리와 Security 응답에서도 같은 request id를 유지합니다.
- 로컬 plain text와 production ECS structured logging을 분리했습니다.
- 공통 예외·Security 응답의 request id, MDC, local·production format과 당시 구현 범위의 민감정보 정적 금지 규칙을 자동 검증에 연결했습니다.
- 전체 완료 여부: 예. 이 결과는 004에서 만든 공통 logging 기반과 당시 source 범위에 한정됩니다.
- 이후 이메일 인증·관리자 초대 기능에서 provider exception을 직접 기록하는 회귀가 추가됐으며, 원래 004 결과를 무효화하지 않고 [Notification provider exception logging 회귀](../../bugs/2026/001-notification-provider-exception-logging.md)가 수정 책임을 소유합니다.
- 현재 `ApiException`이 5xx를 나타내는 경로는 공통 ERROR logging을 우회합니다. 이 별도 경계 결함은 [5xx ApiException 공통 logging 누락](../../bugs/2026/004-api-exception-5xx-logging.md)이 소유합니다.
- 남은 책임: 이 작업에는 없습니다. 현재 기준 위반은 위 Bug들로 분리했습니다.

## 범위

- request id 생성·전파
- MDC lifecycle과 응답 header
- 공통 예외 로그
- local·production logging format
- 민감정보 redaction과 architecture 검증

업무 감사 이벤트의 taxonomy와 저장 정책은 domain 문서와 별도 개선 작업이 소유합니다.

## 확정 결정 색인

[상세 설계](004-api-logging-foundation-design.md)는 다음 판단을 보존합니다.

- request id 신뢰·재사용 기준
- application log와 audit event 책임 분리
- log level, field, stack trace와 redaction 정책
- production structured logging 형식
- filter, exception handler와 Security 경계

## 영구 SOT 승격

- [로깅 기준](../../../tech-stack/280-logging.md)
- [Lombok 기준](../../../tech-stack/270-lombok.md)
- [현재 모듈 지도](../../../project/210-module-map.md)

## 관련 문서

- **결정 근거**: [상세 설계](004-api-logging-foundation-design.md)
- **선행 HTTP 기반**: [API 공통 기반](003-api-common-foundation.md)
