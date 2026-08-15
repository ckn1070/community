# Auth Event Taxonomy 재설계

## 상태

Blocked

## 결과

- 현재 application·새 DB schema·테스트와 문서는 `event_type`, `result`, `reason_code`, `metadata`를 분리한 taxonomy를 사용합니다.
- 그러나 taxonomy 변경 commit이 이미 추가돼 있던 `V7__create_auth_event_table.sql`과 `V10__extend_auth_event_for_email_verification.sql`을 직접 수정했고, 기존 값을 새 값으로 옮기는 forward migration은 만들지 않았습니다.
- 따라서 원래 checksum으로 V7·V10을 적용한 지속 DB는 Flyway validation에 실패할 수 있고, validation을 우회해도 기존 row와 새 constraint의 호환성이 보장되지 않습니다. 반대로 수정본으로 처음 생성한 DB의 checksum과 schema는 현재 source에 맞습니다.
- 전체 완료 여부: 아니요. 기존 `Done` 판정과 “migration/backward compatibility 완료” 주장을 철회합니다.
- 차단 사유: 각 지속 환경의 `flyway_schema_history` checksum과 기존 `auth_event` 값 분포를 확인하지 않은 채 versioned file 복원, repair 또는 forward migration 순서를 안전하게 하나로 정할 수 없습니다.
- 다음 실행자: DB 운영 권한이 있는 실행자가 환경별 history와 데이터 분포를 읽기 전용으로 수집하고, 개발자가 그 결과에 맞는 migration·복구 계획을 별도 검증한 뒤 적용합니다.
- 완료 조건: 기존 설치와 신규 설치 모두 같은 최종 taxonomy에 도달하고 Flyway validation, 기존 row 전환, application integration test와 rollback/복구 절차가 검증됩니다.

## 범위

- account/auth 감사 event taxonomy
- result와 reason code 분리
- metadata key 소유권과 금지 값
- DB constraint와 저장 adapter

보존 기간, 검색 UI, IP와 User-Agent 수집은 별도 운영·개인정보 결정입니다.

## 유효한 결정과 무효화된 결정

[상세 설계](001-auth-event-taxonomy-redesign-design.md)는 다음 판단을 보존합니다.

- `event_type`, `result`, `reason_code`의 의미
- 로그인·token·email·role·password event 분류
- metadata object와 민감정보 금지
- migration과 backward compatibility는 현재 미완료이며, 상세 설계의 기존 migration 선택은 무효화됐습니다.
- persistence·test·운영 문서 동기화

## 영구 SOT 승격

- [Auth Event 정책](../../../domain/120-auth-event-policy.md)
- [Account 도메인](../../../domain/100-account.md)
- [Account 인증/인가 데이터 모델](../../../project/220-account-auth-data-model.md)
- [Account/Auth Schema Reference](../../../project/schema/100-account-auth.md)
- [Account/Auth 물리 schema](../../../project/schema/100-account-auth.md)

taxonomy 의미 자체는 위 영구 SOT에 유효하게 반영됐습니다. 적용된 versioned migration을 수정하지 않는 기준과 현재 예외 상태는 [Flyway 사용 가이드](../../../tech-stack/240-flyway.md#현재-알려진-migration-호환성-결함)가 소유합니다.

## 관련 문서

- **결정 근거**: [상세 설계](001-auth-event-taxonomy-redesign-design.md)
