# Community Kafka 이벤트 전달 기반

## 상태

Proposed

## 계획 단계

실행 계획

## 현재 위치와 다음 행동

Community의 첫 Kafka 적용을 단일 node staging에서 검증하고, 이후 고가용성 구성으로
확장할 수 있는 결과 범위를 정의했습니다. 댓글·답글과 발행할 event는 PostgreSQL에 함께
저장하고, 서비스 안 알림은 Kafka consumer가 비동기로 생성하는 결과로 닫았습니다.

| 구분 | 현재 값 |
| --- | --- |
| 닫힌 결과 | 결과 정의와 해결 설계의 독립 검증·사용자 확정 범위·local checkpoint |
| 남은 gate | 실행 계획 작성·독립 검증과 exact 후보의 사용자 단계 확정 |
| 현재 실행자 | 없음 |
| 현재 행동 | 해결 설계에서 멈추고 다음 명시 요청을 기다림 |
| 사용자 작업 | 없음 |
| 현재 행동 뒤 결과 | 독립 검증을 통과한 [010 Community Kafka 이벤트 전달 상세 설계](010-community-kafka-event-delivery-design.md) 기준선 |
| 다음 전환 | 사용자가 실행 계획 작성을 명시하면 검증 가능한 결과 단위와 의존 순서를 작성 |

## 달성할 결과

현재 단일 Spring Boot application과 PostgreSQL을 사용하는 Community에 Kafka 기반 event
전달 경계를 추가합니다. 첫 대표 Community 쓰기는 broker나 consumer 장애와 분리되고,
전달할 사실은 유실되지 않으며, 중복 전달과 재처리 뒤에도 사용자에게 같은 결과가 두 번
생기지 않아야 합니다.

첫 구축은 비용을 제한한 단일 node staging에서 수행하되 이를 production 고가용성으로
표현하지 않습니다. Event 계약, application 경계, 저장·보안·관측·부하 검증과 운영 책임은
broker 수를 늘릴 때 업무 흐름을 다시 작성하지 않도록 설계합니다.

## 현재 문제

- Community 대화 생성은 현재 `notification` module의 inbox 저장을 같은 PostgreSQL
  transaction에서 직접 호출하므로 알림 쓰기 부하와 실패가 대화 쓰기에 함께 전파됩니다.
- 저장소에는 Kafka broker, topic, producer·consumer, transactional outbox와 재처리의 현재
  계약이 없습니다.
- 현재 staging은 control plane과 workload를 함께 실행하는 단일 node이고 live memory는
  4 GiB, root EBS는 20 GiB입니다. Kafka를 추가하기 전 compute·storage 여유와 data
  생명주기를 별도로 검증해야 합니다.
- 큰 부하를 견딘다는 목표가 아직 workload 비율·처리량·지연·복구 시간의 수치로 닫히지
  않았습니다. 기능 성공만으로 확장성을 주장할 수 없습니다.

## 범위

- 단일 node Kubernetes staging에 Kafka를 재현 가능하게 구축하는 repository 계약
- Community의 첫 대표 event 생산과 실제 소비 결과 한 흐름
- PostgreSQL 업무 transaction과 Kafka 전달 사이의 event 유실 방지
- 중복·순서·실패 event와 consumer 재시작에도 일관된 사용자 결과
- 외부 비공개, 인증된 내부 통신, 최소 권한, secret과 공급망의 보안 결과
- broker·relay·consumer·outbox의 metric, log, health와 장애 진단 신호
- 정상·점증·급증·장시간 부하와 broker·consumer 장애·복구 검증
- 단계적 전환, rollback, 미처리 event 재개와 data 보존
- 단일 node 한계의 명시와 다중 broker·node 고가용성 전환에 필요한 호환 경계
- 구현 뒤 application·infrastructure·schema·운영 절차의 영구 SOT 인계

## 제외 범위

