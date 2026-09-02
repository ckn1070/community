# Community Kafka 이벤트 전달 상세 설계

## 상태

Proposed

## 계획 단계

해결 설계

## 현재 결론과 다음 행동

| 구분 | 현재 내용 |
| --- | --- |
| 현재 결론 | R1-A와 단일-node Kubernetes 범위를 보존한 D2~D9 해결 설계 후보를 Redis 계획과 같은 판단 구조로 정리했고 fresh 독립 검증을 통과했습니다. |
| 닫힌 결과 경계 | Community 댓글·답글 생성에서 서비스 안 알림까지의 첫 수직 흐름, Kafka 사용, 현재 Kubernetes의 단일-node qualification 범위는 다시 고르는 값이 아닙니다. |
| 선행 외부 gate | 현재 해결 설계의 사용자 판단을 막는 외부 gate는 없습니다. Production topology는 peak·SLO·RPO·RTO·비용·운영 책임이 확정되기 전에는 선택하지 않습니다. |
| 남은 gate | D2~D9의 기술 선택·목표 보존·source 관계는 최종 내용 검증을 통과했습니다. 사용자가 이 exact 해결 설계 후보를 확정해야 합니다. |
| 현재 행동 주체 | 사용자가 전체 후보를 확정하거나 바꿀 결정 ID와 값을 지정합니다. |
| 사용자 차례 | `현재 해결 설계 후보 전체를 그대로 확정해줘` 또는 바꿀 `D번호/R1: <변경 요구>`를 답합니다. |
| 현재 행동 뒤 결과 | 사용자 확정 뒤 실행 계획을 엽니다. 별도 명시 요청 전에는 구현·배포를 시작하지 않습니다. |

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
| 4. 핵심 효과 | 사용자 write 분리 | 현재 동기 경로보다 지속 처리량을 낮추지 않으면서 API latency를 broker 상태에서 분리합니다. | 비동기화 자체를 성공으로 간주하지 않고 같은 workload 결과를 대조합니다. |
| 5. 핵심 증거 | 전달·복구 판정 가능성 | Outbox·broker·receipt·inbox를 연결해 누락·중복·backlog와 병목을 구분합니다. | Lag·Pod Ready·count 0 하나만으로 전체 성공을 주장하는 안을 배제합니다. |
| 6. 최적화 | 유지보수성·확장성·비용 | 위 기준을 모두 통과한 안 중 현재 첫 수직 흐름에 불필요한 platform과 영구 운영 표면이 가장 적은 안을 고릅니다. | 미래 production HA를 미리 확정하지 않되 교체할 계약 경계를 남깁니다. |

선택은 점수를 임의로 합산하지 않습니다. 1~3번 필수 기준을 하나라도 위반한 안을 먼저
탈락시키고, 남은 안에서 4~5번의 효과와 증거가 더 직접적인 안을 고른 뒤, 효과가 같으면
6번의 구현·운영 비용이 작은 안을 선택합니다.

### 설계 논의 반영

| 역할 | 반영 내용 |
| --- | --- |
| 사용자 의견 요약 | 기존 Kafka 문서는 이미 결정된 입력과 지금 판단할 기술 선택을 한 화면에서 구별하지 못했고, Redis 문서와 구조적으로 유사하지 않아 전체 상황과 사용자 차례를 판단하기 어려웠습니다. |
| Agent 답변·판단 요약 | Kafka 적용 경계, 전달 원본, consumer 수렴, runtime·storage, 보안, qualification과 production gate는 서로 다른 결정입니다. 같은 시작·종료 상태의 대안끼리만 묶어야 합니다. |
| 최종 반영 | Redis 문서의 first-screen·비교 방법·고정 입력·목표 구조·결정·도구 lifecycle·위험·근거 순서를 그대로 사용합니다. R1은 고정 입력으로 격리하고 D2~D9에 동일한 평가축, 선택, 감수 비용과 재검토 조건을 둡니다. |

### 무엇이 같은 비교군이고 무엇이 아닌가

