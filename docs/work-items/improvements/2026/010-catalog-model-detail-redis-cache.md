# 자동차 모델 상세 Redis cache 도입

## 상태

Proposed

## 계획 단계

결과 정의

## 활성 결정 블록

| 구분 | 현재 내용 |
| --- | --- |
| 현재 결론 | 공개 자동차 모델 상세 조회의 반복 읽기 부하를 줄이기 위해 현재 단일 node Kubernetes에 폐기 가능한 Redis cache를 도입하는 결과 정의 후보입니다. PostgreSQL은 계속 유일한 원본이며 cache가 비어 있거나 사용할 수 없어도 조회 정확성을 잃지 않아야 합니다. |
| 닫힌 결과 경계 | 첫 적용 대상은 `GET /api/v1/car/models/{carModelId}` 하나이며 브랜드·모델 목록과 커뮤니티 기능은 포함하지 않습니다. 단일 Redis instance는 현재 staging 제약에 맞춘 비고가용성 출발점이고 production 고가용성이나 자동 확장을 뜻하지 않습니다. |
| 남은 gate | 성능 성공 기준, 카탈로그 변경 뒤 허용할 최대 stale 시간, 실제 staging 검증을 계획의 완료 범위에 포함할지 확정해야 합니다. |
| 현재 행동 주체 | 사용자가 아래 세 결정 카드의 선택지를 한 번에 확정합니다. |
| exact 응답 형식 | `성능=A, 갱신=A, 완료=A로 확정하고 결과 정의에 반영해줘` |
| 답변 뒤 결과 | Agent가 선택을 결과·범위·품질 수용 기준에 반영하고 새 사용자 판단이 생기지 않으면 결과 정의를 닫아 `Proposed / 해결 설계`로 전환합니다. 그 전에는 Redis 제품·배포 kind·key·TTL·직렬화·동시성 제어 같은 구현 결정을 확정하지 않습니다. |

### 성능 성공 기준

- **결정 주체**: 사용자
- **왜 지금 필요한가**: “상당히 큰 부하”는 방향이지만 수치로 검증할 수 있는 완료 조건은
  아닙니다. 임의 RPS를 먼저 약속하면 현재 단일 node·RDS·데이터 분포와 맞지 않을 수 있습니다.
- **A — 기준선 측정 뒤 수치 동결 (추천)**: 같은 환경·데이터·요청 분포로 cache 도입 전
  최대 안정 처리량과 병목을 먼저 측정합니다. 그 결과로 목표 RPS·p95·오류율·DB 부하를
  구현 전에 동결하고, 도입 전후 비교와 목표 통과를 모두 요구합니다.
- **B — 절대 목표 선결정**: 사용자가 목표 RPS, p95 latency와 허용 오류율을 지금 제공합니다.
  목표는 명확하지만 현재 용량 근거가 없어 node나 RDS 확장 요구와 섞일 수 있습니다.
- **C — 상대 개선만 평가**: 동일 부하에서 cache 전후 응답 시간과 DB 부하 개선만 요구합니다.
  비교는 쉽지만 “큰 부하를 견딘다”는 절대 capacity를 입증하지 못합니다.
- **추천 근거**: A는 현재 시스템의 실제 한계를 먼저 관찰하면서도 최종 계획에는 수치형
  통과 기준을 남깁니다. Redis 효과와 node·RDS 병목을 분리해 설명할 수 있습니다.

### 카탈로그 변경 뒤 최대 stale 시간

- **결정 주체**: 사용자
- **왜 지금 필요한가**: 모델 상세에는 판매 상태와 가격처럼 시점에 따라 바뀌는 정보가 있어
  cache 효율만으로 freshness 기준을 대신할 수 없습니다.
- **A — 최대 5분 (추천)**: 변경 반영 지연을 짧게 제한하면서 반복 조회의 cache 효율을
  확보합니다. 실패한 무효화가 있어도 시간 상한으로 회복해야 합니다.
