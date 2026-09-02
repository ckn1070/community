# 자동차 모델 상세 Redis cache 도입

## 상태

Proposed

## 계획 단계

실행 계획

## 현재 결론과 다음 행동

| 구분 | 현재 내용 |
| --- | --- |
| 현재 단계 | `Proposed / 실행 계획` |
| 현재 결론 | 지금은 Redis를 정확히 설치하고 자동차 모델 상세 조회에 안전하게 결합하는 데만 집중합니다. 확정한 Redis 내부 보호 계약은 구현하되, 별도의 성능·대규모 실패 시험 체계는 이번 계획에 넣지 않습니다. |
| 닫힌 결과 | 대상 API, PostgreSQL 원본성, cache-aside 경계, data·TTL, 동시 miss·DB 보호, Redis 실패 격리, 단일-node Kubernetes 구성, 보안, 최소 관측, flag-off 복구와 production 비승격 경계를 확정했습니다. |
| 남은 gate | Content·문서 독립 검증을 통과한 아래 T1~T6 exact 후보를 사용자가 확정해야 합니다. |
| 지금 actor | 사용자 |
| 지금 할 일 | 수용 기준, T1~T6와 의존 순서가 논의한 범위를 정확히 실행하는지 확인합니다. |
| 사용자 차례 | 실행 계획 전체 확정 또는 변경할 작업 ID와 값을 답합니다. |
| 응답 방식 | `실행 계획 전체 확정` 또는 `T번호: 변경 내용` |
| 이 행동 뒤 결과 | 전체 확정과 동시에 두 문서를 `Planned / 계획 완료`로 전환할 수 있습니다. |
| 다음 단계 전환 | 사용자가 exact 후보 전체를 확정하면 곧바로 `Planned / 계획 완료`가 됩니다. 이후 별도 개발 요청으로 구현을 시작하며, live staging 적용은 repository 검증 뒤 별도 권한으로만 수행합니다. |

## 진행 지도

| 단계 | 상태 | 닫힌 결과 또는 전환 조건 |
| --- | --- | --- |
| 범위·결정 정규화 | 완료 | 자동차 모델 상세 한 경로, PostgreSQL 원본, 단일 Redis instance와 이번 제외 범위를 고정했습니다. |
| 결과 정의 | 완료 | 기존 성공·not-found·DB 오류 의미, 5분 stale 상한과 DB 보호 한도 소진의 공개 오류 의미를 확정했습니다. |
| 해결 설계 | 완료 | [설계 문서](010-catalog-model-detail-redis-cache-design.md)의 D1~D10·D12를 사용자 논의에 따라 닫았습니다. |
| 실행 계획 | 현재 | 아래 수용 기준과 T1~T6 후보가 content·문서 독립 검증을 통과했고 사용자 확정을 기다립니다. |
| 계획 완료 | 후속 | 사용자가 exact 실행 계획 후보를 확정하면 `Planned`로 전환합니다. |
| 개발·검증 | 별도 요청 | `Planned` baseline과 명시적 개발 요청을 입력으로 repository 구현과 검증을 수행합니다. |
| Live staging 확인 | 별도 권한 | Repository 후보가 검증된 뒤 실제 cluster mutation 권한을 받아 최소 smoke만 수행합니다. |

## 달성할 결과

- `GET /api/v1/car/models/{carModelId}`의 재사용 가능한 현재일 성공 결과를 Redis에서 읽습니다.
- PostgreSQL만 자동차 카탈로그의 authoritative source로 유지하며 Redis data는 언제든 폐기하고
  다시 채울 수 있습니다.
- Cold miss는 PostgreSQL을 한 번 읽어 Redis에 저장하고, warm hit는 같은 API 결과를 반환합니다.
- Redis가 비어 있거나 느리거나 사용할 수 없거나 잘못된 document를 반환해도 기존 DB 조회
  경로로 복구합니다.
