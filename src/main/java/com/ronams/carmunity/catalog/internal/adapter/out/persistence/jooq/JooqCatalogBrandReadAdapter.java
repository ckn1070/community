package com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq;

import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarBrand.CAR_BRAND;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarConfiguration.CAR_CONFIGURATION;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarGeneration.CAR_GENERATION;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModel.CAR_MODEL;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModelYear.CAR_MODEL_YEAR;
import static com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarVehicleVariant.CAR_VEHICLE_VARIANT;

import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarBrand;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarConfiguration;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarGeneration;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModel;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarModelYear;
import com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq.generated.tables.CarVehicleVariant;
import com.ronams.carmunity.catalog.internal.application.model.CatalogBrandSummary;
import com.ronams.carmunity.catalog.internal.application.port.out.CatalogBrandReadPort;
import java.time.LocalDate;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/** 생성된 schema model로 기준일의 공개 브랜드 목록을 조회합니다. */
@Component
final class JooqCatalogBrandReadAdapter implements CatalogBrandReadPort {

  private final DSLContext dsl;

  JooqCatalogBrandReadAdapter(DSLContext dsl) {
    this.dsl = dsl;
  }

  @Override
  public List<CatalogBrandSummary> list(LocalDate asOf) {
    CarBrand brand = CAR_BRAND.as("brand");
    CarModel model = CAR_MODEL.as("model");
    CarGeneration generation = CAR_GENERATION.as("generation");
    CarModelYear modelYear = CAR_MODEL_YEAR.as("model_year");
    CarVehicleVariant variant = CAR_VEHICLE_VARIANT.as("variant");
    CarConfiguration configuration = CAR_CONFIGURATION.as("configuration");
    Field<Long> onSaleModelCount = DSL.countDistinct(model.ID).cast(Long.class);

    return dsl.select(
            brand.PUBLIC_ID, brand.NAME_KO, brand.NAME_EN, brand.COUNTRY_CODE, onSaleModelCount)
        .from(brand)
        .join(model)
        .on(model.BRAND_ID.eq(brand.ID))
        .join(generation)
        .on(generation.MODEL_ID.eq(model.ID))
        .join(modelYear)
        .on(modelYear.GENERATION_ID.eq(generation.ID))
        .join(variant)
        .on(variant.MODEL_YEAR_ID.eq(modelYear.ID))
        .join(configuration)
        .on(configuration.VEHICLE_VARIANT_ID.eq(variant.ID))
        .where(configuration.IS_PUBLISHED.isTrue())
        .and(modelYear.SALES_STARTED_ON.le(asOf))
        .and(modelYear.SALES_ENDED_ON.isNull().or(modelYear.SALES_ENDED_ON.gt(asOf)))
        .and(configuration.SALES_STARTED_ON.le(asOf))
        .and(configuration.SALES_ENDED_ON.isNull().or(configuration.SALES_ENDED_ON.gt(asOf)))
        .groupBy(
            brand.ID,
            brand.PUBLIC_ID,
            brand.NAME_KO,
            brand.NAME_EN,
            brand.COUNTRY_CODE,
            brand.DISPLAY_ORDER)
        .orderBy(brand.DISPLAY_ORDER, brand.NAME_KO, brand.PUBLIC_ID)
        .fetch(
            row ->
                new CatalogBrandSummary(
                    row.get(brand.PUBLIC_ID),
                    row.get(brand.NAME_KO),
                    row.get(brand.NAME_EN),
                    row.get(brand.COUNTRY_CODE),
                    row.get(onSaleModelCount)));
  }
}
