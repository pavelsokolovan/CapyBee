package com.capybee.engine.cli;

import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.json.PackLoader;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Production-loader validation used by the independent release-package verifier. */
public final class PackageResourceVerifier {
    private PackageResourceVerifier() {}

    public static void main(String[] args) {
        if (args.length != 1) fail("RESOURCE_ROOT_REQUIRED");
        try {
            Path root = Path.of(args[0]).toRealPath();
            Path profile = root.resolve("profiles/generic-adventure");
            var loadedProfile = ProfileDirectory.load(profile);
            if (!loadedProfile.valid()
                    || !"generic-adventure".equals(loadedProfile.profile().orElseThrow().identity().id().value())) {
                fail("PROFILE_INVALID");
            }

            Path pack = root.resolve("packs/lanterns-of-willow-marsh");
            var loadedPack = new PackLoader(Engine.sectionValidators()).tryLoad(
                    new PackId("lanterns-of-willow-marsh"), new DirectoryPackSource(pack));
            if (!loadedPack.ok()) fail("PACK_INVALID");

            ObjectMapper json = new ObjectMapper();
            for (String relative : List.of(
                    "certification/willow-marsh-v1/suite.json",
                    "certification/dynamic-v1/blueprint.json",
                    "certification/dynamic-live-v1/suite.json",
                    "certification/dynamic-live-v1/suite.schema.json",
                    "certification/dynamic-live-v1/run.schema.json",
                    "certification/dynamic-live-v1/manifest.schema.json")) {
                Path file = root.resolve(relative).normalize();
                if (!file.startsWith(root) || !Files.isRegularFile(file)) fail("CERTIFICATION_RESOURCE_MISSING");
                json.readTree(Files.readString(file, StandardCharsets.UTF_8));
            }
            System.out.println("PACKAGE_RESOURCES_VALID");
            System.out.println("profileFingerprint="
                    + loadedProfile.profile().orElseThrow().identity().fingerprint());
        } catch (Exception failure) {
            System.err.println("PACKAGE_RESOURCE_INVALID: " + failure.getClass().getSimpleName());
            System.exit(2);
        }
    }

    private static void fail(String code) {
        throw new IllegalStateException(code);
    }
}
