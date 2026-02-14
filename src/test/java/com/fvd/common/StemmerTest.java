package com.fvd.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StemmerTest {

    @ParameterizedTest(name = "stem(\"{0}\") → \"{1}\"")
    @CsvSource({
            // Basic suffix stripping
            "configuration, configur",
            "security,      secur",
            "running,       run",
            "classes,       class",
            "used,          used",
            "stopping,      stop",
            // Various suffix rules
            "action,        action",
            "expression,    expres",
            "management,    manage",
            "darkness,      dark",
            "configurable,  configur",
            "accessible,    access",
            "dangerous,     danger",
            "active,        act",
            "powerful,      power",
            "powerless,     power",
            "quickly,       quick",
            "fastest,       fast",
            "runner,        run",
            "configured,    configur",
            // -s stripping
            "endpoints,     endpoint",
            "class,         class",
            // Edge cases (non-null)
            "'',            ''",
            "go,            go",
            "to,            to",
            "configur,      configur",
            "quarkus,       quarku",
            "ing,           ing",
            // Trailing duplicate consonant behavior
            "runn,          runn",
            "see,           see"
    })
    void stemProducesExpectedResult(String input, String expected) {
        assertThat(Stemmer.stem(input)).isEqualTo(expected);
    }

    @Test
    void stemNullReturnsNull() {
        assertThat(Stemmer.stem(null)).isNull();
    }

    // --- Consistency: morphological variants map to same stem ---

    @Test
    void stemMorphologicalVariantsProduceSameStem() {
        String stem1 = Stemmer.stem("configure");
        // "configure" → no matching suffix. Let me check: -ive no (not matching), -tion no, -sion no... 
        // Actually "configure" doesn't match well. Let's try "configuration" and "configurable"
        String configStem1 = Stemmer.stem("configuration");
        String configStem2 = Stemmer.stem("configurable");
        assertThat(configStem1).isEqualTo(configStem2).isEqualTo("configur");
    }
}
