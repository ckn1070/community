# Community Kafka 이벤트 전달 상세 설계

## 상태

Proposed

## 계획 단계

해결 설계

## 현재 결론과 다음 행동

| 항목 | 현재 값 |
| --- | --- |
| 현재 단계 | `해결 설계` |
| 현재 결론 | 닫힌 결과와 R1-A·단일-node Kubernetes 범위를 보존한 exact 해결 설계 후보가 구조·내용 검증과 local checkpoint를 마쳤습니다. |
| 닫힌 결과 | 결과 정의, R1-A, 단일-node Kubernetes의 Strimzi qualification 범위 |
| 남은 gate | Exact 해결 설계 후보의 사용자 단계 확정 |
| 지금 actor | 사용자 |
| 지금 할 일 | 아래 결정 지도와 상세 설계를 확인하고 해결 설계를 확정하거나 변경할 identity를 지정합니다. |
| 사용자 차례 | 지금 |
| 응답 방식 | `해결 설계를 확정해 주세요.` 또는 `D번호/R1: <변경 요구>` |
| 이 행동 뒤 결과 | 변경 요구가 없으면 해결 설계가 닫히고 work item이 다음 명시 요청을 기다립니다. |
| 다음 단계 전환 | 사용자가 실행 계획 작성을 명시하면 `실행 계획`을 엽니다. |

## 문서 책임과 설계 입력

이 문서는 D2~D9에서 장기 복원해야 할 결정 질문, 대안, Agent 기술 선택, 목표 계약과
재검토 조건을 소유합니다. R1의 canonical owner는 work item이며, 이 문서의 R1은 그 값을
소비하는 기술 projection일 뿐 새 대안·선택을 소유하지 않습니다. 구현 task·파일 목록·
test 명령·설치 명령·exact version·image digest·resource
수치·retention 기간·운영 replay 절차는 해결 설계가 확정된 뒤 실행 계획과 영구 SOT·runbook이
소유합니다.

현재 source는 다음 제약을 만듭니다.

- `CommunityService.createComment()`는 댓글과 수신 후보를 계산한 뒤 같은 transaction에서
  `CommunityConversationNotification.store()`를 호출합니다. Store 실패 시 댓글도 rollback하는
  현재 동작이 integration test로 고정돼 있습니다.
- Notification은 `(recipient_account_public_id, source_content_public_id)` unique constraint로
  같은 source comment의 중복 inbox를 막고 `created_at, id` 순서로 inbox를 조회합니다.
- Community와 Notification은 하나의 Spring Boot artifact와 PostgreSQL을 사용합니다. Kafka
  client·broker·outbox·처리 영수증은 아직 없습니다.
- 현재 architecture SOT는 `notification`을 독립 업무 정책이 없는 기술 통합 경계로 분류합니다.
  이 작업은 그 사실을 무시해 새 Supporting Bounded Context라고 선언하지 않습니다.
- Staging은 ARM64 단일 Kubernetes node에서 control plane과 application을 함께 실행합니다.
  Live memory는 4 GiB이고 root EBS는 20 GiB이며, root volume에는 persistent application data를
  두지 않습니다. Kafka용 StorageClass와 block storage는 현재 구현돼 있지 않습니다.
- Strimzi는 KRaft에서 `Kafka`와 `KafkaNodePool`을 사용하고, dual-role node는 개발·test에
  사용할 수 있지만 typical production에는 broker와 controller 분리를 권장합니다. Ephemeral
  storage는 개발·test 전용이며 replication factor 1 topic에는 적합하지 않습니다.
- Testcontainers는 현재 Gradle BOM에 이미 있으며 Kafka module을 추가하면 test process가
  공식 Kafka image 기반 broker를 생성·제거할 수 있습니다.

## 전체 목표 구조

```text
사용자 댓글·답글 요청
  -> Community application
       -> PostgreSQL transaction
            ├─ community_content + community_comment
            └─ community_event_outbox
       -> API 성공

Outbox relay
  -> 미전달 event claim
  -> Kafka topic에 postId key로 at-least-once publish
  -> broker acknowledgement 뒤 publish 상태 갱신

Notification consumer
  -> type·version·payload 검증
  -> 수신자 정책 적용
  -> PostgreSQL transaction
       ├─ processed_community_event 영수증
       └─ notification_inbox 0~2건
  -> DB commit 뒤 offset 확정
```

### 환경과 증거의 전체 연결

```text
로컬·CI Testcontainers
  -> serialization, real publish/consume, 중복·retry·DLQ 계약

현재 단일 node staging
  -> GitOps·network·credential·resource 상한과 실제 수직 흐름
  -> 기능·복구 qualification만 증명

별도 다중 node·다중 AZ qualification
  -> broker 장애, replication, rebalance, 처리량, backlog 복구
  -> 큰 부하·고가용성 후보를 증명

production 후보
  -> 확정 peak·SLO·RPO·RTO·비용·운영 책임으로
     self-managed Strimzi 또는 managed Kafka를 별도 선택
```

단일 node에 broker 하나를 띄우는 결과는 위 경로의 두 번째 단계일 뿐입니다. Application
event 계약은 topology와 분리하지만, storage·replication·failure domain의 증거는 환경을
바꿔서 다시 만들어야 합니다.

## 결정 지도

| 결정 | Agent 기술 선택 후보 | 주된 이유 |
| --- | --- | --- |
| R1 사용자 확정 | Community 댓글·답글 생성 → 서비스 안 알림 한 수직 흐름 | 이미 확정된 결과를 보존하며 새 선택이 아닙니다. |
| D2 전달 원본 | 명시적 PostgreSQL transactional outbox | DB와 Kafka dual-write 유실 없이 현재 database를 내구 인계점으로 사용합니다. |
| D3 Event·module 계약 | Community 사실 event 하나, versioned JSON envelope와 현재 module owner 유지 | 생산자의 업무 사실과 consumer projection을 분리하고 class·table 직렬화와 새 Domain 발명을 피합니다. |
| D4 Relay·처리 | Community 내부 bounded relay, post key locality, at-least-once | 새 CDC platform 없이 요청과 broker를 분리하고 알림별 독립 처리를 유지합니다. |
| D5 Consumer·실패 | DB 처리 영수증과 inbox 원자 저장, commit 뒤 offset, retry와 DLQ 분리 | Kafka·PostgreSQL 분산 transaction 없이 재전달을 정상 경로로 만듭니다. |
| D6 Runtime·storage·환경 | Strimzi KRaft 단일 node와 폐기 가능한 broker storage, 환경별 증명 분리 | 현재 Kubernetes에서 lifecycle을 학습하되 PostgreSQL outbox를 내구 원본으로 두고 single-node 성공을 HA로 확대하지 않습니다. |
| D7 보안 | 내부 TLS, client-role별 principal·ACL, NetworkPolicy, secret lifecycle | Public·익명 접근을 막되 첫 co-resident process의 credential 합집합 위험을 숨기지 않습니다. |
| D8 관측·qualification | 구간별 저-cardinality 신호와 닫힌 부하·장애 기준 | Event가 멈춘 위치와 실제 병목을 구분하고 환경별 합격 주장을 제한합니다. |
| D9 전환·rollback·승격 | Shadow 비교 뒤 비동기 전환, flag-off와 outbox 보존, production 별도 승인 | 사용자 중복 없이 되돌리고 미처리 event의 owner를 유지합니다. |

