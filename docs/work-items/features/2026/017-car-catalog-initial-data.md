# 자동차 카탈로그 초기 데이터 구축

## 상태

Proposed

## 현재 위치와 선행 작업

이 작업은 [016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md)의 11개 테이블과
조회 기능이 구현된 뒤 시작합니다. 현재는 실제 공개할 브랜드 범위와 과거 기간이 정해지지
않았으며 production data도 없습니다.

016의 schema 개발은 이 결정을 기다리지 않고 먼저 진행합니다. 실제 데이터 작업을 시작할
때는 아래 U1과 U2를 사용자가 확정해야 합니다.

## 달성할 결과

선택한 대한민국 자동차 범위의 공식 가격표·catalog·제원·인증 자료를 016 schema에 mapping하고,
빠진 모델·연식·판매 구성·주요 값이 없는지 검수한 뒤 versioned Flyway data migration으로
적용합니다.

완료 상태는 “일부 대표 차량이 들어감”이 아니라 선택한 범위의 공식 목록과 DB 행을 양방향으로
대조해 누락이 0건인 상태입니다.

## 전체 흐름

| 단계 | 만드는 결과 | 현재 상태 |
| --- | --- | --- |
| 1. 016 schema·조회 구현 | 실제 데이터를 받을 11개 테이블과 API | 선행 작업 |
| 2. 공개 범위 결정 | U1 브랜드와 U2 과거 기간 | **다음 사용자 결정** |
| 3. 공식 source inventory | 모델·연식·가격표·catalog·제원·인증 목록 | 후속 |
| 4. source→table mapping | 원문 항목과 정확한 table.column 대응표 | 후속 |
| 5. 입력 방식 결정 | 검토된 SQL 또는 검증 가능한 최소 입력 도구 | 후속 |
| 6. data migration 작성 | 자동차 행을 FK 의존 순서로 입력 | 후속 |
| 7. coverage 검증 | 범위·값·관계·단위·source 누락 0건 | 후속 |
| 8. 문서·독립 검증 | 공개 범위·정정 절차와 최종 감사 | 후속 |

## U1. 최초 공개 브랜드 범위

다음 중 하나를 선택해야 합니다.

| 선택 | 범위 | 영향 |
| --- | --- | --- |
| A | 현대자동차·기아의 국내 일반 개인 구매 승용·SUV·MPV | ICE·HEV·BEV·FCEV와 인승 변형을 한 번에 검증하지만 자료량이 큼 |
| B | 현대자동차만 | 범위가 작지만 기아 가격표 형식과 MPV 대표 자료가 빠짐 |
| C | 기아만 | K5·카니발·EV6를 포함할 수 있지만 FCEV 대표가 빠짐 |
| D | 사용자가 지정한 브랜드·모델 목록 | 우선순위를 정확히 반영하지만 exact 목록이 필요함 |

상용·특장차, 렌터카 전용과 장애인 전용 configuration을 포함할지는 U1 exact 범위에 함께
기록합니다. 기록하지 않은 범위를 임의로 포함하지 않습니다.

## U2. 과거 데이터 기간

| 선택 | 범위 | 영향 |
| --- | --- | --- |
| A | 현재 연식과 바로 이전 세대 | 커뮤니티에서 최근 단종 차량을 참조할 수 있고 조사량을 제한함 |
| B | 사용자가 지정한 시작일 이후 | 완료 경계가 명확하지만 exact 시작일이 필요함 |
| C | 확보 가능한 공식 digital source 전체 | 최대 범위지만 먼저 source 발견 조사를 한 뒤 가장 오래된 포함일과 공백을 다시 확정해야 함 |

U2-C는 “찾히는 만큼”을 완료 조건으로 사용하지 않습니다. discovery 결과의 정확한 시작일과
source 공백을 사용자에게 다시 제시하고 2차 범위를 확정한 뒤 적재합니다.

## 공식 자료 inventory

브랜드마다 다음 자료가 있는지 모델 연식 단위로 기록합니다.