| ID | 하나의 결정 질문 | 같은 비교군인 이유 | 이 표에서 비교하지 않는 것 |
| --- | --- | --- | --- |
| R1 | Kafka 첫 적용에서 무엇이 이미 고정됐는가 | 비교군이 아니라 D2~D9가 공유하는 상위 입력입니다. | Redis·RabbitMQ·동기 호출과의 제품 재선택 |
| D2 | PostgreSQL과 Kafka 사이의 내구 전달 원본은 무엇인가 | 모두 같은 댓글 commit 뒤 event를 유실 없이 인계하는 경로입니다. | Event payload와 consumer 실패 수렴 |
| D3 | 어떤 event와 module 계약을 공개할 것인가 | 모두 같은 Community 사실을 consumer에 전달하되 의미 owner와 결합도가 다릅니다. | Topic·relay runtime |
| D4 | Request와 publish를 어떤 relay·topic 경계로 분리할 것인가 | 모두 같은 outbox를 Kafka에 전달하되 장애 분리·순서·운영 비용이 다릅니다. | Consumer DB transaction |
| D5-A | Consumer DB 결과와 offset을 어떻게 수렴할 것인가 | 모두 같은 record의 중복·중단에서 inbox 결과를 하나로 만듭니다. | 공개 시각·poison event 처분 |
| D5-B | 지연 event의 공개 시각과 cursor 의미를 어떻게 보존할 것인가 | 같은 inbox 결과를 공개하되 기존 API 의미와 정렬 호환성이 다릅니다. | Retry와 DLQ |
| D5-C | 처리 불가 event를 어떻게 retry·격리·replay할 것인가 | 모두 후속 record를 막지 않으면서 exact 미완료 결과를 복구하는 경로입니다. | 정상 record transaction |
| D6-A | 현재 Kubernetes에서 Kafka lifecycle을 누가 소유할 것인가 | 같은 broker 기능을 operator·직접 운영·managed 경로로 제공합니다. | Broker storage 내구성과 production 최종 선택 |
| D6-B | Broker data loss를 허용하고 어떻게 복구할 것인가 | 같은 단일-node qualification에서 storage와 복구 owner만 다릅니다. | 다중-node HA |
| D6-C | 각 환경은 무엇까지 증명할 수 있는가 | 동일 계약을 환경별 failure domain과 evidence 한계로 구분합니다. | Exact version·resource 수치 |
| D7-A | Kafka listener와 network 경계를 어디까지 열 것인가 | 같은 client 연결을 서로 다른 노출 면적으로 제공합니다. | Kafka ACL과 certificate lifecycle |
| D7-B | Producer·consumer·DLQ·replay 권한을 어떻게 나눌 것인가 | 같은 event 경로의 actor별 최소 권한을 비교합니다. | Pod 수준 credential 격리 |
| D7-C | Certificate·credential을 어떻게 교체하고 workload를 격리할 것인가 | 같은 역할 자격의 생명주기와 침해 반경을 다르게 만듭니다. | Network listener 공개 범위 |
| D8-A | 어떤 신호와 oracle로 전달 결과를 판정할 것인가 | 모두 같은 수직 흐름을 관측하되 원인 분리력과 운영 표면이 다릅니다. | 부하 생성기 선택 |
| D8-B | 같은 workload를 어떤 도구로 재현할 것인가 | 모두 외부에서 요청 부하를 만들되 재현성과 artifact 책임이 다릅니다. | 합격 수치 자체 |
| D8-C | 어떤 측정 경로와 환경별 주장으로 합격시킬 것인가 | 동일 workload의 결과를 구간·환경별로 제한해 해석합니다. | Production SLO 입력 결정 |
| D9-A | 기존 동기 경로를 어떤 순서로 전환할 것인가 | 같은 최종 비동기 경로를 사용자 중복과 rollback 가능성만 다르게 달성합니다. | 장애 후 exact replay |
| D9-B | 장애·rollback 때 어떤 원본과 상태를 보존할 것인가 | 같은 사용자 결과로 돌아오되 미처리 event 처분 방식이 다릅니다. | Production topology 선택 |
| D9-C | 언제 production topology를 선택할 수 있는가 | 필수 운영 입력과 qualification evidence의 충족 여부를 판정하는 gate입니다. | 현재 single-node manifest의 단순 확대 |

### 결정 의존 관계

```text
R1 Kafka 적용 경계 고정 입력
  -> D2 PostgreSQL/Kafka 전달 원본 -> D3 event·module 계약 -> D4 relay·topic·독립성
       -> D5-A receipt·inbox·offset -> D5-B 공개 시각·cursor -> D5-C retry·DLQ·replay
  -> D6-A runtime·controller -> D6-B storage·data-loss 복구 -> D6-C 환경별 증명
       -> D7-A listener·network -> D7-B client role·ACL -> D7-C credential·workload 격리
  -> D8-A 관측 oracle -> D8-B 부하 생성 -> D8-C 측정·합격 주장
  -> D9-A 전환 -> D9-B rollback·복구 -> D9-C production 승격 gate
```

### 최종 선택을 한눈에 보는 판정

