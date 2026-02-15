package com.fvd.common.filters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fvd.api.dto.QuickSearchResponse;
import com.fvd.api.dto.SearchResultRef;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
        filter = new FieldSelectionFilter();
        objectMapper = new ObjectMapper();
        SimpleFilterProvider filterProvider = new SimpleFilterProvider()
                .addFilter("fieldSelector", SimpleBeanPropertyFilter.serializeAll())
                .setFailOnUnknownId(false);
        objectMapper.setFilterProvider(filterProvider);

        // Inject objectMapper via reflection
        Field omField = FieldSelectionFilter.class.getDeclaredField("objectMapper");
        omField.setAccessible(true);
        omField.set(filter, objectMapper);

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
        queryParams.putSingle("fields", "title,path");
        when(response.getStatus()).thenReturn(400);

        filter.filter(request, response);

        verify(response, never()).setEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotModifyEntityWhenStatusIs500() {
        queryParams.putSingle("fields", "title,path");
        when(response.getStatus()).thenReturn(500);

        filter.filter(request, response);

        verify(response, never()).setEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldNotModifyEntityWhenEntityIsNull() {
        queryParams.putSingle("fields", "title,path");
        when(response.getStatus()).thenReturn(200);
        when(response.getEntity()).thenReturn(null);

        filter.filter(request, response);

        verify(response, never()).setEntity(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldSerializeWithOnlyRequestedFieldsForDirectDto() throws Exception {
        queryParams.putSingle("fields", "title,path");
        SearchResultRef entity = new SearchResultRef("test.adoc", "Test Title",
                "security", "quarkus-core", 10.0, List.of("test"), "snippet");
        when(response.getStatus()).thenReturn(200);
        when(response.getEntity()).thenReturn(entity);

        filter.filter(request, response);

        verify(response).setEntity(org.mockito.ArgumentMatchers.any(byte[].class));
    }

    @Test
    void shouldSerializeOnlyRequestedFieldsContent() throws Exception {
        queryParams.putSingle("fields", "title,path");
        SearchResultRef entity = new SearchResultRef("test.adoc", "Test Title",
                "security", "quarkus-core", 10.0, List.of("test"), "snippet");
        when(response.getStatus()).thenReturn(200);
        when(response.getEntity()).thenReturn(entity);

        // Capture the entity set on the response
        org.mockito.ArgumentCaptor<Object> entityCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);

        filter.filter(request, response);

        verify(response).setEntity(entityCaptor.capture());
        byte[] jsonBytes = (byte[]) entityCaptor.getValue();
        String json = new String(jsonBytes);

        assertThat(json).contains("\"title\"");
        assertThat(json).contains("\"path\"");
        assertThat(json).doesNotContain("\"subject\"");
        assertThat(json).doesNotContain("\"extension\"");
        assertThat(json).doesNotContain("\"score\"");
        assertThat(json).doesNotContain("\"matchedKeywords\"");
        assertThat(json).doesNotContain("\"snippet\"");
    }

    @Test
    void shouldPreserveEnvelopeFieldsForPaginatedResponse() throws Exception {
        queryParams.putSingle("fields", "title");
        SearchResultRef item = new SearchResultRef("test.adoc", "Test Title",
                "security", "quarkus-core", 10.0, List.of("test"), "snippet");
        QuickSearchResponse entity = QuickSearchResponse.builder()
                .results(List.of(item))
                .totalCount(1)
                .returnedCount(1)
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
        assertThat(json).contains("\"totalCount\"");
        assertThat(json).contains("\"returnedCount\"");
        // Only requested field on items
        assertThat(json).contains("\"title\"");
        // Filtered out fields should not be present
        assertThat(json).doesNotContain("\"subject\"");
        assertThat(json).doesNotContain("\"extension\"");
        assertThat(json).doesNotContain("\"snippet\"");
    }

    @Test
    void shouldSetContentTypeToApplicationJson() {
        queryParams.putSingle("fields", "title,path");
        SearchResultRef entity = new SearchResultRef("test.adoc", "Test Title",
                "security", "quarkus-core", 10.0, List.of("test"), "snippet");
        when(response.getStatus()).thenReturn(200);
        when(response.getEntity()).thenReturn(entity);

        filter.filter(request, response);

        assertThat(responseHeaders.getFirst("Content-Type")).isEqualTo("application/json");
    }

    @Test
    void shouldNotModifyEntityWhenFieldsParseToEmptySet() {
        queryParams.putSingle("fields", ",,,");
        when(response.getStatus()).thenReturn(200);
        SearchResultRef entity = new SearchResultRef();
        when(response.getEntity()).thenReturn(entity);

        filter.filter(request, response);

        verify(response, never()).setEntity(org.mockito.ArgumentMatchers.any(byte[].class));
    }
}
