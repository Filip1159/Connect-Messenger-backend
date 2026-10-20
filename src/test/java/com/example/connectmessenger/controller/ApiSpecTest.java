package com.example.connectmessenger.controller;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docs/openapi.json is what the frontend generates its types from. It must describe the API as it is:
 * after changing an endpoint or a DTO run {@code UPDATE_OPENAPI=true sh ./gradlew test --tests '*ApiSpecTest'}
 * and commit the new file (the frontend copies it to its own repo).
 */
class ApiSpecTest extends ControllerComponentTest {
    private static final Path SPEC = Path.of("docs", "openapi.json");

    @Test
    void theCommittedSpecMatchesTheApi() throws Exception {
        var mapper = JsonMapper.builder().build();
        String served = as(janek()).get("/v3/api-docs").then().statusCode(200).extract().asString();
        var tree = (ObjectNode) mapper.readTree(served);
        tree.remove("servers");  // the generated URL has the random test port in it
        String current = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree) + "\n";

        if (System.getenv("UPDATE_OPENAPI") != null) {
            Files.writeString(SPEC, current);
        }
        assertThat(SPEC).as("run with UPDATE_OPENAPI=true to create it").exists();
        assertThat(Files.readString(SPEC)).as("docs/openapi.json is stale, regenerate it (see this class)").isEqualTo(current);
    }
}
