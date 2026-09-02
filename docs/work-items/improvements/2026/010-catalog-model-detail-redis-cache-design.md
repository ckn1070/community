# 자동차 모델 상세 Redis cache 설계

## 구현 결과와 설계 보존

- D1~D10·D12의 선택과 비교 근거는 [작업 본문](010-catalog-model-detail-redis-cache.md)의
  application cache·단일-node Redis·credential Procedure 구현 결과에 반영됐습니다.
- 구현·검증 결과와 별도 live 운영 경계는 작업 본문이 소유하며, 이 문서는 확정된 선택·배제 대안·
  trade-off와 재검토 조건만 보존합니다.
- 이후 이 계약을 바꿔야 하는 새 사용자 소유 결정이 생기면 영향을 받는 변경을 새 작업에서
  다시 설계합니다.

## 문서 책임과 설계 입력

이 문서는 장기 복원해야 할 기술 결정, 대안, 목표 계약과 재검토 조건을 소유합니다. 구현
작업 순서, test 명령과 live 운영 명령은 소유하지 않습니다. 구현된 현재 기준은 catalog,
Spring Boot, Redis, Kubernetes, local development와 운영 runbook의 각 영구 SOT가
소유합니다.

설계 당시 source는 다음 제약을 만들었습니다.

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
  `readinessState,db`만 포함합니다. Redis는 보조 dependency이므로 이 계약을 바꾸지 않습니다.
- Catalog data의 현재 변경 경로는 Flyway migration이며 runtime 관리자 writer나 crawler가
  없습니다.

## 비교 방법과 결정 기준

R0은 사용자가 이미 고정한 Redis 도입 범위이지 비교표가 아닙니다. D1~D10·D12는 그 입력 아래에서
**제어 위치, transaction, data, 장애, runtime, 운영 경계와 전환**을 차례로 결정하는 의존
관계입니다. 각 비교표에는 같은 질문에 답하고 같은 종료 상태를 만드는 대안만 둡니다.

### 목표를 어떤 기준으로 판정하는가

| 우선순위 | 달성할 목표 | 통과 기준 | 이 기준이 결정을 바꾸는 지점 |
| --- | --- | --- | --- |
| 1. 필수 | 정확성·원본성 | Cache 상태와 무관하게 기존 HTTP 의미를 보존하고 PostgreSQL만 원본으로 유지합니다. | 원본 이중화, stale 상한 초과, cache 오류의 API 전파가 있으면 탈락합니다. |
| 2. 필수 | 장애 격리·복구 | Redis timeout·오염·재시작에도 bounded 시간 안에 DB 경로로 복구합니다. | Redis를 필수 dependency로 만드는 안과 무제한 retry를 탈락시킵니다. |
| 3. 필수 | 단일 node 안전성·보안 | CPU·memory 상한, private network, 최소 credential과 secret 비노출을 보장합니다. | 무제한 memory, public 노출, 무인증 접근을 탈락시킵니다. |
| 4. 핵심 기능 | 설치·결합 완결성 | 실제 Redis와 연결해 cold miss·populate·warm hit·TTL·직렬화·fallback이 end-to-end로 동작합니다. | 설정만 존재하거나 mock에서만 성공하는 안을 탈락시킵니다. |
| 5. 핵심 증거 | 결정론적 검증 가능성 | Hit·miss·bypass·오염·한도 소진·circuit·DB fallback을 서로 구분해 자동 검증합니다. | 결과 원인을 분리할 수 없는 추상화나 관측 계약을 배제합니다. |
| 6. 최적화 | 유지보수성·확장성·비용 | 위 기준을 모두 통과한 안 중 책임이 응집되고 현재 replica 1에 불필요한 운영 요소가 가장 적은 안을 고릅니다. | 미래 HA를 미리 구축하지 않되 replica 증가 때 교체할 경계를 남깁니다. |

선택은 점수를 임의로 합산하지 않습니다. 1~3번 필수 기준을 하나라도 위반한 안을 먼저
탈락시키고, 남은 안에서 4~5번의 효과와 증거가 더 직접적인 안을 고른 뒤, 효과가 같으면
6번의 구현·운영 비용이 작은 안을 선택합니다.

### 설계 논의 반영

| 역할 | 반영 내용 |
| --- | --- |
| 사용자 의견 요약 | 기존 문서는 선택지의 차이, 같은 비교군으로 묶은 이유, 달성 목표와 결정 기준, 최종 추천 근거를 한 자리에서 비교할 수 없어 판단 자료로 사용할 수 없었습니다. |
| Agent 답변·판단 요약 | Redis 제품 선택, cache 흐름의 소유 위치, transaction 경계와 후속 구현 계약은 서로 다른 결정입니다. 같은 시작·종료 상태를 만드는 대안끼리만 묶고 모든 대안에 동일한 평가축을 적용해야 합니다. |
| 최종 반영 | R0은 고정 입력으로 격리하고 D1~D10·D12의 결정 질문과 의존 관계를 먼저 제시합니다. 각 비교군에는 대안별 결과 차이·감수 비용·탈락 이유·선택과 재검토 조건을 둡니다. Transaction, 동시성 보호, 실패 격리, workload controller, storage와 운영 전환처럼 서로 다른 질문은 별도 결정으로 분리합니다. |

### 무엇이 같은 비교군이고 무엇이 아닌가

| ID    | 하나의 결정 질문                                | 같은 비교군인 이유                                                              | 이 표에서 비교하지 않는 것                              |
| ----- | ---------------------------------------- | ----------------------------------------------------------------------- | -------------------------------------------- |
| R0    | Redis 도입에서 무엇이 이미 고정됐는가                    | 비교군이 아니라 D1~D10·D12가 공유하는 상위 입력입니다.                                      | Query 최적화·Caffeine은 별도 목적에 유효할 수 있지만 현재 Redis 구현 대안으로 비교하지 않습니다. |
| D1    | Redis hit·miss·장애 흐름을 어느 계층이 제어할 것인가     | 모두 Redis를 사용하되 흐름의 소유자만 다릅니다.                                           | Redis와 Caffeine의 제품 비교, DB transaction 위치    |
| D2    | 명시적 cache-aside 안에서 DB transaction을 어느 호출 구간에 열 것인가 | D1에서 선택한 흐름은 같고, hit가 transaction interceptor를 지나는지만 다릅니다. | `@Cacheable` advisor 순서와 bean 구조 |
| D3    | Cache key와 value를 어떤 호환 계약으로 저장할 것인가     | 모두 같은 상세 graph를 저장하되 충돌·진화·직렬화 위험을 다르게 부담합니다.                           | 어떤 요청을 저장할지와 TTL                             |
| D4-A  | 어떤 날짜의 성공 결과를 저장할 것인가                    | 같은 성공 응답 중 admission 범위만 다릅니다.                                          | Not-found와 invalidation 방식                   |
| D4-B  | Not-found 결과도 저장할 것인가                    | 같은 404 의미를 유지하면서 DB 재조회와 cache pollution을 다르게 부담합니다.                    | 성공 결과의 날짜 범위                                 |
| D4-C  | 현재 변경 경로에서 stale을 어떻게 끝낼 것인가             | 현재 runtime writer 부재로 TTL·namespace가 파생되는 고정 제약이며 비교군이 아닙니다.            | 미래 writer event 설계                           |
| D5-A  | 같은 key의 동시 miss를 어느 범위에서 합칠 것인가          | 모두 PostgreSQL 중복 조회를 줄이되 보호 범위와 coordination 비용이 다릅니다.                  | 서로 다른 key의 DB 동시성 |
| D5-B  | Single-flight가 수용 한계에 닿으면 DB 부하를 어떻게 제한할 것인가 | 모두 cache miss의 정확성을 유지하되 DB 동시성·대기 상한을 다르게 만듭니다. | 한도 소진 뒤 공개 오류는 D5-C |
| D5-C  | 공유 DB 보호 한도 소진을 어떤 HTTP 오류로 알릴 것인가 | 모두 같은 server-side capacity 소진에서 요청을 실패시키고 재시도를 안내하되 실패 책임의 의미가 다릅니다. | DB 동시성 상한과 client별 rate limit 정책 |
| D6-A  | Redis가 느리거나 실패할 때 요청을 어떻게 제한할 것인가        | 모두 DB fallback을 유지하되 반복 timeout·retry 부하를 다르게 만듭니다.                     | Circuit 구현 library |
| D6-B  | 선택한 circuit 동작을 어떤 구현 경계로 제공할 것인가 | 같은 read/write breaker 동작을 만들되 상태 머신·Spring 결합·검증 책임이 다릅니다. | Circuit 사용 여부 |
| D7-A  | 어떤 application client를 사용할 것인가           | 같은 Redis command 결과를 만드는 client 경계끼리 비교합니다.                             | Cache orchestration 방식                       |
| D7-B  | 어떤 Redis release line을 사용할 것인가           | 현재 필요한 command를 제공하는 지원 line끼리 비교합니다.                                   | Kubernetes workload 형태                       |
| D7-C  | 어떤 Kubernetes controller를 사용할 것인가       | 같은 ephemeral replica 1 Redis를 서로 다른 Pod identity·rollout semantics로 운영합니다. | PVC·RDB·AOF와 production HA |
| D7-D  | Cache data를 재시작 뒤 보존할 것인가              | 같은 controller에서 폐기 가능한 cache의 storage·persistence만 다르게 둡니다. | Controller 선택과 production HA |
| D7-E  | Redis 8 tri-license 중 어떤 준수 경로를 선택할 것인가 | 같은 unmodified Redis server를 사용할 수 있지만 copyleft·서비스 제한·OSI 상태가 다릅니다. | 실제 서비스·수정·재배포 조건의 법률 해석 |
| D8    | 제한된 memory에서 어떤 entry를 제거할 것인가           | 모두 memory 상한을 지키되 write 거부와 hot data 보존 결과가 다릅니다.                       | Pod request·limit의 exact 수치                  |
| D9-A  | Application·운영·probe 권한을 어떻게 격리할 것인가     | 같은 private Redis의 접근·command 권한 방식끼리 비교합니다.                             | Packet 암호화                                   |
| D9-B  | Pod 사이 전송을 어떤 수준으로 보호할 것인가               | 같은 client/server traffic의 인증·암호화 수준끼리 비교합니다.                            | NetworkPolicy·ACL의 권한 범위                     |
| D9-C  | 역할별 credential을 어떤 실행 경계에서 수렴할 것인가       | 같은 두 Secret·ACL 목표 상태를 만드는 수동·재사용·전용 실행 경계를 비교합니다.          | Flag 전환·rollout·기능 smoke                     |
| D10   | Redis를 필수 dependency로 만들지 않으면서 어떤 최소 진단을 남길 것인가 | 모두 서비스 결과를 관찰하되 readiness 결합·노출·운영 표면이 다릅니다. | 별도 시험·수집 platform |
| D12-A | 잘못된 cache logic을 운영자가 어떻게 disable할 것인가 | 같은 DB-only 종료 상태를 만드는 운영 경로끼리 비교합니다. | D6가 소유하는 request timeout·fallback |
| D12-B | Production topology를 지금 확정할 수 있는가        | 필수 SLO·예산·운영 입력 부재를 판정하는 gate이며 아직 기술 비교군이 아닙니다. | 현재 staging runtime 선택 |

