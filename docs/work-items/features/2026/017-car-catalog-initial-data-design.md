# 자동차 카탈로그 초기 데이터 확보 설계

## 문서 역할과 현재 결론

이 문서는 [017 자동차 카탈로그 초기 데이터 구축](017-car-catalog-initial-data.md)의 상세
source 조사, 권리 판정, 원문→column mapping과 입력 방식 대안을 소유합니다. 작업 상태·범위·
단계·완료 조건은 017 본문이, 현재 물리 column은
[110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)가 소유합니다.

2026-08-31 공개 원문 사전 조사에서는 K5·카니발·EV6·넥쏘의 현재 판매 여부와 일부 대표값만
확인했습니다. 아직 exact source bytes·checksum·재실행 결과, 전체 필드 mapping과 외부 공개
권리를 확보하지 않았으므로 아래 source와 값은 **qualification 후보**입니다. 입력 도구와
migration 번호도 선택하지 않았으며 017은 `Proposed`입니다.

## source별 현재 조사 결과

아래 ID는 manifest 후보 이름입니다. Qualification 때 실제 확인 시각, 수집 recipe, HTTP 응답
또는 수령 파일의 SHA-256, 문서 발행·적용일, 이전 revision과의 관계를 채웁니다. 같은 URL의
내용이 바뀌면 새 revision으로 취급합니다.

