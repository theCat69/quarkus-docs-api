package com.fvd.common.exceptions;

import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Jakarta NotSupportedException to RFC 7807 Problem Details response with 415 Unsupported Media Type.
 */
@Provider
public class NotSupportedExceptionMapper extends AbstractProblemDetailMapper<NotSupportedException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.UNSUPPORTED_MEDIA_TYPE;
    }

    @Override
    protected String getTitle() {
        return "Unsupported Media Type";
    }

    @Override
    protected String getDetail(NotSupportedException exception) {
        return "The request content type is not supported. Supported: application/json";
    }
}
