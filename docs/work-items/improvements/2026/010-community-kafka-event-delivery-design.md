# Community Kafka 이벤트 전달 상세 설계

## 상태

Planned

## 계획 단계

계획 완료

## 현재 결론과 다음 행동

| 구분 | 현재 내용 |
| --- | --- |
| 현재 결론 | 해결 설계와 U1~U8 실행 계획을 확정했습니다. 전체 상태는 `Planned`, 계획 단계는 `계획 완료`입니다. 다음 단계는 구현입니다. |
| 닫힌 결과 경계 | Strimzi 단일-node Kafka와 Community 댓글·답글 → 서비스 안 알림 연결, 누락·사용자 중복 방지, 재시작·데이터 손실 복구를 포함합니다. 별도 부하·장애 시험 플랫폼은 후속 범위입니다. |
| 선행 외부 gate | 실제 cluster 자원·환경 권한은 U6 적용부터, Community 기반 활성화 증거는 U7 전환부터 필요합니다. 이 입력 없이도 U1~U5의 repository 작업을 준비할 수 있습니다. |
| 남은 gate | 계획 확정에 남은 gate는 없습니다. U6의 실제 환경 적용과 U7의 Community 전환 조건은 각 실행 단계에서 확인합니다. |
| 현재 행동 주체 | 다음 단계의 구현자가 U1의 댓글·이벤트 원자 저장부터 구현·자동 검증을 진행합니다. U5의 배포 산출물 준비는 독립적으로 진행할 수 있습니다. |
| 사용자 차례 | 계획에 대한 추가 선택·재확정은 없습니다. |
| 현재 행동 뒤 결과 | 구현 착수 시 `In Progress`로 전환하고 각 실행 단위의 완료 증거를 확인합니다. 실제 환경 적용은 U6·U7의 선행 조건을 충족한 뒤 진행합니다. |

## 문서 책임과 설계 입력

