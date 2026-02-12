package com.fvd.github.exceptions;

import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps UpstreamException to RFC 7807 Problem Details response with 502 Bad Gateway.
 */
@Provider
public class UpstreamExceptionMapper implements ExceptionMapper<UpstreamException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(UpstreamException exception) {
        String instance = uriInfo != null ? uriInfo.getPath() : "/api";
        ProblemDetail problem = ProblemDetail.of(
                502,
                "Bad Gateway",
                exception.getMessage(),
                instance
        );
        return Response.status(502)
                .entity(problem)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
