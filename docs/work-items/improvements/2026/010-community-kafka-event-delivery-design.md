# Community Kafka 이벤트 전달 상세 설계

## 상태

Proposed

## 계획 단계

해결 설계

## 현재 결론과 다음 행동

| 구분 | 현재 내용 |
| --- | --- |
| 현재 결론 | 댓글·답글과 `CommunityConversationCreatedV1` outbox event를 같은 PostgreSQL transaction에 저장하고, application relay가 Kafka로 전달한 event를 Notification consumer가 inbox로 수렴시킵니다. 전달은 at-least-once이며 DB unique constraint와 처리 영수증(해당 event를 DB 결과에 반영했다는 완료 record)으로 사용자 중복을 막습니다. |
| 닫힌 입력 | 첫 대상은 Community 댓글·답글 알림이고, 단일 node staging에서 실제 수직 흐름·장애 복구·부하 기준을 검증하되 production 고가용성으로 표현하지 않는다는 [010 결과 정의](010-community-kafka-event-delivery.md)가 확정됐습니다. |
| 남은 gate | 이 해결 설계에는 없습니다. 실행 계획 작성·검증·확정은 다음 계획 단계의 gate입니다. |
| 현재 행동 주체 | 없음. 해결 설계는 독립 검증을 통과한 local checkpoint로 닫혔습니다. |
| 현재 행동 뒤 결과 | Work item은 `실행 계획` 대기이며 구현·staging 적용·push는 이 결과에 포함하지 않습니다. |

## 문서 책임과 설계 입력

이 문서는 장기 복원해야 할 해결 구조, 선택 이유, 경계와 재검토 조건만 소유합니다. Schema
DDL, class·manifest 목록, exact version·digest, resource 수치, 설치 명령, retry 간격, 보존
기간, 부하 script와 장애 복구 절차는 실행 계획 또는 각 영구 SOT·runbook이 소유합니다.

현재 source는 다음 제약을 만듭니다.

- `CommunityService.createComment()`는 댓글 본문을 저장한 뒤 같은 transaction에서
  `CommunityConversationNotification.store()`를 호출합니다. Notification 장애와 쓰기 비용이
  Community 요청에 직접 포함됩니다.
- Community는 게시글 작성자와 direct parent 작성자를 알고 후보 수신자를 계산하며,
  Notification은 actor 제외와 `(recipient, source comment)` 중복 방지를 소유합니다.
- Community와 Notification은 같은 Spring Boot application과 PostgreSQL을 사용합니다.
  Kafka client, broker, outbox와 event 처리 영수증은 아직 없습니다.
- Staging은 ARM64 단일 Kubernetes node에서 control plane과 application을 함께 실행합니다.
  현재 node는 live memory 4 GiB와 암호화된 20 GiB root EBS 하나를 사용하고, 별도 persistent
  volume provisioner와 metric·log backend는 저장소 표준으로 확인되지 않습니다.
- Application readiness는 현재 HTTP와 DB 가용성을 기준으로 합니다. Kafka 장애 중에도 댓글·
  답글을 받는 목표 때문에 broker·relay·consumer 상태를 API readiness와 동일시할 수 없습니다.

## 목표 구조

```text
댓글·답글 요청
  -> Community transaction
       ├─ community content 저장
       └─ 같은 PostgreSQL transaction에 immutable outbox event 저장
  -> 요청 성공

outbox relay
  -> 미전달 event를 post 순서대로 Kafka topic에 at-least-once 전송
  -> broker 확인 뒤 전달 상태 기록

Notification consumer
  -> event 계약 검증과 수신 정책 적용
  -> 같은 PostgreSQL transaction에 처리 영수증과 inbox 0~2건 저장
  -> DB commit 뒤 offset 확정
```

Kafka는 최종 경로에서 실제 알림 생성에 사용합니다. Broker 또는 consumer가 멈춰도 Community
요청은 PostgreSQL commit으로 끝나고, 복구 뒤 outbox와 Kafka backlog에서 알림이 수렴합니다.
기존 동기 inbox 호출은 단계적 전환이 끝나면 제거합니다.

## 결정 요약

