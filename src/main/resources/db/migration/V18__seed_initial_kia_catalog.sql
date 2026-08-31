-- 원본과 column별 provenance는 외부 archive의
-- manifests/kia-k5-2027-2026-08-31.yaml과 normalized/kia/k5/2027/initial-catalog.csv가 소유합니다.
-- 이 migration은 2026-08-31에 취득한 기아 공식 가격표·카탈로그와 현재 판매 목록을 정규화합니다.

INSERT INTO car_brand (
    public_id, code, name_ko, name_en, country_code, homepage_url, display_order
) VALUES (
    'd43787ba-f8d6-5b47-8d99-28da57b4c810', 'KIA', '기아', 'Kia', 'KR', 'https://www.kia.com/kr', 20
);

INSERT INTO car_model (
    public_id, brand_id, code, name_ko, name_en, name_normalized, display_order
)
SELECT
    '68ad71d8-fae9-54fb-8835-9b202d5e5eed', id, 'K5', 'K5', 'K5', 'k5', 10
FROM car_brand
WHERE public_id = 'd43787ba-f8d6-5b47-8d99-28da57b4c810';

INSERT INTO car_generation (
    public_id, model_id, code, name, generation_order, change_type
)
SELECT
    '234509ae-ac71-5731-a1c5-7485da0b8952', id, 'K5-3-PRODUCT-IMPROVEMENT',
    'K5 3세대 상품성 개선 모델', 3, 'MINOR_CHANGE'
FROM car_model
WHERE public_id = '68ad71d8-fae9-54fb-8835-9b202d5e5eed';

INSERT INTO car_model_year (
    public_id, generation_id, market_code, model_year, edition_code, display_name,
    released_on, sales_started_on, summary
)
SELECT
    '9f90d6bf-d830-52e0-a377-53b08f110e71', id, 'KR', 2027, '2027-KR', 'The 2027 K5',
    '2026-07-02', '2026-07-02', '2026년 7월 2일 출시된 국내 판매 연식'
FROM car_generation
WHERE public_id = '234509ae-ac71-5731-a1c5-7485da0b8952';

INSERT INTO car_powertrain (
    public_id, model_year_id, code, name, propulsion_type, primary_fuel_type,
    drivetrain_type, transmission_type, transmission_gears,
    system_max_power_ps
)
SELECT
    '4a94ba5f-94fc-54d5-928d-1dc89384caef', id, 'G2.0-GASOLINE-6AT',
    '스마트스트림 G2.0 가솔린', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 6,
    160
FROM car_model_year
WHERE public_id = '9f90d6bf-d830-52e0-a377-53b08f110e71';

INSERT INTO car_powertrain_component (
    powertrain_id, component_type, component_role, position, name, fuel_type,
    displacement_cc, max_power_ps, max_power_rpm, max_torque_kgf_m,
    max_torque_rpm_from, max_torque_rpm_to, sort_order
)
SELECT
    id, 'ENGINE', 'PRIMARY', 'FRONT', '스마트스트림 G2.0 엔진', 'GASOLINE',
    1999, 160, 6500, 20.0, 4800, 4800, 0
FROM car_powertrain
WHERE public_id = '4a94ba5f-94fc-54d5-928d-1dc89384caef';

INSERT INTO car_vehicle_variant (
    public_id, model_year_id, powertrain_id, code, name, body_type,
    seating_capacity, length_mm, width_mm, height_mm, wheelbase_mm,
    front_track_mm, rear_track_mm, curb_weight_kg, wheel_size_inch,
    front_tire, rear_tire
)
SELECT
    '77d72233-88a4-5c7d-bbf4-1a45f2ab0f38', model_year.id, powertrain.id,
    'G2.0-16-NO-CAMERA', '2.0 가솔린 16인치', 'SEDAN',
    5, 4905, 1860, 1445, 2850, 1633, 1640, 1425, 16,
    '205/65 R16', '205/65 R16'
