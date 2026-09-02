# Community Kafka 이벤트 전달 기반

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
| 지금 할 일 | 아래 결정 색인과 상세 설계를 확인하고 해결 설계를 확정하거나 변경할 identity를 지정합니다. |
| 사용자 차례 | 지금 |
| 응답 방식 | `해결 설계를 확정해 주세요.` 또는 `D번호/R1: <변경 요구>` |
| 이 행동 뒤 결과 | 변경 요구가 없으면 해결 설계가 닫히고 work item이 다음 명시 요청을 기다립니다. |
| 다음 단계 전환 | 사용자가 실행 계획 작성을 명시하면 `실행 계획`을 엽니다. |

## 닫힌 결과 정의

### 전체 상황

제품 범위에는 자동차 catalog 조회와 차량에 연결되지 않은 일반 Community가 함께 있습니다.
Repository에서 catalog 조회 API는 구현됐습니다. Community code도 구현됐지만 현재 기본
설정에서 비활성이고 원 기능 작업은 `In Progress`이므로, R1-A는 이미 production에 제공 중인
기능의 즉시 전환이 아니라 Community 활성화·검증과 순서를 맞춰야 하는 후속 결과입니다.
Account·auth는 보안 결과의 즉시 정확성이 우선이고, media는 독립 정리 worker를 이미 가집니다.

Community 댓글·답글 작성은 현재 서비스 안 알림 저장과 한 번에 성공하거나 실패합니다.
지금은 단순하고 원자적이지만, 알림 저장의 부하·장애가 댓글 작성 전체를 rollback합니다.
이후 검색 색인·활동 통계처럼 같은 사건을 쓰는 consumer가 늘면 생산자가 각 consumer를
동기로 호출하는 구조는 장애 반경과 배포 결합을 키웁니다.

Kafka 도입의 목적은 설치 자체가 아니라, 확정된 업무 사건을 전달·완충해 생산자와 consumer가
서로 다른 속도와 장애 경계에서 동작하고 복구 뒤 같은 사용자 결과로 수렴하게 하는 것입니다.
아직 제품 결과가 없는 기능을 Kafka를 위해 새로 만들거나, 작은 단일 node 성공을 production
고가용성으로 확대하지 않습니다.

### 달성할 결과

다음 결과는 R1-A 확정과 함께 계획의 상위 계약으로 닫혔습니다.

- Community 댓글·답글 작성은 messaging 처리 속도·장애에서 분리됩니다.
- 댓글·답글 작성과 전달할 사실 사이에는 한쪽만 남는 유실이 없고, 복구 뒤 서비스 안 알림이
  사용자 중복 없이 생성됩니다.
- 첫 producer와 consumer는 실제 사용자 결과까지 이어지며, consumer 없는 event 복사는
  완료로 보지 않습니다.
- 로컬·CI contract, 공유 기능 qualification, 별도 failure-domain 부하·장애 qualification과
  production 승격 판단은 서로 다른 환경과 증거로 구분합니다.
- 큰 부하 목표는 API 처리량뿐 아니라 event 발행률, consumer lag, backlog 해소 시간과
  application·PostgreSQL·Kafka·node 병목을 함께 측정합니다.
- 큰 부하·고가용성 주장은 application과 broker의 failure domain을 분리한 별도 환경에서
  broker 중단·확장 중 재배치·처리량·backlog 복구를 실제 검증한 결과에만 허용합니다. 환경
  생성 비용과 운영 권한은 실행 시점에 별도로 승인받습니다.
- Production topology와 수치는 peak·SLO·RPO·RTO·예산·운영 책임이 정해진 뒤 별도 승인합니다.

### 범위

- Community 댓글·답글 생성에서 서비스 안 알림까지 한 수직 흐름
- 업무 저장과 event 전달 사이의 유실·중복·재시작·처리 불가 event·복구 결과
- 변경 호환성, 책임 경계, 보안·관측·운영·rollback 결과
- 로컬·CI, 공유 기능 환경과 별도 failure-domain 부하·장애 qualification
- 구현 뒤 현재 구조·계약과 운영 절차의 영구 문서 인계

### 제외 범위

- Community 외 account·auth·catalog·media event의 이번 구현 연결
- Kafka 도입을 이유로 modular monolith를 microservice로 분리하는 것
- 검색·추천·실시간 채팅·이메일·문자·push 같은 새 제품 기능
- 현재 단일 node 결과로 production 처리량·고가용성·data 내구성을 주장하는 것
- 이 계획 문서 작성만으로 external environment나 production cluster를 실제 생성하는 것
- 아직 workload·SLO·비용이 없는 상태에서 production broker·partition·retention 수치를 고정하는 것
- Kafka를 Community 업무 상태나 알림 inbox의 원본 저장소로 사용하는 것

