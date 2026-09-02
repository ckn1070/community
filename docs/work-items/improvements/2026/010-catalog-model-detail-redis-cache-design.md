# 자동차 모델 상세 Redis cache 설계

## 상태

Proposed

## 계획 단계

해결 설계

## 현재 결론과 다음 행동

| 구분 | 현재 내용 |
| --- | --- |
| 현재 결론 | PostgreSQL을 원본으로 유지하고 catalog application이 명시적으로 Redis를 먼저 조회하는 cache-aside 후보를 선택했습니다. Cache hit에는 DB transaction이 없고, miss에서만 기존 다중 jOOQ 조회를 하나의 read-only transaction으로 실행합니다. 서울 기준 요청 처리 현재일의 조회만 cache에 admission합니다. |
| 닫힌 결과 | 대상 API 하나, 5분 freshness 상한, self-managed 단일 Redis instance, cache 비활성·활성 비교, 저장소 구현 뒤 별도 권한의 staging 실증이라는 [010 자동차 모델 상세 Redis cache 결과 정의](010-catalog-model-detail-redis-cache.md)는 닫혔습니다. 공개 API와 DB schema는 바꾸지 않습니다. |
| 남은 gate | 독립 discovery·closure를 통과했습니다. Exact D1~D12 후보에 대한 사용자 단계 확정만 남았으며, 확정 전에는 실행 계획이나 구현 입력으로 사용할 수 없습니다. |
| 현재 행동 주체 | 사용자가 D1~D12 전체를 검토하고 채택하거나 수정할 결정을 지정합니다. |
| 사용자 차례 | 응답은 `이 해결 설계를 확정하고 실행 계획을 진행해줘` 또는 `해결 설계에서 <결정>을 <값>으로 수정해줘`입니다. |
| 현재 행동 뒤 결과 | 사용자가 exact 후보를 채택하면 work item 본문만 `Proposed / 실행 계획`으로 전환해 구현·검증 단위를 작성합니다. 수정 요청이면 해당 결정과 직접 영향 범위를 해결 설계 단계에서 다시 검증합니다. |

## 문서 책임과 설계 입력

이 문서는 장기 복원해야 할 기술 결정, 대안, 목표 계약과 재검토 조건을 소유합니다. 구현
작업 순서, test 명령과 live 운영 명령은 소유하지 않습니다. 구현 뒤 실제 현재 기준은 catalog,
Spring Boot, Redis, Kubernetes, local development와 운영 runbook의 각 영구 SOT로
승격합니다.

현재 source는 다음 제약을 만듭니다.

- `GetCatalogModelDetailService`가 read-only transaction 안에서
  `CatalogModelDetailReadPort`를 호출하고, jOOQ adapter가 모델·연식·동력계통·판매 구성·변형
  graph를 여러 query group으로 조립합니다.
- `CatalogModelDetail`은 공개 상세를 표현하는 application read model이고 web response나
  JPA entity가 아닙니다. Catalog module은 `shared :: error` 외 다른 업무 module에 의존하지
  않습니다.
- Application은 현재 replica `1`이고 staging의 ARM64 Kubernetes control plane과 workload가
  live memory 4 GiB인 단일 node를 공유합니다. Application에도 아직 resource request·limit가
  없습니다.
- Production profile은 HTTP management endpoint로 `health`만 노출하고 readiness에는
  `readinessState,db`만 포함합니다. Redis·Prometheus collector·부하 시험 artifact는 없습니다.
- Catalog data의 현재 변경 경로는 Flyway migration이며 runtime 관리자 writer나 crawler가
  없습니다.

## 목표 구조

```text
공개 모델 상세 요청
  -> GetCatalogModelDetailService (transaction 없음)
       -> cache 비활성: CatalogModelDetailSourceLoader 직접 호출
       -> cache 활성: CatalogModelDetailCachePort
            -> hit: CatalogModelDetail 반환
            -> miss·cache 장애:
                 같은 key의 process-local single-flight
                   -> CatalogModelDetailSourceLoader
                        -> read-only transaction
                             -> CatalogModelDetailReadPort
                                  -> PostgreSQL jOOQ 조회 graph
                   -> 성공한 기존 모델만 Redis에 best-effort 저장
  -> 기존 Web response mapping과 HTTP 계약
```

Cache는 catalog의 새 업무 Context나 Domain Model이 아닙니다. `catalog` application이 기대하는
선택적 read 보조 능력을 output port로 표현하고 Redis 기술과 document mapping은 catalog의
outbound adapter에 격리합니다. PostgreSQL source loader를 별도 Spring bean으로 두어 proxy를
통과한 miss 경로에만 transaction이 열리게 합니다.

## 결정 요약