| 결정 | 선택 | 주된 이유 |
| --- | --- | --- |
| D1 전달 원본 | 명시적 PostgreSQL transactional outbox | DB와 Kafka dual-write 유실 없이 현재 단일 database를 내구성 있는 인계점으로 사용합니다. |
| D2 Event 경계 | Community 사실 event 하나와 versioned JSON public contract | Community가 대화 사실을 소유하고 Notification이 수신 정책과 inbox를 소유합니다. |
| D3 Relay·순서 | 같은 application artifact의 비동기 relay, post key·sequence, at-least-once | 새 CDC platform 없이 요청을 broker에서 분리하고 같은 대화의 순서를 복구할 수 있습니다. |
| D4 Consumer | DB 처리 영수증과 inbox를 원자 저장한 뒤 offset 확정 | Kafka와 PostgreSQL 분산 transaction 없이 재전달을 안전한 정상 경로로 만듭니다. |
| D5 Kafka runtime | Strimzi KRaft 단일 broker/controller와 폐기 가능한 ephemeral storage | PostgreSQL outbox를 유일한 내구 원본으로 두고 현재 root EBS 계약과 새 CSI 도입을 모두 피합니다. |
| D6 보안 | 내부 TLS 인증, 역할별 최소 ACL, Calico NetworkPolicy | Public 노출과 익명 접근을 막고 기존 cluster 보안 경계를 재사용합니다. |
| D7 관측·부하 | 결과 정의의 신호와 비교 기준을 검증하되 새 관측 stack은 도입하지 않음 | 필요한 증거와 특정 제품 설치를 분리하고 현재 4 GiB node에 별도 platform을 끌어오지 않습니다. |
| D8 전환·복구 | 동기 경로와 event 경로의 겹침 검증 뒤 비동기 전환, outbox 보존 rollback | 사용자 결과 중복 없이 되돌릴 수 있고 Kafka 장애를 Community 장애로 확대하지 않습니다. |

## D1. PostgreSQL과 Kafka 사이의 전달 원본

### 대안

| 대안 | 장점 | 결함·비용 |
| --- | --- | --- |
| 업무 DB commit 뒤 Kafka 직접 전송 | 정상 경로가 짧습니다. | DB commit과 전송 사이의 process 중단에서 event가 사라집니다. |
| 명시적 outbox와 application relay | 한 transaction에 업무 결과와 전달 의도를 남기고 현재 codebase 안에서 상태를 관찰할 수 있습니다. | Outbox schema·relay·정리 책임이 생깁니다. |
| Spring Modulith publication registry | 현재 dependency와 transaction 통합을 재사용할 수 있습니다. | 이 작업의 Kafka 전송 순서·격리·운영 상태가 framework lifecycle에 묶이고 명시적 결과 계약이 흐려집니다. |
| Debezium CDC outbox | Application polling을 없애고 여러 서비스의 변경 스트림으로 확장하기 좋습니다. | Kafka Connect·WAL·connector 운영을 단일 event 흐름과 4 GiB node에 추가합니다. |

명시적 PostgreSQL outbox와 application relay를 선택합니다. 댓글·답글 row와 immutable event
row가 같은 transaction에서 성공하거나 함께 rollback되므로 Kafka 장애는 Community 저장
성공을 바꾸지 않습니다.

Outbox는 event ID, event type·version, aggregate key·sequence, 발생 시각, payload와 전달
상태를 보존합니다. 미전달 event는 자동 재시도하되 계약 위반은 별도 격리 상태로 멈추고 다른
aggregate의 전달을 막지 않습니다. Kafka acknowledgement는 운송 성공일 뿐 사용자 결과의
완료가 아닙니다. Notification의 처리 영수증, inbox 0건 정상 처리 또는 승인된 격리 처분이
확인되기 전에는 전달 완료 outbox도 정리하지 않습니다.

Community는 outbox에 담기는 업무 사실을, Notification은 inbox와 수신 정책을 소유합니다.
Outbox·relay·처리 영수증과 dead-letter 처분은 application integration adapter가 소유하는 전달
기술 상태이며 어느 업무 Domain도 상대 table을 직접 읽거나 변경하지 않습니다. 이 adapter의
정리 경계는 event ID별 downstream 완료 projection만 소비합니다. Original topic과 dead-letter
topic은 Kafka runtime owner가 보존합니다. Outbox·topic·dead-letter·receipt의 exact 보존
기간은 최대 장애·rollback·replay 창을 함께 계산해 실행 계획에서 정하되, receipt는 자신을
참조할 수 있는 모든 재전달 source보다 먼저 삭제할 수 없습니다. 미전달·미처분 격리 row는
나이만으로 삭제하지 않고, rollback·replay 창이 닫히고 downstream 완료 증거가 있는 event만
삭제 적격이 됩니다.

