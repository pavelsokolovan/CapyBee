package com.capybee.engine.pack;

import java.util.Optional;

public record PackVersion(int major, int minor, int patch) implements Comparable<PackVersion> {
    public PackVersion {
        if (major < 0 || minor < 0 || patch < 0) throw new IllegalArgumentException("Negative version");
    }

    public static Optional<PackVersion> tryParse(String text) {
        if (text == null || !text.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")) {
            return Optional.empty();
        }
        String[] parts = text.split("\\.");
        try {
            return Optional.of(new PackVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    @Override
    public int compareTo(PackVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        return result == 0 ? Integer.compare(patch, other.patch) : result;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}