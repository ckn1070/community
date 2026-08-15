# Auth Event Taxonomy 재설계 상세 설계


## 배경

기존 `auth_event` taxonomy는 `event_type`, `result`, `reason_code`의 책임이 섞여 있었습니다.
`LOGIN_SUCCESS`, `LOGIN_FAILURE`는 결과가 `event_type`에 들어갔고, `TOKEN_REUSE_DETECTED`는 실패 원인이 `event_type`으로 올라갔으며, 여러 로그인 실패 원인이 `INVALID_CREDENTIALS` 하나로 뭉개졌습니다.

앞으로 인증, 인가, 관리자, 이메일, token, role 관련 이벤트가 계속 늘어나므로 지금 기준을 다시 세우고 현재 구현에 적용합니다.

## 목표

- `event_type`, `result`, `reason_code`, `metadata`의 책임을 명확히 정의합니다.
- 현재 저장 이벤트를 새 기준에 맞게 다시 분류합니다.
- schema, persistence mapper, application port, 테스트, 운영 문서를 새 taxonomy와 동기화합니다.
- 새 이벤트가 늘어날 때 적용할 확장 규칙을 영구 문서에 남깁니다.

## 범위

- `auth_event.event_type` taxonomy 재정의
- 로그인 실패 reason code 분리
- refresh token reuse/account 상태 거부 reason code 분리
- 이메일 인증 요청/완료 event type 정리
- role, password change, password change requirement event type 정리
- metadata key 소유권과 금지 규칙 정의
- Flyway migration, JPA factory, persistence adapter, 관련 테스트 수정
- domain/project/schema/operations 문서 갱신

## 제외 범위

- `auth_event` table 물리 구조 변경
- 기존 `result` column 제거
- auth event 보존 기간 정책
- IP address, User-Agent 저장 활성화
- 새 관리자 역할 변경 기능 구현

## 설계 표면 인벤토리

| 상태 | 설계 표면 | 확정 내용 | 남은 결정 | 다음 단계 |
| --- | --- | --- | --- | --- |
| 구현 가능 | Auth event taxonomy | `event_type`은 인증/인가 흐름, `result`는 성공/실패, `reason_code`는 세부 원인으로 고정 | 없음 | 구현 완료 |
| 구현 가능 | Login failure reason | unknown identifier, password mismatch, suspended, withdrawn을 분리 | 없음 | 구현 완료 |
| 구현 가능 | Token lifecycle reason | reuse detection과 account 상태 거부를 `TOKEN_REFRESH/FAILURE` reason으로 분리 | 없음 | 구현 완료 |
| 구현 가능 | Metadata policy | event별 허용 key와 민감정보 금지를 명시 | 없음 | 구현 완료 |
| 차단 | DB migration | 기존 V7·V10을 직접 수정한 선택은 적용 이력이 있는 DB의 checksum·기존 row 호환성을 보장하지 못하므로 무효 | 환경별 `flyway_schema_history` checksum과 기존 event 값 분포 | 영향 확인 뒤 forward migration·repair·복구 순서 결정 |
| 구현 가능 | Port/API boundary | auth 모듈이 account 저장 경계에 로그인 실패 reason을 명시 전달 | 없음 | 구현 완료 |

## 확정된 설계

taxonomy의 의미 결정은 아래와 같이 유효합니다. 다만 기존 versioned migration을 직접 수정한 배포 방식은 [Flyway 기준](../../../tech-stack/240-flyway.md)에 위배되므로 확정 설계에서 제외합니다. 안전한 전환 방식은 실제 지속 DB의 적용 이력을 확인한 뒤 다시 결정합니다.

### Column 책임

| column | 책임 | 금지 규칙 |
| --- | --- | --- |
| `event_type` | 어떤 인증/인가 흐름에서 발생한 감사 이벤트인지 나타냅니다. | 성공, 실패, 탐지, 완료 같은 결과나 원인을 넣지 않습니다. |
| `result` | 해당 흐름의 결과를 `SUCCESS` 또는 `FAILURE`로 나타냅니다. | 실패 원인을 넣지 않습니다. |
| `reason_code` | 같은 `event_type/result` 안에서 결과가 발생한 세부 원인을 나타냅니다. | `event_type`과 같은 의미를 반복하지 않습니다. |
| `metadata` | column으로 승격하기에는 보조적인 구조화 값을 event별 허용 key로만 담습니다. | password, token, email 원문, secret, Authorization header, Cookie, 내부 token hash를 넣지 않습니다. |

### 현재 event type 재분류

| 기존 값 | 새 event type | result | reason code |
| --- | --- | --- | --- |
| `SIGN_UP` | `ACCOUNT_SIGN_UP` | `SUCCESS` | null |
| `LOGIN_SUCCESS` | `PASSWORD_LOGIN` | `SUCCESS` | null |
| `LOGIN_FAILURE` account 없음 | `PASSWORD_LOGIN` | `FAILURE` | `UNKNOWN_IDENTIFIER` |
| `LOGIN_FAILURE` password 불일치 | `PASSWORD_LOGIN` | `FAILURE` | `PASSWORD_MISMATCH` |
| `LOGIN_FAILURE` suspended | `PASSWORD_LOGIN` | `FAILURE` | `ACCOUNT_SUSPENDED` |
| `LOGIN_FAILURE` withdrawn | `PASSWORD_LOGIN` | `FAILURE` | `ACCOUNT_WITHDRAWN` |
| `TOKEN_REFRESH` 성공 | `TOKEN_REFRESH` | `SUCCESS` | null |
| `TOKEN_REFRESH` account 상태 거부 | `TOKEN_REFRESH` | `FAILURE` | `ACCOUNT_SUSPENDED` 또는 `ACCOUNT_WITHDRAWN` |
| `TOKEN_REUSE_DETECTED` | `TOKEN_REFRESH` | `FAILURE` | `TOKEN_REUSE_DETECTED` |
| `LOGOUT` | `LOGOUT` | `SUCCESS` | null |
| `PASSWORD_CHANGED` | `PASSWORD_CHANGE` | `SUCCESS` 또는 `FAILURE` | 실패 시 `CURRENT_PASSWORD_MISMATCH` |
| `PASSWORD_RESET_REQUIRED` | `PASSWORD_CHANGE_REQUIREMENT` | `SUCCESS` | `INITIAL_ADMIN_BOOTSTRAP` |
| `ROLE_GRANTED` | `ROLE_CHANGE` | `SUCCESS` | `SUPER_ADMIN_BOOTSTRAP` |
| `EMAIL_VERIFICATION_REQUESTED` | `EMAIL_VERIFICATION_REQUEST` | `SUCCESS` 또는 `FAILURE` | 실패 시 `DELIVERY_FAILED` |
| `EMAIL_VERIFIED` | `EMAIL_VERIFICATION_CONFIRM` | `SUCCESS` | null |

## 관련 문서

- **현재 이벤트 정책**: [Auth Event 정책](../../../domain/120-auth-event-policy.md)
- **현재 물리 schema**: [Account 인증/인가 Schema](../../../project/schema/100-account-auth.md)
- **물리 schema**: [Account/Auth schema](../../../project/schema/100-account-auth.md)