| ID | 선택 | 이 선택으로 달성하는 것 | 선택 때문에 감수하는 것 | 결정적 근거 |
| --- | --- | --- | --- | --- |
| R1 | Community 댓글·답글에서 서비스 안 알림까지 Kafka 첫 수직 흐름 | 사용자 확정 범위를 바꾸지 않고 end-to-end 판단 가능 | 다른 도메인·채널은 이번 결론에 포함하지 않음 | Kafka 사용과 첫 결과 경계는 사용자 고정 입력 |
| D2 | 명시적 PostgreSQL transactional outbox | DB commit과 event 인계를 원자화하고 복구 원본 보존 | Schema·relay·정리 lifecycle 추가 | DB/Kafka dual-write 공백 제거 |
| D3 | Community 사실 event와 versioned JSON 계약 | Producer 사실과 Notification projection 분리 | Explicit mapping·version 관리 | 업무 owner와 persistence/wire 계약 분리 |
| D4 | Application relay, `postId` key, at-least-once | Request 장애 분리와 게시글 locality | 중복 publish를 정상 경로로 처리 | Outbox claim과 consumer 멱등성으로 수렴 가능 |
| D5-A | Receipt·inbox 한 transaction 뒤 offset 확정 | 중단·재전달에도 사용자 결과 하나로 수렴 | 처리 영수증 schema·운영 필요 | Kafka/PostgreSQL 분산 transaction 없이 유실 방지 |
| D5-B | 기존 `createdAt`과 `created_at,id` cursor 유지 | 지연 event에도 공개 API 호환성 보존 | 발생 시각 순 정렬은 제공하지 않음 | 기존 공개 의미를 바꿀 제품 요구가 없음 |
| D5-C | 제한 retry, DLQ 격리, exact replay | Poison event가 후속 처리를 막지 않음 | DLQ·승인 replay lifecycle 추가 | 실패 event도 추적 가능한 미완료 상태로 보존 |
| D6-A | Strimzi KRaft 단일 dual-role node | 현재 GitOps/Kubernetes에서 broker lifecycle 검증 | Operator·CRD·upgrade 책임 | 현재 닫힌 self-managed qualification 범위 |
| D6-B | Ephemeral broker storage와 outbox 재발행 | 새 storage platform 없이 파괴 복구 검증 | Broker log·offset 소실 허용 | Kafka는 transport, PostgreSQL이 내구 원본 |
| D6-C | Local/CI·single-node·multi-node·production 증명 분리 | 작은 환경의 성공을 HA·capacity로 과장하지 않음 | 환경별 별도 evidence 비용 | Failure domain이 다르면 증명 범위도 다름 |
| D7-A | 내부 TLS listener와 deny-by-default NetworkPolicy | Public·anonymous 접근 차단 | Network·certificate 운영 추가 | 현재 cluster 내부 client만 필요 |
| D7-B | Client role별 principal·ACL | Producer·consumer·DLQ·replay 최소 권한 | ACL·identity 관리 증가 | Actor별 실패·침해 반경을 구분해야 함 |
| D7-C | Kubernetes Secret lifecycle, 현재 co-resident 위험 명시 | Git·image·log secret 노출 방지 | 같은 Pod에서는 권한 합집합 위험 잔존 | 현재 artifact를 보존하되 격리 수준을 과장하지 않음 |
| D8-A | Cross-layer signal과 DB 결과 oracle | 멈춘 구간·누락·중복·병목 구분 | Evidence 수집·정렬 책임 | Broker health만으로 사용자 결과를 판정할 수 없음 |
| D8-B | 외부 재현 가능 부하 생성 능력, k6 우선 비교 | Arrival-rate·threshold·종료 code 재현 | Runner·version·artifact 관리 | Ad-hoc 요청으로는 공정한 반복 불가 |
| D8-C | 구간별 측정과 환경별 합격 주장 | API·relay·Kafka·consumer의 병목과 증명 한계 분리 | 여러 oracle 대조 비용 | Single-node 결과에 최대 RPS·HA를 붙이지 않음 |
| D9-A | Additive schema와 shadow 뒤 비동기 전환 | 사용자 중복을 관찰하며 가역적으로 전환 | 병행 기간의 비용·복잡도 | Big-bang보다 결과 비교와 rollback 가능 |
| D9-B | Flag-off, outbox·receipt 보존, exact 미완료 replay | 장애 뒤 누락 없이 이전 경로로 복귀 | 보존·처분 owner 필요 | 완료 증거 없는 삭제·무차별 replay 방지 |
| D9-C | Peak·SLO·RPO·RTO·비용·운영 책임 뒤 별도 선택 | 근거 없는 production topology 확정 방지 | 현재 production 결론 보류 | 필수 제품·운영 입력이 아직 없음 |

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

Notification은 event ID별 처리 영수증의 유일한 writer이고, 다른 module이 receipt table을
직접 조회하지 않도록 완료 여부를 batch로 반환하는 application port를 제공합니다. Community의
outbox cleanup worker는 이 port에서 완료 event ID를 읽거나, 원인·영향·재처리 불가 근거와
운영자 승인을 Community outbox lifecycle에 기록한 terminal disposition만 삭제 적격으로
인정합니다. Cleanup은 적격 표시 뒤 별도 보존 창이 지난 row만 bounded batch로 삭제하며,
조회 실패·불일치·Notification 미완료는 보존으로 수렴합니다. 이렇게 Notification의 완료
owner와 Community의 outbox 보존·정리 owner를 직접 table 결합 없이 연결합니다.

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

### D5-A. 처리 영수증·inbox transaction과 offset

**결정 질문:** 중복 전달·중단·처리 불가 event 뒤에도 inbox가 사용자에게 한 결과로
수렴하도록 DB transaction과 offset·격리 경계를 어떻게 나눠야 합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | 처리 영수증과 inbox를 한 DB transaction에 저장한 뒤 offset 확정 |
| 공통 비교 축 | 중단 지점별 중복·유실, 0건 정상 처리, poison event 격리, replay 안전성 |
| 영향·감수 비용 | Event ID와 기존 inbox unique constraint가 모두 필요하며 delivery는 at-least-once |
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

### D5-B. Inbox 공개 시각과 cursor 의미

**결정 질문:** 지연 도착한 event의 공개 시각과 cursor를 어떤 의미로 유지해야 기존 inbox
API와 새 비동기 전달을 함께 보존할 수 있습니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | `createdAt`은 inbox 생성 시각, 정렬·cursor는 `created_at,id`로 유지 |
| 공통 비교 축 | 공개 API 의미, 기존 cursor 호환, 지연 event 표시, migration 비용 |
| 영향·감수 비용 | Event 발생 시각 순 정렬은 제공하지 않고 `occurredAt`은 내부 evidence로만 보존 |
| 배제 대안 | 기존 `createdAt` 의미 변경, 새 발생 시각 필드와 cursor version을 현재 범위에 추가 |
| 재검토 조건 | 사용자가 원 활동 시각 기준 inbox 정렬·표시를 제품 요구로 확정 |

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

### D5-C. Retry, DLQ와 replay

