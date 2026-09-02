# 자동차 모델 상세 Redis cache 도입

## 상태

Proposed

## 계획 단계

결과 정의

## 현재 결론과 다음 행동

| 구분 | 현재 내용 |
| --- | --- |
| 현재 결론 | DB가 안전하게 처리할 수 있는 한계를 넘었을 때 그 상황을 별도 오류로 구별해 재시도를 안내할지 U1이 열려 있어 결과 정의를 다시 열었습니다. 나머지 대상·원본성·freshness·staging 범위는 닫힌 선행 결과로 유지합니다. |
| 닫힌 결과 경계 | 첫 적용 대상은 `GET /api/v1/car/models/{carModelId}` 하나이고 PostgreSQL은 유일한 원본입니다. 최대 stale 시간은 5분이며, 계획은 저장소 구현 뒤 별도 권한에 따른 staging 실증까지 포함합니다. 단일 Redis instance는 현재 staging의 비고가용성 출발점일 뿐 production 고가용성이나 자동 확장을 뜻하지 않습니다. |
| 선행 외부 gate | License owner가 실제 배포·사업 조건을 공식 세 license 원문과 대조해 하나를 기록하기 전에는 이 작업의 새 catalog model-detail cache Redis workload/image를 repository에 추가하거나 staging·production에 배포하지 않습니다. 기존 Argo CD 내부 Redis는 이번 mutation 범위가 아닌 별도 compliance 관찰입니다. |
| 남은 gate | 현재 결과 정의 후보는 fresh 독립 검증을 통과했습니다. 아래 U1 선택과 그 값을 반영한 결과 정의의 사용자 확정이 필요하며, 그 전에는 실행 계획이나 구현 입력으로 사용하지 않습니다. |
| 현재 행동 주체 | 사용자가 아래 U1의 제품 동작을 선택합니다. |
| 사용자 차례 | `U1은 과부하를 구별하고 재시도를 안내하도록 반영해줘` 또는 `U1은 기존 일반 서버 오류 의미를 유지하도록 반영해줘`로 답합니다. |
| 현재 행동 뒤 결과 | U1 응답을 결과·수용 기준에 반영하고 결과 정의를 닫은 뒤, 보존 중인 해결 설계에서 D5와 API 영향을 다시 검증합니다. |

## 활성 사용자 결정 U1: DB 처리 한계 초과를 별도 오류로 알릴지

**결정 질문:** Redis에 저장된 값이 없는 서로 다른 요청이 한꺼번에 들어와, application이
허용한 DB 동시 조회 수와 최대 대기 시간까지 모두 찼을 때 그 상황을 일반 서버 오류와
구별해 “잠시 뒤 다시 요청할 수 있음”으로 알려야 합니까?

두 후보는 모두 정해진 DB 동시 조회 제한을 우회하지 않아 database를 보호합니다. 차이는
서비스 이용자가 일시적인 처리 한계 초과를 구별하고 재시도 행동을 결정할 수 있는지입니다.

| 비교 축 | U1-A. 일시 과부하를 별도 오류로 구별하고 재시도 안내 | U1-B. 기존 일반 서버 오류로만 응답 |
| --- | --- | --- |
| 이용자가 받는 의미 | 현재 처리 한계가 일시적으로 찼고 나중에 재시도할 수 있음을 압니다. | 예상하지 못한 내부 오류와 구별할 수 없습니다. |
| DB 보호 | 정해진 DB 동시 조회 제한을 우회하지 않습니다. | 정해진 DB 동시 조회 제한을 우회하지 않습니다. |
| 재시도 행동 | 얼마 뒤 다시 요청해야 하는지 안내할 수 있습니다. | 재시도 가능 여부와 시점을 알 수 없습니다. |
| API 변화 | 새 오류 의미와 API 문서·검증이 필요합니다. | 공개 의미를 추가하지 않지만 실제 원인을 일반 오류로 숨깁니다. |
| 운영 판별 | 일시 과부하를 예상하지 못한 장애와 구별해 측정할 수 있습니다. | 별도 내부 지표 없이는 일반 서버 오류와 구별하기 어렵습니다. |
| 현재 목표 판정 | **큰 부하에서 보호 동작을 명확히 드러내고 통제된 재시도를 가능하게 합니다.** | 변경량은 작지만 과부하를 숨겨 이용자·운영자 판단력이 낮습니다. |

