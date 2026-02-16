package com.fvd.common.exceptions;

import com.fasterxml.jackson.core.JsonParseException;
import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebApplicationExceptionMapperTest {

    private WebApplicationExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new WebApplicationExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/test");
        mapper.uriInfo = uriInfo;
    }

    @Test
    void shouldReturnBadRequestForJsonProcessingExceptionCause() {
        JsonParseException cause = new JsonParseException(null, "Unexpected character");
        WebApplicationException exception = new WebApplicationException(cause,
                Response.Status.INTERNAL_SERVER_ERROR);

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void shouldReturnStaticDetailForJsonProcessingExceptionCause() {
        JsonParseException cause = new JsonParseException(null, "Unexpected character");
        WebApplicationException exception = new WebApplicationException(cause,
                Response.Status.INTERNAL_SERVER_ERROR);

        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.detail).isEqualTo("Invalid JSON request body");
    }

    @Test
    void shouldPreserveOriginalStatusForGenericWebApplicationException() {
        WebApplicationException exception = new WebApplicationException("Service down",
                Response.Status.SERVICE_UNAVAILABLE);

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void shouldReturnSafeDetailForGenericWebApplicationException() {
        WebApplicationException exception = new WebApplicationException(
                "Internal stack trace details leaked here",
                Response.Status.SERVICE_UNAVAILABLE);

        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.detail).isEqualTo("Service Unavailable");
    }

    @Test
    void shouldReturnJsonMediaType() {
        WebApplicationException exception = new WebApplicationException(
                Response.Status.BAD_REQUEST);

        Response response = mapper.toResponse(exception);

        assertThat(response.getMediaType().toString()).isEqualTo("application/json");
    }

    @Test
    void shouldReturnTimestamp() {
        WebApplicationException exception = new WebApplicationException(
                Response.Status.NOT_FOUND);

        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.timestamp).isNotNull();
        assertThat(problem.timestamp).isNotEmpty();
    }

    @Test
    void shouldReturnAboutBlankType() {
        WebApplicationException exception = new WebApplicationException(
                Response.Status.NOT_FOUND);

        Response response = mapper.toResponse(exception);
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.type).isEqualTo("about:blank");
    }
}
