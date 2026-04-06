package com.fvd.common.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fvd.api.dto.ChunkResult;
import com.fvd.api.dto.ChunkSearchResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FieldSelectionFilterTest {

    private FieldSelectionFilter filter;
    private ObjectMapper objectMapper;
    private ContainerRequestContext request;
    private ContainerResponseContext response;
    private UriInfo uriInfo;
    private MultivaluedMap<String, String> queryParams;
    private MultivaluedMap<String, Object> responseHeaders;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        SimpleFilterProvider filterProvider = new SimpleFilterProvider()
                .addFilter("fieldSelector", SimpleBeanPropertyFilter.serializeAll())
                .setFailOnUnknownId(false);
        objectMapper.setFilterProvider(filterProvider);

        filter = new FieldSelectionFilter(objectMapper);

        request = mock(ContainerRequestContext.class);
        response = mock(ContainerResponseContext.class);
        uriInfo = mock(UriInfo.class);
        queryParams = new MultivaluedHashMap<>();
        responseHeaders = new MultivaluedHashMap<>();

        when(request.getUriInfo()).thenReturn(uriInfo);
        when(uriInfo.getQueryParameters()).thenReturn(queryParams);
        when(response.getHeaders()).thenReturn(responseHeaders);
    }

    @Test
    void shouldNotModifyEntityWhenFieldsParamAbsent() {
        when(response.getStatus()).thenReturn(200);

        filter.filter(request, response);

        verify(response, never()).setEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotModifyEntityWhenFieldsParamBlank() {
        queryParams.putSingle("fields", "   ");
        when(response.getStatus()).thenReturn(200);

        filter.filter(request, response);

        verify(response, never()).setEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotModifyEntityWhenStatusIsError() {
        queryParams.putSingle("fields", "title,page");
        when(response.getStatus()).thenReturn(400);

        filter.filter(request, response);

        verify(response, never()).setEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotModifyEntityWhenStatusIs500() {
        queryParams.putSingle("fields", "title,page");
        when(response.getStatus()).thenReturn(500);

        filter.filter(request, response);

        verify(response, never()).setEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotModifyEntityWhenEntityIsNull() {
        queryParams.putSingle("fields", "title,page");
        when(response.getStatus()).thenReturn(200);
        when(response.getEntity()).thenReturn(null);

        filter.filter(request, response);

        verify(response, never()).setEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldSerializeWithOnlyRequestedFieldsForDirectDto() throws Exception {
        queryParams.putSingle("fields", "title,page");
        ChunkResult entity = ChunkResult.builder()
                .id("chunk-1").page("test.adoc").title("Test Title")
                .section("Overview").summary("A summary")
                .extensions(List.of()).topics(List.of("test"))
                .score(10.0).url("https://example.com").build();
        when(response.getStatus()).thenReturn(200);
        when(response.getEntity()).thenReturn(entity);

        filter.filter(request, response);

        verify(response).setEntity(org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    void shouldSerializeOnlyRequestedFieldsContent() throws Exception {
        queryParams.putSingle("fields", "title,page");
        ChunkResult entity = ChunkResult.builder()
                .id("chunk-1").page("test.adoc").title("Test Title")
                .section("Overview").summary("A summary")
                .extensions(List.of()).topics(List.of("test"))
                .score(10.0).url("https://example.com").build();
        when(response.getStatus()).thenReturn(200);
        when(response.getEntity()).thenReturn(entity);

        org.mockito.ArgumentCaptor<Object> entityCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);

        filter.filter(request, response);

        verify(response).setEntity(entityCaptor.capture());
        byte[] jsonBytes = (byte[]) entityCaptor.getValue();
        String json = new String(jsonBytes);

        assertThat(json).contains("\"title\"");
        assertThat(json).contains("\"page\"");
        assertThat(json).doesNotContain("\"section\"");
        assertThat(json).doesNotContain("\"summary\"");
        assertThat(json).doesNotContain("\"score\"");
        assertThat(json).doesNotContain("\"extensions\"");
        assertThat(json).doesNotContain("\"topics\"");
    }

    @Test
    void shouldPreserveEnvelopeFieldsForPaginatedResponse() throws Exception {
        queryParams.putSingle("fields", "title");
        ChunkResult item = ChunkResult.builder()
                .id("chunk-1").page("test.adoc").title("Test Title")
                .section("Overview").summary("A summary")
                .extensions(List.of()).topics(List.of("test"))
                .score(10.0).url("https://example.com").build();
        ChunkSearchResponse entity = ChunkSearchResponse.builder()
                .results(List.of(item))
                .total(1)
                .limit(20)
                .offset(0)
                .build();
        when(response.getStatus()).thenReturn(200);
        when(response.getEntity()).thenReturn(entity);

        org.mockito.ArgumentCaptor<Object> entityCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);

        filter.filter(request, response);

        verify(response).setEntity(entityCaptor.capture());
        byte[] jsonBytes = (byte[]) entityCaptor.getValue();
        String json = new String(jsonBytes);

        // Envelope fields should be present
        assertThat(json).contains("\"results\"");
        assertThat(json).contains("\"total\"");
        assertThat(json).contains("\"limit\"");
        // Only requested field on items
        assertThat(json).contains("\"title\"");
        // Filtered out fields should not be present
        assertThat(json).doesNotContain("\"section\"");
        assertThat(json).doesNotContain("\"summary\"");
        assertThat(json).doesNotContain("\"extensions\"");
    }

    @Test
    void shouldSetContentTypeToApplicationJson() {
        queryParams.putSingle("fields", "title,page");
        ChunkResult entity = ChunkResult.builder()
                .id("chunk-1").page("test.adoc").title("Test Title")
                .section("Overview").summary("A summary")
                .extensions(List.of()).topics(List.of("test"))
                .score(10.0).url("https://example.com").build();
        when(response.getStatus()).thenReturn(200);
        when(response.getEntity()).thenReturn(entity);

        filter.filter(request, response);

        assertThat(responseHeaders.getFirst("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void shouldNotModifyEntityWhenFieldsParseToEmptySet() {
        queryParams.putSingle("fields", ",,,");
        when(response.getStatus()).thenReturn(200);
        ChunkResult entity = new ChunkResult();
        when(response.getEntity()).thenReturn(entity);

        filter.filter(request, response);

        verify(response, never()).setEntity(org.mockito.ArgumentMatchers.any(byte[].class));
    }
}