## R1. Kafka 적용 경계와 사용자 결과

R1-A는 사용자가 이미 확정했습니다. 첫 흐름은 Community 댓글·답글 생성 사실을 Kafka로
전달해 서비스 안 알림으로 수렴시키는 수직 단위입니다. 댓글 작성은 PostgreSQL commit으로
성공하고 broker·consumer 장애는 그 성공을 rollback하지 않습니다. 알림은 늦을 수 있지만
복구 뒤 누락·사용자 중복 없이 생성됩니다.

자동차 catalog, Account·auth, Media, 검색·통계·추천은 이번 첫 적용에서 제외합니다. 이
경계는 새로운 선택지가 아니며 결과 정의 무효화 source가 없는 한 다시 열지 않습니다.

## D2. PostgreSQL과 Kafka 사이의 전달 원본

**결정 질문:** Community DB transaction과 Kafka publish 사이에서 한쪽만 남는 dual-write
유실을 어떤 내구 원본과 relay 경계로 막아야 합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | 명시적 PostgreSQL transactional outbox와 application relay |
| 공통 비교 축 | Dual-write 유실, 현재 source 재사용, 새 runtime·운영 표면, 관찰·복구 가능성 |
| 영향 | Schema·claim·retry·보존·정리 lifecycle을 project가 소유 |
| 배제 대안 | DB/Kafka 선후 전송, Spring Modulith registry, Debezium CDC |
| 재검토 조건 | 여러 producer의 publication lifecycle 공유 또는 polling 병목이 실측됨 |

| 대안 | 장점 | 결함·비용 |
| --- | --- | --- |
| DB commit 뒤 `KafkaTemplate.send()` | 정상 경로가 짧음 | DB commit과 publish 사이 process 중단에서 event 유실 |
| Kafka publish 뒤 DB commit | Kafka에 먼저 기록 | DB rollback 시 존재하지 않는 업무 상태의 event가 남음 |
| Spring Modulith publication registry | 현재 dependency와 transaction 통합 재사용 | Kafka 전송 상태·순서·보존·운영 계약이 framework lifecycle에 묶임 |
| 명시적 outbox와 application relay | Transaction·claim·상태·정리 계약을 project가 직접 소유 | Schema·relay·운영 책임이 생김 |
| Debezium CDC outbox | Application polling 제거와 다수 producer 확장 | Kafka Connect·WAL·connector 운영을 첫 수직 흐름에 추가 |

명시적 outbox를 선택합니다. Community content와 outbox row는 같은 transaction에 저장되고,
transaction rollback이면 둘 다 남지 않습니다. Outbox는 최소한 event identity·type·version,
aggregate identity, payload, 발생 시각, publish lifecycle과 retry 관측값을 소유합니다.

Publish 완료만으로 row를 삭제하지 않습니다. 각 event의 downstream 처리 영수증 또는
원인·영향·복구 불가성을 확인한 명시적 terminal disposition이 먼저 있어야 삭제 적격이
됩니다. Replay·rollback 최소 보존 기간은 이 완료 조건 위에 추가되며, 기간이 지났다는
사실만으로 미완료 row를 삭제하지 않습니다. Debezium은 여러 producer나 polling이 실제
병목이 될 때, Spring Modulith registry는 project-owned outbox 운영 비용이 가치보다 커질
때 다시 비교합니다.

## D3. Event 계약과 module 소유권

**결정 질문:** 어떤 업무 사실을 공개 계약으로 넘기고, Community와 Notification은 각각
어떤 상태를 소유해야 현재 module 경계를 보존할 수 있습니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Community 사실 event 하나와 versioned JSON wire contract; 현재 module owner 유지 |
| 공통 비교 축 | 업무 의미 owner, 호환 배포, 개인정보 최소화, consumer 독립성 |
| 영향 | Community가 event 의미를, Notification이 수신 정책·inbox projection을 소유 |
| 배제 대안 | 알림 생성 command, JPA entity 직렬화, 첫 흐름의 Schema Registry, 새 업무 Domain |
| 재검토 조건 | 다수 조직·언어 consumer의 중앙 compatibility enforcement 또는 Notification의 독립 업무 lifecycle 발생 |

| 공통 평가축 | 사실 event + versioned JSON | 알림 생성 command | JPA entity 직렬화 |
| --- | --- | --- | --- |
| 의미 owner | Community 사실, Notification 정책 분리 | Producer가 consumer 행동을 지시 | Persistence 구현이 wire 의미를 결정 |
| 결합·확장 | 새 consumer가 사실을 독립 해석 | 새 consumer마다 command 의미 확대 | Producer schema 변경이 consumer를 직접 파손 |
| 개인정보 | 필요한 식별 사실만 allowlist | 채널·표현 정보가 섞이기 쉬움 | 내부 column·관계가 과다 노출 |
| 호환성 | 명시 version과 optional 확장 | Command별 별도 호환 계약 필요 | DB migration과 wire migration 결합 |
| 판정 | **선택** | R1의 producer/consumer 분리 목표에 불리 | 공개 계약·module owner 위반 |

첫 public event는 `CommunityConversationCreatedV1` 하나입니다. `COMMENT`와 `REPLY`를 별도
class hierarchy로 노출하지 않고 `conversationKind`로 구분합니다.

