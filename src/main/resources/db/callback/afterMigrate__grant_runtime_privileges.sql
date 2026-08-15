GRANT USAGE ON SCHEMA public TO carmunity_runtime;
GRANT SELECT, INSERT, UPDATE, DELETE
  ON ALL TABLES IN SCHEMA public TO carmunity_runtime;
GRANT USAGE
  ON ALL SEQUENCES IN SCHEMA public TO carmunity_runtime;
REVOKE ALL PRIVILEGES
  ON TABLE public.flyway_schema_history FROM carmunity_runtime;
