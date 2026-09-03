# Community Kafka 이벤트 전달 기반

## 상태

Proposed

## 계획 단계

실행 계획

## 현재 결론과 다음 행동

| 구분 | 현재 내용 |
| --- | --- |
| 현재 결론 | 기술·구조 선택을 반영해 해결 설계를 완료했습니다. 다음은 실행 계획 작성이며 아직 미착수입니다. 전체 계획 상태는 `Proposed`입니다. |
| 닫힌 결과 경계 | 이번에는 Strimzi 단일-node Kafka 구축과 Community 댓글·답글 → 서비스 안 알림 연결에 집중합니다. 업무 저장 보호, 누락·사용자 중복 방지와 재시작·데이터 손실 복구 검증을 포함합니다. |
| 선행 외부 gate | 별도 부하·장애 시험 플랫폼과 다중 failure-domain 검증은 후속 범위입니다. Production 판단에는 peak·SLO·RPO·RTO·비용·운영 책임이 필요하며 현재 실행 계획 작성의 선행 조건은 아닙니다. |
| 남은 gate | 실행 계획의 결과 단위·의존 순서·검증·전환·복구 책임 작성과 계획 검증·확정이 남았습니다. 해결 설계의 추가 사용자 선택은 없습니다. |
| 현재 행동 주체 | 다음 단계의 작성자는 Agent입니다. 확정된 설계를 입력으로 작업 본문에 실행 계획을 작성합니다. |
| 사용자 차례 | 추가 기술 선택 없이 실행 계획 단계로 진행할 수 있습니다. 실행 계획은 미작성 상태이며 구현·배포는 별도 후속 단계입니다. |
| 현재 행동 뒤 결과 | 실행 계획을 작성·검증한 후보를 확정하면 `Planned`로 전환합니다. 현재는 해결 설계까지 완료한 상태입니다. |

## 진행 지도

| 단계 | 상태 | 현재 작업에서의 결과·전환 조건 | 실행자와 즉시 다음 행동 |
| --- | --- | --- | --- |
| 범위·결정 정규화 | 완료 | Kafka 첫 적용을 Community 댓글·답글 생성에서 서비스 안 알림까지로 고정하고 account·catalog·media와 새 제품 기능은 제외했습니다. | 사용자 작업 없음 |
| 결과 정의 | 완료 | R1-A에 따라 Community write와 Kafka 장애를 분리하고 복구 뒤 알림이 누락·사용자 중복 없이 수렴하는 결과를 확정했습니다. | 사용자 작업 없음 |
| 해결 설계 | 완료 | 사용자 기술 선택과 Kafka 구축 우선 범위를 반영했습니다. D9-A는 Agent 기술 선택이며 부하·장애 플랫폼은 후속 범위입니다. | 추가 사용자 선택 없음 |
| 실행 계획 | 다음 | 미착수입니다. 확정 설계를 입력으로 결과 단위·의존 순서·test·문서·운영 책임과 완료 증거를 작성합니다. | 다음 단계 작성자는 Agent입니다. |
| 계획 검증·확정 | 후속 | 최초 목적·확정 결정·current source를 기준으로 완성 후보를 검증하고 사용자 채택 뒤 `Planned`로 전환합니다. | 실행 계획 후보와 필수 검증이 준비된 뒤 진행합니다. |
| 개발·결과 검증 | 조건부 후속 | 별도 개발 요청과 `Planned` baseline 뒤 repository 구현·자동 검증을 수행합니다. Kafka 전환 qualification은 `In Progress`인 Community 기반 배포와 `COMMUNITY_ENABLED=false` 상태의 선행 활성화 검증에 순서를 맞춥니다. | 이 계획 작업의 terminal이 아니며 이후 실행 요청과 Community 선행 결과가 필요합니다. |
| 영구 SOT·Git 마무리 | 조건부 후속 | 검증된 실제 event·module·운영 계약만 canonical 문서에 승격하고 승인된 Git·remote 효과만 수행합니다. | 구현·검증 결과가 생긴 뒤 해당 권한 범위에서 진행합니다. |

## 달성할 결과

- Community 댓글·답글 작성은 messaging 처리 속도·장애에서 분리됩니다.
- 댓글·답글 작성과 전달할 사실 사이에는 한쪽만 남는 유실이 없고, 복구 뒤 서비스 안 알림이
  사용자 중복 없이 생성됩니다.
