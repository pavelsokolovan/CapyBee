package com.capybee.engine.generation.profile.json;

import com.capybee.engine.generation.GenerationSetup.SetupOption;
import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.generation.profile.*;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import com.capybee.engine.pack.StartInputDef;
import com.capybee.engine.rules.GeneratedMechanicsV1;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Strict, bounded loader for scenario-neutral generator profile bundles. */
public final class GeneratorProfileLoader {
    private static final int MAX_FILES = 64, MAX_TOTAL = 1_048_576, MAX_JSON = 65_536, MAX_PROMPT = 16_384;
    private static final Set<String> CONDITIONS = GeneratedMechanicsV1.conditionNames();
    private static final Set<String> EFFECTS = GeneratedMechanicsV1.effectNames();
    private static final Set<String> INPUT_IDS = Set.of("genre", "setting", "tone", "interests", "length", "challenge", "game-locale", "story-wish");
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public ProfileLoadResult load(Map<String, byte[]> supplied) {
        var c = new Context();
        if (supplied == null || supplied.size() > MAX_FILES + 1) {
            c.add("PROFILE_LIMIT_EXCEEDED", "profile.json", "", "Profile file count exceeds the supported limit");
            return c.result();
        }
        var resources = new LinkedHashMap<String, String>();
        int total = 0;
        for (var entry : supplied.entrySet()) {
            String path = entry.getKey(); byte[] bytes = entry.getValue();
            if (!validPath(path) || bytes == null) { c.add("PROFILE_RESOURCE_INVALID", safe(path), "", "Invalid resource path"); continue; }
            total += bytes.length;
            int limit = path.endsWith(".json") ? MAX_JSON : path.startsWith("prompts/") ? MAX_PROMPT : MAX_TOTAL;
            if (bytes.length > limit || total > MAX_TOTAL) { c.add("PROFILE_LIMIT_EXCEEDED", path, "", "Resource exceeds a supported limit"); continue; }
            try { resources.put(path, decode(bytes)); }
            catch (CharacterCodingException ex) { c.add("PROFILE_RESOURCE_INVALID", path, "", "Resource is not valid UTF-8"); }
        }
        JsonNode root = parse(resources.get("profile.json"), "profile.json", c);
        if (root == null || !root.isObject()) return c.result();
        fields(root, c, "profile.json", Set.of("schemaVersion", "id", "version", "titleKey", "descriptionKey",
                "supportedLocales", "defaultLocale", "setupResource", "stringResources", "blueprintPromptResources",
                "qualityResources", "policy", "capabilities", "lengthPresets", "challengePresets", "generationBudget",
                "fallbacks", "files"));
        if (integer(root, "schemaVersion") != 1) c.add("PROFILE_SCHEMA_UNSUPPORTED", "profile.json", "/schemaVersion", "Unsupported profile schema version");
        String id = text(root, "id"), versionText = text(root, "version");
        if (id == null || id.length() > 64 || !id.matches("[a-z][a-z0-9-]{0,63}")) c.add("PROFILE_RESOURCE_INVALID", "profile.json", "/id", "Invalid profile id");
        var version = PackVersion.tryParse(versionText).orElse(null);
        if (version == null) c.add("PROFILE_RESOURCE_INVALID", "profile.json", "/version", "Invalid profile version");
        List<String> fileList = strings(root.get("files"), 64, c, "profile.json", "/files");
        if (new HashSet<>(fileList).size() != fileList.size()) c.add("PROFILE_RESOURCE_INVALID", "profile.json", "/files", "Duplicate resource path");
        for (String path : fileList) if (!validPath(path) || path.equals("profile.json")) c.add("PROFILE_RESOURCE_INVALID", "profile.json", "/files", "Invalid indexed path");
        Set<String> actual = new LinkedHashSet<>(resources.keySet()); actual.remove("profile.json");
        for (String path : fileList) if (!actual.contains(path)) c.add("PROFILE_RESOURCE_MISSING", path, "", "Indexed resource is missing");
        for (String path : actual) if (!fileList.contains(path)) c.add("PROFILE_RESOURCE_INVALID", path, "", "Resource is not indexed");

        List<LocaleTag> locales = new ArrayList<>();
        for (String value : strings(root.get("supportedLocales"), 8, c, "profile.json", "/supportedLocales")) {
            if (!LocaleTag.isValid(value)) c.add("PROFILE_LOCALE_INVALID", "profile.json", "/supportedLocales", "Invalid locale");
            else locales.add(new LocaleTag(value));
        }
        if (locales.isEmpty() || new HashSet<>(locales).size() != locales.size()) c.add("PROFILE_LOCALE_INVALID", "profile.json", "/supportedLocales", "Locales must be unique and nonempty");
        String defaultText = text(root, "defaultLocale");
        LocaleTag defaultLocale = LocaleTag.isValid(defaultText) ? new LocaleTag(defaultText) : null;
        if (defaultLocale == null || !locales.contains(defaultLocale)) c.add("PROFILE_LOCALE_INVALID", "profile.json", "/defaultLocale", "Default locale must be supported");

        String setupPath = reference(root, "setupResource", fileList, c);
        Map<LocaleTag,String> stringPaths = localePaths(root.get("stringResources"), locales, fileList, c, "/stringResources");
        Map<LocaleTag,String> promptPaths = localePaths(root.get("blueprintPromptResources"), locales, fileList, c, "/blueprintPromptResources");
        Map<LocaleTag,String> qualityPaths = localePaths(root.get("qualityResources"), locales, fileList, c, "/qualityResources");
        var policy = policy(root.get("policy"), c);
        var caps = capabilities(root.get("capabilities"), c);
        var lengths = lengths(root.get("lengthPresets"), caps, c);
        var challenges = challenges(root.get("challengePresets"), caps, c);
        var budget = budget(root.get("generationBudget"), lengths, c);
        var setup = setup(parse(resources.get(setupPath), setupPath, c), policy, locales, lengths, challenges, c, setupPath);
        var strings = localizedStrings(stringPaths, resources, setup, root, lengths, challenges, c);
        Map<LocaleTag,String> prompts = resourceTexts(promptPaths, resources, c);
        Map<LocaleTag,String> quality = qualityTexts(qualityPaths, resources, c);
        var fallbacks = fallbacks(root.get("fallbacks"), locales, lengths, challenges, c);
        if (!c.issues.isEmpty() || id == null || version == null || defaultLocale == null || policy == null || caps == null
                || budget == null || setup == null) return c.result();
        String fingerprint = fingerprint(fileList, resources, c);
        if (!c.issues.isEmpty()) return c.result();
        var profile = new GeneratorProfile(1, new PackId(id), version, text(root,"titleKey"), text(root,"descriptionKey"),
                locales, defaultLocale, policy, caps, lengths, challenges, budget, setup, strings, prompts, quality, fallbacks, fileList);
        return new ProfileLoadResult(Optional.of(ProfileValidationFactory.validated(profile,
                new ProfileIdentity(profile.id(), version, fingerprint))), List.of());
    }