**결정 질문:** 일시 실패와 처리 불가 event를 어떻게 구분해 후속 record를 막지 않으면서
exact 미완료 결과를 복구합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | 제한된 backoff retry 뒤 원본 identity를 보존한 DLQ 격리, 승인된 exact replay |
| 공통 비교 축 | 일시 실패 회복, poison event 격리, offset 안전성, replay 감사 가능성 |
| 영향·감수 비용 | DLQ topic·failure 분류·승인 replay와 terminal disposition 운영 책임 추가 |
| 배제 대안 | 무한 retry, DLQ 확인 전 offset 확정, 전체 기간 무차별 replay, 실패 record 폐기 |
| 재검토 조건 | Broker 기능이나 운영 표준이 동일한 격리·identity·감사 결과를 더 작은 표면으로 제공 |

| 공통 평가축 | 무한 retry | 제한 retry + DLQ + exact replay | 실패 뒤 offset 확정·폐기 |
| --- | --- | --- | --- |
| 후속 처리 | Poison event가 partition을 계속 막음 | 격리 뒤 독립 event 진행 | 진행되지만 실패 결과 유실 |
| 복구·감사 | 종료·대상 불명확 | Event ID·topic·partition·offset으로 exact 대상 보존 | 원본과 실패 이유 없음 |
| 판정 | 제외 | **선택** | 제외 |

일시적 broker·network·DB 오류는 제한된 backoff 뒤 같은 record를 다시 처리합니다. 지원하지
않는 계약이나 반복 처리 불가 payload는 원본 event ID, topic·partition·offset과 실패 분류를
보존하는 dead-letter topic으로 격리합니다. DLQ publish가 확인되기 전에는 원본 offset을 넘기지
않습니다. 격리는 해당 알림의 미완료 처분이며 다른 알림의 업무 순서를 막지 않습니다. Replay는
원인 수정, exact 대상 승인과 같은 idempotent consumer 경로를 사용합니다.

## D6. Runtime, storage와 환경별 증명 범위

### D6-A. Kafka runtime과 controller

**결정 질문:** 확정된 현재 Kubernetes의 단일-node Kafka lifecycle을 어떤 runtime과
controller가 소유해야 합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Strimzi가 관리하는 KRaft 단일 dual-role node |
| 공통 비교 축 | 현재 Kubernetes 적합성, 선언적 lifecycle, 새 운영 표면, 확장·제거 경계 |
| 영향·감수 비용 | Operator·CRD·RBAC·certificate·upgrade와 별도 bootstrap 책임 추가 |
| 배제 대안 | 직접 StatefulSet·script, 현재 단계의 managed Kafka |
| 재검토 조건 | Production 입력이 확정되거나 operator lifecycle 비용이 현재 범위의 순효과를 초과 |

| Runtime·controller 대안 | 장점 | 비용·새 실패 표면 | 판정 |
| --- | --- | --- | --- |
| Strimzi KRaft | 현재 GitOps·NetworkPolicy 안에서 operator·broker·user·topic lifecycle을 선언적으로 관리 | Operator·CRD·RBAC·certificate와 upgrade 책임 추가 | **선택**. 현재 self-managed Kubernetes qualification 범위를 직접 충족 |
| Managed Kafka | Broker·storage·quorum 운영을 provider에 위임 | 지속 비용, private network·IAM·quota·provider 장애·삭제 책임 | 현재 닫힌 범위가 아니며 production topology 결정 때 재검토 |
| 직접 StatefulSet·script | Operator dependency 없음 | Certificate·user·topic·rolling update를 project가 재구현 | 제외 |

현재 AppProject whitelist에는 `kafka.strimzi.io` CR과 `NetworkPolicy`가 없습니다. Cluster-scoped
CRD·operator·RBAC 설치는 platform bootstrap owner가 별도 운영 경계에서 수행하고, application
GitOps owner는 namespace 안의 `Kafka`, `KafkaNodePool`, `KafkaTopic`, `KafkaUser`와
NetworkPolicy에 필요한 kind만 exact allowlist로 추가합니다. Wildcard group/kind와
cluster-scoped resource 허용은 금지하며, 이 permission 확대가 검토·적용되기 전에는 Kafka
application manifest를 sync하지 않습니다. Operator bootstrap 실패와 application sync 실패는
각 owner의 evidence로 분리합니다.

### D6-B. Broker storage와 data-loss 복구

**결정 질문:** 현재 단일-node qualification에서 broker data를 보존할 것인지, 손실 시 어떤
원본과 actor로 사용자 결과를 복구할 것인지 결정합니다.

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Ephemeral broker storage와 PostgreSQL outbox 기반 exact 미완료 재발행 |
| 공통 비교 축 | 내구 원본, 새 storage lifecycle, 파괴 복구 가능성, 현재 자원·비용 |
| 영향·감수 비용 | Broker record·metadata·offset 소실을 허용하고 cluster·topic 재생성과 재발행 절차를 소유 |
| 배제 대안 | 현재부터 전용 persistent block storage, Kafka data 자체 복원을 사용자 결과 복구로 간주 |
| 재검토 조건 | Ephemeral 경로가 수용 기준을 실패하고 사용자가 CSI·backup·비용·처분 범위를 승인 |