- 같은 key의 동시 miss와 서로 다른 key의 source load를 각각 제한하고, DB 보호 한도 소진은
  `503 Service Unavailable`, `Retry-After`,
  `CATALOG_MODEL_DETAIL_CAPACITY_EXHAUSTED`로 구별합니다.
- 현재 ARM64 단일-node Kubernetes에 replica 1·무영속 Redis를 private하게 배치하고,
  application만 최소 권한으로 접근하게 합니다.
- Cache flag를 끄면 Redis command 없이 기존 DB-only 경로가 동작합니다.

## 현재 문제

- `GetCatalogModelDetailService`가 `CatalogModelDetailReadPort`를 직접 호출하며, jOOQ adapter는
  모델 상세 graph를 만들기 위해 요청마다 여러 query group을 다시 수행합니다.
- Application dependency와 설정에는 catalog Redis client, cache document, timeout·circuit,
  동시 miss 보호와 관련된 계약이 없습니다.
- Kubernetes desired state에는 catalog Redis Deployment·Service·보안 경계가 없습니다.
- 현재 cluster는 control plane과 workload가 제한된 memory의 한 node를 공유하므로 Redis
  process와 cache memory를 무제한으로 둘 수 없습니다.
- Production management endpoint는 `health`만 노출하고 readiness는 DB를 필수 dependency로
  봅니다. 보조 cache를 여기에 잘못 연결하면 정상 DB fallback Pod도 traffic에서 빠질 수
  있습니다.

## 범위

- 자동차 모델 상세 한 endpoint의 application cache port·adapter·orchestration
- 현재일 성공 결과의 versioned key·JSON envelope·최대 5분 TTL 계약
- PostgreSQL source loader의 transaction 경계
- Process-local per-key single-flight와 bounded source-load bulkhead
- Redis read/write timeout·circuit·no-retry·PostgreSQL fallback
- Spring Data Redis·Lettuce와 Resilience4j core dependency·configuration
- Kubernetes Redis replica 1의 Deployment·Service·memory·probe·보안 desired state
- Application과 Redis의 Service·ACL·Secret 연결
- 기능 flag 기본값 `false`, enable 순서와 DB-only 복구 경로
- Unit·실제 Redis integration·manifest render와 별도 권한의 최소 staging smoke
- 구현 결과가 바꾸는 기존 영구 SOT의 동기화

## 제외 범위

- k6 등 부하 도구, 성능 시험 script·runner·platform, RPS·p95·capacity 합격선과 전후 비교
- `redis-only` 같은 실험 전용 mode와 ablation
- 대규모 장애·chaos 시험 platform, Redis 정지·지연을 자동화하는 실험
- Staging 전용 Actuator metric endpoint, 관리자 JWT 보안 체인, dashboard·collector
- RDS·node qualification orchestration
- 이번 계획을 위한 별도 후속 work item이나 범용 Redis platform
- 다른 catalog endpoint, community 기능, session, rate limiter와 distributed lock
- Redis를 원본·backup으로 만드는 persistence, PVC, RDB와 AOF
- Sentinel·Cluster·replica, HPA·node autoscaling, multi-node와 production HA
- TLS·certificate lifecycle과 production topology 확정
- 현재 turn의 application·infrastructure 구현, image 배포와 live cluster 적용

제외한 시험 체계는 Redis 설치와 application 결합이 완료된 뒤 사용자가 별도 범위로 열 때만
다시 다룹니다. 이 경계는 D5·D6의 runtime 보호 기능이나 그 결정론적 검증을 제거하지 않습니다.

## 수용 기준

