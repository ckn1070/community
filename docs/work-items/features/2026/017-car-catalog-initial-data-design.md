# 자동차 카탈로그 초기 데이터 확보 설계

## 문서 역할과 현재 결론

이 문서는 [017 자동차 카탈로그 초기 데이터 구축](017-car-catalog-initial-data.md)의 상세
source 조사, 권리 판정, 원문→column mapping과 입력 방식 대안을 소유합니다. 작업 상태·범위·
단계·완료 조건은 017 본문이, 현재 물리 column은
[110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)가 소유합니다.

2026-08-31 공개 원문 사전 조사에서는 K5·카니발·EV6·넥쏘의 현재 판매 여부와 일부 대표값을
확인했고, 한국에너지공단 file dataset의 exact source bytes·checksum과 대표 후보 168행을
확보했습니다. 제조사 source의 외부 공개 권리, 전체 필드 mapping과 재실행 결과는 아직
확보하지 않았으므로 실제 DB 값은 계속 **qualification 후보**입니다. 입력 도구와 migration
번호도 선택하지 않았으며 017은 `Proposed`입니다.

## source별 현재 조사 결과

아래 ID는 manifest 후보 이름입니다. Qualification 때 실제 확인 시각, 수집 recipe, HTTP 응답
또는 수령 파일의 SHA-256, 문서 발행·적용일, 이전 revision과의 관계를 채웁니다. 같은 URL의
내용이 바뀌면 새 revision으로 취급합니다.

