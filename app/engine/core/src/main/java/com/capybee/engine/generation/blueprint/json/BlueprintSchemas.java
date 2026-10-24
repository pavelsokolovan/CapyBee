package com.capybee.engine.generation.blueprint.json;

import com.capybee.engine.generation.profile.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

/** Provider-neutral schema publication; local parsing remains authoritative. */
public final class BlueprintSchemas {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public String baseSchema(){try(var in=getClass().getResourceAsStream("/generation/blueprint/v1.schema.json")){if(in==null)throw new IllegalStateException("Blueprint schema resource is missing");return new String(in.readAllBytes(),StandardCharsets.UTF_8);}catch(IOException ex){throw new IllegalStateException("Blueprint schema resource cannot be read",ex);}}
    public String specializedSchema(ValidatedGeneratorProfile profile,ResolvedGamePreferences preferences,EffectiveGenerationConstraints limits){
        Objects.requireNonNull(profile);Objects.requireNonNull(preferences);Objects.requireNonNull(limits);
        if(!profile.identity().equals(preferences.profileIdentity()))throw new IllegalArgumentException("Profile context mismatch");
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
        ((ObjectNode)((ObjectNode)defs.get("choice")).get("properties").get("effects")).put("maxItems",Math.min(6,limits.maxEffectsPerChoice()));
        return JSON.writeValueAsString(result);
    }
    private static void narrowArray(ObjectNode parent,String name,int min,int max){ObjectNode array=(ObjectNode)parent.get(name);array.put("minItems",min);array.put("maxItems",max);}
}
