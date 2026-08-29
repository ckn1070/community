package com.ronams.carmunity.catalog.internal.adapter.out.persistence;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelPage;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelSummary;
import com.ronams.carmunity.catalog.internal.application.model.CatalogPriceBasis;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogReadPort;
import com.ronams.carmunity.catalog.internal.application.query.SearchCatalogModelsQuery;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 공개 catalog 조회를 projection SQL로 수행해 내부 DB id와 JPA entity를 경계 밖으로 내보내지 않습니다. */
@Component
class CatalogQueryPersistenceAdapter implements CatalogReadPort {

  private final CatalogJooqQuery jooqQuery;
  private final CatalogDetailQuery detailQuery;

  CatalogQueryPersistenceAdapter(CatalogJooqQuery jooqQuery, CatalogDetailQuery detailQuery) {
    this.jooqQuery = jooqQuery;
    this.detailQuery = detailQuery;
  }

  /** model year와 configuration이 모두 기준일 판매 중인 공개 graph만 브랜드 수에 포함합니다. */
  @Override
  public List<CatalogBrandSummary> listBrands(LocalDate asOf) {
    return jooqQuery.fetch(BRAND_LIST_SQL, Map.of("asOf", asOf)).stream()
        .map(
            record ->
                mapRecord(
                    () ->
                        new CatalogBrandSummary(
                            record.getObject("brand_id", UUID.class),
                            record.getString("name"),
                            record.getString("name_en"),
                            record.getString("country_code"),
                            record.getLong("on_sale_model_count"))))
        .toList();
  }

  /** 목록 content와 전체 model 수를 각각 한 query로 읽어 결과 행 수에 비례하는 SQL을 만들지 않습니다. */
  @Override
  public CatalogModelPage searchModels(SearchCatalogModelsQuery query) {
    Map<String, Object> parameters = modelSearchParameters(query);
    long totalElements =
        mapRecord(
            () ->
                jooqQuery.fetch(MODEL_COUNT_SQL, parameters).getFirst().getLong("total_elements"));
    List<CatalogModelSummary> items =
        jooqQuery.fetch(MODEL_PAGE_SQL, parameters).stream()
            .map(record -> mapRecord(() -> catalogModelSummary(record)))
            .toList();
    int totalPages = (int) ((totalElements + query.size() - 1) / query.size());
    return new CatalogModelPage(items, query.page(), query.size(), totalElements, totalPages);
  }

  @Override
  public Optional<CatalogModelDetail> findModelDetail(
      UUID modelId, UUID requestedModelYearId, LocalDate asOf) {
    return detailQuery.find(modelId, requestedModelYearId, asOf);
  }

  private Map<String, Object> modelSearchParameters(SearchCatalogModelsQuery query) {
    Map<String, Object> parameters = new java.util.LinkedHashMap<>();
    parameters.put("searchPattern", query.likePattern());
    parameters.put("brandId", query.brandId());
    parameters.put("bodyType", enumName(query.bodyType()));
    parameters.put("propulsionType", enumName(query.propulsionType()));
    parameters.put("fuelType", enumName(query.fuelType()));
    parameters.put("minPrice", query.minPrice());
    parameters.put("maxPrice", query.maxPrice());
    parameters.put("salesStatus", query.salesStatus().name());
    parameters.put("asOf", query.asOf());
    parameters.put("sort", query.sort().name());
    parameters.put("limit", query.size());
    parameters.put("offset", query.offset());
    return parameters;
  }

  private String enumName(Enum<?> value) {
    return value == null ? null : value.name();
  }

  private CatalogModelSummary catalogModelSummary(CatalogJooqQuery.CatalogRecord record)
      throws SQLException {
    Long startPrice = record.getObject("start_price", Long.class);
    String priceBasis = record.getString("price_basis");
    return new CatalogModelSummary(
        record.getObject("model_id", UUID.class),
        record.getObject("brand_id", UUID.class),
        record.getString("brand_name"),
        record.getString("model_name"),
        record.getString("model_name_en"),
        record.getObject("representative_model_year_id", UUID.class),
        record.getInt("model_year"),
        record.getString("edition_name"),
        enumSet(record.getObject("body_types", Object[].class), BodyType.class),
        enumSet(record.getObject("propulsion_types", Object[].class), PropulsionType.class),
        enumSet(record.getObject("fuel_types", Object[].class), FuelType.class),
        SalesStatus.valueOf(record.getString("sales_status")),
        record.getObject("released_on", LocalDate.class),
        startPrice,
        priceBasis == null ? null : CatalogPriceBasis.valueOf(priceBasis),
        startPrice == null ? null : "KRW");
  }

