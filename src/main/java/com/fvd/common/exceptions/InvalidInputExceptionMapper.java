package com.fvd.common.exceptions;

import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps InvalidInputException to RFC 7807 Problem Details response with 400 Bad Request.
 */
@Provider
public class InvalidInputExceptionMapper implements ExceptionMapper<InvalidInputException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(InvalidInputException exception) {
        String instance = uriInfo != null ? uriInfo.getPath() : "/api";
        ProblemDetail problem = ProblemDetail.of(
                400,
                "Bad Request",
                exception.getMessage(),
                instance
        );
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(problem)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
