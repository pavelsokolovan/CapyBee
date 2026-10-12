package com.capybee.engine.cli;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.UuidIdSource;
import com.capybee.engine.port.IdSource;

import java.io.InputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.random.RandomGenerator;

public final class PlayMain {
    private PlayMain() {}

    public static void main(String[] args) {
        int exitCode = new ConsoleApp(System.in, System.out, System.err,
                Clock.systemUTC(), new UuidIdSource(), Randoms.unseeded()).run(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
