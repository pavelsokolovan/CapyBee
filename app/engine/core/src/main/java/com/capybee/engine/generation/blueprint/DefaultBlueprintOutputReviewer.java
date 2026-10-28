package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.ResolvedGamePreferences;
import com.capybee.engine.input.PiiScrubber;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Local production review of every narrative-bearing blueprint field. Findings never contain content. */
public final class DefaultBlueprintOutputReviewer implements BlueprintOutputReviewer {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)");
    private static final Pattern BYPASS = Pattern.compile("(?i)(ignore (?:all|previous)|system prompt|developer message|jailbreak|prompt injection)");
    private static final int MAX_FINDINGS = 20;

    @Override
    public Review review(ParsedGameBlueprint blueprint, ResolvedGamePreferences preferences, String qualityPolicyJson) {
        if (blueprint == null || preferences == null || qualityPolicyJson == null) return Review.unavailable("unknown");
        final JsonNode policy;
        try {
            policy = JSON.readTree(qualityPolicyJson);
        } catch (RuntimeException invalid) {
            return Review.unavailable("unknown");
        }
        String version = policy.path("policyVersion").asText("");
        int maximum = policy.path("maxNarrativeCodePoints").asInt(0);
        if (version.isBlank() || maximum < 1 || !policy.path("forbiddenPhrases").isArray())
            return Review.unavailable(version.isBlank() ? "unknown" : version);
        List<String> forbidden = new ArrayList<>();
        policy.path("forbiddenPhrases").forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) forbidden.add(value.asText().toLowerCase(Locale.ROOT)); });
        List<BlueprintIssue> issues = new ArrayList<>();
        Walker walker = new Walker(issues, forbidden, maximum);
        walker.text("/title", blueprint.title()); walker.text("/premise", blueprint.premise()); walker.text("/setting", blueprint.setting());
        for (int i=0;i<blueprint.toneTags().size();i++) walker.text("/toneTags/"+i, blueprint.toneTags().get(i));
        for (int i=0;i<blueprint.storyPromises().size();i++) walker.text("/storyPromises/"+i, blueprint.storyPromises().get(i));
        for (int i=0;i<blueprint.actors().size();i++) { var v=blueprint.actors().get(i); walker.text("/actors/"+i+"/name",v.name()); walker.text("/actors/"+i+"/role",v.role()); walker.text("/actors/"+i+"/description",v.description()); }
        for (int i=0;i<blueprint.objectives().size();i++) { var v=blueprint.objectives().get(i); walker.text("/objectives/"+i+"/title",v.title()); walker.text("/objectives/"+i+"/description",v.description()); }
        for (int i=0;i<blueprint.phases().size();i++) { var v=blueprint.phases().get(i); walker.text("/phases/"+i+"/goal",v.goal()); walker.text("/phases/"+i+"/development",v.development()); walker.text("/phases/"+i+"/transition",v.transition()); }
        for (int i=0;i<blueprint.scenes().size();i++) {
            var v=blueprint.scenes().get(i);
            if(v instanceof ParsedGameBlueprint.DecisionScene s){walker.text("/scenes/"+i+"/title",s.title());walker.text("/scenes/"+i+"/intent",s.intent());for(int j=0;j<s.choices().size();j++)walker.text("/scenes/"+i+"/choices/"+j+"/intent",s.choices().get(j).intent());}
            else if(v instanceof ParsedGameBlueprint.EndingScene s){walker.text("/scenes/"+i+"/title",s.title());walker.text("/scenes/"+i+"/intent",s.intent());}
        }
        for(int i=0;i<blueprint.outcomes().size();i++){var v=blueprint.outcomes().get(i);walker.text("/outcomes/"+i+"/title",v.title());walker.text("/outcomes/"+i+"/description",v.description());}
        return issues.isEmpty() ? Review.pass(version) : new Review(Status.REJECT, version, issues);
    }

    private static final class Walker {
        private final List<BlueprintIssue> issues; private final List<String> forbidden; private final int maximum; private final PiiScrubber pii = new PiiScrubber();
        private Walker(List<BlueprintIssue> issues,List<String> forbidden,int maximum){this.issues=issues;this.forbidden=forbidden;this.maximum=maximum;}
        void text(String pointer,String value){
            if(issues.size()>=MAX_FINDINGS||value==null)return;
            String code=null;
            if(value.codePointCount(0,value.length())>maximum)code="BLUEPRINT_TEXT_TOO_LONG";
            else if(value.codePoints().anyMatch(c->Character.isISOControl(c)))code="BLUEPRINT_CONTROL_CHARACTER";
            else if(URL.matcher(value).find())code="BLUEPRINT_UNSAFE_URL";
            else if(BYPASS.matcher(value).find())code="BLUEPRINT_POLICY_BYPASS";
            else if(pii.scrub(value).changed())code="BLUEPRINT_PRIVATE_DATA";
            else {String lower=value.toLowerCase(Locale.ROOT);if(forbidden.stream().anyMatch(lower::contains))code="BLUEPRINT_FORBIDDEN_CONTENT";}
            if(code!=null)issues.add(new BlueprintIssue(code,"blueprint",pointer,""));
        }
    }
}
