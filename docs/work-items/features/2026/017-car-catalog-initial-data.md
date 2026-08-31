# 자동차 카탈로그 초기 데이터 구축

## 상태

Proposed

## 현재 위치와 선행 작업

이 작업은 [016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md)가 로컬에서
구현·검증한 11개 database table과 조회 기능을 입력 계약으로 사용합니다. 첫 공개 범위는
현대자동차·기아의 대한민국 일반 개인 구매용 현재 판매 승용·SUV·MPV이며, 이미지 파일과
외부 이미지 URL은 모두 제외합니다. 운영 데이터는 아직 없습니다.

현재 자동차 개념과 물리 target은 각각
[130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md)과
[110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)가 소유합니다.
Source 사전 조사·권리 비교·입력 방식 후보는
[017 자동차 카탈로그 초기 데이터 확보 설계](017-car-catalog-initial-data-design.md)가
소유합니다.

016의 아직 수행하지 않은 push 뒤 CI·Sonar 검증과 017의 데이터 확보는 서로 대신하지
않습니다. 현재 요청은 외부 문의·가입·표본·견적 요청·계약·구매, push 또는 환경 적용 권한을
만들지 않습니다.

## 달성할 결과

확정한 범위에서 사용·가공·저장·외부 공개 권리가 확인된 공식 자료를 source manifest와
원문→`table.column` mapping으로 고정하고, 016 schema에 맞는 정형 입력으로 변환합니다.
모든 적재 값은 exact source revision·수집 방식·원문 위치·변환 규칙으로 역추적할 수 있어야
합니다. 선택 범위의 공식 목록과 DB 행을 양방향 대조해 누락 0건을 확인한 뒤 versioned Flyway
data migration으로 적용합니다.

완료는 대표 차량 몇 대가 들어간 상태가 아니라, 선택한 현대·기아 현재 판매 범위의 모델·연식·
판매 구성·가격·옵션·필수 제원·인증 조건이 모두 권리와 provenance gate를 통과한 상태입니다.

## 제외 범위

- runtime crawler, scheduler, 관리자 입력 API·CMS와 application writer 권한
- 사용·보관·재배포 권리가 확인되지 않은 원문 파일, 이미지와 실제 값
- 외부 기관 문의·가입·표본·견적 요청·계약·구매
- push와 staging·production 적용
- 상용·특장차, 렌터카 전용·장애인 전용 판매 구성, 재고·대리점·보조금·중고차
- 차량번호·VIN과 사용자 소유 차량 조회
- 016 schema의 선제 재설계와 모든 핵심 사실의 수정 시점별 감사 이력

대표 source에서 반복 핵심값을 현재 schema에 안전하게 넣을 수 없다고 확인된 경우에만 별도
schema 변경 계획을 먼저 열고, 이 작업에서 JSONB나 수기 추정으로 우회하지 않습니다.

## 현재 위치와 다음 인계

| 단계 | 상태 | 결과와 다음 전환 |
| --- | --- | --- |
| 1. 016 database·조회 API | 완료 | 로컬에서 검증된 11개 table과 공개 조회 계약을 017의 입력으로 사용합니다. |
| 2. 최초 공개 범위 결정 | 완료 | 현대·기아 현재 모델, 이미지 제외, 유료 후보는 계약 없이 비교한다는 범위를 고정했습니다. |
| 3. 확보 계획·provenance 계약 | 완료 | source 적격성, mapping, coverage와 권한 경계를 검증된 `Proposed` checkpoint로 남겼습니다. |
| 4. 공개 source 사전 조사 | 완료 | 대표 4모델의 현재 판매 여부와 공개된 가격·제원·인증 정보, 제조사·공공·유료 후보의 권리 공백을 확인했습니다. |
| 5. exact source qualification | **현재** | 원문을 revision별로 확보해 checksum·recipe를 기록하고 대표 4모델의 exact column mapping, 재실행과 실패 표본을 만듭니다. |
| 6. 입력 방식·계획 확정 | 후속 | 실제 표본 규모와 오류를 근거로 수동 Flyway SQL과 구조화 입력+generator를 비교하고, 필요한 외부 회신까지 닫은 뒤 `$verify-plan`을 통과합니다. |
| 7. 전체 데이터 작성 | 후속 | 전체 inventory·mapping·정형 입력과 versioned data migration을 만듭니다. |
| 8. 결과 검증·영구 문서 반영 | 후속 | 원문↔DB↔API 누락 0건, 정정 경로, 전체 check와 `$verify-dev`를 닫고 SOT·runbook을 동기화합니다. |
| 9. Git·환경 적용 | 후속 | 검증된 exact path를 local commit합니다. push·staging·production은 각각 별도 권한이 있을 때만 수행합니다. |

