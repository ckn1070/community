package com.ronams.carmunity.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.catalog.api.BodyType;
import com.ronams.carmunity.catalog.api.FuelType;
import com.ronams.carmunity.catalog.api.PropulsionType;
import com.ronams.carmunity.catalog.api.SalesStatus;
import org.junit.jupiter.api.Test;

/** 목록 filter와 응답이 외부에 노출하는 catalog enum 계약을 검증합니다. */
class CatalogPublicContractTest {

  /** 차체·동력·연료·판매 상태 값이 문서와 OpenAPI에서 사용할 단일 vocabulary를 제공합니다. */
  @Test
  void exposesDocumentedCatalogEnumValues() {
    assertThat(BodyType.values())
        .extracting(Enum::name)
        .containsExactly(
            "SEDAN",
            "HATCHBACK",
            "WAGON",
            "COUPE",
            "CONVERTIBLE",
            "SUV",
            "MPV",
            "PICKUP",
            "VAN",
            "OTHER");
    assertThat(PropulsionType.values())
        .extracting(Enum::name)
        .containsExactly("ICE", "MHEV", "HEV", "PHEV", "EREV", "BEV", "FCEV");
    assertThat(FuelType.values())
        .extracting(Enum::name)
        .containsExactly("GASOLINE", "DIESEL", "LPG", "ELECTRICITY", "HYDROGEN", "OTHER");
    assertThat(SalesStatus.values())
        .extracting(Enum::name)
        .containsExactly("UPCOMING", "ON_SALE", "DISCONTINUED", "ALL");
  }
}