| 결정 | 선택 | 주된 이유 |
| --- | --- | --- |
| D1 Application 경계 | 명시적 cache-aside와 catalog 전용 output port | 장애·miss·관측·transaction 의미를 annotation 밖에서 직접 통제합니다. |
| D2 Transaction | Orchestrator는 transaction 없음, 별도 source loader만 read-only | Hit가 DB connection·transaction을 소비하지 않고 기존 miss 조회 묶음을 보존합니다. |
| D3 Key·document | Versioned key와 adapter 전용 JSON envelope | Query identity·schema 진화를 명시하고 Java native serialization을 배제합니다. |
| D4 Freshness·admission | 서울 기준 현재일의 성공 결과만 270~300초 TTL, 그 밖의 `asOf`·not-found는 우회 | 5분 상한을 넘지 않으면서 공개 입력의 무제한 key 증가와 동시 만료를 제한합니다. |
| D5 동시 miss | Process-local per-key single-flight, distributed lock 없음 | 현재 replica 1의 stampede를 작은 경계로 줄이고 미래 topology를 선점하지 않습니다. |
| D6 장애 격리 | 짧은 timeout, read/write circuit 분리, retry 없음, DB fallback | Redis 장애를 API 장애와 긴 지연으로 확대하지 않습니다. |
| D7 Redis runtime | Redis Open Source 8.2 extended line, 무영속 단일 Deployment | 지원 기간이 긴 line을 exact image로 고정하고 cache를 재생성 가능하게 유지합니다. |
| D8 자원·eviction | 명시적 request·limit, `maxmemory` headroom, `allkeys-lfu` | 단일 node memory를 무제한 소비하지 않고 hot detail을 우선 보존합니다. |
| D9 보안 | ClusterIP·Calico NetworkPolicy·ACL 3종·non-root, 현재 TLS 없음 | GitOps permission과 Secret lifecycle을 먼저 확장하고 public 노출을 차단합니다. |
| D10 관측 | 저 cardinality Micrometer metric과 기존 application port의 staging 전용 인증 endpoint | 효과·장애·다음 병목을 식별하되 production 노출과 key·value·credential을 늘리지 않습니다. |
| D11 부하 시험 | 외부 k6 OSS, 사전 고정 산식과 3회 교차 disabled/active 비교 | 사후 threshold 변경과 burst credit·실행 순서의 false pass를 차단합니다. |
| D12 전환·승격 | Request-path 자동 fallback과 GitOps flag-off rollout 분리 | 장애 우회와 운영자 rollback 시간을 혼동하지 않고 production HA를 별도 설계 gate로 남깁니다. |

## D1. Application cache 경계

### 대안

| 대안 | 장점 | 결함·비용 |
| --- | --- | --- |
| Spring Cache `@Cacheable` | 코드가 짧고 Spring Boot의 기본 cache metric을 사용할 수 있습니다. | Cache 장애 fallback, corrupt document 처분, local single-flight와 hit transaction 경계를 annotation 조합 뒤에 숨깁니다. |
| 명시적 cache-aside | Hit·miss·bypass·failure와 저장 시점을 application 흐름에서 분리하고 port로 Redis를 교체할 수 있습니다. | Orchestrator와 cache result 계약을 직접 유지해야 합니다. |
| jOOQ adapter 내부 cache | Persistence 호출부 변경이 작습니다. | PostgreSQL 조회와 Redis 장애·document 정책이 한 adapter에 섞이고 application이 cache 결과를 관찰할 수 없습니다. |

### 선택과 계약

명시적 cache-aside를 선택합니다. `CatalogModelDetailCachePort`는 query identity에 대한 hit 또는
non-hit와 성공 결과의 best-effort 저장 능력만 제공합니다. Disabled 구현은 Redis 연결을
사용하지 않고 bypass를 기록하며, enabled 구현만 Spring Data Redis의 Lettuce client를
소비합니다.

Redis exception과 cache document 오류는 adapter 경계에서 분류하고 외부 API 오류로 변환하지
않습니다. Programming defect까지 넓게 삼키지 않으며, 알려진 연결·timeout·command·직렬화
오류만 non-hit로 강등합니다. Web controller, response DTO와 public error contract는 그대로
유지합니다.

Disabled 결과는 Redis lookup뿐 아니라 single-flight도 통과하지 않고 source loader를 직접
호출합니다. 따라서 같은 artifact의 disabled 기준선이 cache 구현의 다른 최적화 효과를 섞지
않고 기존 PostgreSQL 경로를 나타냅니다.

## D2. Transaction과 source snapshot

현재 use case method의 `@Transactional(readOnly = true)`를 그대로 두면 cache hit도 DB
transaction interceptor를 통과합니다. 따라서 cache orchestration은 transaction이 없는
`GetCatalogModelDetailService`가 소유하고, 별도 `CatalogModelDetailSourceLoader` bean이 기존
`CatalogModelDetailReadPort` 호출에만 read-only transaction을 엽니다. 같은 객체의
self-invocation으로 transaction을 우회하지 않습니다.

Miss 한 번의 여러 jOOQ query group은 기존과 같이 한 transaction 경계에서 실행합니다. 현재
PostgreSQL isolation이나 query graph 의미를 이 작업에서 바꾸지 않습니다. Runtime catalog
writer가 생겨 statement 사이 snapshot 일관성이 새 요구가 되면 isolation·read model·active
invalidation을 함께 재설계합니다.

## D3. Cache key와 value document

Key 계약은 다음 논리 구조를 사용합니다.

```text
carmunity:catalog:model-detail:v1:<modelId>:<requestedModelYearId|representative>:<asOf>
```

- `modelId`, 요청 연식의 존재 여부와 `asOf`를 모두 포함해 서로 다른 조회 의미가 충돌하지
  않게 합니다.
- Controller가 생략된 `asOf`를 `Asia/Seoul`의 요청 처리 현재일로 해석한 뒤 만든
  `GetCatalogModelDetailQuery.asOf`를 유일한 날짜 identity로 사용합니다. 생략 요청과 명시한
  같은 현재일 요청은 같은 key로 수렴합니다.
- `representative`는 연식 미지정 요청이 기준일 대표 연식을 선택하는 query임을 나타냅니다.
- `v1`은 cache document나 의미 계약이 호환되지 않게 바뀔 때 증가시킵니다. Rollback 가능한
  additive 변경은 같은 version을 유지하고, incompatible 변경은 새 namespace로 전환합니다.
- Key와 UUID는 개인정보가 아니지만 요청별 log·metric label로 남기지 않습니다.

Value는 adapter 전용 `CatalogModelDetailCacheDocument` JSON envelope입니다. Envelope는 schema
version, query identity, 생성 시각과 상세 graph를 포함하고 adapter가 application read model로
명시적으로 mapping합니다. Key와 envelope identity 불일치, 알 수 없는 schema version, 필수
field 누락과 JSON decode 실패는 corrupt로 분류해 best-effort 삭제 후 DB를 읽습니다.