| Broker storage 대안 | 장점 | 비용·새 실패 표면 | 판정 |
| --- | --- | --- | --- |
| Ephemeral broker storage | 새 storage platform 없이 현재 단일-node 파괴 복구 경로를 검증 | Application과 failure domain·root disk를 공유하며 broker data·offset을 잃을 수 있음 | **선택**. PostgreSQL outbox를 유일한 내구 원본으로 둔 qualification 전용 예외 |
| 전용 persistent block storage | Broker 재시작 뒤 log·offset을 보존 | CSI/PV·attach·backup·용량·orphan cost라는 별도 lifecycle 추가 | 현재 최소 경로가 수용 기준에 실패하고 사용자가 추가 범위를 승인할 때 재검토 |

Broker pod·storage·offset이 소실되면 Kafka data 복원을 주장하지 않습니다. 새 broker·topic을
만든 뒤 downstream receipt도 승인된 terminal disposition도 없는 PostgreSQL outbox만
재발행해 receipt·inbox로 수렴시킵니다. Ephemeral storage도 node disk를 사용하므로
retention·용량·DiskPressure 경계는 실행 계획에서 workload로 계산합니다. 이 최소 경로가
닫힌 부하·복구 기준을 충족하지 못하면 합격 실패로 기록하고, persistent storage나 별도
topology는 영향·비용·owner를 제시해 새 승인을 받습니다.

Qualification broker가 ephemeral storage의 유일한 caller이고 infrastructure operations
owner가 생성·파괴와 DiskPressure evidence를 소유합니다. Kafka 제거 때 함께 폐기하며,
수용 기준 실패 전에는 persistent storage를 현재 필수 범위로 승격하지 않습니다.

### D6-C. 환경별 증명 범위

**결정 질문:** Local/CI, 현재 single-node, 별도 failure-domain 환경과 production 후보가 각각
어떤 주장까지만 증명할 수 있습니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | 환경의 실제 failure domain별로 contract·기능 복구·capacity/HA·production 적합 주장을 분리 |
| 공통 비교 축 | 실제 topology, 장애 독립성, workload 재현성, 증거가 허용하는 주장 |
| 영향·감수 비용 | 같은 계약도 환경별 evidence를 별도로 만들며 single-node 결과에 최대 RPS·HA를 붙이지 않음 |
| 배제 대안 | Local test를 Kubernetes 증거로 확대, single-node 성공을 production 적합성으로 확대 |
| 재검토 조건 | 환경 topology·failure domain 또는 확정 SLO·RPO·RTO가 변경됨 |

| 공통 평가축 | 모든 환경에 하나의 합격 주장 | 환경별 증거 계층 분리 | 모든 검증을 production-like 환경에서만 수행 |
| --- | --- | --- | --- |
| 계약 feedback | 환경 차이를 숨기고 빠른 contract test의 역할도 불명확 | Local/CI에서 빠른 계약, 실제 환경에서 운영 경계를 단계적으로 검증 | 실제 환경 준비 전 feedback이 막힘 |
| 주장 정확성 | Single-node 성공을 HA·capacity로 확대할 위험 | 각 failure domain이 실제로 증명한 범위만 주장 | 실제성은 높지만 모든 변경에 과도한 비용·권한 필요 |
| 비용·가역성 | 작아 보이나 false pass 비용이 큼 | 필요한 evidence만 다음 환경으로 승격 | 외부 환경 유지·운영 표면이 현재 목표보다 큼 |
| 판정 | 제외 | **선택** | 현재 첫 수직 흐름의 최소 end-to-end 경로로는 제외 |

| 환경 | 고정된 최소 형태 | 증명할 수 있는 것 | 증명할 수 없는 것 |
| --- | --- | --- | --- |
| 로컬·CI | Testcontainers가 시작·제거하는 KRaft single broker | 실제 serialization, publish·consume, retry·DLQ·멱등성 계약 | Kubernetes 운영, HA, 장시간 capacity |
| 공유 기능 qualification | 현재 Kubernetes의 Strimzi KRaft 단일 dual-role node | 실제 network·identity·중단·복구와 수직 흐름 | HA, production SLO, 독립 failure domain |
| 부하·장애 qualification | Application과 broker가 분리된 최소 3 failure domain·다중 AZ | Replication, quorum, broker 장애, rebalance, 처리량, backlog 복구 | 장기 production 운영과 확정 SLO |
| Production 후보 | 확정 SLO·RPO·RTO·비용·운영 책임에 맞춘 dedicated Strimzi 또는 managed Kafka | 승인된 운영 목표 | 별도 region DR 등 계획 밖 보장 |

Exact Strimzi·Kafka·client version과 image digest는 ARM64, 현재 Kubernetes·Spring Boot
compatibility와 지원 기간을 공식 source로 확인해 실행 계획에서 고정합니다.

## D7. Network, 인증·인가와 secret

### D7-A. Listener와 network 경계

**결정 질문:** Kafka listener를 어디에 열고 어떤 network 경계의 workload만 접근하게
해야 합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Kubernetes 내부 TLS listener와 deny-by-default NetworkPolicy |
| 공통 비교 축 | 노출 면적, transport 인증·암호화, 허용 workload, 장애 진단 |
| 영향·감수 비용 | Certificate와 NetworkPolicy 운영이 추가되고 AppProject exact allowlist 확대가 선행 |
| 배제 대안 | Public·anonymous·PLAINTEXT listener, broker 연동형 API readiness |
| 재검토 조건 | 외부 managed endpoint나 cluster identity/network 표준이 별도 topology에서 채택됨 |

