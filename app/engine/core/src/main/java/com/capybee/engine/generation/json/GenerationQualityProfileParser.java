package com.capybee.engine.generation.json;

import com.capybee.engine.generation.GenerationQualityEvaluator;
import com.capybee.engine.generation.GenerationQualityProfile;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GenerationQualityProfileParser {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private GenerationQualityProfileParser() {}

    public static GenerationQualityProfile parse(Pack pack, LocaleTag locale, String path) {
        if (path == null || !path.startsWith("generation/quality/") || !pack.manifest().files().contains(path)) fail("PACK_QUALITY_RESOURCE_MISSING");
        JsonNode root;
        try { root = MAPPER.readTree(pack.requireFile(path)); } catch (RuntimeException e) { throw new IllegalArgumentException("PACK_QUALITY_PROFILE_INVALID", e); }
        exact(root, Set.of("schemaVersion","profileId","locale","readability","repetition","phraseSets","toneProfiles","truthPhrases"));
        if (integer(root,"schemaVersion") != 1) fail("PACK_QUALITY_SCHEMA_UNSUPPORTED");
        String id=text(root,"profileId"), localeText=text(root,"locale");
        if (id == null || !id.matches("[a-z0-9][a-z0-9._-]{0,79}")) fail("PACK_QUALITY_PROFILE_INVALID");
        if (!locale.value().equals(localeText)) fail("PACK_QUALITY_LOCALE_MISMATCH");
        JsonNode rr=object(root,"readability"), rp=object(root,"repetition");
        exact(rr,Set.of("maxSentenceCodePoints","maxParagraphCodePoints","maxConsecutiveShortSentences","maxUppercaseRatioBasisPoints"));
        exact(rp,Set.of("fieldDuplicateThresholdBasisPoints","recentSceneThresholdBasisPoints","choiceThresholdBasisPoints","recentSceneWindow"));
        var readability=new GenerationQualityProfile.ReadabilityRules(range(rr,"maxSentenceCodePoints",80,300),range(rr,"maxParagraphCodePoints",200,700),range(rr,"maxConsecutiveShortSentences",2,8),range(rr,"maxUppercaseRatioBasisPoints",1000,4000));
        var repetition=new GenerationQualityProfile.RepetitionRules(range(rp,"fieldDuplicateThresholdBasisPoints",8500,10000),range(rp,"recentSceneThresholdBasisPoints",7500,9500),range(rp,"choiceThresholdBasisPoints",7000,9500),range(rp,"recentSceneWindow",1,8));
        Map<String,List<String>> phraseSets=new LinkedHashMap<>(); JsonNode sets=object(root,"phraseSets");
        if(setSize(sets)>64)fail("PACK_QUALITY_PHRASE_INVALID"); sets.propertyNames().forEach(name->phraseSets.put(name,phrases(sets.get(name))));
        Map<String,GenerationQualityProfile.ToneProfile> tones=new LinkedHashMap<>(); JsonNode toneNode=object(root,"toneProfiles");
        if(setSize(toneNode)>64)fail("PACK_QUALITY_PROFILE_INVALID"); toneNode.propertyNames().forEach(name->{JsonNode n=object(toneNode,name);exact(n,Set.of("incompatiblePhraseSets","advisoryPhraseSets"));List<String> incompatible=strings(n.get("incompatiblePhraseSets"),64);List<String> advisory=strings(n.get("advisoryPhraseSets"),64);if(java.util.stream.Stream.concat(incompatible.stream(),advisory.stream()).anyMatch(x->!phraseSets.containsKey(x)))fail("PACK_QUALITY_PROFILE_INVALID");tones.put(name,new GenerationQualityProfile.ToneProfile(incompatible,advisory));});
        List<GenerationQualityProfile.TruthPhraseFamily> truths=new ArrayList<>(); JsonNode truthNode=root.get("truthPhrases");
        if(truthNode==null||!truthNode.isArray()||truthNode.size()>64)fail("PACK_QUALITY_TRUTH_REFERENCE_INVALID"); Set<String> truthIds=new java.util.HashSet<>();
        for(JsonNode n:truthNode){exact(n,Set.of("factId","affirming","contradicting"));String fact=text(n,"factId");List<String>a=phrases(n.get("affirming")),c=phrases(n.get("contradicting"));if(fact==null||!fact.matches("(outcome|objective|result|actor|setting|phase):[A-Za-z0-9:_-]+")||!truthIds.add(fact)||a.isEmpty()||c.isEmpty()){fail("PACK_QUALITY_TRUTH_REFERENCE_INVALID");}Set<String> normalized=new java.util.HashSet<>();a.forEach(x->normalized.add(GenerationQualityEvaluator.normalize(x)));if(c.stream().map(GenerationQualityEvaluator::normalize).anyMatch(normalized::contains))fail("PACK_QUALITY_TRUTH_AMBIGUOUS");truths.add(new GenerationQualityProfile.TruthPhraseFamily(fact,a,c));}
        String canonical=GenerationJson.encode(Map.of("schemaVersion",1,"profileId",id,"locale",localeText,"readability",readability,"repetition",repetition,"phraseSets",phraseSets,"toneProfiles",tones,"truthPhrases",truths));
        return new GenerationQualityProfile(1,id,locale,readability,repetition,phraseSets,tones,truths,sha256(canonical));
    }
    private static List<String> phrases(JsonNode n){List<String> values=strings(n,64);Set<String> seen=new java.util.HashSet<>();for(String v:values){int cp=v.codePointCount(0,v.length());String normalized=GenerationQualityEvaluator.normalize(v);if(cp<1||cp>120||normalized.isBlank()||!seen.add(normalized)||v.matches("(?is).*<\\s*(script|iframe).*"))fail("PACK_QUALITY_PHRASE_INVALID");}return values;}
    private static List<String> strings(JsonNode n,int max){if(n==null||!n.isArray()||n.size()>max)fail("PACK_QUALITY_PROFILE_INVALID");List<String> out=new ArrayList<>();for(JsonNode x:n){if(!x.isString())fail("PACK_QUALITY_PROFILE_INVALID");out.add(x.asString());}return List.copyOf(out);}
    private static int range(JsonNode n,String key,int min,int max){int value=integer(n,key);if(value<min||value>max)fail("PACK_QUALITY_LIMIT_INVALID");return value;}
    private static int integer(JsonNode n,String key){JsonNode v=n.get(key);return v!=null&&v.isIntegralNumber()&&v.canConvertToInt()?v.intValue():Integer.MIN_VALUE;}
    private static String text(JsonNode n,String key){JsonNode v=n.get(key);return v!=null&&v.isString()?v.asString():null;}
    private static JsonNode object(JsonNode n,String key){JsonNode v=n.get(key);if(v==null||!v.isObject())fail("PACK_QUALITY_PROFILE_INVALID");return v;}
    private static void exact(JsonNode n,Set<String> names){if(n==null||!n.isObject())fail("PACK_QUALITY_PROFILE_INVALID");n.propertyNames().forEach(x->{if(!names.contains(x))fail("PACK_QUALITY_PROFILE_INVALID");});}
    private static int setSize(JsonNode n){return n.propertyNames().size();}
    private static String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static void fail(String code){throw new IllegalArgumentException(code);}
}