- 이번 작업에서 production 다중 AZ·다중 node Kafka cluster를 실제 구축하는 것
- broker 또는 Kubernetes node autoscaling을 켜는 것
- Community 외 account·auth·catalog·media event를 Kafka로 옮기는 것
- Kafka 도입을 이유로 현재 modular monolith를 microservices로 분리하는 것
- 검색·추천·실시간 채팅·이메일·문자·push 같은 새 제품 기능
- Kafka 설치 자체의 최대 benchmark 수치를 서비스 전체 처리량으로 표현하는 것
- 단일 broker에서 장애 무중단, data replica 내구성이나 production 고가용성을 보장하는 것

## 품질 수용 기준

### 정확성과 데이터 일관성

- Community 업무 상태와 전달할 event 사이에 한쪽만 저장되는 dual-write 누락이 없습니다.
- 동일 event가 반복 전달되거나 consumer가 처리 직후 중단돼도 사용자 결과가 중복되지
  않습니다.
- 같은 대화에서 순서가 결과에 영향을 주는 event는 반복·동시 처리 뒤에도 의도한 순서의
  사용자 결과로 수렴합니다.
- 실패하거나 해석할 수 없는 event 하나가 무한 반복돼 다른 알림 처리를 계속 막지 않으며,
  운영자가 원인과 복구 대상을 식별할 수 있습니다.
- Event 계약을 바꾸는 배포에서도 이전 application과 이미 저장된 event가 일방적으로
  깨지지 않으며, 호환되지 않는 event를 정상 처리로 오판하지 않습니다.

### 성능과 확장성

- 읽기·쓰기 비율, payload 크기와 event fan-out을 포함한 대표 workload를 고정하고
  정상·점증·급증·장시간 시나리오를 같은 입력으로 재현합니다.
- API p50·p95·p99, 처리량, event 발행 지연, consumer lag, 사용자 결과 도착 시간, backlog
  해소 속도와 오류율을 함께 관찰합니다.
- Application·PostgreSQL·Kafka·node의 CPU, memory, disk 용량·지연, network와 connection을
  함께 측정해 병목을 Kafka 외부로 숨기지 않습니다.
- 같은 자원·workload에서 현재 동기 알림 경로와 비교해 지속 가능한 대화 쓰기 처리량이
  낮아지지 않고 API p95가 10% 넘게 악화되지 않아야 합니다.
- 가장 먼저 포화되는 자원의 70% 이하를 단일 node 정상 운영점으로 정합니다. 이 운영점의
  60분 test에서 API 오류율은 0.1% 미만이고 알림 도착 p99는 5초 이내이며, event 유실과
  사용자에게 보이는 중복 알림이 없어야 합니다.
- 정상 운영점에서 broker를 10분 중단해도 댓글·답글 작성은 성공해야 합니다. 복구 뒤
  consumer는 새 event를 처리하면서 20분 안에 중단 중 backlog를 해소해야 합니다.
- 단일 node 합격 결과는 그 resource와 workload 범위에만 적용하며, production 승격은
  별도의 가용성·용량·비용 목표와 다중 장애 검증을 요구합니다.
- Production 용량을 주장하거나 승격하기 전에는 제품 owner가 예상 peak 읽기·쓰기·event
  처리량, API·알림 지연, 오류율과 복구 목표를 수치로 확정하고 별도 승격 계획이 그 목표를
  검증해야 합니다. 이 값이 없으면 현재 결과를 production 준비 완료로 판정하지 않습니다.
- 처리량을 늘릴 때 업무 event·공개 API·기존 inbox data를 다시 쓰지 않아야 합니다.

### 장애·복구와 운영

- Broker 중단, producer 전송 실패, consumer 처리 실패, consumer의 DB commit 전·후 중단,
  재균형과 application 재시작을 독립적으로 검증합니다.
- Broker 복구 뒤 미전달 event와 consumer backlog가 자동 또는 승인된 운영 절차로 수렴하고,
  누락·중복 사용자 결과가 없습니다.
