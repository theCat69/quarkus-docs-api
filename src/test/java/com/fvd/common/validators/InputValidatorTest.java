package com.fvd.common.validators;

import com.fvd.common.exceptions.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputValidatorTest {

    @Test
    void validateVersionRejectsNull() {
        assertThatThrownBy(() -> InputValidator.validateVersion(null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validateVersionRejectsEmpty() {
        assertThatThrownBy(() -> InputValidator.validateVersion(""))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validateVersionRejectsBlank() {
        assertThatThrownBy(() -> InputValidator.validateVersion("   "))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validateVersionRejectsTraversal() {
        assertThatThrownBy(() -> InputValidator.validateVersion("../etc"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validateVersionAcceptsSlashForCompositeKeys() {
        assertThatCode(() -> InputValidator.validateVersion("quarkiverse/quarkus-openapi-generator"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateVersionAcceptsValid() {
        assertThatCode(() -> InputValidator.validateVersion("3.27")).doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.validateVersion("main")).doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.validateVersion("3.27.0")).doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.validateVersion("3.27-SNAPSHOT")).doesNotThrowAnyException();
    }

    @Test
    void validatePathRejectsNull() {
        assertThatThrownBy(() -> InputValidator.validatePath(null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validatePathRejectsEmpty() {
        assertThatThrownBy(() -> InputValidator.validatePath(""))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validatePathRejectsTraversal() {
        assertThatThrownBy(() -> InputValidator.validatePath("../../etc/passwd"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validatePathRejectsAbsolutePath() {
        assertThatThrownBy(() -> InputValidator.validatePath("/etc/passwd"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("must not be absolute");
    }

    @Test
    void validatePathAcceptsValid() {
        assertThatCode(() -> InputValidator.validatePath("docs/src/main/asciidoc/security-oidc.adoc"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateKeywordsRejectsNull() {
        assertThatThrownBy(() -> InputValidator.validateKeywords(null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validateKeywordsRejectsEmpty() {
        assertThatThrownBy(() -> InputValidator.validateKeywords(""))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validateKeywordsAcceptsValid() {
        assertThatCode(() -> InputValidator.validateKeywords("oidc,security"))
                .doesNotThrowAnyException();
    }

    @Test
    void validateFilePathsRejectsNull() {
        assertThatThrownBy(() -> InputValidator.validateFilePaths(null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validateFilePathsRejectsTraversal() {
        assertThatThrownBy(() -> InputValidator.validateFilePaths("../../etc/passwd"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validateFilePathsRejectsEmptyEntry() {
        assertThatThrownBy(() -> InputValidator.validateFilePaths("path1,,path2"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validateFilePathsAcceptsValid() {
        assertThatCode(() -> InputValidator.validateFilePaths("path1,path2"))
                .doesNotThrowAnyException();
    }

    // --- Limit validation tests ---

    @Test
    void validateLimitReturnsDefaultWhenNull() {
        assertThat(InputValidator.validateLimit(null, 10, 100)).isEqualTo(10);
    }

    @Test
    void validateLimitReturnsValueWhenValid() {
        assertThat(InputValidator.validateLimit(5, 10, 100)).isEqualTo(5);
    }

    @Test
    void validateLimitRejectsZero() {
        assertThatThrownBy(() -> InputValidator.validateLimit(0, 10, 100))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("limit must be at least 1");
    }

    @Test
    void validateLimitRejectsNegative() {
        assertThatThrownBy(() -> InputValidator.validateLimit(-1, 10, 100))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("limit must be at least 1");
    }

    @Test
    void validateLimitRejectsExceedingMax() {
        assertThatThrownBy(() -> InputValidator.validateLimit(101, 10, 100))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("limit must not exceed 100");
    }

    @Test
    void validateLimitAcceptsMaxValue() {
        assertThat(InputValidator.validateLimit(100, 10, 100)).isEqualTo(100);
    }

    // --- Offset validation tests ---

    @Test
    void validateOffsetReturnsZeroWhenNull() {
        assertThat(InputValidator.validateOffset(null)).isEqualTo(0);
    }

    @Test
    void validateOffsetReturnsValueWhenValid() {
        assertThat(InputValidator.validateOffset(5)).isEqualTo(5);
    }

    @Test
    void validateOffsetAcceptsZero() {
        assertThat(InputValidator.validateOffset(0)).isEqualTo(0);
    }

    @Test
    void validateOffsetRejectsNegative() {
        assertThatThrownBy(() -> InputValidator.validateOffset(-1))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("offset must not be negative");
    }

    // --- parseKeywords tests ---

    @Test
    void parseKeywordsSplitsOnSpaces() {
        assertThat(InputValidator.parseKeywords("security oidc")).containsExactly("security", "oidc");
    }

    @Test
    void parseKeywordsLowercases() {
        assertThat(InputValidator.parseKeywords("Security OIDC")).containsExactly("security", "oidc");
    }

    @Test
    void parseKeywordsRemovesStopWords() {
        assertThat(InputValidator.parseKeywords("how does security work")).containsExactly("security");
    }

    @Test
    void parseKeywordsTrimsAndHandlesMultipleSpaces() {
        assertThat(InputValidator.parseKeywords("  security   oidc  ")).containsExactly("security", "oidc");
    }

    @Test
    void parseKeywordsThrowsOnNull() {
        assertThatThrownBy(() -> InputValidator.parseKeywords(null))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void parseKeywordsThrowsOnBlank() {
        assertThatThrownBy(() -> InputValidator.parseKeywords("   "))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void parseKeywordsThrowsOnAllStopWords() {
        assertThatThrownBy(() -> InputValidator.parseKeywords("how does the"))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("All keywords are stop words");
    }

    @Test
    void parseKeywordsSingleKeyword() {
        assertThat(InputValidator.parseKeywords("security")).containsExactly("security");
    }

    @Test
    void parseKeywordsTabSplitting() {
        assertThat(InputValidator.parseKeywords("security\toidc")).containsExactly("security", "oidc");
    }

    // --- resolveVersion tests ---

    @Test
    void resolveVersionReturnsMainWhenNull() {
        assertThat(InputValidator.resolveVersion(null)).isEqualTo("main");
    }

    @Test
    void resolveVersionReturnsMainWhenEmpty() {
        assertThat(InputValidator.resolveVersion("")).isEqualTo("main");
    }

    @Test
    void resolveVersionReturnsMainWhenBlank() {
        assertThat(InputValidator.resolveVersion("  ")).isEqualTo("main");
    }

    @Test
    void resolveVersionReturnsProvidedVersion() {
        assertThat(InputValidator.resolveVersion("3.27")).isEqualTo("3.27");
    }

    @Test
    void resolveVersionThrowsOnInvalidCharacters() {
        assertThatThrownBy(() -> InputValidator.resolveVersion("invalid!version"))
                .isInstanceOf(InvalidInputException.class);
    }

    // --- validateVersionExists tests ---

    @Test
    void validateVersionExistsAcceptsMainEvenIfNotCached() {
        assertThatCode(() -> InputValidator.validateVersionExists("main", List.of("3.27")))
                .doesNotThrowAnyException();
    }

    @Test
    void validateVersionExistsAcceptsMainWithEmptyList() {
        assertThatCode(() -> InputValidator.validateVersionExists("main", List.of()))
                .doesNotThrowAnyException();
    }

    @Test
    void validateVersionExistsAcceptsCachedVersion() {
        assertThatCode(() -> InputValidator.validateVersionExists("3.27", List.of("3.27", "3.21")))
                .doesNotThrowAnyException();
    }

    @Test
    void validateVersionExistsThrowsForUnknownVersion() {
        assertThatThrownBy(() -> InputValidator.validateVersionExists("9.99", List.of("3.27", "3.21")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Unknown version '9.99'")
                .hasMessageContaining("3.27")
                .hasMessageContaining("3.21");
    }

    @Test
    void validateVersionExistsThrowsForUnknownVersionWithEmptyList() {
        assertThatThrownBy(() -> InputValidator.validateVersionExists("9.99", List.of()))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Unknown version '9.99'")
                .hasMessageContaining("main");
    }

    @Test
    void validateVersionExistsIncludesMainInAvailableWhenNotCached() {
        assertThatThrownBy(() -> InputValidator.validateVersionExists("9.99", List.of("3.27")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("main");
    }

    // --- validateSubjectExists tests ---

    @Test
    void validateSubjectExistsAcceptsNull() {
        assertThatCode(() -> InputValidator.validateSubjectExists(null, Set.of("security")))
                .doesNotThrowAnyException();
    }

    @Test
    void validateSubjectExistsAcceptsBlank() {
        assertThatCode(() -> InputValidator.validateSubjectExists("  ", Set.of("security")))
                .doesNotThrowAnyException();
    }

    @Test
    void validateSubjectExistsAcceptsKnownSubject() {
        assertThatCode(() -> InputValidator.validateSubjectExists("security", Set.of("security", "rest-apis")))
                .doesNotThrowAnyException();
    }

    @Test
    void validateSubjectExistsThrowsForUnknownSubject() {
        assertThatThrownBy(() -> InputValidator.validateSubjectExists("nonexistent", Set.of("security", "rest-apis")))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Unknown subject 'nonexistent'")
                .hasMessageContaining("rest-apis")
                .hasMessageContaining("security");
    }

    @Test
    void validateSubjectExistsThrowsForUnknownSubjectWithEmptySet() {
        assertThatThrownBy(() -> InputValidator.validateSubjectExists("nonexistent", Set.of()))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("Unknown subject 'nonexistent'");
    }
}
