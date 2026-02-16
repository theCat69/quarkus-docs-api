package com.fvd.common.exceptions;

import com.fasterxml.jackson.core.JsonParseException;
import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonProcessingExceptionMapperTest {

    private JsonProcessingExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new JsonProcessingExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/documents/batch");
        mapper.uriInfo = uriInfo;
    }

    @Test
    void shouldReturnBadRequestStatus() {
        Response response = mapper.toResponse(new JsonParseException(null, "Unexpected character"));

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void shouldReturnBadRequestTitle() {
        Response response = mapper.toResponse(new JsonParseException(null, "Unexpected character"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.title).isEqualTo("Bad Request");
    }

    @Test
    void shouldReturnStaticDetailMessage() {
        Response response = mapper.toResponse(new JsonParseException(null, "Unexpected character"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.detail).isEqualTo("Invalid JSON request body");
    }

    @Test
    void shouldIncludeInstancePath() {
        Response response = mapper.toResponse(new JsonParseException(null, "Unexpected character"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.instance).isEqualTo("api/documents/batch");
    }

    @Test
    void shouldReturnAboutBlankType() {
        Response response = mapper.toResponse(new JsonParseException(null, "Unexpected character"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.type).isEqualTo("about:blank");
    }

    @Test
    void shouldReturnTimestamp() {
        Response response = mapper.toResponse(new JsonParseException(null, "Unexpected character"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.timestamp).isNotNull();
        assertThat(problem.timestamp).isNotEmpty();
    }

    @Test
    void shouldReturnJsonMediaType() {
        Response response = mapper.toResponse(new JsonParseException(null, "Unexpected character"));

        assertThat(response.getMediaType().toString()).isEqualTo("application/json");
    }
}