`StringRedisTemplate`의 UTF-8 key/value와 명시적 Jackson mapping을 사용합니다. Spring Data
Redis의 기본 Java native serialization과 Jackson default typing은 사용하지 않습니다. 한 entry가
1 MiB를 넘으면 저장하지 않고 결과만 반환하며, 실제 분포가 이 상한에 접근하면 document 분할
전에 API graph·memory·CPU 비용을 다시 측정합니다. 최초 범위에서는 압축과 부분 cache를
추가하지 않습니다.

## D4. Freshness와 invalidation

- Cache lookup·single-flight·write admission은 resolved `query.asOf()`가 같은 요청에서 주입한
  `Clock`의 `Asia/Seoul` 현재일과 일치할 때만 허용합니다. 과거·미래의 형식상 유효한 날짜는
  기존 API 의미와 PostgreSQL query를 그대로 유지하되 Redis를 읽거나 쓰지 않고 source
  loader로 직행합니다. 따라서 공개 `asOf`가 날짜별 positive key를 무제한 만들 수 없습니다.
- PostgreSQL에서 성공적으로 읽은 존재하는 현재일 상세만 저장합니다.
- Entry TTL은 요청마다 270~300초 범위에서 균등하게 선택합니다. Jitter를 기본 300초에서
  빼는 방향으로만 적용해 사용자가 확정한 5분 stale 상한을 넘지 않습니다.
- Not-found는 저장하지 않습니다. 공개 UUID의 고 cardinality invalid request와 현재일이 아닌
  성공 요청이 positive hot entry를 밀어내는 cache pollution을 피하고 기존 200·404 의미를
  그대로 유지합니다.
- Admission 우회는 raw 날짜나 UUID를 tag·log에 남기지 않고 low-cardinality
  `bypass-non-current-as-of`로만 관찰합니다. 현재일·과거·미래 경계와 다양한 날짜를 병렬로
  보내는 시험에서 Redis command가 0이고 기존 DB 결과가 보존되는지, 같은 부하 뒤 hot-set
  hit와 eviction이 악화되지 않는지 검증합니다.
- 현재 runtime writer가 없고 stale 5분을 허용했으므로 active invalidation, key scan과
  `FLUSHALL`을 정상 경로에 두지 않습니다. Catalog data migration 뒤 기존 값은 TTL 안에
  자연 소멸합니다.
- Document 의미가 바뀌면 namespace version을 올립니다. 새 writer·crawler·관리자 API가
  생기거나 5분보다 강한 read-after-write가 필요해지면 변경 event와 targeted invalidation을
  함께 설계합니다.

## D5. 동시 miss와 stampede 경계

Enabled 상태의 cache miss·Redis 장애에는 application process 안에서 query key별 single-flight를
적용합니다. 먼저 등록한 한 요청만 PostgreSQL을 읽고 같은 key의 나머지 요청은 그 결과를
공유합니다. In-flight entry는 성공·not-found·예외 모두에서 제거하며 동시 요청 수를 넘지 않는
명시적 상한을 둡니다. 상한 초과는 unbounded memory 대기 대신 기존 DB 경로로 우회하고 별도
metric으로 관찰합니다.

Disabled 기준선은 single-flight를 적용하지 않습니다. 이 보호는 cache가 켜진 상태에서 동시
만료·장애가 같은 hot key를 PostgreSQL에 중복 전파하는 것을 줄이는 cache 장애 계약입니다.

현재 application replica가 하나이므로 distributed lock은 추가하지 않습니다. Rolling overlap이나
미래 다중 replica에서는 pod마다 한 번씩 source load가 발생할 수 있습니다. Application replica
증가, HPA 또는 cache-enhanced traffic이 DB fallback capacity를 넘기기 전에는 distributed
single-flight, probabilistic refresh, load shedding과 DB capacity 가운데 필요한 계약을 다시
선택해야 합니다.

## D6. 장애 격리와 fallback

| 상태 | API 동작 | Cache 처분·관측 |
| --- | --- | --- |
| Cache 비활성 | Single-flight 없이 기존 PostgreSQL 경로를 읽음 | `disabled` |
| 정상 hit | Cache detail 반환, DB transaction 없음 | `hit` |
| 정상 miss·만료 | Single-flight로 PostgreSQL을 읽고 성공 결과 저장 | `miss`, `source-load` |
| Redis 연결·command timeout | Redis 결과를 기다리지 않고 PostgreSQL fallback | `error`, read circuit 반영 |
| Redis write 실패·memory 거부 | PostgreSQL 결과는 성공으로 반환 | `write-error`, write circuit 반영 |
| Corrupt document | Best-effort 삭제 후 PostgreSQL fallback | `corrupt` |
| Redis 재시작·빈 cache | Warm-up 전까지 PostgreSQL fallback | miss와 source-load 증가 |
| PostgreSQL 실패 | 기존 DB 오류 의미 유지 | Cache miss가 DB 장애를 숨기지 않음 |

Lettuce connect timeout은 200ms, command timeout은 100ms의 초기 안전 상한을 사용하고 staging
관측으로 더 짧게 조정할 수 있습니다. Cache operation에는 retry를 적용하지 않습니다. 같은
요청 안의 retry보다 authoritative PostgreSQL fallback이 정확하고 latency가 예측 가능합니다.

Resilience4j core `2.4.0`의 programmatic circuit breaker를 read와 write에 따로 둡니다. Read 장애가
반복되면 짧은 open 구간 동안 Redis 호출 자체를 생략하고, write-only memory 거부가 정상 hit까지
막지 않게 합니다. Spring annotation·AOP starter는 사용하지 않습니다. 현재 Spring Boot 4
지원 artifact의 BOM 공백을 application wiring에 들이지 않고 작은 상태 머신만 adapter 안에서
사용하기 위해서입니다. Breaker state는 liveness·readiness에 포함하지 않습니다.