  private <E extends Enum<E>> Set<E> enumSet(Object[] values, Class<E> enumType) {
    if (values == null) {
      return Set.of();
    }
    LinkedHashSet<E> result = new LinkedHashSet<>();
    Arrays.stream(values)
        .map(Object::toString)
        .map(value -> Enum.valueOf(enumType, value))
        .forEach(result::add);
    return Collections.unmodifiableSet(result);
  }

  private <T> T mapRecord(SqlRecordSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (SQLException exception) {
      throw new IllegalStateException("catalog projection을 읽을 수 없습니다.", exception);
    }
  }

  @FunctionalInterface
  private interface SqlRecordSupplier<T> {
    T get() throws SQLException;
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

  /** Filter는 이 CTE의 한 configuration row에 함께 적용하고 그 뒤 model year·model로 접습니다. */
  private static final String MODEL_ELIGIBLE_CTE =
      """
      WITH catalog_rows AS (
          SELECT
              model.id AS model_internal_id,
              model.public_id AS model_id,
              model.name_ko AS model_name,
              model.name_en AS model_name_en,
              model.name_normalized,
              brand.public_id AS brand_id,
              brand.name_ko AS brand_name,
              model_year.public_id AS model_year_id,
              model_year.model_year,
              model_year.display_name AS edition_name,
              model_year.released_on,
              model_year.sales_started_on AS model_year_sales_started_on,
              variant.body_type,
              powertrain.propulsion_type,
              powertrain.primary_fuel_type,
              powertrain.secondary_fuel_type,
              configuration.id AS configuration_id,
              CASE
                  WHEN :asOf < model_year.sales_started_on
                    OR :asOf < configuration.sales_started_on THEN 'UPCOMING'
                  WHEN (model_year.sales_ended_on IS NOT NULL AND :asOf >= model_year.sales_ended_on)
                    OR (configuration.sales_ended_on IS NOT NULL AND :asOf >= configuration.sales_ended_on)
                    THEN 'DISCONTINUED'
                  ELSE 'ON_SALE'
              END AS sales_status,
              current_price.display_price,
              current_price.price_basis
          FROM car_model model
          JOIN car_brand brand ON brand.id = model.brand_id
          JOIN car_generation generation ON generation.model_id = model.id
          JOIN car_model_year model_year ON model_year.generation_id = generation.id
          JOIN car_vehicle_variant variant ON variant.model_year_id = model_year.id
          JOIN car_powertrain powertrain ON powertrain.id = variant.powertrain_id
          JOIN car_configuration configuration ON configuration.vehicle_variant_id = variant.id
          LEFT JOIN LATERAL (
              SELECT
                  COALESCE(price.price_after_tax_benefit, price.price_before_tax_benefit)
                      AS display_price,
                  CASE
                      WHEN price.price_after_tax_benefit IS NOT NULL THEN 'AFTER_TAX_BENEFIT'
                      ELSE 'BEFORE_TAX_BENEFIT'
                  END AS price_basis
              FROM car_configuration_price price
              WHERE price.configuration_id = configuration.id
                AND price.effective_from <= :asOf
              ORDER BY price.effective_from DESC
              LIMIT 1
          ) current_price ON TRUE
          WHERE configuration.is_published
      ),
      eligible AS (
          SELECT *
          FROM catalog_rows row
          WHERE (CAST(:searchPattern AS TEXT) IS NULL OR row.name_normalized LIKE :searchPattern ESCAPE '\\')
            AND (CAST(:brandId AS UUID) IS NULL OR row.brand_id = :brandId)
            AND (CAST(:bodyType AS TEXT) IS NULL OR row.body_type = :bodyType)
            AND (CAST(:propulsionType AS TEXT) IS NULL OR row.propulsion_type = :propulsionType)
            AND (CAST(:fuelType AS TEXT) IS NULL OR row.primary_fuel_type = :fuelType
                 OR row.secondary_fuel_type = :fuelType)
            AND (CAST(:minPrice AS BIGINT) IS NULL OR row.display_price >= :minPrice)
            AND (CAST(:maxPrice AS BIGINT) IS NULL OR row.display_price <= :maxPrice)
            AND (:salesStatus = 'ALL' OR row.sales_status = :salesStatus)
      )
      """;