| ID | 결과 | 통과 증거 |
| --- | --- | --- |
| A1 | 기존 계약 보존 | Cache on·off에서 기존 성공과 not-found 응답이 같고 PostgreSQL 오류를 cache가 숨기지 않습니다. |
| A2 | 원본성 | Redis 전체 data를 지우거나 새 instance로 바꿔도 PostgreSQL에서 상세를 읽어 다시 채웁니다. |
| A3 | Cold·warm 경로 | 첫 조회는 cache miss 뒤 PostgreSQL과 cache write를 거치고, 다음 조회는 Redis document를 역직렬화해 같은 결과를 반환합니다. |
| A4 | Data 계약 | Key namespace·query identity·schema version이 분리되고, 현재일 성공 결과만 저장하며, TTL은 항상 300초 이하입니다. |
| A5 | Negative·오염 처분 | Not-found는 저장하지 않고, 호환되지 않거나 손상된 document는 성공으로 사용하지 않은 채 best-effort 삭제 후 DB로 복구합니다. |
| A6 | 동시성 보호 | 같은 process·key의 동시 miss는 하나의 source load를 공유하며 waiter가 별도 DB permit을 소비하지 않습니다. 서로 다른 key의 source load도 configured 상한을 넘지 않습니다. |
| A7 | 한도 소진 | In-flight key 또는 DB source-load permit과 bounded 대기가 소진되면 제한을 우회하지 않고 정해진 503 body·header를 반환합니다. 기존 429 계약은 바뀌지 않습니다. |
| A8 | Redis 실패 격리 | Connect·command가 bounded timeout 안에 끝나며 retry하지 않습니다. Read/write circuit은 분리되고 실패·open 상태는 DB fallback 또는 best-effort write 생략으로 끝납니다. |
| A9 | 시작·readiness 독립 | Redis를 사용할 수 없어도 application은 시작하고 DB 경로를 제공하며, application readiness에 Redis 상태를 포함하지 않습니다. |
| A10 | Kubernetes runtime | Redis Pod는 ARM64 digest의 선택한 8.2 patch로 기동하고 인증된 `PING`에 Ready가 됩니다. Replica 1, `Recreate`, `emptyDir`, RDB·AOF off가 render 결과에 나타납니다. |
| A11 | 자원 안전 | Redis는 `maxmemory`·`allkeys-lfu`와 container headroom을 갖고, Redis와 application resource request·limit는 node allocatable 안에서 schedulable해야 합니다. |
| A12 | 접근 통제 | Public Service가 없고 Calico NetworkPolicy가 application source를 제한합니다. Default user는 꺼지며 application·operation·probe ACL과 Secret 소비 경계가 분리되고 process 보안 설정이 적용됩니다. |
| A13 | 최소 진단 | Low-cardinality 결과·Redis operation·source-load·circuit 신호와 민감정보 없는 전이·요약 log만 추가합니다. Production management 노출은 `health` 그대로입니다. |
| A14 | 안전한 전환 | 기본 flag-off에서는 Redis command가 없고 DB-only입니다. Redis desired state와 Secret 준비 뒤에만 flag-on 하며, flag-off만으로 DB-only 상태를 복구합니다. |
| A15 | 범위 준수 | Repository에 제외 범위의 도구·script·endpoint·실험 mode가 생기지 않습니다. |

## 결정 색인

상세 비교와 선택 근거는 [Redis cache 설계](010-catalog-model-detail-redis-cache-design.md)가
소유합니다.

| 결정 | 확정값 | 실행 작업 |
| --- | --- | --- |
| D1 | 명시적 cache-aside와 catalog output port | T1·T2 |
| D2 | Miss 전용 PostgreSQL transactional source loader | T1·T2 |
| D3 | Versioned key와 adapter 전용 JSON envelope | T2 |
| D4 | 현재일 positive-only, 300초 이하 downward jitter, namespace invalidation | T2 |
| D5 | Process-local single-flight, bounded DB bulkhead, 503 계약 | T3 |
| D6 | 짧은 timeout, retry 없음, read/write circuit, DB fallback, Resilience4j core | T4 |
| D7 | Spring Data Redis·Lettuce, Redis 8.2, Deployment·Recreate, 무영속, RSALv2 | T1·T5 |
| D8 | `maxmemory`, `allkeys-lfu`, container headroom | T5 |
| D9 | ClusterIP, Calico NetworkPolicy, 역할별 ACL·Secret, non-root, staging 평문 | T5 |
| D10 | Readiness 분리와 최소 health·log·low-cardinality counter | T2~T5 |
| D12 | 기본 false GitOps flag-off, production topology 비승격 | T1·T5·T6 |

