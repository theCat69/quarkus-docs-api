package com.fvd.common.exceptions;

import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;

/**
 * Abstract base for RFC 9457 Problem Detail exception mappers.
 * Concrete subclasses must be annotated with {@code @Provider}.
 *
 * @param <T> the exception type to map
 */
public abstract class AbstractProblemDetailMapper<T extends Throwable>
        implements ExceptionMapper<T> {

    @Context
    UriInfo uriInfo;

    @Override
    public final Response toResponse(T exception) {
        String instance = uriInfo != null ? uriInfo.getPath() : "/api";
        Response.Status status = resolveStatus(exception);
        String title = resolveTitle(exception);
        ProblemDetail problem = ProblemDetail.of(
                status.getStatusCode(),
                title,
                getDetail(exception),
                instance
        );
        return Response.status(status)
                .entity(problem)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * Resolves the HTTP status for the given exception.
     * By default delegates to {@link #getStatus()}.
     * Subclasses may override to vary the status based on the exception.
     */
    protected Response.Status resolveStatus(T exception) {
        return getStatus();
    }

    /**
     * Resolves the title for the given exception.
     * By default delegates to {@link #getTitle()}.
     * Subclasses may override to vary the title based on the exception.
     */
    protected String resolveTitle(T exception) {
        return getTitle();
    }

    protected abstract Response.Status getStatus();
    protected abstract String getTitle();
    protected abstract String getDetail(T exception);
}
