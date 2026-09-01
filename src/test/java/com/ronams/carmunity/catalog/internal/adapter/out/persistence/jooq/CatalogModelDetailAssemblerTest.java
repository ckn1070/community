package com.ronams.carmunity.catalog.internal.adapter.out.persistence.jooq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;

import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Certification;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.Option;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.PowertrainComponent;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.SalesConfiguration;
import com.ronams.carmunity.catalog.internal.application.model.CatalogModelDetail.StandardFeature;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jooq.Record;
import org.junit.jupiter.api.Test;

/** 상세 query의 반복 행을 application graph로 조립하는 책임을 직접 검증합니다. */
class CatalogModelDetailAssemblerTest {

  private final CatalogModelDetailAssembler assembler =
      new CatalogModelDetailAssembler(new CatalogJsonConverter());

  @Test
  void groupsPowertrainRowsAndKeepsEveryComponent() {
    UUID powertrainId = UUID.randomUUID();
    Map<String, Object> base =
        values(
            "powertrain_id", powertrainId,
            "powertrain_name", "듀얼 모터",
            "propulsion_type", "BEV",
            "primary_fuel_type", "ELECTRICITY");

    var powertrains =
        assembler.assemblePowertrains(
            List.of(
                row(with(base, "component_type", "MOTOR", "component_name", "전륜 모터")),
                row(with(base, "component_type", "BATTERY", "component_name", "구동 배터리"))));

    assertThat(powertrains)
        .singleElement()
        .satisfies(
            powertrain -> {
              assertThat(powertrain.powertrainId()).isEqualTo(powertrainId);
              assertThat(powertrain.components())
                  .extracting(PowertrainComponent::componentType)
                  .containsExactly("MOTOR", "BATTERY");
            });
  }

  @Test
  void groupsConfigurationRowsAndConvertsJsonAndOptions() {
    UUID configurationId = UUID.randomUUID();
    Map<String, Object> base =
        values(
            "variant_internal_id", 10L,
            "configuration_id", configurationId,
            "trim_name", "롱 레인지",
            "sales_status", "ON_SALE",
            "display_order", 1,
            "standard_features", "[{\"category\":\"안전\",\"name\":\"충돌방지\"}]",
            "warranties", "[]",
            "safety_ratings", "[]",
            "media", "[]");

    Map<Long, List<SalesConfiguration>> configurations =
        assembler.assembleConfigurations(
            List.of(
                row(option(base, "ADAS", "주행 보조", 1)), row(option(base, "SOUND", "프리미엄 사운드", 2))));

    assertThat(configurations.get(10L))
        .singleElement()
        .satisfies(
            configuration -> {
              assertThat(configuration.configurationId()).isEqualTo(configurationId);
              assertThat(configuration.standardFeatures())
                  .singleElement()
                  .extracting(StandardFeature::name)
                  .isEqualTo("충돌방지");
              assertThat(configuration.options())
                  .extracting(Option::code)
                  .containsExactly("ADAS", "SOUND");
            });
  }

  @Test
  void groupsVariantRowsAndAttachesConfigurations() {
    UUID variantId = UUID.randomUUID();
    UUID powertrainId = UUID.randomUUID();
    SalesConfiguration configuration =
        assembler
            .assembleConfigurations(
                List.of(
                    row(
                        values(
                            "variant_internal_id", 10L,
                            "configuration_id", UUID.randomUUID(),
                            "trim_name", "기본형",
                            "sales_status", "ON_SALE",
                            "display_order", 1,
                            "standard_features", "[]",
                            "warranties", "[]",
                            "safety_ratings", "[]",
                            "media", "[]"))))
            .get(10L)
            .getFirst();
    Map<String, Object> base =
        values(
            "variant_internal_id",
            10L,
            "variant_id",
            variantId,
            "powertrain_id",
            powertrainId,
            "variant_name",
            "SUV",
            "body_type",
            "SUV",
            "seating_capacity",
            5,
            "length_mm",
            4700,
            "width_mm",
            1900,
            "height_mm",
            1600,
            "wheelbase_mm",
            2900);

    var variants =
        assembler.assembleVariants(
            List.of(
                row(with(base, "condition_code", "NORMAL", "condition_name", "상온")),
                row(with(base, "condition_code", "LOW", "condition_name", "저온"))),
            Map.of(10L, List.of(configuration)));

    assertThat(variants)
        .singleElement()
        .satisfies(
            variant -> {
              assertThat(variant.variantId()).isEqualTo(variantId);
              assertThat(variant.certifications())
                  .extracting(Certification::conditionCode)
                  .containsExactly("NORMAL", "LOW");
              assertThat(variant.configurations()).containsExactly(configuration);
            });
  }

  private static Map<String, Object> option(
      Map<String, Object> base, String code, String name, int displayOrder) {
    return with(
        base,
        "option_code",
        code,
        "option_name",
        name,
        "option_price_before_tax_benefit",
        1_000L,
        "included_items",
        "[]",
        "option_display_order",
        displayOrder);
  }

  private static Map<String, Object> values(Object... entries) {
    Map<String, Object> values = new HashMap<>();
    for (int index = 0; index < entries.length; index += 2) {
      values.put((String) entries[index], entries[index + 1]);
    }
    return values;
  }

  private static Map<String, Object> with(Map<String, Object> base, Object... entries) {
    Map<String, Object> values = new HashMap<>(base);
    values.putAll(values(entries));
    return values;
  }

  private static CatalogProjectionRecord row(Map<String, Object> values) {
    Record row =
        mock(
            Record.class,
            invocation -> {
              if (invocation.getMethod().getName().equals("get")
                  && invocation.getArguments().length == 2
                  && invocation.getArgument(0) instanceof String field) {
                return values.get(field);
              }
              return RETURNS_DEFAULTS.answer(invocation);
            });
    return new CatalogProjectionRecord(row);
  }
}
