package com.capybee.engine.input;

import org.junit.jupiter.api.Test;

import java.text.Normalizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiScrubberTest {
    private final PiiScrubber scrubber = new PiiScrubber();

    @Test
    void normalizesUnicodeCountsCodePointsAndCollapsesLines() {
        String decomposed = "  e\u0301\r\n" + "😀".repeat(300) + "  ";

        String normalized = scrubber.normalize(decomposed);

        assertThat(normalized).startsWith(Normalizer.normalize("e\u0301", Normalizer.Form.NFC) + " ");
        assertThat(normalized.codePointCount(0, normalized.length())).isEqualTo(302);
    }

    @Test
    void rejectsOverLimitAndDisallowedControlCharactersWithoutEchoingInput() {
        assertThatThrownBy(() -> scrubber.normalize("😀".repeat(501)))
                .isInstanceOf(PiiScrubber.InputTextException.class)
                .hasMessage("INPUT_TOO_LONG");
        assertThatThrownBy(() -> scrubber.normalize("marker\u0000secret"))
                .isInstanceOf(PiiScrubber.InputTextException.class)
                .hasMessage("INPUT_INVALID_CHARACTERS");
    }

    @Test
    void scrubsCommonContactDetailsAndHidesTextFromDebugRepresentations() {
        String raw = "Mail alice@example.com, call +1 (415) 555-2671, visit https://example.org, "
                + "handle @alice, birthday 1990-02-03, id 12345678901234";

        ProcessedInput processed = scrubber.process(raw);
        String safe = processed.input().text();

        assertThat(processed.changed()).isTrue();
        assertThat(processed.detectedKinds()).contains(PiiKind.EMAIL, PiiKind.PHONE, PiiKind.URL,
                PiiKind.CONTACT_HANDLE, PiiKind.DATE, PiiKind.IDENTIFIER);
        assertThat(safe).contains("[redacted:email]", "[redacted:phone]", "[redacted:url]",
                "[redacted:contact]", "[redacted:date]", "[redacted:identifier]");
        assertThat(safe).doesNotContain("alice@example.com", "415", "example.org", "12345678901234");
        assertThat(processed.toString()).doesNotContain(raw, "alice@example.com");
        assertThat(processed.input().toString()).doesNotContain(raw, safe);
    }
}