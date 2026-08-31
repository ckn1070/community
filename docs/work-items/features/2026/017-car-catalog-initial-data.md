# 자동차 카탈로그 초기 데이터 구축

## 상태

Proposed

## 현재 위치와 선행 작업

이 작업은 [016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md)가 로컬에서
구현·검증한 11개 database table과 조회 기능을 입력 계약으로 사용합니다. 현재는 실제 공개할
범위를 현대자동차·기아의 현재 판매 모델로 제한하고 이미지 없이 시작하기로 확정했으며,
운영 데이터는 아직 없습니다. 이후 다른 브랜드와 과거 모델은 같은 source 적격성·mapping·
coverage gate를 통과하는 증분 작업으로 확장합니다.

현재 자동차 개념과 물리 target은 각각
[130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md)과
[110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)에서 확인합니다.

016의 아직 수행하지 않은 push 뒤 CI·Sonar 검증과 017의 데이터 확보는 서로 대신하지
않습니다. 확정한 공개 범위는 외부 문의·가입·견적 요청·계약·구매나 staging 적용 권한을
만들지 않습니다.

## 달성할 결과

확정한 대한민국 자동차 범위에서 외부 공개 권리가 확인된 공식 가격표·catalog·제원·인증
자료를 출처 명세(source manifest)와 원문→`table.column` 대응표로 고정하고, 016 schema에
맞는 정형 입력으로 변환합니다. Manifest에는 원문 위치뿐 아니라 실제 수집 방식·요청 조건·
적용일·확인 시각·checksum·source revision·권리 근거를 남깁니다. 모든 적재 값은 exact
source와 변환 규칙으로 역추적할 수 있어야 하며, 이후 브랜드·과거 연식을 추가할 때 같은
수집 절차와 판정 기록을 재사용합니다. 빠진 모델·연식·판매 구성·주요 값이 없는지 검수한 뒤
versioned Flyway data migration으로 적용합니다.

완료 상태는 “일부 대표 차량이 들어감”이 아니라 선택한 범위의 공식 목록과 DB 행을 양방향으로
대조해 누락이 0건인 상태입니다.

## 제외 범위

- runtime crawler, scheduler, 관리자 입력 API·CMS와 application writer 권한
- 사용·보관·재배포 권리가 확인되지 않은 원문 파일, 이미지와 실제 값
- 외부 기관 문의·가입·계약·구매, push와 staging·production 적용
- 재고·대리점·견적·보조금, 중고차 매물, 차량번호·VIN과 사용자 소유 차량 조회
- 016 schema의 선제 재설계와 모든 핵심 사실의 수정 시점별 감사 이력

대표 source에서 반복 핵심값을 현재 schema에 안전하게 넣을 수 없다고 확인된 경우에만 별도
schema 변경 계획을 먼저 열고, 이 작업에서 JSONB나 수기 추정으로 우회하지 않습니다.

## 현재 위치와 다음 인계

| 단계 | 상태 | 만드는 결과와 다음 전환 |
| --- | --- | --- |
| 1. 016 database·조회 API | 완료 | 로컬에서 검증된 11개 table과 공개 조회 계약을 017의 입력으로 사용합니다. 016의 push 뒤 검증은 별도 책임으로 남습니다. |
| 2. 최초 공개 범위 결정 | 완료 | 현대·기아 현재 모델, 이미지 제외, 유료 후보는 계약 없이 비교한다는 범위를 고정했습니다. |
| 3. 선택 반영·조사 계획 검증 | 완료 | 확정 결정을 provenance 계약과 후속 조사 단위에 반영해 검증된 `Proposed` local checkpoint로 남깁니다. |
| 4. 데이터 원천 적격성·대표 4모델 검증 | **현재** | 공개 원문으로 manifest·rights·field matrix를 만들고 사용·가공·저장·외부 공개 권리와 필드 대응을 확인합니다. 외부 문의는 별도 권한이 있을 때만 수행합니다. |
| 5. 구현 가능한 계획 확정 | 후속 | 조사 결과로 source 조합·입력 형식·배치 위치·변환 도구를 고정하고 `$verify-plan`을 통과해 `Planned`로 전환합니다. |
| 6. 전체 데이터 작성 | 후속 | 검토된 SQL 또는 정형 입력 변환 도구로 전체 목록·대응표·Flyway migration을 만듭니다. |
| 7. 결과 검증·영구 문서 반영 | 후속 | 원문↔DB↔API 누락 0건, 정정 경로, 전체 check와 `$verify-dev`를 닫고 domain·schema·product·operations SOT를 동기화합니다. |
| 8. Git·환경 적용 | 후속 | 검증된 결과를 exact path로 local commit합니다. push·staging·production은 각각 별도 권한이 있을 때만 적용·검증합니다. |
| 이미지 처리 | 해당 없음 | 첫 공개에는 이미지 파일과 외부 이미지 URL을 모두 넣지 않습니다. 별도 권리·보관 요구가 승인될 때만 증분 범위로 다시 엽니다. |
| 새 infrastructure | 해당 없음 | 초기 수집은 runtime crawler·관리자 writer·scheduler를 만들지 않으므로 새 운영 infrastructure를 요구하지 않습니다. |

