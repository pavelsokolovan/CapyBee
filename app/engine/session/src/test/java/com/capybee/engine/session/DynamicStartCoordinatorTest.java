package com.capybee.engine.session;

import com.capybee.engine.api.Engine;
import com.capybee.engine.generation.blueprint.DefaultBlueprintOutputReviewer;
import com.capybee.engine.generation.profile.*;
import com.capybee.engine.generation.profile.json.GeneratorProfileLoader;
import com.capybee.engine.input.DefaultInputGuard;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.port.TextGenerationException;
import com.capybee.engine.port.TextGenerator;
import com.capybee.engine.session.SessionTypes.OwnerRef;
import com.capybee.engine.session.adapter.InMemorySessionStore;
import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicStartCoordinatorTest {
    private static final Clock CLOCK=Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"),ZoneOffset.UTC);

    @Test void validatesIdsFailsClosedAndReplaysTerminalResult() throws Exception {
        var store=new InMemorySessionStore(CLOCK);
        SessionPorts.PackCatalog catalog=new SessionPorts.PackCatalog(){public List<Pack> all(){return List.of();}public Optional<Pack> find(String id){return Optional.empty();}};
        RunSessionService sessions=new RunSessionService(catalog,store,store,store,store,
                (owner,id,priority)->new Engine(){
                    public com.capybee.engine.api.StartResult start(com.capybee.engine.api.RunRequest request){return null;}
                    public com.capybee.engine.api.ResumeResult resume(com.capybee.engine.api.RunSnapshot snapshot){return null;}
                },
                CLOCK::instant,SessionLimits.defaults(),store,Runnable::run,new byte[32]);
        TextGenerator provider=request->{throw new TextGenerationException(TextGenerationException.Code.UNAVAILABLE);};
        var coordinator=new DynamicStartCoordinator(sessions,new SessionGenerators(provider,store,SessionLimits.defaults()),
                new DefaultBlueprintOutputReviewer(),new DefaultInputGuard(),()->UUID.randomUUID().toString(),CLOCK);
        assertThat(coordinator.startDynamic(null,UUID.randomUUID(),null,null,null,Duration.ofSeconds(1)))
                .isEqualTo(new DynamicStartCoordinator.Failed("DYNAMIC_REQUEST_INVALID"));

        var profile=profile();
        var resolved=new PreparationPresetResolver().resolve(profile,Map.of(),profile.profile().defaultLocale(),HostGenerationCeilings.none());
        var preparation=new ConfirmedDynamicPreparation(profile,resolved.preferences().orElseThrow(),resolved.constraints().orElseThrow(),1,null);
        assertThat(preparation.toString()).contains("suggestion=redacted");
        assertThat(preparation.profile()).isSameAs(profile);
        assertThat(preparation.preferences()).isEqualTo(resolved.preferences().orElseThrow());
        assertThat(preparation.constraints()).isEqualTo(resolved.constraints().orElseThrow());
        assertThat(preparation.revision()).isEqualTo(1);
        assertThat(new DynamicStartCoordinator.NeedsInput("INPUT_BLOCKED").code()).isEqualTo("INPUT_BLOCKED");
        assertThat(new DynamicStartCoordinator.FallbackOffered("FALLBACK").code()).isEqualTo("FALLBACK");
        assertThat(new DynamicStartCoordinator.Cancelled("CANCELLED").code()).isEqualTo("CANCELLED");
        assertThat(new DynamicStartCoordinator.Started(null).run()).isNull();
        UUID requestId=UUID.randomUUID();
        var first=coordinator.startDynamic(new OwnerRef("owner"),requestId,preparation,7L,null,Duration.ofSeconds(2));
        assertThat(first).isInstanceOf(DynamicStartCoordinator.Failed.class);
        assertThat(coordinator.startDynamic(new OwnerRef("owner"),requestId,preparation,7L,null,Duration.ofSeconds(2))).isSameAs(first);
        assertThat(coordinator.startDynamic(new OwnerRef("owner"),requestId,preparation,8L,null,Duration.ofSeconds(2)))
                .isEqualTo(new DynamicStartCoordinator.Failed("DYNAMIC_REQUEST_CONFLICT"));
    }

    private static ValidatedGeneratorProfile profile() throws Exception {
        Path root=Path.of("..","generator-profiles","generic-adventure").toAbsolutePath().normalize();
        Map<String,byte[]> files=new LinkedHashMap<>();
        try(var paths=Files.walk(root)){for(Path path:paths.filter(Files::isRegularFile).sorted().toList())files.put(root.relativize(path).toString().replace('\\','/'),Files.readAllBytes(path));}
        return new GeneratorProfileLoader().load(files).profile().orElseThrow();
    }
}
