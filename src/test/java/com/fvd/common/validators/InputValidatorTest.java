package com.fvd.common.validators;

import com.fvd.common.exceptions.InvalidInputException;
import org.junit.jupiter.api.Test;

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
    void validateVersionRejectsSlash() {
        assertThatThrownBy(() -> InputValidator.validateVersion("3.27/foo"))
                .isInstanceOf(InvalidInputException.class);
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
}
