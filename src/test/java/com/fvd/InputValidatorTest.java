package com.fvd;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

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
        assertThatThrownBy(() -> InputValidator.validateVersion("3.21/foo"))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void validateVersionAcceptsValid() {
        assertThatCode(() -> InputValidator.validateVersion("3.21")).doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.validateVersion("main")).doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.validateVersion("3.21.0")).doesNotThrowAnyException();
        assertThatCode(() -> InputValidator.validateVersion("3.21-SNAPSHOT")).doesNotThrowAnyException();
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
}
