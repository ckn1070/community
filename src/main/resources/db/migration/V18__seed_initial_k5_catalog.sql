-- 원본과 column별 provenance는 외부 archive의
-- manifests/kia-k5-2027-2026-08-31.yaml과 normalized/kia/k5/2027/initial-catalog.csv가 소유합니다.
-- 이 migration은 2026-08-31에 취득한 기아 공식 2026년 8월 가격표와 2026년 7월 카탈로그를 정규화합니다.

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