  private static final String MODEL_COUNT_SQL =
      MODEL_ELIGIBLE_CTE
          + "SELECT COUNT(DISTINCT model_internal_id) AS total_elements FROM eligible";

  private static final String MODEL_PAGE_SQL =
      MODEL_ELIGIBLE_CTE
          + """
          , year_rollup AS (
              SELECT
                  row.model_internal_id,
                  row.model_id,
                  row.model_name,
                  row.model_name_en,
                  row.brand_id,
                  row.brand_name,
                  row.model_year_id,
                  row.model_year,
                  row.edition_name,
                  row.released_on,
                  row.model_year_sales_started_on,
                  CASE MIN(
                      CASE row.sales_status
                          WHEN 'ON_SALE' THEN 1
                          WHEN 'UPCOMING' THEN 2
                          ELSE 3
                      END
                  )
                      WHEN 1 THEN 'ON_SALE'
                      WHEN 2 THEN 'UPCOMING'
                      ELSE 'DISCONTINUED'
                  END AS sales_status,
                  ARRAY_AGG(DISTINCT row.body_type ORDER BY row.body_type) AS body_types,
                  ARRAY_AGG(DISTINCT row.propulsion_type ORDER BY row.propulsion_type)
                      AS propulsion_types,
                  ARRAY_REMOVE(ARRAY_AGG(DISTINCT fuel.fuel_type ORDER BY fuel.fuel_type), NULL)
                      AS fuel_types,
                  MIN(row.display_price) AS start_price,
                  (ARRAY_AGG(row.price_basis ORDER BY row.display_price NULLS LAST, row.configuration_id)
                      FILTER (WHERE row.display_price IS NOT NULL))[1] AS price_basis
              FROM eligible row
              CROSS JOIN LATERAL (
                  VALUES (row.primary_fuel_type), (row.secondary_fuel_type)
              ) fuel(fuel_type)
              GROUP BY
                  row.model_internal_id, row.model_id, row.model_name, row.model_name_en,
                  row.brand_id, row.brand_name, row.model_year_id, row.model_year,
                  row.edition_name, row.released_on, row.model_year_sales_started_on
          ),
          ranked AS (
              SELECT
                  year_rollup.*,
                  ROW_NUMBER() OVER (
                      PARTITION BY model_internal_id
                      ORDER BY
                          CASE sales_status
                              WHEN 'ON_SALE' THEN 1
                              WHEN 'UPCOMING' THEN 2
                              ELSE 3
                          END,
                          released_on DESC NULLS LAST,
                          model_year DESC,
                          model_year_sales_started_on DESC,
                          model_year_id
                  ) AS representative_rank
              FROM year_rollup
          )
          SELECT
              model_id,
              brand_id,
              brand_name,
              model_name,
              model_name_en,
              model_year_id AS representative_model_year_id,
              model_year,
              edition_name,
              body_types,
              propulsion_types,
              fuel_types,
              sales_status,
              released_on,
              start_price,
              price_basis
          FROM ranked
          WHERE representative_rank = 1
          ORDER BY
              CASE WHEN :sort = 'NAME_ASC' THEN model_name END ASC NULLS LAST,
              CASE WHEN :sort = 'PRICE_ASC' THEN start_price END ASC NULLS LAST,
              CASE WHEN :sort = 'PRICE_DESC' THEN start_price END DESC NULLS LAST,
              CASE WHEN :sort = 'RELEASED_DESC' THEN released_on END DESC NULLS LAST,
              model_id
          LIMIT :limit OFFSET :offset
          """;
}
