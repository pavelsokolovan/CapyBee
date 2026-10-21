package com.capybee.engine.adapter.anthropic;

import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationException;
import com.capybee.engine.port.TextGenerationResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnthropicTextGeneratorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_KEY = "local-test-secret";
    private static final String RESPONSE = """
            {"type":"message","model":"test-pinned-model","stop_reason":"end_turn",
             "content":[{"type":"text","text":"{\\"resolution\\":\\"NO_MATCH\\"}"}],
             "usage":{"input_tokens":23,"output_tokens":7,"cache_read_input_tokens":2}}
            """;

    private StructuredGenerationRequest request() {
        return new StructuredGenerationRequest("non-identifying-request", "INPUT_RESOLUTION", "policy-v1",
                "Fixed safe instructions", "{\"text\":\"safe sentinel-free text\"}",
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"resolution\":{\"type\":\"string\"}},\"required\":[\"resolution\"]}",
                400, StructuredGenerationRequest.CostClass.LIGHT);
    }

    @Test
    void sendsSchemaAndDataSeparatelyAndParsesOutputAndUsage() throws IOException {
        AtomicReference<JsonNode> sent = new AtomicReference<>();
        withServer(exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("x-api-key")).isEqualTo(API_KEY);
            assertThat(exchange.getRequestHeaders().getFirst("anthropic-version")).isEqualTo("2023-06-01");
            sent.set(MAPPER.readTree(exchange.getRequestBody().readAllBytes()));
            respond(exchange, 200, RESPONSE);
        }, endpoint -> {
            var generator = new AnthropicTextGenerator(config(endpoint));

            TextGenerationResult result = generator.generate(request());

            assertThat(sent.get().get("max_tokens").intValue()).isEqualTo(400);
            assertThat(sent.get().get("output_config").get("format").get("schema").get("type").asString())
                    .isEqualTo("object");
            assertThat(sent.get().get("messages").get(0).get("content").asString())
                    .contains("safe sentinel-free text");
            assertThat(sent.get().get("system").asString()).contains("Fixed safe instructions");
            assertThat(sent.get().toString()).doesNotContain("non-identifying-request");
            assertThat(result.outputJson()).isEqualTo("{\"resolution\":\"NO_MATCH\"}");
            assertThat(result.usage()).isEqualTo(new TextGenerationResult.TokenUsage(23, 7, 2, 0));
            assertThat(result.modelId()).isEqualTo("test-pinned-model");
            assertThat(result.toString()).doesNotContain("NO_MATCH");
        });
    }

    @Test
    void sceneSlotKeyDoesNotChangeProviderPayloadBytes() throws IOException {
        AtomicReference<byte[]> withoutSlotKey = new AtomicReference<>();
        AtomicReference<byte[]> withSlotKey = new AtomicReference<>();
        withServer(exchange -> {
            withoutSlotKey.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, RESPONSE);
        }, endpoint -> new AnthropicTextGenerator(config(endpoint)).generate(request()));
        withServer(exchange -> {
            withSlotKey.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 200, RESPONSE);
        }, endpoint -> new AnthropicTextGenerator(config(endpoint)).generate(new StructuredGenerationRequest(
                "non-identifying-request", "INPUT_RESOLUTION", "policy-v1", "Fixed safe instructions",
                "{\"text\":\"safe sentinel-free text\"}",
                "{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"resolution\":{\"type\":\"string\"}},\"required\":[\"resolution\"]}",
                400, StructuredGenerationRequest.CostClass.LIGHT, "scene-slot")));

        assertThat(withSlotKey.get()).isEqualTo(withoutSlotKey.get());
    }

    @Test
    void ignoresThinkingBlocksBeforeReturningTextOutput() throws IOException {
        String withThinking = """
                {"type":"message","model":"test-pinned-model","stop_reason":"end_turn",
                 "content":[{"type":"thinking","thinking":"internal reasoning","signature":"sig"},
                            {"type":"text","text":"{\\"resolution\\":\\"NO_MATCH\\"}"}],
                 "usage":{"input_tokens":23,"output_tokens":7}}
                """;
        withServer(exchange -> respond(exchange, 200, withThinking), endpoint -> {
            assertThat(new AnthropicTextGenerator(config(endpoint)).generate(request()).outputJson())
                    .isEqualTo("{\"resolution\":\"NO_MATCH\"}");
        });
    }

    @Test
    void mapsProviderErrorsWithoutExposingResponseBodiesOrCredentials() throws IOException {
        withServer(exchange -> respond(exchange, 429, "private-provider-error"), endpoint -> {
            var generator = new AnthropicTextGenerator(config(endpoint));

            assertThatThrownBy(() -> generator.generate(request()))
                    .isInstanceOf(TextGenerationException.class)
                    .hasMessage("RATE_LIMITED")
                    .hasNoCause()
                    .hasToString("TextGenerationException[RATE_LIMITED]");
        });
    }

    @Test
    void distinguishesRequestAuthenticationPermissionAndResourceErrorsWithoutLeakingBodies() throws IOException {
        var expected = java.util.Map.of(400, "REQUEST_INVALID", 401, "AUTHENTICATION_FAILED",
                403, "PERMISSION_DENIED", 404, "RESOURCE_NOT_FOUND");
        for (var entry : expected.entrySet()) {
            withServer(exchange -> respond(exchange, entry.getKey(), "private-provider-error " + API_KEY), endpoint -> {
                assertThatThrownBy(() -> new AnthropicTextGenerator(config(endpoint)).generate(request()))
                        .isInstanceOf(TextGenerationException.class)
                        .hasMessage(entry.getValue())
                        .hasNoCause()
                        .hasToString("TextGenerationException[" + entry.getValue() + "]");
            });
        }
    }

    @Test
    void malformedUsageIsUnknownAndRefusalIsNotTreatedAsCompleteOutput() throws IOException {
        String malformedUsage = """
                {"type":"message","model":"test-pinned-model","stop_reason":"end_turn",
                 "content":[{"type":"text","text":"{}"}],"usage":{"input_tokens":-1,"output_tokens":0}}
                """;
        withServer(exchange -> respond(exchange, 200, malformedUsage), endpoint -> {
            assertThat(new AnthropicTextGenerator(config(endpoint)).generate(request()).usage()).isNull();
        });
        String refusal = """
                {"type":"message","model":"test-pinned-model","stop_reason":"refusal",
                 "content":[{"type":"text","text":"I cannot help with that."}]}
                """;
        withServer(exchange -> respond(exchange, 200, refusal), endpoint -> {
            assertThat(new AnthropicTextGenerator(config(endpoint)).generate(request()).finishReason())
                    .isEqualTo(TextGenerationResult.FinishReason.REFUSED);
        });
    }

    @Test
    void classifiesKnownBadRequestReasonsWithoutExposingProviderText() throws IOException {
        var cases = java.util.Map.of(
                "Your credit balance is too low to access the API.", "BILLING_UNAVAILABLE",
                "Insufficient credits for this request.", "BILLING_UNAVAILABLE",
                "output_config.format is not supported by this model.", "STRUCTURED_OUTPUT_UNSUPPORTED",
                "This model does not support structured outputs.", "STRUCTURED_OUTPUT_UNSUPPORTED",
                "Unknown request problem.", "REQUEST_INVALID");
        for (var entry : cases.entrySet()) {
            String body = MAPPER.writeValueAsString(java.util.Map.of("type", "error", "error",
                    java.util.Map.of("type", "invalid_request_error", "message", entry.getKey() + " " + API_KEY)));
            withServer(exchange -> respond(exchange, 400, body), endpoint -> {
                assertThatThrownBy(() -> new AnthropicTextGenerator(config(endpoint)).generate(request()))
                        .isInstanceOf(TextGenerationException.class)
                        .hasMessage(entry.getValue())
                        .hasNoCause()
                        .hasToString("TextGenerationException[" + entry.getValue() + "]");
            });
        }
    }

    @Test
    void configurationRequiresExplicitModelAndNeverRendersApiKey() {
        var config = config(URI.create("https://api.anthropic.com/v1/messages"));

        assertThat(config.toString()).doesNotContain(API_KEY);
        assertThatThrownBy(() -> new AnthropicConfig("", API_KEY,
                URI.create("https://api.anthropic.com/v1/messages"), Duration.ofSeconds(1), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Anthropic generation configuration is invalid");
    }

    private AnthropicConfig config(URI endpoint) {
        return new AnthropicConfig("test-pinned-model", API_KEY, endpoint,
                Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    private void withServer(Handler handler, EndpointAction action) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> handler.handle(exchange));
        server.start();
        try {
            action.run(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages"));
        } finally {
            server.stop(0);
        }
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }

    @FunctionalInterface
    private interface EndpointAction {
        void run(URI endpoint) throws IOException;
    }
}