| manifest 후보 ID | 대상 | exact source와 확인한 용도 | 현재 공백 |
| --- | --- | --- | --- |
| `hyundai-current-model-list-web` | 현대 현재 판매 분모 | [현대 catalog·가격표 목록](https://org1.hyundai.com/kr/ko/e/vehicles/catalog-price-download) | 목록 snapshot과 model year 판정 recipe 미작성 |
| `hyundai-nexo-2026-estimation-web` | 26MY 넥쏘 판매 상태 대조 | [모델 선택 페이지](https://www.hyundai.com/kr/ko/e/vehicles/estimation/model/making?model=HE01&modelCode=HEJJ5HAT1&purposeCode=J) | production source에서 제외 |
| `hyundai-nexo-2026-price-pdf` | 넥쏘 가격·옵션·인증값 | [공식 가격표 PDF](https://www.hyundai.com/contents/repn-car/catalog/the-all-new-nexo_price.pdf) | exact bytes·checksum·가격 적용일 의미 미확정 |
| `hyundai-nexo-current-catalog-pdf` | 넥쏘 제원 | [공식 영문 catalog PDF](https://www.hyundai.com/content/dam/hyundai/kr/ko/data/vehicles/catalog/en/nexo-catalog-eng.pdf) | 현재 국내 판매본과 revision 일치 검증 필요 |
| `hyundai-nexo-heritage-web` | 넥쏘 연료 저장 제원 대조 | [2025 NEXO Heritage](https://www.hyundai.com/kr/ko/brand/brandstory/heritage/2025-nexo-fc) | 현재 판매 제원 source로 사용할 수 있는지 미확정 |
| `kia-k5-2027-price-web` | 2027 K5 가격·구성 | [가격 페이지](https://www.kia.com/kr/vehicles/k5/price/private) | 2026-08-01 기준일과 `effective_from` 관계 미확정 |
| `kia-k5-2027-spec-web` | K5 제원·인증 | [제원 페이지](https://www.kia.com/kr/vehicles/k5/specification) | exact snapshot·rights 미확정 |
| `kia-carnival-2027-price-web` | 2027 카니발 가격·구성 | [가격 페이지](https://www.kia.com/kr/vehicles/carnival/price) | 2026-08-27 기준일과 `effective_from` 관계 미확정 |
| `kia-carnival-2027-spec-web` | 카니발 제원·인증 | [제원 페이지](https://www.kia.com/kr/vehicles/carnival/specification) | exact snapshot·rights 미확정 |
| `kia-ev6-2027-price-web` | 2027 EV6 가격·구성 | [가격 페이지](https://www.kia.com/kr/vehicles/ev6/price) | 2026-08-03 기준일과 `effective_from` 관계 미확정 |
| `kia-ev6-2027-spec-web` | EV6 battery·주행거리·전비 | [제원 페이지](https://www.kia.com/kr/vehicles/ev6/specification) | 인증값의 정상·저온 조건 label 미확정 |
| `kea-efficiency-file-dataset` | 정부 신고 인증값 대조 | [공공데이터포털 자동차 표시연비 자료](https://www.data.go.kr/data/15083023/fileData.do), revision `2026-04-24`, SHA-256 `5d4853ce9cbd61589003fdb3386ff3d7c3afd0bf1c38bcb4ed2698d5c9482dce` | 현재 판매·model year·powertrain·CO₂·휠·카메라·EV 온도 조건 필드 없음 |
| `kea-efficiency-open-api` | 최신 신차·CO₂ 보완 후보 | [자동차 표시연비 목록 조회 서비스](https://www.data.go.kr/data/15139827/openapi.do) | 이용허락은 제한 없음이지만 활용신청·service key가 필요하고 exact response field·revision 미확보 |

현대 사이트의 [저작권 안내](https://www.hyundai.com/kr/ko/copyright)는 권리 유보를 표시합니다.
기아 페이지 footer도 권리를 유보하지만 공개 열람이 사실 추출·저장·외부 API 공개 허락을
뜻하지는 않습니다. 그러므로 제조사 공개 source는 정확성 조사에만 사용하고 production 값으로
승인하지 않습니다. 한국에너지공단 `15083023` file dataset은 해당 상세 페이지가
`이용허락범위 제한 없음`을 명시하므로 file revision의 취득·보관·가공은 승인하되, 부족한
identity·조건을 추정해 production 값으로 승격하지 않습니다.

## 대표 원문→column 관찰

아래는 전체 mapping이 아니라 qualification에서 재현한 대표 결과입니다. Exact 23행 mapping은
외부 데이터 저장소의 `normalized/qualification/representative-column-mapping-2026-08-31.csv`가
소유합니다. `source_location`은 KEA 원문 행과 제조사 페이지 section·행 label을 함께 고정하고,
실제 저장값은 권리가 승인된 KEA 원본에서만 가져옵니다.

| 표본과 source location | 관찰값 | target 후보 | 미결정 |
| --- | --- | --- | --- |
| K5 KEA 2991행 ↔ `The 2027 K5 > 1.6 가솔린 터보 > 17인치 타이어` | KEA의 휠·camera·도심·고속도로·복합·등급 | `car_certification` 8개 column mapping 작성 | model year·variant, CO₂·공차중량은 `BLOCKED` |
| 카니발 KEA 621행 ↔ `The 2027 Carnival > 1.6 터보 하이브리드 > 9인승 18인치` | KEA의 인승·휠·도심·고속도로·복합·등급 | `car_vehicle_variant.seating_capacity`와 `car_certification` 7개 column mapping 작성 | `nC` camera 의미, model year·variant, CO₂·공차중량은 `BLOCKED` |
| EV6 현재 제원 `The 2027 EV6 > 스탠다드 2WD 19인치` | 현재 제조사 공통 효율·주행거리와 같은 KEA 행 0건 | 값 없는 blocker mapping 1행 | KEA revision 지연과 EV 정상·저온 조건은 `BLOCKED` |
| 넥쏘 KEA 1957행 ↔ 가격표 p.4 `디 올 뉴 넥쏘 18인치 익스클루시브` | KEA의 휠·도심·고속도로·복합·FCEV type | `car_certification` 6개 column mapping 작성 | model year·variant와 필수 `combined_range_km`는 `BLOCKED` |

가격 페이지의 “기준일”은 가격 적용 시작일이라는 공식 근거가 있을 때만
`car_configuration_price.effective_from`으로 변환합니다. 확인일·발행일·적용일을 서로 대체하지 않으며,
근거가 없으면 해당 가격 mapping은 `BLOCKED`입니다. 넥쏘 heritage에 보이는 6.69 kg 같은 값도
현재 판매 catalog의 같은 조건임을 입증한 뒤에만 hydrogen tank 역할의
`car_powertrain_component.capacity_kg`에 넣습니다.

## 행위별 rights matrix

한 source의 허락을 하나의 `APPROVED`로 축약하지 않습니다. 최소한 다음 행위를 각각
`APPROVED`, `REJECTED`, `PENDING`, `NOT_APPLICABLE`로 판정하고 exact 공개 조항·version을
근거로 연결합니다.

| source | `ACQUIRE` | `TRANSFORM` | `STORE_RAW` | `STORE_EXTRACTED_FACT` | `STORE_DERIVED_FACT` | `PUBLISH_FACT` | `REDISTRIBUTE_RAW` | `RETAIN_AFTER_TERMINATION` |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 한국에너지공단 file dataset `15083023` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` |
| 한국에너지공단 Open API `15139827` | `PENDING` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` |

`STORE_RAW`가 거절되고 checksum 계산만 허용되면 원문은 임시 처리 뒤 폐기하고 허용된 source
identity·checksum·mapping metadata만 보존합니다. `STORE_EXTRACTED_FACT`와
`STORE_DERIVED_FACT` 중 하나라도 닫히지 않으면 그 source의 실제 값은 migration에 넣지 않습니다.
먼저 공개 약관·저작권 안내·dataset metadata만으로 `ACQUIRE`와 임시 처리 허용 범위를
판정합니다. `ACQUIRE=APPROVED`인 source만 exact bytes·checksum 수집으로 진행하고,
`PENDING`이면 실제 값을 취득하지 않고 공개 metadata 정리와 합성 fixture 검증만 수행합니다.

한국에너지공단 판정 근거는 2026-08-31 확인한 dataset 상세의 `이용허락범위 제한 없음`입니다.
승인은 `2026-04-24` file revision에 결속하며, 포털 metadata나 권리 표시가 바뀌면 새 revision
취득 전에 다시 판정합니다. 출처 표시가 법적 의무가 아니더라도 Carmunity provenance 계약에
따라 provider·dataset ID·revision·원문 위치를 가공본과 함께 유지합니다.

Open API도 `이용허락범위 제한 없음`이지만 개발계정 자동승인을 포함한 활용신청과 service key가
필요합니다. 현재 사용자는 이 외부 신청을 승인하지 않았으므로 실제 response를 취득하는
`ACQUIRE`만 `PENDING`이며, 신청 전에는 API payload·field를 확보했다고 기록하지 않습니다.

## qualification 산출물의 논리 계약

외부 raw·manifest·qualification·changes 위치는 사용자 지정 root에 고정했습니다. 최종 정형
입력 형식과 migration 생성 방식은 대표 mapping 뒤 결정하지만, 어떤 대안을 택해도 다음 정보를
잃으면 안 됩니다.

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

현재 대표 후보 CSV는 새 repository 도구를 도입하지 않고 macOS 기본 Ruby 2.6의 표준 CSV
parser로 1회 생성했습니다. 이 선택은 최종 입력 방식이나 generator 채택이 아니라, quoted CSV를
손상 없이 필터링하고 원문 행 번호를 붙이기 위한 qualification 수단입니다. 연간 갱신 빈도와
다음 revision의 반복 오류가 확인되기 전에는 repository script나 `check` hard gate를 만들지
않습니다.

## 2026-08-31 계획 변경

| 달라진 점 | 기존 계획 | 실제 결정과 근거 | 영향 |
| --- | --- | --- | --- |
| 물리 저장 위치 | 입력 방식 결정 뒤 확정 | 사용자 지정 iCloud root 아래 `car-community-data/catalog`로 확정 | raw·manifest·qualification·changes 디렉터리 계약을 먼저 적용 |
| 한국에너지공단 dataset ID | `15005963` 후보 | 기존 URL은 확인 시 HTTP 500, 현재 공식 상세·download metadata는 `15083023` | manifest ID는 유지하고 dataset ID·URL·checksum을 교정 |
| 취득 방식 | file/API 미확정 | 파일은 로그인 없이 가능하고 API는 활용신청·서비스 키가 필요해 file download 선택 | 외부 가입·신청·credential 없이 exact CSV 확보 |
| 가공 범위 | 대표 mapping 전 미정 | 현대·기아 K5·카니발·EV6·넥쏘 후보 168행에 provenance와 blocker만 추가 | identity·필수 schema field가 닫힐 때까지 migration 입력으로 사용하지 않음 |
| 외부 확인 경로 | 제조사·유료 source의 미확정 조건을 별도 확인하는 후보가 있었음 | 사용자 요청에 따라 현재 작업에서 제외하고 공개 이용 조건이 확인된 공공 source만 production 후보로 유지 | 해결되지 않는 field는 외부 확인으로 보완하지 않고 coverage 공백과 범위·schema 결정으로 반환 |

상세 실행 기록은 외부 데이터 저장소의 `changes/2026-08-31-initial-storage.md`, exact source와
checksum·권리는 `manifests/kea-efficiency-file-dataset-2026-04-24.yaml`이 소유합니다.

## `Planned` 전환에 필요한 다음 결과

1. KEA file revision의 manifest·checksum·재실행 evidence는 작성했습니다. 제조사 source는
   production 후보에서 제외하고 대조 URL·section metadata만 유지합니다.
2. 대표 22개 값의 page/section/label→column mapping은 작성했습니다. 네 record의 blocker를
   닫은 뒤 필수 field mapping을 완성합니다.
3. EV 온도 조건, 가격 적용일과 넥쏘 주행거리·연료용량은 현재 `BLOCKED`입니다.
4. 공개 source로 가능한 field와 불가능한 field를 coverage 표로 분리하고, 불가능한 field는
   최초 적재 범위 축소 또는 schema 변경 결정으로 반환합니다.
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
