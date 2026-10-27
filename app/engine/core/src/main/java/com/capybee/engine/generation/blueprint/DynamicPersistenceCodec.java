package com.capybee.engine.generation.blueprint;

import com.capybee.engine.content.*;
import com.capybee.engine.generation.GenerationArtifact;
import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.generation.profile.*;
import com.capybee.engine.pack.*;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.rules.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.*;

/** Explicit canonical tree codec for trusted dynamic definitions and presentation state. */
public final class DynamicPersistenceCodec {
    private static final ObjectMapper JSON = new ObjectMapper();
    private DynamicPersistenceCodec() {}

    public static byte[] definitionBytes(MaterializedGameDefinition value) {
        return JSON.writeValueAsBytes(writeDefinition(value));
    }

    public static byte[] continuationBytes(DynamicPresentationContinuation value) {
        return JSON.writeValueAsBytes(writeContinuation(value));
    }

    public static ObjectNode writeDefinition(MaterializedGameDefinition d) {
        ObjectNode n = JSON.createObjectNode();
        n.put("definitionFormatVersion", d.definitionFormatVersion()); n.put("compilerVersion", d.compilerVersion());
        n.put("origin", d.origin().name()); n.set("profileIdentity", writeProfile(d.profileIdentity()));
        n.put("locale", d.locale().value()); n.put("lengthPresetId", d.lengthPresetId());
        n.put("challengePresetId", d.challengePresetId()); n.put("blueprintSchemaVersion", d.blueprintSchemaVersion());
        n.put("generatedRulesContractVersion", d.generatedRulesContractVersion());
        n.put("semanticValidatorVersion", d.semanticValidatorVersion()); n.put("outputReviewerVersion", d.outputReviewerVersion());
        n.set("storyPlan", JSON.valueToTree(d.storyPlan())); n.set("actors", JSON.valueToTree(d.actors()));
        n.set("phases", JSON.valueToTree(d.phases())); n.set("scenePlans", JSON.valueToTree(d.scenePlans()));
        ArrayNode scenes=n.putArray("canonicalScenes"); d.canonicalScenes().forEach(v->scenes.add(writeScene(v)));
        n.put("startSceneId", d.runtimeSceneSet().startId()); n.set("rules", writeRules(d.runtimeRules()));
        ObjectNode content=n.putObject("localizedContent"); d.localizedContent().forEach(content::put);
        n.set("idBindings", JSON.valueToTree(d.idBindings())); n.set("compilationEvidence", JSON.valueToTree(d.compilationEvidence()));
        return n;
    }

    public static MaterializedGameDefinition readDefinition(JsonNode n) {
        try {
            int version=n.required("definitionFormatVersion").asInt(); if(version!=1)throw new IllegalArgumentException();
            ProfileIdentity profile=readProfile(n.required("profileIdentity")); LocaleTag locale=new LocaleTag(n.required("locale").asString());
            List<MaterializedGameDefinition.ActorPlan> actors=records(n.required("actors"),MaterializedGameDefinition.ActorPlan.class);
            List<MaterializedGameDefinition.PhasePlan> phases=records(n.required("phases"),MaterializedGameDefinition.PhasePlan.class);
            List<MaterializedGameDefinition.ScenePlan> plans=records(n.required("scenePlans"),MaterializedGameDefinition.ScenePlan.class);
            List<Scene> scenes=new ArrayList<>(); n.required("canonicalScenes").forEach(v->scenes.add(readScene(v)));
            LinkedHashMap<String,Scene> sceneMap=new LinkedHashMap<>(); scenes.forEach(v->sceneMap.put(v.id(),v));
            LinkedHashMap<String,String> content=new LinkedHashMap<>(); n.required("localizedContent").properties().forEach(e->content.put(e.getKey(),e.getValue().asString()));
            return new MaterializedGameDefinition(version,n.required("compilerVersion").asString(),
                    MaterializedGameDefinition.Origin.valueOf(n.required("origin").asString()),profile,locale,
                    n.required("lengthPresetId").asString(),n.required("challengePresetId").asString(),
                    n.required("blueprintSchemaVersion").asInt(),n.required("generatedRulesContractVersion").asString(),
                    n.required("semanticValidatorVersion").asString(),n.required("outputReviewerVersion").asString(),
                    JSON.treeToValue(n.required("storyPlan"),MaterializedGameDefinition.StoryPlan.class),actors,phases,plans,scenes,
                    new SceneSet(locale,sceneMap,n.required("startSceneId").asString()),readRules(n.required("rules")),content,
                    JSON.treeToValue(n.required("idBindings"),MaterializedGameDefinition.IdBindings.class),
                    JSON.treeToValue(n.required("compilationEvidence"),MaterializedGameDefinition.CompilationEvidence.class));
        } catch (RuntimeException ex) { throw new IllegalArgumentException("DYNAMIC_DEFINITION_INVALID"); }
    }