| manifest 후보 ID | 대상 | exact source와 확인한 용도 | 현재 공백 |
| --- | --- | --- | --- |
| `hyundai-current-model-list-web` | 현대 현재 판매 분모 | [현대 catalog·가격표 목록](https://org1.hyundai.com/kr/ko/e/vehicles/catalog-price-download) | 목록 snapshot과 model year 판정 recipe 미작성 |
| `hyundai-nexo-2026-estimation-web` | 26MY 넥쏘 판매 상태 | [견적 모델 선택](https://www.hyundai.com/kr/ko/e/vehicles/estimation/model/making?model=HE01&modelCode=HEJJ5HAT1&purposeCode=J) | source code 의미와 revision 미확정 |
| `hyundai-nexo-2026-price-pdf` | 넥쏘 가격·옵션·인증값 | [공식 가격표 PDF](https://www.hyundai.com/contents/repn-car/catalog/the-all-new-nexo_price.pdf) | exact bytes·checksum·가격 적용일 의미 미확정 |
| `hyundai-nexo-current-catalog-pdf` | 넥쏘 제원 | [공식 영문 catalog PDF](https://www.hyundai.com/content/dam/hyundai/kr/ko/data/vehicles/catalog/en/nexo-catalog-eng.pdf) | 현재 국내 판매본과 revision 일치 검증 필요 |
| `hyundai-nexo-heritage-web` | 넥쏘 연료 저장 제원 대조 | [2025 NEXO Heritage](https://www.hyundai.com/kr/ko/brand/brandstory/heritage/2025-nexo-fc) | 현재 판매 제원 source로 사용할 수 있는지 미확정 |
| `kia-k5-2027-price-web` | 2027 K5 가격·구성 | [가격 페이지](https://www.kia.com/kr/vehicles/k5/price/private) | 2026-08-01 기준일과 `effective_from` 관계 미확정 |
| `kia-k5-2027-spec-web` | K5 제원·인증 | [제원 페이지](https://www.kia.com/kr/vehicles/k5/specification) | exact snapshot·rights 미확정 |
| `kia-carnival-2027-price-web` | 2027 카니발 가격·구성 | [가격 페이지](https://www.kia.com/kr/vehicles/carnival/price) | 2026-08-27 기준일과 `effective_from` 관계 미확정 |
| `kia-carnival-2027-spec-web` | 카니발 제원·인증 | [제원 페이지](https://www.kia.com/kr/vehicles/carnival/specification) | exact snapshot·rights 미확정 |
| `kia-ev6-2027-price-web` | 2027 EV6 가격·구성 | [가격 페이지](https://www.kia.com/kr/vehicles/ev6/price) | 2026-08-03 기준일과 `effective_from` 관계 미확정 |
| `kia-ev6-2027-spec-web` | EV6 battery·주행거리·전비 | [제원 페이지](https://www.kia.com/kr/vehicles/ev6/specification) | 인증값의 정상·저온 조건 label 미확정 |
| `kea-efficiency-file-dataset` | 정부 신고 인증값 대조 | [공공데이터포털 자동차 표시연비 자료](https://www.data.go.kr/dataset/15005963/fileData.do) | exact 파일·revision·개별 이용조건·EV 조건 필드 미확정 |
| `kaida-db-service` | 유료 대안 | [KAIDA 자동차등록통계 DB](https://www.kaida.co.kr/ko/service/dbService.do) | 표본·field dictionary·가격·외부 재게시·종료 조건 미확정 |

현대 사이트의 [저작권 안내](https://www.hyundai.com/kr/ko/copyright)는 권리 유보를 표시합니다.
기아 페이지 footer도 권리를 유보하지만 공개 열람이 사실 추출·저장·외부 API 공개 허락을
뜻하지는 않습니다. 공공데이터포털 일반 정책 역시 제3자 권리가 포함되면 별도 권한 확인을
요구합니다. 그러므로 현재 공개 source는 정확성 조사에만 사용하고 production 값으로 승인하지
않습니다.

## 대표 원문→column 관찰

아래는 전체 mapping이 아니라 qualification에서 재현해야 할 표본입니다. `source_location`은
페이지의 section·행 label까지 고정하고, exact value·unit·condition과 target을 한 행씩 연결합니다.

| 표본과 source location | 관찰값 | target 후보 | 미결정 |
| --- | --- | --- | --- |
| K5 제원 `정부 신고 연비 및 등급 > 1.6 가솔린 터보 > 17인치, 빌트인 캠 미적용` | 도심 12.1, 고속도로 16.1, 복합 13.7 km/L, CO₂ 121 g/km, 3등급, 공차중량 1,450 kg | `car_certification.wheel_size_inch=17`, `built_in_camera=false`, `city_efficiency=12.1`, `highway_efficiency=16.1`, `combined_efficiency=13.7`, `co2_g_per_km=121`, `efficiency_grade=3`, `curb_weight_kg=1450`, `efficiency_unit=KM_PER_L` | source revision·rights |
| 카니발 제원 `정부 신고 연비 및 등급 > 1.6 터보 하이브리드 > 9인승, 18인치, 빌트인 캠 미적용` | 도심 14.6, 고속도로 13.3, 복합 14.0 km/L, CO₂ 116 g/km, 2등급, 공차중량 2,155 kg | `car_vehicle_variant.seating_capacity=9`; `car_certification.wheel_size_inch=18`, `built_in_camera=false`와 대응 효율·CO₂·등급·중량 column | 같은 이름 configuration의 stable code와 rights |
| EV6 제원 `배터리 > 스탠다드`와 `정부 신고 연비 및 등급 > 스탠다드 2WD, 19인치` | 62.9 kWh; 복합 5.5, 도심 6.2, 고속도로 4.8 km/kWh; 복합 395, 도심 440, 고속도로 339 km; 523 V, 120.6 Ah, 1,885 kg | `car_powertrain_component.capacity_kwh=62.9`; 대응 battery component의 `voltage_v=523`, `capacity_ah=120.6`; 대응 `car_certification` 효율·주행거리·중량 column | 공개 표에 정상·저온 label이 없어 `temperature_condition`은 `BLOCKED` |
| 넥쏘 가격표 PDF p.1 `Exclusive`와 p.4 `정부 신고 연비 및 등급 > 18인치` | 세제혜택 전 80,410,000원, 후 76,435,000원; 도심 113.7, 고속도로 100.9, 복합 107.6 km/kg; 1,870 kg | `car_configuration_price.price_before_tax_benefit=80410000`, `price_after_tax_benefit=76435000`; 대응 `car_certification` 효율·중량 column | PDF page 기준 재확인, `effective_from`, 주행거리와 연료용량의 exact current source mapping |

가격 페이지의 “기준일”은 가격 적용 시작일이라는 공식 근거가 있을 때만
`car_configuration_price.effective_from`으로 변환합니다. 확인일·발행일·적용일을 서로 대체하지 않으며,
근거가 없으면 해당 가격 mapping은 `BLOCKED`입니다. 넥쏘 heritage에 보이는 6.69 kg 같은 값도
현재 판매 catalog의 같은 조건임을 입증한 뒤에만 hydrogen tank 역할의
`car_powertrain_component.capacity_kg`에 넣습니다.

## 행위별 rights matrix

한 source의 허락을 하나의 `APPROVED`로 축약하지 않습니다. 최소한 다음 행위를 각각
`APPROVED`, `REJECTED`, `PENDING`, `NOT_APPLICABLE`로 판정하고 exact 조항·회신·version을
근거로 연결합니다.

| source | `ACQUIRE` | `TRANSFORM` | `STORE_RAW` | `STORE_EXTRACTED_FACT` | `STORE_DERIVED_FACT` | `PUBLISH_FACT` | `REDISTRIBUTE_RAW` | `RETAIN_AFTER_TERMINATION` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 현대·기아 공개 자료 | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` |
| 한국에너지공단 file dataset | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` |
| KAIDA Service A | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `REJECTED` | `REJECTED` | `PENDING` |
| KAIDA Service B | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` | `PENDING` |

`STORE_RAW`가 거절되고 checksum 계산만 허용되면 원문은 임시 처리 뒤 폐기하고 허용된 source
identity·checksum·mapping metadata만 보존합니다. `STORE_EXTRACTED_FACT`와
`STORE_DERIVED_FACT` 중 하나라도 닫히지 않으면 그 source의 실제 값은 migration에 넣지 않습니다.
먼저 공개 약관·저작권 안내·dataset metadata만으로 `ACQUIRE`와 임시 처리 허용 범위를
판정합니다. `ACQUIRE=APPROVED`인 source만 exact bytes·checksum 수집으로 진행하고,
`PENDING`이면 외부 문의 권한 전까지 공개 metadata 정리와 합성 fixture 검증만 수행합니다.

## qualification 산출물의 논리 계약

물리 파일 형식과 위치는 입력 방식 결정 뒤 고정하지만, 어떤 대안을 택해도 다음 정보를 잃으면
안 됩니다.

- **manifest**: stable source ID, provider, exact URL/dataset ID, acquisition recipe, observed/issued/
  effective 시각, media type, SHA-256, revision·supersedes, rights evidence version
- **rights**: source ID, 행위, 판정, 허용 범위·지역·기간·귀속·종료 조건, evidence location,
  reviewer와 판정 시각
- **identity**: source의 model·model year·powertrain·body·seating·wheel·camera·trim code와
  repository stable code, 승인자, 모호성·충돌 결과
- **mapping**: source ID·revision, page/section/label, raw value·unit·condition, transform rule,
  exact `table.column` 또는 JSONB path, normalized value, 상태와 blocker
- **coverage**: 공식 inventory 분모, source count, expected DB count, actual count, 양방향 차이
- **run evidence**: tool/version, command recipe, input checksum, output checksum·row count,
  duration, warnings/errors, 이전 run과의 diff

각 필드는 type·nullability·정렬 기준을 정합니다. Stable code와 생성 SQL은 입력 순서와 무관하게
결정적이어야 하며, 누락·중복·단위 오류·모호한 identity·권리 미승인·알 수 없는 enum은 명시적
오류로 끝나야 합니다. Variant key는 실제 schema의 identity column 조합으로 만들며 임의
`<CONDITION_SUFFIX>`를 덧붙이지 않습니다.

## 입력 방식 대안과 결정 증거

runtime crawler·관리자 import는 현재 범위가 아닙니다. 전체 확대 전에 같은 대표 4모델 입력과
동일한 기대 DB 상태를 대상으로 아래 세 대안을 비교합니다.

| 대안 | 장점 | 비용·위험 | 채택 조건 |
| --- | --- | --- | --- |
| 검토된 수동 Flyway SQL | 새 도구가 없고 작은 범위에서 diff가 직접 보임 | 반복·오기·stable UUID·coverage 누락 위험 | 실제 전체 예상 규모가 작고 표본 수동 오류가 없으며 검토 비용이 가장 낮음 |
| 구조화 입력 + 기존 Gradle·Java 보조 | schema 검증과 결정적 변환을 재사용할 수 있음 | build 코드 경계와 입력 형식 유지 책임 | 기존 기능만으로 required validation·determinism을 충족하고 새 custom tool이 불필요함 |
| 구조화 입력 + repository generator | 반복 mapping·검증·SQL 생성 자동화 | 도구 자체 테스트·owner·업그레이드·폐기 비용 | 표본에서 반복 가능한 수동 오류와 규모가 확인되고 기존 기능으로 닫히지 않음 |

대안별로 representative inventory·mapping 행 수, 생성 SQL line·byte 수, 사람이 발견한 오류 수,
동일 입력 2회 output diff, validation false positive/negative, compile·실행 시간, PostgreSQL 적용
시간·lock과 rollback 비용을 기록합니다. 새 generator나 `check` hard gate가 우세하면
[130 도구 도입 워크플로우](../../../workflow/130-tool-adoption-workflow.md)의 기존 기능 대조,
구현·운영 owner, 실패·우회·제거 경로를 제시하고 사용자 선택을 받은 뒤에만 채택합니다.

Migration 번호는 구현 직전 repository의 다음 미사용 번호를 확인해 배정합니다. 단일 파일과
분할 방식도 대표 결과를 전체 inventory로 외삽한 SQL 크기·적용 시간·lock·review/rollback
경계를 비교해 threshold를 먼저 기록한 뒤 결정합니다. 현재 보이는 `V18`은 예약된 계약이
아닙니다.

## 외부 확인 package

아래 질문은 준비만 했으며 아직 발송하지 않았습니다. 문의·표본·견적·계약·구매는 각각 현재
사용자의 별도 권한이 있어야 합니다.

### 현대자동차·기아

- 공식 목록·가격표·catalog·제원·인증표의 자동 또는 수동 취득 허용 방법과 호출 제한
- 원문 보관, 사실 추출, 정규화·파생, Carmunity DB 저장과 외부 API 공개 허용 범위
- 출처 표시 문구, 상표 표기, 지역·기간 제한, 갱신·철회와 과거 revision 보존·삭제 조건

### 한국에너지공단·공공데이터포털

- 사용할 exact dataset ID·파일 revision·이용허락과 제3자 권리, 귀속 문구
- model year·동력·구동·휠·카메라·인승·온도 조건을 식별할 exact field와 값 정의
- 정정·갱신 주기, 과거 파일 보관과 추출·파생 사실의 외부 API 공개 조건

### KAIDA Service B

- 45개 field dictionary와 현대·기아 현재 모델 representative sample 제공 가능 여부
- 트림·옵션·가격·제원·인증 조건 coverage와 stable identifier
- 행 단위 사실·집계·파생값의 외부 API 재게시, 원문 보관과 계약 종료 뒤 보존·삭제 조건
- 초기·정기 비용, 갱신 주기, rate/file delivery, 정정 SLA와 source provenance

## `Planned` 전환에 필요한 다음 결과

1. 대표 4모델의 exact manifest·checksum·재실행 evidence를 작성합니다.
2. 위 대표 관찰을 포함한 필수 field의 page/section/label→column mapping을 완성합니다.
3. EV 온도 조건, 가격 적용일과 넥쏘 주행거리·연료용량을 `APPROVED` 또는 `BLOCKED`로 판정합니다.
4. 권리 문의가 필요 없는 기술 evidence를 먼저 만들고, 별도 권한 뒤 외부 rights 공백을 닫습니다.
5. 실제 규모·오류·시간 evidence로 입력 방식과 migration 분할 threshold를 결정합니다.
6. 확정한 contract·task·failure/rollback 기준으로 `$verify-plan` 독립 검증을 통과합니다.

이 결과 전에는 전체 데이터 작성, custom generator 구현, migration 번호 예약과 실제 값 적재를
시작하지 않습니다.

## 관련 문서

- **작업 상태·범위·완료 조건**: [017 자동차 카탈로그 초기 데이터 구축](017-car-catalog-initial-data.md)
- **선행 schema·API**: [016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md)
- **현재 업무 개념**: [130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md)
- **물리 DB 구조**: [110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)
- **DB 변경 방식**: [240 Flyway](../../../tech-stack/240-flyway.md),
  [250 PostgreSQL](../../../tech-stack/250-postgresql.md)
- **도구 선택 gate**: [130 도구 도입 워크플로우](../../../workflow/130-tool-adoption-workflow.md)