초기 breaker 계약은 count window 20회, 계산 최소 10회, failure·slow-call rate 각각 50%,
slow-call 50ms, open 5초, half-open probe 1회입니다. 이 값은 command timeout보다 먼저 느린
상태를 감지하되 일시적인 한두 번의 지연으로 열리지 않게 합니다. Staging evidence로 값을
바꾸더라도 read/write 분리, retry 없음과 readiness 비연동 계약은 유지합니다.

## D7. Redis engine과 Kubernetes workload 형태

Redis Open Source `8.2` extended release line의 현재 patch `8.2.8`을 선택합니다. 실제 manifest에는
mutable tag가 아니라 검증한 multi-architecture image의 `linux/arm64` digest를 고정하며, 구현
전에 더 최신 security patch가 나왔으면 같은 `8.2` line 안에서 공식 release·ARM64 image를 다시
검증해 target을 갱신합니다. `8.2`는 단기 standard line보다 긴 지원 주기를 제공합니다.
Application은 Spring Boot가 관리하는 Spring Data Redis와 기본 Lettuce client를 사용합니다.

Redis는 replica `1`의 Kubernetes `Deployment`와 `ClusterIP` Service로 배치합니다. Cache data는
재생성 가능하고 stable network identity·volume을 복구할 요구가 없으므로 StatefulSet과 PVC를
사용하지 않습니다. Deployment strategy는 `Recreate`로 두어 단일 node rollout 때 두 Redis
process가 동시에 memory를 점유하지 않게 하며, 그 짧은 cache 공백은 application fallback이
흡수합니다.

RDB snapshot과 AOF를 모두 끄고 ephemeral `emptyDir`만 사용합니다. Redis process·Pod·node
재시작으로 cache가 사라지는 것은 정상 복구 흐름입니다. Sentinel, Cluster, replica와 backup은
현재 범위에 추가하지 않습니다.

Redis 8에서 선택 가능한 RSALv2·SSPLv1·AGPLv3 가운데 현재 staging은 OSI 승인 선택지인
AGPLv3를 준수 기준으로 고정합니다. Upstream Redis server와 공식 image를 수정하지 않고,
exact release source URL·image digest·upstream `LICENSE.txt`와 copyright notice를 SBOM·image
provenance에서 함께 보존합니다. 이 결정은 application 자체의 license를 판정하거나 Redis
수정물을 허용하는 근거가 아닙니다. Redis source 수정, Redis protocol·기능의 제3자 제공,
image 재배포 또는 production 상용 조건이 생기면 infrastructure owner가 배포 전에 법률·license
owner의 검토와 AGPLv3 source 제공 의무를 확인합니다. 확인 전에는 해당 변경을 진행하지
않습니다.

## D8. Memory, eviction과 node 자원

Dedicated Redis에는 `maxmemory`와 `allkeys-lfu`를 적용합니다. 모든 entry에 TTL이 있지만 hot
모델을 빈도 기반으로 보존하고, memory가 찼을 때 application write가 cache 때문에 실패하지
않게 cold key를 제거합니다. `maxmemory`는 Redis container memory limit의 최대 2/3로 두어
allocator fragmentation, client buffer와 process overhead에 최소 1/3을 남깁니다.

Redis와 application 모두 CPU·memory request·limit를 갖고 같은 disabled/active 비교에서 값을
바꾸지 않습니다. Exact 값은 live node allocatable, control-plane·Argo·application peak와
entry size·cardinality를 대조해 실행 계획에서 동결하며, 다음 조건을 동시에 만족하지 않으면
staging 활성화를 중단합니다.

- Node system·control-plane 여유를 뺀 뒤 application과 Redis request가 schedulable합니다.
- Redis `maxmemory`, non-evictable overhead와 container limit의 관계가 위 headroom을 지킵니다.
- OOM kill, sustained CPU throttling, node memory pressure와 기존 workload 퇴거가 없습니다.
- Eviction은 cache 용량 신호일 뿐 오류가 아니지만, rising eviction과 hit ratio·DB load 악화가
  함께 나타나면 memory 상향보다 key·entry 분포를 먼저 분석합니다.

## D9. Network, credential과 process 보안

- Redis Service는 public Ingress·NodePort·LoadBalancer 없이 namespace 내부 ClusterIP만
  제공합니다.
- Calico NetworkPolicy는 application Pod에서 Redis TCP port로 들어오는 traffic만 허용하고
  Redis의 불필요한 egress는 차단합니다. DNS나 외부 API가 필요하지 않은 server 특성을
  유지합니다.
- 현재 AppProject allow-list에는 `NetworkPolicy`가 없으므로 workload manifest보다 먼저
  `scripts/bootstrap/gitops/manifests/car-community-appproject.yaml`에
  `networking.k8s.io/NetworkPolicy`를 추가하고, 기존 GitOps bootstrap owner가 tracked YAML을
  one-time server-side apply한 뒤 permission 반영을 확인합니다. 이 선행 조건이 닫히기 전에는
  Redis workload sync를 시작하지 않습니다.
- Redis default user는 끄고 application, operation 진단, probe user를 분리합니다. Application
  user는 catalog model-detail key prefix와 client handshake, `GET`, TTL을 포함한 `SET`, corrupt
  처분용 `DEL`, `PING`의 검증된 최소 command만 사용합니다. 진단 user는 key value 조회 없이
  `INFO`·latency·memory 상태만 읽고, probe user는 `PING`만 허용합니다. `CONFIG`, `FLUSH*`,
  module·script·replication 관리 command는 허용하지 않습니다.
