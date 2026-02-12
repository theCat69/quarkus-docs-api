package com.fvd.common.exceptions;

import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;

/**
 * Abstract base for RFC 7807 Problem Detail exception mappers.
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
        ProblemDetail problem = ProblemDetail.of(
                getStatus().getStatusCode(),
                getTitle(),
                getDetail(exception),
                instance
        );
        return Response.status(getStatus())
                .entity(problem)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    protected abstract Response.Status getStatus();
    protected abstract String getTitle();
    protected abstract String getDetail(T exception);
}