현재 단계는 **미완성**입니다. 공개 웹 조사만으로는 source manifest의 exact bytes·checksum·
revision, 네 모델의 원문 위치→column mapping과 재실행 결과가 생기지 않습니다. 기술
qualification은 계속 수행할 수 있지만, 제조사 외부 공개 권리와 한국에너지공단 exact dataset
조건이 닫히기 전에는 실제 값을 Git·DB·API에 넣지 않습니다. 외부 확인이 필요한 대상과 질문은
[017 자동차 카탈로그 초기 데이터 확보 설계](017-car-catalog-initial-data-design.md)의 요청
package에 준비했으며 아직 발송하지 않았습니다.

### 즉시 다음 실행 단위

- **실행자**: Codex가 공개 약관·저작권 안내·dataset metadata를 source별로 대조합니다.
- **입력**: 설계 문서의 manifest 후보와 공개되어 있는 이용조건입니다. `ACQUIRE`가
  `PENDING`인 동안 원문 파일을 repository나 영구 작업 artifact로 저장하지 않습니다.
- **첫 결과**: 각 source의 `ACQUIRE`·임시 처리·checksum 계산 가능 여부와 exact 근거를
  rights matrix에 정규화하고, 문의가 필요한 항목과 문의 없이 진행 가능한 항목을 분리합니다.
- **관찰 결과**: source별 판정·근거 URL·조항·확인 시각, `APPROVED` 또는 남은 `PENDING`과
  blocker가 보여야 합니다.
- **최초 consumer**: 같은 작업의 manifest·mapping 작성 단계가 `APPROVED` source만 입력으로
  받아 exact bytes·checksum·재실행 evidence를 만듭니다.
- **사용자 작업**: 이 첫 단위에는 없습니다. 외부 문의가 필요한 `PENDING`만 남았을 때 문의
  발송 권한을 별도로 요청합니다.

## 확정 범위와 결정

| 결정 | 첫 공개 범위 | 영향과 재개방 조건 |
| --- | --- | --- |
| 브랜드·차종 | 현대자동차·기아의 국내 일반 개인 구매 승용·SUV·MPV | 다른 브랜드와 상용·특장·전용 판매 구성은 source·권리·identity·coverage를 새로 검증한 증분 작업으로 확장합니다. |
| 기간 | qualification 시작일에 판매 중인 현재 모델 연식 | 단종 모델·이전 세대는 현재 범위의 수집·정정 경로를 닫은 뒤 별도 Flyway 증분으로 추가합니다. |
| 이미지 | 이미지 파일과 외부 이미지 URL을 모두 제외 | 복제·가공·배포 권리와 보관·삭제 정책이 별도 승인될 때만 다시 엽니다. |
| 유료 후보 | 공개 정보로 비교하며 표본·견적·계약·구매하지 않음 | KAIDA 같은 후보의 field·외부 공개·비용·종료 조건은 별도 권한을 받은 요청과 회신으로만 확정합니다. |
| provenance | 모든 적재 값에 exact source·수집 방식·변환 규칙·revision을 연결 | 연결되지 않은 값은 게시하지 않으며 정정·확장에도 같은 계약을 적용합니다. |

## source와 입력 결정의 현재 결론

- 기술 후보 조합은 제조사 공식 목록·가격표·catalog와 한국에너지공단 인증자료입니다.
- 현대·기아 공개 자료는 정확성 대조에는 쓸 수 있지만 외부 공개 재사용 권리가 확인되지 않아
  production source로는 `PENDING`입니다.
- 한국에너지공단 자료는 인증값 보완 후보이며, exact dataset revision·개별 이용허락·제3자
  권리와 EV 온도 조건을 확인해야 합니다.
- KAIDA Service A는 공개용으로 제외하고, Service B는 field dictionary·표본·재게시·종료 조건을
  확인하기 전까지 `PENDING`입니다.
