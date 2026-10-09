package com.capybee.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class EngineBoundaryTest {
    private static final Pattern HOST = Pattern.compile("com\\.capybee\\.server|org\\.springframework|jakarta\\.persistence|org\\.hibernate");
    private static final Pattern JACKSON = Pattern.compile("tools\\.jackson|com\\.fasterxml\\.jackson");
    private static final Pattern THEME = Pattern.compile("(?i:capybee|honeycomb)|\\b(?i:hive|capy)(?![a-z])|(?<=[a-z0-9_])(?:Hive|Capy)(?![a-z])");
    private static final Pattern CONSOLE = Pattern.compile("System\\s*\\.\\s*(?:out|err|in|console)\\b|new\\s+Scanner\\s*\\(\\s*System");
    private static final Pattern NONDETERMINISM = Pattern.compile("(?:Instant|LocalDate|LocalDateTime|ZonedDateTime|OffsetDateTime)\\s*\\.\\s*now\\s*\\(|System\\s*\\.\\s*currentTimeMillis|new\\s+Random\\s*\\(|ThreadLocalRandom|Math\\s*\\.\\s*random|UUID\\s*\\.\\s*randomUUID");
    private static final Pattern VENDOR = Pattern.compile("anthropic|claude|openai|haiku|sonnet", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAMESPACE_LINE = Pattern.compile("^\\s*(?:package\\s|import\\s+(?:static\\s+)?com\\.capybee\\.engine\\.)");
    @TempDir Path temp;

    @Test void productionSourcesRespectEveryBoundary() throws IOException {
        assertEquals(List.of(), scan(Path.of("src/main/java/com/capybee/engine")));
    }

    private List<String> scan(Path root) throws IOException {
        assertTrue(Files.isDirectory(root), "Engine source directory is missing: " + root);
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        assertFalse(sources.isEmpty(), "Engine has no Java sources");
        List<String> violations = new ArrayList<>();
        for (Path source : sources) {
            Path relative = root.relativize(source);
            String zone = relative.getName(0).toString();
            boolean json = false;
            for (Path part : relative) if (part.toString().equals("json")) json = true;
            List<String> lines = Files.readAllLines(source);
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                check(violations, "B1", HOST, line, source, index);
                if (!json && !zone.equals("adapter")) check(violations, "B2", JACKSON, line, source, index);
                if (!NAMESPACE_LINE.matcher(line).find()) check(violations, "B3", THEME, line, source, index);
                if (!zone.equals("cli")) check(violations, "B4", CONSOLE, line, source, index);
                if (!zone.equals("cli") && !zone.equals("adapter")) check(violations, "B5", NONDETERMINISM, line, source, index);
                if (!zone.equals("adapter")) check(violations, "B6", VENDOR, line, source, index);
            }
        }
        return violations;
    }

    private void check(List<String> violations, String rule, Pattern pattern, String line, Path file, int index) {
        if (pattern.matcher(line).find()) violations.add(rule + " " + file + ":" + (index + 1));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "B1|pack|import com.capybee.server.Service;", "B1|pack|import org.springframework.Bean;",
            "B1|pack|import jakarta.persistence.Entity;", "B1|pack|import org.hibernate.Type;",
            "B2|pack|import tools.jackson.databind.JsonNode;", "B2|pack|import com.fasterxml.jackson.databind.JsonNode;",
            "B3|pack|class HiveCell {}", "B3|pack|void getHiveCell() {}", "B3|pack|// capybee",
            "B3|pack|// honeycomb", "B3|pack|class CapyItem {}", "B3|pack|// hive",
            "B4|pack|System.out.println();", "B4|pack|System.err.println();", "B4|pack|System.in.read();",
            "B4|pack|System.console();", "B4|pack|new Scanner(System.in);",
            "B5|pack|Instant.now();", "B5|pack|LocalDate.now();", "B5|pack|LocalDateTime.now();",
            "B5|pack|ZonedDateTime.now();", "B5|pack|OffsetDateTime.now();", "B5|pack|System.currentTimeMillis();",
            "B5|pack|new Random();", "B5|pack|ThreadLocalRandom.current();", "B5|pack|Math.random();",
            "B5|pack|UUID.randomUUID();", "B6|pack|// Anthropic", "B6|pack|// Claude", "B6|pack|// OpenAI",
            "B6|pack|// Haiku", "B6|pack|// Sonnet"
    })
    void scannerDetectsForbiddenConstructs(String rule, String zone, String code) throws IOException {
        Path file = temp.resolve(zone + "/Bad.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package com.capybee.engine.pack;\n" + code);
        List<String> issues = scan(temp);
        assertTrue(issues.stream().anyMatch(issue -> issue.startsWith(rule + " ") && issue.endsWith(":2")), issues.toString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "cli|System.out.println();", "cli|System.in.read();", "cli|Instant.now();",
            "adapter|new Random();", "adapter|// Claude", "adapter|import tools.jackson.databind.JsonNode;",
            "pack/json|import tools.jackson.databind.JsonNode;", "rules/json|import tools.jackson.databind.JsonNode;",
            "pack|class Archive {}", "pack|package com.capybee.engine.pack;", "pack|import com.capybee.engine.pack.Pack;"
    })
    void allowedZonesAndNamespaceLinesPass(String zone, String code) throws IOException {
        Path file = temp.resolve(zone + "/Allowed.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, code);
        assertTrue(scan(temp).isEmpty());
    }

    @Test void missingOrEmptySourceTreeCannotPassVacuously() {
        assertThrows(AssertionError.class, () -> scan(temp.resolve("missing")));
        assertThrows(AssertionError.class, () -> scan(temp));
    }
}