```text
eventId
eventType = CommunityConversationCreated
eventVersion = 1
occurredAt
postId
conversationId
conversationKind = COMMENT | REPLY
actorAccountId
postAuthorAccountId
directParentAuthorAccountId?
```

- Event에는 본문, access token, secret, email, display name과 persistence 내부 ID를 넣지
  않습니다.
- Community는 사건 당시 대화 관계와 후보 작성자 사실을 소유합니다. Notification은 actor
  제외와 recipient 중복 제거, inbox type 변환을 소유합니다.
- Java class·JPA entity·DB row를 그대로 직렬화하지 않고 explicit JSON wire DTO를 둡니다.
- Wire DTO는 `shared :: integration` named interface에 두되, Community가 event 의미와 version을
  소유하고 shared에는 persistence·Kafka client·업무 판단을 두지 않습니다.
- V1 consumer가 읽는 field의 삭제·type·의미 변경은 허용하지 않습니다. Optional field 추가는
  compatibility test를 통과해야 하며, breaking change는 새 version을 병행합니다.
- Schema Registry는 첫 producer·consumer의 계약 test만으로 독립 호환성 요구를 충족할 수
  있으므로 도입하지 않습니다. 조직 단위 producer·consumer가 늘거나 중앙 compatibility
  enforcement가 필요해지면 다시 평가합니다.

| 경계 | 현재 지위 | 이 설계의 책임 |
| --- | --- | --- |
| Community | 게시글·댓글·답글 규칙과 대화 관계를 소유 | Outbox의 유일한 writer이며 request transaction에서 event 사실을 생성 |
| Notification | 현재 SOT상 발송·inbox 기술 통합 경계 | 처리 영수증과 inbox의 유일한 writer이며 public event를 자기 application 입력으로 번역 |
| `shared :: integration` | 두 module의 immutable wire DTO | 업무 로직·persistence·Kafka client 없이 JSON field·type·version만 공개 |

Notification에 사용자별 preference·채널 정책·보존처럼 독립 업무 lifecycle이 생기기 전에는
새 Bounded Context나 Subdomain으로 승격하지 않습니다. Outbox·offset·처리 영수증은 전달 기술
상태이지 새 업무 Domain이 아닙니다.

## D4. Relay, topic과 처리 독립성

**결정 질문:** API 요청을 broker acknowledgement에서 분리하면서 게시글별 locality,
독립 처리와 at-least-once 재전송을 어떤 경계로 보장해야 합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | 같은 artifact의 bounded application relay, `postId` key, at-least-once |
| 공통 비교 축 | 요청 장애 분리, 순서 범위, replica 확장, 새 platform 비용 |
| 영향 | 중복 publish를 정상 경로로 받아들이고 consumer 멱등성을 필수화 |
| 배제 대안 | Request thread 직접 publish, 전역 순서, 첫 흐름의 Kafka Connect·별도 service |
| 재검토 조건 | Relay 자원 간섭이나 polling이 수용 기준의 실제 병목으로 확인됨 |

| 공통 평가축 | Request 직접 publish | Application outbox relay | Debezium/Kafka Connect |
| --- | --- | --- | --- |
| API 장애 분리 | Broker ack·timeout이 요청에 전파 | DB commit 뒤 별도 worker가 처리 | DB commit 뒤 CDC가 처리 |
| 유실 복구 | DB/Kafka 사이 공백 존재 | Outbox 상태·claim으로 project가 복구 | WAL·connector offset으로 platform이 복구 |
| 새 운영 표면 | 작지만 목표 불충족 | Schema·worker 책임 추가 | Connect·connector·WAL 책임 추가 |
| 확장 | Request와 producer 함께 확장 | Worker 상한과 별도 workload 분리 가능 | 다수 producer에 유리 |
| 판정 | 제외 | **선택** | 첫 수직 흐름에는 순효과가 작음 |

첫 topic은 Community 대화 생성 사실 전용 versioned topic 하나이며 partition key는 `postId`입니다.
같은 게시글 record를 같은 partition에 모아 locality를 얻고 서로 다른 게시글을 병렬 처리합니다. Topic 이름,
partition 수·retention·record 크기 상한은 실행 계획에서 workload·backlog recovery 목표와
함께 동결합니다.

Relay는 `community` module 안의 별도 bounded worker로 시작합니다. API request thread가
Kafka acknowledgement를 기다리지 않으며, worker thread·DB connection·claim batch·in-flight
publish 수에 상한을 둡니다. Application replica가 여러 개여도 PostgreSQL claim이 현재 owner를
정하고 만료된 claim을 회수할 수 있어야 합니다.

`postId` key는 같은 게시글 event의 partition locality를 제공하지만 제품의 전역·게시글별 알림
순서 보장은 아닙니다. 각 댓글·답글 알림은 다른 event의 성공에 의존하지 않고 event ID로
멱등 처리합니다. Poison event를 격리한 뒤 같은 partition의 후속 event를 처리할 수 있습니다.
Broker acknowledgement 뒤 DB 상태 갱신 전에 process가 중단될 수 있으므로 중복 publish를
정상으로 허용하고 전달 의미는 at-least-once로 정의합니다.

Relay 처리량이나 API resource 간섭이 실제 병목이 되면 같은 artifact를 relay 전용 workload로
분리합니다. 이 전환은 outbox·event·consumer 계약을 바꾸지 않습니다. 여러 producer와 CDC
운영 필요가 확인되기 전에는 Kafka Connect를 추가하지 않습니다.

## D5. Notification consumer와 실패 수렴

**결정 질문:** 중복 전달·중단·처리 불가 event 뒤에도 inbox가 사용자에게 한 결과로
수렴하도록 DB transaction과 offset·격리 경계를 어떻게 나눠야 합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | 처리 영수증과 inbox를 한 DB transaction에 저장한 뒤 offset 확정 |
| 공통 비교 축 | 중단 지점별 중복·유실, 0건 정상 처리, poison event 격리, replay 안전성 |
| 영향 | Event ID와 기존 inbox unique constraint가 모두 필요하며 delivery는 at-least-once |
| 배제 대안 | 처리 전 offset 확정, offset만으로 중복 방지, 무한 retry, Kafka-DB 분산 transaction |
| 재검토 조건 | 한 event가 여러 database 결과를 원자적으로 바꿔야 하는 새 요구 발생 |

