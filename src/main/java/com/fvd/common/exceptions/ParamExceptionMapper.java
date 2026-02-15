package com.fvd.common.exceptions;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Jakarta BadRequestException (including query parameter type coercion failures)
 * to RFC 7807 Problem Details response with 400 Bad Request.
 */
@Provider
public class ParamExceptionMapper extends AbstractProblemDetailMapper<BadRequestException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.BAD_REQUEST;
    }

    @Override
    protected String getTitle() {
        return "Bad Request";
    }

    @Override
    protected String getDetail(BadRequestException exception) {
        return "Invalid request parameter";
    }
}
