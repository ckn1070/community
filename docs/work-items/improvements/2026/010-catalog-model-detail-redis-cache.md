# 자동차 모델 상세 Redis cache 도입

## 상태

Done

## 결과

`GET /api/v1/car/models/{carModelId}`의 서울 기준 현재일 성공 결과에 Redis cache-aside를
적용했습니다. PostgreSQL은 계속 유일한 authoritative source이며 Redis data는 폐기·재생성
가능합니다. 기능 flag 기본값은 `false`이고 Redis가 비어 있거나 느리거나 사용할 수 없거나
오염된 document를 반환하면 기존 DB 조회 경로로 복구합니다.

Application에는 adapter 전용 versioned JSON document, 최대 300초의 downward-jitter TTL,
positive-only admission, process-local same-key single-flight, bounded in-flight key·source-load,
read/write circuit과 low-cardinality 관측을 구현했습니다. 보호 한도 소진은 기존 429와 분리한
`503 Service Unavailable`, `Retry-After: 2`,
`CATALOG_MODEL_DETAIL_CAPACITY_EXHAUSTED`로 응답합니다.

Kubernetes desired state에는 ARM64 digest로 고정한 Redis 8.2 replica 1 Deployment,
ClusterIP Service와 NetworkPolicy를 추가했습니다. Redis는 `Recreate`, `emptyDir`, RDB·AOF off,
`384mb` maxmemory, `allkeys-lfu`와 container resource/security 상한을 사용합니다. Application,
operation과 probe credential은 최소 ACL과 분리된 Secret consumer를 사용하며 default user는
비활성화됩니다.

Repository 전체 Gradle check, 실제 Redis integration, Bash syntax·ShellCheck·credential
Procedure 계약 test, staging Kustomize render와 Kubernetes schema 검증이 통과했습니다.
Application API·Redis adapter·동시성·circuit·503, manifest·Secret 경계와 기존 회귀를 함께
검증했습니다.

## 완료한 사용자·시스템 결과

| 결과 | 현재 보장 |
| --- | --- |
| 기존 API 보존 | Cache on·off, hit·miss와 Redis 실패에서 기존 성공·not-found·DB 오류 의미를 유지합니다. |
| 원본성과 재생성 | PostgreSQL만 원본이며 현재일 성공 결과만 Redis에 저장합니다. Cache miss·삭제·재시작 뒤 DB에서 다시 채웁니다. |
| Data 계약 | Query identity가 포함된 `carmunity:catalog:model-detail:v1:*` key와 schema version·시간·adapter 전용 전체 graph document를 사용합니다. Envelope와 graph identity·필수 구조가 맞지 않으면 삭제 후 miss로 처리합니다. |
| 동시성·DB 보호 | 같은 process·key의 miss는 한 future와 source 결과를 공유합니다. In-flight key와 DB source-load의 동시성·대기 시간이 각각 bounded이며 제한을 우회하지 않습니다. |
| 장애 격리 | Redis connect·command는 짧은 timeout과 retry 없음, 분리된 read/write circuit을 사용합니다. Read 실패는 보호된 DB path로, write 실패는 성공 응답과 분리된 best-effort 생략으로 끝납니다. |
| 시작·readiness | Redis를 사용할 수 없어도 application이 시작하고 DB 경로를 제공합니다. Application readiness는 Redis에 의존하지 않습니다. |
| 단일-node runtime | Redis replica 1은 무영속·memory bounded·private network·non-root로 배치되며 인증된 `PING`만 probe에 사용합니다. |
| 역할 분리 | Application은 key `GET`·`SET`·`DEL`·`PING`, operation은 `TTL`·`INFO commandstats`·`INFO stats`·`PING`, probe는 `PING`만 사용합니다. 상태 변경 진단 command와 public Redis 노출은 허용하지 않습니다. |
| 안전한 전환 | Redis desired state와 역할별 Secret을 먼저 준비하고 flag를 켭니다. 문제가 있으면 flag-off만으로 Redis command가 없는 DB-only 경로로 복구합니다. |
| 최소 관측 | Cache request·Redis operation·entry byte·source-load와 circuit 전이를 제한된 label의 metric과 민감정보 없는 rate-limited log로 구분합니다. |

## 보존 범위

- 자동차 모델 상세 한 endpoint의 cache port·adapter·orchestration
- 현재일 positive-only versioned key·JSON document·최대 5분 TTL
- PostgreSQL miss source loader의 read-only transaction 경계
- Process-local single-flight, bounded in-flight key·source-load와 503 공개 계약
- Redis timeout·no-retry·read/write circuit·PostgreSQL fallback
- Spring Data Redis·Lettuce와 Resilience4j core configuration
- 단일-node Kubernetes Redis Deployment·Service·NetworkPolicy와 resource·security 계약
- Application·operation·probe ACL, Git 밖 Secret과 credential reconciliation Procedure
- 기능 flag 기본값 `false`, activation·rotation·부분 실패 재개와 DB-only 복구
- Unit·실제 Redis integration·manifest·Procedure 검증과 영구 SOT 동기화