Spring Modulith와 Debezium은 현재 최소 경로에서 제외합니다. 여러 event producer가 같은
publication lifecycle을 공유하거나 Kafka Connect를 이미 운영하게 될 때만 별도 도구 도입
판정으로 재검토합니다.

## D2. Event 계약과 module 소유권

Community가 발행하는 event는 `CommunityConversationCreatedV1` 하나입니다. 이는 알림 생성
명령이 아니라 댓글 또는 답글이 생성됐다는 사실입니다.

| 논리 field | 역할 |
| --- | --- |
| `eventId` | 재전달과 처리 영수증을 결속하는 event UUID |
| `occurredAt` | Community transaction에서 확정한 UTC 발생 시각 |
| `aggregateSequence` | 같은 post 안에서 생성 순서를 복원하는 증가 순번 |
| `conversationType` | 댓글과 답글을 구분하는 값 |
| `sourceCommentId` | 생성된 댓글·답글 public UUID |
| `postId` | 대화가 속한 게시글 public UUID이자 Kafka key |
| `actorId` | 작성자 account public UUID |
| `postAuthorId` | 발생 당시 게시글 작성자 public UUID |
| `parentAuthorId` | 답글의 direct parent 작성자 public UUID, 댓글이면 없음 |

- 댓글 본문, 이메일, 내부 DB ID, access token과 secret은 event에 넣지 않습니다.
- Community는 대화 관계와 발생 당시 후보 작성자 사실을 소유합니다. Notification은 actor
  자신 제거, 같은 recipient 합치기, 향후 구독 정책과 inbox 중복 수렴을 소유합니다.
- Wire contract는 명시적 JSON이며 Java class 이름이나 persistence row를 그대로 직렬화하지
  않습니다.
- V1 consumer가 읽는 field의 삭제·type·의미 변경은 허용하지 않습니다. 호환 가능한 optional
  field만 추가하고, 호환되지 않는 변경은 새 version을 병행한 뒤 이전 backlog를 닫습니다.
- Community 내부 class·table을 Notification이 참조하지 않고, consumer adapter가 public
  event를 Notification application 입력으로 번역합니다.

### DDD 영향 판정

| 경계 | 현재·목표 지위 | 언어·상태 lifecycle | 이번 전술 적용 |
| --- | --- | --- | --- |
| Community | 게시글·댓글·답글 규칙을 소유하는 Core Subdomain의 Bounded Context | `Post`, `Comment`, `Reply`, 대화 관계와 생성 순서를 소유하고 생성 사실을 public event로 투영 | 기존 aggregate 규칙은 바꾸지 않고 outbox를 persistence adapter로 추가합니다. |
| Notification | Inbox 생성·중복·읽음과 수신 정책을 소유하는 Supporting Subdomain의 Bounded Context | `InboxNotification`, recipient policy와 읽음 lifecycle을 소유 | Consumer adapter가 public event를 application 입력으로 번역하고 inbox Entity의 lifecycle을 유지합니다. |

현재 architecture 문서의 “업무 상태 없는 기술 통합 경계” 설명은 실제 inbox·read lifecycle 및
Community 설계와 일치하지 않습니다. 이 작업의 목표 지위는 위 표로 확정하고, 구현 완료 때
architecture와 module map 영구 SOT를 함께 동기화합니다. 이는 이번 해결 설계에서 해당 SOT를
미리 수정한다는 뜻이 아닙니다.

Community는 upstream event producer이고 Notification은 downstream consumer입니다. 조직·team
관계까지 확정할 source가 없으므로 Customer/Supplier 같은 Context Map 관계 유형이나
`Published Language`를 주장하지 않습니다. 두 경계는 versioned JSON public event contract로
통합합니다. Outbox, Kafka adapter, offset과 처리 영수증은 전달 기술 상태이며 새 업무 Domain이나
microservice 경계를 만들지 않습니다.