## 의미 단위 작업

### T1. Application 계약과 설정 경계

| 항목 | 내용 |
| --- | --- |
| 입력 | D1, D2, D6-B, D7-A, D12-A와 현재 `build.gradle`, `TimeConfiguration`, `CatalogModelController`, application profile 설정, catalog port·service 구조 |
| 변경 경계 | Spring Data Redis·기본 Lettuce와 `resilience4j-circuitbreaker` core dependency를 추가합니다. `carmunity.catalog.model-detail-cache.enabled=false`와 cache·동시성·circuit typed configuration을 정의하고, Redis endpoint·username·password·timeout은 Spring Data Redis 연결 설정으로 분리합니다. Catalog 내부에 cache output port와 PostgreSQL source loader를 두고 기존 read transaction을 miss 전용 loader로 이동합니다. Controller의 생략 `asOf`와 application cache admission이 `TimeConfiguration`의 동일한 주입 `Clock`으로 서울 현재일을 계산하게 합니다. |
| 지켜야 할 계약 | Flag-off에서는 Redis bean의 가용성과 무관하게 기존 DB-only 결과가 나와야 합니다. Domain에 cache payload를 넣지 않고 catalog module 내부 application port와 outbound adapter 경계를 유지합니다. Secret 값은 Git에 기록하지 않습니다. |
| 완료 oracle | Configuration binding·validation test, flag-off service test, 동일 `Clock` wiring test, architecture/Gradle dependency test와 기존 catalog query test가 통과합니다. |
| 의존 | 없음. T2~T4의 공유 계약 producer입니다. |

### T2. Cache document와 cache-aside 흐름

| 항목 | 내용 |
| --- | --- |
| 입력 | T1의 cache port·source loader, D3·D4와 `CatalogModelDetail` read model |
| 변경 경계 | Query identity가 드러나는 versioned key와 `schemaVersion`, `cachedAt`, `expiresAt`, detail을 가진 adapter 전용 JSON envelope를 구현합니다. 현재일 성공 결과만 저장하고 300초 이하의 downward jitter를 적용합니다. Miss는 source loader 결과를 best-effort 저장하고, not-found는 저장하지 않습니다. 호환 불가·손상 document는 best-effort 삭제 뒤 miss로 처리합니다. |
| 지켜야 할 계약 | Cache document는 web response·JPA entity·domain model이 아닙니다. Redis hit는 DB transaction을 열지 않으며 cache write 실패가 정상 DB 응답을 실패시키지 않습니다. |
| 완료 oracle | Clock과 jitter를 통제한 unit test가 key·version·현재일·TTL 상한·not-found·corrupt 처분을 검증합니다. 서울 자정 경계에서 controller가 만든 생략 `asOf`와 cache admission의 현재일이 동일한지도 검증합니다. 실제 Redis integration test가 JSON round-trip, TTL과 삭제·재생성을 검증하며 cold와 warm 경로의 API 결과가 같습니다. |
| 의존 | T1 뒤에 수행합니다. T3·T4가 이 orchestration을 감쌉니다. |

### T3. 동시 miss와 DB source-load 보호