현재 단계는 **미완성**입니다. 즉시 다음 작업의 실행자는 Agent이며, K5·카니발·EV6·넥쏘
대표 4모델의 공개 원문을 source manifest·rights matrix·field matrix에 등록해 source
조합과 입력 방식의 실제 근거를 만듭니다. 공개 자료만으로 권리가 닫히지 않는 항목은
`PENDING`으로 중단하고, 외부 문의·표본·견적 요청이 필요한 exact 대상과 질문을 사용자에게
별도 승인 단위로 제시합니다. 대표 4모델에서 source 조합과 입력 방식이 검증된 뒤에만
`Planned`로 전환합니다. 지금 사용자가 수행할 작업은 없습니다.

## 확정 범위와 결정

| 결정 | 첫 공개 범위 | 영향과 재개방 조건 |
| --- | --- | --- |
| 브랜드·차종 | 현대자동차·기아의 국내 일반 개인 구매 승용·SUV·MPV | 다른 브랜드는 source inventory·권리·identity·coverage를 새로 검증한 증분 작업으로 확장합니다. 상용·특장차, 렌터카 전용과 장애인 전용 판매 구성은 별도 포함 결정 전까지 넣지 않습니다. |
| 기간 | 조사 시작일에 판매 중인 현재 모델 연식 | 단종 모델·이전 세대는 첫 공개에서 제외하고, 현재 모델 수집·정정 경로를 닫은 뒤 별도 Flyway 증분으로 추가합니다. |
| 이미지 | 이미지 파일과 외부 이미지 URL을 모두 제외 | 구조화된 문자·숫자 데이터부터 닫습니다. 복제·가공·배포 권리와 보관·삭제 정책이 별도 승인될 때만 다시 엽니다. |
| 유료 후보 | 공개 정보·표본·견적·외부 사용 조건까지 비교하되 계약·구매하지 않음 | KAIDA 같은 후보의 필드·재배포·비용·종료 조건을 제조사·공공 source와 같은 matrix에서 비교합니다. 문의·표본·견적 요청, 계약과 구매는 각각 별도 실행 권한이 필요합니다. |
| provenance | 적재한 모든 값에 exact source·수집 방식·변환 규칙·revision을 연결 | 이후 데이터 추가와 정정 때 같은 수집 recipe를 재실행하고, source 변경과 DB 변경의 차이를 추적합니다. 연결되지 않은 값은 게시하지 않습니다. |

## 현재 확인한 source와 역할

공개 페이지와 API의 제공 범위·이용 조건은 바뀔 수 있으므로 실제 조사 시작일에 다시
확인합니다. 현재 확인한 후보의 역할은 다음과 같습니다.