    private static <T> List<T> records(JsonNode node,Class<T> type){List<T> out=new ArrayList<>();node.forEach(v->out.add(JSON.treeToValue(v,type)));return out;}
    private static ObjectNode writeProfile(ProfileIdentity p){ObjectNode n=JSON.createObjectNode();n.put("id",p.id().value());n.put("version",p.version().toString());n.put("fingerprint",p.fingerprint());return n;}
    private static ProfileIdentity readProfile(JsonNode n){return new ProfileIdentity(new PackId(n.required("id").asString()),PackVersion.tryParse(n.required("version").asString()).orElseThrow(),n.required("fingerprint").asString());}

    public static ObjectNode writeContinuation(DynamicPresentationContinuation c){
        ObjectNode n=JSON.createObjectNode();n.put("continuationFormatVersion",c.continuationFormatVersion());n.set("profileIdentity",writeProfile(c.profileIdentity()));
        n.put("locale",c.locale().value());n.put("policyVersion",c.policyVersion());n.set("constraints",writeConstraints(c.constraints()));
        n.put("sceneMaxAttempts",c.sceneMaxAttempts());n.put("qualityResource",c.qualityResource());n.put("blueprintProviderCalls",c.blueprintProviderCalls());return n;
    }
    public static DynamicPresentationContinuation readContinuation(JsonNode n){try{return new DynamicPresentationContinuation(n.required("continuationFormatVersion").asInt(),readProfile(n.required("profileIdentity")),new LocaleTag(n.required("locale").asString()),n.required("policyVersion").asString(),readConstraints(n.required("constraints")),n.required("sceneMaxAttempts").asInt(),n.required("qualityResource").asString(),n.required("blueprintProviderCalls").asInt());}catch(RuntimeException ex){throw new IllegalArgumentException("DYNAMIC_CONTINUATION_INVALID");}}
    private static ObjectNode writeConstraints(EffectiveGenerationConstraints c){ObjectNode n=JSON.createObjectNode();n.put("minStoryTransitions",c.minStoryTransitions());n.put("maxStoryTransitions",c.maxStoryTransitions());n.put("minScenes",c.minScenes());n.put("maxScenes",c.maxScenes());n.put("maxCounters",c.maxCounters());n.put("maxFlags",c.maxFlags());n.put("maxSets",c.maxSets());n.put("maxConditionDepth",c.maxConditionDepth());n.put("maxEffectsPerChoice",c.maxEffectsPerChoice());ArrayNode conditions=n.putArray("allowedConditions");c.allowedConditions().stream().sorted().forEach(conditions::add);ArrayNode effects=n.putArray("allowedEffects");c.allowedEffects().stream().sorted().forEach(effects::add);n.put("totalCallsPerRun",c.totalCallsPerRun());n.put("blueprintMaxOutputTokens",c.blueprintMaxOutputTokens());n.put("sceneMaxOutputTokens",c.sceneMaxOutputTokens());n.put("maxInputCharacters",c.maxInputCharacters());return n;}
    private static EffectiveGenerationConstraints readConstraints(JsonNode n){return new EffectiveGenerationConstraints(n.required("minStoryTransitions").asInt(),n.required("maxStoryTransitions").asInt(),n.required("minScenes").asInt(),n.required("maxScenes").asInt(),n.required("maxCounters").asInt(),n.required("maxFlags").asInt(),n.required("maxSets").asInt(),n.required("maxConditionDepth").asInt(),n.required("maxEffectsPerChoice").asInt(),stringSet(n.required("allowedConditions")),stringSet(n.required("allowedEffects")),n.required("totalCallsPerRun").asInt(),n.required("blueprintMaxOutputTokens").asInt(),n.required("sceneMaxOutputTokens").asInt(),n.required("maxInputCharacters").asInt());}
    private static Set<String> stringSet(JsonNode n){LinkedHashSet<String> values=new LinkedHashSet<>();n.forEach(v->{if(!v.isString()||!values.add(v.asString()))throw new IllegalArgumentException();});return values;}