- 입력 방식은 아직 선택하지 않습니다. 대표 4모델의 실제 inventory 행 수·mapping 복잡도·수동
  SQL 오류와 재실행 결과를 얻은 뒤 `검토된 수동 Flyway SQL`과 `구조화 입력+generator`를
  비교합니다.

상세 근거, representative observation, rights matrix와 대안·전환 조건은
[017 자동차 카탈로그 초기 데이터 확보 설계](017-car-catalog-initial-data-design.md)에서
확인합니다.

## 가져오는 흐름

```text
source 후보 발견
→ 공개 권리·수집 허용 범위를 행위별 판정
→ 허용된 방법으로 exact revision을 임시 확보
→ source identity·recipe·확인 시각·checksum·권리 근거를 manifest에 기록
→ 원문 위치·값·단위·조건 → 정확한 table.column mapping 작성
→ 대표 표본으로 identity·누락·단위·중복·재실행·revision 변경 검증
→ 실제 규모와 실패 증거로 입력 방식 결정
→ 전체 inventory와 권리 승인된 정형 입력 작성
→ 검토 가능한 versioned Flyway migration 작성
→ fresh DB와 직전 schema upgrade 검증
→ inventory↔DB↔공개 API 양방향 대조
→ 별도 권한을 받은 환경에 동일 migration 적용
```

- 외부 호출과 DB 변경을 한 transaction에 묶지 않습니다.
- 같은 checksum·적용일의 원문은 새 revision이나 migration을 만들지 않습니다.
- 검증 실패 후보는 일부 성공 행만 기존 공개본에 섞지 않고 전체 게시 대상에서 제외합니다.
- 정형 입력의 configuration은 기본적으로 `is_published=false`이며, 최종 coverage assertion을
  통과한 migration만 선택 범위를 공개합니다.
- 적용된 Flyway migration은 수정하지 않고, 정정은 새 source revision과 보상 migration으로
  수행합니다.
- 권리가 닫히기 전의 기술 검증에는 합성 fixture와 저장이 허용된 metadata만 사용합니다.

## source qualification 완료 조건

대표 K5·카니발·EV6·넥쏘 각각에 대해 다음이 모두 있어야 합니다.

- 목록·가격·catalog·제원·인증 source를 문서 단위로 분리한 stable manifest ID
- exact URL 또는 dataset ID, 실제 수집 recipe, 확인·발행·적용 시각, SHA-256과 revision 관계
- `ACQUIRE`, `TRANSFORM`, 원문·추출값·파생값 저장, 외부 사실 공개, 원문 재배포, 종료 뒤
  보존의 행위별 판정
- 모델·연식·동력·variant·configuration의 stable repository code와 source code 대응
- 대표 필수값의 page/section/label/value/unit/condition→정확한 `table.column` 또는 JSONB path
- 정상 원문, 동일 원문 재실행, 필수값 누락, 단위 오류, 중복 identity와 새 revision의 결과
- `BLOCKED` mapping 0건 또는 전체 확대를 멈추는 명시적 blocker

가격 페이지의 “기준일”은 가격 적용 시작일이라는 공식 근거가 있을 때만
`effective_from`으로 사용합니다. 그렇지 않으면 확인일과 적용일을 구분해 `BLOCKED`로 둡니다.

## coverage 기준

### 분모

1. 공식 목록의 범위 안 브랜드·모델
2. 각 모델의 세대·현재 모델 연식
3. 가격표의 동력계통·차체·인승·트림 configuration
4. 모든 기본 가격과 유상 선택 품목
5. catalog의 필수 동력 장치와 주요 typed 제원
6. 공식 인증표의 측정 조건 행
7. 원문에 선언되고 권리가 승인된 기본 품목·보증·안전도

### 완료 조건

- 범위 안 모델·연식·configuration과 source 문서 누락 0건
- manifest recipe·revision·권리 근거 누락 0건
- typed field 값·단위·조건 mapping 누락 0건
- FK·연식·동력계통 오연결 0건
- 가격·옵션·인증 원문 count와 DB count 차이 0건
- 원문 값과 DB 정규화 값의 역대조 차이 0건
- `BLOCKED` 0건