**추천: U1-A, 일시 과부하를 별도 오류로 구별하고 재시도를 안내합니다.** 큰 부하를 견딘다는 목표는 모든
요청을 무조건 성공시키는 것이 아니라, 안전 용량을 넘을 때 database를 보호하고 실패 의미와
회복 행동을 예측 가능하게 만드는 것까지 포함하기 때문입니다. 새 공개 오류 의미와 API 문서·
이용자 재시도 검증을 추가하는 비용을 감수합니다.

구체적인 HTTP status·header·error code는 이 사용자 결정이 닫힌 뒤 REST 기준과 표준을
대조해 해결 설계에서 Agent가 비교·선택합니다. 초과 요청을 제한 없이 DB로 보내는 방식은
DB 보호를 무효화하므로 후보가 아닙니다. DB가 자동 확장되거나 별도 요청 제한 계층이 이
오류 의미를 소유하면 결정을 다시 엽니다.

검증 뒤 `U1은 과부하를 구별하고 재시도를 안내하도록 반영해줘` 또는 `U1은 기존 일반 서버
오류 의미를 유지하도록 반영해줘`로 답하면, Agent가 제품 결과를 반영해 결과 정의를 닫고
구체적인 HTTP 구현값과 영향받은 해결 설계를 비교·검증합니다.

## 진행 지도

| 단계 | 상태 | 현재 작업에서의 결과·전환 조건 | 실행자와 즉시 다음 행동 |
| --- | --- | --- | --- |
| 범위·결정 정규화 | 완료 | 자동차 모델 상세, 현재 단일 node Kubernetes, PostgreSQL 원본성, 부하 시험과 품질 축을 입력으로 고정했습니다. | 사용자 작업 없음 |
| 결과 정의 | 현재 | DB 처리 한계 초과를 별도 오류로 알리고 재시도를 안내할지 U1을 같은 평가축으로 비교·추천했고 fresh 독립 검증을 통과했습니다. U1 선택과 결과 정의 확정이 남았습니다. | 사용자가 U1을 선택합니다. |
| 해결 설계 | 부분 재검토 대기 | R0과 D1~D12 비교 후보를 보존하되 U1이 닫힐 때까지 구현 입력으로 사용하지 않습니다. | 결과 정의 closure 뒤 D5·API 영향부터 다시 검증합니다. |
| 실행 계획 | 후속 | 검증 가능한 결과 단위, 의존 순서, test·문서·운영 책임과 완료 증거를 확정합니다. | 해결 설계 closure 뒤 Agent가 진행합니다. |
| 계획 검증·확정 | 후속 | 최초 목적·확정 결정·current source를 기준으로 완성 후보를 검증하고 사용자 채택 뒤 `Planned`로 전환합니다. | 실행 계획 후보와 필수 검증이 준비된 뒤 진행합니다. |
| 개발·결과 검증 | 조건부 후속 | 별도 개발 요청과 `Planned` baseline 뒤 repository 구현·자동 검증을 수행하고, 그 결과가 검증된 뒤 별도 live 적용 권한을 받아 staging 실증까지 수행합니다. | 이 계획 작업의 terminal이 아니며 이후 실행 요청이 필요합니다. |
| 영구 SOT·Git 마무리 | 조건부 후속 | 검증된 실제 구성과 운영 계약만 canonical 문서에 승격하고 승인된 Git·remote 효과만 수행합니다. | 구현·검증 결과가 생긴 뒤 해당 권한 범위에서 진행합니다. |

## 달성할 결과

