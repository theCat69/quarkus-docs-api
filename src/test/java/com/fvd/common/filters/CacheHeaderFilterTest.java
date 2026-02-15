package com.fvd.common.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CacheHeaderFilterTest {

    private CacheHeaderFilter filter;
    private ObjectMapper objectMapper;
    private ContainerRequestContext request;
    private ContainerResponseContext response;
    private UriInfo uriInfo;
    private MultivaluedMap<String, String> queryParams;
    private MultivaluedMap<String, Object> responseHeaders;

    @BeforeEach
    void setUp() throws Exception {
        filter = new CacheHeaderFilter();
        objectMapper = new ObjectMapper();

        // Inject objectMapper via reflection
        Field omField = CacheHeaderFilter.class.getDeclaredField("objectMapper");
        omField.setAccessible(true);
        omField.set(filter, objectMapper);

        // Inject config properties via reflection
        setField("maxAgeVersioned", 3600);
        setField("maxAgeMain", 900);
        setField("maxAgeCatalog", 1800);

        request = mock(ContainerRequestContext.class);
        response = mock(ContainerResponseContext.class);
        uriInfo = mock(UriInfo.class);
        queryParams = new MultivaluedHashMap<>();
        responseHeaders = new MultivaluedHashMap<>();

        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(response.getHeaders()).thenReturn(responseHeaders);
    }

    private void setField(String name, int value) throws Exception {
        Field field = CacheHeaderFilter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(filter, value);
    }

    // --- Cache-Control max-age tests ---

    @Test
    void shouldSetVersionedMaxAgeForExplicitVersion() {
        setupGetRequest("api/documents", 200);
        queryParams.putSingle("version", "3.27");
        when(response.getEntity()).thenReturn(new TestDto("hello"));

        filter.filter(request, response);

        assertThat(responseHeaders.getFirst("Cache-Control")).isEqualTo("public, max-age=3600");
    }

    @Test
    void shouldSetMainMaxAgeForVersionMain() {
        setupGetRequest("api/search", 200);
        queryParams.putSingle("version", "main");
        when(response.getEntity()).thenReturn(new TestDto("hello"));

        filter.filter(request, response);

        assertThat(responseHeaders.getFirst("Cache-Control")).isEqualTo("public, max-age=900");
    }

    @Test
    void shouldSetMainMaxAgeWhenNoVersionProvided() {
        setupGetRequest("api/search", 200);
        // No version query param
        when(response.getEntity()).thenReturn(new TestDto("hello"));

        filter.filter(request, response);

        assertThat(responseHeaders.getFirst("Cache-Control")).isEqualTo("public, max-age=900");
    }

    @Test
    void shouldSetCatalogMaxAgeForCatalogPath() {
        setupGetRequest("api/catalog", 200);
        queryParams.putSingle("version", "3.27");
        when(response.getEntity()).thenReturn(new TestDto("catalog"));

        filter.filter(request, response);

        assertThat(responseHeaders.getFirst("Cache-Control")).isEqualTo("public, max-age=1800");
    }

    // --- Guard clause tests ---

    @Test
    void shouldSkipNonGetRequests() {
        when(request.getMethod()).thenReturn("POST");
        when(response.getStatus()).thenReturn(200);
        when(uriInfo.getPath()).thenReturn("api/documents/batch");

        filter.filter(request, response);

        assertThat(responseHeaders.containsKey("Cache-Control")).isFalse();
        assertThat(responseHeaders.containsKey("ETag")).isFalse();
    }

    @Test
    void shouldSkipNon2xxResponses() {
        setupGetRequest("api/documents", 404);

        filter.filter(request, response);

        assertThat(responseHeaders.containsKey("Cache-Control")).isFalse();
        assertThat(responseHeaders.containsKey("ETag")).isFalse();
    }

    @Test
    void shouldSkipStatusEndpoint() {
        setupGetRequest("api/status", 200);
        when(response.getEntity()).thenReturn(new TestDto("status"));

        filter.filter(request, response);

        assertThat(responseHeaders.containsKey("Cache-Control")).isFalse();
        assertThat(responseHeaders.containsKey("ETag")).isFalse();
    }

    @Test
    void shouldSkipMetaEndpoint() {
        setupGetRequest("api/meta", 200);
        when(response.getEntity()).thenReturn(new TestDto("meta"));

        filter.filter(request, response);

        assertThat(responseHeaders.containsKey("Cache-Control")).isFalse();
        assertThat(responseHeaders.containsKey("ETag")).isFalse();
    }

    @Test
    void shouldSkipNonApiPaths() {
        setupGetRequest("q/openapi", 200);

        filter.filter(request, response);

        assertThat(responseHeaders.containsKey("Cache-Control")).isFalse();
        assertThat(responseHeaders.containsKey("ETag")).isFalse();
    }

    // --- ETag computation tests ---

    @Test
    void shouldSetETagHeaderWhenEntityPresent() {
        setupGetRequest("api/documents", 200);
        queryParams.putSingle("version", "3.27");
        when(response.getEntity()).thenReturn(new TestDto("hello"));

        filter.filter(request, response);

        Object etag = responseHeaders.getFirst("ETag");
        assertThat(etag).isNotNull();
        assertThat(etag.toString()).startsWith("\"").endsWith("\"");
        // ETag value (without quotes) should be 16 hex chars
        String etagValue = etag.toString().substring(1, etag.toString().length() - 1);
        assertThat(etagValue).hasSize(16);
        assertThat(etagValue).matches("[0-9a-f]{16}");
    }

    @Test
    void shouldComputeETagFromByteArray() throws Exception {
        setupGetRequest("api/documents", 200);
        queryParams.putSingle("version", "3.27");
        byte[] entityBytes = "{\"title\":\"hello\"}".getBytes();
        when(response.getEntity()).thenReturn(entityBytes);

        filter.filter(request, response);

        // Compute expected ETag
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(entityBytes);
        String expectedEtag = "\"" + HexFormat.of().formatHex(hash, 0, 8) + "\"";

        assertThat(responseHeaders.getFirst("ETag")).isEqualTo(expectedEtag);
    }

    @Test
    void shouldComputeETagFromJavaDto() throws Exception {
        setupGetRequest("api/documents", 200);
        queryParams.putSingle("version", "3.27");
        TestDto dto = new TestDto("hello");
        when(response.getEntity()).thenReturn(dto);

        filter.filter(request, response);

        // Compute expected ETag
        byte[] serialized = objectMapper.writeValueAsBytes(dto);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(serialized);
        String expectedEtag = "\"" + HexFormat.of().formatHex(hash, 0, 8) + "\"";

        assertThat(responseHeaders.getFirst("ETag")).isEqualTo(expectedEtag);
    }

    @Test
    void shouldNotSetETagWhenEntityIsNull() {
        setupGetRequest("api/documents", 200);
        queryParams.putSingle("version", "3.27");
        when(response.getEntity()).thenReturn(null);

        filter.filter(request, response);

        assertThat(responseHeaders.getFirst("Cache-Control")).isEqualTo("public, max-age=3600");
        assertThat(responseHeaders.containsKey("ETag")).isFalse();
    }

    // --- Conditional GET (If-None-Match) tests ---

    @Test
    void shouldReturn304WhenIfNoneMatchMatches() throws Exception {
        setupGetRequest("api/documents", 200);
        queryParams.putSingle("version", "3.27");
        TestDto dto = new TestDto("hello");
        when(response.getEntity()).thenReturn(dto);

        // Compute expected ETag
        String etag = filter.computeETag(dto);
        when(request.getHeaderString("If-None-Match")).thenReturn("\"" + etag + "\"");

        filter.filter(request, response);

        verify(response).setStatus(304);
        verify(response).setEntity(null);
        assertThat(responseHeaders.getFirst("ETag")).isEqualTo("\"" + etag + "\"");
        assertThat(responseHeaders.getFirst("Cache-Control")).isEqualTo("public, max-age=3600");
    }

    @Test
    void shouldNotReturn304WhenIfNoneMatchDoesNotMatch() {
        setupGetRequest("api/documents", 200);
        queryParams.putSingle("version", "3.27");
        when(response.getEntity()).thenReturn(new TestDto("hello"));
        when(request.getHeaderString("If-None-Match")).thenReturn("\"0000000000000000\"");

        filter.filter(request, response);

        verify(response, never()).setStatus(304);
        verify(response, never()).setEntity(null);
        assertThat(responseHeaders.getFirst("ETag")).isNotNull();
    }

    @Test
    void shouldNotReturn304WhenNoIfNoneMatchHeader() {
        setupGetRequest("api/documents", 200);
        queryParams.putSingle("version", "3.27");
        when(response.getEntity()).thenReturn(new TestDto("hello"));
        when(request.getHeaderString("If-None-Match")).thenReturn(null);

        filter.filter(request, response);

        verify(response, never()).setStatus(304);
        verify(response, never()).setEntity(null);
        assertThat(responseHeaders.getFirst("ETag")).isNotNull();
        assertThat(responseHeaders.getFirst("Cache-Control")).isEqualTo("public, max-age=3600");
    }

    // --- Helper methods ---

    private void setupGetRequest(String path, int status) {
        when(request.getMethod()).thenReturn("GET");
        when(uriInfo.getPath()).thenReturn(path);
        when(response.getStatus()).thenReturn(status);
    }

    /**
     * Simple DTO for testing ETag computation.
     */
    static class TestDto {
        public String value;

        TestDto(String value) {
            this.value = value;
        }
    }
}