| 공통 평가축 | Offset 먼저 확정 | DB 영수증·inbox commit 뒤 offset | Kafka-DB transaction 결합 |
| --- | --- | --- | --- |
| DB 실패 | Record 유실 가능 | Offset 미완료로 재전달 | 서로 다른 transaction manager 실패 경계 필요 |
| Commit 뒤 중단 | 재전달 없음이나 DB 전 실패 취약 | 재전달되며 영수증으로 no-op | Kafka commit 실패 시 재전달·DB 멱등성은 여전히 필요 |
| 0건·중복 | 완료 증거 없음 | 0건도 영수증, inbox unique와 이중 방어 | DB 멱등성 없이는 해결되지 않음 |
| 운영 복잡도 | 낮지만 정확성 목표 실패 | 현재 PostgreSQL 안에서 관찰 가능 | 분산 commit 오판·복구 표면 증가 |
| 판정 | 제외 | **선택** | 추가 복잡도 대비 정확성 이득 없음 |

Consumer의 한 record 처리 계약은 다음과 같습니다.

1. 지원 type·version과 필수 field를 검증합니다.
2. 후보 작성자에서 actor와 중복 recipient를 제거합니다.
3. 한 PostgreSQL transaction에 event 처리 영수증과 recipient별 inbox 0~2건을 저장합니다.
4. DB commit이 끝난 record만 Kafka offset 완료 대상으로 둡니다.

DB commit 뒤 offset 확정 전에 process가 중단되면 record를 다시 받습니다. Event ID unique
constraint는 같은 event 처리 반복을, 기존 `(recipient, source comment)` unique constraint는
동기·비동기 경로가 겹칠 때 사용자 중복을 막습니다. Inbox가 0건인 event도 처리 영수증을
남겨 성공과 미처리를 구분합니다.

| 공개 시간·cursor 대안 | 기존 `createdAt = inbox 생성 시각` 유지 | `createdAt`을 event 발생 시각으로 변경 | 발생 시각을 새 필드로 추가하고 정렬도 변경 |
| --- | --- | --- | --- |
| 기존 API 의미 | 보존 | 같은 필드 의미 변경 | 기존 필드는 보존 |
| 기존 cursor | `created_at,id` 그대로 호환 | 구 cursor 처분·backfill 필요 | Cursor version·backfill 필요 |
| 지연 event 표시 | 복구 시 새 알림으로 도착 | 원래 활동 시점에 삽입 | 새 시각 노출과 정렬 정책 필요 |
| 판정 | **선택**. 알림이 실제 생성된 시각·현재 cursor 계약 보존 | 제외 | 별도 제품 요구가 생길 때 재검토 |

Public `createdAt`은 현재처럼 inbox projection이 DB에 생성된 시각을 뜻하고 정렬·cursor도
`created_at,id`를 유지합니다. Event의 `occurredAt`은 전달 지연·추적 evidence로 보존하되
현재 공개 정렬 의미를 바꾸지 않습니다. 사용자가 “원 활동 시각 기준 inbox”를 요구할 때만
새 공개 필드·cursor version·backfill을 함께 설계합니다.

일시적 broker·network·DB 오류는 제한된 backoff 뒤 같은 record를 다시 처리합니다. 지원하지
않는 계약이나 반복 처리 불가 payload는 원본 event ID, topic·partition·offset과 실패 분류를
보존하는 dead-letter topic으로 격리합니다. DLQ publish가 확인되기 전에는 원본 offset을 넘기지
않습니다. 격리는 해당 알림의 미완료 처분이며 다른 알림의 업무 순서를 막지 않습니다. Replay는
원인 수정, exact 대상 승인과 같은 idempotent consumer 경로를 사용합니다.

## D6. Runtime, storage와 환경별 증명 범위

**결정 질문:** 확정된 현재 Kubernetes의 단일-node Kafka를 어떤 runtime·storage 경계로
구축하고, 각 환경은 무엇까지만 증명해야 합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Strimzi KRaft 단일 dual-role node와 ephemeral broker storage |
| 공통 비교 축 | 닫힌 사용자 범위, 새 운영 표면, data 내구 owner, resource·failure domain, 확장 경로 |
| 영향 | Broker data·offset 소실을 허용하되 PostgreSQL outbox 재발행으로 사용자 결과를 복구 |
| 배제 대안 | 직접 StatefulSet, 현재 단계의 managed Kafka·전용 block storage |
| 재검토 조건 | 최소 경로가 닫힌 부하·복구 기준에 실패하거나 production 목표가 별도로 확정됨 |

| 환경 | 고정된 최소 형태 | 증명할 수 있는 것 | 증명할 수 없는 것 |
| --- | --- | --- | --- |
| 로컬·CI | Testcontainers가 시작·제거하는 KRaft single broker | 실제 serialization, publish·consume, retry·DLQ·멱등성 계약 | Kubernetes 운영, HA, 장시간 capacity |
| 공유 기능 qualification | 현재 Kubernetes의 Strimzi KRaft 단일 dual-role node | 실제 network·identity·중단·복구와 수직 흐름 | HA, production SLO, 독립 failure domain |
| 부하·장애 qualification | Application과 broker가 분리된 최소 3 failure domain·다중 AZ | Replication, quorum, broker 장애, rebalance, 처리량, backlog 복구 | 장기 production 운영과 확정 SLO |
| Production 후보 | 확정 SLO·RPO·RTO·비용·운영 책임에 맞춘 dedicated Strimzi 또는 managed Kafka | 승인된 운영 목표 | 별도 region DR 등 계획 밖 보장 |

| Runtime·storage 대안 | 장점 | 비용·새 실패 표면 | 판정 |
| --- | --- | --- | --- | --- |
| Strimzi KRaft + ephemeral broker storage | 현재 GitOps·NetworkPolicy 안에서 operator·broker lifecycle을 검증하고 새 storage platform을 만들지 않음 | Application과 failure domain·root disk를 공유하며 broker data·offset을 잃을 수 있음 | **선택**. PostgreSQL outbox를 유일한 내구 원본으로 둔 qualification 전용 예외 |
| Strimzi + 전용 persistent block storage | Broker 재시작 뒤 log·offset을 보존 | CSI/PV·attach·backup·용량·orphan cost라는 별도 lifecycle 추가 | 현재 최소 경로가 수용 기준에 실패하고 사용자가 추가 범위를 승인할 때 재검토 |
| Managed Kafka | Broker·storage·quorum 운영을 provider에 위임 | 지속 비용, private network·IAM·quota·provider 장애·삭제 책임 | 현재 닫힌 범위가 아니며 production topology 결정 때 재검토 |
| 직접 StatefulSet·script | Operator dependency 없음 | Certificate·user·topic·rolling update를 project가 재구현 | 제외 |

