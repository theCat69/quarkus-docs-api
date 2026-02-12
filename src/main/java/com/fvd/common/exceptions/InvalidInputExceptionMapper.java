package com.fvd.common.exceptions;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps InvalidInputException to RFC 7807 Problem Details response with 400 Bad Request.
 */
@Provider
public class InvalidInputExceptionMapper extends AbstractProblemDetailMapper<InvalidInputException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.BAD_REQUEST;
    }

    @Override
    protected String getTitle() {
        return "Bad Request";
    }

    @Override
    protected String getDetail(InvalidInputException exception) {
        return exception.getMessage();
    }
}
