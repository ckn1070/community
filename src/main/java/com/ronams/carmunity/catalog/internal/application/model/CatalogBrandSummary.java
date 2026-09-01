package com.ronams.carmunity.catalog.internal.application.model;

import java.util.UUID;

/** 공개 브랜드 목록에서 application과 Web 경계가 소비하는 읽기 모델입니다. */
public record CatalogBrandSummary(
    UUID brandId, String name, String nameEn, String countryCode, long onSaleModelCount) {}