- **B — 최대 30분**: cache 효율과 운영 단순성은 높아지지만 판매 상태·가격의 이전 값이 더
  오래 노출될 수 있습니다.
- **C — 성공한 카탈로그 배포 직후 stale 불허**: 변경과 cache 전환을 하나의 강한 운영
  계약으로 묶습니다. 현재 data migration 중심 변경 경로에 추가 실패 지점과 복구 절차가
  필요합니다.
- **추천 근거**: A는 현재 읽기 전용·저빈도 변경 특성과 사용자에게 보이는 가격 정보의
  정확성 사이에 보수적인 상한을 둡니다.

### 계획의 완료 범위

- **결정 주체**: 사용자
- **왜 지금 필요한가**: manifest와 test만 있는 상태와 실제 단일 node staging에서 자원·장애·
  성능을 검증한 상태는 근거 수준과 외부 효과가 다릅니다.
- **A — 저장소 구현과 staging 실증을 분리된 단계로 모두 계획 (추천)**: 애플리케이션·배포
  artifact를 먼저 검증한 뒤 별도 실행 권한을 받아 staging에 적용하고 부하·장애 시험과
  rollback 확인까지 수행하도록 계획합니다. 이 선택은 지금 live 적용 권한을 부여하지 않습니다.
- **B — 저장소 구현까지만 계획**: 코드·manifest·자동 test·runbook 완성을 terminal로 두고
  실제 staging 배포와 측정은 후속 작업으로 남깁니다.
- **추천 근거**: A는 단일 node에 Redis를 함께 둘 때의 memory·CPU·disk·network 간섭과 실제
  DB 부하 감소를 확인할 수 있어 운영 승격 판단에 더 강한 근거를 남깁니다.

## 진행 지도

| 단계 | 상태 | 현재 작업에서의 결과·전환 조건 | 실행자와 즉시 다음 행동 |
| --- | --- | --- | --- |
| 범위·결정 정규화 | 완료 | 자동차 모델 상세, 현재 단일 node Kubernetes, PostgreSQL 원본성, 부하 시험과 품질 축을 입력으로 고정했습니다. | 사용자 작업 없음 |
| 결과 정의 | 현재 | 목표·범위·제외·품질 수용 기준 후보를 작성했습니다. 위 세 사용자 결정이 반영되고 사용자가 이 단계 결과를 채택하면 완료됩니다. | 사용자가 성능·freshness·완료 범위를 한 번에 선택합니다. |
| 해결 설계 | 다음 | 닫힌 결과 정의를 입력으로 application, infrastructure, 일관성·동시성, 보안, 관측·시험, rollout·복구 결정을 source와 공식 근거에 대조합니다. | 결과 정의 closure 뒤 Agent가 진행합니다. |
| 실행 계획 | 후속 | 검증 가능한 결과 단위, 의존 순서, test·문서·운영 책임과 완료 증거를 확정합니다. | 해결 설계 closure 뒤 Agent가 진행합니다. |
| 계획 검증·확정 | 후속 | 최초 목적·확정 결정·current source를 기준으로 완성 후보를 검증하고 사용자 채택 뒤 `Planned`로 전환합니다. | 실행 계획 후보와 필수 검증이 준비된 뒤 진행합니다. |
| 개발·결과 검증 | 조건부 후속 | 별도 개발 요청과 `Planned` baseline 뒤 repository 구현·자동 검증을 수행하고, 선택한 완료 범위가 A이면 별도 live 적용 권한을 받아 staging 실증까지 수행합니다. | 이 계획 작업의 terminal이 아니며 이후 실행 요청이 필요합니다. |
| 영구 SOT·Git 마무리 | 조건부 후속 | 검증된 실제 구성과 운영 계약만 canonical 문서에 승격하고 승인된 Git·remote 효과만 수행합니다. | 구현·검증 결과가 생긴 뒤 해당 권한 범위에서 진행합니다. |

## 달성할 결과

- 비회원 자동차 모델 상세 조회의 반복 요청이 안전하게 재사용 가능한 결과를 Redis에서
  얻어 PostgreSQL 조회 횟수와 응답 지연을 줄입니다.