- 첫 producer와 consumer는 실제 사용자 결과까지 이어지며, consumer 없는 event 복사는
  완료로 보지 않습니다.
- 이번에는 로컬·CI의 실제 Kafka 통합 테스트와 현재 단일-node 환경의 구축·전달·복구를 검증합니다.
- 부하·장애 시험 플랫폼과 다중 failure-domain qualification은 후속 독립 범위입니다.
  기존 수치 목표를 후속 검증 기준으로 보존하며 이번 구축 완료로 처리량·latency·HA 달성을
  주장하지 않습니다.
- Production topology와 수치는 peak·SLO·RPO·RTO·예산·운영 책임이 정해진 뒤 별도 승인합니다.

## 현재 문제

- Community 구현 후보는 저장소에 있지만 원 기능 work item은 아직 `In Progress`이고
  `COMMUNITY_ENABLED=false`가 기본입니다. 따라서 R1-A는 운영 중 기능을 즉시 전환하는 작업이
  아니라 Community 기반 배포·활성화 검증과 순서를 맞춰 적용할 후속 결과입니다.
- `CommunityService.createComment()`는 댓글과 알림 수신 후보를 계산한 뒤 같은 transaction에서
  `CommunityConversationNotification.store()`를 호출합니다. 알림 저장 실패가 댓글 작성까지
  rollback하므로 사용자의 핵심 write가 후속 projection의 장애와 결합돼 있습니다.
- Community와 Notification은 하나의 Spring Boot artifact와 PostgreSQL을 사용하고 현재 Kafka
  client·broker·outbox·처리 영수증은 없습니다. 따라서 broker를 추가하는 것만으로 DB 업무
  상태와 event 전달 사이의 유실·중복·재시작 경계가 생기지 않습니다.
- 같은 사건을 소비할 검색 색인·활동 통계 등이 늘면 생산자가 각 consumer를 동기로 호출하는
  구조는 장애 반경과 배포 결합을 계속 키웁니다.
- Staging은 ARM64 단일 Kubernetes node에서 control plane과 application을 함께 실행합니다.
  이 환경의 성공만으로 application과 broker가 분리된 큰 부하·고가용성을 증명할 수 없습니다.
- 현재 저장소에는 event 발행률·lag·backlog 복구와 broker 장애를 사용자 결과까지 대조하는
  반복 가능한 qualification artifact가 없습니다.

## 범위

- Community 댓글·답글 생성에서 서비스 안 알림까지 한 수직 흐름
- 업무 저장과 event 전달 사이의 유실·중복·재시작·처리 불가 event·복구 결과
- 변경 호환성, 책임 경계, 보안·관측·운영·rollback 결과
- 로컬·CI의 실제 Kafka 통합 테스트와 공유 단일-node의 기능·재시작·데이터 손실 복구 검증
- 구현 뒤 현재 구조·계약과 운영 절차의 영구 문서 인계

## 제외 범위

- 범용 부하·장애 시험 플랫폼, k6·Gatling 도입, scripted fault harness와 별도 다중 failure-domain 시험 환경 구축
- 장시간 부하·capacity·HA qualification의 실행과 합격 판정(후속 독립 범위)

- Community 외 account·auth·catalog·media event의 이번 구현 연결
- Kafka 도입을 이유로 modular monolith를 microservice로 분리하는 것
- 검색·추천·실시간 채팅·이메일·문자·push 같은 새 제품 기능
- 현재 단일 node 결과로 production 처리량·고가용성·data 내구성을 주장하는 것
- 이 계획 문서 작성만으로 external environment나 production cluster를 실제 생성하는 것
- 아직 workload·SLO·비용이 없는 상태에서 production broker·partition·retention 수치를 고정하는 것
- Kafka를 Community 업무 상태나 알림 inbox의 원본 저장소로 사용하는 것

## 품질 수용 기준

- Messaging 장애 중에도 선택한 업무 쓰기 결과가 보존되고 복구 뒤 누락 없이 consumer 결과로
  수렴합니다.
- 반복 전달과 어느 중단 지점에서도 사용자 결과가 중복되지 않습니다.
- 지원하지 않는 계약과 처리 불가 event를 정상 성공으로 오판하거나 다른 독립 처리를 무기한
  막지 않습니다.
- 변경·배포 뒤에도 이전 application과 이미 대기 중인 작업을 일방적으로 깨지 않습니다.
- Rollback과 전달 시스템 data 손실 복구 뒤에도 미처리 결과의 책임을 잃지 않습니다.