| Listener·network 대안 | Public·anonymous listener | 내부 TLS와 허용 workload만 연결 | 판정 |
| --- | --- | --- | --- |
| 노출 면적 | Internet·cluster 전체에서 접근 가능 | Kubernetes 내부에서 operator와 허용 producer·consumer만 접근 | **내부 TLS 선택** |
| 인증·암호화 | 익명 또는 평문 접근 위험 | TLS 인증과 암호화 | Public·anonymous 경로 제외 |
| 운영 비용 | 작지만 필수 보안 기준 실패 | NetworkPolicy·certificate 운영 추가 | 현재 cluster 내부 경로에 필요한 최소 경계 |

- Kafka listener는 public internet에 노출하지 않고 Kubernetes 내부에서만 엽니다.
- Broker 통신은 TLS로 암호화하고 client-role별 principal과 ACL을 사용합니다. 익명
  PLAINTEXT listener는 두지 않습니다.
- Calico NetworkPolicy는 operator와 허용한 producer·consumer의 DNS·broker traffic만 엽니다.
  현재 AppProject에 허용되지 않은 `NetworkPolicy`는 D6-A의 application GitOps exact
  allowlist 변경과 보안 검토가 끝난 뒤에만 적용합니다.

### D7-B. Client role과 Kafka ACL

**결정 질문:** Relay, consumer, DLQ와 replay actor에 어떤 Kafka principal·resource 권한을
부여해야 한 actor의 침해나 오설정이 전체 topic 권한으로 번지지 않습니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Client role별 principal과 topic·consumer-group 최소 ACL, shared superuser 금지 |
| 공통 비교 축 | Actor 식별, topic·group 최소 권한, audit 가능성, 침해 반경 |
| 영향·감수 비용 | Principal·ACL 발급·회전·폐기와 role별 연결 설정 관리가 늘어남 |
| 배제 대안 | 익명 접근, application 공용 credential, 평상시 identity로 운영 replay |
| 재검토 조건 | Workload identity 표준이 같은 actor 분리와 최소 권한을 대체 |

| 공통 평가축 | 익명·공유 credential | Role별 principal·ACL |
| --- | --- | --- |
| Actor 구분 | 불가 | Produce·consume·DLQ·replay audit 분리 |
| 침해 반경 | 모든 topic·group 권한 | 해당 role의 topic·group으로 제한 |
| 판정 | 제외 | **선택** |

- Relay producer는 원본 topic write, Notification consumer는 원본 topic read와 자기 consumer
  group, DLQ writer는 dead-letter write에 필요한 권한만 가집니다. Replay identity는 평상시
  application identity와 분리합니다.
- Client role은 별도 principal과 ACL로 구분하고 공유 superuser credential을 application에
  배포하지 않습니다.

### D7-C. Certificate·credential lifecycle과 workload 격리

**결정 질문:** Role credential을 어디에서 생성·교체·폐기하고, 현재 같은 Pod에 놓이는
producer·consumer의 실제 침해 반경을 어떻게 표현합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Strimzi/Kubernetes Secret lifecycle과 role별 credential, 현재 co-resident process 위험 명시 |
| 공통 비교 축 | Secret 비노출, rotation·rollout, Pod 침해 반경, 추가 workload 비용 |
| 영향·감수 비용 | 논리 ACL은 분리하지만 같은 process·Pod 침해 시 mount된 role 권한 합집합이 노출됨 |
| 배제 대안 | Git·image·event·log에 secret 저장, 현재 단계에서 보안 격리라고 과장 |
| 재검토 조건 | Role별 Secret mount 격리가 요구되거나 실측 위험이 별도 workload 비용을 정당화 |

| 공통 평가축 | 익명·공유 credential | Role별 principal, 같은 process에 co-resident | Role별 별도 workload·Secret mount |
| --- | --- | --- | --- |
| 권한 구분 | 불가 | Kafka ACL·audit에서 producer/consumer 구분 | ACL과 Pod credential 노출 모두 구분 |
| 침해 반경 | 전체 권한 | Process가 가진 role credential의 합집합 | 침해된 workload의 role로 제한 |
| 현재 자원·배치 | 단순하지만 보안 목표 실패 | 현재 단일 artifact·node에서 가능 | Pod·resource·mode·배포 lifecycle 추가 |
| 판정 | 제외 | **현재 선택**. 논리적 client-role 분리라고만 주장 | 격리 요구·실측 위험이 비용을 정당화할 때 분리 |

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

### D8-A. 관측과 결과 oracle

**결정 질문:** 닫힌 성능·복구 기준을 어떤 구간별 신호와 재현 가능한 시나리오로 판정해야
Kafka 외부 병목이나 작은 환경의 성공을 Kafka 확장성으로 오판하지 않습니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Request→outbox→broker→consumer→inbox 구간별 신호와 DB 결과 oracle의 교차 대조 |
| 공통 비교 축 | 재현성, 병목 귀속, 사용자 결과 정확성, 환경별 주장 한계, 도구 lifecycle |
| 영향·감수 비용 | API 지표만으로 합격시키지 않고 닫힌 threshold와 backlog·resource를 함께 판정하며 evidence 수집 책임이 늘어남 |
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

### D8-B. 부하 생성 도구

