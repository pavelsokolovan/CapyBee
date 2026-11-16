package com.capybee.server.game;

import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.*;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class AuthoredPackResourceTest {
    @Test void packagedPolishResourcesMatchCanonicalPackAndRequireExplicitAllowlisting() throws Exception {
        var id=new PackId("capybee-new-hive");var loader=new PackLoader(Engine.sectionValidators());
        var canonical=loader.load(id,new DirectoryPackSource(Path.of("../engine/reference-game-packs",id.value())));
        var classpath=new ClasspathPackSource(getClass().getClassLoader(),"game-packs/"+id.value()+"/retained/1.0.0");
        var packaged=loader.load(id,classpath);
        assertThat(packaged.manifest()).isEqualTo(canonical.manifest());assertThat(packaged.files()).isEqualTo(canonical.files());
        assertThat(classpath.read("pack.json")).hasValue(Files.readString(Path.of("../engine/reference-game-packs",id.value(),"pack.json"),StandardCharsets.UTF_8));
        assertThat(packaged.manifest().locales()).containsExactly(new LocaleTag("pl"));
        assertThat(packaged.manifest().features()).doesNotContain(PackFeature.GENERATION);
        var configuration=new GameSessionConfiguration();assertThat(configuration.gamePackRegistry("").all()).isEmpty();
        var registry=configuration.gamePackRegistry(id.value());assertThat(registry.all()).hasSize(1);
        assertThat(registry.find(id).orElseThrow().manifest().version().toString()).isEqualTo("1.3.0");
        var retained=registry.find(id,new PackVersion(1,1,0)).orElseThrow();
        var previous=loader.load(id,new DirectoryPackSource(Path.of("../engine/reference-game-pack-versions",id.value(),"1.1.0")));
        assertThat(retained.files()).isEqualTo(previous.files()); assertThat(retained.manifest()).isEqualTo(previous.manifest());
        var latest=loader.load(id,new DirectoryPackSource(Path.of("../engine/reference-game-pack-versions",id.value(),"1.3.0")));
        assertThat(registry.find(id).orElseThrow().files()).isEqualTo(latest.files());
        assertThat(registry.find(id,new PackVersion(1,0,0))).contains(packaged);
        for (String version : List.of("1.1.0", "1.2.0")) {
            var original = loader.load(id,new DirectoryPackSource(Path.of("../engine/reference-game-pack-versions",id.value(),version)));
            assertThat(registry.find(id,new PackVersion(1, version.equals("1.1.0") ? 1 : 2, 0)).orElseThrow().files()).isEqualTo(original.files());
        }
    }
}
