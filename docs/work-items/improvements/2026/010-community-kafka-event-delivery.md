# Community Kafka 이벤트 전달 기반

## 상태

In Progress

## 결과

| 구분 | 현재 내용 |
| --- | --- |
| 현재 결론 | U1~U5 repository 구현과 실제 PostgreSQL·Kafka를 포함한 전체 로컬 품질 gate를 완료했습니다. 실제 cluster 설치·인증 검증(U6), 비동기 전환·rollback qualification(U7), exact SHA의 CI·Sonar 확인(U8)은 아직 실행하지 않았습니다. |
| 닫힌 결과 경계 | Strimzi 단일-node Kafka와 Community 댓글·답글 → 서비스 안 알림 연결, 누락·사용자 중복 방지, 재시작·데이터 손실 복구를 포함합니다. 별도 부하·장애 시험 플랫폼은 후속 범위입니다. |
| 선행 외부 gate | 실제 cluster 자원·환경 권한은 U6 적용부터, Community 기반 활성화 증거는 U7 전환부터 필요합니다. 이 입력 없이도 U1~U5의 repository 작업을 준비할 수 있습니다. |
| 남은 gate | U6·U7은 target cluster와 live 적용 권한, U8 원격 gate는 push된 exact SHA가 필요합니다. 로컬 결과로 대신 완료 처리하지 않습니다. |
| 현재 행동 주체 | 다음 실행자는 platform 운영자입니다. Kafka runbook의 사전 확인부터 U6 live 설치·인증 시험을 수행합니다. |
| 사용자 차례 | 기술 선택은 닫혔습니다. Live U6·U7 실행을 시작하려면 target cluster 변경 권한을 명시해야 합니다. |
| 현재 행동 뒤 결과 | U6 통과 뒤 capability-off 상태에서 U7의 shadow·async 전환과 rollback을 검증하고, push된 exact SHA로 U8 CI·Sonar를 확인합니다. |

## 진행 지도

| 단계 | 상태 | 현재 작업에서의 결과·전환 조건 | 실행자와 즉시 다음 행동 |
| --- | --- | --- | --- |
| 범위·결정 정규화 | 완료 | Kafka 첫 적용을 Community 댓글·답글 생성에서 서비스 안 알림까지로 고정하고 account·catalog·media와 새 제품 기능은 제외했습니다. | 사용자 작업 없음 |
| 결과 정의 | 완료 | R1-A에 따라 Community write와 Kafka 장애를 분리하고 복구 뒤 알림이 누락·사용자 중복 없이 수렴하는 결과를 확정했습니다. | 사용자 작업 없음 |
| 해결 설계 | 완료 | 사용자 기술 선택과 Kafka 구축 우선 범위를 반영했습니다. D9-A는 Agent 기술 선택이며 부하·장애 플랫폼은 후속 범위입니다. | 추가 사용자 선택 없음 |
| 실행 계획 | 완료 | U1~U8에 결과·의존·검증·전환·복구와 문서 책임을 연결했습니다. | 확정된 순서와 합류점을 구현 입력으로 사용합니다. |
| 계획 검증·확정 | 완료 | 실행 계획의 source·구조·내용 검증과 사용자 확정을 마쳤습니다. 검증된 `Planned` 계획이 구현 기준선입니다. | 추가 사용자 선택·재확정 없음 |
| 개발·결과 검증 | 부분 완료 | U1~U5 repository 결과가 실제 PostgreSQL·Kafka 통합 시험과 전체 `check`를 통과했습니다. U6·U7 live 검증과 U8 원격 품질 gate는 미실행입니다. | 독립 재검증을 닫은 뒤 U6 사전 확인에서 재개합니다. |
| 영구 SOT·Git 마무리 | 진행 중 | U1~U5 계약을 SOT·runbook에 동기화했습니다. 독립 재검증 뒤 의미 단위 commit을 만들며 push는 수행하지 않습니다. | Live 결과는 U6·U7 실행 뒤 같은 문서에 추가합니다. |

## 구현·검증할 결과

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

## 구현 결과와 남은 gate

| 실행 단위 | 현재 결과 | 검증·다음 gate |
| --- | --- | --- |
| U1 | 공개 V1 event와 Community outbox를 댓글 transaction에 원자 저장 | 실제 PostgreSQL에서 rollback·재요청·동시 동일 요청·wire fixture 검증 완료 |
| U2 | 별도 relay와 Notification consumer가 receipt·inbox를 같은 transaction에 저장 | 실제 Kafka에서 0·1·2명, 자기 알림 제외, async-only 결과 검증 완료 |
| U3 | `SKIP LOCKED` claim·30초 lease·fencing, 제한 retry와 DLT ack 경계 구현 | 중복 발행, retry 소진, DLT 실패, DB commit 뒤 offset 실패를 실제 Kafka·PostgreSQL로 검증 완료 |
| U4 | 완료 조회, terminal 근거, 7일 보존·100건 cleanup, dry-run/승인 replay 구현 | broker 중단·topic/group 소실 뒤 미완료만 복구하고 누락·사용자 중복 0 검증 완료 |
| U5 | Strimzi 1.2.0 설치본, Kafka 4.2.1 단일-node CR·topic·role ACL·mTLS 설정 준비 | 세 Kustomize 경로 render와 자동 staging 미포함 계약 검증 완료 |
| U6 | 미실행 | Target Kubernetes API의 CRD·자원·ARM64·mTLS·ACL·NetworkPolicy를 live 검증해야 함 |
| U7 | 미실행 | Community capability-off 선행 조건 뒤 shadow·async 전환·rollback을 live 검증해야 함 |
| U8 | 부분 완료 | 로컬 SOT·runbook과 품질 검증을 마친 뒤, push된 exact SHA의 required CI·Sonar가 남음 |