이 문서는 D2~D9에서 장기 복원해야 할 결정 질문, 대안, Agent 기술 선택, 목표 계약과
재검토 조건을 소유합니다. R1의 canonical owner는 work item이며, 이 문서의 R1은 그 값을
소비하는 기술 projection일 뿐 새 대안·선택을 소유하지 않습니다. 구현 task·파일 목록·
test 명령·설치 명령·exact version·image digest·resource
수치·retention 기간·운영 replay의 실행 책임은
[작업 본문의 실행 계획](010-community-kafka-event-delivery.md#실행-입력과-적용-전-확인)이 소유합니다.
실제 구현 뒤 현재 기준과 재사용 운영 절차는 영구 SOT·runbook으로 인계합니다.

설계 입력으로 확인한 source 제약은 다음과 같습니다. Live 수치는 당시 입력이며 현재 환경 상태의 재검증 결과가 아닙니다.

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
- 현재 Argo CD `car-community` AppProject는 namespace resource로 ConfigMap, ServiceAccount,
  Service, Deployment, Job과 Ingress만 허용합니다. `kafka.strimzi.io` CR과
  `networking.k8s.io/NetworkPolicy`는 허용하지 않으므로 현재 application GitOps 경로만으로
  Strimzi Kafka와 deny-by-default network 경계를 적용할 수 없습니다.

## 비교 방법과 결정 기준

R1은 사용자가 이미 고정한 Kafka 적용 경계이지 비교표가 아닙니다. D2~D9는 그 입력 아래에서
**전달 원본, event 계약, relay, consumer 수렴, runtime, 보안, 검증과 전환**을 차례로
결정하는 의존 관계입니다. 각 비교표에는 같은 질문에 답하고 같은 종료 상태를 만드는 대안만
둡니다.

### 목표를 어떤 기준으로 판정하는가

| 우선순위 | 달성할 목표 | 통과 기준 | 이 기준이 결정을 바꾸는 지점 |
| --- | --- | --- | --- |
| 1. 필수 | 정확성·내구 원본 | 댓글 transaction과 event 인계를 함께 보존하고 PostgreSQL을 복구 가능한 원본으로 유지합니다. | DB·Kafka dual-write 유실, 미완료 outbox 삭제, 사용자 중복이 있으면 탈락합니다. |
| 2. 필수 | 장애 격리·복구 | Broker·relay·consumer 장애가 댓글 성공을 rollback하지 않고, 복구 뒤 누락 없이 수렴합니다. | Request가 broker ack를 기다리거나 재전달을 유실로 바꾸는 안을 탈락시킵니다. |
| 3. 필수 | 단일 node 안전성·보안 | 현재 자원 한계, private TLS, 역할별 최소 권한과 secret 비노출을 지킵니다. | HA 과장, public·anonymous 접근, 무제한 자원 사용을 탈락시킵니다. |
| 4. 핵심 효과 | 사용자 write 분리 | API 성공을 broker 상태에서 분리합니다. 지속 처리량·latency 목표는 후속 부하 qualification에서 판정합니다. | 이번에는 장애 중 write 보존을 검증하고, 성능 향상 주장은 같은 workload 비교 뒤에만 합니다. |
| 5. 핵심 증거 | 전달·복구 판정 가능성 | Outbox·broker·receipt·inbox를 연결해 누락·중복·backlog와 병목을 구분합니다. | Lag·Pod Ready·count 0 하나만으로 전체 성공을 주장하는 안을 배제합니다. |
| 6. 최적화 | 유지보수성·확장성·비용 | 위 기준을 모두 통과한 안 중 현재 첫 수직 흐름에 불필요한 platform과 영구 운영 표면이 가장 적은 안을 고릅니다. | 미래 production HA를 미리 확정하지 않되 교체할 계약 경계를 남깁니다. |

선택은 점수를 임의로 합산하지 않습니다. 1~3번 필수 기준을 하나라도 위반한 안을 먼저
탈락시키고, 남은 안에서 4~5번의 효과와 증거가 더 직접적인 안을 고른 뒤, 효과가 같으면
6번의 구현·운영 비용이 작은 안을 선택합니다.

### 설계 논의 반영

| 역할 | 반영 내용 |
| --- | --- |
| 사용자 의견 요약 | 기술 선택을 판단할 수 있는 분해와 비교가 필요합니다. 필드명별 선택과 대안 없는 기존 사실은 판단할 기술 선택이 아니며, 전체 선택과 이유를 한눈에 읽을 수 있어야 합니다. |
| Agent 답변·판단 요약 | 분해 기준은 구조·동작·비용을 달리하는 실질 대안입니다. 바꿀 수 있는 문자열이나 수치마다 선택을 요구한 구성을 폐기하고, 기존 사실·필수 계약과 기술 비교를 구분합니다. |
| 최종 반영 | 기술 대안 비교와 선택 요약을 먼저 배치하고, 기존 제약·필드명은 상세 계약으로 보존합니다. 사용자 선택과 Agent 기술 선택을 구분해 해결 설계를 닫았습니다. |
| 사용자 의견 요약 | 이번에는 Kafka 구축에 집중하고 장애 관련 시험 플랫폼은 나중에 독립적으로 진행합니다. |
| Agent 답변·판단 요약 | 부하 도구·범용 장애 harness·별도 시험 환경 구축은 첫 수직 흐름과 분리할 수 있습니다. 실제 전달·중복 방지·재시작·데이터 손실 복구 증명은 구축 결과의 정확성을 확인하므로 이번에 유지합니다. |
| 최종 반영 | D8-B·D8-C와 별도 부하·장애 플랫폼을 후속 범위로 옮겼습니다. 수치 목표는 후속 qualification 기준으로 보존하며 이번 구축 완료로 성능·HA 달성을 주장하지 않습니다. |

### 비교표를 읽는 방법

기술 결정 ID는 하나의 설계 질문을 가리킵니다. 각 표는 같은 목적의 대안을 동일 기준에서
비교하며, 추천 뒤에는 감수 비용과 재검토 조건을 둡니다. 기존 요구를 위반하는 안은
채택 가능한 대안처럼 꾸미지 않습니다. 각 카드의 선택 상태가 결정 권위를 표시합니다.
D8-B·D8-C는 후속 참고이며 이번 선택 대기 항목이 아닙니다. D9-A는 Agent 기술 선택입니다.

### 기술 선택을 한눈에 보는 판정

| ID | 기술 질문 | 유지하는 추천 | 감수 비용 |
| --- | --- | --- | --- |
| D2-A | 전달할 이벤트 기록을 직접 관리할지 프레임워크에 맡길지 | 명시적 PostgreSQL outbox | schema, worker와 정리 작업 유지 |
| D2-B | 알림 처리 완료를 발행 측에 어떻게 전달할지 | Notification 공개 batch port | 정리 시 Notification 조회 가능성에 의존 |
| D3-A | 발생한 사실을 알릴지 알림 생성을 지시할지 | 생성 사실 event | 소비자별 해석과 정책 처리 |
| D3-B | 댓글과 답글을 하나의 이벤트 계약으로 다룰지 | 공통 event 하나 | 종류별 변화가 공통 계약에 영향 |
| D3-C | 최종 알림 수신자를 어느 module이 정할지 | Notification에서 확정 | Notification에 수신 정책 구현 |
| D3-D | 발생 당시 관계를 담을지 소비할 때 다시 조회할지 | 발생 당시 관계 public ID 포함 | 관계 ID 노출과 전송량 증가; 새 표시 정보 요구는 별도 검토 |
| D3-E | 공개 이벤트 DTO를 어디에 둘지 | shared :: integration | 공유 public surface와 mapping code 유지 |
| D3-F | 전송 데이터를 JSON으로 보낼지 Avro로 보낼지 | JSON | 호환 규칙을 DTO·test에 정확히 반영해야 함 |
| D3-G | 호환성 검사를 repository에서 할지 중앙 서비스로 강제할지 | Repository contract test | version 병행·제거와 호환 test 관리 |
| D4-A | Outbox를 조회해서 발행할지 DB 변경 로그를 읽을지 | Application polling relay | polling 부하와 worker 운영 |
| D4-B | Relay를 같은 배포 산출물로 만들지 분리할지 | 현재 application artifact | application release 결합 |
| D4-C | Relay를 API process 안에서 실행할지 분리할지 | API process의 bounded worker | API와 CPU·heap·DB budget 공유 |
| D4-D | 댓글과 답글을 같은 topic에 보낼지 | 공용 conversation topic | 종류별 운영 독립성 제한 |
| D4-E | 호환되지 않는 version을 topic으로 분리할지 | Version별 topic | version별 topic 관리 |
| D4-F | 게시글별로 묶을지 개별 대화별로 분산할지 | postId | hot post 편중; 전역·게시글별 알림 순서 비보증 |
| D4-G | 여러 relay가 작업을 나눌지 한 leader만 발행할지 | DB atomic claim + lease | DB contention과 lease 만료까지 지연 |
| D5-B | 실패 record를 제자리에서 재시도할지 별도 topic으로 보낼지 | 제한된 제자리 재시도 후 DLT | 재시도 구간의 partition 지연; 강한 업무 순서 비보증 |
| D5-C | 처리 불가 이벤트를 Kafka에 격리할지 DB에 저장할지 | Kafka DLT | broker 장애 때 격리도 지연; topic·retention 운영 |
| D5-D | 복구 시 정상 소비자를 재사용할지 DB를 직접 보정할지 | 같은 멱등 consumer로 replay | 승인 대기와 정상 consumer 가용성 의존 |
| D6-A | 단일-node 시험에서 broker와 controller를 합칠지 | dual-role | 역할 간 간섭과 HA 비보증 |
| D6-B | 시험 broker의 데이터를 보존할지 재생성할지 | 현재 기능·복구 qualification용 ephemeral | broker 데이터 전손·offset 소실; production 사용 불가 |
| D6-C | Cluster 설치와 application 설정을 누가 관리할지 | Bootstrap과 application GitOps 분리 | CRD·operator 설치와 AppProject 허용의 선행 관계 관리 |
| D7-B | Consumer를 API와 같이 실행할지 전용 workload로 나눌지 | 현재 application workload | 동일 Pod 침해 시 producer·consumer·DLT credential 권한 합집합 |
| D8-A | 기존 진단 신호를 묶을지 전용 관측 플랫폼을 도입할지 | 단일-node에는 기존 신호 종합 | 수동 시간 정렬과 장기 분석 한계 |
| D8-B | HTTP 부하를 어떤 도구로 재현할지 | 후속 검토: k6 / Gatling | 이번 도입 없음 |
| D8-C | 장애 시험을 수동으로 할지 반복 실행 도구로 만들지 | 후속 검토: 수동 / scripted harness | 이번 범용 도구 구축 없음 |
| D8-D | Kafka client lifecycle을 Spring에 맡길지 직접 관리할지 | Spring for Apache Kafka | framework 설정·upgrade 의존 |
| D8-E | 통합 test용 Kafka를 어떻게 준비할지 | Testcontainers Kafka module | Docker·ARM64 image 호환과 test flake 관리 |
| D9-A | 비동기 결과를 비교한 뒤 전환할지 한 번에 교체할지 | Shadow 비교 후 flag cutover | 동기·비동기 병행 부하와 독립 flag 관리 |

### 결정 의존 관계

```text
R1 댓글 저장과 Kafka 장애 분리
  → D2 전달 원본·완료 확인 → D3 이벤트 의미·데이터·호환 계약
  → D4 발행 작업·topic·분산 → D5 재시도·격리·복구
D6 시험 runtime·storage·GitOps와 D7 workload 보안은 배치에서 합류
  → D8 실제 전달·중복·복구 증거 → D9 전환·rollback
  후속 독립 범위: D8 부하·장애 시험 플랫폼과 성능·HA qualification
Production은 별도 요구·비용·운영 입력과 failure-domain 검증 뒤 판단
```

## R1. Kafka 적용 경계와 사용자 결과는 현재 해결 설계에서 다시 고르는 값이 아님

R1은 기술 대안 비교가 아닙니다. 사용자가 첫 적용 기술과 결과 경계를 고정했으므로 D2~D9는
그 수직 흐름을 안전하고 검증 가능하게 구현하는 방법만 결정합니다.

| 구분 | 현재 판정 | 의미 |
| --- | --- | --- |
| 확정 요구 | Community 댓글·답글 저장을 Kafka 장애와 분리하고 알림은 복구 뒤 수렴 | D2~D9의 상위 사용자 입력이며 Kafka 유지 여부나 첫 사용자 결과를 다시 묻지 않습니다. |
| 포함 경계 | Community 댓글·답글 생성에서 서비스 안 알림까지 한 수직 흐름 | 첫 producer·consumer의 닫힌 범위입니다. |
| 제외 경계 | Catalog·Account/Auth·Media·검색·통계·추천 | 이번 설계를 새 기능이나 다른 domain으로 확대하지 않습니다. |
| 재검토 조건 | 사용자가 R1 결과를 직접 변경하거나 독립 source가 결과 정의를 무효화 | Agent 기술 검토나 구현 난이도가 R1을 다시 여는 근거가 아닙니다. |

## 목표 구조

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

```text
로컬·CI Testcontainers
  -> serialization, real publish/consume, 중복·retry·DLQ 계약

현재 단일 node staging
  -> GitOps·network·credential·resource 상한과 실제 수직 흐름
  -> 기능·복구 qualification만 증명

후속 별도 다중 node·다중 AZ qualification
  -> broker 장애, replication, rebalance, 처리량, backlog 복구
  -> 큰 부하·고가용성 후보를 증명

production 후보
  -> 확정 peak·SLO·RPO·RTO·비용·운영 책임으로
     self-managed Strimzi 또는 managed Kafka를 별도 선택
```

단일 node에 broker 하나를 띄우는 결과는 위 경로의 두 번째 단계일 뿐입니다. Application
event 계약은 topology와 분리하지만, storage·replication·failure domain의 증거는 환경을
바꿔서 다시 만들어야 합니다.

## D2. PostgreSQL과 Kafka 사이의 전달 원본

### D2-A. 전달할 이벤트 기록을 직접 관리할지 프레임워크에 맡길지

댓글과 같은 DB transaction에 전달 기록을 남기는 outbox 방식은 유지합니다. 기록의 상태·재전송·정리를 누가 구현할지 비교합니다.

| 비교 기준 | 프로젝트 전용 PostgreSQL outbox | Spring Modulith publication registry |
| --- | --- | --- |
| 상태 표현 | 발행·소비 완료·예외 종료를 프로젝트 schema에 명시 | 프레임워크 publication 상태와 확장점에 맞춤 |
| 복구·정리 | 알림 완료 증거와 보존 조건을 직접 연결 | 기본 재발행 기능을 쓰되 알림 완료 조건은 추가 연결 |
| 유지보수 | schema·worker를 직접 검증 | 라이브러리 규약·upgrade에 의존 |

**추천: 명시적 PostgreSQL outbox.** 현재에는 알림 처리 완료와 예외 종료를 구분한 보존 제어가 필요하므로 상태를 직접 소유하는 기존 선택을 유지합니다.

**감수 비용:** schema, worker와 정리 작업 유지

**재검토 조건:** 여러 module이 같은 publication lifecycle을 공유하거나 직접 관리 비용이 더 커질 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D2-B. 알림 처리 완료를 발행 측에 어떻게 전달할지

Community가 outbox 삭제 적격 여부를 확인할 때 Notification의 완료 결과를 어떤 경계로 받을지 비교합니다.

| 비교 기준 | 공개 application port로 batch 조회 | 완료 이벤트로 별도 projection 유지 |
| --- | --- | --- |
| 완료 확인 | 정리 시점에 Notification의 실제 receipt 조회 | 완료 이벤트를 받아 Community 쪽에 상태 축적 |
| 장애 처리 | 조회 실패면 원본 보존·다음 정리 때 재조회 | 완료 이벤트 자체의 유실·중복과 재처리도 관리 |
| 결합·비용 | 같은 application의 호출 의존 | 호출 분리 대신 producer·consumer·상태 저장 추가 |

**추천: Notification 공개 batch port.** 현재 같은 application 안에서 새 전달 경로 없이 완료 증거를 읽을 수 있습니다. 상대 receipt table 직접 조회는 module 경계 위반이므로 후보에서 제외합니다.

**감수 비용:** 정리 시 Notification 조회 가능성에 의존

**재검토 조건:** module이 별도 service로 분리되거나 조회 비용이 실제 병목이 될 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### 원자 저장·완료 증거·보존 계약

다음은 기존에 사용자 동의가 붙은 계약을 유지한 것입니다. 각각을 다시 선택받지 않습니다.

| 책임 | 유지하는 계약 | 이유·재검토 경계 |
| --- | --- | --- |
| 원자 인계 | Community 저장과 outbox 기록은 같은 PostgreSQL transaction | DB commit 전후의 별도 publish는 유실 또는 유령 event를 만듦 |
| 발행 완료 | Broker acknowledgement 뒤 publish 상태 갱신 | Client send 반환은 broker 수락 증거가 아님; ack 정책 변경 시 재검토 |
| 소비 완료 | Notification이 event ID별 receipt의 유일한 writer | 0건 정상 처리도 완료로 구별; 결과 저장소 분리 시 재검토 |
| 예외 종료 | 근거 있는 terminal disposition 허용, 운영자 승인 필요 | 자동 오판에 따른 원본 삭제 방지; 원인·영향·재처리 불가 근거를 보존 |
| 상태 소유 | Terminal disposition과 outbox cleanup은 Community 소유 | 상대 module table을 직접 변경하지 않음 |
| 보존 | 삭제 적격 이후 유한 보존 창, bounded batch 정리 | 즉시 삭제는 replay 증거 부족, 영구 보존은 무제한 storage; exact 기간은 실행 계획 |
| 불확실한 완료 | 조회 실패·불일치·미완료는 보존 | 정상 완료 증거 또는 승인된 terminal 처분 없이는 삭제하지 않음 |

명시적 outbox를 선택합니다. Community content와 outbox row는 같은 transaction에 저장되고,
transaction rollback이면 둘 다 남지 않습니다. Outbox는 최소한 event identity·type·version,
aggregate identity, payload, 발생 시각, publish lifecycle과 retry 관측값을 소유합니다.

Publish 완료만으로 row를 삭제하지 않습니다. 각 event의 downstream 처리 영수증 또는
원인·영향·복구 불가성을 확인한 명시적 terminal disposition이 먼저 있어야 삭제 적격이
됩니다. Replay·rollback 최소 보존 기간은 이 완료 조건 위에 추가되며, 기간이 지났다는
사실만으로 미완료 row를 삭제하지 않습니다. Debezium은 여러 producer나 polling이 실제
병목이 될 때, Spring Modulith registry는 project-owned outbox 운영 비용이 가치보다 커질
때 다시 비교합니다.

Notification은 event ID별 처리 영수증의 유일한 writer이고, 다른 module이 receipt table을
직접 조회하지 않도록 완료 여부를 batch로 반환하는 application port를 제공합니다. Community의
outbox cleanup worker는 이 port에서 완료 event ID를 읽거나, 원인·영향·재처리 불가 근거와
운영자 승인을 Community outbox lifecycle에 기록한 terminal disposition만 삭제 적격으로
인정합니다. Cleanup은 적격 표시 뒤 별도 보존 창이 지난 row만 bounded batch로 삭제하며,
조회 실패·불일치·Notification 미완료는 보존으로 수렴합니다. 이렇게 Notification의 완료
owner와 Community의 outbox 보존·정리 owner를 직접 table 결합 없이 연결합니다.

**감수 비용·재검토:** Batch application port 유지; module 경계나 receipt store 변경 시 재검토합니다.

## D3. Event 계약과 module 소유권

### D3-A. 발생한 사실을 알릴지 알림 생성을 지시할지

같은 댓글로 알림을 만들더라도 Kafka 메시지가 무엇을 뜻하느냐에 따라 생산자와 소비자의 결합이 달라집니다.

| 비교 기준 | 댓글·답글 생성 사실 event | 알림 생성 command |
| --- | --- | --- |
| 메시지 의미 | 무슨 일이 발생했는지 전달 | 수행할 알림 행동을 지정 |
| 정책 변경 | Notification이 자기 정책으로 해석 | Community가 알림 명령 계약을 알아야 함 |
| 다른 소비자 | 같은 사실을 별도 목적에 재사용 가능 | 알림 명령 의미와 목적이 맞지 않을 수 있음 |

**추천: 생성 사실 event.** 첫 흐름의 알림 처리를 분리하면서 Community가 후속 행동을 지시하지 않게 합니다.

**감수 비용:** 소비자별 해석과 정책 처리

**재검토 조건:** 사실만으로 요구한 결과를 재현할 수 없거나 메시지 목적이 명령으로 바뀔 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D3-B. 댓글과 답글을 하나의 이벤트 계약으로 다룰지

두 종류가 공유하는 데이터와 처리 흐름을 한 계약에 둘지, 종류별로 독립 진화시키는지 비교합니다.

| 비교 기준 | 공통 event + 종류 구분 | 댓글·답글 event 분리 |
| --- | --- | --- |
| 중복 구현 | 공통 field·handler 공유 | 공통 field도 두 계약에서 관리 |
| 독립 진화 | kind 분기와 호환 규칙 유지 | 종류별 version·consumer 변화 가능 |
| 현재 적합성 | 같은 수신자 계산 흐름에 작음 | 독립 lifecycle이 아직 없어 관리점 증가 |

**추천: 공통 event 하나.** 현재 댓글과 답글은 동일한 알림 처리 계약을 공유합니다.

**감수 비용:** 종류별 변화가 공통 계약에 영향

**재검토 조건:** 종류별 보존·보안·소비 lifecycle이 달라질 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D3-C. 최종 알림 수신자를 어느 module이 정할지

작성자 제외·중복 제거 같은 수신자 규칙을 생산자와 소비자 중 어디에 둘지 비교합니다.

| 비교 기준 | Community에서 확정 | Notification에서 확정 |
| --- | --- | --- |
| 생산자 책임 | 대화 저장과 알림 정책을 함께 소유 | 대화 관계만 전달 |
| 정책 변경 | Community 수정·배포 필요 | Notification 내부에서 변경 |
| 전달 데이터 | 최종 수신자 목록 | 판단에 필요한 작성자 관계 |

**추천: Notification에서 확정.** Community는 발생 사실을 소유하고 Notification은 알림 결과를 계산하는 책임을 유지합니다.

**감수 비용:** Notification에 수신 정책 구현

**재검토 조건:** 알림 정책의 업무 owner가 실제로 바뀔 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D3-D. 발생 당시 관계를 담을지 소비할 때 다시 조회할지

Notification이 최종 수신자를 정한다는 조건에서, 판단에 쓸 관계 데이터를 어디서 얻을지 비교합니다.

| 비교 기준 | 발생 당시 관계 public ID 포함 | 대화 ID만 보내고 Community 재조회 |
| --- | --- | --- |
| 시간 의미 | 당시 글·부모 작성자 관계 보존 | 조회 시점의 변경·삭제에 영향 |
| 장애 결합 | 소비 중 Community 조회 불필요 | 추가 조회 실패·재시도 필요 |
| 데이터 비용 | 관계 ID만큼 payload 증가 | payload는 작고 query 비용 증가 |

**추천: 발생 당시 관계 public ID 포함.** 재처리 때도 같은 관계로 결과를 계산하고 추가 조회 의존을 만들지 않습니다. 본문·이름·email·token·secret·DB 내부 ID는 포함하지 않습니다.

**감수 비용:** 관계 ID 노출과 전송량 증가; 새 표시 정보 요구는 별도 검토

**재검토 조건:** 발생 당시 대신 현재 관계를 적용해야 하는 제품 요구가 생길 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D3-E. 공개 이벤트 DTO를 어디에 둘지

Domain·JPA 객체를 그대로 직렬화하지 않는다는 경계 아래, 명시적인 전송 DTO를 두 module에 어떻게 공개할지 비교합니다.

| 비교 기준 | shared :: integration의 immutable DTO | Community 공개 API package |
| --- | --- | --- |
| compile 의존 | 양쪽이 중립 전송 package에 의존 | Notification이 Community 공개 계약에 의존 |
| 의미 소유권 | Community 소유임을 명시적으로 유지 | package와 의미 owner가 일치 |
| 관리 위험 | shared에 업무 로직이 새지 않도록 제한 | 생산자 API 변경이 소비자 의존에 노출 |

**추천: shared :: integration.** 기존 선택대로 field·type·version만 공개하고 event 의미는 Community가 소유합니다. Notification API에 생산자 사실을 두는 안은 책임 방향이 맞지 않습니다.

**감수 비용:** 공유 public surface와 mapping code 유지

**재검토 조건:** module 공개 package 정책이나 계약 배포 경계가 달라질 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D3-F. 전송 데이터를 JSON으로 보낼지 Avro로 보낼지

같은 event 내용을 표현하는 방식의 차이를 비교합니다. 중앙 호환성 강제 여부는 D3-G에서 별도로 비교합니다.

| 비교 기준 | JSON | Avro |
| --- | --- | --- |
| 읽기·진단 | text로 직접 확인 가능 | schema를 사용해 해석 |
| schema 관리 | DTO·직렬화와 test 규약으로 관리 | writer·reader schema와 toolchain 관리 |
| 현재 도입 부담 | 기존 JSON stack 재사용 | schema·encoding 경로 추가 |

**추천: JSON.** 첫 producer·consumer에 필요한 명시 계약을 기존 stack으로 표현합니다. 성능 우열은 payload 실측 없이 단정하지 않습니다.

**감수 비용:** 호환 규칙을 DTO·test에 정확히 반영해야 함

**재검토 조건:** 다언어 schema 사용이나 실측 전송 효율 요구가 현재 비용을 넘을 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D3-G. 호환성 검사를 repository에서 할지 중앙 서비스로 강제할지

기존 소비자가 새 event를 읽을 수 있는지 검사하는 위치와 운영 책임을 비교합니다.

| 비교 기준 | Repository contract test | Schema Registry |
| --- | --- | --- |
| 적용 범위 | 같은 repository의 producer·consumer 변경 검증 | 여러 배포·조직의 schema 정책을 중앙 관리 |
| 추가 운영 | test·fixture 유지 | service·schema subject·권한·가용성 관리 |
| 한계 | repository 밖 소비자는 별도 계약 필요 | 등록 정책·client 연동을 실제 적용해야 강제 가능 |

**추천: Repository contract test.** 현재 두 module의 호환성은 같은 변경에서 검증할 수 있습니다. Optional 추가만 허용하고 삭제·type·의미 변경은 V1/V2 병행을 유지합니다.

**감수 비용:** version 병행·제거와 호환 test 관리

**재검토 조건:** 다조직·다언어 소비자가 늘어 중앙 강제가 필요할 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### 기존 구조 제약과 이벤트 상세 계약

Notification은 [200 현재 프로젝트 아키텍처](../../../project/200-current-architecture.md)와
[210 모듈 지도](../../../project/210-module-map.md)가 정의한 기술 통합 경계입니다.
독립 Bounded Context 지위는 이번 선택지가 아닙니다. Preference·채널 정책·보존처럼 독립
업무 lifecycle이 생길 때만 다시 설계합니다.

명시적 wire DTO 사용, Community의 event 의미 소유, 관계 판단용 public ID만 전달하는 범위는
기존 사용자 동의를 유지합니다. Domain object·JPA entity 직렬화는 업무·저장 모델 변경을
공개 계약에 전파하므로 사용하지 않습니다. 아래는 선택한 구조의 구체적 표현이며 이름별
승인 항목이 아닙니다.

| 이름·값 | 역할과 명명 근거 |
| --- | --- |
| `eventId` | transport message가 아닌 논리 사건 identity; 중복 처리 판정 |
| `eventType = CommunityConversationCreated` | 다른 context의 동명 사건과 구별하는 종류 |
| `eventVersion = 1` | 날짜 대신 순차적인 호환 version 표현 |
| `CommunityConversationCreatedV1` | 병행 version을 구별하는 Java DTO 이름; class 수 관리 비용 |
| `occurredAt` | 원 사건 발생 시각; inbox 생성 시각과 구별 |
| `postId` | 게시글 관계와 partition locality; 일반 aggregate ID보다 대상 명확 |
| `conversationId` | 댓글·답글 공통 identity; 막연한 source ID를 쓰지 않음 |
| `conversationKind = COMMENT / REPLY` | 종류 구분; 생성 의미를 enum에 중복하지 않음 |
| `actorAccountId` | 행위자의 account public ID |
| `postAuthorAccountId` | 최종 recipient가 아닌 글 작성자 후보 관계 |
| `directParentAuthorAccountId?` | 답글에만 존재하는 nullable 직접 부모 작성자 관계 |

필드·enum의 exact wire 표기는 아래와 같습니다. 명명은 전역 naming/generated DTO 정책 변경 시,
관계 입력은 수신 정책·개인정보 범위 변경 시, version 표현은 호환 정책 변경 시 재검토합니다.
계약의 저장·mapping·공개 호환성 관리 비용은 각 이름별 선택 투표가 아닌 DTO 유지 책임입니다.

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

| 경계 | 현재 지위 | 이 설계의 책임 |
| --- | --- | --- |
| Community | 게시글·댓글·답글 규칙과 대화 관계를 소유 | Outbox의 유일한 writer이며 request transaction에서 event 사실을 생성 |
| Notification | 현재 SOT상 발송·inbox 기술 통합 경계 | 처리 영수증과 inbox의 유일한 writer이며 public event를 자기 application 입력으로 번역 |
| `shared :: integration` | 두 module의 immutable wire DTO | 업무 로직·persistence·Kafka client 없이 JSON field·type·version만 공개 |

`eventType`, 숫자 version, Java class suffix, kind literal과 각 field 이름은 구현 전 contract
test에서 이 목록과 exact 일치시킵니다. 명명 변경만으로 앞선 기술 선택이나 제품 요구를 바꾸지 않습니다.

## D4. Relay, topic과 처리 독립성

### D4-A. Outbox를 조회해서 발행할지 DB 변경 로그를 읽을지

업무 transaction 밖에서 전달 기록을 Kafka로 옮기는 두 방식입니다. 요청에서 직접 publish하는 방식은 장애 분리·원자 인계 요구를 충족하지 않아 제외합니다.

| 비교 기준 | Application polling relay | Debezium / Kafka Connect CDC |
| --- | --- | --- |
| 발견 방식 | 미발행 row를 주기적으로 claim | DB 변경 로그에서 outbox 변경 포착 |
| DB·운영 부담 | polling query·claim·worker 유지 | WAL·connector offset·Connect 운영 |
| 확장 조건 | 현재 첫 producer에 관리 범위가 작음 | 여러 producer에서 공통 CDC 운영 가능 |

**추천: Application polling relay.** 첫 흐름에서는 별도 CDC platform 없이 outbox 상태와 발행을 직접 연결합니다.

**감수 비용:** polling 부하와 worker 운영

**재검토 조건:** 실측 polling 병목 또는 여러 producer의 공통 CDC 요구

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D4-B. Relay를 같은 배포 산출물로 만들지 분리할지

코드가 포함되는 artifact의 release 경계를 비교합니다. 실행 process 배치는 D4-C가 다룹니다.

| 비교 기준 | 현재 application artifact | 별도 relay artifact |
| --- | --- | --- |
| version 정합 | Community·wire 변경과 같은 release | 호환 version을 별도로 조율 |
| 독립 배포 | application과 함께 release | relay만 독립 release 가능 |
| 관리 비용 | 기존 build·배포 재사용 | build·배포·rollback owner 추가 |

**추천: 현재 application artifact.** 독립 release 요구가 없는 첫 흐름에 기존 build 경계를 사용합니다.

**감수 비용:** application release 결합

**재검토 조건:** relay만 독립 release할 필요가 생길 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D4-C. Relay를 API process 안에서 실행할지 분리할지

같은 artifact를 사용하면서도 실행 자원과 장애 영향을 어디까지 분리할지 비교합니다.

| 비교 기준 | API process의 bounded worker | 같은 Pod의 별도 process | Relay 전용 workload |
| --- | --- | --- | --- |
| 자원 경계 | thread·connection 상한, heap 공유 | process 분리, Pod 자원 공유 | Pod 자원·scaling 독립 |
| 장애 영향 | process 종료가 API·relay에 함께 영향 | Pod 종료는 함께 영향 | 배포·종료 영향 분리 |
| 운영 비용 | 현재 배치 재사용 | sidecar 시작·종료 관리 | 별도 deployment·capacity 관리 |

**추천: API process의 bounded worker.** 현재 단일-node 범위에서 bounded worker로 시작합니다. 기능 검증에서 자원 고갈을 확인하고 부하별 간섭 수치는 후속 qualification에서 판정합니다.

**감수 비용:** API와 CPU·heap·DB budget 공유

**재검토 조건:** 실측 API 간섭이 수용 기준을 깨면 같은 artifact의 전용 workload 검토

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D4-D. 댓글과 답글을 같은 topic에 보낼지

이벤트 종류 수와 별도로 Kafka 보존·접근·소비 운영 단위를 나눌지 비교합니다.

| 비교 기준 | 공용 conversation topic | 종류별 topic |
| --- | --- | --- |
| 정책 | 같은 보존·ACL 적용 | 종류별 보존·ACL 가능 |
| 소비 설정 | kind를 해석하는 공통 listener | topic별 listener·운영 설정 |
| 관리점 | 작음 | topic·partition·관측 관리 증가 |

**추천: 공용 conversation topic.** 현재 두 종류에 독립 보존·보안 요구가 없습니다.

**감수 비용:** 종류별 운영 독립성 제한

**재검토 조건:** 종류별 retention·보안·consumer 요구가 달라질 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D4-E. 호환되지 않는 version을 topic으로 분리할지

V1과 V2를 병행할 때 Kafka 배치 단위를 비교합니다.

| 비교 기준 | Version별 topic | 한 topic에서 version 혼합 |
| --- | --- | --- |
| 소비 전환 | topic 단위로 구·신 소비자 분리 | listener가 여러 version을 판별 |
| 장애 진단 | version별 lag·오류 분리 | 관측에서 version을 별도 분리 |
| 비용 | topic·ACL·제거 관리 증가 | decoder 분기·혼재 처리 증가 |

**추천: Version별 topic.** 호환되지 않는 계약의 전환과 제거 경계를 분명히 합니다.

**감수 비용:** version별 topic 관리

**재검토 조건:** 공통 migration 표준이나 소비 모델이 바뀔 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D4-F. 게시글별로 묶을지 개별 대화별로 분산할지

Partition key는 어느 record를 같은 Kafka partition에 모을지 정합니다. 공개 inbox 정렬 순서를 정하는 선택은 아닙니다.

| 비교 기준 | postId | conversationId |
| --- | --- | --- |
| 분산 단위 | 같은 게시글 event 결집 | 댓글·답글 건별 분산 |
| 부하 편중 | 인기 게시글이 hot partition 가능 | 개별 대화로 더 잘 분산 |
| 관계 locality | 같은 게시글 처리 경로가 모임 | 같은 게시글도 여러 partition 사용 |

**추천: postId.** 기존 선택인 게시글 관계 locality를 유지합니다. 최종 수신자가 소비 단계에서 정해지므로 recipientId는 현재 발행 key 후보가 아닙니다.

**감수 비용:** hot post 편중; 전역·게시글별 알림 순서 비보증

**재검토 조건:** locality보다 균등 분산이 중요해지는 실측 결과

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D4-G. 여러 relay가 작업을 나눌지 한 leader만 발행할지

Replica가 늘어도 작업이 고착되지 않도록 발행 작업의 소유권과 crash 복구를 비교합니다.

| 비교 기준 | DB atomic claim + lease | Leader 한 개 + failover |
| --- | --- | --- |
| 작업 분배 | row별로 여러 worker가 나눔 | 선출된 leader가 처리 |
| crash 복구 | lease 만료 뒤 다른 worker가 재획득 | leader 실패 탐지·재선출 |
| 관리 비용 | claim contention·lease tuning | 선출 수단과 fencing; 단일 worker 병목 |

**추천: DB atomic claim + lease.** 현재 PostgreSQL을 사용해 row별 소유권과 회수를 함께 관리합니다.

**감수 비용:** DB contention과 lease 만료까지 지연

**재검토 조건:** 확장 모델 또는 claim 고착·중복 증거가 달라질 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### 발행·작업 소유권 상세 계약

At-least-once 전달은 기존 사용자 동의를 유지합니다. Broker ack 뒤 DB 갱신 전 중단으로
중복이 생길 수 있으므로 소비자가 멱등 처리합니다. At-most-once 유실이나 Kafka transaction만으로
PostgreSQL까지 exactly-once라는 주장은 현재 요구를 만족하는 대안이 아닙니다.

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

**감수 비용·재검토:** Lease 만료까지 복구 지연; claim 고착·중복 증거가 생기면 재검토합니다.

## D5. Notification consumer와 실패 수렴

### D5-B. 실패 record를 제자리에서 재시도할지 별도 topic으로 보낼지

일시 오류의 재시도 동안 같은 partition의 후속 record를 기다리게 할지 비교합니다.

| 비교 기준 | 제한된 제자리 재시도 후 DLT | Retry topic으로 이동 후 재시도 |
| --- | --- | --- |
| 후속 record | 재시도 중 지연, 상한 뒤 격리하면 진행 | 원본 partition은 먼저 진행 |
| 전달·순서 | 원본 offset과 오류 처리를 한 경계에서 관리 | topic 이동 ack·지연·순서 변화 추가 관리 |
| 운영 비용 | 추가 retry topic 없이 backoff·상한 설정 | retry topic·listener·ACL·보존 관리 |

**추천: 제한된 제자리 재시도 후 DLT.** 첫 흐름의 기존 제한 retry 계약을 구체적으로 비교합니다. Record 단위·상한 있는 exponential backoff·명시 retryable allowlist를 사용하고, 처리 불가 오류는 격리합니다.

**감수 비용:** 재시도 구간의 partition 지연; 강한 업무 순서 비보증

**재검토 조건:** 일시 실패가 다른 event의 지연 목표를 반복해서 깨는 경우

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D5-C. 처리 불가 이벤트를 Kafka에 격리할지 DB에 저장할지

정상 처리가 끝나지 않은 record를 버리지 않고 복구 대상으로 보관하는 매체를 비교합니다.

| 비교 기준 | Kafka dead-letter topic(DLT) | DB 실패 기록 table |
| --- | --- | --- |
| 복구 입력 | 원본 record와 Kafka 위치를 함께 유지 | DB schema에 payload·실패 상태 저장 |
| 장애 의존 | DLT publish 실패면 원본 offset 진행 금지 | DB 저장 실패면 원본 offset 진행 금지 |
| 운영 비용 | topic·retention·ACL·재전달 운영 | table·query·cleanup·replay adapter 운영 |

**추천: Kafka DLT.** 현재 Kafka 전달 경로에서 격리·재처리를 이어갑니다. DLT는 성공한 알림이 아니며 원본 outbox는 완료 증거 전까지 남습니다.

**감수 비용:** broker 장애 때 격리도 지연; topic·retention 운영

**재검토 조건:** broker 밖 독립 복구 저장소가 필요할 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D5-D. 복구 시 정상 소비자를 재사용할지 DB를 직접 보정할지

원인을 고친 뒤 승인된 실패 event 집합을 어떤 실행 경로로 복구할지 비교합니다.

| 비교 기준 | 같은 멱등 consumer로 replay | 별도 DB backfill |
| --- | --- | --- |
| 정책 일치 | 정상 recipient·receipt·inbox 처리 재사용 | 동일 정책을 별도 구현·검증 |
| 복구 가능 조건 | 정상 consumer를 복원할 수 있음 | consumer 복원이 어려워도 별도 작업 가능 |
| 실패 위험 | 기존 오류가 남으면 재실패 | 정상 계약과 보정 script가 어긋날 위험 |

**추천: 같은 멱등 consumer로 replay.** 운영자가 원인 수정과 exact event ID·offset 집합을 승인한 뒤 정상 계약으로 복구합니다.

**감수 비용:** 승인 대기와 정상 consumer 가용성 의존

**재검토 조건:** consumer 복원이 불가능해 독립 backfill 검증이 필요한 경우

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### 처리 완료와 실패 분류 계약

| 항목 | 유지하는 계약 | 필요한 이유 |
| --- | --- | --- |
| 멱등·완료 | Event ID receipt와 inbox를 한 PostgreSQL transaction에 기록 | Inbox unique 단독으로는 0건 정상 처리와 미처리 구별 불가 |
| Offset | DB commit 뒤 완료 처리 | Commit 전 offset 확정은 DB 결과 유실 가능 |
| 실패 분류 | Consumer adapter의 명시 retryable allowlist | Domain에 전달 실패 정책을 넣거나 모든 오류를 같은 일시 오류로 보지 않음 |
| Retry 단위 | Record 단위, 상한 있는 exponential backoff | Batch 전체 재시도로 독립 event 실패 반경을 넓히지 않음 |
| Retry 종료 | 시도 상한 뒤 DLT, 격리 뒤 후속 record 진행 | 무한 재시도로 partition 고착 금지; exact 횟수·간격은 실행 계획 |
| DLT 보존 | 원본 identity·topic·partition·offset·실패 분류 | Payload만으로 복구 대상을 잃지 않음; 개인정보 추가 금지 |
| DLT offset | DLT acknowledgement 뒤 원본 offset 완료 | 격리 record 자체가 유실되지 않게 함 |
| Replay | 운영자 승인된 exact ID·offset 집합만 정상 consumer로 처리 | 무차별 전체 replay와 자동 재실패 방지 |

실패 분류는 업무 실패 의미가 생길 때, 재시도는 batch 원자성·지연 목표가 바뀔 때,
격리·replay는 복구 매체·승인 정책·정상 consumer 복원 가능성이 바뀔 때 재검토합니다.

### 상세 계약: 처리 영수증·inbox transaction과 offset

**계약의 목적:** 중복 전달·중단·처리 불가 event 뒤에도 inbox가 사용자에게 한 결과로
수렴하도록 DB transaction과 offset·격리 경계를 어떻게 나눠야 합니까?

Consumer의 한 record 처리 계약은 다음과 같습니다.

1. 지원 type·version과 필수 field를 검증합니다.
2. 후보 작성자에서 actor와 중복 recipient를 제거합니다.
3. 한 PostgreSQL transaction에 event 처리 영수증과 recipient별 inbox 0~2건을 저장합니다.
4. DB commit이 끝난 record만 Kafka offset 완료 대상으로 둡니다.

DB commit 뒤 offset 확정 전에 process가 중단되면 record를 다시 받습니다. Event ID unique
constraint는 같은 event 처리 반복을, 기존 `(recipient, source comment)` unique constraint는
동기·비동기 경로가 겹칠 때 사용자 중복을 막습니다. Inbox가 0건인 event도 처리 영수증을
남겨 성공과 미처리를 구분합니다.

### 상세 계약: Inbox 공개 시각과 cursor 의미

**계약의 목적:** 지연 도착한 event의 공개 시각과 cursor를 어떤 의미로 유지해야 기존 inbox
API와 새 비동기 전달을 함께 보존할 수 있습니까?

Public `createdAt`은 현재처럼 inbox projection이 DB에 생성된 시각을 뜻하고 정렬·cursor도
`created_at,id`를 유지합니다. Event의 `occurredAt`은 전달 지연·추적 evidence로 보존하되
현재 공개 정렬 의미를 바꾸지 않습니다. 사용자가 “원 활동 시각 기준 inbox”를 요구할 때만
새 공개 필드·cursor version·backfill을 함께 설계합니다.

### 상세 계약: Retry, DLQ와 replay

**계약의 목적:** 일시 실패와 처리 불가 event를 어떻게 구분해 후속 record를 막지 않으면서
exact 미완료 결과를 복구합니까?

일시적 broker·network·DB 오류는 제한된 backoff 뒤 같은 record를 다시 처리합니다. 지원하지
않는 계약이나 반복 처리 불가 payload는 원본 event ID, topic·partition·offset과 실패 분류를
보존하는 dead-letter topic으로 격리합니다. DLQ publish가 확인되기 전에는 원본 offset을 넘기지
않습니다. 격리는 해당 알림의 미완료 처분이며 다른 알림의 업무 순서를 막지 않습니다. Replay는
원인 수정, exact 대상 승인과 같은 idempotent consumer 경로를 사용합니다.

## D6. Runtime, storage와 환경별 증명 범위

### D6-A. 단일-node 시험에서 broker와 controller를 합칠지

확정된 Strimzi 단일-node qualification 안에서 record 처리와 cluster metadata 역할의 process 배치를 비교합니다.

| 비교 기준 | 한 dual-role process | 역할별 process 분리 |
| --- | --- | --- |
| 자원 | process 기본 비용이 작음 | 각 JVM·Pod의 기본 비용 증가 |
| 격리 | broker 부하가 controller에도 영향 | process 간 영향 일부 분리 |
| 증명 범위 | 단일 failure domain; HA 아님 | 같은 node라 여전히 HA 아님 |

**추천: dual-role.** 현재 자원에서 기능·복구 경로를 확인하는 시험 구성입니다.

**감수 비용:** 역할 간 간섭과 HA 비보증

**재검토 조건:** 별도 failure-domain qualification 또는 production 설계를 열 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D6-B. 시험 broker의 데이터를 보존할지 재생성할지

Kafka 자체의 데이터 보존과 PostgreSQL 원본에서 사용자 결과를 복구하는 것은 구별해서 비교합니다.

| 비교 기준 | Ephemeral emptyDir | Persistent block storage |
| --- | --- | --- |
| Pod 교체 | record·metadata·offset 소실 가능 | volume 재연결로 log 보존 가능 |
| 복구 | 미완료 outbox 재발행·receipt 대조 | volume·Kafka log 복구와 application 대조 |
| 운영 책임 | node disk 상한·파괴 시험 | CSI·StorageClass·volume·backup·비용 |

**추천: 현재 기능·복구 qualification용 ephemeral.** Strimzi 공식 문서는 replication factor 1에서 ephemeral을 부적합하다고 경고합니다. 이 구성은 broker 내구성을 보장하지 않는 시험 한정 선택이며 outbox 복구 시험 통과가 필요합니다. Persistent 안이 내구성 면에서 유리하다는 사실을 숨기지 않습니다.

**감수 비용:** broker 데이터 전손·offset 소실; production 사용 불가

**재검토 조건:** 시험 기준 실패 또는 지속 내구성이 필요하면 비용·owner와 함께 persistent/topology 재검토

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D6-C. Cluster 설치와 application 설정을 누가 관리할지

Operator·CRD 같은 cluster 자원과 namespace Kafka 설정의 변경 책임을 비교합니다.

| 비교 기준 | Bootstrap과 application GitOps 분리 | Platform owner가 둘 다 관리 |
| --- | --- | --- |
| 권한 | application에는 필요한 namespace kind만 허용 | application 권한은 작지만 모든 변경이 platform 경유 |
| 변경 속도 | topic·user·Kafka CR을 application 흐름에서 변경 | platform 작업 일정·검토에 의존 |
| 관리 비용 | 두 owner의 선행 상태 연결 | 한 owner에 업무별 Kafka 설정 누적 |

**추천: Bootstrap과 application GitOps 분리.** 기존 cluster 권한 경계를 유지하면서 namespace 변경을 application delivery로 관리합니다.

**감수 비용:** CRD·operator 설치와 AppProject 허용의 선행 관계 관리

**재검토 조건:** platform 운영 책임이나 GitOps resource 소유 정책이 바뀔 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### 고정된 실행 범위와 적용 제약

현재 Kubernetes에서 Strimzi 단일-node qualification을 수행하는 범위는 이미 확정돼 있습니다.
Raw StatefulSet이나 managed Kafka로 이 범위를 다시 선택받지 않습니다. KRaft는 현재 지원
경로의 제약이며 ZooKeeper를 신규 동등 대안으로 내놓지 않습니다. Exact 지원 version은 아래
호환성 확인을 거쳐 실행 계획에서 고정합니다.

CRD·operator → AppProject의 필요한 kind 허용 → namespace Kafka CR의 선행 관계를 지킵니다.
CRD가 이미 관리됨이 확인된 경우에만 설치 중복을 생략합니다. Wildcard 권한은 사용하지 않습니다.
Community readiness는 durable outbox를 쓰는 PostgreSQL에 의존하며 Kafka 장애와 분리합니다.
Broker·relay·consumer 상태는 별도 health·backlog로 관측합니다. 이는 R1에서 따라오는 계약입니다.

### 상세 계약: Kafka runtime과 controller

**계약의 목적:** 확정된 현재 Kubernetes의 단일-node Kafka lifecycle을 어떤 runtime과
controller가 소유해야 합니까?

현재 AppProject whitelist에는 `kafka.strimzi.io` CR과 `NetworkPolicy`가 없습니다. Cluster-scoped
CRD·operator·RBAC 설치는 platform bootstrap owner가 별도 운영 경계에서 수행하고, application
GitOps owner는 namespace 안의 `Kafka`, `KafkaNodePool`, `KafkaTopic`, `KafkaUser`와
NetworkPolicy에 필요한 kind만 exact allowlist로 추가합니다. Wildcard group/kind와
cluster-scoped resource 허용은 금지하며, 이 permission 확대가 검토·적용되기 전에는 Kafka
application manifest를 sync하지 않습니다. Operator bootstrap 실패와 application sync 실패는
각 owner의 evidence로 분리합니다.

### 상세 계약: Broker storage와 data-loss 복구

**계약의 목적:** 현재 단일-node qualification에서 broker data를 보존할 것인지, 손실 시 어떤
원본과 actor로 사용자 결과를 복구할 것인지 결정합니다.

Broker pod·storage·offset이 소실되면 Kafka data 복원을 주장하지 않습니다. 새 broker·topic을
만든 뒤 downstream receipt도 승인된 terminal disposition도 없는 PostgreSQL outbox만
재발행해 receipt·inbox로 수렴시킵니다. Ephemeral storage도 node disk를 사용하므로
retention·용량·DiskPressure 경계는 실행 계획에서 workload로 계산합니다. 이 최소 경로가
이번 기능·데이터 복구 기준을 충족하지 못하면 합격 실패로 기록하고, persistent storage나 별도
topology는 영향·비용·owner를 제시해 새 승인을 받습니다.

Qualification broker가 ephemeral storage의 유일한 caller이고 infrastructure operations
owner가 생성·파괴와 DiskPressure evidence를 소유합니다. Kafka 제거 때 함께 폐기하며,
수용 기준 실패 전에는 persistent storage를 현재 필수 범위로 승격하지 않습니다.

### 상세 계약: 환경별 증명 범위

**계약의 목적:** Local/CI, 현재 single-node, 별도 failure-domain 환경과 production 후보가 각각
어떤 주장까지만 증명할 수 있습니까?

| 환경 | 고정된 최소 형태 | 증명할 수 있는 것 | 증명할 수 없는 것 |
| --- | --- | --- | --- |
| 로컬·CI | Testcontainers가 시작·제거하는 KRaft single broker | 실제 serialization, publish·consume, retry·DLQ·멱등성 계약 | Kubernetes 운영, HA, 장시간 capacity |
| 공유 기능 qualification | 현재 Kubernetes의 Strimzi KRaft 단일 dual-role node | 실제 network·identity·중단·복구와 수직 흐름 | HA, production SLO, 독립 failure domain |
| 후속 부하·장애 qualification | Application과 broker가 분리된 최소 3 failure domain·다중 AZ | Replication, quorum, broker 장애, rebalance, 처리량, backlog 복구 | 장기 production 운영과 확정 SLO |
| Production 후보 | 확정 SLO·RPO·RTO·비용·운영 책임에 맞춘 dedicated Strimzi 또는 managed Kafka | 승인된 운영 목표 | 별도 region DR 등 계획 밖 보장 |

Exact Strimzi·Kafka·client version과 image digest는 ARM64, 현재 Kubernetes·Spring Boot
compatibility와 지원 기간을 공식 source로 확인해 실행 계획에서 고정합니다.

## D7. Network, 인증·인가와 secret

### D7-B. Consumer를 API와 같이 실행할지 전용 workload로 나눌지

역할별 credential은 유지한 채, 실제 process 침해와 자원 간섭을 어느 수준까지 분리할지 비교합니다.

| 비교 기준 | 현재 application workload | Notification 전용 workload |
| --- | --- | --- |
| credential 침해 | 같은 process에서 접근 가능한 권한 합집합 | Secret mount와 process를 분리할 수 있음 |
| 자원·장애 | API와 CPU·heap·종료 영향 공유 | consumer 자원·종료·scaling 분리 |
| 현재 운영 비용 | 기존 배치 재사용 | deployment·capacity·배포 관리 추가 |

**추천: 현재 application workload.** 첫 단일-node 범위에서 사용하되 논리 ACL 분리를 process 보안 격리라고 부르지 않습니다.

**감수 비용:** 동일 Pod 침해 시 producer·consumer·DLT credential 권한 합집합

**재검토 조건:** Secret mount 격리 요구나 실측 간섭·위험이 추가 workload 비용을 정당화할 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### 보안 요구와 역할별 권한 계약

Public 노출 금지, TLS, deny-by-default NetworkPolicy, Git 밖 Kubernetes Secret은 기존 보안
요구입니다. 평문·익명 접근·credential의 Git 저장을 선택 가능한 대안으로 만들지 않습니다.

| 역할 | 별도 principal·credential의 허용 범위 |
| --- | --- |
| Relay producer | 원본 topic write |
| Notification consumer | 원본 topic read와 자기 consumer group |
| DLT writer | Consumer와 별도 principal·credential, dead-letter write |
| Replay operator | 승인된 operation 전용 identity; 상시 application 권한과 분리 |

ACL은 exact topic·group에 적용합니다. Role·topic·group이 바뀌면 해당 ACL을 갱신하며 공유
superuser나 prefix wildcard로 해결하지 않습니다. DNS·operator 등 필수 flow만 network에
허용합니다. 역할별 credential 증가와 allowlist 관리는 현재 보안 계약의 운영 비용입니다.

### 실행 계획에서 구체화할 credential lifecycle

기존에 유예된 값의 상태를 보존합니다. 이 문서 수정으로 새 issuer·운영 주체를 채택하지 않습니다.

| 구체화 항목 | 현재 경계 | 실행 계획의 확인 내용 |
| --- | --- | --- |
| Certificate issuer | Strimzi/Kubernetes Secret lifecycle 사용 | Strimzi CA와 별도 승인 issuer의 지원·신뢰 관계 |
| 생성 진입점·owner | Kafka 전용 operation으로 설계 | 기존 bootstrap 확장과 전용 owner 중 실제 책임 경계 |
| Rotation | Exact 방식 미정 | In-place 교체와 overlap 후 rollout의 지원·실패·복구 |
| 구 credential 폐기 | Exact gate 미정 | Rotation·rollout 검증 뒤 폐기 증거 |
| Rollout owner | Exact 실행 책임 미정 | Kafka operation과 기존 GitOps delivery의 실제 인계 |

### 상세 계약: Listener와 network 경계

**계약의 목적:** Kafka listener를 어디에 열고 어떤 network 경계의 workload만 접근하게
해야 합니까?

- Kafka listener는 public internet에 노출하지 않고 Kubernetes 내부에서만 엽니다.
- Broker 통신은 TLS로 암호화하고 client-role별 principal과 ACL을 사용합니다. 익명
  PLAINTEXT listener는 두지 않습니다.
- Calico NetworkPolicy는 operator와 허용한 producer·consumer의 DNS·broker traffic만 엽니다.
  현재 AppProject에 허용되지 않은 `NetworkPolicy`는 D6의 application GitOps exact
  allowlist 변경과 보안 검토가 끝난 뒤에만 적용합니다.

### 상세 계약: Client role과 Kafka ACL

**계약의 목적:** Relay, consumer, DLQ와 replay actor에 어떤 Kafka principal·resource 권한을
부여해야 한 actor의 침해나 오설정이 전체 topic 권한으로 번지지 않습니까?

- Relay producer는 원본 topic write, Notification consumer는 원본 topic read와 자기 consumer
  group, DLQ writer는 dead-letter write에 필요한 권한만 가집니다. Replay identity는 평상시
  application identity와 분리합니다.
- Client role은 별도 principal과 ACL로 구분하고 공유 superuser credential을 application에
  배포하지 않습니다.

### 상세 계약: Certificate·credential lifecycle과 workload 격리

**계약의 목적:** Role credential을 어디에서 생성·교체·폐기하고, 현재 같은 Pod에 놓이는
producer·consumer의 실제 침해 반경을 어떻게 표현합니까?

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

## D8. 구축 검증과 후속 부하·장애 qualification

### D8-A. 기존 진단 신호를 묶을지 전용 관측 플랫폼을 도입할지

API·outbox·Kafka·consumer·DB 중 어디에서 막히는지 판정할 방법을 비교합니다.

| 비교 기준 | 기존 metric·admin·DB·log 시간 정렬 | 전용 collector·dashboard·alert |
| --- | --- | --- |
| 단기 기능 시험 | 필요 구간을 직접 대조 가능 | 같은 신호를 지속 수집 |
| 장기 운영 | retention·alert 재현이 제한됨 | 시계열·경보·장기 분석 가능 |
| 새 운영 | query·correlation 유지 | collector 가용성·저장·권한·alert owner |

**추천: 단일-node에는 기존 신호 종합.** 첫 기능 qualification에 적용하고 장기 시계열이 없으면 운영 관측 완료로 주장하지 않습니다. 다중-node 부하 qualification의 collector·retention·dashboard·alert는 별도 도입 검증 대상입니다.

**감수 비용:** 수동 시간 정렬과 장기 분석 한계

**재검토 조건:** 기존 신호로 원인을 구분할 수 없거나 상시 관측을 증명해야 할 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D8-B. 후속 검토: HTTP 부하를 어떤 도구로 재현할지

같은 요청 집합·도착률·합격 기준으로 반복 실행하고 실패를 종료 코드로 알릴 도구를 비교합니다.

| 비교 기준 | k6 | Gatling |
| --- | --- | --- |
| 시나리오 표현 | JavaScript 기반 workload·threshold | Java·Kotlin·Scala 또는 JS/TS SDK의 simulation·assertion |
| 실행 경계 | application 밖 runner에서 실행 | application 밖 runner에서 선택 SDK로 실행 |
| 현재 관리 선택 | 독립 load script·version/image 유지 | 선택 SDK의 build·CLI 유지 |

**후속 검토 기준:** k6와 Gatling은 모두 유효 대안입니다. 부하·장애 시험 플랫폼 작업에서 workload·runner·유지관리 책임을 정한 뒤 선택합니다. 이번에는 도구를 도입하거나 k6로 확정하지 않습니다.

**감수 비용:** 외부 runner·script·version 관리

**재검토 조건:** protocol·시나리오 요구가 도구 한계를 만나거나 JVM 도구 표준이 정해질 때

**선택 상태:** 후속 범위로 분리. 도구 미선택이며 현재 해결 설계의 열린 결정이 아닙니다.

### D8-C. 후속 검토: 장애 시험을 수동으로 할지 반복 실행 도구로 만들지

Broker 재시작·데이터 소실·복구 시험의 입력과 결과를 반복 가능하게 만들 방법을 비교합니다.

| 비교 기준 | Runbook에 따른 수동 시험 | Repository scripted harness |
| --- | --- | --- |
| 반복 정확도 | 운영자가 시점·대상을 맞춤 | 동일 입력·장애 주입·결과 수집 반복 |
| 중단·실패 | 각 단계의 사람 확인 | script의 중단·복구 계약 필요 |
| 비용 | 초기 작성 작음, 반복 인건비 | harness 작성·검증·갱신 |

**후속 검토 기준:** 반복 빈도와 장애 주입 범위가 정해진 뒤 수동 시험과 scripted harness를 비교해 선택합니다. 이번에는 범용 장애 플랫폼을 구축하지 않습니다. 첫 흐름의 복구 검증은 통합 테스트와 대상별 운영 절차로 수행하며 정확한 actor·권한·중단 경계는 실행 계획에서 구체화합니다.

**감수 비용:** harness 유지보수와 파괴 시험 비용

**재검토 조건:** 기존 시험 platform이 같은 결과 oracle을 제공할 때

**선택 상태:** 후속 범위로 분리. 도구 미선택이며 현재 해결 설계의 열린 결정이 아닙니다.

### D8-D. Kafka client lifecycle을 Spring에 맡길지 직접 관리할지

Producer·listener 시작·종료, offset과 오류 처리를 어느 수준에서 구현할지 비교합니다.

| 비교 기준 | Spring for Apache Kafka | Raw kafka-clients |
| --- | --- | --- |
| lifecycle | Spring container·설정 통합 | poll loop·thread·종료 직접 구현 |
| 오류·commit | framework 계약을 설정·검증 | commit·retry orchestration 직접 검증 |
| 유지보수 | Boot 호환 version과 설정 관리 | 직접 제어 범위가 크지만 관리 코드 증가 |

**추천: Spring for Apache Kafka.** 현재 Spring Boot application의 lifecycle에 맞춰 첫 adapter를 구성합니다.

**감수 비용:** framework 설정·upgrade 의존

**재검토 조건:** Spring compatibility나 직접 client 제어 요구가 달라질 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### D8-E. 통합 test용 Kafka를 어떻게 준비할지

Test마다 실제 broker를 사용하면서 격리와 시작 설정의 책임을 비교합니다.

| 비교 기준 | Testcontainers Kafka module | GenericContainer | 공유 broker |
| --- | --- | --- | --- |
| 격리 | test process가 생성·제거 | 직접 생성·제거 구성 | 다른 test·사용자와 상태 공유 |
| listener 설정 | Kafka 전용 API 활용 | listener·port wiring 직접 관리 | 외부 환경 설정에 의존 |
| 비용 | container runtime·image 호환 | 같은 runtime + 관리 코드 | 시작 비용 절감, 정리·충돌 위험 |

**추천: Testcontainers Kafka module.** 기존 BOM을 활용해 실제 protocol·직렬화·중복·실패를 격리해서 검증합니다.

**감수 비용:** Docker·ARM64 image 호환과 test flake 관리

**재검토 조건:** test infrastructure 또는 공식 image 지원이 달라질 때

**선택 상태:** 사용자 확정. 위 추천안을 선택했으며 비교 대안과 재검토 조건은 유지합니다.

### 이번 구축의 수용 기준과 후속 측정 계약

이번 구축은 실제 Kafka를 거친 댓글·답글 → 알림 결과, 업무 저장과 outbox의 원자성,
중복 전달의 멱등성, retry·DLT·replay, broker·consumer 재시작과 ephemeral 데이터 손실 후
복구를 검증합니다. 유실·사용자 중복 허용값은 각각 0입니다. PostgreSQL event·receipt·inbox의
기대 집합과 실제 결과를 대조하며 metric count·log만으로 성공을 선언하지 않습니다.

장시간 부하, 정상 운영점, 처리량·latency·복구 시간의 수치 목표는
[작업 본문의 후속 부하·장애 qualification 기준](010-community-kafka-event-delivery.md#후속-부하장애-qualification-기준)이
소유합니다. 이 수치의 검증과 별도 시험 플랫폼 구축은 이번 완료 gate가 아닙니다.
같은 corpus·snapshot·resource·warm-up과 node 밖 generator를 사용하는 비교 원칙은
후속 시험에 유지합니다. 이번 구축 완료는 성능·capacity·HA 목표 달성을 뜻하지 않습니다.

Metric은 bounded stage·result label만, 진단 log는 event identity와 실패 class만 허용합니다.
본문·계정·credential을 진단 편의로 추가하지 않습니다.

### 상세 계약: 관측과 결과 oracle

**계약의 목적:** 이번 전달·복구 결과와 후속 성능 기준을 어떤 구간별 신호로 구분해 판정해야
Kafka 외부 병목이나 작은 환경의 성공을 Kafka 확장성으로 오판하지 않습니까?

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

### 후속 계약: 부하 생성 도구

**계약의 목적:** 같은 HTTP workload의 arrival-rate, percentile·오류 threshold와 종료 code를
어떤 도구로 재현하고 evidence artifact를 남깁니까?

부하 생성기는 target Kubernetes node 밖에서 실행해 application·Kafka와 CPU·network를
경쟁하지 않습니다. 후속 시험 플랫폼 작업에서 arrival-rate, percentile·error threshold,
고정 seed와 종료 code의 재현 방식을 정합니다. D8-B에서 도구를 선택한 뒤 해당 작업의
계획에서 exact version·image를 고정하며 이번 Kafka 구축 실행 계획에는 포함하지 않습니다.

### 상세 계약: 측정 경로와 환경별 합격 주장

**계약의 목적:** 어떤 구간의 신호와 환경별 oracle을 함께 만족해야 Kafka 도입 효과·병목·복구와
주장 한계를 판정할 수 있습니까?

| 경로 | 측정할 결과 |
| --- | --- |
| 동기 요청 | 댓글·답글 RPS, API p50·p95·p99, error, DB lock·connection, outbox commit 비용 |
| Relay | Claim·publish rate, oldest outbox age, retry·duplicate publish, DB·producer resource |
| Kafka | Produce·fetch latency, partition throughput, disk·network, replication·controller 상태 |
| Consumer | Consume rate, lag, retry·DLQ, DB transaction, inbox 도착 지연, backlog 해소 시간 |

- 로컬·CI는 결정론적인 contract·failure test만 통과시킵니다.
- 후속 부하 qualification에서 단일 node staging의 정상 운영점은 가장 먼저 포화되는 자원의 70% 이하입니다. 같은 자원·
  workload의 현재 동기 경로보다 지속 가능한 대화 쓰기 처리량이 낮아지지 않고 API p95가
  10% 넘게 악화되지 않아야 합니다.
- 후속 qualification의 정상 운영점 60분 test에서 API 오류율은 0.1% 미만, 알림 도착 p99는 5초 이내이고 event
  유실과 사용자 중복 알림이 없어야 합니다. Broker를 10분 중단해도 댓글·답글 작성은
  성공하며, 복구 뒤 새 event를 처리하면서 20분 안에 중단 중 backlog를 해소해야 합니다.
- 이번 구축의 별도 필수 복구 시나리오에서 broker Pod와 `emptyDir`을 소실시켜 KRaft metadata,
  record와 consumer offset이 없는 상태를 만듭니다. Cluster·topic을 재생성하고
  downstream receipt 또는 승인된 terminal disposition이 없는 outbox만 재발행한 뒤,
  기대 event 집합의 누락 0, 사용자 중복 0, 모든 미완료 receipt의 완료를 확인해야 합니다.
- 이 single-node 결과에는 최대 RPS·HA 주장을 붙이지 않습니다.
- 후속 다중 node qualification은 정상·점증·급증·장시간 부하, broker 하나 중단, leader 변경,
  consumer rebalance, network 지연, backlog recovery를 같은 workload와 resource identity로
  반복합니다.
- Production 용량은 제품 owner가 확정한 peak·burst·SLO·RPO·RTO가 있어야 계산합니다. 값이
  없으면 engineering capacity를 측정할 수는 있어도 production 적합 판정은 할 수 없습니다.

기존 동기 경로와 비동기 경로는 같은 application artifact·database snapshot·요청 corpus,
resource·network 진입점과 warm-up·측정 구간으로 비교합니다. Generator 포화, EC2 CPU credit,
RDS connection·CPU, node pressure가 먼저 한계를 만들면 Kafka 성공이 아니라 판정 불가입니다.

## D9. 전환, rollback과 production 승격

### D9-A. 비동기 결과를 비교한 뒤 전환할지 한 번에 교체할지

같은 최종 비동기 경로로 옮기면서 전환 결함을 발견하고 되돌리는 방법을 비교합니다.

| 비교 기준 | Shadow 비교 후 flag cutover | 한 번의 배포로 replacement |
| --- | --- | --- |
| 전환 전 증거 | 동기·비동기 결과를 같은 corpus로 비교 | 사전 test 후 전환된 경로에서 확인 |
| 장애 복구 | flag로 소비·발행을 멈추고 동기 경로 복귀 | 이전 artifact 배포가 필요 |
| 비용·위험 | 병행 부하·unique 충돌·flag 상태 관리 | 구조는 작지만 결함이 바로 primary 결과에 영향 |

**추천: Shadow 비교 후 flag cutover.** 기존 inbox unique의 no-op과 event receipt로 병행 결과를 대조하고 수용 증거 뒤 primary 경로를 바꿉니다.

**감수 비용:** 동기·비동기 병행 부하와 독립 flag 관리

**재검토 조건:** 안전한 shadow가 불가능하거나 배포·rollback 체계가 바뀔 때

**선택 상태:** Agent 기술 선택. 비교 가능한 결과를 확보하고 flag로 복귀할 수 있는 위 추천안을 유지합니다. 사용자 개별 승인으로 기록하지 않습니다.

### 전환·중단·보존 계약

| 대상 | 기존 계약 |
| --- | --- |
| Schema | Additive first, 이전 application이 새 nullable table·column을 무시할 수 있음 |
| 제어 | Relay·consumer 독립 flag; 상시 동기 fallback은 async-off일 때만 사용 |
| Shadow | 전환 전 비교 기간에만 동기·비동기 병행; 영구 fallback과 구별 |
| Async-off | Outbox 기록 계속 유지 |
| Rollback | Outbox·receipt·DLT 보존, 원본 purge 중단 |
| 동기 code 제거 | 장애·재시작·rollback 수용 증거 통과 및 보존 창 종료 뒤 검토 |
| 파괴적 schema 제거 | Outbox 보존·replay window 종료 뒤 |
| Broker data-loss | 정상 receipt도 승인 terminal 처분도 없는 outbox만 재발행 |

Broker record·offset까지 소실된 상황에서 offset rewind는 복구 대안이 아닙니다. 복구 원본,
완료 판정, flag 상태와 보존 창의 관계를 유지하며 exact 실행 명령은 후속 실행 계획이 다룹니다.

### Production 기술 선택의 선행 입력

현재는 production provider를 고를 입력이 없습니다. 아래 값을 각 담당자가 정하고 실제
qualification 증거가 있어야 Dedicated Strimzi와 managed Kafka를 같은 조건에서 비교합니다.

| 필요한 입력 | 입력 책임 | 처음 영향을 받는 판단 |
| --- | --- | --- |
| Peak API·event rate, burst, 최대 backlog | 제품·운영 요구 owner | 처리 용량·partition·buffer·retention |
| API·알림 latency SLO | 제품·운영 요구 owner | 지연 목표; 현재 10%·5초 시험 기준과 구별 |
| RPO·RTO·허용 장애 | 제품·운영 요구 owner | 원본 보존·복구 topology·failure domain |
| 비용 상한 | 비용 책임자 | 같은 기간·traffic의 self-managed/managed 비교 |
| 24×7 on-call·upgrade·incident 책임 | 운영 책임자 | 유지 가능한 운영 모델 |

Dedicated Strimzi는 controller·broker·storage·DR를 직접 운영하고, managed Kafka는 provider와
network·IAM·quota·비용 책임을 나눕니다. 현재 single-node의 replica만 늘리는 안은 HA 증거가
없으므로 production 후보가 아닙니다. 이 입력 공백은 현재 문서의 구조 수정 작업을 막지 않습니다.

### 상세 계약: 전환 계약

**계약의 목적:** 기존 동기 알림 경로를 어떤 호환 순서로 새 비동기 경로로 전환해야 누락·사용자
중복을 관찰하면서 되돌릴 수 있습니까?

1. Outbox·처리 영수증·event contract를 기존 application이 무시할 수 있는 additive schema로
   먼저 준비합니다.
2. 기존 동기 inbox를 유지한 채 outbox write·relay·consumer를 shadow로 켜고, 두 경로가 기존
   unique constraint를 통해 같은 사용자 결과로 수렴하는지 비교합니다.
3. Event 누락·중복 0, backlog 복구와 단일 node 기능 검증이 닫히면 동기 Notification
   호출을 끄고 outbox를 최종 경로로 사용합니다.
4. 장애·재시작·flag-off rollback을 통과하고 보존 창이 끝난 뒤에만 동기 코드 제거를 검토합니다.

Shadow 기간에는 동기와 consumer가 동일 inbox row를 만들 수 있으므로 unique constraint가
충돌을 정상 no-op으로 처리해야 합니다. 처리 영수증은 inbox가 이미 존재해도 event가 완료된
사실을 기록합니다.

### 상세 계약: Rollback과 복구

**계약의 목적:** Application 결함, Kafka 중단 또는 broker data 손실 때 어떤 경로를 끄고 어떤
상태를 보존·재발행해야 exact 미완료 결과만 복구합니까?

- 새 비동기 경로의 application 결함이면 relay·consumer를 멈추고 동기 경로를 다시 켤 수
  있습니다. 이미 저장된 outbox·Kafka record·receipt는 삭제하지 않습니다.
- Kafka 장애 때는 outbox write를 끄지 않습니다. Broker가 돌아오면 pending event를 다시
  publish합니다.
- Broker data·offset 손실은 새 cluster·topic을 복원한 뒤 downstream receipt 또는 승인된
  terminal disposition이 없는 PostgreSQL outbox만 exact 미완료 범위로 계산해 재발행합니다.
  완료 조건 없는 기간 기준 삭제와 전체 기간 무차별 replay는 하지 않습니다.
- Schema rollback은 이전 application이 새 nullable table·column을 무시할 수 있어야 하며,
  destructive migration은 보존·replay window가 끝나기 전에 수행하지 않습니다.

### 상세 계약: Production 승격 조건

**계약의 목적:** 어떤 제품·운영 입력과 실제 qualification evidence가 모두 있어야 production
Kafka topology를 비교·선택할 수 있습니까?

다음이 모두 있어야 production topology를 선택합니다.

- Peak API·event 처리량, burst와 최대 backlog
- API·알림 SLO, RPO·RTO와 허용 장애 범위
- Self-managed 운영 책임·on-call 역량과 managed service 비용 비교
- 다중 AZ network·node·block storage와 backup·upgrade 책임
- 실제 qualification의 처리량·resource·복구 증거

Strimzi를 유지하면 dedicated controller·broker node pool, replication·quorum, rack awareness,
dynamic block storage, upgrade·rebalance와 disaster recovery를 별도 설계합니다. Managed Kafka를
선택하면 VPC·IAM·TLS·quota·비용·migration을 검증하고 provider health뿐 아니라 application
publish·consume과 DB 결과까지 대조합니다. 어느 경로도 단일 node manifest의 replica 수만
바꾸는 것으로 승격하지 않습니다.

## 도구 도입과 제거 판정

### Apache Kafka

| 판정 축 | 현재 결론 |
| --- | --- |
| 근본 문제·증거 | R1이 Kafka transport를 고정했고, 동기 호출은 broker 장애 격리와 backlog 복구라는 종료 상태를 만들지 못합니다. |
| 대안과 선택 또는 도입 경계 | 다른 broker는 사용자 기술 목표 변경이므로 같은 구현 비교군이 아닙니다. Apache Kafka 도입은 고정합니다. |
| 독립 oracle | Broker record만 보지 않고 PostgreSQL outbox·receipt·inbox 기대 집합을 대조합니다. |
| 오판·새 실패 표면 | Broker ack를 사용자 완료로 오판하거나 lag·disk·quota·network 장애를 숨길 수 있습니다. |
| 실행자·실패 경계 | Community relay가 publish하고 Notification consumer가 처리합니다. Kafka 실패는 댓글 commit을 rollback하지 않습니다. |
| Owner·갱신·제거 | Application messaging owner가 topic·client 호환 계약을, infrastructure operations owner가 broker lifecycle을 소유합니다. Kafka는 transport이며 제거 전 대체 경로와 미처리 event 처분을 확정합니다. |

### Strimzi Operator

| 판정 축 | 현재 결론 |
| --- | --- |
| 근본 문제·증거 | 현재 Kubernetes에서 broker·user·topic·certificate lifecycle을 재현 가능하게 소유해야 합니다. |
| 대안과 선택 또는 도입 경계 | Raw StatefulSet은 lifecycle을 재구현하고 managed Kafka는 현재 범위를 바꿉니다. Strimzi를 single-node qualification controller로 선택합니다. |
| 독립 oracle | 공식 Kubernetes/JVM·KRaft 지원 범위와 실제 rolling·파괴 복구 결과를 대조하며 CR Ready만으로 합격시키지 않습니다. |
| 오판·새 실패 표면 | Operator·CRD·RBAC·certificate·reconcile·version hop 실패가 추가됩니다. |
| 실행자·실패 경계 | 운영자가 GitOps로 관리하며 application은 생성된 client 자격만 소비합니다. Operator 장애와 사용자 API readiness를 분리합니다. |
| Owner·갱신·제거 | Platform bootstrap owner가 operator·CRD·cluster RBAC를, application GitOps owner가 namespace CR·exact AppProject allowlist를 소유합니다. 공식 version hop을 따르고 Kafka CR·topic·user 처분 뒤 operator·CRD를 제거합니다. |

### Spring for Apache Kafka

| 판정 축 | 현재 결론 |
| --- | --- |
| 근본 문제·증거 | Spring Boot 안에서 producer/listener lifecycle, offset과 error handling을 일관되게 제공해야 합니다. |
| 대안과 선택 또는 도입 경계 | Raw `kafka-clients`는 container·commit·retry를 직접 구현하게 합니다. Boot 호환 Spring for Apache Kafka를 선택합니다. |
| 독립 oracle | Testcontainers 실제 broker에서 serialization·publish·consume·retry·commit 결과를 검증합니다. Mock 성공은 oracle이 아닙니다. |
| 오판·새 실패 표면 | Ack·retry 설정이 중복·유실을 숨기거나 consumer thread·connection을 고갈시킬 수 있습니다. |
| 실행자·실패 경계 | Community producer adapter와 Notification listener만 호출하며 domain·wire owner를 framework에 넘기지 않습니다. |
| Owner·갱신·제거 | Application build·messaging owner가 Spring Boot compatibility에 맞춰 갱신합니다. Listener flag-off rollback 뒤 Kafka 경로 제거 시 dependency·config도 제거합니다. |

### Testcontainers Kafka

| 판정 축 | 현재 결론 |
| --- | --- |
| 근본 문제·증거 | Mock으로는 실제 Kafka protocol·serialization·broker restart 계약을 증명할 수 없습니다. |
| 대안과 선택 또는 도입 경계 | Shared broker는 격리·재현성이 약하고 GenericContainer는 listener wiring을 재구현합니다. Testcontainers Kafka module을 선택합니다. |
| 독립 oracle | Test process가 만든 공식 Kafka image broker에서 실제 DB 결과까지 대조합니다. Container start만으로 합격시키지 않습니다. |
| 오판·새 실패 표면 | Docker availability, ARM64 image compatibility와 test flake가 생깁니다. |
| 실행자·실패 경계 | Messaging integration test에서만 실행하고 application production runtime에는 포함하지 않습니다. |
| Owner·갱신·제거 | Messaging integration-test owner가 기존 Testcontainers BOM과 module·image를 함께 갱신합니다. 제거하려면 대체 real-broker oracle이 먼저 필요합니다. |

### 후속 부하 생성 도구

| 판정 축 | 현재 결론 |
| --- | --- |
| 근본 문제·증거 | 닫힌 arrival-rate·percentile·오류율·종료 code를 같은 workload로 반복할 수 있어야 합니다. |
| 대안과 선택 또는 도입 경계 | D8-B의 k6·Gatling 비교는 후속 참고입니다. 이번 도입은 없으며 독립 시험 플랫폼 작업에서 도구·version·image를 선택합니다. |
| 독립 oracle | 고정 seed·resource identity와 API·DB·Kafka·inbox 결과를 교차 대조합니다. |
| 오판·새 실패 표면 | Generator 포화, network 차이와 짧은 evidence retention을 Kafka 한계로 오판할 수 있습니다. |
| 실행자·실패 경계 | Qualification actor가 target Kubernetes node 밖에서 실행하며 generator 실패를 system-under-test 실패와 분리합니다. |
| Owner·갱신·제거 | 후속 작업에서 정할 qualification evidence owner가 선택 도구의 script·version/image·결과 보존과 시험 runner 제거를 소유합니다. 전용 관측 stack은 D8-A evidence가 실제로 부족할 때만 별도 심사합니다. |

Ephemeral broker storage는 D6-B, managed Kafka는 D6의 고정 실행 범위와 D9의 production 선행 입력, 전용 block storage는 D6-B,
Debezium·Kafka Connect는 D2와 D4, Schema Registry는 D3, 전용 관측 stack은 D8-A에서 각각
대안·재검토 조건과 제거되지 않은 미래 경계로 보존합니다.

- Managed Kafka는 현재 caller가 없습니다. 선택하면 application messaging owner가 endpoint·credential 전환과
  rollback을, infrastructure operations owner가 VPC·IAM·service lifecycle을 소유합니다.
  Rollback 뒤 provider backlog 종료를 확인한 다음 service·identity·network를 제거합니다.
- 전용 block storage도 현재 caller가 없습니다. 선택하면 infrastructure operations owner가 CSI·attach·filesystem·
  backup·용량과 orphan volume 처분을 소유합니다. Kafka 제거 전 data 보존·폐기 판정을
  끝내고 PV·volume을 제거합니다.
- Debezium·Kafka Connect는 현재 caller가 없습니다. Producer 증가나 polling 병목이 실측돼
  선택하면 data integration owner가 application relay와의 중복
  발행 기간, WAL·connector offset 인계와 relay 제거 순서를 소유합니다.
- Schema Registry는 현재 caller가 없습니다. 다조직·다언어 compatibility test가 부족해
  선택하면 integration-contract owner가 compatibility mode, client
  fallback, 기존 schema 보존과 subject 제거 순서를 소유합니다.
- 전용 관측 stack은 현재 caller가 없습니다. Cross-layer evidence가 실제로 부족해 선택하면
  observability owner가 collector·dashboard·alert routing·
  retention과 evidence export를 소유하고, 대체 evidence 보존 뒤 stack을 제거합니다.

## 목표 보존과 남은 위험

| 목표·위험 | 설계의 대응 | 남은 경계 |
| --- | --- | --- |
| Community write 보호 | Request transaction에는 content와 outbox만 저장 | PostgreSQL 자체 장애는 여전히 write 실패 |
| Event 유실 방지 | Transactional outbox를 downstream receipt 또는 승인된 terminal disposition 전 삭제 금지 | 잘못된 terminal 처분은 복구 원본을 없앰 |
| 사용자 중복 방지 | Event receipt와 기존 inbox unique constraint | 새 consumer가 idempotency 경계를 우회하면 실패 |
| 독립 알림 처리 | Post key는 locality만 제공하고 event ID로 각각 수렴 | Kafka 순서를 inbox 표시 순서로 오판하지 않아야 함 |
| 전체 상황의 확장성 | 환경별 topology와 증명 범위 분리 | Production SLO·비용·운영 책임은 아직 조건부 |
| 단일 node 비용 제한 | 새 persistent storage lifecycle 없이 broker를 폐기 가능한 transport buffer로 제한 | 4 GiB memory·20 GiB root disk와 단일 failure domain은 그대로 |
| 큰 부하 목표 | 후속 독립 qualification에서 capacity·HA 판정 | 이번 구축 완료로 미검증 성능을 주장하지 않으며 별도 환경·비용·workload 입력 필요 |
| 보안 | TLS·client-role별 ACL·NetworkPolicy·Secret lifecycle | Co-resident process 침해 시 role credential 권한이 합쳐짐 |
| 운영 증거 | 구간별 signal과 environment-specific oracle | 상시 collector 부재 시 single-node 관측 완료 불가 |

## 관련 문서와 공식 근거

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
- [Grafana k6 scenarios와 thresholds](https://grafana.com/docs/k6/latest/using-k6/scenarios/)
- [Debezium outbox pattern](https://debezium.io/documentation/reference/stable/transformations/outbox-event-router.html)

- **추가 비교 근거**: [Spring Modulith publication registry](https://docs.spring.io/spring-modulith/reference/events.html),
  [Spring Kafka retry topic](https://docs.spring.io/spring-kafka/reference/retrytopic.html),
  [Avro schema 명세](https://avro.apache.org/docs/1.12.0/specification/),
  [Gatling 시나리오 문서](https://docs.gatling.io/concepts/simulation/)
