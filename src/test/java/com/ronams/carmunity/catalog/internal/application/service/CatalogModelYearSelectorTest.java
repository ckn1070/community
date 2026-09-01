package com.ronams.carmunity.catalog.internal.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ronams.carmunity.catalog.api.SalesStatus;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.ModelYear;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogModelYearSelectorTest {

  private final CatalogModelYearSelector selector = new CatalogModelYearSelector();

  @Test
  void 요청_연식이_있으면_판매_상태와_무관하게_그_연식을_선택한다() {
    ModelYear discontinued =
        modelYear(UUID.randomUUID(), 2024, LocalDate.of(2024, 1, 1), SalesStatus.DISCONTINUED);
    ModelYear onSale =
        modelYear(UUID.randomUUID(), 2026, LocalDate.of(2026, 1, 1), SalesStatus.ON_SALE);

    assertThat(selector.select(List.of(onSale, discontinued), discontinued.modelYearId()))
        .contains(discontinued);
  }

  @Test
  void 대표_연식은_상태_출시일_연식_판매시작일_UUID_순으로_유일하게_선택한다() {
    UUID laterUuid = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
    UUID earlierUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
    LocalDate releaseDate = LocalDate.of(2026, 3, 1);
    ModelYear winner = modelYear(earlierUuid, 2026, releaseDate, SalesStatus.ON_SALE);
    ModelYear uuidTie = modelYear(laterUuid, 2026, releaseDate, SalesStatus.ON_SALE);
    ModelYear nullableRelease = modelYear(UUID.randomUUID(), 2027, null, SalesStatus.ON_SALE);
    ModelYear upcoming =
        modelYear(UUID.randomUUID(), 2028, LocalDate.of(2028, 1, 1), SalesStatus.UPCOMING);

    assertThat(selector.select(List.of(upcoming, nullableRelease, uuidTie, winner), null))
        .contains(winner);
  }

  private ModelYear modelYear(UUID id, int year, LocalDate releasedOn, SalesStatus salesStatus) {
    return new ModelYear(
        id,
        year,
        year + "년형",
        releasedOn,
        LocalDate.of(year, 1, 1),
        LocalDate.of(year + 1, 1, 1),
        salesStatus);
  }
}
