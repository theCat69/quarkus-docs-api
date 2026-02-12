package com.fvd.common.utils;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionPathUtilsTest {

    // --- Single-arg groupByExtension tests ---

    @Test
    void groupByExtensionWithCoreOnlyFiles() {
        List<String> allFiles = List.of("security.adoc", "config.adoc", "getting-started.adoc");

        Map<String, List<String>> result = ExtensionPathUtils.groupByExtension(allFiles);

        assertThat(result).containsKey("quarkus-core");
        assertThat(result.get("quarkus-core")).containsExactly("security.adoc", "config.adoc", "getting-started.adoc");
        assertThat(result).hasSize(1);
    }

    @Test
    void groupByExtensionWithMixedFiles() {
        List<String> allFiles = List.of(
                "config.adoc",
                "quarkiverse/quarkus-cxf/index.adoc",
                "security.adoc",
                "quarkiverse/quarkus-openapi-generator/index.adoc"
        );

        Map<String, List<String>> result = ExtensionPathUtils.groupByExtension(allFiles);

        assertThat(result).hasSize(3);
        assertThat(result.get("quarkus-core")).containsExactly("config.adoc", "security.adoc");
        assertThat(result.get("quarkus-cxf")).containsExactly("quarkiverse/quarkus-cxf/index.adoc");
        assertThat(result.get("quarkus-openapi-generator")).containsExactly("quarkiverse/quarkus-openapi-generator/index.adoc");
    }

    @Test
    void groupByExtensionWithQuarkiverseOnly() {
        List<String> allFiles = List.of(
                "quarkiverse/quarkus-cxf/index.adoc",
                "quarkiverse/quarkus-cxf/usage.adoc",
                "quarkiverse/quarkus-openapi-generator/index.adoc"
        );

        Map<String, List<String>> result = ExtensionPathUtils.groupByExtension(allFiles);

        assertThat(result).hasSize(3);
        assertThat(result.get("quarkus-core")).isEmpty();
        assertThat(result.get("quarkus-cxf")).containsExactly(
                "quarkiverse/quarkus-cxf/index.adoc",
                "quarkiverse/quarkus-cxf/usage.adoc"
        );
        assertThat(result.get("quarkus-openapi-generator")).containsExactly("quarkiverse/quarkus-openapi-generator/index.adoc");
    }

    @Test
    void groupByExtensionWithEmptyList() {
        Map<String, List<String>> result = ExtensionPathUtils.groupByExtension(List.of());

        assertThat(result).hasSize(1);
        assertThat(result.get("quarkus-core")).isEmpty();
    }

    @Test
    void groupByExtensionPreservesInsertionOrder() {
        List<String> allFiles = List.of(
                "config.adoc",
                "quarkiverse/quarkus-cxf/index.adoc",
                "quarkiverse/quarkus-openapi-generator/index.adoc"
        );

        Map<String, List<String>> result = ExtensionPathUtils.groupByExtension(allFiles);

        // quarkus-core should be first, then extensions in order of first appearance
        assertThat(result.keySet()).containsExactly("quarkus-core", "quarkus-cxf", "quarkus-openapi-generator");
    }

    // --- Two-arg groupByExtension tests ---

    @Test
    void groupByExtensionTwoArgWithCoreAndQuarkiverse() {
        List<String> coreFiles = List.of("security.adoc", "config.adoc");
        List<String> quarkiversePaths = List.of(
                "quarkiverse/quarkus-cxf/index.adoc",
                "quarkiverse/quarkus-openapi-generator/index.adoc"
        );

        Map<String, List<String>> result = ExtensionPathUtils.groupByExtension(coreFiles, quarkiversePaths);

        assertThat(result).hasSize(3);
        assertThat(result.get("quarkus-core")).containsExactly("security.adoc", "config.adoc");
        assertThat(result.get("quarkus-cxf")).containsExactly("quarkiverse/quarkus-cxf/index.adoc");
        assertThat(result.get("quarkus-openapi-generator")).containsExactly("quarkiverse/quarkus-openapi-generator/index.adoc");
    }

    @Test
    void groupByExtensionTwoArgWithEmptyQuarkiverse() {
        List<String> coreFiles = List.of("security.adoc");
        List<String> quarkiversePaths = List.of();

        Map<String, List<String>> result = ExtensionPathUtils.groupByExtension(coreFiles, quarkiversePaths);

        assertThat(result).hasSize(1);
        assertThat(result.get("quarkus-core")).containsExactly("security.adoc");
    }

    @Test
    void groupByExtensionTwoArgWithEmptyCoreFiles() {
        List<String> coreFiles = List.of();
        List<String> quarkiversePaths = List.of("quarkiverse/quarkus-cxf/index.adoc");

        Map<String, List<String>> result = ExtensionPathUtils.groupByExtension(coreFiles, quarkiversePaths);

        assertThat(result).hasSize(2);
        assertThat(result.get("quarkus-core")).isEmpty();
        assertThat(result.get("quarkus-cxf")).containsExactly("quarkiverse/quarkus-cxf/index.adoc");
    }

    @Test
    void groupByExtensionTwoArgGroupsMultipleFilesPerExtension() {
        List<String> coreFiles = List.of("security.adoc");
        List<String> quarkiversePaths = List.of(
                "quarkiverse/quarkus-cxf/index.adoc",
                "quarkiverse/quarkus-cxf/usage.adoc",
                "quarkiverse/quarkus-cxf/config.adoc"
        );

        Map<String, List<String>> result = ExtensionPathUtils.groupByExtension(coreFiles, quarkiversePaths);

        assertThat(result.get("quarkus-cxf")).containsExactly(
                "quarkiverse/quarkus-cxf/index.adoc",
                "quarkiverse/quarkus-cxf/usage.adoc",
                "quarkiverse/quarkus-cxf/config.adoc"
        );
    }

    // --- extractExtensionName tests ---

    @Test
    void extractExtensionNameWithValidPath() {
        String result = ExtensionPathUtils.extractExtensionName("quarkiverse/quarkus-cxf/index.adoc");
        assertThat(result).isEqualTo("quarkus-cxf");
    }

    @Test
    void extractExtensionNameWithTwoSegments() {
        String result = ExtensionPathUtils.extractExtensionName("quarkiverse/quarkus-cxf");
        assertThat(result).isEqualTo("quarkus-cxf");
    }

    @Test
    void extractExtensionNameWithSingleSegment() {
        String result = ExtensionPathUtils.extractExtensionName("quarkiverse");
        assertThat(result).isNull();
    }

    @Test
    void extractExtensionNameWithEmptyString() {
        String result = ExtensionPathUtils.extractExtensionName("");
        assertThat(result).isNull();
    }

    // --- Constants tests ---

    @Test
    void coreExtensionKeyIsCorrect() {
        assertThat(ExtensionPathUtils.CORE_EXTENSION_KEY).isEqualTo("quarkus-core");
    }

    @Test
    void quarkiversePrefixIsCorrect() {
        assertThat(ExtensionPathUtils.QUARKIVERSE_PREFIX).isEqualTo("quarkiverse/");
    }
}
