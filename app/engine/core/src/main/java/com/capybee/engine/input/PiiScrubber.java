package com.capybee.engine.input;

import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PiiScrubber {
    public static final int MAX_INPUT_CODE_POINTS = 500;
    public static final int DEFAULT_INPUT_CODE_POINTS = 300;

    private static final Pattern EMAIL = Pattern.compile("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern HANDLE = Pattern.compile("(?<![\\p{L}\\p{N}])@[\\p{L}\\p{N}_.-]{2,}");
    private static final Pattern DATE = Pattern.compile("\\b(?:19|20)\\d{2}[-/.](?:0?[1-9]|1[0-2])[-/.](?:0?[1-9]|[12]\\d|3[01])\\b|\\b(?:0?[1-9]|[12]\\d|3[01])[-/.](?:0?[1-9]|1[0-2])[-/.](?:19|20)\\d{2}\\b");
    private static final Pattern IDENTIFIER = Pattern.compile("(?<!\\d)(?:\\d[ -]?){8,}(?!\\d)");
    private static final Pattern PHONE = Pattern.compile("(?<!\\w)(?:\\+\\d{1,3}[ .()-]?)?(?:\\(?\\d{2,4}\\)?[ .()-]?){2,4}\\d{2,4}(?!\\w)");

    public ScrubResult scrub(String rawText) {
        String normalized = normalize(rawText);
        String safe = normalized;
        Set<PiiKind> kinds = EnumSet.noneOf(PiiKind.class);
        Replacement replacement = replace(safe, EMAIL, "[redacted:email]", PiiKind.EMAIL, kinds);
        safe = replacement.text();
        replacement = replace(safe, URL, "[redacted:url]", PiiKind.URL, kinds);
        safe = replacement.text();
        replacement = replace(safe, HANDLE, "[redacted:contact]", PiiKind.CONTACT_HANDLE, kinds);
        safe = replacement.text();
        replacement = replace(safe, DATE, "[redacted:date]", PiiKind.DATE, kinds);
        safe = replacement.text();
        replacement = replace(safe, IDENTIFIER, "[redacted:identifier]", PiiKind.IDENTIFIER, kinds);
        safe = replacement.text();
        replacement = replace(safe, PHONE, "[redacted:phone]", PiiKind.PHONE, kinds);
        safe = replacement.text();
        return new ScrubResult(safe, !safe.equals(normalized), kinds);
    }

    public ProcessedInput process(String rawText) {
        ScrubResult result = scrub(rawText);
        return new ProcessedInput(new SafeTextInput(result.safeText()), result.changed(), result.detectedKinds());
    }

    public String normalize(String rawText) {
        if (rawText == null) throw new IllegalArgumentException("Input is missing");
        if (rawText.codePointCount(0, rawText.length()) > MAX_INPUT_CODE_POINTS) {
            throw new InputTextException("INPUT_TOO_LONG");
        }
        String lineNormalized = rawText.replace("\r\n", "\n").replace('\r', '\n');
        for (int offset = 0; offset < lineNormalized.length();) {
            int codePoint = lineNormalized.codePointAt(offset);
            if (codePoint == 0 || codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE
                    || Character.isISOControl(codePoint) && codePoint != '\n' && codePoint != '\t') {
                throw new InputTextException("INPUT_INVALID_CHARACTERS");
            }
            offset += Character.charCount(codePoint);
        }
        return Normalizer.normalize(lineNormalized.replace('\n', ' ').replace('\t', ' '), Normalizer.Form.NFC).strip();
    }

    private Replacement replace(String source, Pattern pattern, String placeholder, PiiKind kind, Set<PiiKind> kinds) {
        Matcher matcher = pattern.matcher(source);
        if (!matcher.find()) return new Replacement(source);
        kinds.add(kind);
        return new Replacement(matcher.replaceAll(Matcher.quoteReplacement(placeholder)));
    }

    private record Replacement(String text) {}

    public static final class InputTextException extends IllegalArgumentException {
        private final String code;

        private InputTextException(String code) {
            super(code);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}