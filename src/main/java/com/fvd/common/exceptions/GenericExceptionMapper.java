package com.fvd.common.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Generic exception mapper for unhandled exceptions.
 * Returns RFC 7807 Problem Details response with 500 Internal Server Error.
 */
@Provider
@Slf4j
public class GenericExceptionMapper extends AbstractProblemDetailMapper<Exception> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.INTERNAL_SERVER_ERROR;
    }

    @Override
    protected String getTitle() {
        return "Internal Server Error";
    }

    @Override
    protected String getDetail(Exception exception) {
        log.error("Unhandled exception", exception);
        return "An unexpected error occurred";
    }
}