현재 일반 Deployment의 Kafka 기능 flag는 기본값이 꺼져 있고 동기 알림 fallback은 켜져 있습니다.
`deploy/kafka`는 U6 전까지 `deploy/overlays/stg`에 포함되지 않으므로 이 repository 결과만으로
cluster나 application 전달 경로가 활성화되지 않습니다.

## 현재 문제

- Community 구현 후보는 저장소에 있지만 원 기능 work item은 아직 `In Progress`이고
  `COMMUNITY_ENABLED=false`가 기본입니다. 따라서 R1-A는 운영 중 기능을 즉시 전환하는 작업이
  아니라 Community 기반 배포·활성화 검증과 순서를 맞춰 적용할 후속 결과입니다.
- `CommunityService.createComment()`는 댓글과 알림 수신 후보를 계산한 뒤 같은 transaction에서
  `CommunityConversationNotification.store()`를 호출합니다. 알림 저장 실패가 댓글 작성까지
  rollback하므로 사용자의 핵심 write가 후속 projection의 장애와 결합돼 있습니다.
- 구현 전에는 Community와 Notification이 하나의 Spring Boot artifact와 PostgreSQL만 사용해
  Kafka client·broker·outbox·처리 영수증이 없었습니다. 현재 후보는 이 gap을 outbox·receipt와
  실제 Kafka relay·consumer로 보완하며, U6·U7 live 적용 전까지 운영 경로는 활성화되지 않습니다.
- 같은 사건을 소비할 검색 색인·활동 통계 등이 늘면 생산자가 각 consumer를 동기로 호출하는
  구조는 장애 반경과 배포 결합을 계속 키웁니다.
- Staging은 ARM64 단일 Kubernetes node에서 control plane과 application을 함께 실행합니다.
  이 환경의 성공만으로 application과 broker가 분리된 큰 부하·고가용성을 증명할 수 없습니다.
- 현재 저장소에는 local broker 중단·data loss 통합 시험과 U6/U7 qualification template·runbook이
  있습니다. Live 결과와 exact SHA 원격 gate는 아직 없습니다.

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

## 실행 입력과 적용 전 확인

아래 값은 확정 설계를 구현하기 위한 **Agent 기술 설정값**입니다. 제품 성능 보장이나 추가
사용자 선택지가 아닙니다. 실제 source에서 달라진 입력을 발견하면 영향받는 단위부터 다시
확인하며, 미확인 image·자원·권한을 성공으로 가정하고 배포하지 않습니다.

| 입력 | 계획값·source | 확인할 단위 |
| --- | --- | --- |
| Application | 현재 `build.gradle`의 Java 21·Spring Boot 4.0.6 유지. `spring-boot-starter-kafka`를 version 없이 추가하며 Boot BOM의 Spring Kafka 4.0.5·`kafka-clients` 4.1.2를 사용 | U2에서 `dependencyInsight`와 실제 송수신 확인. Kafka 때문에 Boot 전체를 upgrade하지 않음 |
| 통합 테스트 | 기존 Testcontainers BOM 2.0.5에 `org.testcontainers:testcontainers-kafka` 추가. `org.testcontainers.kafka.KafkaContainer`와 `apache/kafka:4.2.1` 사용 | U2 시작 전 registry manifest의 ARM64·CI AMD64 및 digest 확인 후 test support에 고정. Docker 부재는 skip 성공이 아닌 실패 |
| Kubernetes broker | Strimzi 1.2.0·Kafka 4.2.1·`kafka.strimzi.io/v1`. 현재 Kubernetes 1.36 계열과 공식 지원표가 겹침 | U5에서 공식 release artifact 고정, U6에서 실제 cluster version·ARM64 실행 확인 |
| Operator image | `quay.io/strimzi/operator@sha256:77f8fa8121a67561c3418de985783d197f51b8931e9a47f793dc0437dc6bb21f` | U5에서 manifest pin, U6에서 실제 architecture·image identity 대조 |
| Broker image | `quay.io/strimzi/kafka@sha256:47ea0bd5cd416a92cbcfa248ae4b4b01a1e5d6b87aeafa67a691abdc29e20d0d` | Kafka 4.2.1용 release digest. 실제 broker와 operator가 생성하는 component image를 함께 확인 |
| Topic·group | 원본 `community.conversation-created.v1`, DLT `community.conversation-created.v1.dlt`, group `notification.community-conversation.v1` | U2·U5가 동일 설정 계약을 소비. 원본·DLT 각 3 partition, RF 1·min ISR 1은 시험 환경 한정 |
| Namespace·listener | 기존 application namespace 안의 internal TLS listener, Strimzi CA와 `KafkaUser`의 mTLS 사용 | U5·U6. 외부 listener·익명 접속·topic 자동 생성 금지 |
| Migration | 기존 V22~V25는 보존하고 outbox·receipt를 추가 migration으로 생성 | U1·U2 구현 시 최신 migration 목록에서 다음 번호 확인. 기존 checksum 변경 금지 |

