package com.fvd.quarkiverse.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AntoraPlaybookParserTest {

    private final AntoraPlaybookParser parser = new AntoraPlaybookParser();

    @Test
    void parsesYamlWithSingleSource() {
        String yaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-openapi-generator
                      branches: main
                      start_path: docs
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(1);
        ResolvedContentSource source = result.get(0);
        assertThat(source.org()).isEqualTo("quarkiverse");
        assertThat(source.repo()).isEqualTo("quarkus-openapi-generator");
        assertThat(source.branch()).isEqualTo("main");
        assertThat(source.startPath()).isEqualTo("docs");
        assertThat(source.extensionName()).isEqualTo("quarkus-openapi-generator");
    }

    @Test
    void parsesYamlWithMultipleSources() {
        String yaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-openapi-generator
                      branches: main
                      start_path: docs
                    - url: https://github.com/quarkiverse/quarkus-amazon-services
                      branches: main
                      start_path: docs
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).extensionName()).isEqualTo("quarkus-openapi-generator");
        assertThat(result.get(1).extensionName()).isEqualTo("quarkus-amazon-services");
    }

    @Test
    void resolvesSingleConcreteBranch() {
        String yaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-cxf
                      branches: 3.8
                      start_path: docs
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).branch()).isEqualTo("3.8");
    }

    @Test
    void handlesListOfBranchesPicksFirstConcrete() {
        String yaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-cxf
                      branches:
                        - "3.8"
                        - "2.7"
                      start_path: docs
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).branch()).isEqualTo("3.8");
    }

    @Test
    void handlesListWithWildcardFirstPicksFirstConcrete() {
        String yaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-cxf
                      branches:
                        - "v*"
                        - "3.8"
                      start_path: docs
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).branch()).isEqualTo("3.8");
    }

    @Test
    void handlesWildcardBranchFallsBackToMain() {
        String yaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-cxf
                      branches: "v*"
                      start_path: docs
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).branch()).isEqualTo("main");
    }

    @Test
    void handlesRegexBranchFallsBackToMain() {
        String yaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-cxf
                      branches: "/^v\\\\d+\\\\..*/"
                      start_path: docs
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).branch()).isEqualTo("main");
    }

    @Test
    void handlesListOfAllWildcardsFallsBackToMain() {
        String yaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-cxf
                      branches:
                        - "v*"
                        - "release-*"
                      start_path: docs
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).branch()).isEqualTo("main");
    }

    @Test
    void handlesStartPathMapping() {
        String yaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-openapi-generator
                      branches: main
                      start_path: custom/docs/path
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).startPath()).isEqualTo("custom/docs/path");
    }

    @Test
    void handlesNullStartPathDefaultsToEmpty() {
        String yaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-openapi-generator
                      branches: main
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).startPath()).isEmpty();
    }

    @Test
    void handlesNullBranchesFallsBackToMain() {
        String yaml = """
                content:
                  sources:
                    - url: https://github.com/quarkiverse/quarkus-openapi-generator
                      start_path: docs
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).branch()).isEqualTo("main");
    }

    @Test
    void skipsSourcesWithInvalidUrl() {
        String yaml = """
                content:
                  sources:
                    - url: not-a-github-url
                      branches: main
                      start_path: docs
                    - url: https://github.com/quarkiverse/quarkus-openapi-generator
                      branches: main
                      start_path: docs
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).extensionName()).isEqualTo("quarkus-openapi-generator");
    }

    @Test
    void handlesEmptySourcesList() {
        String yaml = """
                content:
                  sources: []
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).isEmpty();
    }

    @Test
    void handlesNullContent() {
        String yaml = """
                site:
                  title: Something
                """;

        List<ResolvedContentSource> result = parser.parse(yaml);

        assertThat(result).isEmpty();
    }
}