| 항목 | 내용 |
| --- | --- |
| 입력 | T2 miss 흐름, D5, U1-A와 현재 RFC 9457 오류 생성 경계 |
| 변경 경계 | Process-local per-key single-flight를 두고 leader의 결과를 같은-key waiter가 공유합니다. In-flight key 수와 PostgreSQL source-load 동시성·대기 시간을 각각 bounded configuration으로 제한합니다. 한도 소진은 catalog capacity exception으로 만들고 503·`Retry-After`·project code로 변환합니다. 모델 상세 endpoint의 `@ApiResponse`에 503, `Retry-After` header와 RFC 9457 body/project `code` schema를 반영합니다. |
| 지켜야 할 계약 | Waiter는 별도 DB permit을 소비하거나 제한을 우회해 직접 DB를 읽지 않습니다. Shared future는 완료·예외·취소 모든 경로에서 제거됩니다. 기존 429 응답과 header 정책은 바꾸지 않습니다. |
| 완료 oracle | 통제된 executor/barrier test가 같은 key 하나의 source call, 서로 다른 key의 permit 상한, waiter 결과·예외 공유, map cleanup을 증명합니다. Web test가 exact status·header·body code와 기존 429 비회귀를 검증합니다. `OpenApiContractTest`는 모델 상세의 exact 503 response·header·schema를 요구해 annotation 누락을 실패로 만듭니다. |
| 의존 | T2 뒤에 수행합니다. T4 fallback과 결합한 뒤 최종 service 경로를 닫습니다. |

### T4. Redis timeout·circuit·fallback

| 항목 | 내용 |
| --- | --- |
| 입력 | T2 orchestration, T3 source-load 보호와 D6 |
| 변경 경계 | Lettuce connect·command timeout을 PostgreSQL fallback에 남길 request budget보다 작은 양수로 제한합니다. Cache operation에는 retry를 두지 않습니다. Resilience4j core의 read/write circuit을 adapter 안에서 분리하고 open일 때 Redis를 즉시 우회합니다. Read 실패는 T3의 보호된 DB path로, write 실패는 성공 응답과 분리된 best-effort 생략으로 끝냅니다. |
| 지켜야 할 계약 | Slow-call threshold는 완료 호출 분류용이고 실제 시간 상한은 timeout이 집행합니다. Breaker는 application liveness·readiness를 바꾸지 않습니다. Redis 오류가 공개 API 오류로 직접 전파되지 않습니다. |
| 완료 oracle | Fake clock·통제된 실패를 사용하는 unit test가 read/write 상태 분리, closed/open/half-open, no-retry와 즉시 bypass를 검증합니다. 실제 사용할 수 없는 Redis endpoint를 둔 integration test에서 application 시작, bounded DB fallback과 기존 API 결과를 확인합니다. |
| 의존 | T2·T3 뒤에 수행합니다. T5 application wiring이 같은 property 계약을 소비합니다. |

### T5. Kubernetes Redis와 application 연결

| 항목 | 내용 |
| --- | --- |
| 입력 | D7~D10·D12, T1·T4 configuration, 현재 `deploy/base`, `deploy/overlays/stg`와 AppProject allow-list |
| 변경 경계 | 구현 시점 Redis 8.2 최신 patch의 공식 multi-architecture image와 ARM64 digest를 확인해 고정합니다. Replica 1 Deployment·`Recreate`, ClusterIP Service, `emptyDir`, RDB·AOF off, `maxmemory`·`allkeys-lfu`, resource request·limit와 보안 context를 desired state에 추가합니다. Default user를 끄고 application·operation·probe ACL을 분리하며 Git 밖 Secret을 참조합니다. Calico NetworkPolicy와 bootstrap owner의 AppProject namespaced resource allow-list를 함께 변경하고 application Deployment가 Service와 application credential만 소비하게 합니다. |
| 지켜야 할 계약 | RSALv2 경로와 notice·image provenance를 보존합니다. Redis는 public ingress에 노출하지 않습니다. Password를 manifest·probe argument·log에 넣지 않습니다. Redis probe만 인증된 `PING`을 사용하고 application readiness에는 Redis를 추가하지 않습니다. TLS와 production 승격을 암묵적으로 포함하지 않습니다. |
| 완료 oracle | Kustomize render와 schema 검증에서 image digest·controller·storage·persistence off·probe·resources·security context·Service·NetworkPolicy·Secret reference가 확인됩니다. 정적 정책 test가 public exposure, default user와 plaintext secret을 거부합니다. |
| 의존 | T1의 property·Secret 계약을 소비합니다. T2~T4와 병렬 작성할 수 있지만 T6 전에는 하나의 후보로 통합합니다. |

