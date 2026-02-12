package com.fvd.common.exceptions;

import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AbstractProblemDetailMapperTest {

    /**
     * Concrete test subclass to exercise the abstract mapper logic.
     */
    static class TestMapper extends AbstractProblemDetailMapper<RuntimeException> {

        @Override
        protected Response.Status getStatus() {
            return Response.Status.BAD_REQUEST;
        }

        @Override
        protected String getTitle() {
            return "Test Title";
        }

        @Override
        protected String getDetail(RuntimeException exception) {
            return exception.getMessage();
        }
    }

    @Test
    void shouldReturnCorrectStatusCode() {
        TestMapper mapper = new TestMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/test");
        mapper.uriInfo = uriInfo;

        Response response = mapper.toResponse(new RuntimeException("bad input"));

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void shouldReturnCorrectTitle() {
        TestMapper mapper = new TestMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/test");
        mapper.uriInfo = uriInfo;

        Response response = mapper.toResponse(new RuntimeException("bad input"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.title).isEqualTo("Test Title");
    }

    @Test
    void shouldReturnDetailFromException() {
        TestMapper mapper = new TestMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/test");
        mapper.uriInfo = uriInfo;

        Response response = mapper.toResponse(new RuntimeException("something went wrong"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.detail).isEqualTo("something went wrong");
    }

    @Test
    void shouldReturnInstanceFromUriInfo() {
        TestMapper mapper = new TestMapper();
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath()).thenReturn("api/documents");
        mapper.uriInfo = uriInfo;

        Response response = mapper.toResponse(new RuntimeException("error"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.instance).isEqualTo("api/documents");
    }

    @Test
    void shouldFallbackToApiWhenUriInfoIsNull() {
        TestMapper mapper = new TestMapper();
        mapper.uriInfo = null;

        Response response = mapper.toResponse(new RuntimeException("error"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.instance).isEqualTo("/api");
    }

    @Test
    void shouldReturnAboutBlankType() {
        TestMapper mapper = new TestMapper();
        mapper.uriInfo = null;

        Response response = mapper.toResponse(new RuntimeException("error"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.type).isEqualTo("about:blank");
    }

    @Test
    void shouldReturnTimestamp() {
        TestMapper mapper = new TestMapper();
        mapper.uriInfo = null;

        Response response = mapper.toResponse(new RuntimeException("error"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.timestamp).isNotNull();
        assertThat(problem.timestamp).isNotEmpty();
    }

    @Test
    void shouldReturnJsonMediaType() {
        TestMapper mapper = new TestMapper();
        mapper.uriInfo = null;

        Response response = mapper.toResponse(new RuntimeException("error"));

        assertThat(response.getMediaType().toString()).isEqualTo("application/json");
    }

    @Test
    void shouldReturnStatusCodeInProblemDetail() {
        TestMapper mapper = new TestMapper();
        mapper.uriInfo = null;

        Response response = mapper.toResponse(new RuntimeException("error"));
        ProblemDetail problem = (ProblemDetail) response.getEntity();

        assertThat(problem.status).isEqualTo(400);
    }
}