- PostgreSQL을 자동차 카탈로그의 유일한 authoritative source로 유지하고 Redis data는 언제든
  폐기·재구축할 수 있습니다.
- Cache miss, 빈 cache, 만료, 장애와 재시작 상황에도 기존 API의 응답 의미와 오류 계약을
  보존하고, Redis 장애가 모델 상세 전체 장애로 곧바로 확대되지 않게 합니다.
- 동일한 환경·데이터·workload의 도입 전후 부하 시험으로 처리량, latency, 오류율, cache
  효율, application·Redis·node·database 부하를 함께 관찰해 Redis 효과와 다음 병목을
  구분합니다.
- 현재 단일 node staging의 비용·용량 제약 안에서 resource 사용 상한과 운영 경계를 두고,
  고가용성·다중 node·production 전환은 실측 trigger가 있는 별도 결정으로 남깁니다.
- 구성, 관측, 장애 대응, disable·rollback과 후속 topology 전환 기준을 재현 가능한 repository
  artifact와 운영 문서로 남깁니다.

## 현재 문제

- 모델 상세 application service는 현재 하나의 read port를 직접 호출하고, jOOQ adapter는
  모델·연식·동력계통·판매 구성·기술 변형을 조합하기 위해 요청마다 여러 고정 query group을
  수행합니다. 동일 모델의 반복 조회도 같은 관계형 read cost를 다시 지불합니다.
- 현재 application dependency와 runtime 설정에는 자동차 카탈로그 cache가 없으며, cache
  hit·miss·latency·failure를 관찰하는 계약도 없습니다.
- 현재 Kubernetes application은 replica `1`이고 resource request·limit과 HPA가 없습니다.
  Control plane과 workload가 live memory 4 GiB인 단일 node를 공유하므로 Redis를 추가하면
  application·platform과 같은 node 자원을 경쟁합니다.
- 현재 저장소에는 자동차 모델 상세의 처리량·latency·database 부하를 재현하는 부하 시험이
  없습니다. 따라서 “Redis가 필요하다”거나 “큰 부하를 견딘다”는 결론을 아직 수치로 증명할
  수 없습니다.
- 자동차 카탈로그 SOT는 실제 query plan이 관계형 조회 목표를 충족하지 못할 때 cache를
  검토하도록 요구합니다. 도입은 이 조건을 무시하지 않고 기준선과 도입 후 증거를 함께
  만들어야 합니다.

## 범위

- 공개 자동차 모델 상세 조회 한 경로의 반복 read cost와 capacity
- 기존 API 성공·not-found 응답 의미와 기준일 기반 카탈로그 규칙의 보존
- PostgreSQL 원본과 폐기 가능한 Redis 보조 data 사이의 일관성·freshness 결과
- Cache가 정상, 비어 있음, 만료됨, 사용할 수 없음, 느림, 재시작됨인 상태의 서비스 결과
- 현재 단일 node Kubernetes에 함께 배치할 때의 CPU·memory·storage·network·health 경계
- Application, Redis, node와 PostgreSQL을 연결하는 최소 관측 지표와 민감정보 없는 진단
- 동일 workload로 반복 가능한 기준선·도입 후 부하 시험과 결과 판정
- 안전한 enable·disable, 배포 순서, 실패 중단, rollback과 cache 재구축 결과
- 고가용성·다중 node·production topology로 재검토할 측정 trigger와 이관 경계

## 제외 범위

- 브랜드 목록, 모델 목록, 검색, 추천과 다른 자동차 카탈로그 endpoint의 cache
- 게시글·댓글·반응 등 커뮤니티 기능, session, shared rate limiter와 distributed lock
- Redis를 자동차 카탈로그의 원본이나 영구 보존·backup 대상으로 사용하는 구성
- 자동차 카탈로그 schema, 공개 API payload와 업무 규칙의 변경
- 관리자 입력·crawler·별도 writer API와 범용 cache invalidation platform
- Redis Sentinel·Cluster, 상시 replica, 다중 node, Kubernetes HPA·node autoscaling과
  production 고가용성·SLA