FROM car_model_year model_year
JOIN car_powertrain powertrain ON powertrain.model_year_id = model_year.id
WHERE model_year.public_id = '9f90d6bf-d830-52e0-a377-53b08f110e71'
  AND powertrain.public_id = '4a94ba5f-94fc-54d5-928d-1dc89384caef';

INSERT INTO car_configuration (
    public_id, vehicle_variant_id, code, trim_name, sales_channel,
    sales_started_on, display_order, is_published, standard_features
)
SELECT
    '7d836d76-88a1-5d9f-9da8-f7eb22c8c179', id, 'SMART-SELECTION',
    '스마트 셀렉션', 'PRIVATE', '2026-07-02', 10, TRUE,
    $features$[
      {"category":"파워트레인","name":"스마트스트림 G2.0 엔진","description":null},
      {"category":"파워트레인","name":"6단 자동변속기(수동 겸용, 부츠타입)","description":null},
      {"category":"파워트레인","name":"고급형 ISG","description":null},
      {"category":"파워트레인","name":"속도감응형 전동식 파워스티어링(MDPS)","description":null},
      {"category":"파워트레인","name":"주행모드 통합제어 시스템","description":null},
      {"category":"첨단 운전자 보조(ADAS)","name":"전방 충돌방지 보조(차량/보행자/자전거 탑승자)","description":null},
      {"category":"첨단 운전자 보조(ADAS)","name":"차로 이탈방지 보조","description":null},
      {"category":"첨단 운전자 보조(ADAS)","name":"운전자 주의 경고(전방 차량 출발 알림 기능 포함)","description":null},
      {"category":"첨단 운전자 보조(ADAS)","name":"하이빔 보조","description":null},
      {"category":"첨단 운전자 보조(ADAS)","name":"차로 유지 보조","description":null},
      {"category":"첨단 운전자 보조(ADAS)","name":"지능형 속도 제한 보조","description":null},
      {"category":"안전","name":"9에어백(운전석/동승석 어드밴스드, 운전석 무릎, 전복감지 커튼, 앞좌석/뒷좌석 사이드)","description":null},
      {"category":"안전","name":"전/후방 주차 거리 경고","description":null},
      {"category":"안전","name":"VSM","description":null},
      {"category":"안전","name":"경사로 밀림방지장치","description":null},
      {"category":"안전","name":"개별 타이어 공기압 경보 시스템","description":null},
      {"category":"안전","name":"앞좌석/뒷좌석 시트벨트 리마인더","description":null},
      {"category":"안전","name":"유아용 시트 고정 장치","description":null},
      {"category":"안전","name":"전자식 차일드락","description":null},
      {"category":"안전","name":"타이어 임시수리장치","description":null},
      {"category":"안전","name":"소화기","description":null},
      {"category":"외장","name":"205/65 R16 타이어&알로이 휠","description":null},
      {"category":"외장","name":"LED 헤드램프","description":null},
      {"category":"외장","name":"LED 주간 주행등","description":null},
      {"category":"외장","name":"LED 보조제동등","description":null},
      {"category":"외장","name":"LED 리피터 일체형 아웃사이드 미러(전동조절, 전동접이, 열선)","description":null},
      {"category":"외장","name":"에어로타입 와이퍼","description":null},
      {"category":"외장","name":"바디칼라 아웃사이드 도어핸들","description":null},
      {"category":"외장","name":"도어손잡이 조명(프론트)","description":null},
      {"category":"외장","name":"크롬 실루엣 몰딩","description":null},
      {"category":"외장","name":"블랙하이그로시 가니쉬(윈드쉴드 사이드/ B/C필라, 아웃사이드 미러 베이스부)","description":null},
      {"category":"외장","name":"샤크핀 안테나","description":null},
      {"category":"외장","name":"‘스마트 셀렉션‘ 전용 디자인(프론트/리어 범퍼, 라디에이터 그릴 패턴)","description":null},
      {"category":"내장","name":"블랙 인테리어","description":null},
      {"category":"내장","name":"슈퍼비전 클러스터(4.2인치 칼라 TFT LCD)","description":null},
      {"category":"내장","name":"LED 실내등(맵램프, 룸램프)","description":null},
      {"category":"내장","name":"D/N 룸미러","description":null},
      {"category":"내장","name":"슬라이딩 선바이저(거울, LED 조명)","description":null},
      {"category":"시트","name":"인조가죽시트","description":null},
      {"category":"시트","name":"앞좌석 열선시트","description":null},
      {"category":"시트","name":"앞좌석 통풍시트","description":null},
      {"category":"시트","name":"운전석 시트 높이조절장치","description":null},
      {"category":"시트","name":"앞좌석 시트벨트 높이조절장치","description":null},
      {"category":"시트","name":"앞좌석 시트백 포켓","description":null},
      {"category":"편의","name":"버튼시동 스마트키 시스템(스마트키 원격시동 포함)","description":null},
      {"category":"편의","name":"스마트 트렁크","description":null},
      {"category":"편의","name":"풋파킹 브레이크","description":null},
      {"category":"편의","name":"후방 모니터","description":null},
      {"category":"편의","name":"주행 중 후방 뷰","description":null},
      {"category":"편의","name":"크루즈 컨트롤","description":null},
      {"category":"편의","name":"오토 라이트 컨트롤","description":null},
      {"category":"편의","name":"후석 승객 알림","description":null},
      {"category":"편의","name":"수동식 틸트&텔레스코픽 스티어링 휠","description":null},
      {"category":"편의","name":"운전석 세이프티 파워 윈도우","description":null},
      {"category":"편의","name":"동승석/뒷좌석 파워 윈도우","description":null},
      {"category":"편의","name":"매뉴얼 에어컨","description":null},
      {"category":"편의","name":"고성능 에어컨 필터","description":null},
      {"category":"편의","name":"외부공기 유입방지 제어","description":null},
      {"category":"편의","name":"뒷좌석 에어벤트","description":null},
      {"category":"편의","name":"애프터 블로우","description":null},
      {"category":"편의","name":"C타입 USB 단자(충전용, 뒷좌석 2개)","description":null},
      {"category":"편의","name":"100W C타입 USB 단자(충전용, 앞좌석 1개/전용 충전 케이블 1개)","description":null},
      {"category":"편의","name":"앞좌석 파워아웃렛","description":null},
      {"category":"인포테인먼트","name":"12.3인치 디스플레이 오디오(기아 커넥트, 폰 프로젝션)","description":null},
      {"category":"인포테인먼트","name":"무선 소프트웨어 업데이트","description":null},
      {"category":"인포테인먼트","name":"6스피커","description":null},
      {"category":"인포테인먼트","name":"스티어링 휠 오디오 리모컨","description":null},
      {"category":"인포테인먼트","name":"블루투스 핸즈프리","description":null},
      {"category":"인포테인먼트","name":"C타입 USB 단자(데이터/충전 전환형)","description":null}
    ]$features$::JSONB