### 결정 의존 관계

```text
R0 Redis 고정 입력
  -> D1 Redis 흐름의 소유자
       -> D2 DB transaction 경계
       -> D3 key·value 계약 -> D4-A admission -> D4-B negative cache -> D4-C stale 종료
       -> D5-A 동시 miss -> D5-B DB 부하 상한 -> D5-C 공개 오류 계약
       -> D6-A 장애 동작 -> D6-B circuit 구현
  -> D7-A client -> D7-B release line -> D7-C controller -> D7-D storage -> D7-E license
       -> D8 memory·eviction
       -> D9-A network·credential -> D9-B transport -> D9-C credential operation
  -> D10 최소 health·log·counter
  -> D6 request 우회 -> D12-A operator disable -> D12-B production gate
```

### 최종 선택을 한눈에 보는 판정

| ID  | 선택                                                                   | 이 선택으로 달성하는 것                                                    | 선택 때문에 감수하는 것                      | 결정적 근거                                                 |
| --- | -------------------------------------------------------------------- | ---------------------------------------------------------------- | ---------------------------------- | ------------------------------------------------------ |
| R0  | Redis 도입은 상위 고정 입력 | 사용자가 확정한 범위 안에서 Redis 설치·결합 방법에 집중 | 이 문서에서 Query 최적화·Caffeine 우열은 결론 내리지 않음 | Redis 선택은 현재 비교 결과가 아니라 사용자 결정 |
| D1  | 명시적 cache-aside와 catalog port | Hit·miss·오염·fallback·저장을 한 application 흐름에서 명시적으로 제어 | Annotation 방식보다 코드·test 증가 | 정확한 결과 분류와 장애 처분을 한 경계가 소유해야 함 |
| D2  | Miss 전용 transactional source loader | Hit는 transaction 경계를 지나지 않고 miss query graph의 transaction 경계를 유지 | Spring bean 하나 추가 | Cache 작업과 DB transaction을 분리하면서 기존 DB 읽기 일관성을 보존 |
| D3  | Versioned key와 명시적 JSON envelope                                     | Query 충돌 방지, schema 진화, 안전한 오염 판정                                | Mapping code와 schema version 관리    | Java 직렬화 결합과 불명확한 호환 실패를 제거                            |
| D4-A | 현재일 성공 결과만 admission | 날짜 key 폭증을 막고 현재 hot read에 집중 | 과거·미래 조회는 가속하지 않음 | 현재일 반복 조회가 첫 목표 |
| D4-B | Not-found는 저장하지 않음 | 공개 UUID의 negative cache pollution 방지 | 반복 404는 DB를 다시 조회 | 404 부하보다 positive hot set 보존을 우선 |
| D4-C | 300초 이하 TTL jitter + incompatible namespace 변경 | 5분 stale 상한과 동시 만료 완화 | 5분보다 빠른 변경 반영은 보장하지 않음 | Runtime writer가 없어 event invalidation producer가 없음 |
| D5-A | Process-local per-key single-flight | 현재 replica 1의 hot-key stampede 완화 | 다중 replica 전체 중복은 막지 못함 | 현재 범위에서 distributed coordination 없이 필요한 보호를 달성 |
| D5-B | 공유 future + bounded DB source bulkhead | 같은-key waiter를 별도 누적하지 않고 DB 동시성을 안전 예산 안에 제한 | 포화 시 bounded 대기 뒤 U1의 공개 의미 발생 | Direct DB 우회로 stampede를 다시 만들지 않음 |
| D5-C | `503 Service Unavailable` + `Retry-After` + `CATALOG_MODEL_DETAIL_CAPACITY_EXHAUSTED` | 일시적인 서버 처리 한계와 재시도 행동을 기존 요청 제한 오류와 구별 | 공개 API 오류 계약·문서·검증 추가 | U1-A를 HTTP 표준 의미와 기존 429 계약을 훼손하지 않고 구현 |
| D6-A | 짧은 timeout + read/write circuit + DB fallback, retry 없음 | 반복 Redis 장애의 지연·client work 제한 | Breaker 상태와 threshold 운영 | Redis 장애를 API 장애로 확대하지 않는 필수 기준 |
| D6-B | Resilience4j core programmatic API | Adapter 안에서 read/write 상태를 분리하고 검증된 상태 머신 사용 | Library dependency·upgrade 관리 | 자체 구현 오류와 global AOP 결합을 함께 피함 |
| D7-A | Spring Data Redis + Lettuce | Spring 관리 client 경계 유지 | Raw client 세밀 제어는 사용하지 않음 | 필요한 기능을 만족하는 최소 추가 관리 표면 |
| D7-B | Redis 8.2 extended 최신 patch | 2030-09-01까지 patch line 유지 | 최신 standard 기능은 늦게 수용 | 현재 기능보다 장기 patch 경로 우선 |
| D7-C | Deployment + `Recreate` | Stable identity 없이 replica 1 운영 | Restart 때 identity 유지 없음 | Ephemeral cache에는 Stateful identity가 불필요 |
| D7-D | `emptyDir`, RDB·AOF off | Cache를 원본처럼 복구하지 않음 | Restart 때 cache 유실 | PostgreSQL에서 완전 재생성 가능 |
| D7-E | RSALv2 | Unmodified Redis를 내부 cache로 사용하는 현재 조건에서 source 공개 의무 없이 단순한 준수 경로 선택 | Redis 기능 자체의 제3자 managed service 제공 제한과 notice 보존 | 사용자가 가장 제약이 적은 현재 사용 경로를 선택했고, 현재 서비스는 Redis 기능 자체를 판매하지 않음 |
| D8  | `maxmemory` + `allkeys-lfu` + container headroom                     | Memory 상한과 hot entry 보존                                          | Cold entry eviction                | Cache write보다 node 생존을 우선하는 필수 기준                      |
| D9-A | ClusterIP + NetworkPolicy + 역할별 ACL·Secret + non-root | Public 노출과 credential 침해 범위 축소 | ACL·Secret lifecycle 운영 증가 | 같은 cluster 안에서도 최소 권한이 필수 |
| D9-B | Staging 한정 ACL + 평문 cluster network | 현재 single-node에서 certificate 체계를 추가하지 않음 | Packet 암호화가 없고 production 승격 불가 | 현재 failure domain 대비 운영 비용, 명시적 재검토 gate |
| D9-C | NUL-delimited stdin을 받는 전용 remote operations entrypoint | 두 Secret·ACL을 값 비노출·fail-close로 재수렴 | Script·contract test·전달 inventory 유지 | Credential Procedure의 반복 조립과 부분 실패를 한 실행 owner가 집행 |
| D10 | Readiness 분리 + 최소 health·log·low-cardinality counter | Redis 실패가 Pod traffic 제외로 번지지 않으면서 설치·결합 결과를 구분 | 상시 dashboard·세밀한 장기 분석은 제공하지 않음 | 지금 필요한 것은 runtime 동작 확인이며 새 관리 endpoint는 불필요 |
| D12-A | GitOps flag-off | 감사 가능한 DB-only 전환 | Sub-minute disable 보장 없음 | 기존 delivery owner 재사용 |
| D12-B | Production 승격 보류 | 근거 없는 HA·비용 확정 방지 | 현재 production topology 미정 | SLO·RTO/RPO·예산·on-call 입력이 없음 |

## R0. Redis 도입은 현재 결정에서 다시 고르는 값이 아님

R0은 기술 대안 비교가 아닙니다. 사용자가 첫 적용 기술을 Redis로 고정했으므로 D1~D10·D12는
Redis를 안전하고 검증 가능하게 구현하는 방법만 결정합니다. Query 최적화와 Caffeine도 다른
조건에서는 유효하지만, PostgreSQL query를 줄이는 방식과 재사용 범위·일관성·운영 경계가
달라 현재 Redis 구현 후보와 같은 종료 상태를 만들지 않습니다.

| 구분 | 현재 판정 | 의미 |
| --- | --- | --- |
| 고정 요구 | 외부 Redis를 단일-node Kubernetes에 먼저 도입 | D1 이후는 이 제약 안에서 완전한 구현 경로를 선택합니다. |
| 검증할 결과 | 실제 Redis의 cold miss·populate·warm hit·TTL·직렬화·fallback이 end-to-end로 동작 | 설정 존재나 mock 성공만으로 설치·결합 완료를 선언하지 않습니다. |
| 유지 gate | 정확성·원본성·실패 격리·자원·보안과 flag-off 복구 수용 기준 통과 | Redis가 application에 안전하게 결합됐다는 근거가 됩니다. |
| 탈락 gate | 기존 API 의미 훼손, Redis가 필수 dependency가 됨, 자원·보안 계약 위반 | Flag-off 뒤 Redis resource를 제거하고 현재 DB-only 상태로 복구합니다. |

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

## D1. Application cache 경계

**결정 질문:** Redis를 사용한다는 상위 결정은 유지하면서, `cache 확인 → 필요하면 PostgreSQL
조회 → cache 저장` 흐름을 어느 계층이 소유해야 합니까?

세 대안은 정상 hit와 miss에서는 같은 HTTP 결과를 만들 수 있습니다. 차이는 속도 자체가
아니라 실패·동시성·관측 정책이 어디에 보이고 누가 유지하는가입니다.

| 비교 축 | Spring Cache `@Cacheable` | 명시적 cache-aside | jOOQ adapter 내부 cache |
| --- | --- | --- | --- |
| 흐름 소유자 | Spring cache interceptor와 `CacheManager` | Catalog application service | PostgreSQL jOOQ adapter |
| 정상 hit·miss | Annotation이 method 호출 생략·저장을 수행 | Application 코드가 lookup·source load·put을 순서대로 수행 | Adapter가 DB 호출 전후에 Redis를 처리 |
| 장애 fallback | `CacheErrorHandler` 등 확장 지점으로 구현 가능 | 알려진 Redis 오류를 cache result로 분류해 같은 흐름에서 DB로 전환 | Adapter 내부 예외 분기로 구현 |
| 오염 document 처분 | Custom cache 구현·serializer/error handler에 정책이 분산될 수 있음 | Adapter가 `corrupt`를 반환하고 삭제·fallback을 명시 | DB mapping과 cache document 처분이 같은 adapter에 결합 |
| 동시 miss | `sync=true`와 provider 동작에 의존하며 별도 상한 정책은 추가 구현 | Per-key single-flight·상한·우회를 직접 표현 | Adapter 내부 coordination으로 persistence 책임이 확대 |
| Transaction 관계 | Cache advice와 transaction advice 순서 또는 bean 분리가 필요 | Non-transactional orchestrator가 transactional loader를 명시 호출 | Adapter가 application transaction 안에서 호출되면 hit도 경계를 통과 |
| 안전한 비활성화 | Annotation과 provider 설정을 함께 검증 | Application 흐름 진입 전에 flag-off로 DB path를 직접 선택 | Adapter 내부 flag가 persistence 책임에 숨음 |
| 관측·test | 표준 추상화는 간단하지만 custom outcome이 늘수록 여러 확장 지점을 함께 검증 | Hit·miss·bypass·error·corrupt와 source-load를 application 계약으로 직접 검증 | Application 관점의 cache outcome 분리가 가장 어려움 |
| 유지 비용 | 기본 기능은 가장 작고 custom policy가 늘수록 설정·확장 코드 증가 | 처음부터 orchestration·result test가 필요 | 호출부는 작지만 adapter의 변경 이유와 통합 test가 커짐 |
| 현재 목표 판정 | 단순 key·TTL cache라면 최선이지만 현재 실패·측정 계약에는 간접적 | **모든 필수·핵심 기준을 직접 충족** | Hexagonal owner와 관측 기준에서 탈락 |

