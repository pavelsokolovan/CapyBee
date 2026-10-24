package com.capybee.engine.generation.blueprint.json;

import static org.assertj.core.api.Assertions.assertThat;
import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.generation.blueprint.ParsedGameBlueprint;
import com.capybee.engine.generation.blueprint.BlueprintIssue;
import com.capybee.engine.generation.blueprint.ParsedGameBlueprint.*;
import com.capybee.engine.generation.profile.*;
import com.capybee.engine.pack.*;
import com.capybee.engine.rules.ResultClass;
import java.util.*;
import org.junit.jupiter.api.Test;

class BlueprintOutputParserTest {
    private final BlueprintOutputParser parser=new BlueprintOutputParser();
    private final BlueprintWriter writer=new BlueprintWriter();
    private final Context context=context();

    @Test void parsesCompleteProposalAndRoundTripsDeterministically(){
        ParsedGameBlueprint source=proposal();String json=writer.writeString(source);
        var first=parser.parse(json,context.profile,context.preferences,context.constraints);
        assertThat(first.accepted()).isTrue();assertThat(first.blueprint()).contains(source);
        assertThat(writer.write(first.blueprint().orElseThrow())).isEqualTo(writer.write(source));
        assertThat(parser.parse(writer.write(source),context.profile,context.preferences,context.constraints).blueprint()).contains(source);
    }
    @Test void rejectsDuplicateAndUnknownKeysWithoutLeakingValues(){
        String json=writer.writeString(proposal());
        var duplicate=parser.parse(json.replaceFirst("\\{","{\"title\":\"private-secret\","),context.profile,context.preferences,context.constraints);
        assertThat(duplicate.accepted()).isFalse();assertThat(duplicate.issues().getFirst().code()).isEqualTo("BLUEPRINT_DUPLICATE_KEY");assertThat(duplicate.toString()).doesNotContain("private-secret");
        var unknown=parser.parse(json.replaceFirst("\\{","{\"private-secret\":\"private-secret\","),context.profile,context.preferences,context.constraints);
        assertThat(unknown.issues().getFirst().code()).isEqualTo("BLUEPRINT_UNKNOWN_FIELD");assertThat(unknown.issues().getFirst().pointer()).isEmpty();assertThat(unknown.toString()).doesNotContain("private-secret");
    }
    @Test void rejectsMismatchedContextBeforeReadingContent(){
        var wrong=new ResolvedGamePreferences(new ProfileIdentity(new PackId("other"),new PackVersion(1,0,0),"other"),new LocaleTag("en"),"short","gentle",Optional.empty(),Optional.empty(),Optional.empty(),List.of());
        var result=parser.parse("private-not-json",context.profile,wrong,context.constraints);
        assertThat(result.issues().getFirst().code()).isEqualTo("BLUEPRINT_CONTEXT_MISMATCH");assertThat(result.toString()).doesNotContain("private-not-json");
    }
    @Test void unresolvedAndCyclicSymbolsRemainOnlyParsedProposals(){
        String json=writer.writeString(proposal()).replace("\"destinationSceneId\":\"middle\"","\"destinationSceneId\":\"missing-scene\"");
        var result=parser.parse(json,context.profile,context.preferences,context.constraints);
        assertThat(result.accepted()).isTrue();assertThat(result.blueprint().orElseThrow().toString()).isEqualTo("ParsedGameBlueprint[redacted]");
    }
    @Test void publishesBaseAndContextSpecializedSchemas(){
        var schemas=new BlueprintSchemas();
        assertThat(schemas.baseSchema()).contains("draft/2020-12").contains("additionalProperties");
        String specialized=schemas.specializedSchema(context.profile,context.preferences,context.constraints);
        assertThat(specialized).contains("\"locale\":\"en\"").contains("FLAG_IS").doesNotContain("COUNTER_ADD").doesNotContain("SET_ADD");
    }
    @Test void rejectsDecodedLoneSurrogate(){
        String json=writer.writeString(proposal()).replace("Signal Path","\\uD800");
        assertThat(parser.parse(json,context.profile,context.preferences,context.constraints).issues())
                .extracting(BlueprintIssue::code).contains("BLUEPRINT_TEXT_INVALID");
    }

