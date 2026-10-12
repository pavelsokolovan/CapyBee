package com.capybee.engine.content.json;

import com.capybee.engine.content.Block;
import com.capybee.engine.content.BlockKind;
import com.capybee.engine.content.Scene;
import com.capybee.engine.content.SceneOption;
import com.capybee.engine.content.SceneSet;
import com.capybee.engine.pack.LocaleTag;
import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackFeature;
import com.capybee.engine.pack.PackValidationContext;
import com.capybee.engine.pack.SectionValidator;
import com.capybee.engine.rules.Effect;
import com.capybee.engine.rules.json.RulesParser;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public final class SceneSectionValidator implements SectionValidator {
    @Override
    public void validate(Pack pack, PackValidationContext context) {
        Map<LocaleTag, SceneSet> sceneSets = SceneSets.load(pack, context);
        boolean hasEndRun = hasEndRun(sceneSets, RulesParser.parse(pack));
        for (LocaleTag locale : pack.manifest().locales()) {
            SceneSet set = sceneSets.get(locale);
            if (set == null) {
                if (!pack.manifest().features().contains(PackFeature.GENERATION)) {
                    context.error("SCENES_MISSING", "pack.json", "/locales", "Missing scenes for locale " + locale.value());
                }
                continue;
            }
            if (set.startId() == null) {
                context.error("SCENE_START_MISSING", "pack.json", "/scenes", "Locale " + locale.value() + " has no start scene");
            }
            Map<String, Scene> scenes = new LinkedHashMap<>(set.scenes());
            Set<String> sceneIds = new LinkedHashSet<>(scenes.keySet());
            Set<String> duplicateIds = new HashSet<>();
            for (String id : scenes.keySet()) {
                if (id == null || id.isBlank()) duplicateIds.add(id);
            }
            if (!duplicateIds.isEmpty()) {
                context.error("PACK_DUPLICATE_ID", "pack.json", "/scenes", "Duplicate scene ids in locale " + locale.value());
            }
            long starts = scenes.values().stream().filter(Scene::start).count();
            if (starts > 1) {
                context.error("SCENE_START_MULTIPLE", "pack.json", "/scenes", "Multiple start scenes in locale " + locale.value());
            }
            for (Scene scene : scenes.values()) {
                validateScene(scene, locale, context);
            }
            if (!sceneIds.isEmpty()) {
                Set<String> optionIds = new LinkedHashSet<>();
                for (Scene scene : scenes.values()) {
                    for (var option : scene.options()) {
                        optionIds.add(option.id());
                    }
                }
                if (!optionIds.isEmpty()) {
                    for (Scene scene : scenes.values()) {
                        Set<String> ids = new HashSet<>();
                        for (var option : scene.options()) {
                            if (!ids.add(option.id())) {
                                context.error("PACK_DUPLICATE_ID", "pack.json", "/scenes", "Duplicate option id " + option.id() + " in scene " + scene.id());
                            }
                        }
                    }
                }
            }
            checkReachability(set, locale, context, hasEndRun);
        }
        validateLocaleParity(pack, context);
    }

    private void validateScene(Scene scene, LocaleTag locale, PackValidationContext context) {
        int wayCount = 0;
        if (scene.options() != null && !scene.options().isEmpty()) wayCount++;
        if (scene.next() != null && !scene.next().isBlank()) wayCount++;
        if (scene.end()) wayCount++;
        if (wayCount != 1) {
            context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id(), "Scene must have exactly one of options, next, or end");
        }
        for (Block block : scene.blocks()) {
            if (block.kind() == null) {
                context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id() + "/blocks", "Block kind is required");
            }
            if (block.kind() == BlockKind.SPEECH) {
                if (block.speaker() == null || block.speaker().isBlank() || block.speaker().length() > 40) {
                    context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id() + "/blocks", "Speech blocks need a valid speaker");
                }
            } else if (block.speaker() != null && !block.speaker().isBlank()) {
                context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id() + "/blocks", "Only speech blocks may set a speaker");
            }
            if (block.text() == null || block.text().isBlank() || block.text().length() > 2000) {
                context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id() + "/blocks", "Block text must be 1-2000 chars");
            }
            if (block.refs() != null) {
                for (String ref : block.refs()) {
                    if (ref == null || !ref.matches("[a-z][a-z0-9-]*:[a-z][a-z0-9-]*")) {
                        context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id() + "/blocks", "Invalid ref: " + ref);
                    }
                }
            }
        }
        for (SceneOption option : scene.options()) {
            if (option.id() == null || !option.id().matches("[a-z][a-z0-9-]*")) {
                context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id() + "/options", "Invalid option id");
            }
            if (option.label() == null || option.label().isBlank() || option.label().length() > 120) {
                context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id() + "/options", "Option label invalid");
            }
            if (option.next() == null || option.next().isBlank()) {
                context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id() + "/options", "Option next target is required");
            }
        }
        if (scene.next() != null && !scene.next().isBlank() && scene.options() != null && !scene.options().isEmpty()) {
            context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id(), "Scene cannot have both options and next");
        }
        if (!scene.branches().isEmpty() && (scene.next() == null || scene.next().isBlank() || scene.end()
                || scene.options() != null && !scene.options().isEmpty())) {
            context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id(), "Branches are only allowed on linear scenes");
        }
        if (scene.end()) {
            if (scene.options() != null && !scene.options().isEmpty()) {
                context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id(), "End scene cannot have options");
            }
        } else if (scene.outcome() != null) {
            context.error("SCENE_FIELD_INVALID", "pack.json", "/scenes/" + scene.id() + "/outcome", "Outcome is only allowed on end scenes");
        }
        if (scene.next() != null && !scene.next().isBlank()) {
            if (scene.next().equals(scene.id())) {
                // cycles are legal; no-op
            }
        }
    }

    private void checkReachability(SceneSet set, LocaleTag locale, PackValidationContext context, boolean hasEndRun) {
        Map<String, Scene> scenes = set.scenes();
        if (set.startId() == null || !scenes.containsKey(set.startId())) {
            return;
        }
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(set.startId());
        while (!queue.isEmpty()) {
            String id = queue.remove();
            if (!visited.add(id)) continue;
            Scene scene = scenes.get(id);
            if (scene == null) continue;
            if (scene.options() != null) {
                for (var option : scene.options()) {
                    if (option.next() != null && scenes.containsKey(option.next())) {
                        queue.add(option.next());
                    }
                }
            }
            if (scene.next() != null && !scene.next().isBlank()) {
                if (scenes.containsKey(scene.next())) {
                    queue.add(scene.next());
                }
            }
            for (var branch : scene.branches()) {
                if (scenes.containsKey(branch.next())) queue.add(branch.next());
            }
        }
        for (String sceneId : scenes.keySet()) {
            if (!visited.contains(sceneId)) {
                context.warn("SCENE_UNREACHABLE", "pack.json", "/scenes", "Scene is unreachable: " + sceneId);
            }
        }
        if (visited.isEmpty()) return;
        Set<String> withEnd = new LinkedHashSet<>();
        for (String sceneId : visited) {
            Scene scene = scenes.get(sceneId);
            if (scene != null && scene.end()) {
                withEnd.add(sceneId);
            }
        }
        if (withEnd.isEmpty()) {
            if (!hasEndRun) {
                context.error("SCENE_NO_END", "pack.json", "/scenes", "No end scene or END_RUN effect is available in locale " + locale.value());
            }
            return;
        }
        for (String sceneId : visited) {
            if (sceneId.equals(set.startId())) continue;
            if (reachesEnd(scenes, sceneId, new HashSet<>(), withEnd)) {
                continue;
            }
            context.warn("SCENE_NO_PATH_TO_END", "pack.json", "/scenes", "Scene has no path to an end scene: " + sceneId);
        }
    }

    private boolean hasEndRun(Map<LocaleTag, SceneSet> sceneSets, com.capybee.engine.rules.Rules rules) {
        if (rules.objectives().stream().anyMatch(objective -> containsEndRun(objective.onComplete()))) return true;
        return sceneSets.values().stream().flatMap(set -> set.scenes().values().stream())
                .anyMatch(scene -> containsEndRun(scene.onEnter())
                        || scene.options().stream().anyMatch(option -> containsEndRun(option.effects())));
    }

    private boolean containsEndRun(List<Effect> effects) {
        return effects.stream().anyMatch(Effect.EndRun.class::isInstance);
    }

    private boolean reachesEnd(Map<String, Scene> scenes, String current, Set<String> seen, Set<String> endIds) {
        if (endIds.contains(current)) return true;
        if (!seen.add(current)) return false;
        Scene scene = scenes.get(current);
        if (scene == null) return false;
        if (scene.end()) return true;
        if (scene.options() != null) {
            for (var option : scene.options()) {
                if (reachesEnd(scenes, option.next(), seen, endIds)) {
                    return true;
                }
            }
        }
        for (var branch : scene.branches()) {
            if (reachesEnd(scenes, branch.next(), new HashSet<>(seen), endIds)) return true;
        }
        if (scene.next() != null && !scene.next().isBlank()) {
            if (reachesEnd(scenes, scene.next(), new HashSet<>(seen), endIds)) {
                return true;
            }
        }
        return false;
    }

    private void validateLocaleParity(Pack pack, PackValidationContext context) {
        List<LocaleTag> locales = pack.manifest().locales();
        if (locales.size() < 2) return;
        Map<LocaleTag, Set<String>> sceneIdsByLocale = new LinkedHashMap<>();
        Map<LocaleTag, Set<String>> optionIdsByLocale = new LinkedHashMap<>();
        for (LocaleTag locale : locales) {
            var set = SceneSets.load(pack).get(locale);
            if (set == null) continue;
            sceneIdsByLocale.put(locale, new LinkedHashSet<>(set.scenes().keySet()));
            Set<String> optionIds = new LinkedHashSet<>();
            for (Scene scene : set.scenes().values()) {
                for (SceneOption option : scene.options()) {
                    optionIds.add(option.id());
                }
            }
            optionIdsByLocale.put(locale, optionIds);
        }
        Set<String> firstSceneIds = new LinkedHashSet<>(sceneIdsByLocale.values().stream().findFirst().orElse(Set.of()));
        Set<String> firstOptionIds = new LinkedHashSet<>(optionIdsByLocale.values().stream().findFirst().orElse(Set.of()));
        for (var entry : sceneIdsByLocale.entrySet()) {
            if (!Objects.equals(entry.getValue(), firstSceneIds) || !Objects.equals(optionIdsByLocale.get(entry.getKey()), firstOptionIds)) {
                context.error("SCENE_LOCALE_MISMATCH", "pack.json", "/locales", "Scene ids and option ids are not structurally identical across locales");
                break;
            }
        }
    }
}