- 비회원 자동차 모델 상세 조회의 반복 요청이 안전하게 재사용 가능한 결과를 Redis에서
  얻어 PostgreSQL 조회 횟수와 응답 지연을 줄입니다.
- PostgreSQL을 자동차 카탈로그의 유일한 authoritative source로 유지하고 Redis data는 언제든
  폐기·재구축할 수 있습니다.
- Cache miss, 빈 cache, 만료, Redis 장애와 재시작 상황에는 기존 API 응답 의미를 보존하고,
  DB 처리 한계 초과의 공개 오류 의미는 U1에서 명시적으로 결정합니다. Redis 장애가 모델 상세
  전체 장애로 곧바로 확대되지 않게 합니다.
- 동일한 환경·데이터·workload에서 `disabled`·`redis-only`·`redis-protected`를 비교해
  처리량, latency, 오류율, cache 효율, application·Redis·node·database 부하를 함께
  관찰하고 Redis 효과·single-flight 효과·다음 병목을 구분합니다.
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
- 최대 5분 freshness 상한을 정상 만료, 카탈로그 변경, 무효화 실패와 clock 경계에서 검증합니다.
- Redis와 측정 기반을 함께 구현한 뒤 같은 application artifact의 `disabled`·`redis-only`·
  `redis-protected` 상태에서 동결한 RPS·p95·오류율·DB 부하 기준을 같은 환경·data·요청
  분포로 재현합니다. Redis와 single-flight 효과를 별도 pair로 귀속하고 warm-up과 측정
  구간을 분리합니다.
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
- 성능 측정 기반과 Redis 구현은 함께 준비하며, 같은 artifact의 `disabled`를 기준선으로
  먼저 측정한 뒤 `redis-only`와 `redis-protected`를 비교합니다. 별도 선행 측정 프로젝트를
  만들지 않습니다.
- 카탈로그 변경 뒤 stale 허용 상한은 5분입니다.
- 계획의 완료 범위에는 저장소 구현·자동 검증과 별도 live 적용 권한을 받은 뒤의 staging
  부하·장애·rollback 실증이 포함됩니다. 이 결정 자체는 현재 live 적용 권한이 아닙니다.

## 해결 설계 결정 색인

- Application 경계, cache key·document·TTL, transaction·동시성, 장애 격리, Redis runtime,
  Kubernetes 배치, 보안, 관측·부하 시험, 전환·복구와 production 승격 조건은
  [010 자동차 모델 상세 Redis cache 설계](010-catalog-model-detail-redis-cache-design.md)의
  D1~D12가 소유합니다.
- 해결 설계는 PostgreSQL schema를 변경하지 않습니다. 공개 API 변경 여부는 U1이 소유하며,
  Redis 장애 fallback 자체는 기존 성공·not-found·DB 오류 의미를 보존합니다.
- DB 처리 한계 초과를 별도 오류로 구별하고 재시도를 안내할지 U1은 사용자 선택 전까지 열려
  있습니다. 구체적인 HTTP 구현값은 그 뒤 해결 설계에서 Agent가 정합니다.
  나머지 D1~D12 기술 선택도 fresh 독립 검증과 사용자 단계 확정 전에는 실행 계획이나 구현
  입력이 아닙니다.

## 관련 문서

- [200 제품 기능 범위](../../../product/200-feature-scope.md)
- [130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md)
- [110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)
- [016 신차 카탈로그 schema·조회 API](../../features/2026/016-new-car-catalog-query-api.md)
- [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md)
- [150 Kubernetes cluster](../../../project/infrastructure/150-kubernetes-cluster.md)
- [220 GitOps Application Delivery](../../../project/infrastructure/220-gitops-application-delivery.md)
- [004 Infrastructure 진화 Backlog](../../backlog/2026/004-infrastructure-evolution.md)
- [010 자동차 모델 상세 Redis cache 설계](010-catalog-model-detail-redis-cache-design.md)
- [100 작업 문서 생명주기](../../100-work-item-lifecycle.md)
