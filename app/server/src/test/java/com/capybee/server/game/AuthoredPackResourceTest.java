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
        var classpath=new ClasspathPackSource(getClass().getClassLoader(),"game-packs/"+id.value());
        var packaged=loader.load(id,classpath);
        assertThat(packaged.manifest()).isEqualTo(canonical.manifest());assertThat(packaged.files()).isEqualTo(canonical.files());
        assertThat(classpath.read("pack.json")).hasValue(Files.readString(Path.of("../engine/reference-game-packs",id.value(),"pack.json"),StandardCharsets.UTF_8));
        assertThat(packaged.manifest().locales()).containsExactly(new LocaleTag("pl"));
        assertThat(packaged.manifest().features()).doesNotContain(PackFeature.GENERATION);
        var configuration=new GameSessionConfiguration();assertThat(configuration.gamePackRegistry("").all()).isEmpty();
        assertThat(configuration.gamePackRegistry(id.value()).all()).hasSize(1);
    }
}
