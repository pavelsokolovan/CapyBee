package com.capybee.engine.generation.blueprint;

import com.capybee.engine.api.DynamicSnapshotSource;
import com.capybee.engine.generation.blueprint.json.BlueprintWriter;
import com.capybee.engine.generation.profile.ProfileIdentity;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Creates and verifies immutable dynamic persistence identities. */
public final class DynamicGamePersistence {
    private static final ObjectMapper JSON = new ObjectMapper();
    private DynamicGamePersistence() {}

    public static DynamicGameBundle bundle(BlueprintGenerationResult.CandidateReviewed accepted,
            MaterializedGameDefinition definition, DynamicPresentationContinuation continuation) {
        byte[] blueprintBytes = new BlueprintWriter().write(accepted.blueprint().blueprint());
        String blueprintFingerprint = fingerprint(blueprintBytes);
        var evidence = accepted.evidence();
        AcceptedBlueprintArchive archive = new AcceptedBlueprintArchive(1,
                new String(blueprintBytes, StandardCharsets.UTF_8), definition.profileIdentity(), definition.locale(),
                definition.lengthPresetId(), definition.challengePresetId(), definition.generatedRulesContractVersion(),
                definition.semanticValidatorVersion(), definition.outputReviewerVersion(), evidence.attempts(), blueprintFingerprint);
        String definitionFingerprint = fingerprint(DynamicPersistenceCodec.definitionBytes(definition));
        String continuationFingerprint = fingerprint(DynamicPersistenceCodec.continuationBytes(continuation));
        String game = fingerprint(("capybee-dynamic-game-v1\n" + blueprintFingerprint + "\n"
                + definitionFingerprint + "\n" + continuationFingerprint).getBytes(StandardCharsets.UTF_8));
        return new DynamicGameBundle(1, archive, definition, continuation, definitionFingerprint,
                continuationFingerprint, game);
    }

    public static Verification verify(DynamicGameBundle bundle) {
        try {
            if (bundle == null || bundle.bundleFormatVersion() != 1) return new Verification(false, "DYNAMIC_BUNDLE_VERSION_UNSUPPORTED");
            AcceptedBlueprintArchive archive = bundle.acceptedBlueprint();
            if (archive.archiveFormatVersion() != 1) return new Verification(false, "DYNAMIC_BLUEPRINT_ARCHIVE_INVALID");
            if (!archive.profileIdentity().equals(bundle.definition().profileIdentity())
                    || !archive.locale().equals(bundle.definition().locale())
                    || !archive.lengthPresetId().equals(bundle.definition().lengthPresetId())
                    || !archive.challengePresetId().equals(bundle.definition().challengePresetId())
                    || !archive.profileIdentity().equals(bundle.continuation().profileIdentity())
                    || !archive.locale().equals(bundle.continuation().locale()))
                return new Verification(false, "DYNAMIC_BINDING_MISMATCH");
            var blueprintTree = JSON.readTree(archive.canonicalBlueprint());
            if (blueprintTree == null || !blueprintTree.isObject()
                    || !archive.canonicalBlueprint().equals(JSON.writeValueAsString(blueprintTree))
                    || blueprintTree.required("schemaVersion").asInt() != bundle.definition().blueprintSchemaVersion()
                    || !blueprintTree.required("locale").asString().equals(bundle.definition().locale().value())
                    || !blueprintTree.required("title").asString().equals(bundle.definition().storyPlan().title())
                    || !blueprintTree.required("premise").asString().equals(bundle.definition().storyPlan().premise())
                    || !blueprintTree.required("setting").asString().equals(bundle.definition().storyPlan().setting()))
                return new Verification(false, "DYNAMIC_BLUEPRINT_ARCHIVE_INVALID");
            var evidence = bundle.definition().compilationEvidence();
            if (blueprintTree.required("actors").size() != evidence.actorCount()
                    || blueprintTree.required("objectives").size() != evidence.objectiveCount()
                    || blueprintTree.required("scenes").size() != evidence.sceneCount()
                    || blueprintTree.required("outcomes").size() != evidence.outcomeCount()
                    || blueprintTree.required("state").required("counters").size() != evidence.counterCount()
                    || blueprintTree.required("state").required("flags").size() != evidence.flagCount()
                    || blueprintTree.required("state").required("sets").size() != evidence.setCount()
                    || !ids(blueprintTree.required("actors")).equals(bundle.definition().idBindings().actors().keySet())
                    || !ids(blueprintTree.required("objectives")).equals(bundle.definition().idBindings().objectives().keySet())
                    || !ids(blueprintTree.required("scenes")).equals(bundle.definition().idBindings().scenes().keySet())
                    || !ids(blueprintTree.required("outcomes")).equals(bundle.definition().idBindings().outcomes().keySet()))
                return new Verification(false, "DYNAMIC_BINDING_MISMATCH");
            String blueprint = fingerprint(archive.canonicalBlueprint().getBytes(StandardCharsets.UTF_8));
            String definition = fingerprint(DynamicPersistenceCodec.definitionBytes(bundle.definition()));
            String continuation = fingerprint(DynamicPersistenceCodec.continuationBytes(bundle.continuation()));
            String game = fingerprint(("capybee-dynamic-game-v1\n" + blueprint + "\n" + definition + "\n"
                    + continuation).getBytes(StandardCharsets.UTF_8));
            if (!equal(blueprint, archive.blueprintFingerprint()) || !equal(definition, bundle.definitionFingerprint())
                    || !equal(continuation, bundle.continuationFingerprint()) || !equal(game, bundle.gameFingerprint()))
                return new Verification(false, "DYNAMIC_BUNDLE_FINGERPRINT_MISMATCH");
            var verified = new MaterializedGameDefinitionVerifier().verify(bundle.definition());
            return verified.valid() ? new Verification(true, null) : new Verification(false, "DYNAMIC_DEFINITION_INVALID");
        } catch (RuntimeException exception) {
            return new Verification(false, "DYNAMIC_BUNDLE_FINGERPRINT_MISMATCH");
        }
    }

    static String fingerprint(byte[] bytes) {
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static boolean equal(String left, String right) {
        return left != null && right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII),
                right.getBytes(StandardCharsets.US_ASCII));
    }

    private static java.util.Set<String> ids(tools.jackson.databind.JsonNode values) {
        java.util.LinkedHashSet<String> result = new java.util.LinkedHashSet<>();
        values.forEach(value -> result.add(value.required("id").asString()));
        return result;
    }

    public record Verification(boolean valid, String code) {}
}
