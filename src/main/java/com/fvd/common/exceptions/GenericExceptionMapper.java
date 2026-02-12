package com.fvd.common.exceptions;

import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Generic exception mapper for unhandled exceptions.
 * Returns RFC 7807 Problem Details response with 500 Internal Server Error.
 */
@Provider
@Slf4j
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Exception exception) {
        log.error("Unhandled exception", exception);
        String instance = uriInfo != null ? uriInfo.getPath() : "/api";
        ProblemDetail problem = ProblemDetail.of(
                500,
                "Internal Server Error",
                "An unexpected error occurred",
                instance
        );
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(problem)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
