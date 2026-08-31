-- 원본·revision·checksum은 외부 archive의 manifests/hyundai-current-catalog-2026-08-31.yaml과
-- normalized/inventory/hyundai-current-models-2026-08-31.csv가 소유합니다.
-- 현대 공식 2026년 8월 판매 목록에서 상용·특장·어린이 운송 전용 모델을 제외한 31개 차종을 적재합니다.

INSERT INTO car_brand (
    public_id, code, name_ko, name_en, country_code, homepage_url, display_order
) VALUES (
    md5('hyundai:brand')::UUID, 'HYUNDAI', '현대자동차', 'Hyundai', 'KR',
    'https://www.hyundai.com/kr/ko', 10
);

CREATE TEMPORARY TABLE hyundai_current_seed (
    model_code VARCHAR(60) PRIMARY KEY,
    name_ko VARCHAR(100) NOT NULL,
    name_en VARCHAR(100) NOT NULL,
    display_order INTEGER NOT NULL,
    model_year SMALLINT NOT NULL,
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

INSERT INTO hyundai_current_seed VALUES
    ('FN01', '디 올 뉴 아반떼', 'The all-new AVANTE', 10, 2027, 'G1.6-6AT', '가솔린 1.6', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, 'G1.6-16', '가솔린 1.6 16인치', 'SEDAN', 'STANDARD', 5, 4765, 1855, 1420, 2750, 'MODERN', '모던', 23980000, NULL),
    ('FN02', '디 올 뉴 아반떼 Hybrid', 'The all-new AVANTE Hybrid', 20, 2027, 'G1.6-HEV-6DCT', '가솔린 1.6 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'DCT', 6, 'G1.6-HEV-16', '가솔린 1.6 하이브리드 16인치', 'SEDAN', 'STANDARD', 5, 4765, 1855, 1420, 2750, 'MODERN', '모던', 30420000, NULL),
    ('GN11', '더 뉴 그랜저', 'The new GRANDEUR', 30, 2027, 'G2.5-8AT', '가솔린 2.5', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 8, 'G2.5-18', '가솔린 2.5 18인치', 'SEDAN', 'STANDARD', 5, 5050, 1880, 1460, 2895, 'PREMIUM', '프리미엄', 42450000, NULL),
    ('GN12', '더 뉴 그랜저 Hybrid', 'The new GRANDEUR Hybrid', 40, 2027, 'G1.6T-HEV-6AT', '가솔린 1.6 터보 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, 'G1.6T-HEV-18', '가솔린 1.6 터보 하이브리드 18인치', 'SEDAN', 'STANDARD', 5, 5050, 1880, 1460, 2895, 'PREMIUM', '프리미엄', 49330000, 48330000),
    ('DN23', '쏘나타 디 엣지', 'SONATA The Edge', 50, 2026, 'G2.0-6AT', '가솔린 2.0', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, 'G2.0-17', '가솔린 2.0 17인치', 'SEDAN', 'STANDARD', 5, 4910, 1860, 1445, 2840, 'PREMIUM', '프리미엄', 29330000, NULL),
    ('DN24', '쏘나타 디 엣지 Hybrid', 'SONATA The Edge Hybrid', 60, 2026, 'G2.0-HEV-6AT', '가솔린 2.0 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, 'G2.0-HEV-16', '가솔린 2.0 하이브리드 16인치', 'SEDAN', 'STANDARD', 5, 4910, 1860, 1445, 2840, 'PREMIUM', '프리미엄', 34180000, 33180000),
    ('FX03', '팰리세이드', 'PALISADE', 70, 2027, 'G2.5T-8AT', '가솔린 2.5 터보', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 8, 'G2.5T-9-18', '가솔린 2.5 터보 9인승 18인치', 'SUV', 'STANDARD', 9, 5060, 1980, 1765, 2970, 'EXCLUSIVE', '익스클루시브', 44780000, NULL),
    ('FX04', '팰리세이드 Hybrid', 'PALISADE Hybrid', 80, 2027, 'G2.5T-HEV-6AT', '가솔린 2.5 터보 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, 'G2.5T-HEV-9-18', '가솔린 2.5 터보 하이브리드 9인승 18인치', 'SUV', 'STANDARD', 9, 5060, 1980, 1765, 2970, 'EXCLUSIVE', '익스클루시브', 50770000, NULL),
    ('QX07', '베뉴', 'VENUE', 90, 2027, 'G1.6-CVT', '가솔린 1.6', 'ICE', 'GASOLINE', 'FWD', 'CVT', NULL, 'G1.6-15', '가솔린 1.6 15인치', 'SUV', 'STANDARD', 5, 4040, 1770, 1565, 2520, 'SMART', '스마트', 19940000, NULL),
    ('SX19', '코나', 'KONA', 100, 2027, 'G1.6T-8AT', '가솔린 1.6 터보', 'ICE', 'GASOLINE', 'FWD', 'AUTOMATIC', 8, 'G1.6T-17', '가솔린 1.6 터보 17인치', 'SUV', 'STANDARD', 5, 4350, 1825, 1580, 2660, 'MODERN', '모던', 24630000, NULL),
    ('SX20', '코나 Hybrid', 'KONA Hybrid', 110, 2027, 'G1.6-HEV-6DCT', '가솔린 1.6 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'DCT', 6, 'G1.6-HEV-17', '가솔린 1.6 하이브리드 17인치', 'SUV', 'STANDARD', 5, 4350, 1825, 1580, 2660, 'MODERN', '모던', 30380000, 29380000),
    ('MX07', '싼타페', 'SANTA FE', 120, 2026, 'G2.5T-8DCT', '가솔린 2.5 터보', 'ICE', 'GASOLINE', 'FWD', 'DCT', 8, 'G2.5T-5-18', '가솔린 2.5 터보 5인승 18인치', 'SUV', 'STANDARD', 5, 4830, 1900, 1720, 2815, 'EXCLUSIVE', '익스클루시브', 36570000, NULL),
    ('MX08', '싼타페 Hybrid', 'SANTA FE Hybrid', 130, 2026, 'G1.6T-HEV-6AT', '가솔린 1.6 터보 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, 'G1.6T-HEV-5-18', '가솔린 1.6 터보 하이브리드 5인승 18인치', 'SUV', 'STANDARD', 5, 4830, 1900, 1720, 2815, 'EXCLUSIVE', '익스클루시브', 41220000, 40220000),
    ('NX17', '투싼', 'TUCSON', 140, 2026, 'G1.6T-7DCT', '가솔린 1.6 터보', 'ICE', 'GASOLINE', 'FWD', 'DCT', 7, 'G1.6T-17', '가솔린 1.6 터보 17인치', 'SUV', 'STANDARD', 5, 4640, 1865, 1665, 2755, 'MODERN', '모던', 28440000, NULL),
    ('NX18', '투싼 Hybrid', 'TUCSON Hybrid', 150, 2026, 'G1.6T-HEV-6AT', '가솔린 1.6 터보 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, 'G1.6T-HEV-17', '가솔린 1.6 터보 하이브리드 17인치', 'SUV', 'STANDARD', 5, 4640, 1865, 1665, 2755, 'MODERN', '모던', 34180000, 33180000),
    ('US46', '더 뉴 스타리아 리무진 Hybrid', 'The new STARIA Limousine Hybrid', 160, 2026, 'G1.6T-HEV-6AT', '가솔린 1.6 터보 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, 'G1.6T-HEV-7-HIGH-18', '가솔린 1.6 터보 하이브리드 7인승 하이루프 18인치', 'MPV', 'HIGH_ROOF', 7, 5255, 1995, 2200, 3275, 'INSPIRATION', '인스퍼레이션', 59800000, NULL),
    ('US31', '더 뉴 스타리아 라운지', 'The new STARIA Lounge', 170, 2026, 'L3.5-8AT', 'LPG 3.5', 'ICE', 'LPG', 'FWD', 'AUTOMATIC', 8, 'L3.5-7-18', 'LPG 3.5 7인승 18인치', 'MPV', 'STANDARD', 7, 5255, 1995, 1990, 3275, 'INSPIRATION', '인스퍼레이션', 47720000, NULL),
    ('US34', '더 뉴 스타리아 라운지 Hybrid', 'The new STARIA Lounge Hybrid', 180, 2026, 'G1.6T-HEV-6AT', '가솔린 1.6 터보 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, 'G1.6T-HEV-7-18', '가솔린 1.6 터보 하이브리드 7인승 18인치', 'MPV', 'STANDARD', 7, 5255, 1995, 1990, 3275, 'PRESTIGE', '프레스티지', 44990000, NULL),
    ('US30', '더 뉴 스타리아', 'The new STARIA', 190, 2026, 'L3.5-8AT', 'LPG 3.5', 'ICE', 'LPG', 'FWD', 'AUTOMATIC', 8, 'L3.5-11-17', 'LPG 3.5 11인승 17인치', 'MPV', 'STANDARD', 11, 5255, 1995, 1990, 3275, 'SMART', '스마트', 35020000, NULL),
    ('US33', '더 뉴 스타리아 Hybrid', 'The new STARIA Hybrid', 200, 2026, 'G1.6T-HEV-6AT', '가솔린 1.6 터보 하이브리드', 'HEV', 'GASOLINE', 'FWD', 'AUTOMATIC', 6, 'G1.6T-HEV-11-17', '가솔린 1.6 터보 하이브리드 11인승 17인치', 'MPV', 'STANDARD', 11, 5255, 1995, 1990, 3275, 'SMART', '스마트', 38700000, NULL),
    ('HE02', '넥쏘', 'NEXO', 210, 2027, 'FCEV-2WD', '수소전기 2WD', 'FCEV', 'HYDROGEN', 'FWD', 'REDUCTION_GEAR', NULL, 'FCEV-18', '수소전기 18인치', 'SUV', 'STANDARD', 5, 4750, 1865, 1640, 2790, 'MODERN', '모던', 80447000, 76470000),
    ('SX17', '코나 Electric', 'KONA Electric', 220, 2025, 'STANDARD-2WD', '스탠다드 2WD', 'BEV', 'ELECTRICITY', 'FWD', 'REDUCTION_GEAR', NULL, 'STANDARD-2WD-17', '스탠다드 2WD 17인치', 'SUV', 'STANDARD', 5, 4355, 1825, 1575, 2660, 'E-VALUE-PLUS', '이-밸류 플러스', 43680000, 41520000),
    ('NE09', '아이오닉 5', 'IONIQ 5', 230, 2027, 'STANDARD-2WD', '스탠다드 2WD', 'BEV', 'ELECTRICITY', 'RWD', 'REDUCTION_GEAR', NULL, 'STANDARD-2WD-19', '스탠다드 2WD 19인치', 'SUV', 'STANDARD', 5, 4655, 1890, 1605, 3000, 'E-VALUE-PLUS', '이-밸류 플러스', 49810000, 47350000),
    ('US39', '더 뉴 스타리아 Electric', 'The new STARIA Electric', 240, 2027, 'EV-2WD', '전기 2WD', 'BEV', 'ELECTRICITY', 'FWD', 'REDUCTION_GEAR', NULL, 'EV-11-17', '전기 11인승 17인치', 'MPV', 'STANDARD', 11, 5255, 1995, 1990, 3275, 'MODERN', '모던', 60290000, NULL),
    ('US40', '더 뉴 스타리아 라운지 Electric', 'The new STARIA Lounge Electric', 250, 2027, 'EV-2WD', '전기 2WD', 'BEV', 'ELECTRICITY', 'FWD', 'REDUCTION_GEAR', NULL, 'EV-7-18', '전기 7인승 18인치', 'MPV', 'STANDARD', 7, 5255, 1995, 1990, 3275, 'PRESTIGE', '프레스티지', 65490000, NULL),
    ('US41', '더 뉴 스타리아 리무진 Electric', 'The new STARIA Limousine Electric', 260, 2027, 'EV-2WD', '전기 2WD', 'BEV', 'ELECTRICITY', 'FWD', 'REDUCTION_GEAR', NULL, 'EV-7-HIGH-18', '전기 7인승 하이루프 18인치', 'MPV', 'HIGH_ROOF', 7, 5255, 1995, 2200, 3275, 'INSPIRATION', '인스퍼레이션', 89110000, 84820000),
    ('ME02', '아이오닉 9', 'IONIQ 9', 270, 2027, 'LONG-RANGE-2WD', '롱레인지 2WD', 'BEV', 'ELECTRICITY', 'RWD', 'REDUCTION_GEAR', NULL, 'LONG-2WD-19', '롱레인지 2WD 19인치', 'SUV', 'STANDARD', 7, 5060, 1980, 1790, 3130, 'EXCLUSIVE', '익스클루시브', 71100000, 67590000),
    ('CE03', '더 뉴 아이오닉 6', 'The new IONIQ 6', 280, 2026, 'STANDARD-2WD', '스탠다드 2WD', 'BEV', 'ELECTRICITY', 'RWD', 'REDUCTION_GEAR', NULL, 'STANDARD-2WD-18', '스탠다드 2WD 18인치', 'SEDAN', 'STANDARD', 5, 4925, 1880, 1495, 2950, 'E-VALUE-PLUS', '이-밸류 플러스', 51090000, 48560000),
    ('CE05', '아이오닉 6 N', 'IONIQ 6 N', 290, 2026, 'N-AWD', 'N AWD', 'BEV', 'ELECTRICITY', 'AWD', 'REDUCTION_GEAR', NULL, 'N-AWD-20', 'N AWD 20인치', 'SEDAN', 'STANDARD', 5, 4935, 1940, 1495, 2965, 'N', 'N', 84060000, 79900000),
    ('NE08', '아이오닉 5 N', 'IONIQ 5 N', 300, 2026, 'N-AWD', 'N AWD', 'BEV', 'ELECTRICITY', 'AWD', 'REDUCTION_GEAR', NULL, 'N-AWD-21', 'N AWD 21인치', 'SUV', 'STANDARD', 5, 4715, 1940, 1585, 3000, 'ESSENTIAL', '에센셜', 78790000, NULL),
    ('CN26', '아반떼 N', 'AVANTE N', 310, 2026, 'G2.0T-6MT', 'N 가솔린 2.0 터보', 'ICE', 'GASOLINE', 'FWD', 'MANUAL', 6, 'G2.0T-19', 'N 가솔린 2.0 터보 19인치', 'SEDAN', 'STANDARD', 5, 4710, 1825, 1415, 2720, 'N', 'N', 33560000, NULL);

INSERT INTO car_model (public_id, brand_id, code, name_ko, name_en, name_normalized, display_order)
SELECT md5('hyundai:model:' || seed.model_code)::UUID, brand.id, seed.model_code,
       seed.name_ko, seed.name_en, LOWER(seed.model_code), seed.display_order
FROM hyundai_current_seed seed
CROSS JOIN car_brand brand
WHERE brand.code = 'HYUNDAI';

INSERT INTO car_generation (public_id, model_id, code, name, generation_order, change_type)
SELECT md5('hyundai:generation:' || seed.model_code)::UUID, model.id, 'CURRENT',
       seed.name_ko || ' 현재 판매 모델', 1, 'MINOR_CHANGE'
FROM hyundai_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'HYUNDAI';

INSERT INTO car_model_year (
    public_id, generation_id, market_code, model_year, edition_code, display_name,
    sales_started_on, summary
)
SELECT md5('hyundai:model-year:' || seed.model_code || ':' || seed.model_year || '-KR')::UUID,
       generation.id, 'KR', seed.model_year, seed.model_year || '-KR', seed.name_en,
       DATE '2026-08-31', '2026-08-31 현대 공식 판매 목록에서 확인한 국내 판매 snapshot'
FROM hyundai_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'HYUNDAI'
JOIN car_generation generation ON generation.model_id = model.id AND generation.code = 'CURRENT';

INSERT INTO car_powertrain (
    public_id, model_year_id, code, name, propulsion_type, primary_fuel_type,
    drivetrain_type, transmission_type, transmission_gears
)
SELECT md5('hyundai:powertrain:' || seed.model_code || ':' || seed.powertrain_code)::UUID,
       model_year.id, seed.powertrain_code, seed.powertrain_name, seed.propulsion_type,
       seed.primary_fuel_type, seed.drivetrain_type, seed.transmission_type, seed.transmission_gears
FROM hyundai_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'HYUNDAI'
JOIN car_generation generation ON generation.model_id = model.id AND generation.code = 'CURRENT'
JOIN car_model_year model_year ON model_year.generation_id = generation.id;

INSERT INTO car_vehicle_variant (
    public_id, model_year_id, powertrain_id, code, name, body_type, roof_type,
    seating_capacity, length_mm, width_mm, height_mm, wheelbase_mm
)
SELECT md5('hyundai:variant:' || seed.model_code || ':' || seed.variant_code)::UUID,
       model_year.id, powertrain.id, seed.variant_code, seed.variant_name, seed.body_type,
       seed.roof_type, seed.seating_capacity, seed.length_mm, seed.width_mm,
       seed.height_mm, seed.wheelbase_mm
FROM hyundai_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'HYUNDAI'
JOIN car_generation generation ON generation.model_id = model.id AND generation.code = 'CURRENT'
JOIN car_model_year model_year ON model_year.generation_id = generation.id
JOIN car_powertrain powertrain ON powertrain.model_year_id = model_year.id AND powertrain.code = seed.powertrain_code;

INSERT INTO car_configuration (
    public_id, vehicle_variant_id, code, trim_name, sales_channel,
    sales_started_on, display_order, is_published
)
SELECT md5('hyundai:configuration:' || seed.model_code || ':' || seed.trim_code)::UUID,
       variant.id, seed.trim_code, seed.trim_name, 'PRIVATE', DATE '2026-08-31', 10, TRUE
FROM hyundai_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'HYUNDAI'
JOIN car_generation generation ON generation.model_id = model.id AND generation.code = 'CURRENT'
JOIN car_model_year model_year ON model_year.generation_id = generation.id
JOIN car_vehicle_variant variant ON variant.model_year_id = model_year.id AND variant.code = seed.variant_code;

INSERT INTO car_configuration_price (
    configuration_id, effective_from, price_before_tax_benefit,
    price_after_tax_benefit, source_price_label
)
SELECT configuration.id, DATE '2026-08-31', seed.price_before_tax_benefit,
       seed.price_after_tax_benefit, '2026-08-31 현대 공식 가격표 판매가격'
FROM hyundai_current_seed seed
JOIN car_model model ON model.code = seed.model_code
JOIN car_brand brand ON brand.id = model.brand_id AND brand.code = 'HYUNDAI'
JOIN car_generation generation ON generation.model_id = model.id AND generation.code = 'CURRENT'
JOIN car_model_year model_year ON model_year.generation_id = generation.id
JOIN car_vehicle_variant variant ON variant.model_year_id = model_year.id AND variant.code = seed.variant_code
JOIN car_configuration configuration ON configuration.vehicle_variant_id = variant.id
                                    AND configuration.code = seed.trim_code;
