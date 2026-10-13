package com.capybee.engine.pack.json;

import com.capybee.engine.pack.*;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class PackLoader {
    private final List<SectionValidator> sectionValidators;

    public PackLoader(List<SectionValidator> sectionValidators) {
        this.sectionValidators = List.copyOf(sectionValidators);
    }

    public LoadResult tryLoad(PackId expectedId, PackSource source) {
        Objects.requireNonNull(expectedId);
        Objects.requireNonNull(source);
        PackValidationContext context = new PackValidationContext();
        Optional<String> manifestText = source.read("pack.json");
        if (manifestText.isEmpty()) {
            context.error("PACK_MANIFEST_MISSING", "pack.json", "", "Manifest is missing");
            return result(null, context);
        }
        int manifestBytes = bytes(manifestText.get());
        if (manifestBytes > PackLayout.MAX_FILE_BYTES) {
            context.error("PACK_TOO_LARGE", "pack.json", "", "File exceeds size limit");
            return result(null, context);
        }
        String text = stripBom(manifestText.get(), "pack.json", context);
        JsonNode root = JsonDocuments.parse(text, "pack.json", context);
        if (context.hasErrors()) return result(null, context);
        if (root == null || !root.isObject()) {
            context.error("PACK_FIELD_INVALID", "pack.json", "", "Expected manifest object");
            return result(null, context);
        }
        JsonNode schema = root.get("schemaVersion");
        if (schema == null || !schema.isIntegralNumber() || !schema.canConvertToInt()) {
            context.error("PACK_FIELD_INVALID", "pack.json", "/schemaVersion", "Expected integer schema version");
            return result(null, context);
        }
        if (!PackLayout.SUPPORTED_SCHEMA_VERSIONS.contains(schema.intValue())) {
            context.error("PACK_SCHEMA_UNSUPPORTED", "pack.json", "/schemaVersion", "Unsupported schema version");
            return result(null, context);
        }
        ManifestParser.ParsedManifest manifest = new ManifestParser(context).parse(root);
        if (manifest == null) return result(null, context);
        if (manifest.id() != null && !manifest.id().equals(expectedId)) {
            context.error("PACK_ID_MISMATCH", "pack.json", "/id", "Manifest id differs from requested id");
        }
        Map<String, String> files = new LinkedHashMap<>();
        Map<String, JsonNode> documents = new LinkedHashMap<>();
        readFiles(manifest, source, manifestBytes, files, documents, context);
        if (manifest.defaultLocale() != null && !manifest.locales().contains(manifest.defaultLocale())) {
            context.error("PACK_DEFAULT_LOCALE_INVALID", "pack.json", "/defaultLocale", "Default locale is not declared");
        }
        for (LocaleTag locale : manifest.locales()) {
            String path = "strings/" + locale.value() + ".json";
            StringsParser.validate(documents.get(path), locale.value(), path, context);
        }
        registerReferences(manifest, context);
        Pack pack = null;
        if (!context.hasErrors()) {
            pack = new Pack(manifest.manifest(), files);
            for (SectionValidator validator : sectionValidators) validator.validate(pack, context);
        }
        return result(pack, context);
    }

    public Pack load(PackId expectedId, PackSource source) {
        LoadResult result = tryLoad(expectedId, source);
        if (!result.ok()) throw new PackValidationException(result.issues());
        return result.pack().orElseThrow();
    }

    private void readFiles(ManifestParser.ParsedManifest manifest, PackSource source, int manifestBytes,
                           Map<String, String> files, Map<String, JsonNode> documents, PackValidationContext context) {
        Set<String> indexed = new HashSet<>();
        List<String> readable = new ArrayList<>();
        for (int index = 0; index < manifest.files().size(); index++) {
            String path = manifest.files().get(index);
            String pointer = "/files/" + index;
            if (!PackLayout.isValidFilePath(path) || path.equals("pack.json")) {
                context.error("PACK_FILE_PATH_INVALID", "pack.json", pointer, "Invalid relative file path");
            } else if (!indexed.add(path)) {
                context.error("PACK_FILE_DUPLICATE", "pack.json", pointer, "File is indexed twice");
            } else {
                readable.add(path);
            }
        }
        if (!manifest.features().contains(PackFeature.PLACES) && indexed.contains("places.json")) {
            context.error("PLACES_FEATURE_REQUIRED", "places.json", "", "places.json requires the PLACES feature");
        }
        for (String required : PackLayout.requiredFiles(manifest.locales(), manifest.features())) {
            if (!indexed.contains(required)) {
                context.error("PACK_REQUIRED_FILE_MISSING", required, "", "Required file is not indexed");
            }
        }
        long totalBytes = manifestBytes;
        boolean packTooLarge = false;
        for (String path : readable) {
            Optional<String> content = source.read(path);
            if (content.isEmpty()) {
                context.error("PACK_FILE_MISSING", path, "", "Indexed file is missing");
                continue;
            }
            int size = bytes(content.get());
            totalBytes += size;
            if (totalBytes > PackLayout.MAX_PACK_BYTES && !packTooLarge) {
                context.error("PACK_TOO_LARGE", "pack.json", "/files", "Pack exceeds size limit");
                packTooLarge = true;
            }
            if (size > PackLayout.MAX_FILE_BYTES) {
                context.error("PACK_TOO_LARGE", path, "", "File exceeds size limit");
                continue;
            }
            if (packTooLarge) continue;
            String text = stripBom(content.get(), path, context);
            if (text.isBlank()) {
                context.error("PACK_FILE_EMPTY", path, "", "File is empty or blank");
                continue;
            }
            files.put(path, text);
            if (path.endsWith(".json")) documents.put(path, JsonDocuments.parse(text, path, context));
        }
    }

    private void registerReferences(ManifestParser.ParsedManifest manifest, PackValidationContext context) {
        int freeTextCount = 0;
        for (int index = 0; index < manifest.startInputs().size(); index++) {
            StartInputDef input = manifest.startInputs().get(index);
            String pointer = "/startInputs/" + index;
            context.references().define("startInput", input.id(), "pack.json", pointer + "/id");
            for (LocaleTag locale : manifest.locales()) {
                context.references().reference("stringKey:" + locale.value(), input.labelKey(), "pack.json", pointer + "/labelKey");
            }
            if (input instanceof StartInputDef.FreeTextInput) {
                freeTextCount++;
                if (manifest.contentPolicy() == null || !manifest.contentPolicy().freeTextAllowed()) {
                    context.error("PACK_FREE_TEXT_NOT_ALLOWED", "pack.json", pointer, "Free text is not allowed by policy");
                }
            }
        }
        if (freeTextCount > PackLayout.MAX_FREE_TEXT_INPUTS) {
            context.error("PACK_TOO_MANY_FREE_TEXT_INPUTS", "pack.json", "/startInputs", "Too many free-text inputs");
        }
        for (int index = 0; index < manifest.events().size(); index++) {
            context.references().define("event", manifest.events().get(index).id(), "pack.json", "/events/" + index + "/id");
        }
    }

    private LoadResult result(Pack pack, PackValidationContext context) {
        List<PackIssue> issues = new ArrayList<>(context.issues());
        issues.addAll(context.references().check());
        boolean errors = issues.stream().anyMatch(issue -> issue.severity() == PackIssue.Severity.ERROR);
        return new LoadResult(errors ? Optional.empty() : Optional.ofNullable(pack), issues);
    }

    private String stripBom(String text, String file, PackValidationContext context) {
        if (text.startsWith("\uFEFF")) {
            context.warn("PACK_BOM_STRIPPED", file, "", "Leading UTF-8 BOM removed");
            return text.substring(1);
        }
        return text;
    }

    private int bytes(String text) { return text.getBytes(StandardCharsets.UTF_8).length; }
}