    private static ParsedGameBlueprint proposal(){
        var condition=new FlagCondition("ready",true);var actors=List.of(new Actor("guide","Mika","A helpful guide","Patient and observant."));
        var state=new State(List.of(),List.of(new Flag("ready",false)),List.of());
        var objective=List.of(new Objective("finish","Finish the task","Prepare a safe signal.",condition));
        var phases=Arrays.stream(PhaseKind.values()).map(k->new Phase(k,"Follow the current goal.","The plan develops clearly.","Choose the next useful step.",List.of("guide"),List.of("finish"))).toList();
        List<Scene> scenes=List.of(decision("start",PhaseKind.OPENING,"middle",List.of()),decision("middle",PhaseKind.DEVELOPMENT,"challenge",List.of()),decision("challenge",PhaseKind.CHALLENGE,"resolve",List.of(new FlagEffect("ready",true))),decision("resolve",PhaseKind.RESOLUTION,"success",List.of()),new EndingScene("success",PhaseKind.ENDING,"A bright result","Recognize the completed work.",List.of("guide"),List.of("finish"),"won"),new EndingScene("partial",PhaseKind.ENDING,"A useful pause","Recognize useful progress.",List.of("guide"),List.of("finish"),"helped"));
        return new ParsedGameBlueprint(1,"en","Signal Path","A team prepares a signal together.","A quiet workshop.",List.of("gentle"),List.of("Make a thoughtful choice"),actors,state,objective,phases,"start",scenes,List.of(new Outcome("won",ResultClass.SUCCESS,"Signal ready","The signal is ready."),new Outcome("helped",ResultClass.PARTIAL_SUCCESS,"Progress made","The team made progress.")));
    }
    private static DecisionScene decision(String id,PhaseKind phase,String destination,List<Effect> effects){return new DecisionScene(id,phase,"A careful step","Plan the next action.",List.of("guide"),List.of("finish"),List.of(new Choice("continue","Continue with the plan.",destination,null,effects)));}
    private static Context context(){
        var identity=new ProfileIdentity(new PackId("test-profile"),new PackVersion(1,0,0),"fingerprint");
        var caps=new ProfileCapabilities(List.of(PhaseKind.values()),"FORWARD_DAG",false,6,12,4,1,4,1,4,2,4,4,12,2,8,0,10,3,4,32,6,100,Set.of("FLAG_IS"),Set.of("FLAG_SET"));
        var length=new LengthPreset("short","length.short",5,7,12);
        var challenge=new ChallengePreset("gentle","challenge.gentle",4,12,2,4,6,Set.of("FLAG_IS"),Set.of("FLAG_SET"));
        var policy=new GeneratorProfile.ProfilePolicy(10,14,true,300,Set.of());
        var raw=new GeneratorProfile(1,identity.id(),identity.version(),"title","description",List.of(new LocaleTag("en")),new LocaleTag("en"),policy,caps,List.of(length),List.of(challenge),new ProfileGenerationBudget(20,2,2000,2,1000,1200),null,Map.of(),Map.of(),Map.of(),List.of(),List.of());
        var validated=ProfileValidationFactory.validated(raw,identity);
        var prefs=new ResolvedGamePreferences(identity,new LocaleTag("en"),"short","gentle",Optional.empty(),Optional.empty(),Optional.empty(),List.of());
        var limits=new EffectiveGenerationConstraints(5,7,6,12,4,12,2,4,6,Set.of("FLAG_IS"),Set.of("FLAG_SET"),20,2000,1000,1200);
        return new Context(validated,prefs,limits);
    }
    private record Context(ValidatedGeneratorProfile profile,ResolvedGamePreferences preferences,EffectiveGenerationConstraints constraints){}
}