- Plaintext password는 Git에 두지 않습니다. 기존 application prerequisite bootstrap의 exact
  NUL-delimited 입력을 application·operation·probe password 3개만큼 확장하고, 다음 두 Secret을
  같은 credential generation으로 생성·검증합니다.
  - Application Secret: application username·plaintext password만 가지며 application Pod가
    explicit key reference로 소비합니다.
  - Redis server Secret: 세 user의 password hash가 들어간 ACL file과 probe username·plaintext
    password만 가지며 Redis Pod만 소비합니다. Operation plaintext password는 local 허용
    입력에만 있고 cluster에는 hash만 남습니다.
- Probe는 Secret key에서 `REDISCLI_AUTH` 환경변수로 password를 받고 고정 probe username으로
  `redis-cli PING`을 호출합니다. Pod spec command argument와 log에는 plaintext를 넣지 않습니다.
- 최초 생성은 Deployment보다 먼저 두 Secret schema·ACL hash를 검증합니다. Rotation은 먼저
  GitOps flag-off rollout을 완료한 뒤 같은 bootstrap operation으로 두 Secret을 갱신하고,
  Secret `resourceVersion`을 소비하는 exact Pod-template annotation으로 Redis와 application을
  순서대로 rollout합니다. Argo Application은 이 두 annotation path만 ignore하도록 확장합니다.
  인증 PING, 새 application credential 연결, 두 rollout의 Ready와 old Pod 종료가 모두 확인돼야
  rotation이 끝납니다. 실패 중에는 flag-off와 DB 경로를 유지합니다.
- 제거는 flag-off와 DB 기준선 수용, GitOps Redis workload·reference 제거 뒤 별도 bootstrap
  cleanup이 exact 두 Redis Secret만 삭제하는 순서입니다. 생성·rotation·제거의 script contract,
  test와 runbook을 함께 바꾸며 다른 application Secret을 재사용하거나 삭제하지 않습니다.
- Redis container도 `runAsNonRoot`, `allowPrivilegeEscalation: false`, read-only root filesystem,
  `RuntimeDefault` seccomp와 capability drop을 적용합니다. Health probe는 password를 command
  argument나 log에 넣지 않습니다.

현재는 single-tenant cluster의 같은 단일 node Pod network, NetworkPolicy와 ACL을 신뢰 경계로
두고 Redis TLS를 추가하지 않습니다. 다중 node, 신뢰하지 않는 workload, 외부 network hop 또는
production 승격에서는 TLS server identity·certificate rotation과 client trust를 먼저 설계하지
않고 현재 plaintext 구성을 그대로 승격할 수 없습니다.

## D10. Health, metric과 log

Redis가 보조 cache이므로 application startup, liveness와 readiness의 필수 dependency로 만들지
않습니다. Cache enabled 설정이 유효한지는 fail-fast로 검증하지만 실행 중 Redis health가
내려가도 readiness의 `readinessState,db`는 유지하고 circuit·fallback으로 요청을 처리합니다.
Redis Pod 자체의 startup·readiness와 liveness는 D9의 probe user와 `REDISCLI_AUTH`를 사용하는
인증된 `PING`으로 process 응답을 확인합니다.

Application metric은 다음 low-cardinality 신호를 소유합니다.

- Lookup 결과: `disabled`, `bypass-non-current-as-of`, `hit`, `miss`, `error`, `corrupt`,
  `circuit-open`
- Redis operation별 count·duration·outcome과 read/write circuit state transition
- PostgreSQL source-load, single-flight leader·shared waiter·상한 우회 count
- Serialized entry byte 분포와 cache 저장 생략 count

Metric tag와 log에 model UUID, key, cache value, credential과 request payload를 넣지 않습니다.
반복 요청마다 INFO log를 남기지 않고 circuit transition과 rate-limited 장애 요약만 구조화해
기록합니다.

현재 Kubernetes·ALB가 사용하는 application port의 `/actuator/health/readiness` 계약은 유지하고
production profile의 HTTP 노출도 `health`만 유지합니다. 별도 collector가 없는 현재 범위에서는
Prometheus registry·상시 monitoring stack·새 management port를 추가하지 않습니다. 대신 기존
application port에서 staging profile에만 진단용 `/actuator/metrics`를 노출하고 기존 최고 관리자
Bearer JWT를 요구하는 별도 보안 체인으로 보호합니다. 같은 public Ingress 경로에서 network상
도달할 수 있으므로 이를 내부 endpoint라고 부르지 않으며, production profile에서는 계속
노출하지 않습니다. 이는 production metric backend가 아니라 qualification 중 등록된 metric을
확인하는 제한된 진단 surface입니다.

Staging qualification은 이 진단 surface, Redis 진단 user의 `INFO`, RDS Database Insights·
Enhanced Monitoring과 node·container source를 같은 측정 구간에 맞춥니다. 상시 수집·alerting이
필요해지면 private management endpoint, collector·retention·access control을 별도 설계합니다.
외부 k6 runner는 공개 모델 상세 endpoint만 호출하고 관리자 JWT나 Redis credential을 받지
않습니다. 별도 관측 운영자가 최고 관리자 JWT와 Redis 진단 credential을 각각 필요한 관측에만
사용합니다.

## D11. 부하 시험과 비교 계약

부하 생성기는 target Kubernetes 단일 node 밖에서 실행하는 Grafana k6 OSS `2.2.0`을 선택합니다.
k6는 arrival-rate scenario, HTTP check, percentile·error threshold와 비정상 종료 code를 script로
보존할 수 있습니다. JVM 기반 Gatling은 현재 한 endpoint 비교에 build·runtime 결합이 더 크고,
JMeter는 GUI/XML 중심 운영 부담이 있으며, `wrk`·`hey`는 분포·단계·threshold 계약이 부족해
선택하지 않습니다. k6 image도 exact version·digest와 실행 architecture를 고정합니다.

비교 가능한 결과는 다음 identity를 모두 유지합니다.

