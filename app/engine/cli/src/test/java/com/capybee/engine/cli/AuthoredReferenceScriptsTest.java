package com.capybee.engine.cli;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.UuidIdSource;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import static org.assertj.core.api.Assertions.*;

/** Deterministic source-neutral CLI checks, driven by reviewed reference data. */
class AuthoredReferenceScriptsTest {
    @Test void everyReviewedAuthoredRouteRunsAsAStrictOfflineScript() throws Exception {
        var fixtures=new ObjectMapper().readTree(Files.readString(Path.of("../fixtures/reference-authored-routes.json")));
        for(var pack:fixtures.path("packs"))for(var route:pack.path("routes")) {
            var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();
            var app=new ConsoleApp(new ByteArrayInputStream(new byte[0]),new PrintStream(out,true,StandardCharsets.UTF_8),
                    new PrintStream(err,true,StandardCharsets.UTF_8),Clock.systemUTC(),new UuidIdSource(),Randoms.seeded(7));
            int code=app.run(new String[]{"play","--pack-dir",Path.of("../reference-game-packs",pack.path("packId").asString()).toString(),
                    "--script",Path.of("../certification/authored-reference-v1/routes",route.path("id").asString()+".script").toString(),
                    "--locale",pack.path("locale").asString(),"--memory","--offline","--no-color"});
            assertThat(code).as("offline script %s: %s",route.path("id").asString(),err.toString(StandardCharsets.UTF_8)).isZero();
            assertThat(out.toString(StandardCharsets.UTF_8)).contains(route.path("requiredText").asString());
            assertThat(err.toString(StandardCharsets.UTF_8)).doesNotContain("SCRIPT_INVALID","SCRIPT_INCOMPLETE","START_REJECTED");
        }
    }

    @org.junit.jupiter.api.io.TempDir Path temporary;
    @Test void aFreshOfflineProcessResumesEachRouteFromItsFirstAcceptedRevision() throws Exception {
        var fixtures=new ObjectMapper().readTree(Files.readString(Path.of("../fixtures/reference-authored-routes.json")));
        for(var pack:fixtures.path("packs"))for(var route:pack.path("routes")) {
            var root=Files.createDirectory(temporary.resolve(route.path("id").asString()));
            var lines=Files.readAllLines(Path.of("../certification/authored-reference-v1/routes",route.path("id").asString()+".script"));
            var first=root.resolve("first.script");Files.writeString(first,lines.getFirst()+"\n/quit\n");
            var rest=root.resolve("rest.script");Files.writeString(rest,String.join("\n",lines.subList(1,lines.size()))+"\n");
            var base=new java.util.ArrayList<>(java.util.List.of("play","--pack-dir",Path.of("../reference-game-packs",pack.path("packId").asString()).toAbsolutePath().toString(),
                    "--store-dir",root.resolve("store").toString(),"--offline","--prefetch","off","--no-color"));
            var initial=new java.util.ArrayList<>(base);initial.addAll(java.util.List.of("--script",first.toString()));
            launch(initial);
            var resumed=new java.util.ArrayList<>(base);resumed.addAll(java.util.List.of("--continue","--script",rest.toString()));
            assertThat(launch(resumed)).contains(route.path("requiredText").asString());
        }
    }
    String launch(java.util.List<String> args) throws Exception {
        var command=new java.util.ArrayList<>(java.util.List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Dfile.encoding=UTF-8","-cp",
                System.getProperty("surefire.test.class.path",System.getProperty("java.class.path")),PlayMain.class.getName()));command.addAll(args);
        var builder=new ProcessBuilder(command).redirectErrorStream(true);builder.environment().put("GENERATION_PROVIDER_MODE","disabled");
        var process=builder.start();boolean finished=process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS);
        if(!finished)process.destroyForcibly();assertThat(finished).as("offline CLI process terminates").isTrue();
        var text=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as("offline CLI process: %s",text).isZero();return text;
    }
}