- 실제 Kafka protocol을 사용하는 통합 테스트에서 원자성, 중복 전달, retry·DLT·replay와
  broker·consumer 재시작을 검증합니다. 현재 ephemeral 구성은 broker 데이터·offset 소실 뒤
  미완료 outbox 재발행으로 누락 0·사용자 중복 0을 증명해야 합니다.
- 기존 API·Kafka·DB·node 신호로 전달 경로와 오류를 구분하고 bounded worker·resource 경계를
  확인합니다. 성능과 복구 시간의 수치 합격 판정은 아래 후속 범위에 둡니다.

- Kafka는 public internet에 노출하지 않고 허용한 producer·consumer만 인증된 암호화 통신을
  사용합니다.
- Secret과 불필요한 개인정보를 event·Git·image·metric·일반 log에 남기지 않습니다.
- 업무 책임은 상대의 내부 구현을 우회하지 않고 한 결과의 변경 주체를 하나로 유지합니다.
- 도입 도구마다 official source, 실제 caller·독립 oracle, 지원·upgrade·rollback·제거 조건과
  새 실패 표면을 계획합니다.

### 후속 부하·장애 qualification 기준

별도 시험 플랫폼을 구축하는 후속 작업의 검증 목표입니다. 이번 Kafka 구축의 완료 조건과
분리하며 값을 완화하거나 이미 달성한 것으로 처리하지 않습니다.

- 동기 API, event 전달, broker와 consumer를 분리 측정하고 PostgreSQL·node·network 병목을
  Kafka 성능으로 오판하지 않습니다.
- 정상·점증·급증·장시간 부하와 broker·consumer 장애·backlog 복구를 재현 가능한 입력과
  종료 판정으로 검증합니다.
- 단일 node 결과는 기능·resource 상한 증거로만 사용하고 큰 부하·HA 주장은 application과
  broker 장애를 분리한 별도 환경의 실제 failure test에만 연결합니다.
- Production 적합성은 확정 peak·SLO·RPO·RTO·비용과 운영 책임이 없으면 판정하지 않습니다.
- 같은 자원·workload에서 현재 동기 알림 경로보다 지속 가능한 대화 쓰기 처리량이 낮아지지
  않고 API p95가 10% 넘게 악화되지 않아야 합니다.
- 가장 먼저 포화되는 자원의 70% 이하를 단일-node 정상 운영점으로 정합니다. 이 운영점의
  60분 test에서 API 오류율은 0.1% 미만이고 알림 도착 p99는 5초 이내이며 event 유실과
  사용자에게 보이는 중복 알림이 없어야 합니다.
- 정상 운영점에서 broker를 10분 중단해도 댓글·답글 작성은 성공해야 합니다. 복구 뒤
  consumer는 새 event를 처리하면서 20분 안에 중단 중 backlog를 해소해야 합니다.

## 결과 결정

### 확정

- Kafka의 첫 적용 대상은 Community 댓글·답글 생성에서 서비스 안 알림까지 한 흐름입니다.
- 댓글·답글 저장은 Kafka 장애와 분리하고, 알림은 consumer가 복구 뒤 누락·사용자 중복 없이
  생성합니다.
- 현재 Kubernetes에는 Strimzi 기반 단일 Kafka node를 qualification 용도로 구축하는 범위를
  사용합니다.
- 이번에는 Kafka 구축과 첫 알림 흐름에 집중합니다. 부하·장애 시험 플랫폼은 후속 독립
  범위이며, 동기 대비 도입 효과·병목·수치 목표의 판정은 그 후속 검증에서 수행합니다.
- 현재 단일-node 환경은 기능·복구·resource 경계를 검증하는 출발점이며 production
  고가용성이나 큰 부하를 증명하지 않습니다.
- 큰 부하·HA 주장은 application과 broker의 failure domain을 분리한 별도 환경에서만 검증합니다.
- Production topology는 peak·SLO·RPO·RTO·예산·운영 책임을 입력으로 별도 선택합니다.
- 현재 계획의 확정은 repository 구현, external environment 생성이나 live 적용 권한이 아닙니다.

### R1. Kafka 적용 경계와 사용자 결과

**결정 질문:** 첫 Kafka 흐름에서 Community 댓글·답글 저장 성공을 broker·consumer 장애와
분리하고, 서비스 안 알림을 복구 뒤 생성하도록 바꿀 것입니까? 두 후보는 같은 알림 결과를
목표로 하지만 사용자의 댓글 write가 후속 전달 장애와 결합되는지가 다릅니다.

