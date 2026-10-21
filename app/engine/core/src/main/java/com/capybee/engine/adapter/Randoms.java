package com.capybee.engine.adapter;

import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public final class Randoms {
    public static final String L64X128_MIX_RANDOM = "L64X128MixRandom";

    private Randoms() {}

    public static RandomGenerator seeded(long seed) {
        RandomGeneratorFactory factory = RandomGeneratorFactory.of(L64X128_MIX_RANDOM);
        if (factory == null) {
            throw new IllegalStateException("Random generator 'L64X128MixRandom' is unavailable");
        }
        return factory.create(seed);
    }

    public static RandomGenerator unseeded() {
        return seeded(System.nanoTime());
    }
}