    private static ObjectNode writeScene(Scene s){ObjectNode n=JSON.createObjectNode();n.put("id",s.id());n.put("start",s.start());ArrayNode blocks=n.putArray("blocks");s.blocks().forEach(v->blocks.add(JSON.valueToTree(v)));ArrayNode options=n.putArray("options");for(SceneOption o:s.options()){ObjectNode x=options.addObject();x.put("id",o.id());x.put("label",o.label());x.put("next",o.next());x.set("effects",writeEffects(o.effects()));x.set("requires",writeCondition(o.requires()));if(o.unmetHint()==null)x.putNull("unmetHint");else x.put("unmetHint",o.unmetHint());x.put("hideWhenUnmet",o.hideWhenUnmet());}if(s.next()==null)n.putNull("next");else n.put("next",s.next());n.put("end",s.end());n.set("onEnter",writeEffects(s.onEnter()));ArrayNode branches=n.putArray("branches");for(SceneBranch b:s.branches()){ObjectNode x=branches.addObject();x.set("condition",writeCondition(b.condition()));x.put("next",b.next());}if(s.outcome()==null)n.putNull("outcome");else n.put("outcome",s.outcome());if(s.inputMode()==null)n.putNull("inputMode");else n.put("inputMode",s.inputMode().name());n.put("maxChars",s.maxChars());return n;}
    private static Scene readScene(JsonNode n){List<Block> blocks=records(n.required("blocks"),Block.class);List<SceneOption> options=new ArrayList<>();n.required("options").forEach(x->options.add(new SceneOption(x.required("id").asString(),x.required("label").asString(),x.required("next").asString(),readEffects(x.required("effects")),readCondition(x.get("requires")),nullable(x,"unmetHint"),x.required("hideWhenUnmet").asBoolean())));List<SceneBranch> branches=new ArrayList<>();n.required("branches").forEach(x->branches.add(new SceneBranch(readCondition(x.required("condition")),x.required("next").asString())));String mode=nullable(n,"inputMode");return new Scene(n.required("id").asString(),n.required("start").asBoolean(),blocks,options,nullable(n,"next"),n.required("end").asBoolean(),readEffects(n.required("onEnter")),branches,nullable(n,"outcome"),mode==null?null:SceneInputMode.valueOf(mode),n.required("maxChars").asInt());}