### 품질 수용 기준

#### 정확성과 복구

- Messaging 장애 중에도 선택한 업무 쓰기 결과가 보존되고 복구 뒤 누락 없이 consumer 결과로
  수렴합니다.
- 반복 전달과 어느 중단 지점에서도 사용자 결과가 중복되지 않습니다.
- 지원하지 않는 계약과 처리 불가 event를 정상 성공으로 오판하거나 다른 독립 처리를 무기한
  막지 않습니다.
- 변경·배포 뒤에도 이전 application과 이미 대기 중인 작업을 일방적으로 깨지 않습니다.
- Rollback과 전달 시스템 data 손실 복구 뒤에도 미처리 결과의 책임을 잃지 않습니다.

#### 성능과 증거 경계

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

#### 보안과 유지보수

- Kafka는 public internet에 노출하지 않고 허용한 producer·consumer만 인증된 암호화 통신을
  사용합니다.
- Secret과 불필요한 개인정보를 event·Git·image·metric·일반 log에 남기지 않습니다.
- 업무 책임은 상대의 내부 구현을 우회하지 않고 한 결과의 변경 주체를 하나로 유지합니다.
- 도입 도구마다 official source, 실제 caller·독립 oracle, 지원·upgrade·rollback·제거 조건과
  새 실패 표면을 계획합니다.

## 현재 결정 색인

| Identity | 상태 | 현재 값 | 상세 owner |
| --- | --- | --- | --- |
| R1 | 사용자 확정 | Community 댓글·답글 저장은 Kafka 장애와 분리하고, 알림은 consumer가 복구 뒤 생성 | 사용자 결정 canonical owner는 이 문서; [상세 설계의 R1](010-community-kafka-event-delivery-design.md#r1-kafka-적용-경계와-사용자-결과)은 기술 projection |
| 단일-node runtime | 사용자 확정 | 현재 Kubernetes에 Strimzi 기반 단일 Kafka node를 qualification 용도로 구축 | [상세 설계 D6](010-community-kafka-event-delivery-design.md#d6-runtime-storage와-환경별-증명-범위) |
| D2~D9 | Agent 후보 | 전달 원본부터 production 승격까지 해결 설계 선택 | [상세 설계 결정 지도](010-community-kafka-event-delivery-design.md#결정-지도) |

활성 value 결정은 없습니다. Managed Kafka는 현재 대안이 아니라 production 또는 최소 경로
실측 실패 때만 여는 재검토 후보입니다.

## 남은 설계 closure

구조와 내용이 독립 검증된 exact 해결 설계 후보에 대한 사용자 단계 확정만 남았습니다.
이미 닫힌 R1-A와 단일-node Kubernetes 범위를 다시 선택하지 않습니다.

## 전체 진행 지도

| 단계 | 상태 | 다음 조건 |
| --- | --- | --- |
| 결과 정의 | 완료 | 재개방하지 않음 |
| 해결 설계 | 현재 | Exact 후보의 사용자 단계 확정 |
| 실행 계획 | 미착수 | 사용자가 작성을 명시 |
| 개발 | 미착수 | 검증·확정된 실행 계획과 별도 구현 요청 |

## 관련 문서

- **제품 전체 범위**: [100 제품 개요](../../../product/100-product-overview.md), [200 기능 범위](../../../product/200-feature-scope.md)
- **현재 Community 결과**: [018 일반 커뮤니티](../../features/2026/018-general-community.md)
- **현재 동기 알림 결정**: [018 일반 커뮤니티 해결 설계](../../features/2026/018-general-community-design.md#d9-서비스-안-알림의-원자성과-중복-방지)
- **현재 application 구조**: [200 현재 프로젝트 아키텍처](../../../project/200-current-architecture.md), [210 모듈 지도](../../../project/210-module-map.md)
- **현재 infrastructure**: [100 Infrastructure overview](../../../project/infrastructure/100-infrastructure-overview.md), [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md), [150 Kubernetes cluster](../../../project/infrastructure/150-kubernetes-cluster.md)
- **현재 확장 후보**: [004 Infrastructure 진화 Backlog](../../backlog/2026/004-infrastructure-evolution.md)
- **계획 단계와 상태**: [100 작업 문서 생명주기](../../100-work-item-lifecycle.md#계획-단계와-최소-artifact)