Broker pod·storage·offset이 소실되면 Kafka data 복원을 주장하지 않습니다. 새 broker·topic을
만든 뒤 downstream receipt도 승인된 terminal disposition도 없는 PostgreSQL outbox만
재발행해 receipt·inbox로 수렴시킵니다. Ephemeral storage도 node disk를 사용하므로
retention·용량·DiskPressure 경계는 실행 계획에서 workload로 계산합니다. 이 최소 경로가
닫힌 부하·복구 기준을 충족하지 못하면 합격 실패로 기록하고, persistent storage나 별도
topology는 영향·비용·owner를 제시해 새 승인을 받습니다.

Exact Strimzi·Kafka·client version과 image digest는 ARM64, 현재 Kubernetes·Spring Boot
compatibility와 지원 기간을 공식 source로 확인해 실행 계획에서 고정합니다.

## D7. Network, 인증·인가와 secret

**결정 질문:** 어떤 workload가 어떤 경로·자격·최소 권한으로 Kafka에 접근하고 secret은
어디에서 생성·교체·폐기해야 합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | 내부 TLS, client-role별 principal·ACL, deny-by-default NetworkPolicy, Kubernetes Secret lifecycle |
| 공통 비교 축 | 노출 면적, actor 분리, 최소 권한, rotation·폐기, 장애 진단 |
| 영향 | Producer·consumer·DLQ·replay identity와 권한을 분리하고 broker 상태를 API readiness에서 분리 |
| 배제 대안 | Public/anonymous listener, shared credential, Git·image 내 secret, broker 연동형 API readiness |
| 재검토 조건 | Cluster identity 표준이나 외부 managed endpoint가 별도 topology에서 채택됨 |

| 공통 평가축 | 익명·공유 credential | Role별 principal, 같은 process에 co-resident | Role별 별도 workload·Secret mount |
| --- | --- | --- | --- |
| 권한 구분 | 불가 | Kafka ACL·audit에서 producer/consumer 구분 | ACL과 Pod credential 노출 모두 구분 |
| 침해 반경 | 전체 권한 | Process가 가진 role credential의 합집합 | 침해된 workload의 role로 제한 |
| 현재 자원·배치 | 단순하지만 보안 목표 실패 | 현재 단일 artifact·node에서 가능 | Pod·resource·mode·배포 lifecycle 추가 |
| 판정 | 제외 | **현재 선택**. 논리적 client-role 분리라고만 주장 | 격리 요구·실측 위험이 비용을 정당화할 때 분리 |

- Kafka listener는 public internet에 노출하지 않고 Kubernetes 내부에서만 엽니다.
- Broker 통신은 TLS로 암호화하고 client-role별 principal과 ACL을 사용합니다. 익명
  PLAINTEXT listener는 두지 않습니다.
- Relay producer는 원본 topic write, Notification consumer는 원본 topic read와 자기 consumer
  group, DLQ writer는 dead-letter write에 필요한 권한만 가집니다. Replay identity는 평상시
  application identity와 분리합니다.
- Calico NetworkPolicy는 operator와 허용한 producer·consumer의 DNS·broker traffic만 엽니다.
- Certificate와 credential은 Kubernetes Secret lifecycle을 사용합니다. 값을 Git·image·event·
  log에 남기거나 application과 broker credential을 합치지 않습니다.
- Credential 생성·rotation·폐기와 workload rollout은 현재 application Secret bootstrap의
  owner를 무단 확장하지 않고, 실행 계획에서 Kafka 전용 operation으로 설계합니다.
- 첫 topology에서는 producer·consumer가 같은 process·Pod에 있어 role별 credential을
  논리적으로 나눠도 Pod 침해 반경은 권한의 합집합입니다. 이를 workload 보안 격리로
  표현하지 않으며, 별도 Secret mount 수준의 격리가 필요하면 같은 artifact를 role별
  workload로 분리하는 추가 비용·resource를 먼저 검증합니다.
- Kafka 장애는 API readiness 실패가 아닙니다. PostgreSQL에 outbox를 기록할 수 없는 경우만
  Community write 실패이며, relay·consumer·broker 상태는 별도 health·backlog 신호입니다.

## D8. 관측·부하·장애 qualification

**결정 질문:** 닫힌 성능·복구 기준을 어떤 구간별 신호와 재현 가능한 시나리오로 판정해야
Kafka 외부 병목이나 작은 환경의 성공을 Kafka 확장성으로 오판하지 않습니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Request→outbox→broker→consumer→inbox 구간별 신호와 DB 결과 oracle의 교차 대조 |
| 공통 비교 축 | 재현성, 병목 귀속, 사용자 결과 정확성, 환경별 주장 한계, 도구 lifecycle |
| 영향 | API 지표만으로 합격시키지 않고 닫힌 threshold와 backlog·resource를 함께 판정 |
| 배제 대안 | Broker health/lag/count 0 단독 판정, target node 내부 부하 생성, single-node HA 주장 |
| 재검토 조건 | 현재 신호로 60분 시계열·장애 구간·종료 판정을 재현할 수 없음이 확인됨 |

| 공통 평가축 | Broker 단독 health·lag | 현재 source의 cross-layer snapshot·DB oracle | 전용 collector·dashboard·alert stack |
| --- | --- | --- | --- |
| 사용자 결과 | Inbox 누락·중복 판정 불가 | Outbox·receipt·inbox 기대 집합 대조 | 같은 DB oracle을 별도로 연결해야 함 |
| 병목 귀속 | Application·DB·generator 누락 | API·DB·Kafka·node를 시간 정렬 | 장시간 상관·alert에 유리 |
| 추가 운영 | 작지만 합격 oracle 불충분 | 실행 artifact와 evidence 정리 필요 | Collector·storage·retention·upgrade 추가 |
| 판정 | 제외 | **단일-node 선택** | 현재 방식이 60분 evidence를 재현하지 못할 때 별도 도입 |

