# Security Audit 장애 복구

이 runbook은 `IndependentSecurityAuditAppender`가 `FAILED`를 반환하거나
`security audit append failed` 구조화 로그가 발생했을 때 감사 누락 범위와 복구 가능성을
판정하는 절차를 소유합니다.

## 실행 계약

- 감사 저장 실패를 notification provider 실패나 업무 transaction 실패로 재분류하지 않습니다.
- 원래 사실을 재구성할 authoritative source가 없으면 event를 추측해 insert하지 않습니다.
- `auth_event` 직접 보정은 대상·값·근거를 별도 승인한 경우에만 수행합니다.
- credential, token, account 상태를 이 runbook에서 변경하지 않습니다.

## 1. 실패 신호 고정

애플리케이션 로그에서 아래 field를 함께 확보합니다.

- message: `security audit append failed`
- `eventType`
- `accountPublicId` 또는 null
- 발생 시각과 배포 revision
- 같은 request의 request id가 있으면 request id

로그가 없고 DB event만 의심되면 자동 복구로 진행하지 않고 audit gap 조사로 분류합니다.

## 2. DB 적용 상태 확인

읽기 전용 조회로 V13 이후 migration과 최근 event를 확인합니다.

```sql
SELECT version, description, success
FROM flyway_schema_history
WHERE version IN ('13', '14')
ORDER BY installed_rank;

SELECT id, account_public_id, event_type, result, reason_code, occurred_at
FROM auth_event
WHERE account_public_id = :account_public_id
  AND occurred_at BETWEEN :window_start AND :window_end
ORDER BY occurred_at, id;
```

`accountPublicId`가 null인 실패는 identifier 원문을 복원하지 않습니다. 로그에 안전한
`identifier_hash`가 남아 있지 않으면 식별자 기반 event를 재구성할 source가 없습니다.

## 3. 복구 source 판정

| source | 판정 |
| --- | --- |
| 같은 transaction의 account·refresh session 상태와 exact occurredAt·result·reason을 모두 복원 가능 | 보정 후보. 별도 변경 승인과 exact SQL 검토가 필요합니다. |
| notification provider receipt만 존재 | delivery 결과의 보조 증거입니다. account 식별자·event taxonomy를 추측하지 않습니다. |
| application error log만 있고 payload가 불완전 | audit gap으로 기록하고 event를 삽입하지 않습니다. |
| DB와 로그가 서로 충돌 | 즉시 중단하고 migration revision·transaction rollback 여부를 조사합니다. |

## 4. Audit gap 기록

복원 source가 없으면 다음을 incident 또는 운영 기록에 남깁니다.

- 영향 시간 범위와 revision
- 실패 event type과 알려진 public id
- 저장 실패 원인 class
- 업무·provider 결과는 유지됐다는 사실
- 추측 insert를 하지 않은 이유

현재 구현은 원본 command를 durable queue에 보존하지 않으므로 자동 replay를 지원하지 않습니다.

## 5. 종료 조건

- 저장 장애 원인이 해소되고 새 독립 append가 정상 저장됩니다.
- 영향 범위가 exact event 또는 복구 불가능한 gap으로 분류됐습니다.
- 보정이 필요하면 이 runbook 밖의 별도 승인 작업으로 분리됐습니다.

## 관련 문서

- [Auth Event 정책](../domain/120-auth-event-policy.md)
- [Account/Auth 데이터 모델](../project/220-account-auth-data-model.md)
- [Notification 결과와 감사 저장 실패 혼합](../work-items/bugs/2026/005-notification-audit-result-separation.md)
