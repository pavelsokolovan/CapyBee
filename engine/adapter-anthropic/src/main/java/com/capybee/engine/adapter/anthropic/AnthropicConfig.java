package com.capybee.engine.adapter.anthropic;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record AnthropicConfig(String modelId, String apiKey, URI endpoint,
                              Duration connectTimeout, Duration responseTimeout) {
    private static final URI DEFAULT_ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");

    public AnthropicConfig {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(apiKey, "apiKey");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(connectTimeout, "connectTimeout");
        Objects.requireNonNull(responseTimeout, "responseTimeout");
        if (modelId.isBlank() || !modelId.matches("[A-Za-z0-9._:-]{3,100}")) {
            throw configurationError();
        }
        if (apiKey.isBlank()) throw configurationError();
        boolean secure = "https".equalsIgnoreCase(endpoint.getScheme())
            && "api.anthropic.com".equalsIgnoreCase(endpoint.getHost());
        boolean localTestEndpoint = "http".equalsIgnoreCase(endpoint.getScheme())
                && ("localhost".equalsIgnoreCase(endpoint.getHost()) || "127.0.0.1".equals(endpoint.getHost())
                || "::1".equals(endpoint.getHost()));
        if ((!secure && !localTestEndpoint) || endpoint.getHost() == null || endpoint.getUserInfo() != null
            || !"/v1/messages".equals(endpoint.getPath()) || endpoint.getQuery() != null
            || endpoint.getFragment() != null) {
            throw configurationError();
        }
        validateTimeout(connectTimeout);
        validateTimeout(responseTimeout);
    }

    public static AnthropicConfig fromEnvironment() {
        String model = System.getenv("GENERATION_MODEL_ID");
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (model == null || key == null) throw configurationError();
        return new AnthropicConfig(model, key, DEFAULT_ENDPOINT, Duration.ofSeconds(5), Duration.ofSeconds(25));
    }

    @Override
    public String toString() {
        return "AnthropicConfig[modelId=" + modelId + ", apiKey=redacted, endpoint=" + endpoint
                + ", connectTimeout=" + connectTimeout + ", responseTimeout=" + responseTimeout + "]";
    }

    private static void validateTimeout(Duration timeout) {
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofMinutes(1)) > 0) {
            throw configurationError();
        }
    }

    private static IllegalArgumentException configurationError() {
        return new IllegalArgumentException("Anthropic generation configuration is invalid");
    }
}