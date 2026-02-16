package com.fvd.common.exceptions;

import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotSupportedExceptionMapperTest {

    private NotSupportedExceptionMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new NotSupportedExceptionMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/documents/batch");
        mapper.uriInfo = uriInfo;
    }

    @Test
    void shouldReturnUnsupportedMediaTypeStatus() {
        Response response = mapper.toResponse(new NotSupportedException("text/plain is not supported"));

        assertThat(response.getStatus()).isEqualTo(415);
    }

    @Test
    void shouldReturnUnsupportedMediaTypeTitle() {
        Response response = mapper.toResponse(new NotSupportedException("text/plain is not supported"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.title).isEqualTo("Unsupported Media Type");
    }

    @Test
    void shouldReturnStaticDetailWithSupportedType() {
        Response response = mapper.toResponse(new NotSupportedException("text/plain is not supported"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.detail).isEqualTo("The request content type is not supported. Supported: application/json");
    }

    @Test
    void shouldIncludeInstancePath() {
        Response response = mapper.toResponse(new NotSupportedException("text/plain is not supported"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.instance).isEqualTo("api/documents/batch");
    }

    @Test
    void shouldReturnAboutBlankType() {
        Response response = mapper.toResponse(new NotSupportedException("text/plain is not supported"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.type).isEqualTo("about:blank");
    }

    @Test
    void shouldReturnTimestamp() {
        Response response = mapper.toResponse(new NotSupportedException("text/plain is not supported"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.timestamp).isNotNull();
        assertThat(problem.timestamp).isNotEmpty();
    }

    @Test
    void shouldReturnJsonMediaType() {
        Response response = mapper.toResponse(new NotSupportedException("text/plain is not supported"));

        assertThat(response.getMediaType().toString()).isEqualTo("application/json");
    }

    @Test
    void shouldReturn415StatusCodeInProblemDetail() {
        Response response = mapper.toResponse(new NotSupportedException("text/plain is not supported"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.status).isEqualTo(415);
    }
}