Spring Boot의 starter 선택은 [Spring Kafka 공식 안내](https://docs.spring.io/spring-kafka/reference/quick-tour.html),
Testcontainers class·module은 [공식 Kafka module](https://java.testcontainers.org/modules/kafka/)을 따릅니다.
Strimzi 조합은 [지원표](https://strimzi.io/downloads/)와
[1.2.0 release의 image 목록](https://github.com/strimzi/strimzi-kafka-operator/releases/tag/1.2.0)을
기준으로 골랐습니다. BOM 숫자는 현재 4.0.6 artifact에서 확인한 값이며 최신 4.0 문서의 숫자로
대체하지 않습니다. 지원표 일치는 실제 통합·ARM64 기동 검증을 대신하지 않습니다.

| 제한할 대상 | 초기 설정과 이유 | 실패·변경 경계 |
| --- | --- | --- |
| Wire payload | JSON 16 KiB 이하. 게시글·댓글 본문을 싣지 않는 D3 계약에 충분한 상한 | 상한 초과·알 수 없는 type/version은 명시 오류. Type header에 따른 임의 class 역직렬화 금지 |
| Relay | Worker 1개, 한 번에 claim 1개, in-flight 1개, idle polling 500 ms. Lease 30초, publish 완료 대기 10초, `max.block.ms` 1초 | Network 대기 중 DB transaction·connection을 보유하지 않음. Lease token이 다른 update는 거부. `acks=all`과 producer idempotence 사용 |
| Consumer | Concurrency 1, `max.poll.records=10`, auto commit 해제. 최초 처리 뒤 일시 오류만 1·2·4초 간격으로 최대 3회 추가 시도 | 전체 처리·retry·DLT 대기가 `max.poll.interval.ms` 안에 들어오는지 검증. 상한 초과·영구 오류는 DLT로 격리 |
| Outbox 정리 | 완료 receipt 또는 승인된 terminal 처분으로 삭제 적격이 된 시점부터 7일 보존, 한 batch 최대 100개 | 미완료·조회 실패는 기간과 무관하게 보존. Rollback·복구 중 cleanup 중지. Receipt는 replay 가능 범위를 닫기 전 자동 삭제하지 않음 |
| Broker buffer | 원본·DLT 모두 24시간, partition별 `retention.bytes=64 MiB`, segment 16 MiB. Ephemeral volume 상한 2 GiB | 6개 partition의 명목 data 예산은 384 MiB이며 segment·index·내부 topic·metadata를 별도로 계산. Retention은 즉시 삭제되는 hard disk quota가 아님 |
| Node 자원 | Broker·operator·entity operator의 heap·request·limit·ephemeral-storage를 U5 manifest에 명시. Application의 동시 rollout Pod와 migration Job까지 합산 | U6 적용 전 allocatable·현재 사용량·eviction 여유와 맞춰 최종값 고정. Source의 4 GiB memory·20 GiB root disk만으로 배치 가능하다고 판단하지 않음 |

메모리·disk 여유 부족이 관측되면 운영자가 relay를 중지하고 outbox를 보존해 전달 원본을 보호합니다. Topic
만료·broker 손실 뒤에도 미완료 outbox는 복구 원본으로 남습니다. 자원 부족을 원본 삭제나
무단 node 증설로 해결하지 않으며, 현재 범위로 합격할 수 없는 경우 자원·storage 변경의
영향을 별도 판단합니다. 이 제한값의 검증은 작은 고정 corpus와 경계 테스트로 수행하며
부하 시험 플랫폼을 다시 이번 범위로 들이지 않습니다.

Relay·consumer·cleanup은 각각 기본 비활성, 기존 동기 알림은 기본 활성으로 시작합니다.
Kafka 설정·Secret이 없는 상태에서도 비활성 application은 기동해야 하고, 활성 role의
누락·잘못된 설정만 명시 실패시킵니다. Readiness는 현재처럼 DB에 의존하며 Kafka·worker
장애와 pending outbox는 별도 진단 신호로 확인합니다.

## 작업 순서와 합류점

번호는 결과를 식별하며 모든 작업을 일렬로 실행하라는 뜻이 아닙니다. U1 안에서는 공유
wire 계약과 원자 저장의 대표 결과를 먼저 검증한 뒤 consumer 작업으로 확장합니다.

```text
Application: U1 원자 저장 → U2 실제 Kafka 알림 ┬→ U3 장애·중복 수렴 ─┐
                                             └→ U4 보존·재발행 ───┤
Infrastructure: U5 배포 산출물 → U6 Kafka·인증 연결 ────────────────┤
Community 기존 배포·활성화 증거 + live 적용 권한 ──────────────────┤
                                                               ↓
                                                     U7 전환·복구 검증
                                                               ↓
                                                     U8 결과·운영 인계
```

| 단위 | 필수 입력 → 처음 소비하는 행동 | 독립성·합류 |
| --- | --- | --- |
| U1 | 확정 D2·D3 → 공유 계약과 업무 transaction 변경 | 최초 application 단위 |
| U2 | U1의 검증된 wire·outbox → relay와 Notification consumer 연결 | 실제 Kafka의 첫 사용자 결과를 대표 검증 |
| U3 | U2의 정상 publish·receipt·inbox → 중단·오류 경계 확장 | U4와 착수 의존은 없음. 같은 adapter 수정은 직렬 조정 |
| U4 | U2의 event receipt → 공개 완료 조회·기본 보존·cleanup | U3 뒤 구현을 권장하나 필수 선행은 아님. Crash·DLT·재발행 최종 검증에서 U3와 합류 |
| U5 | 위 runtime·topic·보안 입력 → 설치 및 GitOps manifest 작성 | U1~U4의 application 코드와 독립적으로 준비 가능 |
| U6 | U5의 검증된 manifest·runbook → 실제 cluster 설치·인증 시험 | 환경 권한·실제 자원 gate가 이 단위부터 필요 |
| U7 | U3·U4의 복구 증거 + U6 endpoint·identity + Community 활성화 증거 → 실제 비동기 전환 | 각 트랙의 최초 실제 환경 합류점 |
| U8 | U7 수용 증거 + 최종 code 품질 결과 → SOT·운영·완료 상태 정합화 | 미완료 live 검증을 local test 통과로 대체하지 않음 |

## 실행 단위

### U1. 댓글과 전달할 이벤트를 함께 저장

**목표·근거:** D2·D3의 원자 인계를 구현합니다. 댓글 성공과 이벤트 인계가 같은 DB commit에
남아야 하며, 동일 작성 요청을 재시도해도 새 outbox가 늘지 않아야 합니다.

- 개발자가 `shared::integration`에 `CommunityConversationCreatedV1`과 named interface를
  만들고 D3 wire literal·version·공개 ID·nullable 관계를 contract fixture로 고정합니다.
  Community·Notification의 Modulith 허용 의존을 이 공개 경계에만 추가합니다.
- Community 소유 outbox table과 저장 port/adapter를 추가하고 `CommunityService.createComment()`의
  댓글 transaction에서 기록합니다. 기존 생성 idempotency가 반환하는 경로는 재기록하지 않습니다.
  이 단위에서는 기존 동기 알림을 유지합니다.
- 기존 `CommunityNotificationTransactionIntegrationTest`의 동기 mode rollback 검증을 유지하고,
  outbox 저장 실패 시 댓글 rollback, 댓글 실패 시 outbox 부재, 재요청·동시 동일 요청의
  event identity 유일성을 먼저 실패 테스트로 작성한 뒤 구현합니다.
- **검증:** 예정 `CommunityOutboxTransactionIntegrationTest`와 wire contract test,
  기존 Community transaction·schema test, `ApplicationModuleVerificationTest`를 실행합니다.
  DB row를 직접 대조해 댓글과 outbox가 함께 0건 또는 함께 1건인지 확인합니다.
- **문서·완료:** event·module 계약의 변경 위치를 해당 SOT에 연결합니다. 다른 module의
  내부 table을 직접 쓰지 않는 원자 저장과 공개 계약이 검증되면 U2가 이를 소비할 수 있습니다.
- **복귀:** 외부 발행이 아직 없으므로 기존 동기 동작으로 유지합니다. 적용된 추가 table을
  지우거나 이미 실행한 migration을 수정하는 rollback은 하지 않습니다.

### U2. 실제 Kafka를 거쳐 첫 알림을 생성

**목표·근거:** U1 결과를 D4·D5·D8-D/E의 relay·consumer에 연결해 댓글 하나가 실제 broker를
거쳐 Notification의 receipt와 inbox로 수렴하는 대표 경로를 만듭니다.

- 개발자가 Spring Kafka starter와 Testcontainers module을 추가하고 공식 broker image의
  digest·platform을 고정합니다. JSON은 adapter가 명시된 V1 type으로 읽고 domain·JPA 객체를
  wire로 직렬화하지 않습니다.
- Community relay는 outbox를 읽어 broker ack를 받은 뒤 발행 상태를 갱신합니다. Notification
  listener는 공개 관계 ID로 최종 수신자를 계산하고 **receipt와 inbox를 같은 DB transaction**에
  저장합니다. Transaction 성공이 반환된 뒤 offset을 확정합니다. 수신자 0명도 receipt를 남깁니다.
- 기존 동기·새 비동기 경로는 Notification의 동일 수신자 규칙을 소비합니다. Shadow 기간의
  정책 복사본을 두지 않으며 기존 inbox unique와 `created_at, id` 조회 계약을 유지합니다.
- **검증:** 예정 `CommunityKafkaDeliveryIntegrationTest`에서 동기 회귀, 제한된 shadow,
  **동기 저장을 끈 async-only** corpus를 분리합니다. 새 댓글·답글의 기대 recipient를 fixture에서
  독립 계산해 receipt와 정확한 inbox 집합을 비교합니다. 0·1·2명 수신, 자기 알림 제외와
  겹친 수신자 제거를 포함합니다. Sync가 만든 row·전체 count만으로 async 성공을 판정하지 않습니다.
- **문서·완료:** local 실행과 test 설정을 개발 문서·test stack에 연결합니다. 실제 publish·consume과
  async-only 알림 결과가 확인돼야 U3·U4로 확장합니다. 이 단계만으로 live 활성화하지 않습니다.
- **복귀:** relay·consumer를 비활성화하고 동기 경로를 유지합니다. 이미 기록된 outbox·receipt를
  보존하고 dependency 제거는 아직 소비하는 코드·설정이 없는 경우에만 합니다.

### U3. 중단·중복·처리 불가 이벤트를 수렴

**목표·근거:** U2 대표 경로의 각 중단 지점에 D4-G·D5의 at-least-once·멱등·실패 격리를 적용합니다.

- 개발자가 PostgreSQL의 원자 claim·lease 회수와 소유 token 조건부 갱신을 구현합니다.
  두 relay가 경쟁해도 유효 owner가 하나이며, 만료된 owner의 늦은 ack가 새 owner 상태를
  덮지 못해야 합니다. Kafka 대기와 DB transaction을 분리하고 worker·connection·queue 상한을 검증합니다.
- Notification의 동일 event 동시 처리에서 receipt unique와 inbox 저장이 같은 transaction으로
  묶이도록 합니다. DB commit 전 실패는 offset을 남기지 않고, commit 뒤 재전달은 중복을 만들지 않습니다.
- Allowlist에 든 일시 오류만 제한 재시도합니다. 지원하지 않는 version·역직렬화 실패·영구 오류는
  DLT로 격리하고, **DLT ack 후에만 원본 offset을 전진**시킵니다. DLT 저장도 실패하면
  원본을 완료 처리하지 않습니다. Poison event 뒤의 독립 정상 event가 처리되는지 확인합니다.
- **검증:** 예정 `CommunityKafkaFailureIntegrationTest`에서 ack 뒤 DB 갱신 전 중단,
  consumer commit 전·후 중단, 동시 중복, lease 만료·오래된 owner, retry 소진, DLT 장애,
  poison 다음 record 처리를 실제 Kafka·PostgreSQL로 검증합니다. 고정 지연 sleep에 기대지 않고
  fault 주입 지점과 bounded await로 각 중단을 재현합니다.
- **문서·완료:** 실패 분류·관측 신호·offset 의미를 SOT·Kafka 운영 owner에 연결합니다.
  업무 write는 broker 장애 중 성공하고 누락·사용자 중복은 0이어야 합니다.
- **복귀:** 소비·발행을 각각 멈추고 outbox·receipt·DLT를 보존합니다. 실패 record를 삭제하거나
  offset을 임의 rewind하는 것을 복구 수단으로 삼지 않습니다.

### U4. 원본 보존·재발행·정리를 연결

**목표·근거:** D2-B·D5-D·D6-B가 요구하는 복구 원본과 유한 cleanup을 만듭니다. 기본 구현의
선행은 U2 receipt입니다. U3의 crash·DLT 증거는 최종 복구 검증에서 필요합니다.

- Notification이 event ID 묶음의 완료 결과를 공개 port로 제공합니다. Community는 그 결과나
  승인된 terminal 처분으로만 삭제 적격을 표시하고, 7일 보존 창 뒤 최대 100건씩 정리합니다.
  조회 실패·누락·불일치·미완료이면 보존하며 상대 module receipt table에 직접 접근하지 않습니다.
- 운영 재발행은 동일 application artifact의 **일회 실행 operation**으로 준비합니다. 대상 event ID,
  이유·범위·예상 건수를 dry-run으로 확인한 뒤 운영자 승인 범위만 처리합니다. Web 관리 API나
  범용 장애 시험 도구를 새로 만들지 않습니다.
- DLT 재처리는 원본 event ID를 유지해 정상 topic으로 보내고, broker data·offset 소실은
  **이미 publish된 것까지 포함해 완료 receipt·terminal 처분이 없는 outbox**를 대상으로 합니다.
  둘 다 기존 멱등 consumer를 통과합니다. 재발행의 broker ack와 downstream 완료를 구분합니다.
- Terminal 처분은 Community가 원인·영향·재처리 불가 근거·운영자 승인을 기록한 경우에만
  가능합니다. 일시 DB 오류나 receipt 조회 실패를 terminal로 자동 변환하지 않습니다.
- **검증:** 예정 `CommunityOutboxLifecycleIntegrationTest`에서 제어 가능한 clock으로 보존
  경계·batch 상한·조회 장애를 검증합니다. U3 결과와 합류해 DLT 재발행, broker 교체 후
  미완료 집합 복구, 재실행·중간 중단을 검증하고 유실·사용자 중복 0을 대조합니다.
- **문서·완료:** Kafka lifecycle runbook에 actor·권한·dry-run·승인·중단·재개·결과 oracle을
  완성합니다. 원본을 무조건 삭제하지 않으면서 완료 event만 정리할 수 있어야 합니다.
- **복귀:** cleanup을 먼저 끄고 재발행을 중지합니다. Receipt와 원본을 보존하며 새로운
  terminal 처분 없이 중단된 operation의 같은 대상에서 재개합니다.

### U5. Kubernetes 배포 산출물을 준비

**목표·근거:** D6·D7의 단일-node Kafka와 권한 경계를 선언형 산출물로 준비합니다.
U1~U4의 코드 완료를 기다릴 필요는 없습니다.

- Infrastructure 담당자가 공식 고정 release의 CRD·RBAC·Cluster Operator 설치 파일을
  `scripts/bootstrap/kafka/manifests/`에 준비합니다. 기존 `kubectl`·Kustomize와 공식 manifest를
  사용하며 새 installer framework·Helm·Connect·관측 stack을 도입하지 않습니다.
- Kafka·KafkaNodePool·KafkaTopic·KafkaUser·NetworkPolicy desired state는 별도 Kustomize 구성으로
  준비하고 U6의 CRD·operator·allowlist 준비 전에는 자동 sync되는 staging overlay에 포함하지 않습니다. 기존 AppProject에는 이 작업에 필요한 namespace kind만 추가하고
  `clusterResourceWhitelist: []`는 유지합니다. Cluster-scoped 설치와 권한은 platform이 소유합니다.
- Topic/User Operator가 생성하는 Secret·Service·Pod 등은 operator가 관리합니다. Secret 값이나
  생성 resource 사본을 GitOps에 넣지 않습니다. CRD 설치 전 CR을 자동 sync하는 순서 오류를 막습니다.
- Kafka 전용 정책은 broker·operator·허용 workload에 필요한 흐름만 허용합니다. 기존 API의
  RDS·DNS·외부 연동을 namespace 전체 deny 정책으로 끊지 않도록 selector·egress를 대조합니다.
- **검증:** local `kubectl kustomize deploy/overlays/stg`와 설치용 경로 render에서 source·namespace·
  RBAC·image·Secret reference·resource 상한을 확인합니다. 예정 `KafkaDeploymentManifestContractTest`는
  public listener·wildcard ACL·cluster 권한의 우회 같은 실제 금지 계약을 검사합니다.
  Server dry-run은 CRD가 설치된 승인 환경에서 U6가 수행합니다.
- **문서·완료:** Kafka 전용 lifecycle runbook에 최초 설치·생성 owner·검증·취소 절차를 준비합니다.
  공용 Application bootstrap과 환경변수 router는 전용 owner로 연결하는 진입점만 유지합니다.
- **복귀:** 아직 적용 전이면 해당 산출물만 수정합니다. 이미 설치된 CRD를 먼저 지우는 취소는
  금지하며 U6의 resource·사용자 data 처분 순서를 따릅니다.

### U6. 실제 Kafka와 인증을 연결

**목표·근거:** U5 산출물을 승인된 qualification 환경에 적용하고 D6·D7의 실제 실행 증거를 만듭니다.
이 단위부터 환경 mutation 권한이 필요하며, 없는 동안 local 구현·검증은 계속할 수 있습니다.

- 운영자가 target cluster·namespace·현재 revision을 확인하고, Kubernetes version·ARM64 image·
  실제 여유 자원을 대조합니다. Broker·operator·entity operator뿐 아니라 기존 platform,
  application rollout의 동시 Pod·migration Job과 eviction 여유까지 맞춰 resource 값을 고정합니다.
  부족하면 설치를 중단하고 범위·비용 변경 판단으로 반환합니다.
- CRD·operator 설치와 readiness → AppProject allowlist 적용 → 준비한 Kafka Kustomize 구성을
  staging overlay에 연결·전달 → Kafka/NodePool·Topic/User·NetworkPolicy 생성·검증 순서로 진행합니다. Application의 relay·consumer는 아직 비활성입니다.
- Strimzi CA·User Operator로 relay, Notification consumer, DLT writer, replay의 별도 principal을
  생성합니다. 각 topic·group에 필요한 최소 ACL만 부여하고 application은 읽기 전용 Secret
  mount를 소비합니다. Replay 자격은 평상시 application에 mount하지 않습니다.
- 자격 교체는 새 principal·Secret·ACL 생성 → mount 전환·application rollout → 새 자격으로
  실제 허용 작업 검증 → 이전 principal의 ACL·User 폐기 순서입니다. 폐기 전에는 이전 자격으로
  복귀할 수 있어야 하며, 폐기 뒤에는 이전 자격의 접근 거부까지 확인합니다. CA 갱신·만료와
  application trust reload도 같은 Kafka owner의 운영 절차에 포함합니다.
- **검증:** 승인된 환경에서 server dry-run, CR status·operator reconciliation·기동 image를 확인한 뒤
  각 role의 허용 publish/read와 잘못된 topic/group·무자격·비허용 Pod의 거부를 실제 client로 확인합니다.
  TLS 연결 성공만으로 ACL·NetworkPolicy 검증을 통과시키지 않습니다.
- **문서·완료:** endpoint·identity·인증서 trust·role별 접근·교체·자원 증거가 준비되면 U7이
  소비합니다. Secret 실제값과 live inventory는 문서에 복제하지 않습니다.
- **복귀:** application의 Kafka role을 끄고 rollout 상태를 복구합니다. 사용자 data·outbox의
  복구 책임을 먼저 확인한 뒤 Kafka CR, 전용 identity와 마지막으로 operator를 처분합니다.
  다른 cluster가 소비하는 공용 CRD·RBAC는 삭제하지 않습니다.

### U7. 비동기로 전환하고 되돌릴 수 있음을 검증

**목표·근거:** U3·U4·U6 결과를 D9의 shadow·flag 전환에 합류시킵니다. 기존 Community 기반
배포·활성화 증거가 필요하며 현재 `COMMUNITY_ENABLED=false`를 임의로 바꾸지 않습니다.

- 개발자·운영자가 동일 release의 additive migration, 새/이전 application 호환성, feature flag와
  Secret 준비를 대조합니다. Application delivery·Community 활성화는 기존 전용 runbook을 소비합니다.
- 고정 corpus에서 **동기 회귀 → 제한된 shadow → 별도의 async-only 결과**를 구분해 확인합니다.
  Event ID마다 예상 수신자와 실제 receipt·inbox를 비교하고 자기 알림 제외·0명 처리·읽음 상태·
  cursor 조회를 확인합니다. Async-only 증명 전 primary를 전환하지 않습니다.
- 승격은 동기 알림을 끄고 relay·consumer로 처리하도록 합니다. Broker 중단 중에도 댓글·답글은
  outbox와 함께 commit돼야 하고, 복구 후 pending event가 누락·사용자 중복 없이 처리돼야 합니다.
- 승인된 시험 대상에서 broker process 재시작과 Pod·ephemeral storage·offset 손실을 구분해
  검증합니다. 원본 DB는 유지하고 cleanup을 중지한 뒤 broker·topic을 재생성하고 U4의 미완료
  집합을 재발행합니다. 마지막에는 정상 신규 event도 처리되는지 확인합니다.
- **검증·완료:** 새/기존 알림의 정확한 집합, DB commit·offset 경계, API write 보호,
  재시작·data-loss 복구, flag-off rollback을 모두 통과해야 합니다. 같은 corpus의 수신자
  기대 집합이 독립 oracle이며 Pod Ready·lag 0만으로 완료하지 않습니다.
- **문서·복귀:** Kafka runbook의 검증된 절차와 기존 배포 runbook을 연결합니다. 문제 시
  cleanup 중지 → 비동기 consumer·relay 중지/진행 중 처리 종료 확인 → 동기 경로 복귀 순서입니다.
  Outbox는 계속 저장하고 기존 미완료 event의 후속 복구 책임을 유지합니다. 동기 복귀가 이미
  쌓인 비동기 backlog까지 처리했다고 보고하지 않습니다.

### U8. 최종 결과와 운영 책임을 인계

**목표·근거:** 기능·복구 증거와 실제 코드·설정·영구 문서를 맞추고 이번 구축 범위를 종료합니다.

- 개발자가 아래 품질 검증을 최종 변경에 수행하고, 운영자는 U6·U7의 실제 환경 증거와
  Kafka lifecycle runbook의 시작·중단·재개·제거 절차를 대조합니다. 각 단위에서 갱신한 SOT를
  마지막에 한 번 맞추며 모든 문서 작성을 이 단위까지 미루지 않습니다.
- 승인된 Git 전달 뒤에는 최종 SHA의 required CI와 Sonar revision·Quality Gate·Open issues를
  확인합니다. 현재 CI는 `main` push·PR에서 실행되므로 feature branch push만으로 검증 완료를
  주장하지 않습니다. 계획이나 구현 요청으로 push·PR·image 게시 권한을 만들지 않습니다.
- **완료:** U1~U7의 이번 수용 기준·회귀·보안·환경 증거와 영구 문서가 모두 닫혀야 합니다.
  환경 권한이나 실제 검증이 남으면 local 구현 완료와 전체 미완료를 구분합니다.
- **복귀·조건부 제거:** 동기 코드·schema·복구 원본의 제거는 이번 기본 완료 조건에 포함하지
  않습니다. 최소 보존 창이 끝나고 전환·복구 증거가 유지되는 경우에만 별도 제거를 검토합니다.
  장시간 부하·HA·범용 장애 시험 플랫폼은 후속 범위로 남습니다.

## 검증과 완료 판정

아래 명령은 **구현 후 수행할 계획**입니다. 현재 문서 작성 중 실행한 테스트 결과가 아닙니다.
새 test class 이름은 예정 이름이며 실제 구현 이름과 함께 명령을 갱신합니다.

| 검증 묶음 | 실행 명령·진입점 | 통과 기준 |
| --- | --- | --- |
| 공유 계약·원자 저장 | `./gradlew test --tests '*CommunityOutboxTransactionIntegrationTest' --tests '*ApplicationModuleVerificationTest'` | rollback·재요청·module 공개 경계가 U1 oracle과 일치 |
| 실제 전달·실패·보존 | `./gradlew test --tests '*CommunityKafkaDeliveryIntegrationTest' --tests '*CommunityKafkaFailureIntegrationTest' --tests '*CommunityOutboxLifecycleIntegrationTest'` | 실제 Kafka·PostgreSQL에서 async-only·중복·crash·DLT·재발행·정리 경계 통과 |
| 의존 version | `./gradlew dependencyInsight --dependency spring-kafka --configuration runtimeClasspath` 및 `kafka-clients` 조회 | Boot BOM과 실제 해석 version이 일치, 수동 override 없음 |
| 전체 application 회귀 | `./gradlew spotlessCheck check --no-daemon --rerun-tasks --console=plain` | 기존 Community 동기 mode·cursor·Notification·보안·schema·모듈 test와 새 test 통과 |
| 배포 source | `kubectl kustomize deploy/overlays/stg`, 고정 operator 설치 파일 render, 기존 GitOps bootstrap 계약 test | 설치 owner·allowlist·비활성 기본값·필요 network flow·Secret 비노출 보존 |
| 실제 환경 | U6의 role별 허용/거부, U7의 전환·중단·재시작·data-loss·rollback 절차 | 예상 event/recipient 집합의 누락 0·사용자 중복 0, 중단 중 댓글 저장 보존 |
| 최종 revision | 현재 CI와 Sonar 검증 workflow | 정확한 최종 SHA의 required CI 성공, Sonar revision 일치·Quality Gate·Open issues gate 통과 |

Outbox write 보호는 U1·U3·U7, 실제 알림은 U2·U7, 보존·복구는 U3·U4·U7,
보안과 자원 경계는 U3·U5·U6, 배포 호환성·rollback은 U1·U5·U7, 운영·문서 인계는
각 단위와 U8이 증명합니다. 후속 수치 부하 기준을 이 표의 합격 항목으로 끌어오지 않습니다.

## 전환과 복구

| 상태 | 동기 알림 | Relay·consumer | Cleanup·복구 책임 |
| --- | --- | --- | --- |
| 준비 | 유지 | 비활성 | 추가 schema·outbox를 보존, Kafka 없이 application 기동 가능 |
| 제한된 shadow | 유지 | 지정 검증 범위에서 활성 | Cleanup 중지. Inbox unique가 중복을 막으며 별도 async-only corpus가 필요 |
| 비동기 primary | 비활성 | 활성 | 완료 증거와 보존 창을 충족한 row만 정리. Pending age·DLT·disk 신호 확인 |
| Application rollback | 비활성화 완료 확인 뒤 복귀 | 먼저 멈추고 진행 중 작업을 정리 | Cleanup 중지, outbox 계속 기록, 이미 쌓인 backlog는 U4로 복구 |
| Broker data-loss | 승인된 mode 유지 | 새 broker 확인 전 중지 | 원본 DB 보존, 완료 증거 없는 event를 재발행하고 정상 신규 처리 확인 |

Schema는 추가 방식으로 배포하며 과거 version의 실행 가능성을 먼저 확인합니다. 이전 artifact가
outbox 기록을 지원하지 않는다면 일반 flag rollback 대상이 될 수 없습니다. 원본 기록을 유지하는
동기 mode를 우선 사용하고, 전달 원본을 빠뜨리는 artifact rollback은 수행하지 않습니다.

## 영구 문서 반영

| 구현 결과 | 기존 owner와 반영 내용 | 반영 시점 |
| --- | --- | --- |
| Module·event·transaction | `docs/project/200-current-architecture.md`, `210-module-map.md`에 실제 공개 경계·Community 원본·Notification receipt 책임 반영 | U1~U4 구현과 함께 |
| Runtime·test | `docs/tech-stack/100-current-stack.md`, `300-testing-stack.md`, `.env.example`, `docs/development/100-local-development.md`에 dependency·비활성 기본값·실제 Kafka 테스트·Secret 주입 계약 | U2~U3 |
| Kafka·GitOps | 기존 infrastructure overview·Kubernetes·Argo CD 설명에 component와 권한·설치 관계 반영. Generic architecture 규칙을 Kafka 전용으로 바꾸지 않음 | U5~U6 |
| Kafka 전용 lifecycle | 예정 `docs/operations/380-kafka-lifecycle-runbook.md` 한 owner에 설치·자격 교체·관측·replay·data-loss·제거 절차 작성 | U4~U6에서 준비하고 U7 증거로 검증 |
| 공용 운영 진입점 | 기존 `350` Application bootstrap, `420` 배포·rollback, `540` 환경변수 router, `560` Community 활성화를 재사용하고 Kafka 전용 owner 링크만 필요한 곳에 추가 | U5~U7 |
| 최종 상태 | 본 work item과 연도 router를 실제 완료 증거에 맞춰 갱신 | U8 |

Kafka lifecycle은 기존 `350`의 최초 application 연결이나 `540`의 source 발견만으로 수행할 수
없습니다. 전용 owner 하나는 인증·재발행·데이터 손실 복구의 실행 책임을 남기는 데 필요합니다.
현재 비어 있는 `380` 후보는 구현 시 direct directory·번호·inbound link를 다시 확인해 배치합니다.
이번 계획 작성에서는 그 runbook이나 별도 backlog·시험 플랫폼 문서를 만들지 않습니다.

## 관련 문서

- **제품 전체 범위**: [100 제품 개요](../../../product/100-product-overview.md), [200 기능 범위](../../../product/200-feature-scope.md)
- **현재 Community 결과**: [018 일반 커뮤니티](../../features/2026/018-general-community.md)
- **현재 동기 알림 결정**: [018 일반 커뮤니티 해결 설계](../../features/2026/018-general-community-design.md#d9-서비스-안-알림의-원자성과-중복-방지)
- **현재 application 구조**: [200 현재 프로젝트 아키텍처](../../../project/200-current-architecture.md), [210 모듈 지도](../../../project/210-module-map.md)
- **현재 infrastructure**: [100 Infrastructure overview](../../../project/infrastructure/100-infrastructure-overview.md), [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md), [150 Kubernetes cluster](../../../project/infrastructure/150-kubernetes-cluster.md)
- **현재 확장 후보**: [004 Infrastructure 진화 Backlog](../../backlog/2026/004-infrastructure-evolution.md)
- **계획 단계와 상태**: [100 작업 문서 생명주기](../../100-work-item-lifecycle.md#계획-단계와-최소-artifact)