- 같은 application artifact와 PostgreSQL data snapshot
- Primary 비교에는 같은 서울 현재일 `asOf`, model/model-year 요청 corpus, 요청 분포와 network
  진입점을 사용하고 과거·미래 `asOf` 우회는 별도 scenario로 분리
- 같은 application·Redis·node resource 설정
- Cache disabled 기준선에서도 Redis Pod를 실행하되 application은 연결하지 않아 node 점유를
  동일하게 유지
- 독립 warm-up과 측정 구간, cold·hot-key·realistic mixed workload의 구분
- Load generator CPU·network가 먼저 포화되지 않았다는 증거

Capacity 탐색과 도입 합격 판정을 분리합니다. 다음 규칙과 수치는 첫 enabled 측정 전에
repository scenario·threshold artifact로 고정하고 결과를 본 뒤 낮추지 않습니다.

`capacity(mode, i)`는 `mode ∈ {disabled, enabled}`, `i ∈ {1, 2, 3}`에 공통인 단 하나의 측정
계약입니다.

- Primary realistic mixed corpus로 1 RPS에서 시작해 5분 step마다 두 배로 올리고 첫 실패
  구간만 이분 탐색합니다. 각 step의 독립 warm-up은 5분, 측정은 10분입니다.
- 한 step의 절대 안정 envelope는 HTTP 실패율 `0.1%` 이하, p95 `500ms` 이하, p99 `1s` 이하,
  Hikari acquire timeout 0건·active connection 80% 미만, RDS connection 80% 미만·CPU p95 70%
  미만, Pod restart·OOM·지속 throttling·node pressure 0건입니다. 하나라도 넘으면 그 step은
  실패입니다.
- 각 `capacity(mode, i)` 값은 envelope를 통과한 최고 RPS입니다. 동일 절차를 mode별로 3회
  실행하고 `B = min(capacity(disabled, 1..3))`,
  `E = min(capacity(enabled, 1..3))`로 보수적으로 집계합니다. 시작 RPS·step·warm-up·측정 시간,
  반복 수와 집계 방식은 mode에 따라 바꿀 수 없습니다.
- 세 disabled capacity를 먼저 완료해 `B`와 raw 결과를 저장한 뒤 primary qualification RPS
  `Q = max(1, floor(0.8 × B))`를 자동 동결합니다. 그 뒤에만 enabled capacity와 qualification을
  실행하며, 사람은 enabled 결과를 보고 `B`, `Q`, `E` 산식이나 envelope를 바꾸지 않습니다.
- 동일 index의 disabled·enabled capacity는 비교 pair입니다. 세 capacity pair 전체가 같은
  `Asia/Seoul` 날짜 안에서 끝나고 모든 request의 고정 `asOf`가 그 날짜와 일치해야 합니다.
  날짜가 바뀌거나 admission 조건이 달라진 run은 해당 batch 전체를 무효화합니다.

Qualification은 같은 realistic mixed corpus를 고정 seed로 재생하며 `disabled → enabled`,
`enabled → disabled`, `disabled → enabled`의 세 pair를 실행합니다. 각 run은 같은 5분 warm-up과
10분 측정 구간을 사용하고 cache 상태, background deployment·migration 부재와 요청 corpus를
기록합니다. 한 pair는 같은 서울 날짜와 현재일 `asOf` 안에서 끝나야 하며 날짜가 바뀌면 그
pair를 무효화합니다. 모든 run이 `Q`와 위 절대 envelope를 통과해야 합니다.

EC2와 RDS credit 공정성은 capacity와 qualification의 모든 disabled·enabled 비교 pair에 같은
admission gate로 적용합니다.

- Pair 두 run의 각 resource별 `CPUCreditBalance` 시작값은 `min/max >= 0.95`이고 측정 중 어느
  쪽도 0에 도달하지 않아야 합니다. Instance type·credit mode도 같아야 합니다.
- 조건을 어긴 pair나 그 pair가 들어간 `B`·`E` aggregate는 폐기하고 credit 회복 뒤 같은
  mode·index·순서로 다시 실행합니다.
- 반복해서 동등한 credit·날짜 상태를 만들 수 없으면 별도 승인으로 capacity와 qualification의
  모든 run을 같은 fixed-performance instance에서 재실행하기 전에는 결과를 `unverifiable`로
  판정합니다.

세 qualification pair 중앙값에서 enabled p95는 disabled의 80% 이하이고 PostgreSQL
source-load/request는 50% 이하이어야 하며, `E`는 `1.5 × B` 이상이어야 합니다. 이 최소 개선
폭을 하나라도 충족하지 못하면 Redis 도입 효과는 불합격이며 hit ratio로 대체하지 않습니다.

Hit ratio 하나로 성공을 판정하지 않고 application, Redis, JVM, PostgreSQL connection·query,
node pressure를 같은 구간에서 함께 봅니다. Generator CPU·network 포화나 외부 Ingress 병목이
RPS 상한을 만들면 Redis 성공으로 판정하지 않고 원인을 분리할 때까지 `unverifiable`로 둡니다.

정상 비교 외에 empty cache, 동시 hot-key miss, entry eviction, Redis 정지·느린 응답·재시작과
flag-off rollback에서 API 의미·fallback capacity·회복을 확인합니다. Full staging 부하는 일반
CI gate가 아니라 비용·서비스 영향을 통제한 별도 live qualification이며, repository CI는
결정론적인 unit·integration·manifest·짧은 smoke 계약만 소유합니다.

## D12. Configuration, rollout과 production 승격

`carmunity.catalog.model-detail-cache.enabled`를 공개 기능 flag로 두고 기본값은 `false`로
fail-close합니다. TTL·jitter·entry 상한·single-flight 상한과 circuit 값은 검증되는 typed
configuration이고, Redis endpoint·username·password·timeout은 Spring Data Redis 연결
configuration으로 분리합니다. Flag가 꺼졌을 때 cache adapter는 Redis command를 실행하지
않습니다.