## D3. Relay, topic과 순서

첫 topic은 Community 대화 생성 사실 전용의 versioned topic 하나이며 key는 `postId`입니다.
같은 key는 같은 partition으로 보내 Kafka 안의 순서를 유지하고, 서로 다른 post 사이에는 업무
순서를 만들지 않습니다. Partition 수, 보존 기간과 event 크기 상한은 결과 정의의 workload와
장애 창을 사용해 실행 계획에서 계산하며 해결 설계에 임의의 영구 숫자로 고정하지 않습니다.

Relay는 같은 Spring Boot artifact 안의 비동기 component로 시작합니다. 별도 Kafka Connect,
JobRunr 또는 전용 service를 추가하지 않습니다. API process와 같은 pod에서 bounded worker로
실행하되 thread·DB connection·in-flight 수를 제한하여 요청 자원을 고갈시키지 않습니다. 실측
격리가 필요해지면 같은 artifact를 relay 전용 workload로 분리할 수 있으며 outbox와 event
계약은 바꾸지 않습니다.

`aggregateSequence`는 각 post에 대해 Community transaction 안에서 atomic하게 증가하고
unique해야 합니다. 여러 relay가 겹쳐도 PostgreSQL claim이 한 event의 현재 owner를 정하고,
만료된 claim은 다른 worker가 회수할 수 있어야 합니다. 같은 post에 선행 미완료 sequence가
있으면 후행 event는 publish 적격이 아니며, Kafka가 확인하기 전에는 전달 완료로 표시하지
않습니다. 확인 뒤 DB 상태 기록 전에 process가 중단되면 같은 event가 다시 전송될 수 있으므로
delivery semantics는 at-least-once입니다. Exact sequence row·claim query·index·lease는 실행
계획이 이 불변식을 구현하고 검증할 때 정합니다.

Kafka producer의 idempotence와 broker acknowledgement는 불필요한 중복과 확인되지 않은 성공을
줄이는 방어로 사용하지만, DB와 Kafka 사이 exactly-once를 주장하지 않습니다. 최종 중복 방지는
D4의 PostgreSQL 계약이 소유합니다.

## D4. Notification consumer와 실패 수렴

Notification consumer는 다음 순서로 한 record를 처리합니다.

1. 지원하는 type·version과 필수 field를 검증합니다.
2. Post 작성자와 direct parent 작성자 후보에서 actor와 중복 recipient를 제거합니다.
3. 한 PostgreSQL transaction에서 event ID 처리 영수증과 recipient별 inbox row를 저장합니다.
4. DB commit이 끝난 record만 Kafka offset을 확정합니다.

DB commit 뒤 offset 확정 전에 process가 멈추면 record를 다시 받습니다. Event ID unique
constraint는 같은 event 처리의 반복을, 기존 `(recipient, source comment)` unique constraint는
사용자에게 보이는 알림 중복을 막습니다. 자기 글에 단 댓글처럼 inbox가 0건인 event도 처리
영수증을 남겨 완료를 구분합니다.

사용자 inbox의 canonical order와 cursor는 consumer 도착 시각이 아니라 event 발생 시각,
aggregate sequence와 event ID의 안정된 tie-breaker를 사용합니다. 따라서 오래 지연된 event가
복구돼도 새 알림처럼 위로 튀지 않고 같은 post의 결과가 생성 순서로 수렴합니다. 기존
`created_at, id` cursor 변경은 실행 계획의 schema·query·API 회귀 단위에서 함께 다룹니다.

연결·DB 같은 일시 오류는 offset을 확정하지 않고 제한된 재시도 뒤 다시 받습니다. 지원하지
않는 계약이나 반복 처리 불가 record는 원본 event ID와 Kafka 위치, 실패 분류를 보존하는
dead-letter topic으로 격리하고, 격리 전송이 확인된 뒤에만 원본 offset을 넘깁니다. Replay는
원인 수정과 exact 대상 승인을 전제로 같은 idempotent consumer 경로를 사용합니다. Retry 횟수·
간격, dead-letter 보존과 replay 명령은 실행 계획·runbook 책임입니다.

## D5. Kafka runtime, storage와 단일 node 경계