공식 자료에 항목이 없다고 확인한 경우만 `NOT_PUBLISHED`를 허용합니다. 자료 자체를 찾지 못했거나
권리·조건을 복원하지 못한 경우는 완료가 아니라 `BLOCKED`입니다.

## 개발·적용 계획

### 작업 1. 대표 source를 qualification합니다

- 네 모델의 문서별 manifest와 exact representative mapping을 작성합니다.
- source bytes를 저장할 권리가 없으면 checksum 계산 뒤 폐기하고 허용된 metadata만 남깁니다.
- 같은 recipe 재실행과 source revision 변경, 실패 표본을 검증합니다.

### 작업 2. 외부 권리·계약 공백을 닫습니다

- 별도 권한을 받은 뒤 현대·기아의 사실 추출·저장·외부 공개·과거 revision 보존 범위를
  서면으로 확인합니다.
- 한국에너지공단 exact dataset의 이용허락·식별자·EV 조건을 확인합니다.
- 별도 비교 승인이 있으면 KAIDA Service B의 표본·field dictionary·견적·종료 조건을 받습니다.

### 작업 3. 입력 방식을 확정합니다

- 대표 inventory count, mapping 행 수, 예상 전체 행 수·SQL 크기와 수동 오류를 측정합니다.
- 수동 Flyway SQL, 기존 Gradle·Java 기능을 사용한 보조 변환, repository generator를 같은
  시작·종료 상태에서 비교합니다.
- custom tool이나 `check` 차단이 필요하면 도구 도입 gate와 사용자 선택을 별도로 통과합니다.
- 단일 migration의 예상 적용 시간·lock·복구 비용을 측정하고 분할 threshold를 고정합니다.

### 작업 4. 전체 inventory·mapping과 data migration을 작성합니다

- 모델 목록부터 연식별 가격·catalog·제원·인증 source를 등록합니다.
- 선택한 입력 방식으로 dependency 순서의 결정적 SQL을 만듭니다.
- 같은 입력 재실행, fresh DB와 현재 V17 DB upgrade에서 같은 결과를 검증합니다.

### 작업 5. coverage·API·정정 경로를 검증합니다

- inventory count와 DB count를 model year·configuration·fact group별로 비교합니다.
- DB 값을 source mapping으로 역조회하고 공개 API가 같은 범위를 반환하는지 확인합니다.
- 합성 fixture로 누락·단위·중복·FK·권리 미승인·부분 실패를 검증합니다.
- 실제 PostgreSQL migration test와 `./gradlew check`, `$verify-dev`를 통과합니다.

### 작업 6. 문서·Git·승인 환경 적용을 분리합니다

- 실제 공개 범위·source 확인일·수집·정정 절차를 domain·schema·product와 필요한 operations
  owner에 반영합니다.
- local commit, push, staging과 production 적용 권한을 각각 확인합니다.
- staging 적용을 승인받으면
  [420 GitOps application 배포와 rollback runbook](../../../operations/420-gitops-deployment-and-rollback-runbook.md)의
  migration Job·rollout 검증을 따릅니다.

## 전환 gate

- 대표 4모델 qualification과 입력 방식 결정 전에는 `Proposed`를 유지합니다.
- 권리·인증 조건·입력 방식·migration 분할 기준이 닫히고 `$verify-plan`을 통과해야
  `Planned`로 전환합니다.
- 전체 inventory와 data migration 구현을 시작할 때 `In Progress`로 전환합니다.
- 권리 허락이 거절되면 임의 축약·추정하지 않고 공식 공급자 조합 또는 제품 범위 변경을
  사용자 결정으로 다시 엽니다.

## 관련 문서

- **선행 schema·API**: [016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md)
- **source 조사·설계**: [017 자동차 카탈로그 초기 데이터 확보 설계](017-car-catalog-initial-data-design.md)
- **현재 업무 개념**: [130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md)
- **물리 DB 구조**: [110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)
- **과거 DB 설계 결정**: [016 신차 카탈로그 DB 설계 결정 기록](016-new-car-catalog-query-api-design.md)
- **제품 범위**: [200 제품 기능 범위](../../../product/200-feature-scope.md)
- **DB 변경**: [240 Flyway](../../../tech-stack/240-flyway.md),
  [250 PostgreSQL](../../../tech-stack/250-postgresql.md)
