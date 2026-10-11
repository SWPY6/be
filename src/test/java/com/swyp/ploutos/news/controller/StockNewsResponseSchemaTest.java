package com.swyp.ploutos.news.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;

class StockNewsResponseSchemaTest {

    private final Map<String, Schema> schemas = ModelConverters.getInstance(true).readAll(StockNewsResponse.class);

    @Test
    void 하위_스키마는_다른_모듈과_겹치지_않는_이름으로_등록된다() {
        // when
        Schema<?> response = schemas.get("StockNewsResponse");

        // then
        assertThat(schemas).containsOnlyKeys("StockNewsResponse", "StockNewsWindow", "StockNewsItem");
        assertThat(response.getProperties().get("items").getItems().get$ref())
                .isEqualTo("#/components/schemas/StockNewsItem");
        assertThat(response.getProperties().get("window").get$ref())
                .isEqualTo("#/components/schemas/StockNewsWindow");
    }

    @Test
    void 응답이_항상_내보내는_필드는_모두_required다() {
        // when & then
        schemas.forEach((name, schema) ->
                assertThat(schema.getRequired()).as(name).containsExactlyInAnyOrderElementsOf(schema.getProperties().keySet()));
    }
}
