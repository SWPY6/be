package com.swyp.ploutos.common.config;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;

class NullableSchemaCustomizerTest {

    private final NullableSchemaCustomizer customizer = new NullableSchemaCustomizer();

    enum Precision { EXACT, DATE }

    record Range(String from) {
    }

    record Sample(
            @io.swagger.v3.oas.annotations.media.Schema(description = "범위", nullable = true, requiredMode = REQUIRED)
            Range nullableRange,
            Range range,
            @io.swagger.v3.oas.annotations.media.Schema(nullable = true) Precision nullablePrecision,
            Precision precision,
            @io.swagger.v3.oas.annotations.media.Schema(nullable = true) String nullableText
    ) {
    }

    @Test
    void null을_허용하는_객체_참조는_참조_또는_null로_바뀐다() {
        // given
        OpenAPI openApi = openApiOf(Sample.class);

        // when
        customizer.customise(openApi);

        // then
        Schema<?> property = property(openApi, "nullableRange");
        assertThat(property.get$ref()).isNull();
        assertThat(property.getTypes()).isNull();
        assertThat(property.getDescription()).isEqualTo("범위");
        assertThat(property.getAnyOf()).hasSize(2);
        assertThat(property.getAnyOf().get(0).get$ref()).isEqualTo("#/components/schemas/Range");
        assertThat(property.getAnyOf().get(1).getTypes()).containsExactly("null");
    }

    @Test
    void null을_허용하는_객체_참조가_바뀌어도_required는_유지된다() {
        // given
        OpenAPI openApi = openApiOf(Sample.class);

        // when
        customizer.customise(openApi);

        // then
        assertThat(openApi.getComponents().getSchemas().get("Sample").getRequired()).contains("nullableRange");
    }

    @Test
    void null을_허용하는_enum은_값_목록에_null이_들어간다() {
        // given
        OpenAPI openApi = openApiOf(Sample.class);

        // when
        customizer.customise(openApi);

        // then
        assertThat(enumOf(openApi, "nullablePrecision")).containsExactly("EXACT", "DATE", null);
    }

    @Test
    void null을_허용하지_않는_속성은_그대로_둔다() {
        // given
        OpenAPI openApi = openApiOf(Sample.class);

        // when
        customizer.customise(openApi);

        // then
        assertThat(property(openApi, "range").get$ref()).isEqualTo("#/components/schemas/Range");
        assertThat(enumOf(openApi, "precision")).containsExactly("EXACT", "DATE");
        assertThat(property(openApi, "nullableText").getTypes()).isEqualTo(Set.of("string", "null"));
    }

    @Test
    void 스키마가_없는_문서는_그대로_둔다() {
        // given
        OpenAPI openApi = new OpenAPI();

        // when
        customizer.customise(openApi);

        // then
        assertThat(openApi.getComponents()).isNull();
    }

    private static OpenAPI openApiOf(Class<?> type) {
        Map<String, Schema> schemas = ModelConverters.getInstance(true).readAll(type);
        return new OpenAPI().components(new Components().schemas(schemas));
    }

    private static Schema<?> property(OpenAPI openApi, String name) {
        Schema<?> sample = openApi.getComponents().getSchemas().get("Sample");
        return sample.getProperties().get(name);
    }

    /** enum 값 목록. null을 담을 수 있게 변경 가능한 목록으로 옮긴다. */
    private static List<Object> enumOf(OpenAPI openApi, String name) {
        return new ArrayList<>(property(openApi, name).getEnum());
    }
}
