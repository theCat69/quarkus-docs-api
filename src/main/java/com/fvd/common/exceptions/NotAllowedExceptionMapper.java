package com.fvd.common.exceptions;

import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Jakarta NotAllowedException to RFC 7807 Problem Details response with 405 Method Not Allowed.
 */
@Provider
public class NotAllowedExceptionMapper extends AbstractProblemDetailMapper<NotAllowedException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.METHOD_NOT_ALLOWED;
    }

    @Override
    protected String getTitle() {
        return "Method Not Allowed";
    }

    @Override
    protected String getDetail(NotAllowedException exception) {
        return "HTTP method not allowed on this resource";
    }
}