| 독자 질문 | 필요한 신호 | 판정 경계 |
| --- | --- | --- |
| API가 Kafka에서 분리됐는가 | Community latency·오류율, outbox insert 실패 | Broker 장애 중 API 성공과 함께 봄 |
| Event가 어디서 멈췄는가 | Pending outbox count·oldest age, claim·publish 성공·실패 | DB 적체와 broker 거부를 구분 |
| Broker가 정상인가 | Produce latency·error, under-replicated partition, offline partition, disk·network | Single node에서 HA 신호로 확대 금지 |
| Consumer가 따라오는가 | Lag, 처리·retry·DLQ, rebalance, inbox 도착 지연 | Offset만이 아니라 DB 결과와 대조 |
| 복구가 끝났는가 | Outbox·lag 수렴, receipt·inbox 기대 집합 | Count 0 하나로 누락 없음 주장 금지 |
| 자원이 버티는가 | Application·PostgreSQL·Kafka·node CPU, memory, disk I/O·capacity, network·connection | 가장 먼저 포화된 자원을 Kafka 성능으로 오판 금지 |

Event ID는 제한된 structured log와 처리 영수증의 correlation에 사용하되 metric label로 넣지
않습니다. Account UUID, post ID, payload, topic key와 credential도 metric·일반 log에서 제외합니다.

현재 단일 node 기능 qualification은 Actuator, Kafka admin·consumer group 상태, PostgreSQL
검증 query, pod·node resource와 log를 시간 정렬해 사용합니다. 이 표면으로 장시간 시계열과
alert를 재현할 수 없으면 기능 qualification까지만 통과시키고 운영 관측 완료로 표현하지
않습니다. 다중 node 부하 qualification에는 Prometheus-compatible collector·retention·dashboard와
alert routing을 별도 도구 도입으로 검증합니다.

### 부하와 장애 qualification

부하 생성기는 target Kubernetes node 밖에서 실행해 application·Kafka와 CPU·network를
경쟁하지 않습니다. 현재 저장소에 표준 부하 도구가 없으므로 실행 계획은 arrival-rate,
percentile·error threshold, 고정 seed와 종료 code를 재현 가능한 artifact로 남길 수 있는 k6를
우선 비교하되, 도구 선택은 공식 version·image·owner·제거 조건과 함께 닫습니다.

### 측정 경로

| 경로 | 측정할 결과 |
| --- | --- |
| 동기 요청 | 댓글·답글 RPS, API p50·p95·p99, error, DB lock·connection, outbox commit 비용 |
| Relay | Claim·publish rate, oldest outbox age, retry·duplicate publish, DB·producer resource |
| Kafka | Produce·fetch latency, partition throughput, disk·network, replication·controller 상태 |
| Consumer | Consume rate, lag, retry·DLQ, DB transaction, inbox 도착 지연, backlog 해소 시간 |

### 환경별 합격 주장

- 로컬·CI는 결정론적인 contract·failure test만 통과시킵니다.
- 단일 node staging의 정상 운영점은 가장 먼저 포화되는 자원의 70% 이하입니다. 같은 자원·
  workload의 현재 동기 경로보다 지속 가능한 대화 쓰기 처리량이 낮아지지 않고 API p95가
  10% 넘게 악화되지 않아야 합니다.
- 정상 운영점의 60분 test에서 API 오류율은 0.1% 미만, 알림 도착 p99는 5초 이내이고 event
  유실과 사용자 중복 알림이 없어야 합니다. Broker를 10분 중단해도 댓글·답글 작성은
  성공하며, 복구 뒤 새 event를 처리하면서 20분 안에 중단 중 backlog를 해소해야 합니다.
- 별도의 필수 파괴 시나리오에서 broker Pod와 `emptyDir`을 소실시켜 KRaft metadata,
  record와 consumer offset이 없는 상태를 만듭니다. Cluster·topic을 재생성하고
  downstream receipt 또는 승인된 terminal disposition이 없는 outbox만 재발행한 뒤,
  기대 event 집합의 누락 0, 사용자 중복 0, 모든 미완료 receipt의 완료를 확인해야 합니다.
- 이 single-node 결과에는 최대 RPS·HA 주장을 붙이지 않습니다.
- 다중 node qualification은 정상·점증·급증·장시간 부하, broker 하나 중단, leader 변경,
  consumer rebalance, network 지연, backlog recovery를 같은 workload와 resource identity로
  반복합니다.
- Production 용량은 제품 owner가 확정한 peak·burst·SLO·RPO·RTO가 있어야 계산합니다. 값이
  없으면 engineering capacity를 측정할 수는 있어도 production 적합 판정은 할 수 없습니다.

기존 동기 경로와 비동기 경로는 같은 application artifact·database snapshot·요청 corpus,
resource·network 진입점과 warm-up·측정 구간으로 비교합니다. Generator 포화, EC2 CPU credit,
RDS connection·CPU, node pressure가 먼저 한계를 만들면 Kafka 성공이 아니라 판정 불가입니다.

## D9. 전환, rollback과 production 승격

**결정 질문:** 기존 동기 경로에서 어떤 호환 조건으로 전환·rollback하고, 무엇이 갖춰지기
전에는 production 적합성을 주장하지 않아야 합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Additive schema → shadow 수렴 → 비동기 전환 → 검증 뒤 동기 의존 제거 |
| 공통 비교 축 | 사용자 중복, 가역성, backlog owner, 이전 artifact 호환, production evidence |
| 영향 | 동기·비동기 경로가 겹치는 기간과 flag-off rollback, outbox 보존 창이 필요 |
| 배제 대안 | Big-bang 전환, broker 장애 때 outbox 중단, 미완료 전체 무차별 replay |
| 재검토 조건 | 확정 peak·SLO·RPO·RTO·비용·운영 책임과 별도 qualification evidence가 생김 |

| 공통 평가축 | Big-bang 비동기 전환 | Shadow 수렴 뒤 단계 전환 | 동기·비동기 무기한 병행 |
| --- | --- | --- | --- |
| 누락·중복 관찰 | 전환 뒤에만 발견 | 같은 source 결과를 unique·receipt로 대조 | 중복 write 비용과 owner가 상시 남음 |
| Rollback | 이전 artifact·schema 호환에 의존 | Flag-off와 보존 outbox로 되돌림 | 전환 종료 조건이 없음 |
| 부하·복구 | 실제 traffic에서 즉시 위험 | Gate별 evidence 뒤 다음 단계 | 두 경로 비용이 capacity를 왜곡 |
| 판정 | 제외 | **선택** | 제외 |

### 전환 계약

