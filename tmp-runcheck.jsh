import com.capybee.engine.api.*;
import com.capybee.engine.adapter.*;
import com.capybee.engine.pack.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
var source = new DirectoryPackSource(Path.of("engine", "fixtures", "game-packs", "fixture-minimal"));
var pack = new com.capybee.engine.pack.json.PackLoader(com.capybee.engine.api.Engine.sectionValidators()).load(new PackId("fixture-minimal"), source);
var engine = Engine.builder(new PackRegistry(java.util.List.of(pack)))
    .clock(Clock.fixed(Instant.parse("2026-10-08T10:00:00Z"), ZoneOffset.UTC))
    .random(Randoms.seeded(42L))
    .ids(new SequentialIdSource("run"))
    .build();
var request = new RunRequest(new PackId("fixture-minimal"), null, Map.of(), 99L);
var result = engine.start(request);
System.out.println(result);
System.out.println(result.ok());
System.out.println(result.issues());