### 선택과 계약

**선택: 명시적 cache-aside.** `@Cacheable`이 잘못된 기술이어서가 아니라, 현재 수용 기준이
단순 hit·miss를 넘어 flag-off, 날짜 bypass, Redis 오류, corrupt document, single-flight
상한과 source-load를 서로 다른 결과로 검증하도록 요구하기 때문에 선택합니다. 이 요구가
단순 key·TTL cache로 줄어들면 코드가 더 작은 `@Cacheable`을 다시 우선 검토합니다.

`CatalogModelDetailCachePort`는 query identity에 대한 hit 또는 non-hit와 성공 결과의
best-effort 저장 능력만 제공합니다. 공개 설정은 flag-off의 DB-only 경로와 flag-on의 완성된
Redis 경로 두 상태만 제공합니다. Single-flight와 source-load 보호는 flag-on 경로의 일부이며
따로 끄는 실험용 mode를 만들지 않습니다.

Redis exception과 cache document 오류는 adapter 경계에서 분류하고 외부 API 오류로 변환하지
않습니다. Programming defect까지 넓게 삼키지 않으며, 알려진 연결·timeout·command·직렬화
오류만 non-hit로 강등합니다. Web controller, response DTO와 public error contract는 그대로
유지합니다.

Flag-off는 Redis command를 실행하지 않고 기존 PostgreSQL 결과를 그대로 반환해야 합니다.

## D2. Transaction 경계

**결정 질문:** D1에서 선택한 명시적 cache-aside 흐름 안에서 cache hit와 PostgreSQL miss 조회
중 어디까지 DB transaction interceptor를 통과시켜야 합니까? 이 결정은 D1의 하위 결정이며,
두 대안 모두 cache lookup을 application 코드 안에서 직접 수행합니다.

| 비교 축 | 명시적 orchestrator 전체에 transaction | PostgreSQL source loader에만 transaction |
| --- | --- | --- |
| Cache hit | Transaction interceptor와 context를 통과합니다. 실제 connection 획득은 transaction manager·lazy acquisition 설정에 따라 달라질 수 있습니다. | Transaction interceptor를 통과하지 않고 Redis 결과를 반환합니다. |
| Cache miss | 여러 jOOQ query를 한 read-only transaction에서 실행합니다. | 여러 jOOQ query를 한 read-only transaction에서 실행합니다. |
| 책임·비용 분리 | Hit에도 불필요한 transaction interceptor 경계가 남습니다. | Hit는 cache 경계만 지나고 miss의 DB 작업만 transaction 안에 둡니다. |
| 구조 비용 | 기존 annotation을 유지해 변경이 작습니다. | Proxy를 통과하는 별도 Spring bean이 하나 필요합니다. |
| 실패 위험 | Cache lookup이 method 내부에 있으므로 advisor 순서와 무관하게 interceptor를 통과합니다. | 같은 객체 self-invocation을 피해야 합니다. |
| 현재 목표 판정 | 정확성은 만족하지만 hit 경로 최소화·측정 기준에서 열세입니다. | **정확성과 hit 경로 분리를 함께 충족합니다.** |

**선택: PostgreSQL source loader에만 read-only transaction.** Cache hit에는 transaction
경계를 만들지 않으면서 miss의 기존 transaction 범위를 보존하는 것이 결정적 차이입니다.
PostgreSQL 기본 `READ COMMITTED`에서는 여러 SELECT가 동일 snapshot을 공유한다고 보장하지
않으므로 이 선택을 snapshot 일관성 근거로 사용하지 않습니다.

이 비교의 “transaction 통과”는 interceptor·context 경계를 뜻하며 실제 JDBC connection은
transaction manager와 lazy acquisition 설정에 따라 뒤늦게 획득될 수 있습니다. D1을
`@Cacheable`로 바꾼다면 cache advice와 transaction advice의 명시적 order 또는 bean 경계를
새로 비교해야 하며, 현재 D2 결론을 그대로 투영하지 않습니다.

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

**결정 질문:** 동일 query를 충돌 없이 식별하고 application 배포가 바뀌어도 안전하게 읽거나
폐기할 수 있도록 어떤 저장 형식을 사용해야 합니까?

| 비교 축                 | Framework 기본 key + Java native serialization    | Versioned key + application 객체 JSON                 | Versioned key + adapter 전용 JSON envelope                 |
| -------------------- | ----------------------------------------------- | --------------------------------------------------- | -------------------------------------------------------- |
| Query identity       | 기본 인자 조합에 의존해 의미가 key에서 드러나지 않습니다.              | 명시할 수 있습니다.                                         | 명시할 수 있고 document 안에서도 identity를 대조합니다.                  |
| Application model 결합 | Java class·serializer와 강하게 결합합니다.               | Application read model 변경이 cache schema 변경으로 직결됩니다. | Adapter mapping이 application model과 cache schema를 분리합니다. |
| 호환성·오염 판정            | Class/version mismatch가 runtime 역직렬화 오류로 드러납니다. | Key version은 구분하지만 payload 자체 version 판정은 약합니다.     | Key namespace와 envelope schema version을 모두 검사합니다.        |
| 보안·상호운용              | Native serialization 사용과 Java type 결합이 큽니다.     | 명시적 JSON으로 줄어듭니다.                                   | 명시적 JSON과 허용 field mapping으로 가장 좁습니다.                    |
| 구현 비용                | 가장 작습니다.                                        | 중간입니다.                                              | Mapper·envelope test가 필요해 가장 큽니다.                        |
| 현재 목표 판정             | 호환성·오염 복구 기준에서 탈락합니다.                           | 가능하지만 application 변경과 cache 계약이 과결합됩니다.             | **명시적 진화·오염 fallback 기준을 가장 직접 충족합니다.**                  |

**선택: Versioned key와 adapter 전용 JSON envelope.** Cache schema를 application 객체와
분리하고, 알 수 없는 version·identity 불일치를 DB fallback으로 안전하게 전환하기 위해서입니다.
Payload 측정에서 mapping CPU·entry 크기가 목표 latency·memory 예산을 넘거나 여러 consumer가
같은 document 계약을 공유해야 하면 application 객체 JSON, 압축·분할 또는 별도 schema 계약을
같은 축으로 다시 비교합니다.

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
Redis의 기본 Java native serialization과 Jackson default typing은 사용하지 않습니다. 한 entry의
저장 상한은 Redis memory 예산과 분리된 typed configuration으로 제한합니다. 상한을 넘으면
저장하지 않고 결과만 반환하며, 분할 전 API graph·memory·CPU 비용을
다시 측정합니다. 최초 범위에서는 압축과 부분 cache를 추가하지 않습니다.

## D4. Freshness와 invalidation

### D4-A. 성공 결과의 날짜 admission

**결정 질문:** 같은 성공 응답 중 어떤 `asOf` 범위를 cache에 넣어야 합니까? 두 후보는 기존
HTTP 의미와 5분 TTL을 같이 유지하고 저장 날짜 범위만 다릅니다.

| 비교 축 | 모든 유효 `asOf` 성공 결과 | 서울 기준 현재일 성공 결과만 |
| --- | --- | --- |
| 반복 현재일 read | 가속합니다. | 가속합니다. |
| 과거·미래 read | 가속합니다. | PostgreSQL로 직행합니다. |
| Key cardinality | 공개 날짜 수만큼 늘어날 수 있습니다. | Model·model-year의 현재 hot set으로 제한합니다. |
| 현재 목표 판정 | 범위를 넘는 cache pollution 비용이 큽니다. | **첫 대상인 현재 카탈로그 반복 read를 최소 key 범위로 충족합니다.** |

**선택: 서울 기준 현재일 성공 결과만 admission.** 과거·미래 조회를 가속하지 않는 비용을
감수하고 bounded hot set을 우선합니다. 실제 트래픽에서 과거·미래 조회가 독립 병목으로
확인되면 해당 분포와 cardinality를 근거로 다시 비교합니다.

### D4-B. Negative cache

**결정 질문:** 존재하지 않는 model의 404 결과를 Redis에 저장해야 합니까? 두 후보는 같은
404 HTTP 의미를 만들며 반복 DB 조회와 공개 UUID pollution만 다릅니다.

| 비교 축 | Not-found도 짧게 저장 | Not-found는 저장하지 않음 |
| --- | --- | --- |
| 반복 404 DB 부하 | 줄입니다. | 요청마다 DB를 조회합니다. |
| 공개 UUID 공격·오입력 | Negative key가 대량 생성될 수 있습니다. | Redis hot set을 오염시키지 않습니다. |
| Invalidation | 모델이 새로 생기면 negative TTL까지 404가 남습니다. | 새 모델을 즉시 DB에서 찾습니다. |
| 현재 목표 판정 | 404 부하 증거 없이 공격 표면과 stale 의미를 늘립니다. | **Positive hot set과 정확한 신규 반영을 우선합니다.** |

**선택: Not-found는 저장하지 않습니다.** 반복 404가 실제 DB 병목으로 확인되고 UUID별
rate limit·짧은 negative TTL로 pollution을 제한할 수 있을 때 다시 비교합니다.

### D4-C. Stale 종료는 현재 source에서 파생

현재 catalog 변경 경로는 Flyway뿐이고 runtime writer·event producer가 없습니다. 따라서
active invalidation을 선택지로 꾸미지 않고 다음을 파생 계약으로 둡니다.

| 고정 입력             | 파생 계약                                            | 재검토 조건                    |
| ----------------- | ------------------------------------------------ | ------------------------- |
| 최대 stale 5분       | TTL은 300초 상한을 넘지 않는 하향 jitter                    | 더 강한 read-after-write 요구  |
| Runtime writer 없음 | 정상 경로에 event invalidation·key scan·`FLUSHALL` 없음 | Writer·crawler·관리자 API 추가 |
| Document 의미 변경 가능 | 호환되지 않으면 key namespace version 증가                | 호환 migration 요구           |

- Cache lookup·single-flight·write admission은 resolved `query.asOf()`가 같은 요청에서 주입한
  `Clock`의 `Asia/Seoul` 현재일과 일치할 때만 허용합니다. 과거·미래의 형식상 유효한 날짜는
  기존 API 의미와 PostgreSQL query를 그대로 유지하되 Redis를 읽거나 쓰지 않고 source
  loader로 직행합니다. 따라서 공개 `asOf`가 날짜별 positive key를 무제한 만들 수 없습니다.