## 제외 범위

- k6 등 부하·성능 시험 script·runner·platform, RPS·p95·capacity 합격선과 전후 비교
- `redis-only` 실험 mode, ablation과 대규모 장애·chaos 시험 platform
- Staging 전용 metric endpoint, dashboard·collector와 RDS·node qualification orchestration
- 다른 catalog endpoint, community 기능, session, rate limiter와 distributed lock
- Redis 원본·backup화, PVC, RDB·AOF persistence
- Sentinel·Cluster·replica, HPA·node autoscaling, multi-node와 production HA
- TLS·certificate lifecycle과 production topology 확정
- Image 배포, live cluster apply와 staging smoke 실행

제외한 시험 체계와 production topology는 Redis 설치·application 결합 완료를 판정하는 현재
결과가 아닙니다. 필요할 때 별도 목표·SLO·예산·운영 입력으로 새 작업을 엽니다.

## 확정 결정 색인

상세 비교·탈락 이유·trade-off와 재검토 조건은
[Redis cache 설계](010-catalog-model-detail-redis-cache-design.md)가 보존합니다.

| 결정 | 확정값 |
| --- | --- |
| D1·D2 | 명시적 cache-aside와 catalog output port, miss 전용 transactional source loader |
| D3·D4 | Versioned adapter document, 현재일 positive-only, 300초 이하 downward jitter, namespace invalidation |
| D5 | Process-local single-flight, bounded in-flight·DB bulkhead, 503·`Retry-After` 계약 |
| D6 | 짧은 timeout, retry 없음, read/write circuit, DB fallback, Resilience4j core |
| D7·D8 | Spring Data Redis·Lettuce, Redis 8.2, Deployment·Recreate, 무영속, RSALv2, `allkeys-lfu` |
| D9 | ClusterIP, Calico NetworkPolicy, 역할별 최소 ACL·Secret, staging 평문과 전용 credential entrypoint |
| D10 | Application readiness 분리와 metric·circuit 전이·rate-limited 실패 요약 |
| D12 | GitOps flag 기본값 `false`, DB-only 복구와 production topology 비승격 |

## 승격한 영구 SOT

| 현재 계약 | Canonical owner |
| --- | --- |
| Catalog cache port·adapter·transaction·관측 lifecycle | [210 Module Map](../../../project/210-module-map.md), [130 자동차 카탈로그](../../../domain/130-car-catalog.md) |
| Redis·Lettuce·Resilience4j version과 사용 원칙 | [100 현재 기술 스택](../../../tech-stack/100-current-stack.md) |
| Local Redis·cache 환경변수 | [.env.example](../../../../.env.example), [100 Local 개발](../../../development/100-local-development.md) |
| 503·`Retry-After` 공개 계약 | [230 REST API 설계](../../../architecture/230-rest-api-design.md) |
| Kubernetes resource·GitOps·Secret·staging transport 경계 | [deploy README](../../../../deploy/README.md), [220 GitOps Application Delivery](../../../project/infrastructure/220-gitops-application-delivery.md) |
| Credential·activation·rotation·복구 Procedure | [580 Catalog Redis 활성화·credential](../../../operations/580-catalog-redis-activation-and-credential-runbook.md) |

## 외부 후속 범위

현재 완료 범위에 남은 사용자 작업은 없습니다. Image 배포와 live staging 적용·smoke는
수행하지 않았으며 repository 결과만으로 완료를 추정하지 않습니다. 실제 환경에 활성화할
때는 별도 변경 권한과 target 입력을 확보한 뒤 580 runbook의
`AppProject permission → 역할별 Secret → flag-off Redis sync·인증 → application 연결 → flag-on cold/warm no-write 확인`
순서로 실행합니다. 문제가 있으면 flag를 `false`로 되돌리고 PostgreSQL data나 Redis key를
scan·flush하지 않습니다.

## 관련 문서

- [010 자동차 모델 상세 Redis cache 설계](010-catalog-model-detail-redis-cache-design.md)
- [016 신차 카탈로그 schema·조회 API](../../features/2026/016-new-car-catalog-query-api.md)
- [004 Infrastructure 진화 Backlog](../../backlog/2026/004-infrastructure-evolution.md)
- [100 작업 문서 생명주기](../../100-work-item-lifecycle.md)