| 비교 축 | R1-A. 댓글 write와 알림 전달 분리 | R1-B. 현재 동기 transaction 유지 |
| --- | --- | --- |
| 댓글·답글 성공 의미 | PostgreSQL 업무 저장과 유실 없는 전달 인계가 함께 commit되면 성공합니다. | 알림 저장까지 같은 transaction에서 성공해야 합니다. |
| Kafka·consumer 장애 | 댓글·답글 성공을 rollback하지 않고 복구 뒤 알림으로 수렴합니다. | 현재는 Kafka가 없고 알림 저장 장애가 댓글·답글을 rollback합니다. |
| 알림 도착 | 늦을 수 있지만 처리 영수증과 inbox unique 계약으로 누락·사용자 중복을 막습니다. | 즉시 같은 transaction에서 저장되지만 후속 처리와 장애 경계가 결합됩니다. |
| 구현·운영 영향 | Outbox, relay, broker, consumer, retry·DLQ·replay와 관측 책임이 생깁니다. | 구조는 작지만 consumer 확장 때 생산자 결합과 장애 반경이 커집니다. |
| 목표 판정 | **업무 write를 보호하면서 실제 사용자 결과까지 비동기로 수렴합니다.** | 현재 단순성은 유지하지만 Kafka 도입의 장애 분리·완충 목표를 달성하지 못합니다. |

**선택: R1-A.** 사용자가 첫 Kafka 적용을 Community 댓글·답글에서 서비스 안 알림까지로
확정했습니다. 댓글·답글은 PostgreSQL commit으로 성공하고 알림은 지연될 수 있지만, 복구 뒤
누락·사용자 중복 없이 생성되는 결과를 선택했습니다. 내구 전달·broker·consumer 운영 비용을
감수하는 대신 후속 projection 장애가 사용자의 핵심 write를 rollback하지 않게 합니다.
사용자가 첫 적용 결과를 직접 바꾸거나 Notification의 제품 의미가 달라지면 R1을 다시 엽니다.

## 해결 설계 결정 색인

- 닫힌 R1의 기술 projection과 전달 원본, event·module 계약, relay·topic, consumer 멱등·실패,
  runtime·storage·환경별 증명, 보안, 관측·qualification, 전환·rollback·production 승격은
  [010 Community Kafka 이벤트 전달 상세 설계](010-community-kafka-event-delivery-design.md)의
  R1과 D2~D9가 소유합니다.
- D2~D9의 비교표는 기술 선택과 그 근거를 소유합니다. 기존 사용자 동의가 있는 범위는 해당
  선택·계약에서 확정으로 반영했습니다. D9-A는 Agent 기술 선택이며 D8-B·D8-C는 후속
  미선택입니다. 필드명·기존 구조 제약은 개별 선택 카드가 아닙니다. 해결 설계는 실행 계획의
  입력으로 사용할 수 있으며 구현은 검증된 `Planned` baseline과 별도 실행 요청 뒤에 진행합니다.
- Managed Kafka, persistent block storage, Debezium·Kafka Connect, Schema Registry와 전용
  관측 stack은 현재 선택이 아닙니다. 각 재검토 조건은 관련 D section이 소유합니다.

## 관련 문서

- **제품 전체 범위**: [100 제품 개요](../../../product/100-product-overview.md), [200 기능 범위](../../../product/200-feature-scope.md)
- **현재 Community 결과**: [018 일반 커뮤니티](../../features/2026/018-general-community.md)
- **현재 동기 알림 결정**: [018 일반 커뮤니티 해결 설계](../../features/2026/018-general-community-design.md#d9-서비스-안-알림의-원자성과-중복-방지)
- **현재 application 구조**: [200 현재 프로젝트 아키텍처](../../../project/200-current-architecture.md), [210 모듈 지도](../../../project/210-module-map.md)
- **현재 infrastructure**: [100 Infrastructure overview](../../../project/infrastructure/100-infrastructure-overview.md), [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md), [150 Kubernetes cluster](../../../project/infrastructure/150-kubernetes-cluster.md)
- **현재 확장 후보**: [004 Infrastructure 진화 Backlog](../../backlog/2026/004-infrastructure-evolution.md)
- **계획 단계와 상태**: [100 작업 문서 생명주기](../../100-work-item-lifecycle.md#계획-단계와-최소-artifact)