    private static GeneratorProfile.ProfilePolicy policy(JsonNode n, Context c) {
        if (!object(n,c,"/policy")) return null;
        fields(n,c,"profile.json",Set.of("audience","freeTextAllowed","maxSuggestionCodePoints","forbiddenTags"));
        JsonNode a=n.get("audience"); if(!object(a,c,"/policy/audience")) return null;
        fields(a,c,"profile.json",Set.of("minAge","maxAge"));
        int min=integer(a,"minAge"),max=integer(a,"maxAge"),suggestion=integer(n,"maxSuggestionCodePoints");
        boolean allowed=bool(n,"freeTextAllowed");
        if(min<0||max<min||max>17||(allowed&&(suggestion<1||suggestion>300))||(!allowed&&suggestion!=0)) c.add("PROFILE_CAPABILITY_INCONSISTENT","profile.json","/policy","Invalid audience or suggestion policy");
        return new GeneratorProfile.ProfilePolicy(min,max,allowed,suggestion,new LinkedHashSet<>(strings(n.get("forbiddenTags"),32,c,"profile.json","/policy/forbiddenTags")));
    }

    private static ProfileCapabilities capabilities(JsonNode n, Context c) {
        if(!object(n,c,"/capabilities")) return null;
        fields(n,c,"profile.json",Set.of("phaseKinds","graphKind","placesEnabled","minScenes","maxScenes","maxChoicesPerScene","minActors","maxActors","minObjectives","maxObjectives","minEndings","maxEndings","maxCounters","maxFlags","maxSets","maxSetValues","counterMin","counterMax","maxCounterDelta","maxConditionDepth","maxConditionNodes","maxEffectsPerChoice","maxExploredStates","allowedConditions","allowedEffects"));
        List<String> phases=strings(n.get("phaseKinds"),5,c,"profile.json","/capabilities/phaseKinds");
        if(!phases.equals(Arrays.stream(PhaseKind.values()).map(Enum::name).toList())) c.add("PROFILE_CAPABILITY_UNSUPPORTED","profile.json","/capabilities/phaseKinds","All phases are required in order");
        String graph=text(n,"graphKind"); boolean places=bool(n,"placesEnabled");
        if(!"FORWARD_DAG".equals(graph)||places)c.add("PROFILE_CAPABILITY_UNSUPPORTED","profile.json","/capabilities","Unsupported graph or places capability");
        int minScenes=i(n,"minScenes"),maxScenes=i(n,"maxScenes"),choices=i(n,"maxChoicesPerScene"),minActors=i(n,"minActors"),maxActors=i(n,"maxActors"),minObj=i(n,"minObjectives"),maxObj=i(n,"maxObjectives"),minEnd=i(n,"minEndings"),maxEnd=i(n,"maxEndings"),counters=i(n,"maxCounters"),flags=i(n,"maxFlags"),sets=i(n,"maxSets"),setValues=i(n,"maxSetValues"),counterMin=i(n,"counterMin"),counterMax=i(n,"counterMax"),delta=i(n,"maxCounterDelta"),depth=i(n,"maxConditionDepth"),nodes=i(n,"maxConditionNodes"),effects=i(n,"maxEffectsPerChoice"),states=i(n,"maxExploredStates");
        if(minScenes<6||minScenes>maxScenes||maxScenes>24||choices<2||choices>4||minActors<1||minActors>maxActors||maxActors>12||minObj<1||minObj>maxObj||maxObj>8||minEnd<2||minEnd>maxEnd||maxEnd>4||counters<0||counters>4||flags<0||flags>12||sets<0||sets>2||setValues<1||setValues>8||counterMin<0||counterMin>=counterMax||counterMax>20||delta<1||delta>5||delta>counterMax-counterMin||depth<1||depth>4||nodes<1||nodes>32||effects<0||effects>6||states<1||states>100000)c.add("PROFILE_CAPABILITY_INCONSISTENT","profile.json","/capabilities","Capability bounds are inconsistent");
        Set<String> conditions=new LinkedHashSet<>(strings(n.get("allowedConditions"),CONDITIONS.size(),c,"profile.json","/capabilities/allowedConditions"));
        Set<String> allowedEffects=new LinkedHashSet<>(strings(n.get("allowedEffects"),EFFECTS.size(),c,"profile.json","/capabilities/allowedEffects"));
        if(!CONDITIONS.containsAll(conditions)||!EFFECTS.containsAll(allowedEffects))c.add("PROFILE_CAPABILITY_UNSUPPORTED","profile.json","/capabilities","Unknown mechanic discriminator");
        coherent(counters,flags,sets,conditions,allowedEffects,c,"/capabilities");
        return new ProfileCapabilities(Arrays.asList(PhaseKind.values()),graph,places,minScenes,maxScenes,choices,minActors,maxActors,minObj,maxObj,minEnd,maxEnd,counters,flags,sets,setValues,counterMin,counterMax,delta,depth,nodes,effects,states,conditions,allowedEffects);
    }

