package com.fvd.api.services;

import com.fvd.api.dto.MetaResponse;
import com.fvd.api.dto.meta.EndpointMeta;
import com.fvd.api.dto.meta.ParameterMeta;
import com.fvd.cache.services.CacheService;
import com.fvd.common.SearchConstants;
import com.fvd.common.validators.InputValidator;
import com.fvd.subject.services.SubjectDeriver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetaServiceTest {

    @Mock
    private SubjectDeriver subjectDeriver;

    @Mock
    private CacheService cacheService;

    private MetaService metaService;

    @BeforeEach
    void setUp() {
        when(subjectDeriver.getValidSubjectNames()).thenReturn(
                Set.of("security", "core-concepts", "data-persistence"));
        when(cacheService.listCachedVersions()).thenReturn(List.of("main", "3.27"));
        metaService = new MetaService(subjectDeriver, cacheService);
    }

    @Test
    void shouldReturnAllEndpoints() {
        MetaResponse response = metaService.getCapabilities();

        assertThat(response.endpoints).hasSize(8);
        List<String> paths = response.endpoints.stream()
                .map(e -> e.path)
                .toList();
        assertThat(paths).containsExactly(
                "/api/meta",
                "/api/catalog",
                "/api/search",
                "/api/documents",
                "/api/code-samples",
                "/api/search/syntax",
                "/api/documents/batch",
                "/api/documents/related"
        );
    }

    @Test
    void shouldReturnCorrectMethodAndPathForEachEndpoint() {
        MetaResponse response = metaService.getCapabilities();

        for (EndpointMeta endpoint : response.endpoints) {
            assertThat(endpoint.method).isNotBlank();
            assertThat(endpoint.path).isNotBlank();
        }
        // Verify specific methods
        assertThat(findEndpoint(response, "/api/meta").method).isEqualTo("GET");
        assertThat(findEndpoint(response, "/api/catalog").method).isEqualTo("GET");
        assertThat(findEndpoint(response, "/api/search").method).isEqualTo("GET");
        assertThat(findEndpoint(response, "/api/documents").method).isEqualTo("GET");
        assertThat(findEndpoint(response, "/api/code-samples").method).isEqualTo("GET");
        assertThat(findEndpoint(response, "/api/search/syntax").method).isEqualTo("GET");
        assertThat(findEndpoint(response, "/api/documents/batch").method).isEqualTo("POST");
        assertThat(findEndpoint(response, "/api/documents/related").method).isEqualTo("GET");
    }

    @Test
    void shouldReturnNonEmptyDescriptionsForAllEndpoints() {
        MetaResponse response = metaService.getCapabilities();

        for (EndpointMeta endpoint : response.endpoints) {
            assertThat(endpoint.summary).isNotBlank();
            assertThat(endpoint.description).isNotBlank();
        }
    }

    @Test
    void shouldMarkKeywordsAsRequiredOnSearchEndpoint() {
        MetaResponse response = metaService.getCapabilities();

        EndpointMeta search = findEndpoint(response, "/api/search");
        Optional<ParameterMeta> keywords = findParameter(search, "keywords");
        assertThat(keywords).isPresent();
        assertThat(keywords.get().required).isTrue();
    }

    @Test
    void shouldMarkKeywordsAsRequiredOnCodeSamplesEndpoint() {
        MetaResponse response = metaService.getCapabilities();

        EndpointMeta codeSamples = findEndpoint(response, "/api/code-samples");
        Optional<ParameterMeta> keywords = findParameter(codeSamples, "keywords");
        assertThat(keywords).isPresent();
        assertThat(keywords.get().required).isTrue();
    }

    @Test
    void shouldIncludeAllDocumentEndpointParameters() {
        MetaResponse response = metaService.getCapabilities();

        EndpointMeta documents = findEndpoint(response, "/api/documents");
        List<String> paramNames = documents.parameters.stream()
                .map(p -> p.name)
                .toList();
        assertThat(paramNames).contains("path", "keywords", "subject", "extension",
                "limit", "offset", "brief");
    }

    @Test
    void shouldIncludeLanguageParameterOnCodeSamples() {
        MetaResponse response = metaService.getCapabilities();

        EndpointMeta codeSamples = findEndpoint(response, "/api/code-samples");
        Optional<ParameterMeta> language = findParameter(codeSamples, "language");
        assertThat(language).isPresent();
        assertThat(language.get().type).isEqualTo("string");
        assertThat(language.get().required).isFalse();
    }

    @Test
    void shouldReturnCorrectPaginationConstraints() {
        MetaResponse response = metaService.getCapabilities();

        assertThat(response.pagination.defaultLimit).isEqualTo(SearchConstants.DEFAULT_LIMIT);
        assertThat(response.pagination.maxLimit).isEqualTo(SearchConstants.MAX_LIMIT);
        assertThat(response.pagination.defaultOffset).isEqualTo(SearchConstants.DEFAULT_OFFSET);
    }

    @Test
    void shouldReturnSupportedSearchFeatures() {
        MetaResponse response = metaService.getCapabilities();

        assertThat(response.searchSyntax.supportedFeatures).isNotEmpty();
        assertThat(response.searchSyntax.supportedFeatures).anyMatch(f -> f.contains("Stemming"));
        assertThat(response.searchSyntax.supportedFeatures).anyMatch(f -> f.contains("Prefix matching"));
    }

    @Test
    void shouldReturnUnsupportedSearchFeatures() {
        MetaResponse response = metaService.getCapabilities();

        assertThat(response.searchSyntax.unsupportedFeatures).isNotEmpty();
        assertThat(response.searchSyntax.unsupportedFeatures).anyMatch(f -> f.contains("Phrase search"));
        assertThat(response.searchSyntax.unsupportedFeatures).anyMatch(f -> f.contains("Boolean operators"));
    }

    @Test
    void shouldReturnSearchTips() {
        MetaResponse response = metaService.getCapabilities();

        assertThat(response.searchSyntax.tips).isNotEmpty();
    }

    @Test
    void shouldReturnSubjectsFromSubjectDeriver() {
        MetaResponse response = metaService.getCapabilities();

        assertThat(response.filters.subjects).containsExactly(
                "core-concepts", "data-persistence", "security");
    }

    @Test
    void shouldReturnVersionsFromCacheService() {
        MetaResponse response = metaService.getCapabilities();

        assertThat(response.filters.versions).containsExactly("main", "3.27");
    }

    @Test
    void shouldReturnDefaultVersionAsMain() {
        MetaResponse response = metaService.getCapabilities();

        assertThat(response.apiInfo.defaultVersion).isEqualTo(InputValidator.DEFAULT_VERSION);
    }

    @Test
    void shouldReturnExtensionsNote() {
        MetaResponse response = metaService.getCapabilities();

        assertThat(response.filters.extensionsNote).isNotBlank();
    }

    @Test
    void shouldReturnLimitConstraintsOnSearchEndpoints() {
        MetaResponse response = metaService.getCapabilities();

        EndpointMeta search = findEndpoint(response, "/api/search");
        Optional<ParameterMeta> limit = findParameter(search, "limit");
        assertThat(limit).isPresent();
        assertThat(limit.get().constraints).isNotNull();
        assertThat(limit.get().constraints.min).isEqualTo(1);
        assertThat(limit.get().constraints.max).isEqualTo(SearchConstants.MAX_LIMIT);
    }

    @Test
    void shouldReturnOffsetConstraintsOnSearchEndpoints() {
        MetaResponse response = metaService.getCapabilities();

        EndpointMeta search = findEndpoint(response, "/api/search");
        Optional<ParameterMeta> offset = findParameter(search, "offset");
        assertThat(offset).isPresent();
        assertThat(offset.get().constraints).isNotNull();
        assertThat(offset.get().constraints.min).isEqualTo(0);
    }

    @Test
    void shouldReturnVersionPatternConstraint() {
        MetaResponse response = metaService.getCapabilities();

        EndpointMeta catalog = findEndpoint(response, "/api/catalog");
        Optional<ParameterMeta> version = findParameter(catalog, "version");
        assertThat(version).isPresent();
        assertThat(version.get().constraints).isNotNull();
        assertThat(version.get().constraints.pattern).isEqualTo("[a-zA-Z0-9._/-]+");
    }

    @Test
    void shouldReturnMetaEndpointWithEmptyParameters() {
        MetaResponse response = metaService.getCapabilities();

        EndpointMeta meta = findEndpoint(response, "/api/meta");
        assertThat(meta.parameters).isEmpty();
    }

    private EndpointMeta findEndpoint(MetaResponse response, String path) {
        return response.endpoints.stream()
                .filter(e -> e.path.equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Endpoint not found: " + path));
    }

    private Optional<ParameterMeta> findParameter(EndpointMeta endpoint, String name) {
        return endpoint.parameters.stream()
                .filter(p -> p.name.equals(name))
                .findFirst();
    }
}
