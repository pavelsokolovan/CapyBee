package com.capybee.engine.generation.blueprint.json;

import com.capybee.engine.generation.profile.*;
import com.capybee.engine.rules.GeneratedMechanicsV1;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/** Provider-neutral schema publication; local parsing remains authoritative. */
public final class BlueprintSchemas {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public String baseSchema(){try(var in=getClass().getResourceAsStream("/generation/blueprint/v1.schema.json")){if(in==null)throw new IllegalStateException("Blueprint schema resource is missing");String schema=new String(in.readAllBytes(),StandardCharsets.UTF_8);verifyBaseContract(JSON.readTree(schema));return schema;}catch(IOException ex){throw new IllegalStateException("Blueprint schema resource cannot be read",ex);}}
    public String specializedSchema(ValidatedGeneratorProfile profile,ResolvedGamePreferences preferences,EffectiveGenerationConstraints limits){
        Objects.requireNonNull(profile);Objects.requireNonNull(preferences);Objects.requireNonNull(limits);
        if(!GenerationProfileTrust.validContext(profile,preferences,limits))throw new IllegalArgumentException("Profile context mismatch");
        var caps=profile.profile().capabilities();
        Map<String,Object> effective=new LinkedHashMap<>();effective.put("locale",preferences.gameLocale().value());effective.put("minScenes",limits.minScenes());effective.put("maxScenes",Math.min(24,limits.maxScenes()));effective.put("minActors",caps.minActors());effective.put("maxActors",Math.min(12,caps.maxActors()));effective.put("minObjectives",caps.minObjectives());effective.put("maxObjectives",Math.min(8,caps.maxObjectives()));effective.put("minOutcomes",Math.max(2,caps.minEndings()));effective.put("maxOutcomes",Math.min(4,caps.maxEndings()));effective.put("maxCounters",Math.min(4,limits.maxCounters()));effective.put("maxFlags",Math.min(12,limits.maxFlags()));effective.put("maxSets",Math.min(2,limits.maxSets()));effective.put("allowedConditions",limits.allowedConditions().stream().sorted().toList());effective.put("allowedEffects",limits.allowedEffects().stream().sorted().toList());effective.put("maxConditionDepth",Math.min(4,limits.maxConditionDepth()));effective.put("maxEffectsPerChoice",Math.min(6,limits.maxEffectsPerChoice()));
        ObjectNode result=(ObjectNode)JSON.readTree(baseSchema());
        result.set("x-capybee-effectiveConstraints",JSON.valueToTree(effective));
        ObjectNode properties=(ObjectNode)result.get("properties");
        ((ObjectNode)properties.get("locale")).put("const",preferences.gameLocale().value());
        narrowArray(properties,"scenes",limits.minScenes(),Math.min(24,limits.maxScenes()));
        narrowArray(properties,"actors",caps.minActors(),Math.min(12,caps.maxActors()));
        narrowArray(properties,"objectives",caps.minObjectives(),Math.min(8,caps.maxObjectives()));
        narrowArray(properties,"outcomes",Math.max(2,caps.minEndings()),Math.min(4,caps.maxEndings()));
        ObjectNode defs=(ObjectNode)result.get("$defs"); ObjectNode state=(ObjectNode)((ObjectNode)defs.get("state")).get("properties");
        narrowArray(state,"counters",0,Math.min(4,limits.maxCounters()));narrowArray(state,"flags",0,Math.min(12,limits.maxFlags()));narrowArray(state,"sets",0,Math.min(2,limits.maxSets()));
        ObjectNode choiceProperties=(ObjectNode)((ObjectNode)defs.get("choice")).get("properties");
        ObjectNode effects=(ObjectNode)choiceProperties.get("effects");
        effects.put("maxItems",Math.min(6,limits.maxEffectsPerChoice()));
        ObjectNode decisionProperties=(ObjectNode)((ObjectNode)((ArrayNode)((ObjectNode)defs.get("decision")).get("allOf")).get(1)).get("properties");
        ((ObjectNode)decisionProperties.get("choices")).put("maxItems",Math.min(4,caps.maxChoicesPerScene()));
        ObjectNode counterProperties=(ObjectNode)((ObjectNode)defs.get("counter")).get("properties");
        for(String field:List.of("min","max","initial")){((ObjectNode)counterProperties.get(field)).put("minimum",caps.counterMin());((ObjectNode)counterProperties.get(field)).put("maximum",caps.counterMax());}
        specializeConditions(defs,limits.allowedConditions());
        specializeEffects(defs,limits.allowedEffects());
        if(limits.allowedEffects().isEmpty()){ObjectNode impossible=JSON.createObjectNode();impossible.set("not",JSON.createObjectNode());effects.set("items",impossible);}
        return JSON.writeValueAsString(result);
    }
    private static void specializeConditions(ObjectNode defs,Set<String> allowed){
        if(!GeneratedMechanicsV1.conditionNames().containsAll(allowed))throw new IllegalArgumentException("Condition contract mismatch");
        ObjectNode condition=(ObjectNode)defs.get("condition");ArrayNode branches=(ArrayNode)condition.get("oneOf");ArrayNode kept=JSON.createArrayNode();
        for(JsonNode branch:branches){String ref=branch.get("$ref").asString();if(conditionBranchAllowed(ref,allowed))kept.add(branch);}
        condition.set("oneOf",kept);
        ObjectNode counterType=(ObjectNode)((ObjectNode)((ObjectNode)defs.get("counterCondition")).get("properties")).get("type");
        ArrayNode counterValues=JSON.createArrayNode();for(String value:GeneratedMechanicsV1.conditionNames())if(value.startsWith("COUNTER_")&&allowed.contains(value))counterValues.add(value);counterType.set("enum",counterValues);
        ObjectNode compositeType=(ObjectNode)((ObjectNode)((ObjectNode)defs.get("compositeCondition")).get("properties")).get("type");
        ArrayNode compositeValues=JSON.createArrayNode();for(String value:List.of("ALL","ANY"))if(allowed.contains(value))compositeValues.add(value);compositeType.set("enum",compositeValues);
    }
    private static boolean conditionBranchAllowed(String ref,Set<String> allowed){return switch(ref){case "#/$defs/counterCondition"->allowed.stream().anyMatch(v->v.startsWith("COUNTER_"));case "#/$defs/flagCondition"->allowed.contains("FLAG_IS");case "#/$defs/setContainsCondition"->allowed.contains("SET_CONTAINS");case "#/$defs/setSizeCondition"->allowed.contains("SET_SIZE_AT_LEAST");case "#/$defs/visitedCondition"->allowed.contains("VISITED_SCENE");case "#/$defs/compositeCondition"->allowed.contains("ALL")||allowed.contains("ANY");case "#/$defs/notCondition"->allowed.contains("NOT");default->false;};}
    private static void specializeEffects(ObjectNode defs,Set<String> allowed){if(!GeneratedMechanicsV1.effectNames().containsAll(allowed))throw new IllegalArgumentException("Effect contract mismatch");ObjectNode effect=(ObjectNode)defs.get("effect");ArrayNode branches=(ArrayNode)effect.get("oneOf");ArrayNode kept=JSON.createArrayNode();for(JsonNode branch:branches){ObjectNode type=(ObjectNode)((ObjectNode)branch.get("properties")).get("type");JsonNode constant=type.get("const");if(constant!=null&&allowed.contains(constant.asString()))kept.add(branch);else if(type.get("enum") instanceof ArrayNode values){ArrayNode enabled=JSON.createArrayNode();for(JsonNode value:values)if(allowed.contains(value.asString()))enabled.add(value);if(!enabled.isEmpty()){type.set("enum",enabled);kept.add(branch);}}}effect.set("oneOf",kept);}
    private static void narrowArray(ObjectNode parent,String name,int min,int max){ObjectNode array=(ObjectNode)parent.get(name);array.put("minItems",min);array.put("maxItems",max);}
    private static void verifyBaseContract(JsonNode root){JsonNode defs=root.get("$defs");Set<String> conditions=new LinkedHashSet<>();for(String name:List.of("counterCondition","flagCondition","setContainsCondition","setSizeCondition","visitedCondition","compositeCondition","notCondition"))conditions.addAll(typeValues(defs.get(name).get("properties").get("type")));Set<String> effects=new LinkedHashSet<>();for(JsonNode branch:defs.get("effect").get("oneOf"))effects.addAll(typeValues(branch.get("properties").get("type")));if(!conditions.equals(GeneratedMechanicsV1.conditionNames())||!effects.equals(GeneratedMechanicsV1.effectNames()))throw new IllegalStateException("Generated mechanics contract mismatch");}
    private static Set<String> typeValues(JsonNode type){Set<String> result=new LinkedHashSet<>();if(type.has("const"))result.add(type.get("const").asString());if(type.has("enum"))for(JsonNode value:type.get("enum"))result.add(value.asString());return result;}
}