- PostgreSQL에서 성공적으로 읽은 존재하는 현재일 상세만 저장합니다.
- Entry TTL은 300초에서 빼는 방향의 jitter만 적용해 사용자가 확정한 5분 stale 상한을
  넘지 않습니다. Exact 폭과 분포는 구현에서 typed configuration으로 두고 clock을 통제한
  test로 항상 300초 이하인지 검증합니다.
- Not-found는 저장하지 않아 기존 404 의미는 PostgreSQL source에서 유지합니다.
- Admission 우회는 raw 날짜나 UUID를 tag·log에 남기지 않고 low-cardinality
  `bypass-non-current-as-of`로만 관찰합니다. 현재일·과거·미래 경계의 결정론적 test에서
  우회 요청의 Redis command가 0이고 기존 DB 결과가 보존되는지 검증합니다.
- Catalog data migration 뒤 기존 값은 TTL 안에 자연 소멸합니다. 새 writer·crawler·관리자
  API가 생기면 변경 event와 targeted invalidation을 별도 결정으로 비교합니다.

## D5. 동시 miss와 stampede 경계

### D5-A. 같은 key의 source load 결합

**결정 질문:** 같은 key가 동시에 miss일 때 몇 번의 PostgreSQL source load를 허용해야
합니까?

| 비교 축 | 제어 없음 | Process-local per-key single-flight | Redis distributed lock·coordination |
| --- | --- | --- | --- |
| Replica 1의 중복 load | 요청 수만큼 발생할 수 있습니다. | Process 안에서 leader 한 번으로 합칩니다. | Cluster 전체에서 한 번에 가깝게 합칠 수 있습니다. |
| 다중 replica 보호 | 없습니다. | Pod마다 한 번씩 발생할 수 있습니다. | Replica 사이까지 보호합니다. |
| Redis 장애 시 | DB stampede가 그대로 전파됩니다. | Process 안의 중복은 계속 줄일 수 있습니다. | Lock 장애·lease·owner 상실이라는 새 실패가 생깁니다. |
| Memory·대기 상한 | 별도 상태가 없습니다. | In-flight map·waiter 상한을 직접 둡니다. | Lock TTL·대기·fencing 정책이 필요합니다. |
| 현재 비용 | 가장 작지만 수용 기준을 충족하지 못합니다. | 현재 replica 1에 필요한 최소 비용입니다. | 현재 범위에 과도합니다. |
| 현재 목표 판정 | Hot-key stampede 기준에서 탈락합니다. | **현재 topology의 목표를 충족합니다.** | 미래 topology를 선점해 유지보수성 기준에서 열세입니다. |

**선택: Process-local per-key single-flight.** 현재 replica 1에서 실제로 존재하는 중복만
제거하며, replica 2 이상이 되면 distributed coordination·refresh·load shedding·DB capacity를
같은 기준으로 다시 비교합니다.

Flag-on 상태의 cache miss·Redis 장애에는 application process 안에서 query key별
single-flight를 적용합니다. 먼저 등록한 한 요청만 PostgreSQL을 읽고 같은 key의 나머지 요청은
별도 waiter collection 없이 하나의 shared future 결과를 기다립니다. In-flight entry는
성공·not-found·예외 모두에서 제거합니다.

현재 application replica가 하나이므로 distributed lock은 추가하지 않습니다. Rolling overlap이나
미래 다중 replica에서는 pod마다 한 번씩 source load가 발생할 수 있습니다. Application replica
증가, HPA 또는 cache-enhanced traffic이 DB fallback capacity를 넘기기 전에는 distributed
single-flight, probabilistic refresh, load shedding과 DB capacity 가운데 필요한 계약을 다시
선택해야 합니다.

### D5-B. In-flight 포화와 DB source-load 상한

**결정 질문:** 동시에 들어온 서로 다른 key가 in-flight 한계에 닿았을 때 정확성을 유지하면서
DB·대기 memory를 어떻게 제한해야 합니까?

| 비교 축 | 초과 요청마다 DB 직접 조회 | 상한 없이 single-flight entry 생성 | Bounded in-flight key + DB source bulkhead |
| --- | --- | --- | --- |
| 같은 key stampede | 초과 요청이 다시 중복 DB 조회를 만듭니다. | 막습니다. | Shared future가 있는 key는 계속 합칩니다. |
| 서로 다른 key DB 동시성 | Application 요청 수까지 늘 수 있습니다. | 고유 key 수까지 늘 수 있습니다. | Cache source-load permit 이하로 제한합니다. |
| 대기·memory | DB pool 앞 대기가 늘어납니다. | In-flight map이 무제한 증가합니다. | Map·DB 실행·대기 시간이 모두 bounded입니다. |
| 포화 시 결과 | DB가 버티면 정확하지만 장애 증폭이 큽니다. | 늦게 resource 고갈로 실패할 수 있습니다. | Permit을 bounded 시간 기다린 뒤 U1에서 선택한 공개 결과로 종료합니다. |
| 현재 목표 판정 | Single-flight 보호를 우회해 탈락합니다. | Memory 상한이 없어 탈락합니다. | **DB 동시성과 대기 memory를 예측 가능한 상한 안에 둡니다.** |

**선택: bounded in-flight key와 DB source-load bulkhead.** 같은-key 요청은 shared future에
합류시키고 leader와 새 key만 DB permit을 소비합니다. In-flight key 수, DB permit과 대기 시간은
Hikari pool·request timeout보다 작은 안전 예산의 typed configuration으로 제한합니다.
현재 값은 application 설정과 [Local 개발](../../../development/100-local-development.md)이
소유합니다. 포화 요청을 DB로 직접 우회하지 않아 stampede를 다시 만들지 않는 대신,
capacity를 넘는 요청은 bounded 대기 뒤 D5-C의 공개 오류로 실패합니다. 별도 오류와 재시도
안내는 사용자가 선택한 U1-A가 소유하고, 구체적인 HTTP status·header·error code는 D5-C가 소유합니다.
DB connection pool·request timeout·memory 측정으로 bulkhead 없이도 안전 상한이 이미 보장되거나,
다중 replica·외부 admission control이 source-load 상한을 소유하게 되면 이 결정을 다시 엽니다.

### D5-C. DB 보호 한도 소진의 HTTP 계약

**결정 질문:** D5-B의 DB source-load permit과 최대 대기 시간이 모두 소진된 같은 상황을
어떤 HTTP 오류로 반환해야 U1-A의 일시 과부하·재시도 의미를 정확히 전달할 수 있습니까?
두 후보는 같은 요청을 실패시키고 `Retry-After`를 제공할 수 있지만 실패 책임의 의미가 다릅니다.

| 비교 축 | `429 Too Many Requests` | `503 Service Unavailable` |
| --- | --- | --- |
| 표준 의미 | 해당 client가 일정 시간에 너무 많은 요청을 보냈다는 요청 빈도 제한입니다. | 서버가 일시적인 과부하로 요청을 처리할 수 없다는 가용성 실패입니다. |
| Client 행동 | 자신의 요청 속도를 낮추는 근거가 됩니다. | 서버가 제시한 시간 뒤 일시 실패를 재시도하는 근거가 됩니다. |
| 기존 계약과의 충돌 | 현재 community rate limit의 429와 같은 의미가 되어 client 책임과 server DB capacity를 혼동합니다. | 기존 429를 유지하면서 catalog server capacity exhaustion을 별도로 분류합니다. |
| `Retry-After` | 사용할 수 있지만 rate-limit 정책의 재시도 시점으로 해석됩니다. | RFC 9110이 일시적 과부하의 예상 회복 시간을 알리는 용도로 정의합니다. |
| 관측·운영 | Client rate limit과 합산돼 원인별 capacity 판단이 흐려집니다. | 전용 오류 code·metric으로 DB 보호 동작과 일반 5xx를 구별할 수 있습니다. |
| 현재 목표 판정 | 요청자별 quota가 아닌 공유 DB 처리 한계이므로 실제 실패 책임과 맞지 않습니다. | **U1-A의 일시 서버 과부하와 재시도 의미를 그대로 표현합니다.** |

**선택: `503 Service Unavailable`, `Retry-After`, `CATALOG_MODEL_DETAIL_CAPACITY_EXHAUSTED`.** 현재 보호 한도는
특정 client의 요청량을 판정하는 rate limiter가 아니라 모든 요청이 공유하는 server-side DB
source capacity이므로 503을 사용합니다. 응답 body는 RFC 9457 `ProblemDetail` 형식을 유지하고,
프로젝트 확장 field인 `code`에 위 catalog 전용 literal을 넣습니다. RFC 9457이 이 code를
정의하는 것은 아닙니다.

`Retry-After`는 delta-seconds로 제공합니다. 초기값은 현재 production Hikari 연결 획득 상한
`1500ms`를 초 단위로 올림한 `2`초로 두고, D5-B의 더 짧은 permit 대기 상한 때문에 이 값이
줄어들지 않게 합니다. 이 값은 cache의 짧은 대기 상한이 아니라 기존 DB 연결 획득 상한을
기준으로 선택한 재시도 안내입니다.

`ApiErrorResponseFactory`는 RFC 9457 body와 project `code`를 만들고,
`ApiExceptionHandler`는 명시적으로 retry-after를 소유한 catalog capacity 503에만 `2`초를
붙입니다. 기존 429의 `Retry-After: 1`과 다른 503 정책은 바꾸지 않습니다.
[REST API 설계](../../../architecture/230-rest-api-design.md)의 실패 응답 표에 이 503 의미와
재시도 계약을 영구 기준으로 승격했습니다.

공유 source capacity가 아니라 client·credential별 quota가 실패 원인이 되면 429를 다시
비교합니다. 외부 gateway가 같은 capacity admission과 공개 오류를 소유하게 되면 application
계약의 중복 여부를 다시 검토합니다.

## D6. 장애 격리와 fallback

### D6-A. Redis 장애 동작

**결정 질문:** Redis가 느리거나 실패할 때 기존 API를 유지하면서 반복 요청의 추가 지연과
client work를 어떻게 제한해야 합니까?

| 비교 축 | Timeout 뒤 DB fallback | Redis retry 뒤 DB fallback | 짧은 timeout + circuit + 즉시 DB fallback |
| --- | --- | --- | --- |
| 한 요청의 정확성 | 충족합니다. | 충족합니다. | 충족합니다. |
| 한 요청의 추가 지연 | Timeout 한 번만큼 발생합니다. | Retry 횟수만큼 늘어납니다. | Closed 상태에서는 timeout 한 번, open 상태에서는 Redis를 즉시 우회합니다. |
| 장애 지속 중 Redis call | 모든 요청이 계속 호출합니다. | 모든 요청이 더 많이 호출합니다. | Circuit open 동안 호출을 제한합니다. |
| PostgreSQL fallback | 즉시 실행합니다. | Retry가 끝난 뒤 실행합니다. | Redis를 건너뛴 뒤 즉시 실행합니다. |
| 상태·운영 비용 | 가장 작습니다. | Retry 정책이 추가됩니다. | Breaker 상태·threshold·metric 관리가 필요합니다. |
| 현재 목표 판정 | 짧은 장애에는 충분하지만 지속 장애의 증폭을 제한하지 못합니다. | Latency·work 증폭 때문에 탈락합니다. | **지속 장애 격리와 bounded latency를 함께 충족합니다.** |

