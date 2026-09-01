package com.ronams.carmunity.catalog.internal.application.model;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** 모델 page가 JPA entity나 내부 DB id 없이 전달하는 공개 목록 읽기 모델입니다. */
public record CatalogModelSummary(
    UUID modelId,
    UUID brandId,
    String brandName,
    String name,
    String nameEn,
    UUID representativeModelYearId,
    int modelYear,
    String editionName,
    Set<BodyType> bodyTypes,
    Set<PropulsionType> propulsionTypes,
    Set<FuelType> fuelTypes,
    SalesStatus salesStatus,
    LocalDate releasedOn,
    Long startPrice,
    CatalogPriceBasis priceBasis,
    String currencyCode) {}