- CDN, 검색엔진, message broker와 RDS instance·storage topology 변경
- 아직 선택하지 않은 다른 workload를 위한 범용 Redis platform 선행 구축

## 품질 수용 기준

- Cache hit와 miss가 같은 입력에 대해 기존 PostgreSQL 조회와 의미상 동일한 응답을 만들고,
  존재하지 않는 모델·기준일 경계·가격·판매 상태를 바꾸지 않습니다.
- Redis data를 모두 지우거나 새 instance로 교체해도 PostgreSQL에서 모델 상세를 다시 읽어
  정상 결과로 회복할 수 있습니다.
- Redis 연결 실패·timeout·잘못된 cache data가 무제한 대기, 잘못된 성공 응답 또는 전체 API
  장애로 확대되지 않으며, fallback과 오류 분류를 자동 test와 장애 시험으로 확인합니다.
- 선택한 freshness 상한을 정상 만료, 카탈로그 변경, 무효화 실패와 clock 경계에서 검증합니다.
- 선택한 성능 결정에 따라 동결한 RPS·p95·오류율·DB 부하 기준과 도입 전후 비교를 같은
  환경·data·요청 분포에서 재현하고, warm-up과 측정 구간을 분리합니다.
- Cache stampede, hot key, 동시 miss와 Redis 재시작 중에도 database·connection pool·node에
  통제되지 않은 부하 증폭이 발생하지 않는지 검증합니다.
- Redis와 application의 resource 사용에 상한을 두며 memory 고갈·eviction·CPU throttling·
  node pressure 때의 중단 기준과 복구 행동이 명시됩니다.
- Redis는 public ingress에 노출하지 않고 필요한 application workload만 접근하며, credential·
  cache value·key와 진단 log가 비밀정보나 불필요한 개인정보를 포함하지 않습니다.
- Cache hit ratio만으로 성공을 판정하지 않고 request latency·error, Redis latency·memory·
  eviction, JVM·connection pool, PostgreSQL query·connection과 node pressure를 함께 관찰합니다.
- Image architecture, health check, graceful termination, rollout 순서와 disable·rollback 경로를
  repository source와 runbook에서 재현할 수 있습니다.
- 단일 instance 장애와 node 장애를 구분하고, 현재 구성을 production 고가용성으로 오인하지
  않도록 운영 경계와 승격 trigger가 문서화됩니다.

## 결과 결정

### 확정

- 첫 Redis 적용 대상은 비회원 자동차 모델 상세 조회입니다.
- Redis는 현재 self-managed 단일 node Kubernetes 안에서 단일 instance로 시작합니다.
- PostgreSQL은 유일한 authoritative source이고 Redis는 폐기 가능한 read 보조 수단입니다.
- 부하 시험은 선택 기능이 아니라 도입 전 기준선, 도입 후 효과와 다음 병목을 입증하는 완료
  증거입니다.
- 설계는 성능뿐 아니라 정확성, 장애 격리, 보안, 관측성, resource 상한, 배포·복구와 미래
  확장 경계를 함께 다룹니다.
- 현재 단계의 단일 instance는 비용과 검증 범위를 제한한 출발점이며 production 고가용성이나
  설정 변경만으로 완성되는 자동 확장 구성이 아닙니다.

## 관련 문서

- [200 제품 기능 범위](../../../product/200-feature-scope.md)
- [130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md)
- [110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)
- [016 신차 카탈로그 schema·조회 API](../../features/2026/016-new-car-catalog-query-api.md)
- [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md)
- [150 Kubernetes cluster](../../../project/infrastructure/150-kubernetes-cluster.md)
- [220 GitOps Application Delivery](../../../project/infrastructure/220-gitops-application-delivery.md)
- [004 Infrastructure 진화 Backlog](../../backlog/2026/004-infrastructure-evolution.md)
- [100 작업 문서 생명주기](../../100-work-item-lifecycle.md)