FROM car_vehicle_variant
WHERE public_id = '77d72233-88a4-5c7d-bbf4-1a45f2ab0f38';

-- K5에서 검증한 관계를 차량별 migration으로 복제하지 않고, 같은 공식 판매 목록 snapshot의
-- 나머지 승용·RV 21개 차종을 한 release batch로 적재합니다. 상세 provenance와 원본 checksum은
-- 외부 archive의 manifests/kia-current-catalog-2026-08-31.yaml과 normalized/batches가 소유합니다.
CREATE TEMPORARY TABLE kia_current_seed (
    model_code VARCHAR(60) PRIMARY KEY,
    name_ko VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,
    display_order INTEGER NOT NULL,
    powertrain_code VARCHAR(60) NOT NULL,
    powertrain_name VARCHAR(120) NOT NULL,
    propulsion_type VARCHAR(12) NOT NULL,
    primary_fuel_type VARCHAR(16) NOT NULL,
    drivetrain_type VARCHAR(8) NOT NULL,
    transmission_type VARCHAR(20),
    transmission_gears SMALLINT,
    variant_code VARCHAR(80) NOT NULL,
    variant_name VARCHAR(160) NOT NULL,
    body_type VARCHAR(16) NOT NULL,
    roof_type VARCHAR(16) NOT NULL,
    seating_capacity SMALLINT NOT NULL,
    length_mm INTEGER NOT NULL,
    width_mm INTEGER NOT NULL,
    height_mm INTEGER NOT NULL,
    wheelbase_mm INTEGER NOT NULL,
    trim_code VARCHAR(80) NOT NULL,
    trim_name VARCHAR(120) NOT NULL,
    price_before_tax_benefit BIGINT NOT NULL,
    price_after_tax_benefit BIGINT
) ON COMMIT DROP;