    private static List<LengthPreset> lengths(JsonNode n, ProfileCapabilities caps, Context c) {
        var out=new ArrayList<LengthPreset>(); if(!array(n,1,4,c,"/lengthPresets"))return out;
        Set<String> ids=new HashSet<>(); for(int x=0;x<n.size();x++){JsonNode v=n.get(x); if(!object(v,c,"/lengthPresets/"+x))continue; fields(v,c,"profile.json",Set.of("id","labelKey","minStoryTransitions","maxStoryTransitions","maxScenes")); String id=text(v,"id"); int min=i(v,"minStoryTransitions"),max=i(v,"maxStoryTransitions"),scenes=i(v,"maxScenes"); if(!validId(id)||!ids.add(id)||min<4||min>max||max>16||caps==null||scenes<caps.minScenes()||scenes>caps.maxScenes()||scenes<max+caps.minEndings())c.add("PROFILE_PRESET_INVALID","profile.json","/lengthPresets/"+x,"Invalid length preset"); out.add(new LengthPreset(id,text(v,"labelKey"),min,max,scenes));} return out;
    }
    private static List<ChallengePreset> challenges(JsonNode n, ProfileCapabilities caps, Context c) {
        var out=new ArrayList<ChallengePreset>(); if(!array(n,1,4,c,"/challengePresets"))return out; Set<String> ids=new HashSet<>(),signatures=new HashSet<>();
        for(int x=0;x<n.size();x++){JsonNode v=n.get(x);if(!object(v,c,"/challengePresets/"+x))continue;fields(v,c,"profile.json",Set.of("id","labelKey","maxCounters","maxFlags","maxSets","maxConditionDepth","maxEffectsPerChoice","allowedConditions","allowedEffects"));String id=text(v,"id");int counters=i(v,"maxCounters"),flags=i(v,"maxFlags"),sets=i(v,"maxSets"),depth=i(v,"maxConditionDepth"),effects=i(v,"maxEffectsPerChoice");Set<String> conditions=new LinkedHashSet<>(strings(v.get("allowedConditions"),16,c,"profile.json","/challengePresets/"+x+"/allowedConditions")),allowedEffects=new LinkedHashSet<>(strings(v.get("allowedEffects"),16,c,"profile.json","/challengePresets/"+x+"/allowedEffects"));String sig=counters+":"+flags+":"+sets+":"+depth+":"+effects+":"+conditions+":"+allowedEffects;if(!validId(id)||!ids.add(id)||caps==null||counters<0||counters>caps.maxCounters()||flags<0||flags>caps.maxFlags()||sets<0||sets>caps.maxSets()||depth<1||depth>caps.maxConditionDepth()||effects<0||effects>caps.maxEffectsPerChoice()||!caps.allowedConditions().containsAll(conditions)||!caps.allowedEffects().containsAll(allowedEffects)||!signatures.add(sig))c.add("PROFILE_PRESET_INVALID","profile.json","/challengePresets/"+x,"Invalid or duplicate challenge preset");coherent(counters,flags,sets,conditions,allowedEffects,c,"/challengePresets/"+x);out.add(new ChallengePreset(id,text(v,"labelKey"),counters,flags,sets,depth,effects,conditions,allowedEffects));}return out;
    }
    private static ProfileGenerationBudget budget(JsonNode n,List<LengthPreset> lengths,Context c){if(!object(n,c,"/generationBudget"))return null;fields(n,c,"profile.json",Set.of("totalCallsPerRun","blueprintMaxAttempts","blueprintMaxOutputTokens","sceneMaxAttempts","sceneMaxOutputTokens","maxInputCharacters"));int total=i(n,"totalCallsPerRun"),ba=i(n,"blueprintMaxAttempts"),bt=i(n,"blueprintMaxOutputTokens"),sa=i(n,"sceneMaxAttempts"),st=i(n,"sceneMaxOutputTokens"),chars=i(n,"maxInputCharacters");if(total<1||total>64||ba<1||ba>2||bt<512||bt>12000||sa<1||sa>2||st<128||st>1500||chars<1024||chars>32000||lengths.stream().anyMatch(l->total<l.maxStoryTransitions()+2))c.add("PROFILE_BUDGET_INSUFFICIENT","profile.json","/generationBudget","Generation budget is invalid or insufficient");return new ProfileGenerationBudget(total,ba,bt,sa,st,chars);}