**결정 질문:** 같은 HTTP workload의 arrival-rate, percentile·오류 threshold와 종료 code를
어떤 도구로 재현하고 evidence artifact를 남깁니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Target node 밖에서 실행하는 k6 scenario와 threshold artifact |
| 공통 비교 축 | Arrival-rate 제어, percentile·오류 threshold, deterministic input, CI/외부 runner 재현성 |
| 영향·감수 비용 | 별도 k6 runtime·script·version/image와 result artifact lifecycle 추가 |
| 배제 대안 | Ad-hoc curl loop, target node 내부 실행, 현재 범위의 Gatling·JMeter harness |
| 재검토 조건 | k6가 필요한 protocol·workload·artifact를 재현하지 못하거나 저장소 표준 도구가 같은 결과를 더 적게 소유 |

| 공통 평가축 | Ad-hoc 요청 script | k6 | Gatling·JMeter 새 harness |
| --- | --- | --- | --- |
| Arrival-rate·threshold | 직접 구현·종료 판정 취약 | Scenario·threshold·exit code를 선언 | 가능하지만 JVM/GUI 중심 harness와 결과 관리 추가 |
| 재현 artifact | 입력·seed·summary 규약을 새로 소유 | Script·option·JSON summary로 고정 | 별도 project/plugin lifecycle 필요 |
| 판정 | 제외 | **선택** | 현재 HTTP 수직 흐름에는 추가 비용이 큼 |

부하 생성기는 target Kubernetes node 밖에서 실행해 application·Kafka와 CPU·network를
경쟁하지 않습니다. 현재 저장소에 표준 부하 도구가 없으므로 실행 계획은 arrival-rate,
percentile·error threshold, 고정 seed와 종료 code를 재현 가능한 k6 artifact로 구체화하고
공식 지원 범위에서 exact version·image를 고정합니다.

### D8-C. 측정 경로와 환경별 합격 주장

**결정 질문:** 어떤 구간의 신호와 환경별 oracle을 함께 만족해야 Kafka 도입 효과·병목·복구와
주장 한계를 판정할 수 있습니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | API→outbox→Kafka→consumer→inbox 측정과 환경별 합격 주장을 묶되 failure domain 밖으로 확대 금지 |
| 공통 비교 축 | 사용자 결과, 병목 귀속, 장시간 안정성, 장애·backlog 복구, topology 증명 한계 |
| 영향·감수 비용 | 동일 workload·resource identity와 여러 source의 시간 정렬·evidence 보존 필요 |
| 배제 대안 | API latency, Kafka lag, Pod Ready 또는 count 0 하나만으로 합격 |
| 재검토 조건 | 확정 workload·SLO·topology가 바뀌거나 현재 cross-layer evidence로 종료 판정이 불가능 |

| 공통 평가축 | API threshold 단독 | Kafka throughput·lag 단독 | Paired cross-layer 측정 + DB 결과 oracle |
| --- | --- | --- | --- |
| 도입 효과 귀속 | Request 변화만 보고 relay·consumer 병목 누락 | Broker 경로만 보고 API·DB·generator 병목 누락 | 동일 workload에서 API·relay·broker·consumer 자원을 함께 귀속 |
| 사용자 결과 | 알림 누락·중복·backlog 완료를 판정 못함 | Record·offset과 inbox 결과가 어긋날 수 있음 | Outbox·receipt·inbox 기대 집합으로 누락·중복·완료 판정 |
| 환경 주장 | 작은 환경 수치를 확대하기 쉬움 | Broker 수치를 production capacity로 오판 가능 | D6-C 환경의 failure domain 안에서만 합격 주장 |
| 판정 | 제외 | 제외 | **선택**. 비용은 늘지만 R1 결과와 성능 원인을 함께 증명 |

| 경로 | 측정할 결과 |
| --- | --- |
| 동기 요청 | 댓글·답글 RPS, API p50·p95·p99, error, DB lock·connection, outbox commit 비용 |
| Relay | Claim·publish rate, oldest outbox age, retry·duplicate publish, DB·producer resource |
| Kafka | Produce·fetch latency, partition throughput, disk·network, replication·controller 상태 |
| Consumer | Consume rate, lag, retry·DLQ, DB transaction, inbox 도착 지연, backlog 해소 시간 |

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

### D9-A. 전환 계약

**결정 질문:** 기존 동기 알림 경로를 어떤 호환 순서로 새 비동기 경로로 전환해야 누락·사용자
중복을 관찰하면서 되돌릴 수 있습니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Additive schema → shadow 수렴 → 비동기 전환 → 검증 뒤 동기 의존 제거 |
| 공통 비교 축 | 누락·중복 관찰, 이전 artifact 호환, 가역성, 병행 종료 조건 |
| 영향·감수 비용 | 동기·비동기 경로가 겹치는 기간의 write·관측 비용과 feature flag 운영 추가 |
| 배제 대안 | Big-bang 전환, 동기·비동기 무기한 병행 |
| 재검토 조건 | 기존 unique constraint로 shadow 결과를 안전하게 수렴할 수 없음이 source/test에서 확인됨 |

| 공통 평가축 | Big-bang 비동기 전환 | Shadow 수렴 뒤 단계 전환 | 동기·비동기 무기한 병행 |
| --- | --- | --- | --- |
| 누락·중복 관찰 | 전환 뒤에만 발견 | 같은 source 결과를 unique·receipt로 대조 | 중복 write 비용과 owner가 상시 남음 |
| Rollback | 이전 artifact·schema 호환에 의존 | Flag-off와 보존 outbox로 되돌림 | 전환 종료 조건이 없음 |
| 부하·복구 | 실제 traffic에서 즉시 위험 | Gate별 evidence 뒤 다음 단계 | 두 경로 비용이 capacity를 왜곡 |
| 판정 | 제외 | **선택** | 제외 |

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

