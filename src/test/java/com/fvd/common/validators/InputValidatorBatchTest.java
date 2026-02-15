package com.fvd.common.validators;

import com.fvd.common.exceptions.InvalidInputException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InputValidatorBatchTest {

    @Test
    void shouldThrowWhenPathsIsNull() {
        assertThatThrownBy(() -> InputValidator.validateBatchPaths(null, 10))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("paths must not be empty");
    }

    @Test
    void shouldThrowWhenPathsIsEmpty() {
        assertThatThrownBy(() -> InputValidator.validateBatchPaths(List.of(), 10))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("paths must not be empty");
    }

    @Test
    void shouldThrowWhenPathsExceedsMaxSize() {
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            paths.add("doc" + i + ".adoc");
        }
        assertThatThrownBy(() -> InputValidator.validateBatchPaths(paths, 10))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("paths must not exceed 10 entries");
    }

    @Test
    void shouldThrowWhenPathContainsDoubleDot() {
        List<String> paths = List.of("valid.adoc", "../secret.adoc");
        assertThatThrownBy(() -> InputValidator.validateBatchPaths(paths, 10))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("..");
    }

    @Test
    void shouldThrowWhenPathIsBlank() {
        List<String> paths = List.of("valid.adoc", "  ");
        assertThatThrownBy(() -> InputValidator.validateBatchPaths(paths, 10))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void shouldThrowWhenPathIsAbsolute() {
        List<String> paths = List.of("valid.adoc", "/etc/passwd");
        assertThatThrownBy(() -> InputValidator.validateBatchPaths(paths, 10))
                .isInstanceOf(InvalidInputException.class)
                .hasMessageContaining("must not be absolute");
    }

    @Test
    void shouldDeduplicatePaths() {
        List<String> paths = List.of("security.adoc", "config.adoc", "security.adoc");
        List<String> result = InputValidator.validateBatchPaths(paths, 10);
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly("security.adoc", "config.adoc");
    }

    @Test
    void shouldPassValidPaths() {
        List<String> paths = List.of("security.adoc", "config.adoc");
        List<String> result = InputValidator.validateBatchPaths(paths, 10);
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly("security.adoc", "config.adoc");
    }
}