### T6. 통합 검증, 최소 staging smoke와 SOT 동기화

| 항목 | 내용 |
| --- | --- |
| 입력 | T1~T5의 동결 후보, 현재 CI·GitOps·문서 owner, `docs/operations/540-application-environment-and-secret-change-runbook.md`의 전용 Procedure handoff와 AppProject one-time apply owner |
| 변경 경계 | Repository에서는 전체 Gradle check, 실제 Redis integration test, Kustomize render와 금지 범위 검색을 실행합니다. 구현 source가 바꾼 현재 계약을 기존 catalog·module map·tech stack·local development·deploy·REST owner에 동기화합니다. 공용 Secret runbook이 이 Redis ACL lifecycle을 실행할 수 없으므로 operations owner 아래 Redis 전용 activation·credential Procedure와 router entry를 구현 범위에 둡니다. 이 Procedure는 application·operation·probe credential 생성, server ACL hash와 application/probe plaintext Secret의 분리 적용, 같은 입력 재수렴, rotation, 부분 실패 재개, 값 비노출 검증과 flag-off 복구를 소유합니다. Repository 후보 검증 뒤 별도 live 권한이 생기면 `AppProject NetworkPolicy 허용 적용·확인 → 역할별 Secret 준비·검증 → Redis flag-off sync → 인증 PING → application Service·ACL 연결 → flag-on` 순서로 최소 staging smoke를 수행합니다. |
| 지켜야 할 계약 | 이 작업은 live apply 권한을 만들지 않습니다. AppProject manifest는 Application이 추적하는 overlay 밖의 bootstrap owner이므로 Redis sync 전에 별도로 적용·확인합니다. Smoke는 Pod Ready, application Ready, cold/warm 동일 응답, Redis key TTL, flag-off DB-only 복구만 확인합니다. 제외 범위의 도구·script·실험을 추가하지 않습니다. |
| 완료 oracle | `./gradlew check`, 실제 Redis integration, overlay render, AppProject contract와 문서 link 검증이 통과합니다. 전용 Procedure는 최초 생성·same-input 재실행·rotation·부분 실패 표와 secret 비노출 postcondition을 갖습니다. 별도 권한의 staging에서는 NetworkPolicy 허용, 역할별 Secret schema, Redis Pod Ready, application Ready, cold miss 뒤 key 생성, warm hit 동일 응답과 flag-off 복구의 직접 증거를 남깁니다. 실행하지 않은 live 확인은 완료로 보고하지 않습니다. |
| 의존 | T1~T5 뒤에 수행합니다. Repository 검증이 먼저이며 live staging은 그 뒤의 별도 승인 단계입니다. |

## 의존 관계

```text
T1 application 계약·설정
  -> T2 cache document·cache-aside
       -> T3 single-flight·DB bulkhead·503
            -> T4 timeout·circuit·fallback

T1 property·Secret 계약
  -> T5 Kubernetes Redis·application wiring

T2 + T3 + T4 + T5
  -> T6 repository 통합 검증·SOT 동기화
       -> 별도 live 권한이 있을 때만 최소 staging smoke
```

T5 manifest 작성은 T2~T4와 독립적으로 진행할 수 있지만, T1이 생산한 property·Secret 계약을
먼저 소비해야 합니다. T6 전에는 application과 infrastructure가 동일 endpoint·credential·flag
계약을 쓰는지 직렬로 대조합니다.

## 검증

