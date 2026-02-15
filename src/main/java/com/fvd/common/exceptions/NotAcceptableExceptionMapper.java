package com.fvd.common.exceptions;

import jakarta.ws.rs.NotAcceptableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Jakarta NotAcceptableException to RFC 7807 Problem Details response with 406 Not Acceptable.
 */
@Provider
public class NotAcceptableExceptionMapper extends AbstractProblemDetailMapper<NotAcceptableException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.NOT_ACCEPTABLE;
    }

    @Override
    protected String getTitle() {
        return "Not Acceptable";
    }

    @Override
    protected String getDetail(NotAcceptableException exception) {
        return "The requested media type is not supported. Supported: application/json";
    }
}