**선택: 짧은 timeout, read/write circuit 분리, retry 없음, PostgreSQL fallback.** Circuit
복잡성은 감수하되 Redis 장애가 이어질 때 모든 요청이 같은 timeout을 반복하는 비용을
제한합니다.
결정론적 실패 test에서 timeout-only가 같은 bounded call 결과를 더 단순하게 충족하거나,
Redis가 authoritative dependency로 바뀌어 DB fallback이 정확하지 않게 되면 이 결정을 다시
비교합니다.

| 상태 | API 동작 | Cache 처분·관측 |
| --- | --- | --- |
| Flag-off | Single-flight 없이 기존 PostgreSQL 경로를 읽음 | `disabled` |
| 정상 hit | Cache detail 반환, DB transaction 없음 | `hit` |
| 정상 miss·만료 | Single-flight로 PostgreSQL을 읽고 성공 결과 저장 | `miss`, `source-load` |
| Redis 연결·command timeout | Redis 결과를 기다리지 않고 PostgreSQL fallback | `error`, read circuit 반영 |
| Redis write 실패·memory 거부 | PostgreSQL 결과는 성공으로 반환 | `write-error`, write circuit 반영 |
| Corrupt document | Best-effort 삭제 후 PostgreSQL fallback | `corrupt` |
| Redis 재시작·빈 cache | Warm-up 전까지 PostgreSQL fallback | miss와 source-load 증가 |
| PostgreSQL 실패 | 기존 DB 오류 의미 유지 | Cache miss가 DB 장애를 숨기지 않음 |

Lettuce connect·command timeout은 request와 PostgreSQL fallback에 남겨야 할 latency budget보다
작은 bounded 값으로 둡니다. 구현 시 current request·Hikari budget을 확인하고 timeout 뒤에도
DB fallback을 시도할 시간이 남는 exact 값을 typed configuration과 test로 동결합니다. Cache operation에는 retry를 적용하지 않습니다. 같은 요청 안의 retry보다
authoritative PostgreSQL fallback이 정확하고 latency가 예측 가능합니다.

### D6-B. Circuit 구현 경계

**결정 질문:** D6-A가 선택한 read/write circuit 동작을 어떤 구현 경계로 제공해야 합니까?
세 후보는 같은 closed·open·half-open 결과를 만들 수 있지만 상태 머신과 Spring 결합 책임이
다릅니다.

| 비교 축 | 자체 상태 머신 | Resilience4j Spring AOP starter | Resilience4j core programmatic API |
| --- | --- | --- | --- |
| 상태 전이 정확성 | Half-open·동시 call·window를 직접 구현합니다. | 검증된 library가 제공합니다. | 검증된 library가 제공합니다. |
| 적용 위치 | Adapter 코드에 직접 둡니다. | Annotation·advisor·Spring configuration에 분산됩니다. | Redis adapter가 명시적으로 호출합니다. |
| Read/write 분리 | 직접 설계할 수 있습니다. | 이름별 instance는 가능하지만 AOP order와 fallback wiring을 함께 검증합니다. | 두 instance와 결과 처분을 adapter에서 직접 분리합니다. |
| Spring Boot 4 결합 | 없습니다. | Starter·AOP·configuration binding 호환성을 추가 검증합니다. | Core artifact만 사용해 Spring integration surface가 작습니다. |
| Metric·test | 모든 transition·metric을 직접 만듭니다. | Library event와 Spring metric integration을 사용합니다. | Library event를 Micrometer adapter에 명시 연결하고 단위 test합니다. |
| 유지 비용 | Dependency는 없지만 concurrency defect 위험이 가장 큽니다. | Wiring은 작아 보이나 global proxy 동작과 starter upgrade를 관리합니다. | Dependency upgrade는 필요하지만 적용 범위와 test 경계가 가장 좁습니다. |
| 현재 목표 판정 | 검증된 상태 머신을 다시 만드는 위험 때문에 탈락합니다. | 현재 한 adapter에 비해 Spring 결합 표면이 큽니다. | **검증된 상태 머신과 명시적 adapter 제어를 함께 충족합니다.** |

**선택: `resilience4j-circuitbreaker:2.4.0` core programmatic API.** Library dependency를
관리하는 비용을 감수하고 자체 concurrency 상태 머신과 global AOP 결합을 피합니다. Spring
Boot 4용 starter 호환성이 공식적으로 닫히고 여러 adapter가 공통 정책을 소비하게 되면 starter를
같은 축으로 다시 비교합니다. Breaker state는 liveness·readiness에 포함하지 않습니다.

Breaker는 read/write 상태를 분리하고, 충분한 표본 뒤 failure·slow-call 비율로 열며, bounded
open 구간 뒤 제한된 half-open probe만 허용합니다. Exact window·최소 표본·비율·시간·probe 수는
구현에서 typed configuration으로 두고 closed·open·half-open 전이를 결정론적으로 검증합니다. Slow-call threshold는
완료된 호출을 사후 분류하는 값이지 timeout처럼 호출을 중단시키는 수단이 아닙니다. 실제 시간
상한은 command timeout이 집행합니다. 값을 바꾸더라도 read/write 분리, retry 없음과 readiness
비연동 계약은 유지합니다.

## D7. Redis engine과 Kubernetes workload 형태

이 절에는 같은 비교군이 아닌 다섯 기술 질문이 있습니다. Client는 application 호출 방식,
release line은 server support, controller는 Pod identity·rollout, storage는 data 복구 방식을
비교하고 license는 허용된 사용·배포 준수 경로를 결정합니다.

### D7-A. Application Redis client

**결정 질문:** D1의 cache-aside가 같은 Redis string command를 실행할 때 connection·codec을
어떤 client 경계에 맡길 것입니까? 세 후보는 동일한 cache port 결과를 만들 수 있습니다.

| 비교 축 | Spring Data Redis + Lettuce | Raw Lettuce | Spring Data Redis + Jedis |
| --- | --- | --- | --- |
| Spring Boot configuration | Boot가 endpoint·timeout·connection factory를 관리합니다. | Configuration과 lifecycle을 직접 조립합니다. | Boot가 connection factory를 관리할 수 있습니다. |
| Cache document 제어 | `StringRedisTemplate` 위에서 명시적 JSON mapping이 가능합니다. | Low-level command와 codec을 직접 관리합니다. | `StringRedisTemplate` 위에서 같은 mapping이 가능합니다. |
| Connection 모델 | 기본 Lettuce client의 thread-safe connection과 Netty 기반 I/O를 사용합니다. | 같은 Lettuce 특성을 쓰지만 Spring integration을 직접 구현합니다. | Blocking I/O와 pool 운영을 선택·관리합니다. |
| 현재 추가 코드 | 가장 작습니다. | Connection·codec·metric wiring이 늘어납니다. | Client 교체 근거와 pool 설정이 추가됩니다. |
| 현재 목표 판정 | **현재 Spring stack과 명시적 document 제어를 최소 비용으로 결합합니다.** | 동일 결과는 가능하지만 Spring 관리 경로를 중복 구현합니다. | 현재 workload에서 Lettuce를 바꿀 독립 이점이 없습니다. |

**선택: Spring Data Redis와 기본 Lettuce client.** 이것은 Redis 연결 도구 선택이며 D1의
`@Cacheable`과 명시적 cache-aside 중 무엇을 고르는가와는 별개입니다. Raw client의 세밀한
제어를 포기하고 Spring 관리 경계를 유지합니다. Lettuce 결함이나 blocking client가 유리한
실측 workload가 확인되면 같은 client 축으로 다시 비교합니다.

### D7-B. Redis server release line

**결정 질문:** 현재 필요한 string·TTL·ACL·eviction 기능을 제공하는 지원 line 중 신기능
추종과 장기 patch 유지 중 무엇을 우선할 것입니까?

| 비교 축 | 최신 8.10 standard line | 8.2 extended line |
| --- | --- | --- |
| 현재성 | 더 최신 기능과 성능 개선을 포함합니다. | 현재 목적에 필요한 string·ACL·eviction 기능을 충족합니다. |
| 지원 기간 | 다음 minor 이후 제한된 기간 동안 security·critical fix를 받는 standard 정책입니다. | 2030-09-01까지 security·critical fix를 받는 extended 정책입니다. |
| Upgrade 빈도 | 최신 minor를 따라가려면 더 자주 line을 재평가합니다. | Minor line을 오래 유지하고 patch만 갱신할 수 있습니다. |
| 현재 목표 판정 | 최신 기능이 현재 cache 결과를 바꾸지 않아 운영 변경 비용이 더 큽니다. | **장기 patch 경로와 현재 필요한 기능을 함께 충족합니다.** |

**선택: Redis Open Source 8.2 extended line의 구현 시점 최신 patch.** 현재 확인값은
`8.2.9`이며 image digest와 ARM64 manifest는 구현 직전에 다시 검증합니다. 최신 standard
기능을 늦게 수용하는 대신 지원 기간과 upgrade 빈도를 우선합니다. 필요한 기능이 extended
line에 없거나 지원 정책이 바뀌면 latest standard를 다시 비교합니다.

### D7-C. Kubernetes controller

**결정 질문:** 같은 replica 1·ephemeral Redis Pod를 stable identity 없이 갱신할지, stable
identity를 부여해 갱신할지 결정합니다. Storage와 HA는 이 표의 비교축이 아닙니다.

| 비교 축 | Deployment + `Recreate` | StatefulSet, ephemeral storage |
| --- | --- | --- |
| Pod identity | 교체 가능한 Pod identity | 순서와 stable network identity 제공 |
| Rollout | 기존 Pod 종료 뒤 새 Pod를 만들어 동시 memory 점유 방지 | Ordered replacement와 stable ordinal 유지 |
| 현재 필요 | Client는 ClusterIP를 사용하고 개별 Redis Pod identity를 알 필요가 없음 | Replica·persistent volume·cluster membership가 없어서 identity 소비자가 없음 |
| 현재 목표 판정 | **현재 필요한 rollout만 제공하는 최소 controller** | 사용하지 않는 identity 계약과 운영 표면이 늘어남 |

**선택: Deployment + `Recreate`.** 짧은 cache 공백은 D6의 PostgreSQL fallback이 흡수하며,
단일 4 GiB node에서 old/new Redis가 동시에 memory를 점유하지 않게 합니다. Stable identity를
소비하는 replication·cluster membership가 생기면 controller를 다시 비교합니다.

### D7-D. Storage와 persistence

**결정 질문:** 같은 replica 1 controller에서 폐기 가능한 cache data를 restart 뒤 복구할
것입니까?