    private static ProfileSetup setup(JsonNode root,GeneratorProfile.ProfilePolicy policy,List<LocaleTag> locales,List<LengthPreset> lengths,List<ChallengePreset> challenges,Context c,String file){if(root==null||!root.isObject())return null;fields(root,c,file,Set.of("schemaVersion","steps","inputs","questions"));if(integer(root,"schemaVersion")!=1)c.add("PROFILE_SETUP_INVALID",file,"/schemaVersion","Unsupported setup version");JsonNode ins=root.get("inputs");if(!array(ins,1,8,c,"/inputs"))return null;var inputs=new ArrayList<StartInputDef>();Set<String> ids=new HashSet<>();for(int x=0;x<ins.size();x++){JsonNode n=ins.get(x);fields(n,c,file,Set.of("id","kind","required","labelKey","values","maxItems","maxChars"));String id=text(n,"id"),kind=text(n,"kind"),label=text(n,"labelKey");boolean req=bool(n,"required");List<String> vals=n.has("values")?strings(n.get("values"),16,c,file,"/inputs/"+x+"/values"):List.of();if(!INPUT_IDS.contains(id)||!ids.add(id)||label==null)c.add("PROFILE_SETUP_INVALID",file,"/inputs/"+x,"Invalid setup input");if("ENUM".equals(kind))inputs.add(new StartInputDef.EnumInput(id,req,label,vals));else if("ENUM_SET".equals(kind)){int max=i(n,"maxItems");if(max<1||max>4||max>vals.size())c.add("PROFILE_SETUP_INVALID",file,"/inputs/"+x,"Invalid set input");inputs.add(new StartInputDef.EnumSetInput(id,req,label,vals,max));}else if("FREE_TEXT".equals(kind)){int max=i(n,"maxChars");if(!"story-wish".equals(id)||req||policy==null||!policy.freeTextAllowed()||max!=policy.maxSuggestionCodePoints())c.add("PROFILE_SETUP_INVALID",file,"/inputs/"+x,"Invalid transient input");inputs.add(new StartInputDef.FreeTextInput(id,req,label,max));}else c.add("PROFILE_SETUP_INVALID",file,"/inputs/"+x,"Unknown input kind");}
        requireInput(inputs,"length",lengths.stream().map(LengthPreset::id).toList(),c,file);requireInput(inputs,"challenge",challenges.stream().map(ChallengePreset::id).toList(),c,file);requireInput(inputs,"game-locale",locales.stream().map(LocaleTag::value).toList(),c,file);
        JsonNode stepsNode=root.get("steps");var steps=new ArrayList<ProfileSetup.SetupStep>();List<String> flattened=new ArrayList<>();if(array(stepsNode,1,6,c,"/steps")){Set<String>sids=new HashSet<>();for(int x=0;x<stepsNode.size();x++){JsonNode n=stepsNode.get(x);fields(n,c,file,Set.of("id","titleKey","inputIds"));List<String> refs=strings(n.get("inputIds"),8,c,file,"/steps/"+x+"/inputIds");if(refs.isEmpty()||!sids.add(text(n,"id")))c.add("PROFILE_SETUP_INVALID",file,"/steps/"+x,"Invalid setup step");flattened.addAll(refs);steps.add(new ProfileSetup.SetupStep(text(n,"id"),text(n,"titleKey"),refs));}}
        if(!flattened.equals(inputs.stream().map(StartInputDef::id).toList())||new HashSet<>(flattened).size()!=flattened.size())c.add("PROFILE_SETUP_INVALID",file,"/steps","Steps must cover inputs once in order");
        JsonNode qs=root.get("questions");var questions=new ArrayList<ProfileSetup.SetupQuestion>();if(!array(qs,inputs.size(),inputs.size(),c,"/questions"))return null;for(int x=0;x<qs.size();x++){JsonNode n=qs.get(x);fields(n,c,file,Set.of("inputId","helpKey","defaultValues","options","freeTextNoticeKey"));StartInputDef input=x<inputs.size()?inputs.get(x):null;List<String> defs=strings(n.get("defaultValues"),4,c,file,"/questions/"+x+"/defaultValues");var options=new ArrayList<SetupOption>();JsonNode os=n.get("options");if(os!=null&&os.isArray())for(int y=0;y<os.size();y++){JsonNode o=os.get(y);fields(o,c,file,Set.of("value","labelKey"));options.add(new SetupOption(text(o,"value"),text(o,"labelKey")));}String notice=text(n,"freeTextNoticeKey");if(input==null||!input.id().equals(text(n,"inputId")))c.add("PROFILE_SETUP_INVALID",file,"/questions/"+x,"Question order mismatch");else if(input instanceof StartInputDef.FreeTextInput){if(!defs.isEmpty()||!options.isEmpty()||notice==null)c.add("PROFILE_SETUP_INVALID",file,"/questions/"+x,"Invalid free text question");}else{List<String> declared=input instanceof StartInputDef.EnumInput e?e.values():((StartInputDef.EnumSetInput)input).values();if(!declared.equals(options.stream().map(SetupOption::value).toList())||!declared.containsAll(defs)||new HashSet<>(defs).size()!=defs.size()||(input.required()&&defs.size()!=1)||(input instanceof StartInputDef.EnumInput&&defs.size()>1))c.add("PROFILE_SETUP_INVALID",file,"/questions/"+x,"Options or defaults mismatch");if(notice!=null)c.add("PROFILE_SETUP_INVALID",file,"/questions/"+x,"Notice only allowed for free text");}questions.add(new ProfileSetup.SetupQuestion(text(n,"inputId"),text(n,"helpKey"),defs,options,notice));}return new ProfileSetup(1,steps,inputs,questions);}

