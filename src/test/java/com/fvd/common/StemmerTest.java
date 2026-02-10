package com.fvd.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StemmerTest {

    // --- Basic suffix stripping ---

    @Test
    void stemConfigurationStripsAtion() {
        assertThat(Stemmer.stem("configuration")).isEqualTo("configur");
    }

    @Test
    void stemSecurityStripsIty() {
        assertThat(Stemmer.stem("security")).isEqualTo("secur");
    }

    @Test
    void stemRunningStripsIngAndReducesDuplicate() {
        // "running" → strip -ing → "runn" → reduce dup → "run"
        assertThat(Stemmer.stem("running")).isEqualTo("run");
    }

    @Test
    void stemClassesStripsEs() {
        assertThat(Stemmer.stem("classes")).isEqualTo("class");
    }

    @Test
    void stemUsedDoesNotStripEdWhenTooShort() {
        // "used" → strip -ed → "us" (length 2 < 3) → no strip → "used"
        assertThat(Stemmer.stem("used")).isEqualTo("used");
    }

    @Test
    void stemStoppingStripsIngAndReducesDuplicate() {
        // "stopping" → strip -ing → "stopp" → reduce dup → "stop"
        assertThat(Stemmer.stem("stopping")).isEqualTo("stop");
    }

    // --- Various suffix rules ---

    @Test
    void stemActionTooShortAfterTionStrip() {
        // "action" → strip -tion → "ac" (length 2 < 3) → no strip → "action"
        assertThat(Stemmer.stem("action")).isEqualTo("action");
    }

    @Test
    void stemExpressionStripsSion() {
        assertThat(Stemmer.stem("expression")).isEqualTo("expres");
    }

    @Test
    void stemManagementStripsMent() {
        assertThat(Stemmer.stem("management")).isEqualTo("manage");
    }

    @Test
    void stemDarknessStripsNess() {
        assertThat(Stemmer.stem("darkness")).isEqualTo("dark");
    }

    @Test
    void stemConfigurableStripsAble() {
        assertThat(Stemmer.stem("configurable")).isEqualTo("configur");
    }

    @Test
    void stemAccessibleStripsIble() {
        assertThat(Stemmer.stem("accessible")).isEqualTo("access");
    }

    @Test
    void stemDangerousStripsOus() {
        assertThat(Stemmer.stem("dangerous")).isEqualTo("danger");
    }

    @Test
    void stemActiveStripsIve() {
        assertThat(Stemmer.stem("active")).isEqualTo("act");
    }

    @Test
    void stemPowerfulStripsFul() {
        assertThat(Stemmer.stem("powerful")).isEqualTo("power");
    }

    @Test
    void stemPowerlessStripsLess() {
        assertThat(Stemmer.stem("powerless")).isEqualTo("power");
    }

    @Test
    void stemQuicklyStripsLy() {
        assertThat(Stemmer.stem("quickly")).isEqualTo("quick");
    }

    @Test
    void stemFastestStripsEst() {
        assertThat(Stemmer.stem("fastest")).isEqualTo("fast");
    }

    @Test
    void stemRunnerStripsErAndReducesDuplicate() {
        // "runner" → strip -er → "runn" → reduce dup → "run"
        assertThat(Stemmer.stem("runner")).isEqualTo("run");
    }

    @Test
    void stemConfiguredStripsEd() {
        assertThat(Stemmer.stem("configured")).isEqualTo("configur");
    }

    // --- -s stripping ---

    @Test
    void stemEndpointsStripsS() {
        assertThat(Stemmer.stem("endpoints")).isEqualTo("endpoint");
    }

    @Test
    void stemDoesNotStripSFromSsEnding() {
        // "class" ends in "ss" — don't strip -s
        assertThat(Stemmer.stem("class")).isEqualTo("class");
    }

    // --- Edge cases ---

    @Test
    void stemShortWordUnchanged() {
        assertThat(Stemmer.stem("go")).isEqualTo("go");
    }

    @Test
    void stemNullReturnsNull() {
        assertThat(Stemmer.stem(null)).isNull();
    }

    @Test
    void stemEmptyStringReturnsEmpty() {
        assertThat(Stemmer.stem("")).isEqualTo("");
    }

    @Test
    void stemTwoCharWordUnchanged() {
        assertThat(Stemmer.stem("to")).isEqualTo("to");
    }

    @Test
    void stemAlreadyStemmedWordUnchanged() {
        // "configur" doesn't match any suffix rule
        assertThat(Stemmer.stem("configur")).isEqualTo("configur");
    }

    @Test
    void stemWordEndingInSStripsS() {
        // "quarkus" → strip -s → "quarku" (length 6 >= 3, not ending in "ss")
        assertThat(Stemmer.stem("quarkus")).isEqualTo("quarku");
    }

    @Test
    void stemWordShorterThanSuffixUnchanged() {
        // "ing" is the same length as the suffix "ing"
        assertThat(Stemmer.stem("ing")).isEqualTo("ing");
    }

    // --- Trailing duplicate consonant reduction ---

    @Test
    void stemTrailingDuplicateConsonantReducedAfterIngStrip() {
        // "running" → strip -ing → "runn" → reduce dup → "run"
        // Dup reduction only applies after -ing, -ed, -er suffix stripping
        assertThat(Stemmer.stem("running")).isEqualTo("run");
    }

    @Test
    void stemStandaloneDoubleConsonantNotReduced() {
        // "runn" has no suffix stripped, so dup reduction does not apply
        assertThat(Stemmer.stem("runn")).isEqualTo("runn");
    }

    @Test
    void stemTrailingDuplicateVowelNotReduced() {
        // Vowel duplicates are not reduced: "see" stays "see"
        assertThat(Stemmer.stem("see")).isEqualTo("see");
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
