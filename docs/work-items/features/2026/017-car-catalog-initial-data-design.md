# 자동차 카탈로그 초기 데이터 확보 설계

## 문서 역할과 현재 결론

이 문서는 [017 자동차 카탈로그 초기 데이터 구축](017-car-catalog-initial-data.md)의 상세
source 조사, 권리 판정, 원문→column mapping과 입력 방식 대안을 소유합니다. 작업 상태·범위·
단계·완료 조건은 017 본문이, 현재 물리 column은
[110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)가 소유합니다.

2026-08-31 공개 원문 사전 조사에서는 K5·카니발·EV6·넥쏘의 현재 판매 여부와 일부 대표값을
확인했고, 한국에너지공단 file dataset의 exact source bytes·checksum과 대표 후보 168행을
확보했습니다. 이후 외부 연락 제외를 제조사 공개 원본 제외로 확대했던 해석을 교정하고, 기아
공식 가격표·카탈로그·현재 웹 snapshot을 production source로 포함했습니다. 그 결과 첫 K5
configuration 1건, 기본품목 69건, 독립 선택 가능한 옵션 2건과 완성 인증 1건을 V18 입력으로
만들었습니다. 이 K5 단위는 계약 검증 표본으로 종료하며, 후속 확장은 전체 판매 모델
inventory를 먼저 고정한 뒤 구조화 입력과 release batch migration으로 수행합니다.

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
| `kia-k5-official` | 2027 K5 가격·구성·제원·인증 | [가격 페이지](https://www.kia.com/kr/vehicles/k5/price/private), [제원 페이지](https://www.kia.com/kr/vehicles/k5/specification), 공식 가격표·카탈로그 PDF | 2026-08-31 snapshot·checksum·column mapping과 V18 입력 확보 |
| `kia-carnival-2027-price-web` | 2027 카니발 가격·구성 | [가격 페이지](https://www.kia.com/kr/vehicles/carnival/price) | 2026-08-27 기준일과 `effective_from` 관계 미확정 |
| `kia-carnival-2027-spec-web` | 카니발 제원·인증 | [제원 페이지](https://www.kia.com/kr/vehicles/carnival/specification) | exact snapshot·rights 미확정 |
| `kia-ev6-2027-price-web` | 2027 EV6 가격·구성 | [가격 페이지](https://www.kia.com/kr/vehicles/ev6/price) | 2026-08-03 기준일과 `effective_from` 관계 미확정 |
| `kia-ev6-2027-spec-web` | EV6 battery·주행거리·전비 | [제원 페이지](https://www.kia.com/kr/vehicles/ev6/specification) | 인증값의 정상·저온 조건 label 미확정 |
| `kea-efficiency-file-dataset` | 정부 신고 인증값 대조 | [공공데이터포털 자동차 표시연비 자료](https://www.data.go.kr/data/15083023/fileData.do), revision `2026-04-24`, SHA-256 `5d4853ce9cbd61589003fdb3386ff3d7c3afd0bf1c38bcb4ed2698d5c9482dce` | 현재 판매·model year·powertrain·CO₂·휠·카메라·EV 온도 조건 필드 없음 |
| `kea-efficiency-open-api` | 최신 신차·CO₂ 보완 후보 | [자동차 표시연비 목록 조회 서비스](https://www.data.go.kr/data/15139827/openapi.do) | 이용허락은 제한 없음이지만 활용신청·service key가 필요하고 exact response field·revision 미확보 |

제조사 원본 파일 자체는 애플리케이션에서 재배포하지 않고 사용자 지정 비공개 작업 archive에
보관합니다. 공개된 차량 사실만 출처와 revision을 연결해 저장·제공하며 메일·전화·문의·
활용신청은 하지 않습니다. 공개 접근 가능하다는 사실을 상업적 재배포 허락으로 간주하지 않고,
행위별 법적 상태는 manifest에 사실대로 기록합니다. 한국에너지공단 `15083023`
file dataset은 해당 상세 페이지가
`이용허락범위 제한 없음`을 명시하므로 file revision의 취득·보관·가공은 승인하되, 부족한
identity·조건을 추정해 production 값으로 승격하지 않습니다.

## 대표 원문→column 관찰

아래는 전체 mapping이 아니라 qualification에서 재현한 대표 결과입니다. Exact 23행 mapping은
외부 데이터 저장소의 `normalized/qualification/representative-column-mapping-2026-08-31.csv`가
소유합니다. `source_location`은 KEA 원문 행과 제조사 페이지 section·행 label을 함께 고정하고,
K5 V18 저장값은 추가로 기아 공식 원본을 사용하며 exact 116행 mapping을 외부 데이터 저장소의
`normalized/kia/k5/2027/initial-catalog.csv`에 보존합니다.

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
| K5 2027 공개 source bundle | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `PENDING` | `REJECTED` | `PENDING` |
| 한국에너지공단 file dataset `15083023` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` |
| 한국에너지공단 Open API `15139827` | `PENDING` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` | `APPROVED` |

`STORE_RAW`가 거절되고 checksum 계산만 허용되면 원문은 임시 처리 뒤 폐기하고 허용된 source
identity·checksum·mapping metadata만 보존합니다. `STORE_EXTRACTED_FACT`와
`STORE_DERIVED_FACT` 중 하나라도 닫히지 않으면 그 source의 실제 값은 migration에 넣지 않습니다.
먼저 공개 약관·저작권 안내·dataset metadata만으로 `ACQUIRE`와 임시 처리 허용 범위를
판정합니다. `ACQUIRE=APPROVED`인 source만 exact bytes·checksum 수집으로 진행하고,
`PENDING`이면 실제 값을 취득하지 않고 공개 metadata 정리와 합성 fixture 검증만 수행합니다.

K5 bundle의 `STORE_RAW=APPROVED`는 사용자가 지정한 비공개 작업 archive에 저장하는 현재 작업
승인만 뜻하며 일반적 라이선스 판정이 아닙니다. `PUBLISH_FACT`의 법적 허락은 확인하지 못했으므로
manifest에 `NOT_LEGALLY_VERIFIED`로 기록하고, 원본 재배포는 프로젝트 정책으로 거절했습니다.

한국에너지공단 판정 근거는 2026-08-31 확인한 dataset 상세의 `이용허락범위 제한 없음`입니다.
승인은 `2026-04-24` file revision에 결속하며, 포털 metadata나 권리 표시가 바뀌면 새 revision
취득 전에 다시 판정합니다. 출처 표시가 법적 의무가 아니더라도 Carmunity provenance 계약에
따라 provider·dataset ID·revision·원문 위치를 가공본과 함께 유지합니다.

Open API도 `이용허락범위 제한 없음`이지만 개발계정 자동승인을 포함한 활용신청과 service key가
필요합니다. 현재 사용자는 이 외부 신청을 승인하지 않았으므로 실제 response를 취득하는
`ACQUIRE`만 `PENDING`이며, 신청 전에는 API payload·field를 확보했다고 기록하지 않습니다.

## KEA 단일 source coverage와 첫 K5 확장 결과

Exact 202행 결과는 외부 데이터 저장소의
`normalized/qualification/public-source-column-coverage-2026-08-31.csv`가 소유합니다.

| 범위 | source로 확인한 값 | 완성을 막는 값 | 결과 |
| --- | --- | --- | --- |
| 제품·판매 계층 | 제조사명과 네 model family의 일부 이름 | 세대·현재 model year·판매 기간·variant 필수 치수·trim·가격·option | 게시 가능한 `car_configuration` 0건 |
| K5·카니발 인증 | 일부 조건과 도심·고속도로·복합 효율·등급 | 액체연료 인증 필수 `co2_g_per_km`; 카니발 camera 조건 | 완성된 인증 0건 |
| 넥쏘 인증 | 휠과 도심·고속도로·복합 효율 | 수소 인증 필수 `combined_range_km` | 완성된 인증 0건 |
| EV6 인증 | 과거 KEA 후보 행 | 현재 2027 일치 행과 전기 인증 필수 `temperature_condition` | 완성된 인증 0건 |

Repository에서 생성하는 UUID·stable code와 DB default는 외부 source coverage로 세지 않습니다.
부모 identity나 측정 필수값이 닫히지 않은 행은 일부 column이 있어도 insert 가능으로 판정하지
않습니다. Actual schema column은 각각 source mapping·미검증 source field·repository 생성·
DB default·미확보 중 하나로만 분류하고, 대표 23행 mapping이 있는 9개 고유 column만
`SOURCE_MAPPED_REPRESENTATIVE`로 인정했습니다. 따라서 현재 source만으로는 수동 SQL과
generator 중 어느 입력 방식도 실제 data migration 후보를 만들지 못한다는 결론은 **KEA 단일
source에만** 적용됩니다. 기아 공식 원본을 결합한 K5 2027 mapping은 게시 configuration 1건과
완성 액체연료 인증 1건을 만들었습니다. Exact 원본·checksum은
`manifests/kia-k5-2027-2026-08-31.yaml`, column·JSON 묶음 mapping은
`normalized/kia/k5/2027/initial-catalog.csv`가 소유합니다.

## qualification 산출물의 논리 계약

외부 raw·manifest·qualification·changes 위치는 사용자 지정 root에 고정했습니다. 첫 K5는
정형 mapping CSV와 검토된 수동 Flyway SQL을 사용했습니다. 어떤 후속 대안을 택해도 다음 정보를
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

## 입력 방식 결정과 migration 경계

runtime crawler·관리자 import는 현재 범위가 아닙니다. V18에서 수동 SQL이 한 configuration에도
제품 계층·가격·옵션·인증과 stable UUID를 반복 작성해야 한다는 점을 확인했습니다. 아직 적용하지
않은 V18은 외부 executable을 추가하지 않고 SQL 내부 선언적 seed table과 deterministic UUID
표현식으로 기아 22개 차종을 한 번에 변환합니다. 차량별 SQL 복제는 사용하지 않습니다.

| 대안 | 장점 | 비용·위험 | 채택 조건 |
| --- | --- | --- | --- |
| 검토된 수동 Flyway SQL | 새 도구가 없고 작은 범위에서 diff가 직접 보임 | 반복·오기·stable UUID·coverage 누락 위험 | V18 계약 검증 표본에만 사용하고 후속 전체 확대에는 사용하지 않음 |
| 구조화 입력 + 기존 Gradle·Java 보조 | schema 검증과 결정적 변환을 재사용할 수 있음 | build 코드 경계와 입력 형식 유지 책임 | 기존 기능만으로 required validation·determinism을 충족하고 새 custom tool이 불필요함 |
| SQL 내부 선언적 seed table | 별도 runtime 없이 Flyway가 같은 transaction에서 관계를 생성 | SQL review 비용과 source field 수동 대조 | 미적용 V18의 Kia batch에 채택. 모델 coverage를 PostgreSQL test로 고정 |

generator는 representative inventory·mapping 행 수, 생성 SQL line·byte 수, 동일 입력 2회 output
diff, validation false positive/negative, 실행 시간과 PostgreSQL 적용 시간·lock을 기록합니다.
구현·운영 owner, 실패·우회·제거 경로는
[130 도구 도입 워크플로우](../../../workflow/130-tool-adoption-workflow.md)에 따라 고정합니다.

첫 완결 단위의 migration은 구현 직전 다음 미사용 번호 `V18`을 확인해 배정했습니다. 이 migration은
적용 전이므로 K5 표본을 보존한 채 기아 현재 판매 22개 차종 전체를 같은 파일의 제조사 batch로
확장했습니다. 후속 현대 migration도 같은 공식 inventory snapshot과 source revision을 공유하는
제조사 release batch로 만들며 차량 identity 자체를 파일 경계로 사용하지 않습니다.

현재 대표 후보 CSV를 만든 macOS 기본 Ruby 2.6 parser는 qualification용 일회성 수단으로만
남깁니다. 후속 generator는 repository가 소유하고 입력 schema, 결정적 출력, 오류·삭제 경로와
검증 명령을 함께 제공합니다.

## 2026-08-31 계획 변경

| 달라진 점 | 기존 계획 | 실제 결정과 근거 | 영향 |
| --- | --- | --- | --- |
| 물리 저장 위치 | 입력 방식 결정 뒤 확정 | 사용자 지정 iCloud root 아래 `car-community-data/catalog`로 확정 | raw·manifest·qualification·changes 디렉터리 계약을 먼저 적용 |
| 한국에너지공단 dataset ID | `15005963` 후보 | 기존 URL은 확인 시 HTTP 500, 현재 공식 상세·download metadata는 `15083023` | manifest ID는 유지하고 dataset ID·URL·checksum을 교정 |
| 취득 방식 | file/API 미확정 | 파일은 로그인 없이 가능하고 API는 활용신청·서비스 키가 필요해 file download 선택 | 외부 가입·신청·credential 없이 exact CSV 확보 |
| 가공 범위 | 대표 mapping 전 미정 | 현대·기아 K5·카니발·EV6·넥쏘 후보 168행에 provenance와 blocker만 추가 | identity·필수 schema field가 닫힐 때까지 migration 입력으로 사용하지 않음 |
| 외부 연락 범위 | 제조사·유료 source 조건 확인 후보가 있었음 | 메일·전화·문의·활용신청만 제외하고 로그인 없이 공개된 제조사 페이지·PDF는 production source로 사용 | source 공백 때문에 적재를 중단하지 않고 공개 원본을 계속 탐색·보관 |
| 공개 source coverage | KEA 단일 source에서 게시 가능 configuration·완성 인증 모두 0건 | 기아 공식 원본을 결합해 K5 configuration·인증 각 1건을 완성 | K5는 표본으로 종료하고 전체 inventory를 배치로 수집·검증 |
| 입력 방식 | 대표 4모델 전체 비교 뒤 선택 | V18은 수동 SQL 표본, 후속 전체 범위는 구조화 입력+generator | 수동 SQL을 차량별로 복제하지 않음 |
| migration 분할 | 모델별 수직 단위를 차례로 추가 | 같은 inventory snapshot·source revision의 검증된 release batch로 생성 | SQL 크기·적용 시간·review 임계치만 분할 근거로 사용 |
| V18 처리 | K5 표본을 유지하고 새 번호부터 batch 추가 | migration 미적용이라는 사용자 확인에 따라 V18을 Kia 22개 차종 batch로 직접 교체 | K5 상세 행은 보존하고 나머지 21개 차종의 대표 게시 구성을 같은 transaction에 추가 |
| generator 도입 | 별도 repository generator를 후속 표준으로 도입 | Kia batch는 SQL 내부 선언적 seed table과 PostgreSQL constraint/test로 필요한 결정성을 충족 | 새 executable의 owner·유지비 없이 모델별 migration 복제를 제거하며, 필요성이 다시 확인될 때 도입을 재평가 |
| 제조사 실행 순서 | 현대·기아 원본을 함께 모은 뒤 게시 batch 결정 | 사용자 결정에 따라 Kia 22개 V18을 검증·commit한 뒤 현대 31개 V19를 작성 | 앞 제조사 PostgreSQL 검증을 다음 제조사 mutation의 gate로 사용하고 두 결과를 별도 commit으로 보존 |
| 현대 inventory revision | 2026년 7월 검색 snapshot 후보 | 실제 렌더링 페이지와 공식 상품 API는 2026년 8월 25일 갱신본 | 화면 노출 36개 중 킨더·ST1·포터 Electric 5개를 제외하고 31개를 현재 범위로 고정 |

상세 실행 기록은 외부 데이터 저장소의 `changes/2026-08-31-initial-storage.md`, exact source와
checksum·권리는 `manifests/kea-efficiency-file-dataset-2026-04-24.yaml`이 소유합니다.

## 다음 확장에 필요한 결과

1. KEA와 K5 공식 원본의 manifest·checksum·column mapping을 작성했습니다.
2. 첫 K5 configuration의 필수 제품·판매·가격·옵션·인증 mapping과 V18을 완성했습니다.
3. EV 온도 조건, 가격 적용일과 넥쏘 주행거리·연료용량은 현재 `BLOCKED`입니다.
4. KEA 단일 source coverage 0건과 K5 공식 원본 결합 결과 1건을 분리해 기록했습니다.
5. 현대·기아 전체 판매 모델 inventory와 source URL 집합을 먼저 고정하고 원본 취득을 제조사
   snapshot 배치로 수행합니다.
6. 구조화 입력과 generator로 첫 다차종 release batch를 만들고 PostgreSQL·API coverage를
   배치 단위로 검증합니다.
7. 확정한 contract·task·failure/rollback 기준으로 `$verify-plan` 독립 검증을 통과합니다.

후속 원본과 mapping은 모델 사이에 직렬 선행조건을 두지 않고 inventory 전체에서 축적합니다.
게시 migration과 조회 검증만 동결된 release batch 단위로 직렬화합니다.

## 관련 문서

- **작업 상태·범위·완료 조건**: [017 자동차 카탈로그 초기 데이터 구축](017-car-catalog-initial-data.md)
- **선행 schema·API**: [016 신차 카탈로그 schema·조회 API](016-new-car-catalog-query-api.md)
- **현재 업무 개념**: [130 자동차 카탈로그 도메인](../../../domain/130-car-catalog.md)
- **물리 DB 구조**: [110 자동차 카탈로그 Schema Reference](../../../project/schema/110-car-catalog.md)
- **DB 변경 방식**: [240 Flyway](../../../tech-stack/240-flyway.md),
  [250 PostgreSQL](../../../tech-stack/250-postgresql.md)
- **도구 선택 gate**: [130 도구 도입 워크플로우](../../../workflow/130-tool-adoption-workflow.md)