| source | 확인한 범위 | 이번 계획의 역할 |
| --- | --- | --- |
| [현대자동차 카탈로그·가격표](https://www.hyundai.com/kr/ko/e/vehicles/catalog-price-download), [기아 카탈로그·가격표](https://www.kia.com/kr/vehicles/catalog-price) | 판매 모델의 가격표·catalog와 일부 상세 페이지에서 트림·기본품목·선택품목·제원을 공개함 | 제품·판매 사실의 정확성 대조용 1차 원문입니다. 양사 사이트의 copyright 표시는 공개 열람과 Carmunity의 재사용·재배포 허락이 같지 않음을 보여 주므로, 서면 허락이나 계약 범위를 확인하기 전까지 실제 값의 적재 source는 `PENDING`입니다. |
| [한국에너지공단 자동차 표시연비 정보](https://www.data.go.kr/dataset/15005963/openapi.do) | 모델명·제조사·차종·복합/도심/고속도로 연비·1회 충전 주행거리·등급을 제공함 | 연비와 전기차 주행거리의 공식 대조 source입니다. 모델·기술 변형 연결과 이용 조건은 표본에서 검증합니다. |
| [한국교통안전공단 신규등록정보 API](https://www.data.go.kr/data/15059401/openapi.do) | 차종·지역·연료·차명 코드별 월간 신규등록 통계를 제공함 | 공개 범위의 인기·누락 대조용입니다. 트림·옵션·판매가격의 원문으로 사용하지 않습니다. |
| [자동차365 자동차종합정보 개방 서비스](https://www.car365.go.kr/ccpt/comm/ntcn/ntcDetailView.do?_menuId=M640101000&bbsId=123&pstId=5550) | 특정 자동차의 기본·제원·이력·성능점검 API와 등록 생애주기 자료를 제공함 | 차량 단위 이력 source이므로 판매 중인 전체 신차 카탈로그 적재에서는 제외합니다. |
| [Hyundai Developers 데이터 API](https://developers.hyundai.com/web/v1/hyundai/data_api) | 동의한 사용자가 구매한 차량의 차명·트림·색상·옵션 등을 조회함 | 사용자 소유 차량 조회 API이므로 전체 신차 카탈로그 source에서 제외합니다. |
| [KAIDA Vehicle Registration DB Service](https://www.kaida.co.kr/en/service/dbService.do) | 신규 등록 국산·수입차의 제원·구매가격 등 45개 필드를 월간 Excel로 제공하고 외부 사용용 `Service B`를 안내함 | 유료 후보입니다. 공개 설명만으로는 트림·공식 판매가격·옵션·외부 재배포·계약 종료 뒤 처분 조건을 닫을 수 없으므로 표본·계약 조건을 확인하기 전까지 `PENDING`입니다. |

공개 웹페이지를 자동으로 읽을 수 있다는 기술적 가능성, `robots.txt` 허용이나 검색 노출은
복제·가공·저장·외부 공개 권리의 증거가 아닙니다. 권리가 닫히지 않은 원문을 production
crawler로 가져오는 경로는 선택하지 않습니다.

## source 적격성 gate

source마다 다음 값을 rights matrix에 기록하고 `APPROVED`인 용도로만 사용합니다.

| 항목 | 반드시 닫을 내용 |
| --- | --- |
| identity | stable source manifest ID, 제공자, 원문 종류, URL 또는 계약 dataset ID, 적용 시장·모델 연식 |
| 수집 recipe | 수동 공식 다운로드, 공식 API와 request parameter, 계약 delivery와 수령 파일 ID 중 실제 방법·도구·절차 |
| revision | source 발행·적용일, 실제 확인·수령 시각, checksum, 이전 revision과 `supersedes` 관계 |
| 권리 근거 | 약관·공공데이터 이용허락·서면 허락·계약의 문서명과 확인일 |
| 허용 행위 | 가져오기, 값 변환, 원문 보관, Carmunity API·화면 공개, 수정본 배포 가능 여부 |
| 보존·종료 | 원문과 변환 데이터의 보존 기간, 계약 종료·철회 때 삭제 또는 계속 제공 조건 |
| 갱신 계약 | 발행·갱신 주기, 정정·삭제 통지, 과거 revision 접근 가능 여부 |
| 필드 범위 | 11개 table과 표시 JSONB 중 실제 제공·미제공·추가 확인 필드 |
| 판정 | `APPROVED`, `RESTRICTED`, `REJECTED`, `PENDING`과 판정자·근거 |

`PENDING` source는 표본 구조를 합성 fixture로만 검증하고 실제 값을 Git·DB·API에 넣지
않습니다. 유료 후보 비교 범위에 들어 있어도 표본·견적 문의, 계약과 구매는 각각 별도 실행
권한을 받습니다.
Agent는 공개 약관·계약 문구와 기술적 사용 범위를 대조하고, 제품 owner 또는 지정된 계약·
법무 책임자가 외부 공개 근거를 최종 승인합니다. 견적·계약·비공개 표본 원문, credential과
secret은 Git에 저장하지 않고 승인된 보관 위치와 식별 정보만 rights matrix에 연결합니다.

## 공식 자료 inventory

브랜드마다 다음 자료가 있는지 모델 연식 단위로 기록합니다.

| source 종류 | 확인하는 값 | 주로 입력하는 테이블 |
| --- | --- | --- |
| 공식 모델 목록·heritage | 모델, 세대, 연식, 출시·종료 시점 | brand, model, generation, model_year |
| 공식 가격표 | 동력계통, 인승·트림, 기본 가격, 선택 품목·색상, 가격·선택 상태 적용 시작일 | powertrain, variant, configuration, price, option |
| 공식 catalog·제원 | 치수·중량·견인·루프 적재·회전반경·휠·타이어·동력 장치·성능·충전·기본 품목 | powertrain, component, variant, configuration |
| 정부 또는 제조사 공식 인증표 | 인증 기준과 조건별 연비·전비·수소 효율·CO₂·주행거리 | certification |
| 공식 보증·안전·media 자료 | 보증, 공인 안전도, 공개 권리 | configuration JSONB |

공식 자료는 runtime DB에 저장하거나 자동차 행의 FK로 연결하지 않습니다. 대신 inventory와
원문 항목 mapping을 version-controlled 자료로 함께 검토해, 하나의 DB 행이 여러 문서를
근거로 하더라도 각 원문 값과 대상 칼럼의 대응을 잃지 않게 합니다. 원문 파일 자체를
보존해야 한다는 요구가 생기면 URL metadata만 DB에 넣지 않고 별도 보관 위치·보존 기간·해시
검증을 함께 설계합니다.

각 inventory 항목은 stable source manifest ID, 원문 적용일, 실제 확인 시각, checksum,
source revision, rights matrix 판정과 실제 수집 recipe에 연결합니다. Recipe는 수동 공식
다운로드, 공식 API의 endpoint·request parameter, 계약 delivery의 dataset·수령 파일 ID처럼
같은 범위를 다시 확보하는 데 필요한 방법을 기록합니다. 같은 URL의 내용이 바뀌어도 새
원문인지 구분하고 이전 revision과의 `supersedes` 관계를 남겨야 하며, 원문 보관이 허용되지
않으면 파일을 Git에 넣지 않고 허용된 보관 위치와 checksum만 기록합니다.

새 브랜드·연식·정정 자료는 기존 recipe를 복사하는 대신 현재 source 조건으로 재실행해 새
revision을 등록합니다. URL, API parameter, delivery 형식이나 권리 조건이 바뀌면 기존
manifest를 덮어쓰지 않고 변경 이유와 새 recipe를 추가합니다.

검색 결과, 자동차 매체와 판매자가 옮겨 쓴 자료는 핵심 수치의 원본으로 사용하지 않습니다.
원문에 발행일이 없으면 날짜를 추정하지 않고 실제 확인일만 기록합니다.

## 원문 항목 mapping 계약

source를 읽은 뒤 다음 단위로 mapping 표를 작성합니다.

| key | 필수 내용 |
| --- | --- |
| 대상 | brand, model, generation, model_year와 configuration code |
| source identity | source manifest ID, source revision, 원문 checksum과 rights matrix 판정 |
| 수집 방식 | manifest의 수집 recipe와 이번 실행·수령 식별자 |
| 원문 위치 | source URL 또는 dataset ID·확인일·page 또는 section·원문 label |
| 원문 값 | 값, 단위와 인승·휠·카메라·온도 같은 조건 |
| DB 대상 | 정확한 `table.column` 또는 허용된 configuration JSONB 경로 |
| 정규화 값 | DB에 넣을 값과 변환 규칙 |
| 상태 | `MAPPED`, `NOT_PUBLISHED`, `OUT_OF_SCOPE`, `BLOCKED` |

typed column에 해당하는 값을 JSONB로 우회하지 않습니다. 예를 들어 EV 배터리 kWh·Ah·V는
component 칼럼, 카니발 휠·카메라별 연비는 certification 행, K5 트림 가격은 price 행에
mapping합니다. 원문에 있으나 현재 schema에 넣을 수 없는 반복 핵심값이 발견되면 data를 버리거나
EAV로 우회하지 않고 schema 변경 작업을 먼저 엽니다. 적재되는 모든 정규화 값은 이 mapping
중 하나와 연결되어야 하며, source·수집 방식·원문 위치·변환 규칙 중 하나라도 끊긴 값은
`BLOCKED`로 두고 게시하지 않습니다.

표시 이름을 identity로 사용하지 않습니다. 제조사가 공개한 안정된 code가 있으면 원문 code와
repository code의 대응을 남기고, 없으면 부모 code·시장·연식·edition·동력·차체·인승·판매
채널 조합으로 repository code를 고정합니다. 공개 UUID는 정형 입력에서 한 번 배정한 뒤 다시
생성하지 않으며, 내부 `BIGINT id`는 migration이 부모 code로 조회해 연결합니다.

### 주요 제원 정규화 규칙

| 원문 정보 | DB 대상 | 규칙 |
| --- | --- | --- |
| 배터리 gross·nominal 용량 | `car_powertrain_component.capacity_kwh` | 제조사가 총 용량에 사용한 gross 또는 nominal 원문 명칭을 mapping에 남깁니다. |
| 배터리 usable·net 용량 | `car_powertrain_component.usable_capacity_kwh` | 제조사가 별도로 공개한 값만 저장하며 총 용량에서 임의 계산하지 않습니다. |
| 차량 최대 AC·DC 수전 출력 | `max_ac_charge_kw`, `max_dc_charge_kw` | 대표 충전 시간의 충전기 출력과 구분합니다. |
| 대표 AC 충전 시간 | `ac_charge_minutes`, `ac_charge_from_percent`, `ac_charge_to_percent`, `ac_charge_power_kw` | 네 값이 모두 공개된 조건 중 충전기 출력이 가장 높은 한 건을 저장합니다. |
| 대표 DC 충전 시간 | `fast_charge_minutes`, `fast_charge_from_percent`, `fast_charge_to_percent`, `fast_charge_power_kw` | 제조사가 대표로 제시한 최단 조건을 우선하고, 복수이면 네 값이 모두 공개된 조건 중 충전기 출력이 가장 높은 한 건을 저장합니다. |
| 충전 단자 | `ac_charge_connector`, `dc_charge_connector` | 시장별 공식 명칭을 정규화하고 원문 명칭을 mapping에 남깁니다. |
| 제동·비제동 견인중량 | variant의 두 towing column | 조건을 합치지 않고 제조사가 각각 공개한 값만 저장합니다. |
| 루프 적재중량 | `max_roof_load_kg` | 정차 상태가 아닌 주행 중 허용값만 저장합니다. |
| 최소 회전반경 | `minimum_turning_radius_m` | 원문이 회전직경만 제공하면 2로 나눈 변환식과 원문 단위를 mapping에 기록합니다. |
| 인증 기준 | `car_certification.certification_standard` | `KOREA`, `WLTP`, `EPA`처럼 source가 선언한 기준을 정규화하며 추정하지 않습니다. |

대표 충전 조건이 여러 개인 사실만으로 행이나 별도 충전 테이블을 늘리지 않습니다. 첫 조회는
비교 가능한 대표 AC 한 건과 DC 한 건을 제공하고, 향후 조건별 충전 곡선·다중 시간 비교가 제품
요구가 될 때만 반복 구조를 다시 설계합니다.

## 대표 4모델 qualification

전체 범위 입력 전에 K5, 카니발, EV6, 넥쏘의 조사 시작일 기준 판매 모델 연식을 사용합니다.
이 네 모델은 두 제조사, ICE·HEV·BEV·FCEV, MPV와 조건별 인증을 한 번에 대조하기 위한 source
계약 표본이며 production 공개 범위를 대신하지 않습니다. 조사 시작일에 판매가 끝났거나 공식
자료가 없으면 같은 제조사·동력 형식의 현재 모델로 교체하고 교체 근거를 남깁니다.

qualification은 다음 세 결과 단위로 진행합니다.

1. 네 모델의 공식 원문 inventory, rights matrix와 필수 field matrix를 만듭니다.
2. 정상 원문, 같은 원문 재실행, 필수값 누락, 단위 오류, 중복 configuration과 새
   source revision을 manifest의 수집 recipe로 다시 확보해 공통 후보 형식으로 변환합니다.
3. source별 필드·권리·비용·갱신 책임을 비교하고 아래 입력 방식을 확정합니다.

다음 중 하나라도 남으면 전체 inventory로 확대하지 않습니다.

- 외부 공개 권리가 `PENDING`이거나 계약 종료 뒤 데이터 처분이 불명확함
- 모델·세대·연식·동력·기술 변형·판매 구성을 안정된 code로 연결할 수 없음
- 가격의 세금 기준·적용일이나 인증 측정 조건을 복원할 수 없음
- 016 typed column의 반복 핵심값을 JSONB나 수기 추정으로 우회해야 함
- 같은 원문 재실행이 다른 SQL·UUID·행 수를 만듦

## 가져오는 방법

```text
APPROVED source의 새 원문 발견
→ exact 수집 recipe로 원문을 확보하고 실행·수령 식별자를 기록
→ source identity·적용일·확인 시각·checksum·revision·권리 근거를 manifest에 고정
→ 선택 범위의 inventory 분모와 원문→table.column mapping 작성
→ 공개 DB와 분리된 정형 입력 후보로 변환
→ identity·필수값·단위·가격 기준·중복·FK·JSON 형식 사전 검증
→ 검토 가능한 Flyway SQL 생성 또는 직접 작성
→ 빈 PostgreSQL과 직전 schema가 적용된 PostgreSQL에서 migration 검증
→ inventory↔DB↔공개 API를 양방향 대조
→ 별도 권한을 받은 환경에 같은 migration 적용
```

- 외부 호출과 DB 변경을 한 transaction에 묶지 않습니다. 원문 수집·변환은 runtime DB 밖의
  후보에서 끝내고, 검증된 migration만 DB transaction으로 적용합니다.
- 같은 checksum·적용일의 원문은 새 후보나 migration을 만들지 않습니다. 내용이나 적용일이
  바뀌면 새 source revision과 `supersedes` 관계를 등록하고 기존 mapping과의 diff를 먼저
  만든 뒤 새 migration으로만 반영합니다.
- 검증 실패 후보는 전체를 게시 대상에서 제외하고 source·오류 위치·이유를 남깁니다. 일부
  성공 행만 기존 공개본에 섞지 않습니다.
- 정형 입력 후보의 판매 구성은 기본적으로 `is_published=false`입니다. 원문↔DB coverage와
  공개 API 계약을 모두 검증한 최종 migration만 선택 범위의 판매 구성을 `true`로 전환하며,
  그 migration이 실패하면 transaction 전체를 rollback해 일부 행만 공개하지 않습니다.
- 적용된 Flyway migration은 수정하지 않습니다. 잘못 적용된 데이터는 원인을 고친 보상
  migration으로 정정하고 원래 migration과 정정 이력을 보존합니다.
- 현재 016 schema는 가격·옵션 적용일은 보존하지만 모든 핵심 제원의 수정 전 값을 version별로
  복원하지 않습니다. 그 이력이 제품 요구가 되면 data migration으로 흉내 내지 않고 별도
  schema 변경을 먼저 엽니다.
- 실제 자료의 사용 권리가 닫히기 전 generator·검증기 개발에는 합성 fixture만 사용합니다.

## coverage 기준

### 분모

확정한 현대·기아 현재 모델 범위에 속하는 다음 항목을 모두 분모로 셉니다.

1. 공식 목록의 브랜드·모델
2. 각 모델의 세대·모델 연식
3. 가격표의 모든 대상 동력계통·차체·인승·트림 configuration
4. 각 configuration의 모든 기본 가격과 유상 선택 품목
5. catalog의 필수 동력 장치와 주요 typed 제원
6. 공식 인증표의 모든 측정 조건 행
7. 원문에 선언된 기본 품목·색상·보증·안전도

### 완료 조건

- 범위 안 모델·연식·configuration 누락 0건
- source 문서 누락 0건
- source manifest의 수집 recipe·revision·권리 근거 누락 0건
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

관리자 runtime import는 반복 운영 요구와 writer 권한·감사 설계를 별도 승인하기 전에는 선택하지
않습니다. custom generator도 inventory 규모와 반복 실패 증거 없이 미리 만들지 않습니다.
공식 데이터 공급자는 유료 후보 비교와 source 적격성 gate가 선택하는 upstream source이며,
SQL과 generator 중 하나를 고르는 이 입력 방식과 같은 대안으로 취급하지 않습니다.

**현재 추천:** 현대·기아의 전체 현재 모델처럼 반복 자료가 있는 범위이므로 `구조화 입력 +
repository generator`를 우선 후보로 검증합니다. Generator는 source를 직접 내려받는 crawler가
아니라, manifest와 mapping을 갖춘 검토된 정형 입력을 같은 SQL로 재현하고 사전 검증하는
build-time 도구입니다. 정형 입력의 exact 파일 형식·배치 경로와 generator entrypoint는 대표
4모델에서 SQL·diff 가독성, 중복·누락 검출과 라이선스 보관 조건을 대조한 결과로 작업 3에서
고정합니다.

## 개발·적용 계획

### 작업 1. 확정 범위를 조사 입력으로 고정합니다

- 현대·기아의 조사 시작일 현재 판매 모델 목록과 일반 개인 구매 승용·SUV·MPV 경계를
  inventory 분모로 씁니다.
- 이미지 제외와 유료 후보 비교 범위, 포함하지 않는 외부 문의·계약·구매 효과를 함께 고정합니다.
- 모든 값의 exact source·수집 방식·revision·변환 규칙을 manifest와 mapping의 필수값으로
  고정합니다.

완료 결과는 임의 판단 없이 각 차량·날짜·media·공급사 후보를 포함·제외할 수 있는 범위입니다.

### 작업 2. source 적격성과 대표 4모델을 검증합니다

- 후보 source마다 rights matrix를 작성하고 허용된 행위와 종료 조건을 확인합니다.
- K5·카니발·EV6·넥쏘로 source manifest, field matrix, identity와 실패 표본을 검증합니다.
- 세 qualification 결과를 모두 대조해 primary source 조합과 배제 후보·재검토 조건을
  판정합니다.

완료 결과는 전체 범위로 확대해도 되는 source 계약과 중단 기준의 실제 판정입니다.

### 작업 3. 입력 방식을 확정하고 검증합니다

- 확정 범위와 대표 표본을 기준으로 SQL과 구조화 입력+generator 후보를 비교합니다.
- 선택한 방법은 같은 입력의 결정적 SQL, stable UUID, 중복 code, FK, 단위, 가격,
  component 종류와 JSON 형식을 migration 작성 전에 검사합니다.
- 정형 입력을 선택하면 exact 파일 형식·배치 경로, generator entrypoint와 생성 SQL diff 검증을
  계획에 반영한 뒤 구현합니다.

완료 결과는 구현자가 임의 도구나 source 보관 위치를 다시 선택할 필요가 없는 입력 경로입니다.

### 작업 4. 전체 공식 inventory와 mapping을 작성합니다

- 모델 목록부터 시작해 연식별 가격표·catalog·제원·인증 source를 등록합니다.
- source의 configuration, 옵션, 조건별 인증 행을 먼저 세고 DB target을 지정합니다.
- inventory마다 checksum·적용일·source revision·수집 recipe·rights 판정과 정규화 identity를
  연결합니다.
- schema에 없는 반복 핵심값은 적재 전에 016 보완 대상으로 보고합니다.

완료 결과는 누락을 숨길 수 없는 분모와 source→table.column 대응표입니다.

### 작업 5. data migration을 작성합니다

- brand→model→generation→model_year→powertrain/component→variant→configuration→
  price/option/certification 순서로 입력합니다.
- 적용된 migration을 수정하지 않고 정정은 다음 번호 migration으로 작성합니다.
- 동일 input 재실행과 빈 DB·기존 DB upgrade에서 같은 결과가 나오는지 검증합니다.

완료 결과는 빈 DB와 기존 DB에 같은 결과를 만드는 versioned data migration입니다.

### 작업 6. coverage와 API를 양방향 검증합니다

- inventory count와 DB count를 model year·configuration·fact group별로 비교합니다.
- DB 값을 source mapping으로 역조회해 값·단위·조건을 다시 확인합니다.
- 브랜드→모델 목록→과거/현재 상세 API가 선택 범위를 빠짐없이 반환하는지 검증합니다.
- 잘못된 값의 보상 migration과 source revision 변경 표본으로 정정 경로를 검증합니다.
- 합성 fixture로 같은 입력의 동일 SQL, 누락·단위 오류·중복 code·깨진 FK·권리 미승인·부분
  변환 실패를 자동 검증하고 `./gradlew check` 전체를 통과합니다.
- 실제 PostgreSQL에서 V1부터의 빈 DB 적용과 V17까지 적용된 DB의 upgrade를 모두 검증합니다.

완료 결과는 위 coverage 조건을 모두 만족하는 실제 데이터 후보입니다.

### 작업 7. 영구 문서와 독립 검증을 닫습니다

- 실제 공개 브랜드·기간, source 확인일, 수집 recipe, 입력·정정 절차를
  domain·schema·product·operations 문서에 반영합니다.
- 전체 check와 `verify-dev` 독립 검증을 수행합니다.

이미지 관련 구현·운영 작업은 `해당 없음`으로 닫습니다. 향후 이미지를 다시 열면 허락 범위와
원본·변환본 보관·변환·삭제 정책을 구조화 데이터와 분리된 증분 계획에서 검증합니다.

### 작업 8. 별도 승인된 환경에 적용합니다

- local commit, push, staging과 production 적용은 각각 현재 요청의 별도 권한을 확인합니다.
- 적용한 exact migration과 DB row count, 공개 API coverage를 같은 환경에서 다시 확인합니다.

완료 결과는 선택한 환경에 검증된 동일 migration이 적용되고 공개 조회가 inventory와 일치하는
상태입니다.

## 전환·권한 경계

- 016은 schema·API 구현 작업의 상태와 결과를 추적하고, 017은 production data의 조사·검수·
  migration 작업을 추적합니다. 현재 schema 자체는
  [110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)가 소유합니다.
- 외부 데이터 공급 계약, custom 도구 도입, push와 staging 적용은 각각 별도 권한이 필요합니다.
- 대표 4모델 qualification에서 source 조합·수집 recipe·입력 방식까지 검증되기 전에는
  `Proposed`를 유지합니다. 이 결과가 `$verify-plan`을 통과하면 `Planned`, 전체 inventory와
  data migration 작성을 시작할 때 `In Progress`로 전환합니다.
- 첫 공개에는 이미지 파일과 외부 이미지 URL을 넣지 않습니다.
- 선택 범위의 공식 자료가 확보되지 않으면 임의 추정하지 않고 범위 축소 또는 공식 공급자
  도입을 사용자 결정으로 다시 엽니다.

## 관련 문서

- **선행 schema·API**: [016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md)
- **현재 업무 개념**: [130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md)
- **물리 DB 구조와 설계 이유**: [110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)
- **과거 DB 설계 결정**: [016 DB 설계 결정 기록](016-new-car-catalog-query-api-design.md)
- **제품 범위**: [200 제품 기능 범위](../../../product/200-feature-scope.md)
- **DB 변경**: [240 Flyway](../../../tech-stack/240-flyway.md),
  [250 PostgreSQL](../../../tech-stack/250-postgresql.md)