- Disk 고갈, consumer lag, 처리 불가능 event, outbox 적체와 인증 실패를 운영자가 서로
  구분할 수 있습니다.
- 배포와 data·event 계약 변경은 호환 가능한 순서를 가지며, 새 경로를 끄거나 이전 application으로
  rollback해도 기존 Community data와 미처리 event를 잃지 않습니다.
- Backup·보존·삭제와 재처리 범위, owner, 실행 조건과 완료 증거가 운영 절차에 연결됩니다.

### 보안과 유지보수

- Kafka는 public ingress로 노출하지 않고 허용한 workload의 인증된 암호화 통신만
  수락합니다. 허용되지 않은 workload의 연결과 다른 event 접근은 거부합니다.
- Secret은 Git·image·log에 노출되지 않고 최소 권한과 교체·폐기 경계를 가집니다.
- 도입 artifact는 현재 ARM64·Kubernetes·Java와 호환되는 공식 배포 source를 사용하고,
  갱신·취약점 대응·제거 책임을 잃지 않습니다.
- Event 생산·전달·소비가 상대 업무 module의 내부 구현이나 소유 data를 직접 변경하지
  않습니다.
- 단일 node용 예외값은 환경별 구성에 격리하고, application의 event 계약에 replica 수를
  새기지 않습니다.
- 도입한 구성요소마다 owner, 관측 방법, upgrade·rollback과 제거 경로가 있습니다.

## 결과 결정

### 확정

- 첫 Kafka 적용 대상은 Community로 제한합니다.
- 첫 broker topology는 비용을 제한한 단일 node Kubernetes staging입니다.
- 첫 실제 소비 흐름은 댓글·답글 생성 event를 거쳐 서비스 안 알림을 만드는 수직 단위로
  제한합니다. Kafka 설치 확인이나 소비자 없는 event 복사는 완료로 보지 않습니다.
- 댓글·답글과 발행할 event는 같은 PostgreSQL transaction에 저장합니다. Kafka나 consumer
  장애 중에도 대화 작성은 성공하고, 서비스 안 알림은 복구 뒤 누락 없이 생성합니다.
- 계획과 구현은 정확성, 기술적 완결성, 확장성, 유지보수성, 보안, 부하 검증과 운영·복구를
  함께 다룹니다.
- 단일 node의 부하 합격선은 현재 동기 경로와 같은 자원·workload 비교, 포화 자원의 70%
  운영점, 60분 정상 test, broker 10분 중단과 20분 backlog 복구로 판정합니다.
- Production peak와 SLO·복구 수치는 제품 owner가 production 승격 전에 별도로 확정해야
  하며, 그 값이 없으면 이 작업은 production 용량을 보장하지 않습니다.
- 단일 node 결과는 production 고가용성의 증거로 사용하지 않으며, 이후 topology 확장을
  막지 않는 계약을 만듭니다.

### 활성 결정

- 없음

## 관련 문서

- **해결 설계**: [010 Community Kafka 이벤트 전달 상세 설계](010-community-kafka-event-delivery-design.md)
- **현재 Community 결과**: [018 일반 커뮤니티](../../features/2026/018-general-community.md)
- **현재 동기 알림 결정과 재검토 조건**: [018 일반 커뮤니티 해결 설계](../../features/2026/018-general-community-design.md#d9-서비스-안-알림의-원자성과-중복-방지)
- **현재 application 구조**: [200 현재 프로젝트 아키텍처](../../../project/200-current-architecture.md), [210 모듈 지도](../../../project/210-module-map.md)
- **현재 infrastructure**: [100 Infrastructure overview](../../../project/infrastructure/100-infrastructure-overview.md), [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md)
- **현재 확장 후보**: [004 Infrastructure 진화 Backlog](../../backlog/2026/004-infrastructure-evolution.md)
- **계획 단계와 상태**: [100 작업 문서 생명주기](../../100-work-item-lifecycle.md#계획-단계와-최소-artifact)