### Runtime 선택

| 선택지 | 판정 | 이유 |
| --- | --- | --- |
| Strimzi Operator와 KRaft | 선택 | Kubernetes custom resource로 broker·user·topic lifecycle을 선언하고 ZooKeeper 없이 현재 cluster 안에서 재현합니다. |
| 직접 StatefulSet·script | 제외 | Kafka lifecycle·인증·rolling 변경을 project가 직접 재구현해야 합니다. |
| 외부 managed Kafka | 현재 제외 | 첫 staging의 비용 제한과 self-managed 학습 범위를 바꾸며 network·계정·비용 계약을 새로 요구합니다. |

첫 topology는 broker와 controller 역할을 겸하는 단일 Kafka node입니다. Replication factor와
`min.insync.replicas`도 단일 node에 맞추므로 process·node·volume 장애 중 무중단이나 replica
내구성을 제공하지 않습니다. 이는 contract와 장애 복구를 검증하는 staging topology일 뿐
production 고가용성 증거가 아닙니다.

Strimzi 공식 문서는 replication factor 1에서 ephemeral storage가 적합하지 않다고 명시합니다.
그럼에도 현재 단일 node staging에서는 Kafka storage를 의도적으로 폐기 가능한 transport
buffer로 제한하고 PostgreSQL outbox를 유일한 내구 원본으로 선택합니다. 이는 production
권장이 아니라, root volume에는 persistent application data를 두지 않는 현재 infrastructure
SOT와 새 CSI를 승인 없이 추가하지 않는 최소 경로를 함께 지키는 qualification 전용 예외입니다.

Broker pod·volume·node가 소실되면 Kafka data와 offset을 복구됐다고 가장하지 않습니다. Cluster와
topic을 재생성하고 D1의 downstream 완료 조건을 충족하지 않은 outbox를 재발행해 receipt·inbox로
다시 수렴시킵니다. Ephemeral storage도 root filesystem 용량을 사용하므로 byte/time retention,
volume size limit, disk headroom과 DiskPressure 중단 기준을 실행 계획에서 workload로 계산합니다.
이 경로가 부하·복구 기준을 충족하지 못하면 단일 node 합격 실패로 기록합니다.

별도 persistent block storage·EBS CSI 또는 root storage SOT 변경은 자동 후속 작업이 아닙니다.
최소 경로가 실패한 증거, 추가 비용·권한·owner·backup·제거 책임을 먼저 제시하고 사용자가
범위를 승인할 때만 새 도구 도입으로 진행합니다.

Operator·Kafka·client의 exact version과 image digest는 ARM64, 현재 Kubernetes·Spring Boot
호환성과 지원 기간을 공식 source로 확인해 실행 계획에서 고정합니다. 해결 설계는 시간이
지나면 낡는 version 문자열을 영구 결정으로 복제하지 않습니다.

## D6. Network, 인증과 secret

- Kafka listener는 Kubernetes 내부 전용이며 Ingress, NodePort와 public load balancer로
  노출하지 않습니다.
- Broker 통신은 TLS로 암호화하고 workload identity별 인증을 사용합니다. 익명 PLAINTEXT
  listener는 두지 않습니다.
- Relay producer는 원본 topic write, Notification consumer는 원본 topic read와 자기 consumer
  group, 격리 writer는 dead-letter write에 필요한 권한만 갖습니다. Replay identity는 일반
  consumer와 분리하고 평상시 application에 부여하지 않습니다.
- Calico NetworkPolicy는 Kafka operator와 허용한 producer·consumer의 DNS·broker traffic만
  열고 다른 namespace·workload의 접근을 거부합니다.
- Certificate와 credential은 Kubernetes Secret lifecycle을 사용하며 Git, image, event payload,
  log에 값이나 재사용 가능한 token을 남기지 않습니다. Exact Secret 이름과 rotation 명령은
  manifest·runbook의 실행 시 source에서 확인합니다.
- Kafka 장애를 API readiness 실패로 바꾸지 않습니다. Relay·consumer의 health와 backlog는
  별도 관측 신호이고, DB에 outbox를 기록할 수 없는 경우만 Community write 자체의 실패입니다.

## D7. 관측과 부하 검증 경계

