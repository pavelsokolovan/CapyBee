package com.capybee.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DatabaseUrlNormalizerTest {

    @Test
    void normalizesFlyStylePostgresUrl() {
        assertEquals(
            "jdbc:postgresql://host:5432/capybee?sslmode=require",
            DatabaseUrlNormalizer.normalize("postgres://user:pass@host:5432/capybee?sslmode=require")
        );
    }

    @Test
    void normalizesPostgresqlSchemeWithoutJdbcPrefix() {
        assertEquals(
            "jdbc:postgresql://host:5432/capybee?sslmode=require",
            DatabaseUrlNormalizer.normalize("postgresql://user:pass@host:5432/capybee?sslmode=require")
        );
    }

    @Test
    void leavesJdbcUrlUntouched() {
        assertEquals(
            "jdbc:postgresql://localhost:5432/capybee",
            DatabaseUrlNormalizer.normalize("jdbc:postgresql://localhost:5432/capybee")
        );
    }
}