| 검증 계층 | 확인 대상 | 실패 시 처분 |
| --- | --- | --- |
| 정적·구조 | Module 의존성, typed configuration, manifest render, Secret 비노출, public Redis 금지 | 관련 T로 돌아가 후보를 수정합니다. |
| Unit | Key·envelope·TTL·admission, single-flight·bulkhead, 503 mapping, circuit state | 해당 계약 producer인 T2·T3·T4를 수정합니다. |
| 실제 Redis integration | ACL 연결, JSON round-trip, TTL, corrupt 처분, cold/warm, unavailable fallback | Client·adapter·runtime 계약을 분리해 T2·T4·T5를 수정합니다. |
| Repository regression | 전체 `./gradlew check`, 기존 API·429·readiness 계약 | 비회귀가 닫힐 때까지 통합 후보를 동결하지 않습니다. |
| 최소 staging smoke | 인증 PING, Pod·application Ready, 실제 Service 연결, cold/warm, flag-off | 별도 live 권한 안에서만 수행하며 실패 시 flag-off 상태로 복구합니다. |
| 범위 감사 | 제외 범위 artifact 부재와 기존 문서 owner 동기화 | 범위를 넘은 변경을 후보에서 제거합니다. |

이 계획은 throughput, latency percentile이나 최대 처리량을 Redis 설치·결합 완료의 oracle로
사용하지 않습니다.

## 전환과 복구

1. Application artifact와 Redis desired state는 기능 flag 기본값 `false`로 먼저 준비합니다.
2. 별도 live 권한에서 bootstrap owner의 tracked AppProject manifest를 적용하고
   `networking.k8s.io/NetworkPolicy`만 추가됐는지 확인합니다.
3. Redis 전용 credential Procedure로 Git 밖 역할별 Secret을 생성·적용하고 key schema와
   consumer 분리를 값 노출 없이 확인합니다.
4. Redis desired state를 sync하고 인증된 `PING`에 Ready인지 확인합니다.
5. Application이 ClusterIP·application ACL로 연결할 수 있고 flag-off DB-only 경로가 유지되는지
   확인합니다.
6. Flag-on 뒤 cold request가 DB 결과를 저장하고 warm request가 같은 응답을 반환하는지
   확인합니다.
7. 이상이 있으면 flag를 `false`로 되돌려 Redis read/write를 중단합니다. PostgreSQL data는
   rollback하지 않으며 Redis key는 삭제 scan 대신 TTL 소멸 또는 workload 제거로 처분합니다.
8. Redis Deployment 제거는 flag-off와 application 안정 확인 뒤 별도 desired-state 변경으로
   수행합니다.

AppProject 권한만 적용된 뒤 실패하면 application이나 Secret 상태는 바뀌지 않았으므로 허용을
그대로 두고 3단계부터 재개합니다. Redis 도입을 중단하고 그 `NetworkPolicy` 허용을 소비하는
다른 manifest가 없을 때만 tracked allow-list를 이전 revision으로 되돌린 뒤 bootstrap owner로
재적용합니다. Secret 적용이 일부 끝났다면 삭제·새 값 추측 대신 같은 local source와 동일한
Procedure로 재수렴하며 flag는 계속 `false`로 유지합니다.

## 영구 SOT

구현이 완료된 뒤 실제 source와 일치하는 내용만 다음 owner에 반영합니다.

| 계약 | 기존 owner |
| --- | --- |
| Catalog module의 cache port·adapter와 read lifecycle | `docs/project/210-module-map.md`, catalog domain·architecture 문서 |
| Redis·Resilience4j version과 사용 목적 | `docs/tech-stack/100-current-stack.md`와 해당 index |
| Local Redis 설정 key와 실행 방법 | `.env.example`, `docs/development/100-local-development.md` |
| Kubernetes resource·GitOps 책임·Secret 경계 | `deploy/README.md`, infrastructure·GitOps 문서 |
| 503 오류와 `Retry-After` | `docs/architecture/230-rest-api-design.md` |
| Redis credential·활성화·복구 | `docs/operations/000-index.md` 아래 Redis 전용 Procedure; 공용 540은 발견·handoff만 유지 |

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