1. Outbox·처리 영수증·event contract를 기존 application이 무시할 수 있는 additive schema로
   먼저 준비합니다.
2. 기존 동기 inbox를 유지한 채 outbox write·relay·consumer를 shadow로 켜고, 두 경로가 기존
   unique constraint를 통해 같은 사용자 결과로 수렴하는지 비교합니다.
3. Event 누락·중복, backlog, latency와 단일 node 기능 qualification이 닫히면 동기 Notification
   호출을 끄고 outbox를 최종 경로로 사용합니다.
4. 장애·재시작·flag-off rollback을 통과하고 보존 창이 끝난 뒤에만 동기 코드 제거를 검토합니다.

Shadow 기간에는 동기와 consumer가 동일 inbox row를 만들 수 있으므로 unique constraint가
충돌을 정상 no-op으로 처리해야 합니다. 처리 영수증은 inbox가 이미 존재해도 event가 완료된
사실을 기록합니다.

### Rollback과 복구

- 새 비동기 경로의 application 결함이면 relay·consumer를 멈추고 동기 경로를 다시 켤 수
  있습니다. 이미 저장된 outbox·Kafka record·receipt는 삭제하지 않습니다.
- Kafka 장애 때는 outbox write를 끄지 않습니다. Broker가 돌아오면 pending event를 다시
  publish합니다.
- Broker data·offset 손실은 새 cluster·topic을 복원한 뒤 downstream receipt 또는 승인된
  terminal disposition이 없는 PostgreSQL outbox만 exact 미완료 범위로 계산해 재발행합니다.
  완료 조건 없는 기간 기준 삭제와 전체 기간 무차별 replay는 하지 않습니다.
- Schema rollback은 이전 application이 새 nullable table·column을 무시할 수 있어야 하며,
  destructive migration은 보존·replay window가 끝나기 전에 수행하지 않습니다.

### Production 승격 조건

다음이 모두 있어야 production topology를 선택합니다.

- Peak API·event 처리량, burst와 최대 backlog
- API·알림 SLO, RPO·RTO와 허용 장애 범위
- Self-managed 운영 책임·on-call 역량과 managed service 비용 비교
- 다중 AZ network·node·block storage와 backup·upgrade 책임
- 실제 qualification의 처리량·resource·복구 증거

Strimzi를 유지하면 dedicated controller·broker node pool, replication·quorum, rack awareness,
dynamic block storage, upgrade·rebalance와 disaster recovery를 별도 설계합니다. Managed Kafka를
선택하면 VPC·IAM·TLS·quota·비용·migration을 검증합니다. 어느 경로도 단일 node manifest의
replica 수만 바꾸는 것으로 승격하지 않습니다.

## 도구 도입과 제거 판정

| 대상 | 근본 문제·권위 | 같은 시작→종료 상태의 경로 비교 | 선택과 생애주기 순효과 |
| --- | --- | --- | --- |
| Apache Kafka | 사용자 목표이자 R1의 transport; 제거하면 목표가 바뀜 | 무도구 동기 호출은 장애 격리·backlog 복구 실패 / 다른 broker는 사용자 기술 목표 변경 / Kafka는 닫힌 입력 | 도입 고정. 업무 원본은 PostgreSQL이며 Kafka 제거에는 대체 transport·미처리 처분 필요 |
| Strimzi | 사용자가 현재 Kubernetes self-managed qualification을 확정 | Raw StatefulSet은 lifecycle 재구현 / managed는 현재 범위 변경 / Strimzi는 선언형 broker·user·topic owner | 도입 고정. Operator·CRD·RBAC·certificate upgrade와 Kafka 뒤 제거 순서가 늘어남 |
| Spring for Apache Kafka | Spring Boot 안 producer/listener lifecycle과 offset·error handling 필요 | Raw `kafka-clients`는 container·commit·retry를 직접 소유 / Spring Kafka는 Boot 호환 lifecycle 재사용 | **선택**. 새 dependency 대신 직접 구현·검증할 surface를 줄이며 Boot compatibility와 listener 설정 오판 책임이 생김 |
| Testcontainers Kafka | Mock이 못 잡는 실제 serialization·broker·restart 계약을 local/CI에서 반복 | Mock은 protocol oracle 아님 / shared broker는 격리·재현성 약함 / GenericContainer는 listener wiring 재구현 / module은 Apache Kafka container lifecycle 제공 | **선택**. Testcontainers BOM·Docker 선행조건은 현재 권위로 확인됐고 test dependency·image 관리가 늘어남 |
| Ephemeral storage | 새 storage platform 없이 닫힌 단일-node qualification 수행 | Node disk transport buffer / 전용 block storage는 CSI·backup lifecycle 추가 / managed storage는 범위 변경 | **선택**. Data-loss를 허용하는 대신 파괴시험·outbox 미완료 보존이 필수 |
| Debezium·Registry·전용 관측 stack | 현재 첫 producer·consumer와 단일-node evidence에는 필수 관계 없음 | Application relay·contract test·cross-layer evidence로 같은 현재 종료 상태 도달 | 미도입. 현재 경로의 실측 실패나 다조직 governance가 생길 때만 새 심사 |
| 부하 생성 능력 | 닫힌 arrival-rate·percentile·종료 판정 재현 필요 | 임시 ad-hoc 요청은 재현·종료 code 부족 / 기존 저장소 표준 없음 / 재현 가능한 외부 generator artifact 필요 | **능력 계약만 선택**. Exact 제품은 현재 권위로 정의되지 않았고 실행 계획에서 k6를 첫 후보로 비교해 version·owner·제거까지 고정 |