| source 종류 | 확인하는 값 | 주로 입력하는 테이블 |
| --- | --- | --- |
| 공식 모델 목록·heritage | 모델, 세대, 연식, 출시·종료 시점 | brand, model, generation, model_year |
| 공식 가격표 | 동력계통, 인승·트림, 기본 가격, 선택 품목·색상, 가격·선택 상태 적용 시작일 | powertrain, variant, configuration, price, option |
| 공식 catalog·제원 | 치수·중량·휠·타이어·동력 장치·성능·충전·기본 품목 | powertrain, component, variant, configuration |
| 정부 또는 제조사 공식 인증표 | 조건별 연비·전비·수소 효율·CO₂·주행거리 | certification |
| 공식 보증·안전·media 자료 | 보증, 공인 안전도, 공개 권리 | configuration JSONB |

공식 자료는 runtime DB에 저장하거나 자동차 행의 FK로 연결하지 않습니다. 대신 inventory와
원문 항목 mapping을 version-controlled 자료로 함께 검토해, 하나의 DB 행이 여러 문서를
근거로 하더라도 각 원문 값과 대상 칼럼의 대응을 잃지 않게 합니다. 원문 파일 자체를
보존해야 한다는 요구가 생기면 URL metadata만 DB에 넣지 않고 별도 보관 위치·보존 기간·해시
검증을 함께 설계합니다.

검색 결과, 자동차 매체와 판매자가 옮겨 쓴 자료는 핵심 수치의 원본으로 사용하지 않습니다.
원문에 발행일이 없으면 날짜를 추정하지 않고 실제 확인일만 기록합니다.

## 원문 항목 mapping 계약

source를 읽은 뒤 다음 단위로 mapping 표를 작성합니다.

| key | 필수 내용 |
| --- | --- |
| 대상 | brand, model, generation, model_year와 configuration code |
| 원문 위치 | source URL·확인일·page 또는 section·원문 label |
| 원문 값 | 값, 단위와 인승·휠·카메라·온도 같은 조건 |
| DB 대상 | 정확한 `table.column` 또는 허용된 configuration JSONB 경로 |
| 정규화 값 | DB에 넣을 값과 변환 규칙 |
| 상태 | `MAPPED`, `NOT_PUBLISHED`, `OUT_OF_SCOPE`, `BLOCKED` |

typed column에 해당하는 값을 JSONB로 우회하지 않습니다. 예를 들어 EV 배터리 kWh·Ah·V는
component 칼럼, 카니발 휠·카메라별 연비는 certification 행, K5 트림 가격은 price 행에
mapping합니다. 원문에 있으나 현재 schema에 넣을 수 없는 반복 핵심값이 발견되면 data를 버리거나
EAV로 우회하지 않고 016 schema 보완을 먼저 엽니다.

## coverage 기준

### 분모

U1·U2 범위에 속하는 다음 항목을 모두 분모로 셉니다.

1. 공식 목록의 브랜드·모델
2. 각 모델의 세대·모델 연식
3. 가격표의 모든 대상 동력계통·차체·인승·트림 configuration
4. 각 configuration의 모든 기본 가격과 유상 선택 품목
5. catalog의 필수 동력 장치와 주요 typed 제원
6. 공식 인증표의 모든 측정 조건 행
7. 원문에 선언된 기본 품목·색상·보증·안전도·공개 가능한 media

### 완료 조건

- 범위 안 모델·연식·configuration 누락 0건
- source 문서 누락 0건
- typed field 값·단위·조건 mapping 누락 0건
- FK가 끊기거나 다른 연식·동력계통에 연결된 행 0건
- 가격표의 트림·가격·옵션 수와 DB 행 수 불일치 0건
- 인증표의 조건 행과 DB certification 행 불일치 0건
- 원문 값과 DB 정규화 값을 역대조한 차이 0건
- `BLOCKED` 0건

공식 자료를 확인했지만 항목이 공개되지 않은 경우만 `NOT_PUBLISHED`를 허용합니다. 자료 자체를
찾지 못한 경우는 완료가 아니라 `BLOCKED`입니다. 누락된 대상 행을 inventory에서 지워 coverage
100%를 만들지 않습니다.

## 입력 방식 결정

inventory와 mapping의 실제 규모를 본 뒤 다음 중 가장 단순한 방법을 선택합니다.