INSERT INTO kia_current_seed VALUES
    ('CARNIVAL', '카니발', 'Carnival', 10, 'G3.5-8AT', '3.5 가솔린', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 8, 'G3.5-9-18', '3.5 가솔린 9인승 18인치', 'MPV', 'STANDARD', 9, 5155, 1995, 1775, 3090, 'PRESTIGE', '프레스티지', 36860000, NULL),
    ('CARNIVAL-HI-LIMOUSINE', '카니발 하이리무진', 'Carnival Hi Limousine', 20, 'G3.5-8AT', '3.5 가솔린', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 8, 'G3.5-9-HIGH-18', '3.5 가솔린 9인승 하이루프 18인치', 'MPV', 'HIGH_ROOF', 9, 5155, 1995, 2045, 3090, 'NOBLESSE', '노블레스', 63270000, NULL),
    ('EV3', 'EV3', 'EV3', 30, 'LONG-RANGE-2WD', '롱레인지 2WD', 'BEV', 'ELECTRICITY', 'FWD', 'REDUCTION_GEAR', NULL, 'LONG-2WD-17', '롱레인지 2WD 17인치', 'SUV', 'STANDARD', 5, 4300, 1850, 1560, 2680, 'AIR', '에어', 42030000, 39950000),
    ('EV3-GT', 'EV3 GT', 'EV3 GT', 40, 'GT-AWD', 'GT AWD', 'BEV', 'ELECTRICITY', 'AWD', 'REDUCTION_GEAR', NULL, 'GT-AWD-20', 'GT AWD 20인치', 'SUV', 'STANDARD', 5, 4310, 1850, 1560, 2680, 'GT', 'GT', 56540000, 53750000),
    ('EV4', 'EV4', 'EV4', 50, 'STANDARD-2WD', '스탠다드 2WD', 'BEV', 'ELECTRICITY', 'FWD', 'REDUCTION_GEAR', NULL, 'STANDARD-2WD-17', '스탠다드 2WD 17인치', 'SEDAN', 'STANDARD', 5, 4730, 1860, 1480, 2820, 'AIR', '에어', 42520000, 40420000),
    ('EV4-GT', 'EV4 GT', 'EV4 GT', 60, 'GT-AWD', 'GT AWD', 'BEV', 'ELECTRICITY', 'AWD', 'REDUCTION_GEAR', NULL, 'GT-AWD-20', 'GT AWD 20인치', 'SEDAN', 'STANDARD', 5, 4730, 1860, 1475, 2820, 'GT', 'GT', 58040000, 55170000),
    ('EV5', 'EV5', 'EV5', 70, 'STANDARD-2WD', '스탠다드 2WD', 'BEV', 'ELECTRICITY', 'FWD', 'REDUCTION_GEAR', NULL, 'STANDARD-2WD-18', '스탠다드 2WD 18인치', 'SUV', 'STANDARD', 5, 4610, 1875, 1675, 2750, 'AIR', '에어', 43710000, 41550000),
    ('EV5-GT', 'EV5 GT', 'EV5 GT', 80, 'GT-AWD', 'GT AWD', 'BEV', 'ELECTRICITY', 'AWD', 'REDUCTION_GEAR', NULL, 'GT-AWD-20', 'GT AWD 20인치', 'SUV', 'STANDARD', 5, 4610, 1875, 1665, 2750, 'GT', 'GT', 59540000, 56600000),
    ('EV6', 'EV6', 'EV6', 90, 'LONG-RANGE-2WD', '롱레인지 2WD', 'BEV', 'ELECTRICITY', 'RWD', 'REDUCTION_GEAR', NULL, 'LONG-2WD-19', '롱레인지 2WD 19인치', 'SUV', 'STANDARD', 5, 4695, 1880, 1550, 2900, 'LIGHT', '라이트', 45870000, 43600000),
    ('EV6-GT', 'EV6 GT', 'EV6 GT', 100, 'GT-AWD', 'GT AWD', 'BEV', 'ELECTRICITY', 'AWD', 'REDUCTION_GEAR', NULL, 'GT-AWD-21', 'GT AWD 21인치', 'SUV', 'STANDARD', 5, 4695, 1890, 1545, 2900, 'GT', 'GT', 73210000, NULL),
    ('EV9', 'EV9', 'EV9', 110, 'LONG-RANGE-2WD', '롱레인지 2WD', 'BEV', 'ELECTRICITY', 'RWD', 'REDUCTION_GEAR', NULL, 'LONG-2WD-19', '롱레인지 2WD 19인치', 'SUV', 'STANDARD', 7, 5010, 1980, 1755, 3100, 'AIR', '에어', 65190000, 61970000),
    ('EV9-GT', 'EV9 GT', 'EV9 GT', 120, 'GT-AWD', 'GT AWD', 'BEV', 'ELECTRICITY', 'AWD', 'REDUCTION_GEAR', NULL, 'GT-AWD-21', 'GT AWD 21인치', 'SUV', 'STANDARD', 6, 5015, 1980, 1755, 3100, 'GT', 'GT', 89030000, 84740000),
    ('K8', 'K8', 'K8', 140, 'G1.6T-HEV-6AT', '1.6 터보 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, 'G1.6T-HEV-17', '1.6 터보 하이브리드 17인치', 'SEDAN', 'STANDARD', 5, 5050, 1880, 1455, 2895, 'NOBLESSE-LIGHT', '노블레스 라이트', 43670000, 42670000),
    ('K9', 'K9', 'K9', 150, 'G3.8-8AT', '3.8 가솔린', 'ICE', 'GASOLINE', 'RWD', 'AUTOMATIC', 8, 'G3.8-18', '3.8 가솔린 18인치', 'SEDAN', 'STANDARD', 5, 5140, 1915, 1490, 3105, 'PLATINUM', '플래티넘', 60340000, NULL),
    ('MORNING', '모닝', 'Morning', 160, 'G1.0-4AT', '1.0 가솔린', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 4, 'G1.0-14', '1.0 가솔린 14인치', 'HATCHBACK', 'STANDARD', 5, 3595, 1595, 1485, 2400, 'TRENDY', '트렌디', 14210000, NULL),
    ('NIRO', '니로', 'Niro', 170, 'G1.6-HEV-6DCT', '1.6 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'DCT', 6, 'G1.6-HEV-16', '1.6 하이브리드 16인치', 'SUV', 'STANDARD', 5, 4430, 1825, 1545, 2720, 'TRENDY', '트렌디', 30270000, 29270000),
    ('RAY', '레이', 'Ray', 180, 'G1.0-4AT', '1.0 가솔린', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 4, 'G1.0-14', '1.0 가솔린 14인치', 'HATCHBACK', 'STANDARD', 5, 3595, 1595, 1700, 2520, 'TRENDY', '트렌디', 15550000, NULL),
    ('RAY-EV', '레이 EV', 'Ray EV', 190, 'EV-2WD', 'EV 2WD', 'BEV', 'ELECTRICITY', 'FWD', 'REDUCTION_GEAR', NULL, 'EV-14', 'EV 14인치', 'HATCHBACK', 'STANDARD', 4, 3595, 1595, 1710, 2520, 'LIGHT', '라이트', 28520000, NULL),
    ('SELTOS', '셀토스', 'Seltos', 200, 'G1.6T-8AT', '1.6 가솔린 터보', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 8, 'G1.6T-16', '1.6 가솔린 터보 16인치', 'SUV', 'STANDARD', 5, 4430, 1830, 1600, 2690, 'TRENDY', '트렌디', 25120000, NULL),
    ('SORENTO', '쏘렌토', 'Sorento', 210, 'G2.5T-8DCT', '2.5 가솔린 터보', 'ICE', 'GASOLINE', 'FWD', 'DCT', 8, 'G2.5T-5-18', '2.5 가솔린 터보 5인승 18인치', 'SUV', 'STANDARD', 5, 4815, 1900, 1695, 2815, 'PRESTIGE', '프레스티지', 36410000, NULL),
    ('SPORTAGE', '스포티지', 'Sportage', 220, 'G1.6T-8AT', '1.6 가솔린 터보', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 8, 'G1.6T-17', '1.6 가솔린 터보 17인치', 'SUV', 'STANDARD', 5, 4685, 1865, 1660, 2755, 'PRESTIGE', '프레스티지', 29440000, NULL);

INSERT INTO car_model (public_id, brand_id, code, name_ko, name_en, name_normalized, display_order)
SELECT md5('kia:model:' || seed.model_code)::UUID, brand.id, seed.model_code, seed.name_ko,
       seed.name_en, LOWER(seed.model_code), seed.display_order
FROM kia_current_seed seed
CROSS JOIN car_brand brand
WHERE brand.code = 'KIA';

INSERT INTO car_generation (public_id, model_id, code, name, generation_order, change_type)
SELECT md5('kia:generation:' || seed.model_code)::UUID, model.id, 'CURRENT',
       seed.name_ko || ' 현재 판매 모델', 1, 'MINOR_CHANGE'
FROM kia_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'KIA';

INSERT INTO car_model_year (
    public_id, generation_id, market_code, model_year, edition_code, display_name,
    sales_started_on, summary
)
SELECT md5('kia:model-year:' || seed.model_code || ':2027-KR')::UUID, generation.id,
       'KR', 2027, '2027-KR', 'The 2027 ' || seed.name_en, DATE '2026-08-31',
       '2026-08-31 기아 공식 판매 목록에서 확인한 국내 판매 snapshot'
FROM kia_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'KIA'
JOIN car_generation generation ON generation.model_id = model.id AND generation.code = 'CURRENT';

INSERT INTO car_powertrain (
    public_id, model_year_id, code, name, propulsion_type, primary_fuel_type,
    drivetrain_type, transmission_type, transmission_gears
)
SELECT md5('kia:powertrain:' || seed.model_code || ':' || seed.powertrain_code)::UUID,
       model_year.id, seed.powertrain_code, seed.powertrain_name, seed.propulsion_type,
       seed.primary_fuel_type, seed.drivetrain_type, seed.transmission_type,
       seed.transmission_gears
FROM kia_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'KIA'
JOIN car_generation generation ON generation.model_id = model.id AND generation.code = 'CURRENT'
JOIN car_model_year model_year ON model_year.generation_id = generation.id AND model_year.edition_code = '2027-KR';

INSERT INTO car_vehicle_variant (
    public_id, model_year_id, powertrain_id, code, name, body_type, roof_type,
    seating_capacity, length_mm, width_mm, height_mm, wheelbase_mm
)
SELECT md5('kia:variant:' || seed.model_code || ':' || seed.variant_code)::UUID,
       model_year.id, powertrain.id, seed.variant_code, seed.variant_name, seed.body_type,
       seed.roof_type, seed.seating_capacity, seed.length_mm, seed.width_mm,
       seed.height_mm, seed.wheelbase_mm
FROM kia_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'KIA'
JOIN car_generation generation ON generation.model_id = model.id AND generation.code = 'CURRENT'
JOIN car_model_year model_year ON model_year.generation_id = generation.id AND model_year.edition_code = '2027-KR'
JOIN car_powertrain powertrain ON powertrain.model_year_id = model_year.id AND powertrain.code = seed.powertrain_code;

INSERT INTO car_configuration (
    public_id, vehicle_variant_id, code, trim_name, sales_channel,
    sales_started_on, display_order, is_published
)
SELECT md5('kia:configuration:' || seed.model_code || ':' || seed.trim_code)::UUID,
       variant.id, seed.trim_code, seed.trim_name, 'PRIVATE', DATE '2026-08-31', 10, TRUE
FROM kia_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'KIA'
JOIN car_generation generation ON generation.model_id = model.id AND generation.code = 'CURRENT'
JOIN car_model_year model_year ON model_year.generation_id = generation.id AND model_year.edition_code = '2027-KR'
JOIN car_vehicle_variant variant ON variant.model_year_id = model_year.id AND variant.code = seed.variant_code;

INSERT INTO car_configuration_price (
    configuration_id, effective_from, price_before_tax_benefit,
    price_after_tax_benefit, source_price_label
)
SELECT configuration.id, DATE '2026-08-31', seed.price_before_tax_benefit,
       seed.price_after_tax_benefit, '2026-08-31 기아 공식 가격표 판매가격'
FROM kia_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'KIA'
JOIN car_generation generation ON generation.model_id = model.id AND generation.code = 'CURRENT'
JOIN car_model_year model_year ON model_year.generation_id = generation.id AND model_year.edition_code = '2027-KR'
JOIN car_vehicle_variant variant ON variant.model_year_id = model_year.id AND variant.code = seed.variant_code
JOIN car_configuration configuration ON configuration.vehicle_variant_id = variant.id
                                    AND configuration.code = seed.trim_code;

INSERT INTO car_configuration_price (
    configuration_id, effective_from, price_before_tax_benefit, source_price_label
)
SELECT
    id, '2026-08-01', 27630000, '2026년 8월 1일 기준 판매가격'
FROM car_configuration
WHERE public_id = '7d836d76-88a1-5d9f-9da8-f7eb22c8c179';

INSERT INTO car_configuration_option (
    configuration_id, code, name, option_type, category, is_selectable,
    price_before_tax_benefit, included_items, effective_from, display_order
)
SELECT id, option_row.code, option_row.name, 'PACKAGE', option_row.category, TRUE,
       option_row.price, option_row.included_items::JSONB, '2026-08-01', option_row.display_order
FROM car_configuration
CROSS JOIN (VALUES
    ('CLUSTER-PACK-12-3', '12.3인치 클러스터 팩', '인포테인먼트', 1300000,
     '["슈퍼비전 클러스터(12.3인치 풀사이즈 칼라 TFT LCD)", "파노라믹 커브드 디스플레이", "하이패스 자동결제 시스템(e hi-pass)", "레인센서", "터치타입 독립제어 풀오토 에어컨(운전석/동승석)", "공기청정 시스템(미세먼지 센서 포함)", "오토디포그", "12.3인치 내비게이션(기아 커넥트, 폰 프로젝션, 인카페이먼트)"]', 10),
    ('PANORAMA-SUNROOF', '파노라마 선루프', '외장', 1090000,
     '["파노라마 선루프"]', 20)
) AS option_row(code, name, category, price, included_items, display_order)
WHERE car_configuration.public_id = '7d836d76-88a1-5d9f-9da8-f7eb22c8c179';

INSERT INTO car_certification (
    vehicle_variant_id, condition_code, condition_name, certification_type,
    certification_standard, wheel_size_inch, built_in_camera, curb_weight_kg,
    efficiency_grade, city_efficiency, highway_efficiency, combined_efficiency,
    efficiency_unit, co2_g_per_km
)
SELECT
    id, '16-NO-CAMERA', '16인치 타이어', 'LIQUID_FUEL', 'KOREA', 16, FALSE, 1425,
    3, 11.2, 14.7, 12.6, 'KM_PER_L', 132
FROM car_vehicle_variant
WHERE public_id = '77d72233-88a4-5c7d-bbf4-1a45f2ab0f38';
