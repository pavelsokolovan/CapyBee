package com.capybee.server.config;

import java.net.URI;
import java.net.URISyntaxException;

public final class DatabaseUrlNormalizer {

    private DatabaseUrlNormalizer() {
    }

    public static String normalize(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return rawUrl;
        }

        String trimmed = rawUrl.trim();
        if (trimmed.startsWith("jdbc:")) {
            return trimmed;
        }

        String candidate = trimmed;
        if (candidate.startsWith("postgres://")) {
            candidate = "postgresql://" + candidate.substring("postgres://".length());
        }

        if (candidate.startsWith("postgresql://")) {
            try {
                URI uri = new URI(candidate);
                StringBuilder jdbc = new StringBuilder("jdbc:postgresql://");
                if (uri.getHost() != null) {
                    jdbc.append(uri.getHost());
                }
                if (uri.getPort() != -1) {
                    jdbc.append(":").append(uri.getPort());
                }
                if (uri.getPath() != null && !uri.getPath().isBlank()) {
                    jdbc.append(uri.getPath());
                }
                if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                    jdbc.append("?").append(uri.getQuery());
                }
                return jdbc.toString();
            } catch (URISyntaxException ex) {
                return trimmed;
            }
        }

        return trimmed;
    }
}