| 방법 | 적합한 조건 | 확인할 위험 |
| --- | --- | --- |
| 검토된 Flyway data SQL | 범위가 작고 일회성 정정이 대부분 | 긴 SQL 오기와 code 중복 |
| 구조화 입력 + repository generator | 같은 문서 형식을 여러 연식에 반복하고 수동 SQL 오류가 실제로 큼 | generator 자체 검증·유지 비용 |
| 공식 데이터 공급자 | 수동 정규화가 지속 불가능하고 계약된 source가 있음 | 비용·license·중단·형식 변경 |

관리자 runtime import는 반복 운영 요구와 writer 권한·감사 설계를 별도 승인하기 전에는 선택하지
않습니다. custom generator도 inventory 규모와 반복 실패 증거 없이 미리 만들지 않습니다.

## 개발·적용 계획

### 작업 1. U1·U2를 확정합니다

- 브랜드·모델 종류, 판매 채널, 상용·특장·전용 configuration 포함 여부를 exact 목록으로 씁니다.
- U2-B는 시작일, U2-C는 discovery 후 2차 시작일과 공백 결정을 닫습니다.

완료 결과는 임의 판단 없이 각 차량과 날짜를 포함·제외할 수 있는 범위입니다.

### 작업 2. 공식 inventory와 mapping을 작성합니다

- 모델 목록부터 시작해 연식별 가격표·catalog·제원·인증 source를 등록합니다.
- source의 configuration, 옵션, 조건별 인증 행을 먼저 세고 DB target을 지정합니다.
- schema에 없는 반복 핵심값은 적재 전에 016 보완 대상으로 보고합니다.

완료 결과는 누락을 숨길 수 없는 분모와 source→table.column 대응표입니다.

### 작업 3. 입력 방식을 확정하고 검증합니다

- SQL과 generator 후보를 같은 입력·출력으로 비교합니다.
- 선택한 방법은 중복 code, FK, 단위, 가격, component 종류와 JSON 형식을 입력 전에 검사합니다.

완료 결과는 구현자가 임의 도구를 다시 선택할 필요가 없는 입력 경로입니다.

### 작업 4. data migration을 작성합니다

- brand→model→generation→model_year→powertrain/component→variant→configuration→
  price/option/certification 순서로 입력합니다.
- 적용된 migration을 수정하지 않고 정정은 다음 번호 migration으로 작성합니다.

완료 결과는 빈 DB와 기존 DB에 같은 결과를 만드는 versioned data migration입니다.

### 작업 5. coverage와 API를 양방향 검증합니다

- inventory count와 DB count를 model year·configuration·fact group별로 비교합니다.
- DB 값을 source mapping으로 역조회해 값·단위·조건을 다시 확인합니다.
- 브랜드→모델 목록→과거/현재 상세 API가 선택 범위를 빠짐없이 반환하는지 검증합니다.

완료 결과는 위 coverage 조건을 모두 만족하는 실제 데이터 후보입니다.

### 작업 6. 영구 문서와 독립 검증을 닫습니다

- 실제 공개 브랜드·기간, source 확인일, 입력·정정 절차를 domain·schema·product·operations
  문서에 반영합니다.
- 전체 check와 `verify-dev` 독립 검증을 수행합니다.

## 전환·권한 경계

- 016은 schema와 API, 017은 production data migration을 소유합니다.
- 외부 데이터 공급 계약, custom 도구 도입, push와 staging 적용은 각각 별도 권한이 필요합니다.
- 권리가 확인되지 않은 media는 URL을 공개 data로 넣지 않습니다.
- 선택 범위의 공식 자료가 확보되지 않으면 임의 추정하지 않고 범위 축소 또는 공식 공급자
  도입을 사용자 결정으로 다시 엽니다.

## 관련 문서

- **선행 schema·API**: [016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md)
- **물리 DB 설계**: [016 신차 카탈로그 조회 기능 DB 설계](016-new-car-catalog-query-api-design.md)
- **제품 범위**: [200 제품 기능 범위](../../../product/200-feature-scope.md)
- **DB 변경**: [240 Flyway](../../../tech-stack/240-flyway.md),
  [250 PostgreSQL](../../../tech-stack/250-postgresql.md)
