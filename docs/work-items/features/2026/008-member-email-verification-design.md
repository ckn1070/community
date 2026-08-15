# 일반 회원 이메일 인증 설계 상세 근거

## 문서 목적

이 문서는 [008-member-email-verification.md](008-member-email-verification.md)의 결정 항목 D1-D6에 대한 상세 근거입니다.
현재 구현 결과와 영구 문서 링크는 작업 본문이 소유하며, 이 문서는 token, event, notification, transaction, API와 account 상태 전이의 선택 근거만 소유합니다.

## 결정 상세 목차

| ID | 선택 | 상세 근거 |
| --- | --- | --- |
| D1 | email 전용 테이블을 폐기하고 purpose/channel 기반 token 구조로 설계합니다. | [D1](#d1-email-verification-token-저장-구조) |
| D2 | 기존 `auth_event`의 event type을 확장합니다. | [D2](#d2-인증-감사-이벤트-기록) |
| D3 | notification 모듈을 분리합니다. | [D3](#d3-resend-연동-경계) |
| D4 | commit 이후 직접 발송, bounded immediate retry, 수동 재발송을 포함하고 outbox·webhook·durable retry는 제외합니다. | [D4](#d4-발송-transaction과-reliability) |
| D5 | 이메일 링크는 화면 진입용, token 소비는 backend `POST`로 처리합니다. | [D5](#d5-인증-완료-api-방식) |
| D6 | 이메일 인증 완료 시 account를 `PENDING`에서 `ACTIVE`로 전환합니다. | [D6](#d6-account-상태-전이) |

## 설계 필요 판단

이 작업은 설계가 필요합니다.
단순 이메일 발송 코드 추가가 아니라 다음 기준을 바꾸기 때문입니다.

- 새 DB table과 schema constraint
- account 상태 전이
- token 생명주기와 보안 경계
- 인증 감사 event taxonomy
- 외부 API 연동 경계
- transaction과 실패 복구 정책

## D1. Email verification token 저장 구조

- 선택 근거: email 전용 token table은 password reset, email change, phone verification으로 확장할 때 좁습니다. account 소유권은 유지하되 목적과 전달 채널을 분리하는 구조가 장기 확장과 현재 account 기반 흐름을 함께 만족하므로 이 방향을 선택했습니다.
- 추가 근거: email 전용 테이블은 첫 기능의 세부 구현으로 향후 account verification token 기반을 고정해 버릴 수 있습니다.
- 선택: 기존 `account_email_verification_token` 전용 테이블은 폐기하고, `purpose`와 `delivery_channel`을 가진 account verification token 구조로 재설계합니다.
- 분류: 기반 구조 설계 결정입니다.

### 왜 이 결론인가

token은 이메일 인증에만 쓰이지 않습니다.
비밀번호 재설정, 이메일 변경, 휴대폰 인증처럼 목적과 전달 채널만 다른 account 검증 흐름이 늘어날 수 있습니다.

따라서 테이블 이름과 컬럼을 email verification 하나에 고정하면 첫 기능의 세부 구현이 향후 인증 token 기반을 결정해 버립니다.
email verification 전용 구조는 확장 범위에 비해 너무 좁습니다.

다만 현재 가입 흐름은 account를 먼저 만들고 그 account를 인증하는 방식입니다.
그래서 `account` 소유권은 유지하되, email에 고정하지 않고 다음 축을 분리합니다.

- `purpose`: `EMAIL_VERIFICATION`, `PASSWORD_RESET`, `EMAIL_CHANGE`, `PHONE_VERIFICATION` 같은 사용 목적
- `delivery_channel`: `EMAIL`, `SMS` 같은 전달 채널
- `recipient_normalized`: 발송 대상 email 또는 phone snapshot
- `token_hash`: 원문 token 대신 저장할 hash
- `expires_at`, `consumed_at`, `revoked_at`: token 생명주기

### 선택지 비교

| 선택지 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| `purpose`/`delivery_channel` 기반 account verification token | email, password reset, phone verification까지 확장할 여지를 남깁니다. | 지금 이메일 인증 하나만 보면 설계가 조금 커집니다. | 선택됨 |
| email verification 전용 token 테이블 | 현재 기능만 보면 가장 단순합니다. | password reset, phone verification이 생기면 구조가 좁아집니다. | 폐기 |
| stateless signed token | DB row 없이 단순합니다. | 1회 소비, 강제 revoke, 재발급 폐기가 약합니다. | 제외 |
| account 밖 범용 verification challenge | pre-signup OTP 같은 비계정 흐름까지 담을 수 있습니다. | 현재 account 기반 인증 흐름보다 넓어 과합니다. | 보류 |

### 영향

- token table 이름과 컬럼 재설계
- token persistence adapter
- purpose/channel 기반 token 발급, 폐기, 소비 application 흐름

### 재검토 조건

- account가 없는 pre-signup OTP나 외부 identity verification을 지원해야 합니다.

## D2. 인증 감사 이벤트 기록

- 선택: 기존 `auth_event`의 event type을 확장합니다.
- 분류: 기술 표준형 설계 결정입니다.

완료 당시 이메일 인증 요청은 `EMAIL_VERIFICATION_REQUESTED`의 `SUCCESS`·`FAILURE`, 인증 완료는 `EMAIL_VERIFIED/SUCCESS`로 기록했고 invalid confirm failure event는 범위에서 제외했습니다. 후속 [Auth Event Taxonomy 재설계](../../improvements/2026/001-auth-event-taxonomy-redesign.md)가 이를 `EMAIL_VERIFICATION_REQUEST`·`EMAIL_VERIFICATION_CONFIRM`으로 대체하고 확인 실패 reason을 추가했습니다. 현재 exact event 계약은 [Auth Event 정책](../../../domain/120-auth-event-policy.md)이 소유합니다.

### 왜 이 결론인가

이메일 인증 요청과 인증 완료는 인증 흐름의 일부입니다.
기존 `auth_event`에 남기면 가입, 로그인, token refresh, 이메일 인증을 같은 인증 감사 흐름에서 볼 수 있습니다.

이번 작업은 delivery 상태, bounce, complaint, suppression 추적을 제외하므로 별도 email delivery event table까지 만들 필요는 약합니다.

### 선택지 비교

| 선택지 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| 기존 `auth_event` 확장 | 기존 audit 구조를 재사용합니다. | check constraint 변경이 필요합니다. | 선택 |
| 이메일 인증 전용 event table | provider event와 분리하기 쉽습니다. | 이번 범위에는 과합니다. | 제외 |
| event 기록 없음 | 구현이 작습니다. | 인증 요청과 실패 추적이 약해집니다. | 제외 |

### 영향

- 완료 당시 `auth_event` event type constraint migration
- 완료 당시 이메일 인증 요청 성공·실패와 완료 성공 event 기록

### 재검토 조건

- Resend webhook, bounce, complaint, suppression 상태를 계정 정책에 반영해야 합니다.

## D3. Resend 연동 경계

- 선택 근거: 두 번째 email purpose부터 template, provider mapping, logging, tag, 오류 변환 책임이 account 모듈 안에서 커집니다. account는 이메일 인증 도메인 흐름을 소유하고, 발송 provider와 template 경계는 notification 모듈이 소유하는 구조를 선택했습니다.
- 추가 근거: 두 번째 email purpose부터 provider, template, logging, tag, 오류 변환 책임이 account 모듈 안에서 커지므로 notification 모듈을 분리합니다.
- 선택: `notification` 모듈을 선분리하고, account는 notification port를 호출하는 방향으로 수정합니다.
- 분류: 기반 구조 설계 결정입니다.

### 왜 이 결론인가

이메일 인증 하나만 보면 account 내부 outbound adapter도 가능합니다.
하지만 비밀번호 재설정, 이메일 변경, 운영 알림이 생기는 순간 달라지는 것이 많습니다.

두 번째 email purpose가 생기면 달라지는 것은 메서드 하나가 늘어나는 정도가 아닙니다.

- 발신 주소, subject, template, text/html body 정책이 purpose별로 나뉩니다.
- Resend tag, idempotency key, message id 추적 기준이 공통화됩니다.
- provider 오류 변환, 민감정보 미노출 로그, rate limit 대응이 모든 email purpose에 반복됩니다.
- account 모듈이 여러 email template와 provider 세부사항을 소유하게 됩니다.
- 나중에 SMS가 추가되면 발송 채널 추상화가 account 밖에 있어야 합니다.

따라서 이번 시점에 거대한 notification platform을 만들지는 않더라도, 최소한 다음 경계는 분리하는 편이 낫습니다.

- `notification` 모듈이 email 발송 port와 Resend adapter를 소유합니다.
- account는 이메일 인증 목적의 발송 command를 notification port로 요청합니다.
- template과 provider request mapping은 notification 쪽에 둡니다.
- outbox, queue, webhook, delivery 상태 추적은 여전히 별도 후속 기반으로 둡니다.

### 선택지 비교

| 선택지 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| 최소 notification 모듈 선분리 | email purpose가 늘어도 provider와 template 경계가 흔들리지 않습니다. | 이번 기능만 보면 파일과 모듈이 늘어납니다. | 선택됨 |
| account output port + Resend adapter | 현재 기능 구현은 작습니다. | 두 번째 email purpose부터 account 모듈이 발송 기반을 떠안습니다. | 폐기 |
| application service에서 SDK 직접 호출 | 코드가 가장 짧습니다. | use case가 provider와 secret, 오류 모델에 결합됩니다. | 제외 |
| full notification platform | 장기 운영 기능까지 담을 수 있습니다. | outbox, webhook, delivery 상태까지 포함되어 너무 큽니다. | 제외 |

### 영향

- notification module/package 구조
- notification email send port
- Resend adapter와 설정 binding
- account 이메일 인증 use case의 notification port 호출
- fake notification sender 기반 테스트

### 재검토 조건

- outbox, queue, webhook, delivery 상태 추적을 포함해야 합니다. 이 경우 notification 기반 작업을 더 크게 분리합니다.

## D4. 발송 transaction과 reliability

- 선택 근거: 1-2회 수준의 자동 재시도 요구는 같은 요청 흐름 안의 짧은 즉시 재시도로 충족합니다. 장애를 DB에 저장해 나중에 복구하는 outbox/worker 기반은 이메일 인증보다 큰 발송 신뢰성 기반이라 이번 범위에서 제외합니다.
- 추가 근거: 포함하는 재시도는 outbox나 scheduler에 저장하는 durable retry가 아니라, commit 이후 발송 호출 안에서 provider 일시 오류에 한해 짧게 즉시 재시도하는 범위입니다.
- 선택: account와 token을 commit한 뒤 직접 발송하고, 발송 호출은 provider 일시 오류에 한해 최대 2회까지 bounded immediate retry를 적용합니다. 사용자의 수동 재발송 요청은 포함하고, outbox, queue, scheduler, Resend webhook 기반 durable retry는 이번 작업에서 제외합니다.
- 분류: 요구사항 의존형 설계 결정입니다.

### 왜 이 결론인가

DB transaction 안에서 외부 API를 호출하지 않는 것이 기본값입니다.
account 저장과 token 준비는 하나의 application transaction 안에서 처리하고, commit 이후 `afterCommit` 경계에서 이메일 발송을 시도합니다.

짧은 즉시 재시도까지 실패하면 account는 `PENDING`으로 남습니다.
사용자는 수동 재발송 요청으로 복구할 수 있습니다.

이 방식은 이번 기능 범위를 이메일 인증에 집중하게 해줍니다.
다만 durable retry는 없습니다.

여기서 제외하는 것은 발송 실패를 DB에 남기고 worker나 scheduler가 나중에 자동으로 다시 보내는 durable retry 기반입니다.
네트워크 일시 오류나 provider 5xx 같은 짧은 실패에 대해 같은 요청 흐름 안에서 1-2회 재시도하는 것은 이번 기능 범위에 포함할 수 있습니다.
사용자가 인증 메일 재발송 API를 호출해 새 token을 발급하거나 기존 정책에 맞춰 다시 보내는 흐름도 이번 기능 범위에 포함합니다.

### 선택지 비교

| 선택지 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| commit 이후 직접 발송 + bounded immediate retry + 수동 재발송 | durable retry 기반 없이도 일시 실패를 한 번 더 흡수할 수 있습니다. | 프로세스 종료나 장시간 장애는 복구하지 못합니다. | 선택됨 |
| commit 이후 직접 발송 + 수동 재발송 | 가장 작고 단순합니다. | 일시적인 provider 오류도 사용자 재발송에 의존합니다. | 제외 |
| outbox + worker | 발송 누락 자동 복구가 가능합니다. | 별도 기반 작업이 큽니다. | 이번 범위 제외 |
| Resend webhook 포함 | bounce/suppression 추적이 가능합니다. | public webhook 보안, idempotency, event 저장이 필요합니다. | 이번 범위 제외 |

### 분리하는 게 나은 대상

outbox, queue, scheduler, Resend webhook, delivery 상태 추적은 분리하는 게 낫습니다.
이 항목은 이메일 인증보다 큰 발송 신뢰성 기반입니다.

분리 작업에는 다음 결정이 필요합니다.

- outbox table과 transaction boundary
- worker 실행 방식
- retry 횟수와 backoff
- idempotency key 정책
- dead-letter와 운영 알림
- webhook endpoint와 signature 검증
- provider event 저장과 보존 기간

### 영향

- email verification request use case
- transaction synchronization 경계
- provider 일시 오류에 대한 짧은 retry 정책
- 재발송 API
- 발송 실패 로그와 오류 변환

### 재검토 조건

- 인증 이메일 발송 누락을 자동 복구해야 합니다.
- delivery 상태를 운영 화면이나 계정 정책에 반영해야 합니다.

## D5. 인증 완료 API 방식

- 선택: 이메일 링크는 화면 진입용으로 쓰고, token 소비는 backend `POST`로 처리합니다.
- 분류: 기술 표준형에 가까운 UX/API 결정입니다.

### 왜 이 결론인가

메일 보안 장비나 inbox provider는 링크를 자동으로 열어볼 수 있습니다.
인증 링크가 backend `GET`을 바로 호출해 token을 소비하면, 사용자가 누르기 전에 single-use token이 소진될 수 있습니다.

따라서 링크는 frontend 인증 화면으로 이동시키고, 사용자가 명시적으로 확인하면 backend `POST`가 token을 소비하는 방식을 추천합니다.

### 선택지 비교

| 선택지 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| 화면 진입 + `POST` 소비 | link scanner에 강합니다. | frontend 인증 화면이 필요합니다. | 선택 |
| backend `GET` 직접 인증 | 구현이 가장 단순합니다. | scanner가 token을 소비할 수 있습니다. | 제외 |

### 영향

- 인증 이메일 링크 생성
- 이메일 인증 완료 API
- public security allowlist

### 재검토 조건

- frontend 화면 없이 backend redirect만으로 이메일 인증을 끝내야 하는 제품 요구가 생깁니다.

## D6. Account 상태 전이

- 선택: 이메일 인증 완료 시 account를 `PENDING`에서 `ACTIVE`로 전환합니다.
- 분류: 요구사항 의존형 도메인 결정입니다.

### 왜 이 결론인가

기존 회원가입 흐름은 account를 `PENDING`으로 만듭니다.
이메일 인증의 목적은 이메일 소유권 확인이고, 그 결과가 account 활성화 조건입니다.

따라서 token 검증 성공 시 `email_verified_at`을 기록하고 account 상태를 `ACTIVE`로 바꾸는 것이 자연스럽습니다.
인증 전 사용 가능한 기능 목록은 로그인/세션 정책과 연결되므로 이 작업 완료 당시 전체 접근 정책에서 분리했습니다. 후속 로그인·token 작업이 이를 구현했으며 현재 접근 계약은 [Account 인증/인가 정책](../../../domain/110-account-auth-policy.md)이 소유합니다.

### 선택지 비교

| 선택지 | 장점 | 단점 | 판단 |
| --- | --- | --- | --- |
| 인증 완료 시 `ACTIVE` 전환 | account 생명주기가 명확합니다. | 인증 전 접근 정책은 별도 결정이 필요합니다. | 선택 |
| 가입 즉시 `ACTIVE`, 이메일 인증은 부가 상태 | 초기 UX가 단순합니다. | 이메일 인증의 보안 의미가 약해집니다. | 제외 |
| 인증 후에도 별도 승인 대기 | 운영 통제는 강합니다. | 현재 일반 회원 가입 목적보다 무겁습니다. | 제외 |

### 영향

- account domain 상태 전이
- 인증 완료 use case
- 완료 당시 제외한 인증 전 접근 정책. 현재 owner는 Account 인증/인가 정책입니다.

### 재검토 조건

- 이메일 인증 외 운영 승인, 약관 동의, 추가 심사 같은 활성화 조건이 생깁니다.

## 정책값과 가입 흐름

| ID | 선택 | 근거와 영향 |
| --- | --- | --- |
| P1 | token TTL 30분 | raw token 노출 시간을 제한하면서 일반적인 이메일 확인 시간을 제공합니다. |
| P2 | 재요청 cooldown 60초 | 더블 클릭과 짧은 반복 요청으로 인한 provider 호출과 token 재발급을 줄입니다. |
| P3 | cooldown 중 기존 open token 유지, 신규 발급·발송 없음 | raw token을 저장하지 않아 기존 token을 재발송할 수 없고, 새 token 발급은 cooldown 의미를 약화합니다. |
| P4 | 회원가입 직후 자동 발송 없음 | 클라이언트가 가입 흐름에서 인증 메일 요청 API를 명시적으로 호출하게 해 signup과 notification 책임을 분리합니다. |

P1-P3은 실제 발송 지연, abuse 지표와 사용자 문의가 다른 값을 요구하면 재검토합니다.
P4는 제품이 server-driven 가입 orchestration을 명시적으로 요구할 때 재검토합니다.

## 최종 동작 계약

- 가입 성공은 `PENDING` account까지만 만들고 인증 메일은 자동 발송하지 않습니다.
- request API가 token을 발급하고 commit 이후 Resend를 호출합니다.
- request cooldown은 60초이고 token TTL은 30분입니다.
- confirm API는 single-use token을 소비하고 account를 `ACTIVE`로 전환합니다.
- notification provider와 template은 account에서 분리됩니다.
- runtime key는 `RESEND_API_KEY`, `NOTIFICATION_EMAIL_FROM`, `EMAIL_VERIFICATION_BASE_URL`입니다.

## 관련 문서

- **구현 결과**: [이메일 인증](008-member-email-verification.md)
- **선행 schema·account 생성**: [DB Schema](001-account-auth-db-schema.md), [자체 가입](002-member-self-sign-up-api.md)
- **업무·데이터 기준**: [Account domain](../../../domain/100-account.md), [Account 데이터 모델](../../../project/220-account-auth-data-model.md)
