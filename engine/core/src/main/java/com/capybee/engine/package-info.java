/**
 * Universal, headless runtime. Game-specific content belongs in static packs.
 * Hosts depend on the engine, never the reverse. Domain values are immutable;
 * JSON processing stays in JSON packages, I/O in sources and host adapters,
 * and time, random values and identifiers are supplied through ports.
 */
package com.capybee.engine;