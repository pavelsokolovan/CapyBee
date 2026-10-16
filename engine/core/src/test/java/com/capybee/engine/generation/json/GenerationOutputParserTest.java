package com.capybee.engine.generation.json;

import com.capybee.engine.generation.GenerationCatalog;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationOutputParserTest {
    @Test
    void allStageSchemasDefineEveryRequiredPropertyAndCloseNestedObjects() {
        var parser = new GenerationOutputParser();
        var mapper = new ObjectMapper();
        for (GenerationCatalog.Stage stage : GenerationCatalog.Stage.values()) {
            validateSchema(mapper.readTree(parser.schema(stage)));
        }
    }

    private void validateSchema(JsonNode schema) {
        String type = schema.get("type").asString();
        if (type.equals("object")) {
            assertThat(schema.get("additionalProperties").booleanValue()).isFalse();
            JsonNode properties = schema.get("properties");
            assertThat(properties).isNotNull();
            Set<String> names = new HashSet<>();
            properties.propertyNames().forEach(names::add);
            Set<String> required = new HashSet<>();
            schema.get("required").forEach(field -> required.add(field.asString()));
            assertThat(required).isEqualTo(names).isNotEmpty();
            properties.properties().forEach(property -> validateSchema(property.getValue()));
        } else if (type.equals("array")) {
            assertThat(schema.get("items")).isNotNull();
            validateSchema(schema.get("items"));
        } else {
            assertThat(type).isEqualTo("string");
        }
    }
}