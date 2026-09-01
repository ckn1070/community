package com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq;

import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarBrand.CAR_BRAND;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarConfiguration.CAR_CONFIGURATION;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarConfigurationPrice.CAR_CONFIGURATION_PRICE;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarGeneration.CAR_GENERATION;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModel.CAR_MODEL;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModelYear.CAR_MODEL_YEAR;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarPowertrain.CAR_POWERTRAIN;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarVehicleVariant.CAR_VEHICLE_VARIANT;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarBrand;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarConfiguration;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarConfigurationPrice;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarGeneration;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModel;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModelYear;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarPowertrain;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarVehicleVariant;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelPage;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelSummary;
import com.ronams.carmunity.catalog.internal.application.model.CatalogPriceBasis;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogModelSearchPort;
import com.ronams.carmunity.catalog.internal.application.query.SearchCatalogModelsQuery;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Select;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterUtils;
import org.springframework.stereotype.Component;

/** 공개 catalog 조회를 projection SQL로 수행해 내부 DB id와 JPA entity를 경계 밖으로 내보내지 않습니다. */
@Component
class JooqCatalogModelSearchAdapter implements CatalogModelSearchPort {

  private final DSLContext dsl;

  JooqCatalogModelSearchAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  /** 목록 content와 전체 model 수를 각각 한 query로 읽어 결과 행 수에 비례하는 SQL을 만들지 않습니다. */
  @Override
  public CatalogModelPage search(SearchCatalogModelsQuery query) {
    Map<String, Object> parameters = modelSearchParameters(query);
    String eligibleCte = eligibleCte();
    long totalElements =
        fetch(eligibleCte + MODEL_COUNT_SQL, parameters).getFirst().getLong("total_elements");
    List<CatalogModelSummary> items =
        fetch(eligibleCte + MODEL_PAGE_SQL, parameters).stream()
            .map(this::catalogModelSummary)
            .toList();
    int totalPages = (int) ((totalElements + query.size() - 1) / query.size());
    return new CatalogModelPage(items, query.page(), query.size(), totalElements, totalPages);
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

  private List<CatalogProjectionRecord> fetch(String sql, Map<String, ?> parameters) {
    var parsedSql = NamedParameterUtils.parseSqlStatement(sql);
    var parameterSource = new MapSqlParameterSource(parameters);
    String preparedSql = NamedParameterUtils.substituteNamedParameters(parsedSql, parameterSource);
    Object[] bindings = NamedParameterUtils.buildValueArray(parsedSql, parameterSource, null);
    return dsl.fetch(preparedSql, bindings).stream().map(CatalogProjectionRecord::new).toList();
  }

  private String eligibleCte() {
    return "WITH catalog_rows AS (" + dsl.renderInlined(catalogRows()) + ")" + ELIGIBLE_CTE_SQL;
  }

  private Select<?> catalogRows() {
    CarModel model = CAR_MODEL.as("model");
    CarBrand brand = CAR_BRAND.as("brand");
    CarGeneration generation = CAR_GENERATION.as("generation");
    CarModelYear modelYear = CAR_MODEL_YEAR.as("model_year");
    CarVehicleVariant variant = CAR_VEHICLE_VARIANT.as("variant");
    CarPowertrain powertrain = CAR_POWERTRAIN.as("powertrain");
    CarConfiguration configuration = CAR_CONFIGURATION.as("configuration");
    CarConfigurationPrice price = CAR_CONFIGURATION_PRICE.as("price");
    Field<LocalDate> asOf = DSL.field(DSL.sql(":asOf"), LocalDate.class);
    Field<Long> displayPrice =
        DSL.coalesce(price.PRICE_AFTER_TAX_BENEFIT, price.PRICE_BEFORE_TAX_BENEFIT)
            .as("display_price");
    Field<String> priceBasis =
        DSL.when(
                price.PRICE_AFTER_TAX_BENEFIT.isNotNull(),
                CatalogPriceBasis.AFTER_TAX_BENEFIT.name())
            .otherwise(CatalogPriceBasis.BEFORE_TAX_BENEFIT.name())
            .as("price_basis");
    Table<?> currentPrice =
        DSL.lateral(
                DSL.select(displayPrice, priceBasis)
                    .from(price)
                    .where(price.CONFIGURATION_ID.eq(configuration.ID))
                    .and(price.EFFECTIVE_FROM.le(asOf))
                    .orderBy(price.EFFECTIVE_FROM.desc())
                    .limit(1))
            .asTable("current_price");
    Field<String> salesStatus =
        DSL.when(
                asOf.lt(modelYear.SALES_STARTED_ON).or(asOf.lt(configuration.SALES_STARTED_ON)),
                SalesStatus.UPCOMING.name())
            .when(
                modelYear
                    .SALES_ENDED_ON
                    .isNotNull()
                    .and(asOf.ge(modelYear.SALES_ENDED_ON))
                    .or(
                        configuration
                            .SALES_ENDED_ON
                            .isNotNull()
                            .and(asOf.ge(configuration.SALES_ENDED_ON))),
                SalesStatus.DISCONTINUED.name())
            .otherwise(SalesStatus.ON_SALE.name())
            .as("sales_status");

    return DSL.select(
            model.ID.as("model_internal_id"),
            model.PUBLIC_ID.as("model_id"),
            model.NAME_KO.as("model_name"),
            model.NAME_EN.as("model_name_en"),
            model.NAME_NORMALIZED,
            brand.PUBLIC_ID.as("brand_id"),
            brand.NAME_KO.as("brand_name"),
            modelYear.PUBLIC_ID.as("model_year_id"),
            modelYear.MODEL_YEAR,
            modelYear.DISPLAY_NAME.as("edition_name"),
            modelYear.RELEASED_ON,
            modelYear.SALES_STARTED_ON.as("model_year_sales_started_on"),
            variant.BODY_TYPE,
            powertrain.PROPULSION_TYPE,
            powertrain.PRIMARY_FUEL_TYPE,
            powertrain.SECONDARY_FUEL_TYPE,
            configuration.ID.as("configuration_id"),
            salesStatus,
            currentPrice.field(displayPrice),
            currentPrice.field(priceBasis))
        .from(model)
        .join(brand)
        .on(brand.ID.eq(model.BRAND_ID))
        .join(generation)
        .on(generation.MODEL_ID.eq(model.ID))
        .join(modelYear)
        .on(modelYear.GENERATION_ID.eq(generation.ID))
        .join(variant)
        .on(variant.MODEL_YEAR_ID.eq(modelYear.ID))
        .join(powertrain)
        .on(powertrain.ID.eq(variant.POWERTRAIN_ID))
        .join(configuration)
        .on(configuration.VEHICLE_VARIANT_ID.eq(variant.ID))
        .leftJoin(currentPrice)
        .on(DSL.trueCondition())
        .where(configuration.IS_PUBLISHED.isTrue());
  }

  private CatalogModelSummary catalogModelSummary(CatalogProjectionRecord row) {
    Long startPrice = row.getObject("start_price", Long.class);
    String priceBasis = row.getString("price_basis");
    return new CatalogModelSummary(
        row.getObject("model_id", UUID.class),
        row.getObject("brand_id", UUID.class),
        row.getString("brand_name"),
        row.getString("model_name"),
        row.getString("model_name_en"),
        row.getObject("representative_model_year_id", UUID.class),
        row.getInt("model_year"),
        row.getString("edition_name"),
        enumSet(row.getObject("body_types", Object[].class), BodyType.class),
        enumSet(row.getObject("propulsion_types", Object[].class), PropulsionType.class),
        enumSet(row.getObject("fuel_types", Object[].class), FuelType.class),
        SalesStatus.valueOf(row.getString("sales_status")),
        row.getObject("released_on", LocalDate.class),
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

  /** Filter는 한 configuration row에 함께 적용하고 그 뒤 model year·model로 접습니다. */
  private static final String ELIGIBLE_CTE_SQL =
      """
      , eligible AS (
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
      "SELECT COUNT(DISTINCT model_internal_id) AS total_elements FROM eligible";

  private static final String MODEL_PAGE_SQL =
      """
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
