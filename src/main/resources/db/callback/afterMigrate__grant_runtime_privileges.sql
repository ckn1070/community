GRANT USAGE ON SCHEMA public TO carmunity_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE
  ON ALL TABLES IN SCHEMA public TO carmunity_runtime;
GRANT USAGE
  ON ALL SEQUENCES IN SCHEMA public TO carmunity_runtime;
REVOKE ALL PRIVILEGES
  ON TABLE public.flyway_schema_history FROM carmunity_runtime;

-- Catalog는 검토된 migration으로만 변경하므로 runtime role에는 조회 권한만 남깁니다.
REVOKE INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER
  ON TABLE
    public.car_brand,
    public.car_model,
    public.car_generation,
    public.car_model_year,
    public.car_powertrain,
    public.car_powertrain_component,
    public.car_vehicle_variant,
    public.car_configuration,
    public.car_configuration_price,
    public.car_configuration_option,
    public.car_certification
  FROM carmunity_runtime;

-- Runtime은 catalog identity를 발급하지 않으므로 11개 sequence 권한을 모두 회수합니다.
REVOKE ALL PRIVILEGES
  ON SEQUENCE
    public.car_brand_id_seq,
    public.car_model_id_seq,
    public.car_generation_id_seq,
    public.car_model_year_id_seq,
    public.car_powertrain_id_seq,
    public.car_powertrain_component_id_seq,
    public.car_vehicle_variant_id_seq,
    public.car_configuration_id_seq,
    public.car_configuration_price_id_seq,
    public.car_configuration_option_id_seq,
    public.car_certification_id_seq
  FROM carmunity_runtime;