| 비교 축 | `emptyDir`, RDB·AOF off | PVC + RDB 또는 AOF |
| --- | --- | --- |
| Restart 결과 | Cache가 비고 PostgreSQL에서 다시 채움 | Snapshot 또는 append log에서 cache를 복원 |
| 원본성 | Redis가 폐기 가능한 보조 data라는 계약과 일치 | 원본 아닌 data의 durability·복구 절차가 추가됨 |
| 운영 비용 | Volume·snapshot·restore 없음 | PVC, flush, corruption, restore와 retention 관리 필요 |
| 현재 목표 판정 | **재생성 가능한 cache의 최소 storage 계약** | 현재 결과에 필요한 가치 없이 운영 책임만 증가 |

**선택: `emptyDir`, RDB·AOF off.** Restart warm-up이 DB 안정 envelope를 깨는 실증이 나오면
persistence를 자동 채택하지 않고 D5의 stampede·refresh와 함께 같은 목표로 다시 비교합니다.
Sentinel·Cluster와 managed Redis는 controller나 storage의 대안이 아니라 D12-B의 production
topology gate 뒤에서 SLO·failure domain·비용을 고정한 후 비교합니다.

Redis Open Source `8.2` extended release line의 현재 patch `8.2.9`를 선택합니다. 실제 manifest에는
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

### D7-E. Redis 8 tri-license 준수 경로

**결정 질문:** 공식 Redis image를 수정하지 않고 자동차 서비스의 내부 cache로 운영할 때
Redis 8이 허용한 세 license 중 현재 사용 조건에서 의무가 가장 단순한 준수 경로는 무엇입니까?
세 후보는 같은 software를 사용할 수 있지만 서비스 제한과 source 공개 의무가 다릅니다.

| 비교 축 | RSALv2 | SSPLv1 | AGPLv3 |
| --- | --- | --- | --- |
| License 성격 | Source-available, non-copyleft, OSI 미승인 | Source-available, copyleft, OSI 미승인 | Open source, copyleft, OSI 승인 |
| Redis가 밝힌 핵심 제한·의무 | Redis 기능을 제3자에게 managed service로 상용 제공할 수 없고 notice를 보존합니다. | 제품을 service로 제공하면 수정물과 management layer source 공개 의무가 생깁니다. | 수정·파생 Redis를 network로 제공할 때 대응 source 제공 검토가 필요합니다. |
| 현재 unmodified 내부 cache | Redis 기능 자체를 제3자에게 service로 제공하지 않고 notice를 보존하면 현재 목적과 맞습니다. | 현재 목적에도 service 제공 범위와 source 공개 의무를 계속 판정해야 합니다. | 현재 목적에도 network 제공·파생물과 대응 source 범위를 계속 판정해야 합니다. |
| 향후 위험 trigger | Redis 기능 자체의 외부 제공·재판매 | Redis service 제공·management layer 개발 | Redis 수정·파생물·network 제공·배포 |
| 현재 조건의 단순성 | **Source 공개 의무를 추가하지 않아 현재 내부 cache 경로가 가장 단순합니다.** | Management layer까지 이어질 수 있는 공개 범위가 더 큽니다. | Copyleft 대응 source 경계 검토가 필요합니다. |
| 선택을 다시 여는 입력 | Redis 기능 자체의 서비스 제공·재판매, source 수정·image 재배포 | Redis service 제공과 management layer 개발 | Redis 수정·파생물·network 제공 |

**선택: RSALv2.** 사용자가 요청한 “현재 조건에서 가장 제약이 적은 경로”에 따라, Redis source를
수정하지 않고 자동차 서비스 내부 cache로만 사용하는 현재 범위에는 RSALv2를 적용합니다.
License notice와 exact image provenance는 repository artifact에 보존합니다. Redis 기능 자체를
제3자에게 managed service로 제공·재판매하거나 Redis source 수정·image 재배포·사업 조건 변경이
생기면 구현·배포를 멈추고 세 원문과 현재 조건을 다시 대조합니다. 이 선택 기록은 법률 자문을
대신하지 않습니다.

## D8. Memory, eviction과 node 자원

**결정 질문:** Redis가 정한 memory 상한에 도달했을 때 API 성공과 hot data 재사용 중 무엇을
보존하도록 entry를 처분해야 합니까?

| 비교 축 | `noeviction` | TTL key 대상 `volatile-lru` | 전체 key 대상 `allkeys-lfu` |
| --- | --- | --- | --- |
| Memory 상한 | `maxmemory`로 제한 | `maxmemory`로 제한 | `maxmemory`로 제한 |
| 상한 도달 시 write | 새 cache write를 거부합니다. API는 best-effort 규칙으로 성공할 수 있지만 cache 효율이 급락합니다. | TTL이 있는 key 중 최근 사용이 적은 값을 제거합니다. | 전체 key 중 사용 빈도가 낮은 값을 제거합니다. |
| 현재 data 특성 | 모든 entry가 TTL을 가지므로 write 거부 이점이 없습니다. | Cold entry 제거가 가능하지만 최근성 위주입니다. | 반복 조회가 많은 hot model을 빈도 기준으로 우선 보존합니다. |
| 운영 해석 | Write error와 memory 포화가 함께 증가합니다. | Eviction·hit 추이를 관찰합니다. | Eviction·hit·DB source-load 추이를 함께 관찰합니다. |
| 현재 목표 판정 | Node는 보호하지만 hot read 효과를 쉽게 잃습니다. | 유효하지만 workload의 반복 빈도 특성을 덜 반영합니다. | **Memory 상한과 hot read 보존을 가장 잘 결합합니다.** |

**선택: `maxmemory`와 `allkeys-lfu`.** Cache entry보다 process·node 생존을 우선하고,
동일하게 제거가 필요할 때에는 반복 사용 빈도가 낮은 값을 먼저 버립니다. Policy만으로
container OOM을 막을 수 없으므로 별도 request·limit와 allocator headroom을 필수로 둡니다.
실측 access 분포가 빈도보다 최근성을 더 잘 예측하거나, TTL 없는 다른 data가 같은 instance에
들어오거나, eviction이 DB source-load 안전선을 넘기면 policy·instance 분리를 다시 비교합니다.

Dedicated Redis에는 `maxmemory`와 `allkeys-lfu`를 적용합니다. 모든 entry에 TTL이 있지만 hot
모델을 빈도 기반으로 보존하고, memory가 찼을 때 application write가 cache 때문에 실패하지
않게 cold key를 제거합니다. `maxmemory`는 container limit에서 allocator fragmentation,
client buffer와 process overhead를 위한 headroom을 남깁니다. 현재 초기값은 manifest와
[GitOps Application Delivery](../../../project/infrastructure/220-gitops-application-delivery.md)가
소유하며 live 사용량 검증을 완료한 운영 sizing으로 해석하지 않습니다.

Redis와 application 모두 CPU·memory request·limit를 갖습니다. 실제 활성화 전에 live node allocatable,
control-plane·Argo·application 사용량과 entry size·cardinality를 대조하며, 다음 조건을 동시에 만족하지 않으면
staging 활성화를 중단합니다.

- Node system·control-plane 여유를 뺀 뒤 application과 Redis request가 schedulable합니다.
- Redis `maxmemory`, non-evictable overhead와 container limit의 관계가 위 headroom을 지킵니다.
- OOM kill, sustained CPU throttling, node memory pressure와 기존 workload 퇴거가 없습니다.
- Eviction은 cache 용량 신호일 뿐 오류가 아니지만, rising eviction과 hit ratio·DB load 악화가
  함께 나타나면 memory 상향보다 key·entry 분포를 먼저 분석합니다.

## D9. Network, credential과 process 보안

이 절은 접근 권한과 전송 암호화를 별도 결정으로 비교합니다. NetworkPolicy·ACL은 누가 어떤
command를 실행하는지 제한하고, TLS는 허용된 두 Pod 사이의 packet 노출·server 사칭을 제한하므로
서로 대체할 수 없습니다.

### D9-A. Network와 Redis 권한

**결정 질문:** Public endpoint가 아닌 같은 cluster 내부 Redis에서 credential 유출이나
오용이 발생했을 때 침해 범위를 어디까지 제한해야 합니까?

| 비교 축 | ClusterIP만 사용, 인증 없음 | 단일 shared ACL user | NetworkPolicy + 역할별 ACL user·Secret |
| --- | --- | --- | --- |
| 외부 노출 | Public Service를 만들지 않으면 차단됩니다. | 동일합니다. | 동일합니다. |
| Namespace 내부 접근 | 접근 가능한 모든 Pod가 command를 실행할 수 있습니다. | Password를 가진 workload만 접근합니다. | Application Pod source와 역할별 credential·command를 함께 제한합니다. |
| Credential 유출 영향 | Credential이 없어 network 접근 자체가 곧 권한입니다. | Application·운영·probe 권한이 모두 노출됩니다. | 유출된 역할의 key prefix·command 범위로 제한됩니다. |
| Probe·운영 분리 | 불가능합니다. | 같은 강한 credential을 재사용합니다. | `PING` 전용 probe와 value 비조회 진단 user를 분리합니다. |
| 운영 비용 | 가장 작지만 최소 권한 기준을 충족하지 못합니다. | 중간입니다. | ACL file, Secret 두 개와 rotation 절차가 필요합니다. |
| 현재 목표 판정 | 무인증 내부 신뢰 때문에 탈락합니다. | 최소 권한·비밀 분리에서 열세입니다. | **침해 범위를 줄이는 필수 보안 기준을 충족합니다.** |

**선택: ClusterIP, Calico NetworkPolicy, application·operation·probe ACL 분리, 전용 Secret과
non-root process.** Network 위치만 신뢰하지 않고 source workload, credential과 command를
겹쳐 제한합니다.
ACL·Secret rotation이 가용성 목표를 반복해서 깨거나 service mesh·외부 secret manager가 같은
최소 권한과 더 직접적인 identity를 제공하면 역할 수와 credential 경계를 다시 비교합니다.

### D9-B. Redis 전송 암호화

**결정 질문:** 현재 single-tenant·single-node staging에서 application과 Redis 사이 전송을
어떤 인증·암호화 수준으로 보호해야 합니까?

| 비교 축 | ACL + 평문 cluster network | Server TLS + ACL | Mutual TLS + ACL |
| --- | --- | --- | --- |
| Packet의 credential·value 노출 | Node network를 관찰할 수 있으면 노출될 수 있습니다. | 암호화해 노출을 줄입니다. | 암호화해 노출을 줄입니다. |
| Server identity | NetworkPolicy·Service DNS를 신뢰합니다. | Application이 Redis certificate를 검증합니다. | 양쪽이 certificate로 서로를 검증합니다. |
| Client 권한 | Redis ACL credential이 담당합니다. | Redis ACL credential이 담당합니다. | Client certificate와 ACL을 함께 운영합니다. |
| Secret·rotation | ACL Secret만 회전합니다. | CA·server certificate·ACL을 함께 회전합니다. | CA·server·client certificate와 ACL을 함께 회전합니다. |
| 현재 failure domain에서 추가 보호 | 권한 있는 node 침해에는 약합니다. | Packet 관찰·server 사칭을 줄이지만 같은 node의 privileged compromise 전체를 막지는 못합니다. | Client identity도 강화하지만 같은 node compromise와 Secret 접근은 별도 통제가 필요합니다. |
| 현재 목표 판정 | **현재 staging 신뢰 경계와 최소 운영 비용을 충족하지만 production 전송 보호로 승격할 수 없습니다.** | 보안은 강하지만 현재 certificate owner·rotation 기반이 없어 새 운영 체계가 필요합니다. | 현재 한 client의 risk 대비 가장 복잡합니다. |