관측 계약은 특정 stack 설치가 아니라 다음 질문에 답할 수 있는 신호를 요구합니다.

| 질문 | 필요한 신호 |
| --- | --- |
| 요청이 Kafka에서 분리됐는가 | Community API latency·오류율, outbox insert 실패 |
| Event가 어디서 멈췄는가 | 미전달·격리 outbox 수와 가장 오래된 age, publish 성공·실패 |
| Consumer가 따라오는가 | consumer lag, 처리 성공·재시도·dead-letter, inbox 도착 지연 |
| 단일 node가 버티는가 | application·PostgreSQL·Kafka·node CPU, memory, disk, network와 connection |
| 복구가 끝났는가 | broker 중단 구간의 outbox·lag가 0으로 수렴하고 receipt·inbox가 기대 집합과 일치 |

Event payload, account UUID, topic key와 credential을 log·metric label에 넣지 않습니다. Correlation은
event ID를 제한된 structured log와 처리 영수증에 사용하고 high-cardinality metric으로 만들지
않습니다.

부하 검증은 work item이 확정한 동일 자원·동일 workload의 동기 경로 대비 기준, 포화 자원
70% 이하 운영점, 60분 지속, broker 10분 중단과 20분 backlog 복구를 그대로 사용합니다.
정상·점증·급증·장시간 시나리오는 같은 seed와 요청 비율·payload·fan-out으로 반복하고 API
p50·p95·p99와 처리량만이 아니라 outbox 지연, lag, inbox 도착, 오류율과 모든 공유 자원을 함께
수집합니다.

현재 저장소에는 Prometheus, Loki, Alloy와 load generator 표준이 없습니다. 해결 설계는 이들을
필수 platform으로 도입하지 않습니다. 실행 계획은 먼저 현재 Actuator health, application·pod·
node log와 resource snapshot, Kafka admin/consumer group 상태, PostgreSQL 검증 query와 외부
부하 실행 결과로 수용 기준 증거를 만들 수 있는지 증명합니다. 재현 가능한 증거가 부족할 때만
필요한 최소 수집기 또는 load tool을 별도 도구 도입 판정과 사용자 승인으로 제안합니다.

## D8. 전환, rollback과 확장 경계

전환은 다음 결과 순서를 지킵니다. 이는 구현 명령이 아니라 호환 조건입니다.

1. Outbox·처리 영수증과 event contract를 기존 application이 무시할 수 있는 additive 변경으로
   먼저 배포합니다.
2. 기존 동기 inbox 저장을 유지한 채 outbox write·relay·consumer를 켜 동일 event가 기존 unique
   constraint로 같은 사용자 결과에 수렴하는지 검증합니다.
3. Backlog, duplicate, latency와 부하 기준을 통과하면 Community의 동기 Notification 호출을
   끄고 outbox를 최종 경로로 사용합니다.
4. 장애·재시작·rollback 검증까지 닫힌 뒤 코드의 동기 의존을 제거합니다. Outbox와 receipt는
   보존·재처리 창이 끝나기 전에 삭제하지 않습니다.

Kafka 장애 때는 event write를 끄지 않고 Community 요청과 outbox 적재를 유지합니다. 새 비동기
경로에 application 결함이 있으면 동기 inbox 경로를 다시 켤 수 있으며, 같은 comment의
outbox·consumer 재처리는 unique constraint로 기존 inbox와 중복되지 않습니다. Broker data
손실은 새 cluster와 topic을 복원한 뒤 PostgreSQL outbox에서 승인된 범위를 재발행합니다.

Application replica 또는 relay worker를 늘려도 event key·sequence와 DB claim 계약은
유지합니다. 다중 broker·node production으로 승격할 때는 별도 RPO/RTO·peak·SLO를 먼저 받고,
replication, quorum, rack/AZ, 독립 block storage, backup·upgrade와 비용을 새 topology 설계로
검증합니다. 단일 node 합격선을 그대로 production 용량이나 고가용성 주장으로 사용하지
않습니다.

## 도구 도입과 제거 판정

