package com.swyp.ploutos.disclosure.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;

class StockDisclosureResponseSchemaTest {

    private final Map<String, Schema> schemas = ModelConverters.getInstance(true).readAll(StockDisclosureResponse.class);

    @Test
    void 하위_스키마는_다른_모듈과_겹치지_않는_이름으로_등록된다() {
        // when
        Schema<?> response = schemas.get("StockDisclosureResponse");

        // then
        assertThat(schemas).containsOnlyKeys("StockDisclosureResponse", "StockDisclosureWindow",
                "StockDisclosureFiledDateRange", "StockDisclosureItem");
        assertThat(response.getProperties().get("items").getItems().get$ref())
                .isEqualTo("#/components/schemas/StockDisclosureItem");
    }

    @Test
    void 응답이_항상_내보내는_필드는_모두_required다() {
        // when & then
        schemas.forEach((name, schema) ->
                assertThat(schema.getRequired()).as(name).containsExactlyInAnyOrderElementsOf(schema.getProperties().keySet()));
    }
}