**현재 선택: ACL + 평문 cluster network를 staging에만 허용합니다.** 비용을 숨긴 “내부라서
안전” 판단이 아니라, 현재 single node에서 certificate lifecycle을 추가해도 node failure·침해
domain은 그대로라는 trade-off를 수용한 결정입니다. 다중 node, 신뢰하지 않는 workload, 외부
network hop, production 승격 또는 전송 암호화 요구가 생기면 server TLS를 최소 후보로 다시
열고 certificate 발급·rotation·client trust를 먼저 설계합니다. 그 gate 없이 평문 구성을
production으로 복사하지 않습니다.

- Redis Service는 public Ingress·NodePort·LoadBalancer 없이 namespace 내부 ClusterIP만
  제공합니다.
- Calico NetworkPolicy는 application Pod에서 Redis TCP port로 들어오는 traffic만 허용하고
  Redis의 불필요한 egress는 차단합니다. DNS나 외부 API가 필요하지 않은 server 특성을
  유지합니다.
- 설계 시점 AppProject allow-list에 없던 `NetworkPolicy`를 Redis desired state의 소비 권한으로
  추가했습니다. Repository 변경과 live 적용 권한은 분리합니다.
- Redis default user는 끄고 application, operation 진단, probe user를 분리합니다. Application
  user는 catalog model-detail key prefix와 client handshake, `GET`, TTL을 포함한 `SET`, corrupt
  처분용 `DEL`, `PING`의 검증된 최소 command만 사용합니다. 진단 user는 key value 조회 없이
  prefix의 `TTL`, `INFO commandstats`·`INFO stats`와 `PING`만 사용하고, probe user는 `PING`만 허용합니다.
  `MEMORY PURGE`, `LATENCY RESET`, `CONFIG`, `FLUSH*`,
  module·script·replication 관리 command는 허용하지 않습니다.
- Plaintext password는 Git에 두지 않습니다. Application과 Redis server가 서로 필요한 값만
  읽도록 다음 두 Secret 책임을 분리합니다.
  - Application Secret: application username·plaintext password만 가지며 application Pod가
    explicit key reference로 소비합니다.
  - Redis server Secret: 세 user의 password hash가 들어간 ACL file과 probe username·plaintext
    password만 가지며 Redis Pod만 소비합니다. Operation plaintext password는 cluster에
    저장하지 않습니다.
- Probe는 Secret key에서 `REDISCLI_AUTH` 환경변수로 password를 받고 고정 probe username으로
  `redis-cli PING`을 호출합니다. Pod spec command argument와 log에는 plaintext를 넣지 않습니다.
- Credential 생성·rotation·제거는 다른 application Secret과 분리되고 flag-off 상태로
  복구 가능해야 합니다. Exact actor·순서·완료 증거는
  [580 Catalog Redis 활성화·credential](../../../operations/580-catalog-redis-activation-and-credential-runbook.md)이 소유합니다.
- Redis container도 `runAsNonRoot`, `allowPrivilegeEscalation: false`, read-only root filesystem,
  `RuntimeDefault` seccomp와 capability drop을 적용합니다. Health probe는 password를 command
  argument나 log에 넣지 않습니다.

현재 신뢰 경계와 TLS의 한계·재검토 조건은 D9-B가 소유하며, NetworkPolicy나 ACL이 전송
암호화를 제공한다고 해석하지 않습니다.

### D9-C. 역할별 credential을 어떤 실행 경계에서 수렴할 것인가

**결정 질문:** 실제 값을 argument·log·Git에 노출하지 않으면서 두 Secret과 ACL file을
같은 입력으로 재수렴하려면 어떤 실행 경계를 사용해야 합니까?

| 비교 축 | Runbook의 긴 shell block | 기존 단일 Secret helper에 결합 | 전용 remote operations entrypoint |
| --- | --- | --- | --- |
| 실패 전파 | 복사한 shell option과 마지막 command에 따라 앞선 실패가 가려질 수 있습니다. | 서로 다른 consumer·ACL hash 계약을 일반 helper가 떠안습니다. | `set -euo pipefail`, lock과 두 postcondition을 한 owner가 집행합니다. |
| Secret 노출 | `kubectl --from-literal` argument나 YAML 확인 출력에 값이 드러날 수 있습니다. | 기존 helper를 크게 일반화해야 합니다. | 세 값을 NUL-delimited stdin으로 받고 base64/hash manifest를 stdin apply하며 값을 출력하지 않습니다. |
| 재실행·부분 실패 | 운영자가 중간 artifact와 재개 지점을 해석해야 합니다. | 기존 notification/community 생애주기와 실패 경계가 섞입니다. | 두 Secret만 같은 field manager로 재수렴하고 둘 다 검증돼야 성공합니다. |
| 현재 목표 판정 | 재사용 가능한 Procedure 기준과 fail-close를 충족하지 못합니다. | 책임·변경 주기가 다른 기존 helper를 과도하게 확장합니다. | **Redis credential lifecycle의 가장 좁은 반복 실행 경계입니다.** |

**선택: `scripts/operations/application/reconcile-catalog-redis-secrets.sh`.** 확정된 범위가 요구한
credential 생성·same-input 재수렴·rotation의 Secret 단계만 자동화합니다. AppProject 적용,
GitOps flag 전환, rollout과 cold/warm smoke는 권한·실패·재개 경계가 다르므로 runbook의 사람
checkpoint로 남깁니다. Contract test는 입력 오류, 다른 owner/schema, plaintext argument·log
노출, 두 Secret postcondition, 같은 입력 재실행과 두 번째 apply 실패를 재현합니다. 실제
Kubernetes SSA·rollout·기능 성공은 별도 live 권한의 staging smoke만 증명합니다.

이 entrypoint는 Redis credential lifecycle이 제거되거나 표준 external secret manager가 같은
두 Secret·ACL 수렴과 rotation을 더 직접 소유하면 함께 제거합니다. 현재 repository의 Bash와
`kubectl`만 사용하며 새 runtime·외부 package·CI hard gate를 추가하지 않습니다. 운영자가 필요할
때만 target EC2에서 실행하고 실패하면 flag-off를 유지한 채 같은 입력으로 다시 실행합니다.

## D10. Health, metric과 log

**결정 질문:** Redis를 application의 필수 dependency로 만들거나 새 관리 endpoint를 열지 않고,
설치·결합 결과와 fallback 원인을 구별할 최소 진단은 무엇입니까?

| 비교 축 | Health·오류 log만 | 내부 low-cardinality counter + health·요약 log | 새 metric endpoint·collector |
| --- | --- | --- | --- |
| Hit·miss·fallback 구별 | 요청 결과를 안정적으로 분리하기 어렵습니다. | 결과·operation·source-load·circuit을 제한된 label로 분리합니다. | 분리하고 장기 집계할 수 있습니다. |
| Readiness 영향 | Redis를 health group에 넣으면 정상 DB fallback Pod도 traffic에서 빠질 수 있습니다. | Application readiness는 기존 `readinessState,db`를 유지합니다. | 구성에 따라 분리할 수 있지만 관리 surface가 늘어납니다. |
| 노출·운영 표면 | 작지만 검증 정보가 부족합니다. | 기존 application 내부 계측과 log만 사용해 작습니다. | Endpoint 인증·수집·retention·dashboard owner가 필요합니다. |
| 현재 목표 판정 | 설치·결합 원인 판정에 부족합니다. | **현재 확인에 필요한 신호와 최소 운영 표면을 함께 충족합니다.** | 별도 관측 체계가 필요한 범위이므로 현재는 과도합니다. |

**선택: readiness 분리와 내부 low-cardinality counter, 상태 전이·rate-limited 요약 log.**
Redis가 보조 cache이므로 application startup, liveness와 readiness의 필수 dependency로 만들지
않습니다. Cache enabled 설정 자체가 유효한지는 fail-fast로 검증하지만 실행 중 Redis health가
내려가도 readiness의 `readinessState,db`는 유지하고 circuit·fallback으로 요청을 처리합니다.
Redis Pod의 startup·readiness·liveness만 D9의 probe user와 `REDISCLI_AUTH`를 사용하는 인증된
`PING`으로 확인합니다.

Application 내부 계측은 다음 low-cardinality 신호를 소유합니다.

- Lookup 결과: `disabled`, `bypass-non-current-as-of`, `hit`, `miss`, `error`, `corrupt`,
  `circuit-open`
- Redis read/write operation의 count·duration·outcome과 circuit state transition
- PostgreSQL source-load, single-flight leader·shared waiter·capacity exhaustion count
- Serialized entry byte 분포와 cache 저장 생략 count

Label과 log에는 model UUID, Redis key·value, credential과 request payload를 넣지 않습니다.
반복 요청마다 INFO log를 남기지 않고 circuit transition과 rate-limited 실패 요약만 구조화해
기록합니다. Production profile의 HTTP management 노출은 `health` 그대로 유지합니다. 별도
metric endpoint, 보안 체인, collector, dashboard와 장기 retention은 현재 범위에 추가하지 않습니다.

## D12. Configuration, rollout과 production 승격

Request 안의 Redis 장애 우회는 D6이 이미 소유하며 이 절에서 다시 선택하지 않습니다. D12는
운영자의 기능 disable과 production topology 승격만 소유합니다.

### D12-A. 잘못된 cache logic을 운영자가 disable

**결정 질문:** Cache logic 오류를 발견한 운영자가 Redis와 독립적으로 DB-only 상태를 만들 때
어떤 control plane을 사용해야 합니까? 세 후보는 모두 cache read/write 중단을 목표로 합니다.

| 비교 축 | Redis에 동적 kill switch 저장 | GitOps configuration flag-off | Manifest·code 즉시 제거 |
| --- | --- | --- | --- |
| Redis 장애 독립성 | Switch 저장소도 Redis라면 장애 때 읽지 못할 수 있습니다. | Redis와 독립된 Git·Argo delivery를 사용합니다. | Redis와 독립적입니다. |
| 변경 감사·재현 | 별도 flag store 감사 계약이 필요합니다. | Git revision과 Argo reconciliation으로 남습니다. | 큰 코드·resource 변경이 되어 긴급 disable과 제거가 결합됩니다. |
| 반영 속도 | 별도 control plane을 구축하면 가장 빠를 수 있습니다. | PR·merge·sync·rollout 시간이 필요합니다. | Build·배포·resource 처분이 필요해 가장 느리고 위험합니다. |
| 현재 운영 비용 | 새 availability·permission·audit owner가 필요합니다. | 기존 delivery 경계를 재사용합니다. | Rollback보다 제거 작업이 커집니다. |
| 현재 목표 판정 | Sub-minute RTO 요구가 없어 현재는 과도합니다. | **현재 owner에서 감사 가능한 최소 disable 경로입니다.** | 긴급 disable과 lifecycle 제거를 혼동해 탈락합니다. |