    private static Map<LocaleTag,Map<String,String>> localizedStrings(Map<LocaleTag,String> paths,Map<String,String> resources,ProfileSetup setup,JsonNode profile,List<LengthPreset> lengths,List<ChallengePreset> challenges,Context c){Set<String> keys=new LinkedHashSet<>();keys.add(text(profile,"titleKey"));keys.add(text(profile,"descriptionKey"));if(setup!=null){setup.steps().forEach(s->keys.add(s.titleKey()));setup.inputs().forEach(i->keys.add(i.labelKey()));setup.questions().forEach(q->{keys.add(q.helpKey());if(q.freeTextNoticeKey()!=null)keys.add(q.freeTextNoticeKey());q.options().forEach(o->keys.add(o.labelKey()));});}lengths.forEach(l->keys.add(l.labelKey()));challenges.forEach(v->keys.add(v.labelKey()));var out=new LinkedHashMap<LocaleTag,Map<String,String>>();for(var e:paths.entrySet()){JsonNode root=parse(resources.get(e.getValue()),e.getValue(),c);var values=new LinkedHashMap<String,String>();if(root!=null&&root.isObject())root.properties().forEach(p->{if(p.getValue().isString())values.put(p.getKey(),p.getValue().asString());});for(String key:keys){String value=values.get(key);if(key==null||value==null||value.isBlank()||value.codePointCount(0,value.length())>600||value.contains("<")||value.matches(".*\\{[^}]+}.*"))c.add("PROFILE_STRING_MISSING",e.getValue(),"/"+safe(key),"Localized string is missing or unsafe");}out.put(e.getKey(),Map.copyOf(values));}return Map.copyOf(out);}
    private static List<GeneratorProfile.FallbackRef> fallbacks(JsonNode n,List<LocaleTag> locales,List<LengthPreset> lengths,List<ChallengePreset> challenges,Context c){var out=new ArrayList<GeneratorProfile.FallbackRef>();if(n==null)return out;if(!n.isArray()){c.add("PROFILE_FALLBACK_INVALID","profile.json","/fallbacks","Expected fallback array");return out;}Set<String> tuples=new HashSet<>();for(int x=0;x<n.size();x++){JsonNode v=n.get(x);fields(v,c,"profile.json",Set.of("packId","packVersion","locale","lengthPresetId","challengePresetId"));String pid=text(v,"packId"),pv=text(v,"packVersion"),loc=text(v,"locale"),len=text(v,"lengthPresetId"),challenge=text(v,"challengePresetId");PackVersion ver=PackVersion.tryParse(pv).orElse(null);String tuple=loc+":"+len+":"+challenge;if(!PackId.isValid(pid)||ver==null||locales.stream().noneMatch(l->l.value().equals(loc))||lengths.stream().noneMatch(l->l.id().equals(len))||challenges.stream().noneMatch(p->p.id().equals(challenge))||!tuples.add(tuple))c.add("PROFILE_FALLBACK_INVALID","profile.json","/fallbacks/"+x,"Invalid fallback reference");else out.add(new GeneratorProfile.FallbackRef(new PackId(pid),ver,new LocaleTag(loc),len,challenge));}return out;}