    private static ObjectNode writeRules(Rules r){ObjectNode n=JSON.createObjectNode();n.set("counters",JSON.valueToTree(r.counters()));n.set("flags",JSON.valueToTree(r.flags()));n.set("sets",JSON.valueToTree(r.sets()));ArrayNode objectives=n.putArray("objectives");for(ObjectiveDef o:r.objectives()){ObjectNode x=objectives.addObject();x.put("id",o.id());x.put("labelKey",o.labelKey());x.set("completeWhen",writeCondition(o.completeWhen()));x.set("onComplete",writeEffects(o.onComplete()));}n.set("outcomes",JSON.valueToTree(r.outcomes()));ArrayNode facts=n.putArray("resultFacts");for(ResultFactDef f:r.resultFacts()){ObjectNode x=facts.addObject();x.put("id",f.id());x.put("labelKey",f.labelKey());x.set("when",writeCondition(f.when()));}return n;}
    private static Rules readRules(JsonNode n){List<CounterDef> counters=records(n.required("counters"),CounterDef.class);List<FlagDef> flags=records(n.required("flags"),FlagDef.class);List<SetDef> sets=records(n.required("sets"),SetDef.class);List<ObjectiveDef> objectives=new ArrayList<>();n.required("objectives").forEach(x->objectives.add(new ObjectiveDef(x.required("id").asString(),x.required("labelKey").asString(),readCondition(x.required("completeWhen")),readEffects(x.required("onComplete")))));List<OutcomeDef> outcomes=records(n.required("outcomes"),OutcomeDef.class);List<ResultFactDef> facts=new ArrayList<>();n.required("resultFacts").forEach(x->facts.add(new ResultFactDef(x.required("id").asString(),x.required("labelKey").asString(),readCondition(x.required("when")))));return new Rules(counters,flags,sets,objectives,outcomes,facts);}

    private static JsonNode writeCondition(Condition c){if(c==null)return JSON.nullNode();ObjectNode n=JSON.createObjectNode();if(c instanceof Condition.CounterAtLeast v){n.put("type","COUNTER_AT_LEAST");n.put("id",v.counter());n.put("value",v.value());}else if(c instanceof Condition.CounterAtMost v){n.put("type","COUNTER_AT_MOST");n.put("id",v.counter());n.put("value",v.value());}else if(c instanceof Condition.CounterEquals v){n.put("type","COUNTER_EQUALS");n.put("id",v.counter());n.put("value",v.value());}else if(c instanceof Condition.FlagIs v){n.put("type","FLAG_IS");n.put("id",v.flag());n.put("boolean",v.value());}else if(c instanceof Condition.SetContains v){n.put("type","SET_CONTAINS");n.put("id",v.set());n.put("member",v.value());}else if(c instanceof Condition.SetSizeAtLeast v){n.put("type","SET_SIZE_AT_LEAST");n.put("id",v.set());n.put("value",v.size());}else if(c instanceof Condition.VisitedScene v){n.put("type","VISITED_SCENE");n.put("id",v.scene());}else if(c instanceof Condition.All v){n.put("type","ALL");ArrayNode a=n.putArray("conditions");v.conditions().forEach(x->a.add(writeCondition(x)));}else if(c instanceof Condition.Any v){n.put("type","ANY");ArrayNode a=n.putArray("conditions");v.conditions().forEach(x->a.add(writeCondition(x)));}else if(c instanceof Condition.Not v){n.put("type","NOT");n.set("condition",writeCondition(v.condition()));}else throw new IllegalArgumentException("Unsupported dynamic condition");return n;}
    private static Condition readCondition(JsonNode n){if(n==null||n.isNull())return null;String t=n.required("type").asString();return switch(t){case"COUNTER_AT_LEAST"->new Condition.CounterAtLeast(n.required("id").asString(),n.required("value").asInt());case"COUNTER_AT_MOST"->new Condition.CounterAtMost(n.required("id").asString(),n.required("value").asInt());case"COUNTER_EQUALS"->new Condition.CounterEquals(n.required("id").asString(),n.required("value").asInt());case"FLAG_IS"->new Condition.FlagIs(n.required("id").asString(),n.required("boolean").asBoolean());case"SET_CONTAINS"->new Condition.SetContains(n.required("id").asString(),n.required("member").asString());case"SET_SIZE_AT_LEAST"->new Condition.SetSizeAtLeast(n.required("id").asString(),n.required("value").asInt());case"VISITED_SCENE"->new Condition.VisitedScene(n.required("id").asString());case"ALL"->new Condition.All(readConditions(n.required("conditions")));case"ANY"->new Condition.Any(readConditions(n.required("conditions")));case"NOT"->new Condition.Not(readCondition(n.required("condition")));default->throw new IllegalArgumentException("Unknown condition");};}
    private static List<Condition> readConditions(JsonNode n){List<Condition> out=new ArrayList<>();n.forEach(v->out.add(readCondition(v)));return out;}
    private static ArrayNode writeEffects(List<Effect> values){ArrayNode a=JSON.createArrayNode();for(Effect e:values){ObjectNode n=a.addObject();if(e instanceof Effect.CounterAdd v){n.put("type","COUNTER_ADD");n.put("id",v.counter());n.put("value",v.delta());}else if(e instanceof Effect.CounterSet v){n.put("type","COUNTER_SET");n.put("id",v.counter());n.put("value",v.value());}else if(e instanceof Effect.FlagSet v){n.put("type","FLAG_SET");n.put("id",v.flag());n.put("boolean",v.value());}else if(e instanceof Effect.SetAdd v){n.put("type","SET_ADD");n.put("id",v.set());n.put("member",v.value());}else if(e instanceof Effect.SetRemove v){n.put("type","SET_REMOVE");n.put("id",v.set());n.put("member",v.value());}else throw new IllegalArgumentException("Unsupported dynamic effect");}return a;}
    private static List<Effect> readEffects(JsonNode n){List<Effect> out=new ArrayList<>();n.forEach(x->{String t=x.required("type").asString();out.add(switch(t){case"COUNTER_ADD"->new Effect.CounterAdd(x.required("id").asString(),x.required("value").asInt());case"COUNTER_SET"->new Effect.CounterSet(x.required("id").asString(),x.required("value").asInt());case"FLAG_SET"->new Effect.FlagSet(x.required("id").asString(),x.required("boolean").asBoolean());case"SET_ADD"->new Effect.SetAdd(x.required("id").asString(),x.required("member").asString());case"SET_REMOVE"->new Effect.SetRemove(x.required("id").asString(),x.required("member").asString());default->throw new IllegalArgumentException("Unknown effect");});});return out;}