### D9-B. Rollback과 복구

**결정 질문:** Application 결함, Kafka 중단 또는 broker data 손실 때 어떤 경로를 끄고 어떤
상태를 보존·재발행해야 exact 미완료 결과만 복구합니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | Relay·consumer flag-off와 동기 경로 복귀, outbox·receipt 보존, exact 미완료 재발행 |
| 공통 비교 축 | 사용자 write 지속, backlog owner, 이전 artifact 호환, 중복 없는 replay |
| 영향·감수 비용 | 보존 창·terminal disposition·복구 승인과 cluster/topic 재생성 절차 필요 |
| 배제 대안 | Kafka 장애 때 outbox write 중단, 미완료 전체 삭제, 기간 전체 무차별 replay |
| 재검토 조건 | Runtime topology나 event completion owner가 바뀌어 현재 exact 미완료 계산이 성립하지 않음 |

| 공통 평가축 | 상태 삭제 후 복귀 | 보존 + exact 미완료 replay | 전체 기간 replay |
| --- | --- | --- | --- |
| 누락 위험 | 미처리 event 상실 | Receipt·disposition 없는 event 보존 | 완료 event까지 재처리 |
| 중복·감사 | 복구 근거 없음 | Event ID와 승인 범위로 추적 | 불필요한 중복·대상 과다 |
| 판정 | 제외 | **선택** | 제외 |

- 새 비동기 경로의 application 결함이면 relay·consumer를 멈추고 동기 경로를 다시 켤 수
  있습니다. 이미 저장된 outbox·Kafka record·receipt는 삭제하지 않습니다.
- Kafka 장애 때는 outbox write를 끄지 않습니다. Broker가 돌아오면 pending event를 다시
  publish합니다.
- Broker data·offset 손실은 새 cluster·topic을 복원한 뒤 downstream receipt 또는 승인된
  terminal disposition이 없는 PostgreSQL outbox만 exact 미완료 범위로 계산해 재발행합니다.
  완료 조건 없는 기간 기준 삭제와 전체 기간 무차별 replay는 하지 않습니다.
- Schema rollback은 이전 application이 새 nullable table·column을 무시할 수 있어야 하며,
  destructive migration은 보존·replay window가 끝나기 전에 수행하지 않습니다.

### D9-C. Production 승격 조건

**결정 질문:** 어떤 제품·운영 입력과 실제 qualification evidence가 모두 있어야 production
Kafka topology를 비교·선택할 수 있습니까?

| 항목 | 내용 |
| --- | --- |
| Agent 선택 | 현재 production 선택 보류; 입력과 evidence가 닫힌 뒤 dedicated Strimzi와 managed Kafka를 별도 비교 |
| 공통 비교 축 | Peak·burst, SLO·RPO·RTO, 다중 failure domain, 비용, on-call·backup·upgrade 책임 |
| 영향·감수 비용 | 현재 계획에서 production topology·partition·retention·capacity 확정을 미룸 |
| 배제 대안 | Single-node manifest의 replica 수만 확대, 입력 없이 self-managed/managed를 미리 확정 |
| 재검토 조건 | 아래 필수 입력과 다중-node qualification evidence가 모두 확정됨 |

| 공통 평가축 | Dedicated Strimzi | Managed Kafka | 현재 single-node 확대 |
| --- | --- | --- | --- |
| 운영 책임 | Controller·broker·storage·DR를 project가 소유 | Provider와 project가 network·IAM·quota·비용 책임 분담 | HA·failure-domain 증거 없음 |
| 선택 가능 시점 | SLO·비용·on-call과 다중-node 증거 뒤 | 같은 입력과 provider 제약 검증 뒤 | Production 후보에서 제외 |
| 현재 판정 | 조건부 미래 후보 | 조건부 미래 후보 | 제외 |

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

### 부하 생성 도구

| 판정 축 | 현재 결론 |
| --- | --- |
| 근본 문제·증거 | 닫힌 arrival-rate·percentile·오류율·종료 code를 같은 workload로 반복할 수 있어야 합니다. |
| 대안과 선택 또는 도입 경계 | Ad-hoc 요청은 재현성과 종료 판정이 부족합니다. D8-B에서 외부 k6를 선택했고 실행 계획은 exact version·image만 고정합니다. |
| 독립 oracle | 고정 seed·resource identity와 API·DB·Kafka·inbox 결과를 교차 대조합니다. |
| 오판·새 실패 표면 | Generator 포화, network 차이와 짧은 evidence retention을 Kafka 한계로 오판할 수 있습니다. |
| 실행자·실패 경계 | Qualification actor가 target Kubernetes node 밖에서 실행하며 generator 실패를 system-under-test 실패와 분리합니다. |
| Owner·갱신·제거 | Qualification evidence owner가 k6 script·version/image·결과 보존을 소유하고 시험 runner와 artifact를 제거합니다. 전용 관측 stack은 D8-A evidence가 실제로 부족할 때만 별도 심사합니다. |

Ephemeral broker storage는 D6-B, managed Kafka는 D6-A와 D9-C, 전용 block storage는 D6-B,
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
| 큰 부하 목표 | 별도 다중 node qualification에서만 capacity·HA 판정 | 실제 환경 mutation·비용 권한과 workload 값 필요 |
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
