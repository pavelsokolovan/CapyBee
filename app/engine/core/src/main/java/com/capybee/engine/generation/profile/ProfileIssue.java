package com.capybee.engine.generation.profile;

/** A content-free, stable diagnostic produced while loading or resolving a profile. */
public record ProfileIssue(String code, String resource, String pointer, String message) {}