| 대상 | 실제 caller·시점 | 독립 oracle과 지원 경계 | 새 실패·오판 표면 | Upgrade·rollback·제거 |
| --- | --- | --- | --- | --- |
| Apache Kafka | Community relay와 Notification consumer가 비동기 전달 때 사용 | DB outbox·receipt·inbox와 broker record를 대조해 유실·중복·backlog를 판정. Kafka는 업무 원본이나 DB 결과 oracle이 아님 | Broker ack를 사용자 완료로 오판, lag·disk·quota·network 장애 | Downstream receipt 또는 승인된 terminal disposition 전 outbox 삭제 금지. 제거 전 대체 경로와 미처리 event 처분 필요 |
| Strimzi Operator | 현재 단일-node qualification과 향후 self-managed topology에서 운영자가 broker·user·topic lifecycle 관리 | 공식 지원 Kubernetes/JVM·KRaft matrix와 실제 rolling/restore test. CR Ready만으로 data·consumer 정상 판정 금지 | Operator·CRD·RBAC·certificate·reconcile 장애 | Version hop별 공식 upgrade 경로, Git revert와 data 보존. Kafka CR·topic·user 뒤 operator·CRD 제거 |
| Managed Kafka | Production topology가 별도로 선택될 때만 application과 provider 운영자가 사용 | Provider health와 application produce/consume·DB 결과를 함께 대조. Service healthy만으로 end-to-end 판정 금지 | IAM·quota·network·비용·provider 장애와 console drift | 현재 미도입; 선택 시 endpoint 전환 rollback과 backlog 종료 뒤 service·identity·network 제거 |
| Spring for Apache Kafka | Community producer adapter와 Notification listener가 application runtime에서 사용 | Boot dependency management 지원 범위와 실제 Testcontainers contract test. Mock 성공을 broker 호환으로 오판 금지 | Retry/ack 설정이 중복·유실을 숨기거나 consumer thread를 고갈 | Boot 호환 version으로 동반 upgrade, listener flag-off와 이전 artifact rollback, Kafka 경로 제거 시 dependency·config 삭제 |
| Testcontainers Kafka | Messaging integration test가 test process에서만 사용 | 공식 Kafka image의 실제 publish/consume·restart 결과. Container start만으로 application 계약 통과 금지 | Docker availability·image architecture·test flake | BOM과 module 동반 upgrade, 실패 시 integration test만 중단, dependency 제거 시 대체 real-broker oracle 필요 |
| Ephemeral broker storage | 현재 qualification broker가 transport buffer로 사용 | Broker data 소실 뒤 outbox 재발행과 receipt·inbox 수렴으로 판정. Pod Ready를 복구 완료로 오판 금지 | Node disk·DiskPressure·broker log·offset 소실 | Kafka 제거 때 함께 폐기; 수용 기준 실패 시에만 persistent storage를 별도 심사 |
| 전용 block storage | 현재 caller 없음 | Persistent storage가 없어서 최소 경로가 실패한 실측과 outbox 기반 재발행 경계를 함께 대조 | CSI·attach·filesystem·PV bind·backup·orphan cost | 현재 제외; 사용자 승인 뒤 도입하면 별도 확장·재부착·처분 lifecycle 필요 |
| Debezium·Kafka Connect | 현재 caller 없음 | Producer 증가·polling 병목이 실측될 때만 별도 도입 심사 | WAL·connector·schema·offset 운영이 첫 event보다 큼 | 현재 제외; 도입 시 application relay와 중복 기간·제거 순서 별도 설계 |
| Schema Registry | 현재 caller 없음 | 복수 조직·언어 consumer에서 JSON compatibility test가 부족할 때만 심사 | Registry 가용성이 publish·deploy를 새로 막음 | 현재 제외; 도입 시 compatibility mode와 client fallback·schema 보존 계약 필요 |
| 부하 생성·조건부 관측 도구 | Qualification actor가 target node 밖에서 사용 | 고정 workload·resource identity와 application/DB/Kafka 결과의 교차 대조 | Generator 포화·고 cardinality·짧은 retention을 Kafka 한계로 오판 | Exact 부하 도구는 실행 계획에서 k6를 첫 후보로 비교해 version·owner·evidence retention을 고정하고 시험 뒤 제거. 관측 stack은 현재 evidence 실패 전 미도입 |

## 목표 보존과 남은 위험

| 목표·위험 | 설계의 대응 | 남은 경계 |
| --- | --- | --- |
| Community write 보호 | Request transaction에는 content와 outbox만 저장 | PostgreSQL 자체 장애는 여전히 write 실패 |
| Event 유실 방지 | Transactional outbox를 downstream receipt 또는 승인된 terminal disposition 전 삭제 금지 | 잘못된 terminal 처분은 복구 원본을 없앰 |
| 사용자 중복 방지 | Event receipt와 기존 inbox unique constraint | 새 consumer가 idempotency 경계를 우회하면 실패 |
| 독립 알림 처리 | Post key는 locality만 제공하고 event ID로 각각 수렴 | Kafka 순서를 inbox 표시 순서로 오판하지 않아야 함 |
| 전체 상황의 확장성 | 환경별 topology와 증명 범위 분리 | Production SLO·비용·운영 책임은 아직 조건부 |
| 단일 node 비용 제한 | 새 persistent storage lifecycle 없이 broker를 폐기 가능한 transport buffer로 제한 | 4 GiB memory·20 GiB root disk와 단일 failure domain은 그대로 |
| 큰 부하 목표 | 별도 다중 node qualification에서만 capacity·HA 판정 | 실제 환경 mutation·비용 권한과 workload 값 필요 |
| 보안 | TLS·client-role별 ACL·NetworkPolicy·Secret lifecycle | Co-resident process 침해 시 role credential 권한이 합쳐짐 |
| 운영 증거 | 구간별 signal과 environment-specific oracle | 상시 collector 부재 시 single-node 관측 완료 불가 |

## Source·SOT 관계

- **결과 정의와 전체 진행 상태**:
  [010 Community Kafka 이벤트 전달 기반](010-community-kafka-event-delivery.md)
- **현재 동기 알림 결정**:
  [018 일반 커뮤니티 해결 설계](../../features/2026/018-general-community-design.md#d9-서비스-안-알림의-원자성과-중복-방지)
- **제품 전체 적용 후보의 기준**:
  [100 제품 개요](../../../product/100-product-overview.md),
  [200 기능 범위](../../../product/200-feature-scope.md)
- **현재 module·DDD 경계**:
  [200 현재 프로젝트 아키텍처](../../../project/200-current-architecture.md),
  [210 모듈 지도](../../../project/210-module-map.md)
- **현재 staging node·cluster**:
  [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md),
  [150 Kubernetes cluster](../../../project/infrastructure/150-kubernetes-cluster.md)
- [Apache Kafka 설계와 전달 의미](https://kafka.apache.org/documentation/#design)
- [Strimzi KRaft·node pool·storage](https://strimzi.io/docs/operators/latest/deploying)
- [Spring Kafka listener와 offset](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html)
- [Testcontainers Kafka module](https://java.testcontainers.org/modules/kafka/)
- [Debezium outbox pattern](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