    public static ObjectNode writeArtifact(DynamicPresentationArtifact a){ObjectNode n=JSON.createObjectNode();n.put("artifactFormatVersion",a.artifactFormatVersion());n.put("artifactId",a.artifactId());n.put("sceneId",a.sceneId());n.put("locale",a.locale().value());n.put("policyVersion",a.policyVersion());n.put("outcome",a.outcome().name());n.set("payload",JSON.valueToTree(a.payload()));n.set("continuityBasisSceneIds",JSON.valueToTree(a.continuityBasisSceneIds()));ObjectNode e=n.putObject("evidence");e.put("providerCalls",a.evidence().providerCalls());if(a.evidence().failureCode()==null)e.putNull("failureCode");else e.put("failureCode",a.evidence().failureCode());if(a.evidence().usage()==null)e.putNull("usage");else e.set("usage",JSON.valueToTree(a.evidence().usage()));e.set("findingCodes",JSON.valueToTree(a.evidence().findingCodes()));return n;}
    public static DynamicPresentationArtifact readArtifact(JsonNode n){try{JsonNode e=n.required("evidence");TextGenerationResult.TokenUsage usage=e.required("usage").isNull()?null:JSON.treeToValue(e.required("usage"),TextGenerationResult.TokenUsage.class);List<String> basis=new ArrayList<>();n.required("continuityBasisSceneIds").forEach(v->basis.add(v.asString()));List<String> findings=new ArrayList<>();e.required("findingCodes").forEach(v->findings.add(v.asString()));return new DynamicPresentationArtifact(n.required("artifactFormatVersion").asInt(),n.required("artifactId").asString(),n.required("sceneId").asString(),new LocaleTag(n.required("locale").asString()),n.required("policyVersion").asString(),GenerationArtifact.Outcome.valueOf(n.required("outcome").asString()),JSON.treeToValue(n.required("payload"),GenerationArtifact.PlannedScenePayload.class),basis,new DynamicPresentationArtifact.Evidence(e.required("providerCalls").asInt(),nullable(e,"failureCode"),usage,findings));}catch(RuntimeException ex){throw new IllegalArgumentException("DYNAMIC_ARTIFACT_INVALID");}}
    private static String nullable(JsonNode n,String field){JsonNode v=n.get(field);return v==null||v.isNull()?null:v.asString();}
}
