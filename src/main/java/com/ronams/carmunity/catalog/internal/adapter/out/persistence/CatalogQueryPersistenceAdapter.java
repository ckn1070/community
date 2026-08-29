package com.ronams.carmunity.catalog.internal.adapter.out.persistence;

import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogReadPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** 공개 catalog 조회를 projection SQL로 수행해 내부 DB id와 JPA entity를 경계 밖으로 내보내지 않습니다. */
@Component
class CatalogQueryPersistenceAdapter implements CatalogReadPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  CatalogQueryPersistenceAdapter(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /** model year와 configuration이 모두 기준일 판매 중인 공개 graph만 브랜드 수에 포함합니다. */
  @Override
  public List<CatalogBrandSummary> listBrands(LocalDate asOf) {
    return jdbcTemplate.query(
        BRAND_LIST_SQL,
        Map.of("asOf", asOf),
        (resultSet, rowNumber) ->
            new CatalogBrandSummary(
                resultSet.getObject("brand_id", java.util.UUID.class),
                resultSet.getString("name"),
                resultSet.getString("name_en"),
                resultSet.getString("country_code"),
                resultSet.getLong("on_sale_model_count")));
  }

  private static final String BRAND_LIST_SQL =
      """
      SELECT
          brand.public_id AS brand_id,
          brand.name_ko AS name,
          brand.name_en,
          brand.country_code,
          COUNT(DISTINCT model.id) AS on_sale_model_count
      FROM car_brand brand
      JOIN car_model model ON model.brand_id = brand.id
      JOIN car_generation generation ON generation.model_id = model.id
      JOIN car_model_year model_year ON model_year.generation_id = generation.id
      JOIN car_vehicle_variant variant ON variant.model_year_id = model_year.id
      JOIN car_configuration configuration ON configuration.vehicle_variant_id = variant.id
      WHERE configuration.is_published
        AND model_year.sales_started_on <= :asOf
        AND (model_year.sales_ended_on IS NULL OR :asOf < model_year.sales_ended_on)
        AND configuration.sales_started_on <= :asOf
        AND (configuration.sales_ended_on IS NULL OR :asOf < configuration.sales_ended_on)
      GROUP BY brand.id, brand.public_id, brand.name_ko, brand.name_en, brand.country_code,
               brand.display_order
      ORDER BY brand.display_order, brand.name_ko, brand.public_id
      """;
}
