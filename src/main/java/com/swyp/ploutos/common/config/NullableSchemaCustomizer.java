package com.swyp.ploutos.common.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;

/**
 * swagger-core가 OpenAPI 3.1에서 null을 허용하는 객체·enum 속성을 잘못 표현하는 것을 문서 생성 뒤 고친다.
 * 어노테이션으로는 고칠 수 없다(어떤 조합이든 {@code $ref} 옆에 {@code type}이 붙는다).
 * <ul>
 *   <li>{@code $ref}와 null 타입이 함께 있으면 "null만 허용"이 되므로 {@code anyOf: [$ref, null]}로 바꾼다.</li>
 *   <li>null 타입 enum의 {@code enum}에 null이 없으면 null 값이 명세를 어기므로 null을 넣는다.</li>
 * </ul>
 */
@Component
class NullableSchemaCustomizer implements OpenApiCustomizer {

    private static final String NULL = "null";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        for (Schema<?> schema : openApi.getComponents().getSchemas().values()) {
            Map<String, Schema> properties = schema.getProperties();
            if (properties != null) {
                properties.replaceAll((name, property) -> fix(property));
            }
        }
    }

    private Schema fix(Schema property) {
        Set<String> types = property.getTypes();
        if (types == null || !types.contains(NULL)) {
            return property;
        }
        if (property.get$ref() != null) {
            return refOrNull(property);
        }
        if (property.getEnum() != null && !property.getEnum().contains(null)) {
            property.getEnum().add(null);
        }
        return property;
    }

    private static Schema refOrNull(Schema property) {
        Schema ref = new JsonSchema().$ref(property.get$ref());
        Schema nullType = new JsonSchema().types(Set.of(NULL));
        return new JsonSchema()
                .anyOf(List.of(ref, nullType))
                .description(property.getDescription());
    }
}