    private static String fingerprint(List<String> files,Map<String,String> resources,Context c){try{MessageDigest d=MessageDigest.getInstance("SHA-256");writePart(d,"generator-profile-v1");List<String> paths=new ArrayList<>();paths.add("profile.json");paths.addAll(files);paths.sort(String::compareTo);for(String path:paths){String value=resources.get(path);if(value==null)continue;writePart(d,path);if(path.endsWith(".json")){JsonNode node=parse(value,path,c);writePart(d,node==null?"":canonical(node));}else writePart(d,value.replace("\r\n","\n").replace('\r','\n'));}return HexFormat.of().formatHex(d.digest());}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
    private static void writePart(MessageDigest d,String value){byte[] b=value.getBytes(StandardCharsets.UTF_8);d.update(ByteBuffer.allocate(4).putInt(b.length).array());d.update(b);}
    private static String canonical(JsonNode n){if(n.isObject()){var names=new ArrayList<String>();names.addAll(n.propertyNames());names.sort(String::compareTo);StringJoiner j=new StringJoiner(",","{","}");for(String name:names)j.add(quote(name)+":"+canonical(n.get(name)));return j.toString();}if(n.isArray()){StringJoiner j=new StringJoiner(",","[","]");for(JsonNode child:n)j.add(canonical(child));return j.toString();}return n.toString();}
    private static String quote(String s){try{return JSON.writeValueAsString(s);}catch(RuntimeException e){throw new IllegalStateException(e);}}

    private static Map<LocaleTag,String> localePaths(JsonNode n,List<LocaleTag> locales,List<String> files,Context c,String pointer){var out=new LinkedHashMap<LocaleTag,String>();if(n==null||!n.isObject()){c.add("PROFILE_RESOURCE_MISSING","profile.json",pointer,"Locale resource map is missing");return out;}for(LocaleTag locale:locales){String path=text(n,locale.value());if(path==null||!files.contains(path))c.add("PROFILE_RESOURCE_MISSING","profile.json",pointer+"/"+locale.value(),"Locale resource is missing");else out.put(locale,path);}if(n.size()!=locales.size())c.add("PROFILE_RESOURCE_INVALID","profile.json",pointer,"Locale resource keys must exactly match supported locales");return out;}
    private static Map<LocaleTag,String> resourceTexts(Map<LocaleTag,String> paths,Map<String,String> resources,Context c){var out=new LinkedHashMap<LocaleTag,String>();paths.forEach((k,v)->{String text=resources.get(v);if(text==null||text.isBlank())c.add("PROFILE_RESOURCE_MISSING",v,"","Resource is missing or blank");else out.put(k,text);});return Map.copyOf(out);}
    private static Map<LocaleTag,String> qualityTexts(Map<LocaleTag,String> paths,Map<String,String> resources,Context c){var out=resourceTexts(paths,resources,c);for(var e:paths.entrySet()){String file=e.getValue();JsonNode root=parse(resources.get(file),file,c);if(root==null||!root.isObject()){c.add("PROFILE_RESOURCE_INVALID",file,"","Quality resource must be a JSON object");continue;}fields(root,c,file,Set.of("schemaVersion","policyVersion","maxNarrativeCodePoints","forbiddenPhrases"));int version=integer(root,"schemaVersion"),max=integer(root,"maxNarrativeCodePoints");String policyVersion=text(root,"policyVersion");List<String> phrases=strings(root.get("forbiddenPhrases"),64,c,file,"/forbiddenPhrases");if(version!=1||policyVersion==null||!policyVersion.matches("[a-z0-9][a-z0-9._-]{0,63}")||max<1000||max>24000||phrases.stream().anyMatch(v->v.codePointCount(0,v.length())>120)){c.add("PROFILE_RESOURCE_INVALID",file,"","Quality review policy is invalid");}}return out;}
    private static String reference(JsonNode root,String field,List<String> files,Context c){String path=text(root,field);if(path==null||!files.contains(path))c.add("PROFILE_RESOURCE_MISSING","profile.json","/"+field,"Referenced resource is missing");return path;}
    private static void requireInput(List<StartInputDef> inputs,String id,List<String> values,Context c,String file){var matches=inputs.stream().filter(v->v.id().equals(id)).toList();if(matches.size()!=1||!matches.getFirst().required()||!(matches.getFirst() instanceof StartInputDef.EnumInput e)||!e.values().equals(values))c.add("PROFILE_SETUP_INVALID",file,"/inputs","Required semantic input is missing or mismatched");}
    private static void coherent(int counters,int flags,int sets,Set<String> conditions,Set<String> effects,Context c,String pointer){if(counters==0&&(conditions.stream().anyMatch(v->v.startsWith("COUNTER_"))||effects.stream().anyMatch(v->v.startsWith("COUNTER_")))||flags==0&&(conditions.contains("FLAG_IS")||effects.contains("FLAG_SET"))||sets==0&&(conditions.stream().anyMatch(v->v.startsWith("SET_"))||effects.stream().anyMatch(v->v.startsWith("SET_"))))c.add("PROFILE_CAPABILITY_INCONSISTENT","profile.json",pointer,"Mechanic requires nonzero state capacity");}
    private static boolean validPath(String p){return p!=null&&!p.isBlank()&&!p.startsWith("/")&&!p.contains("\\")&&!p.contains("..")&&!p.contains(":")&&!p.contains("?")&&!p.contains("#")&&!p.contains("//")&&!p.endsWith("/");}
    private static String decode(byte[] b)throws CharacterCodingException{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();}
    private static JsonNode parse(String value,String file,Context c){if(value==null){c.add("PROFILE_RESOURCE_MISSING",safe(file),"","Resource is missing");return null;}try{JsonNode n=JSON.readTree(value);if(depth(n)>24)c.add("PROFILE_LIMIT_EXCEEDED",file,"","JSON nesting exceeds supported limit");return n;}catch(RuntimeException e){c.add("PROFILE_JSON_INVALID",file,"","Invalid JSON resource");return null;}}
    private static int depth(JsonNode n){if(n==null||n.isValueNode())return 1;int max=0;for(JsonNode child:n)max=Math.max(max,depth(child));return 1+max;}
    private static void fields(JsonNode n,Context c,String file,Set<String> allowed){if(n==null||!n.isObject()){c.add("PROFILE_JSON_INVALID",file,"","Expected object");return;}n.propertyNames().forEach(k->{if(!allowed.contains(k))c.add("PROFILE_UNKNOWN_FIELD",file,"/"+safe(k),"Unknown field");});}
    private static boolean object(JsonNode n,Context c,String pointer){if(n==null||!n.isObject()){c.add("PROFILE_JSON_INVALID","profile.json",pointer,"Expected object");return false;}return true;}
    private static boolean array(JsonNode n,int min,int max,Context c,String pointer){if(n==null||!n.isArray()||n.size()<min||n.size()>max){c.add("PROFILE_JSON_INVALID","profile.json",pointer,"Expected bounded array");return false;}return true;}
    private static List<String> strings(JsonNode n,int max,Context c,String file,String pointer){var out=new ArrayList<String>();if(n==null||!n.isArray()||n.size()>max){c.add("PROFILE_JSON_INVALID",file,pointer,"Expected bounded string array");return out;}for(JsonNode v:n)if(!v.isString()||v.asString().isBlank()){c.add("PROFILE_JSON_INVALID",file,pointer,"Expected nonblank strings");}else out.add(v.asString());return out;}
    private static int integer(JsonNode n,String k){JsonNode v=n==null?null:n.get(k);return v!=null&&v.isIntegralNumber()&&v.canConvertToInt()?v.intValue():Integer.MIN_VALUE;}
    private static int i(JsonNode n,String k){return integer(n,k);}
    private static boolean bool(JsonNode n,String k){JsonNode v=n==null?null:n.get(k);return v!=null&&v.isBoolean()&&v.booleanValue();}
    private static String text(JsonNode n,String k){JsonNode v=n==null?null:n.get(k);return v!=null&&v.isString()?v.asString():null;}
    private static boolean validId(String id){return id!=null&&id.matches("[a-z][a-z0-9-]{0,63}");}
    private static String safe(String value){return value==null?"":value.replaceAll("[^A-Za-z0-9._/-]","?");}
    private static final class Context{final List<ProfileIssue> issues=new ArrayList<>();boolean truncated;void add(String code,String resource,String pointer,String message){if(issues.size()<100)issues.add(new ProfileIssue(code,resource,pointer,message));else if(!truncated){issues.set(99,new ProfileIssue("PROFILE_ISSUES_TRUNCATED","profile.json","","Additional issues were omitted"));truncated=true;}}ProfileLoadResult result(){return new ProfileLoadResult(Optional.empty(),issues);}}
}
