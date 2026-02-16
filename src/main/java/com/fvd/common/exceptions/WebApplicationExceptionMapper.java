package com.fvd.common.exceptions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fvd.common.resources.ProblemDetail;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps WebApplicationException to RFC 7807 Problem Details response.
 * When the cause is a JsonProcessingException (e.g., malformed request body),
 * returns 400 Bad Request with a safe detail message.
 * Otherwise, preserves the original status from the WebApplicationException.
 */
@Provider
@Slf4j
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException exception) {
        String instance = uriInfo != null ? uriInfo.getPath() : "/api";

        if (exception.getCause() instanceof JsonProcessingException jpe) {
            log.debug("Invalid JSON request body: {}", jpe.getOriginalMessage());
            ProblemDetail problem = ProblemDetail.of(
                    Response.Status.BAD_REQUEST.getStatusCode(),
                    "Bad Request",
                    "Invalid JSON request body",
                    instance
            );
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(problem)
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        Response.StatusType statusInfo = exception.getResponse().getStatusInfo();
        log.debug("WebApplicationException: status={}, message={}",
                statusInfo.getStatusCode(), exception.getMessage());
        ProblemDetail problem = ProblemDetail.of(
                statusInfo.getStatusCode(),
                statusInfo.getReasonPhrase(),
                statusInfo.getReasonPhrase(),
                instance
        );
        return Response.status(statusInfo.getStatusCode())
                .entity(problem)
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