**선택: 기본 `false`의 Git-managed flag-off rollout.** Sub-minute operator disable이 실제
SLO가 되지 않는 현재에는 기존 delivery 경계를 재사용합니다. 즉시 전환을 포기하는
trade-off가 있으며, sub-minute SLO가 생기면 Redis와 독립된 동적 flag store를 다시 비교합니다.

### D12-B. Production topology 승격

현재는 production 기술 후보를 고르는 단계가 아닙니다. SLO·RTO/RPO, node·AZ failure domain,
허용 AWS 비용과 on-call 책임이 없으므로 self-managed와 managed를 공정하게 비교할 공통 종료
상태가 정의되지 않았습니다.

| 구분 | 현재 판정 | 다음 gate |
| --- | --- | --- |
| 현재 선택 | **Staging replica 1 구성을 production으로 승격하지 않음** | 아래 trigger 전까지 비HA 검증 환경으로만 유지 |
| 금지 shortcut | Replica·memory·HPA 설정만 확대 | Failover·data topology·connection·운영 책임을 해결하지 못하므로 production HA로 부르지 않음 |
| 미래 비교 후보 | Self-managed Sentinel·Cluster / AWS managed Redis | 같은 SLO·AZ·traffic·기간을 고정한 live AWS 견적과 운영 인건비로 비교 |
| 기본 추천 방향 | 운영 인력이 제한되고 HA가 필수면 managed 우선 검토 | 실제 가격·service 제약·법적 요구가 self-managed 이점을 만드는지 함께 확인 |

**현재 선택: production 승격 보류 gate.** 이는 기술 결정을 회피한 것이 아니라 필수 입력 없이
싼 single node나 비싼 managed service를 최선이라고 꾸미지 않는 결정입니다. Trigger가 생기면
그 시점에만 실제 대체안을 동일 비용·가용성 축으로 비교합니다.

`carmunity.catalog.model-detail-cache.enabled`를 공개 기능 flag로 두고 기본값은 `false`로
fail-close합니다. TTL·jitter·entry 상한·single-flight 상한과 circuit 값은 검증되는 typed
configuration이고, Redis endpoint·username·password·timeout은 Spring Data Redis 연결
configuration으로 분리합니다. Flag가 꺼졌을 때 cache adapter는 Redis command를 실행하지
않습니다.

Staging은 같은 application artifact의 flag-off를 기준선으로 사용하고 flag-on을 도입 후보로
사용합니다. Rollback 완료는 GitOps target 반영, DB-only 동작, Redis command 부재로 판정해야
하지만 exact PR·sync·rollout 순서는 운영 runbook이 소유합니다. 현재 staging은
이 전달 시간의 보장 RTO가 없으며 PostgreSQL data는 rollback 대상이 아닙니다. Old cache key는
scan 삭제 대신 TTL 소멸에 맡깁니다.

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
| 도입 당시 문제·증거 | 설계 당시에는 같은 모델 상세 요청이 매번 여러 PostgreSQL query group을 다시 실행했고 외부 cache 경계가 없었습니다. 도입된 cache 경계의 현재 구조는 [Module Map](../../../project/210-module-map.md)이 소유합니다. |
| 도입 경계 | Redis는 R0의 사용자 고정 입력이며 PostgreSQL의 원본 지위를 바꾸지 않는 선택적 catalog outbound adapter로만 사용합니다. Query 최적화·Caffeine과의 우열은 이번 결정이 아닙니다. |
| 독립 oracle | 실제 Redis를 사용한 cold miss→PostgreSQL→populate→warm hit, TTL·직렬화·오염 처분과 Redis unavailable fallback을 확인합니다. Mock이나 Redis `PING`만으로 application 결합 완료를 선언하지 않습니다. |
| 오판·새 실패 표면 | Warm 값만 확인하면 source 경로와 serializer 결함을 놓칠 수 있습니다. 단일 node failure domain, credential·upgrade·memory 운영은 새 비용으로 남습니다. |
| 실행자·실패 경계 | Application request의 선택적 adapter와 staging의 GitOps workload만 소비합니다. Redis 실패는 짧은 timeout·circuit 뒤 DB fallback으로 제한합니다. |
| Owner·갱신·제거 | Catalog application owner가 client·document를, infrastructure owner가 image·resource·ACL을 소유합니다. 8.2 security patch와 Spring 호환성을 점검합니다. 계약 위반이 닫히지 않으면 flag-off 후 Redis resource·client·document·test·문서를 함께 제거합니다. |

### Resilience4j circuit breaker

| 판정 축 | 현재 결론 |
| --- | --- |
| 근본 문제·증거 | Lettuce timeout과 try/catch만으로는 Redis 장애 때 모든 요청이 timeout을 반복해 fallback latency와 client work를 증폭합니다. |
| 대안과 선택 | Retry는 authoritative DB fallback보다 느리고, 자체 상태 머신은 half-open·동시성·metric 결함 위험이 큽니다. Spring AOP starter 대신 작은 `resilience4j-circuitbreaker:2.4.0` core API만 adapter 내부에서 사용합니다. |
| 독립 oracle | 통제한 Redis client 실패와 fake clock에서 call count, breaker transition, DB fallback과 API 결과를 결정론적으로 확인합니다. |
| 오판·새 실패 표면 | 민감한 threshold는 정상 Redis를 일시 우회하는 false open을, 둔한 threshold는 timeout 반복을 만듭니다. 분리 breaker와 bounded 값으로 제한하며 false open의 결과는 정확한 DB fallback입니다. |
| 실행자·실패 경계 | Redis adapter만 programmatic하게 호출하고 annotation·global AOP·readiness에 연결하지 않습니다. Library failure나 open은 cache bypass 이상으로 확대되지 않습니다. |
| Owner·갱신·제거 | Catalog Redis adapter가 version·configuration을 소유합니다. Lettuce나 향후 cache infrastructure가 동등한 검증된 short-circuit를 제공하거나 Redis를 제거하면 dependency·configuration·metric을 함께 제거합니다. |

## 목표 보존과 남은 위험

| 사용자 목표·제약 | 설계가 보존하는 방법 | 남은 위험·재검토 |
| --- | --- | --- |
| 반복 read 재사용 | Hot detail 재사용, single-flight와 LFU | Unique-key miss·Redis 실패 시 처리 능력은 DB 경로에 제한됩니다. |
| 정확성과 5분 freshness | Query identity key, 300초 이하 TTL, corrupt fallback | Runtime writer가 생기면 active invalidation이 필요합니다. |
| 장애 격리 | 짧은 timeout, circuit, retry 없음, readiness 분리 | Cache-enhanced peak 전체를 Redis 없이 보장하지는 않습니다. |
| DB 보호 한도 초과 | 503, `Retry-After`, `CATALOG_MODEL_DETAIL_CAPACITY_EXHAUSTED`로 일시적인 server 처리 한계와 재시도 행동을 구별 | Retry 값은 current request·Hikari budget 규칙과 함께 갱신해야 합니다. |
| 확장성과 유지보수성 | Catalog port·adapter, versioned document, DB source 분리 | 다중 replica 전에는 distributed concurrency를 재설계해야 합니다. |
| 단일 node 비용 경계 | 무영속 replica 1, request·limit, maxmemory headroom | Node 자체가 application·Redis 공통 failure domain입니다. |
| 보안 | 비공개 Service, NetworkPolicy, 최소 ACL, Secret, non-root | 현재 Pod network는 TLS로 암호화하지 않습니다. Production 승격 gate입니다. |
| 설치·결합 확인 | 실제 Redis의 cold/warm·TTL·직렬화·ACL·flag-off를 repository integration과 최소 staging smoke로 확인 | Live smoke는 repository 검증 뒤 별도 권한이 있어야 합니다. |

## 관련 문서와 공식 근거

- **작업 결과 정의**: [010 자동차 모델 상세 Redis cache 도입](010-catalog-model-detail-redis-cache.md)
- **Catalog 업무 기준**: [130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md)
- **Application 경계**: [200 헥사고날 아키텍처](../../../architecture/200-hexagonal-architecture.md),
  [210 Spring Boot 모듈 구조](../../../architecture/210-spring-boot-module-structure.md),
  [220 경계와 매핑 규칙](../../../architecture/220-boundary-and-mapping-rules.md)
- **현재 API 오류 계약**: [230 REST API 설계](../../../architecture/230-rest-api-design.md),
  `ApiErrorResponseFactory`, `ApiExceptionHandler`, `CatalogErrorCode`,
  `CommunityPublicRateLimitFilter`
- **현재 runtime**: [120 EC2 Kubernetes node](../../../project/infrastructure/120-ec2-kubernetes-node.md),
  [150 Kubernetes cluster](../../../project/infrastructure/150-kubernetes-cluster.md),
  [220 GitOps Application Delivery](../../../project/infrastructure/220-gitops-application-delivery.md)
- **Spring 공식 근거**: [Spring Boot Redis](https://docs.spring.io/spring-boot/reference/data/nosql.html#data.nosql.redis),
  [Spring Cache annotation](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html),
  [Spring Data RedisTemplate과 serializer](https://docs.spring.io/spring-data/redis/reference/redis/template.html),
  [Spring Boot metric](https://docs.spring.io/spring-boot/reference/actuator/metrics.html)
- **Redis 공식 근거**: [지원 version 정책](https://redis.io/docs/latest/operate/oss_and_stack/install/version-mgmt/),
  [Redis Open Source 8.2 release note](https://redis.io/docs/latest/operate/oss_and_stack/stack-with-enterprise/release-notes/redisce/redisos-8.2-release-notes/),
  [Persistence](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/),
  [Eviction](https://redis.io/docs/latest/develop/reference/eviction/),
  [Security와 ACL](https://redis.io/docs/latest/operate/oss_and_stack/management/security/),
  [License](https://redis.io/legal/licenses/)
- **Kubernetes 공식 근거**: [Workload controller](https://kubernetes.io/docs/concepts/workloads/controllers/),
  [Resource 관리](https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/),
  [NetworkPolicy](https://kubernetes.io/docs/concepts/services-networking/network-policies/),
  [Probe](https://kubernetes.io/docs/concepts/workloads/pods/probes/),
  [Linux 보안 제약](https://kubernetes.io/docs/concepts/security/linux-kernel-security-constraints/)
- **Circuit breaker 공식 근거**: [Resilience4j circuit breaker](https://resilience4j.readme.io/docs/circuitbreaker),
  [Resilience4j release](https://github.com/resilience4j/resilience4j/releases)
- **HTTP 오류 공식 근거**: [RFC 9110 503 Service Unavailable](https://www.rfc-editor.org/rfc/rfc9110.html#name-503-service-unavailable),
  [RFC 9110 Retry-After](https://www.rfc-editor.org/rfc/rfc9110.html#name-retry-after),
  [RFC 6585 429 Too Many Requests](https://www.rfc-editor.org/rfc/rfc6585.html#section-4)