Staging 전환은 Redis와 같은 application artifact를 flag-off로 먼저 배치한 상태를 기준선으로
사용하고, resource·correctness가 확인된 뒤 Git-managed config를 flag-on으로 바꿉니다.

Redis 연결·timeout 장애는 운영자 변경을 기다리지 않고 D6의 bounded timeout·circuit가 같은
request path에서 PostgreSQL로 자동 우회합니다. 반면 잘못된 cache logic이나 수용 기준 실패의
운영자 rollback은 즉시 kill switch가 아니라 flag-off GitOps PR·merge, Argo reconciliation과
Deployment rollout 순서입니다. Target revision `Synced`·`Healthy`, 새 Pod `Ready`, old Pod 종료,
`disabled` metric 증가와 한 측정 구간의 Redis command 0이 모두 확인돼야 rollback이 끝납니다.
현재 staging은 이 전달 시간에 대한 보장 RTO가 없습니다. Sub-minute operator disable이 필요해지면
동적 flag store의 가용성·권한·감사 계약을 별도 설계하며 현재 Redis에 kill switch를 저장하지
않습니다.

PostgreSQL data나 cache migration은 rollback 대상이 아닙니다. Redis resource 제거는 traffic이
DB 기준선 capacity 안에 있고 flag-off가 확인된 뒤의 별도 처분입니다. Cache key version 변경은
old key를 scan·삭제하지 않고 TTL 소멸에 맡깁니다.

현재 단일 Redis Deployment에는 자동 scale이나 failover가 없습니다. Application HPA만 켜면
pod별 miss와 Redis connection 수가 늘 뿐 Redis 고가용성이나 capacity가 완성되지 않습니다.
다음 중 하나가 생기면 현재 구성을 설정 변경만으로 production에 승격하지 않고 topology와
운영 책임을 다시 설계합니다.

- Application replica 2개 이상, HPA 또는 다중 node
- Redis 장애 중에도 cache-enhanced peak traffic을 유지해야 하는 SLO
- Node 교체·AZ 장애의 자동 복구, 명시적 RTO·RPO 또는 무중단 Redis maintenance 요구
- Dataset·command rate가 단일 process memory·CPU를 지속 초과
- TLS, managed secret rotation, audit·상시 metric collector와 on-call 요구

재설계에서는 managed Redis와 self-managed Sentinel/Cluster의 비용·failure domain·upgrade·
backup·관측 책임을 새로 비교합니다. PostgreSQL authoritative source와 application cache port는
그 전환에서도 유지합니다.

## 도구 도입과 제거 판정

### Redis runtime

| 판정 축 | 현재 결론 |
| --- | --- |
| 근본 문제·증거 | 같은 모델 상세 요청이 매번 여러 PostgreSQL query group을 다시 실행하고, 현재는 전후 capacity·latency·DB 부하를 재현할 측정 artifact도 없습니다. |
| 완전한 경로 비교 | PostgreSQL query 최적화만 유지하는 경로, replica 1에 가장 단순한 in-process Caffeine을 두는 경로, 외부 Redis cache를 두는 경로를 비교했습니다. 현재 singleton 성능만 보면 Caffeine이 더 단순하지만 닫힌 결과가 Redis 도입과 미래 application replica가 공유할 수 있는 경계를 요구하므로 Redis를 선택합니다. |
| 독립 oracle | 같은 artifact의 disabled/active HTTP 결과·k6 latency와 application source-load, RDS query·connection, Redis memory·eviction, node pressure를 대조합니다. Redis 자체 hit 수치만으로 필요성을 입증하지 않습니다. |
| 오판·새 실패 표면 | Warm cache나 편향된 hot-key 분포는 효과를 과대평가하고, RDS·network·generator 변동은 효과를 가릴 수 있습니다. 고정 identity와 여러 source를 사용해 줄이되 단일 node failure domain, credential·upgrade·memory 운영은 새 비용으로 남습니다. |
| 실행자·실패 경계 | Application request의 선택적 adapter와 staging의 GitOps workload만 소비합니다. 장애는 짧은 timeout·circuit 뒤 DB fallback으로 제한하고 full staging 부하는 별도 live 권한에서만 실행합니다. |
| Owner·갱신·제거 | Catalog application owner가 client·document를, infrastructure owner가 image·resource·ACL을 소유합니다. 8.2 security patch와 Spring 호환성을 점검합니다. 순효과가 없거나 운영 비용이 크면 flag-off 후 Redis resource·client·document·test·문서를 함께 제거합니다. |

### k6 부하 생성기

| 판정 축 | 현재 결론 |
| --- | --- |
| 근본 문제·증거 | 현재 저장소에는 실제 HTTP arrival rate, percentile과 오류 threshold를 반복하는 부하 시험이 없습니다. Unit·integration test와 수동 요청은 capacity를 증명하지 못합니다. |
| 대안과 선택 | 수동 `curl`·`wrk`·`hey`, Gatling, JMeter와 k6를 비교했습니다. 한 endpoint의 HTTP scenario·threshold를 가장 작은 script와 독립 executable로 보존하는 k6를 선택합니다. |
| 독립 oracle | k6는 외부 HTTP 결과만 판정합니다. Application·Redis·RDS·node의 별도 source가 내부 병목과 generator 포화를 확인하므로 k6 단독 성공을 전체 성공으로 쓰지 않습니다. |
| 오판·새 실패 표면 | 비현실적 key 분포·warm-up, generator CPU·network 포화와 외부 network 변동이 false pass·fail을 만들 수 있습니다. 고정 corpus, 외부 generator 자원 증거와 반복 구간으로 제한합니다. |
| 실행자·실패 경계 | Repository CI는 결정론적인 syntax·짧은 smoke만, 승인된 staging qualification은 운영자가 외부 generator에서 실행합니다. Full load 실패는 application delivery 전체를 자동 차단하는 CI gate가 아닙니다. |
| Owner·갱신·제거 | Performance test artifact가 2.2.0 image digest와 scenario를 소유합니다. 조직 표준 load platform이 같은 oracle을 더 직접 제공하거나 이 endpoint가 사라지면 script·image·문서 흔적을 함께 제거합니다. |