| 대상 | 현재 판정 | 필수성·제거 경계 |
| --- | --- | --- |
| Apache Kafka | 사용자 목표의 핵심 runtime | Event transport를 제거하면 이번 결과 자체가 사라집니다. 제거 시 동기 경로 복원 또는 다른 transport migration이 필요합니다. |
| Strimzi Operator | 현재 Kubernetes에서 Kafka lifecycle을 재현하는 최소 관리 계층 | Raw broker manifest·script를 project가 소유하는 것보다 작은 관리 표면입니다. Kafka 제거 때 CR·operator·RBAC를 함께 제거합니다. |
| Spring for Apache Kafka | Relay producer와 Notification consumer adapter가 호출하는 Spring-managed client | Raw `kafka-clients`보다 listener container·offset commit·error handling과 Spring lifecycle을 현재 application에 결속합니다. 공식 Spring Boot dependency compatibility가 선택 oracle이고 application integration owner가 관리하며, adapter 제거 때 build dependency·config를 함께 제거합니다. |
| Strimzi ephemeral volume | 새 storage plugin 없는 단일 node transport buffer | PostgreSQL outbox가 내구 원본이며 volume은 broker pod와 함께 폐기할 수 있습니다. Kafka 제거 때 별도 persistent data lifecycle을 남기지 않습니다. |
| Debezium·Kafka Connect·Schema Registry | 도입하지 않음 | 단일 JSON event와 application relay에 필수 관계가 없습니다. 여러 producer·schema 거버넌스 요구가 생길 때 별도 판정합니다. |
| EBS CSI·Prometheus·Loki·Alloy·k6 | 이 해결 설계에서 도입하지 않음 | 각각 독립 storage·관측·부하 도구입니다. 현재 source로 수용 기준을 만들 수 없다는 증거와 유지관리 owner, 제거 경계를 제시한 뒤에만 추가합니다. |

## 목표 보존과 남은 위험

| 목표·위험 | 설계의 대응 | 남은 경계 |
| --- | --- | --- |
| Community write와 알림 장애 분리 | 같은 DB transaction에는 content와 outbox만 저장 | PostgreSQL 자체 장애는 여전히 요청 실패입니다. |
| Event 유실 방지 | Transactional outbox가 Kafka 전송 전 원본을 보존 | Outbox 정리·backup 범위를 잘못 잡으면 재발행 창이 줄어듭니다. |
| 사용자 중복 방지 | Event receipt와 기존 inbox unique constraint | 새 consumer가 이 idempotency 경계를 우회하지 않아야 합니다. |
| 같은 대화 순서 | Post key·aggregate sequence와 발생 기준 inbox 정렬 | Partition 변경 migration은 별도 검증이 필요합니다. |
| 단일 node 비용 제한 | 기존 application artifact, Strimzi 1 node와 disposable storage | 4 GiB memory·20 GiB shared disk가 가장 먼저 실패할 수 있으며 합격하지 못하면 별도 storage·topology·자원 범위를 다시 결정합니다. |
| 보안 | 내부 TLS, 역할별 ACL, NetworkPolicy와 Secret lifecycle | 단일 node compromise는 broker와 application을 함께 노출할 수 있습니다. |
| 운영 증거 | 필요한 신호와 수용 기준을 먼저 고정 | 현재 도구만으로 60분 시계열을 재현하지 못하면 별도 관측 도입 승인이 필요합니다. |
| Production 확장 | Event·outbox·consumer 계약을 topology와 분리 | 다중 AZ·RPO/RTO·비용은 현재 해결하지 않았습니다. |

## 관련 문서와 공식 근거

- **결과 정의와 품질 기준**:
  [010 Community Kafka 이벤트 전달 기반](010-community-kafka-event-delivery.md)
- **현재 동기 알림 결정**:
  [018 일반 커뮤니티 해결 설계](../../features/2026/018-general-community-design.md#d9-서비스-안-알림의-원자성과-중복-방지)
- **현재 module 경계**:
  [200 현재 프로젝트 아키텍처](../../../project/200-current-architecture.md),
  [210 모듈 지도](../../../project/210-module-map.md)
- **현재 staging node**:
  [100 Infrastructure overview](../../../project/infrastructure/100-infrastructure-overview.md),
  [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md)
- [Strimzi KRaft와 storage 구성](https://strimzi.io/docs/operators/latest/deploying)
- [Spring Kafka offset commit](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listener-container.html)
- [Debezium outbox pattern](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)