### Resilience4j circuit breaker

| 판정 축 | 현재 결론 |
| --- | --- |
| 근본 문제·증거 | Lettuce timeout과 try/catch만으로는 Redis 장애 때 모든 요청이 timeout을 반복해 fallback latency와 client work를 증폭합니다. |
| 대안과 선택 | Retry는 authoritative DB fallback보다 느리고, 자체 상태 머신은 half-open·동시성·metric 결함 위험이 큽니다. Spring AOP starter 대신 작은 `resilience4j-circuitbreaker:2.4.0` core API만 adapter 내부에서 사용합니다. |
| 독립 oracle | 통제한 Redis timeout·거부 상태에서 call count, breaker transition, DB fallback과 API 결과를 client metric·실제 fault 양쪽에서 확인합니다. |
| 오판·새 실패 표면 | 민감한 threshold는 정상 Redis를 일시 우회하는 false open을, 둔한 threshold는 timeout 반복을 만듭니다. 분리 breaker와 bounded 값으로 제한하며 false open의 결과는 정확한 DB fallback입니다. |
| 실행자·실패 경계 | Redis adapter만 programmatic하게 호출하고 annotation·global AOP·readiness에 연결하지 않습니다. Library failure나 open은 cache bypass 이상으로 확대되지 않습니다. |
| Owner·갱신·제거 | Catalog Redis adapter가 version·configuration을 소유합니다. Lettuce나 향후 cache infrastructure가 동등한 검증된 short-circuit를 제공하거나 Redis를 제거하면 dependency·configuration·metric을 함께 제거합니다. |

## 목표 보존과 남은 위험

| 사용자 목표·제약 | 설계가 보존하는 방법 | 남은 위험·재검토 |
| --- | --- | --- |
| 큰 read 부하 | Hot detail 재사용, single-flight, LFU와 외부 부하 시험 | Unique-key miss·Redis 장애 시 capacity는 DB 기준선에 제한됩니다. |
| 정확성과 5분 freshness | Query identity key, 300초 이하 TTL, corrupt fallback | Runtime writer가 생기면 active invalidation이 필요합니다. |
| 장애 격리 | 짧은 timeout, circuit, retry 없음, readiness 분리 | Cache-enhanced peak 전체를 Redis 없이 보장하지는 않습니다. |
| 확장성과 유지보수성 | Catalog port·adapter, versioned document, DB source 분리 | 다중 replica 전에는 distributed concurrency를 재설계해야 합니다. |
| 단일 node 비용 경계 | 무영속 replica 1, request·limit, maxmemory headroom | Node 자체가 application·Redis 공통 failure domain입니다. |
| 보안 | 비공개 Service, NetworkPolicy, 최소 ACL, Secret, non-root | 현재 Pod network는 TLS로 암호화하지 않습니다. Production 승격 gate입니다. |
| 전후 비교 | 같은 artifact·자원·data와 disabled/active flag | Generator나 외부 RDS 변동이 크면 결과 구간을 다시 취득해야 합니다. |

## 관련 문서와 공식 근거

- **작업 결과 정의**: [010 자동차 모델 상세 Redis cache 도입](010-catalog-model-detail-redis-cache.md)
- **Catalog 업무 기준**: [130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md)
- **Application 경계**: [200 헥사고날 아키텍처](../../../architecture/200-hexagonal-architecture.md),
  [210 Spring Boot 모듈 구조](../../../architecture/210-spring-boot-module-structure.md),
  [220 경계와 매핑 규칙](../../../architecture/220-boundary-and-mapping-rules.md)
- **현재 runtime**: [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md),
  [150 Kubernetes cluster](../../../project/infrastructure/150-kubernetes-cluster.md),
  [220 GitOps Application Delivery](../../../project/infrastructure/220-gitops-application-delivery.md)
- **Spring 공식 근거**: [Spring Boot Redis](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.redis),
  [Spring Data RedisTemplate과 serializer](https://docs.spring.io/spring-data/redis/reference/redis/template.html),
  [Spring Boot metric](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- **Redis 공식 근거**: [지원 version 정책](https://redis.io/docs/latest/operate/oss_and_stack/install/version-mgmt/),
  [Persistence](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/),
  [Eviction](https://redis.io/docs/latest/develop/reference/eviction/),
  [Security와 ACL](https://redis.io/docs/latest/operate/oss_and_stack/management/security/),
  [License](https://redis.io/legal/licenses/)
- **Kubernetes 공식 근거**: [Workload controller](https://kubernetes.io/docs/concepts/workloads/controllers/),
  [Resource 관리](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/),
  [NetworkPolicy](https://kubernetes.io/docs/concepts/services-networking/network-policies/),
  [Probe](https://kubernetes.io/docs/concepts/workloads/pods/probes/),
  [Linux 보안 제약](https://kubernetes.io/docs/concepts/security/linux-kernel-security-constraints/)
- **부하 시험 공식 근거**: [k6 scenario](https://grafana.com/docs/k6/latest/using-k6/scenarios/),
  [k6 threshold](https://grafana.com/docs/k6/latest/using-k6/thresholds/),
  [k6 release](https://github.com/grafana/k6/releases)
- **Circuit breaker 공식 근거**: [Resilience4j circuit breaker](https://resilience4j.readme.io/docs/circuitbreaker),
  [Resilience4j release](https://github.com/resilience4j/resilience4j/releases)
