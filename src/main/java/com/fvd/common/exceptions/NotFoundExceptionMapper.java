package com.fvd.common.exceptions;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Jakarta NotFoundException (no matching route) to RFC 7807 Problem Details response with 404.
 */
@Provider
public class NotFoundExceptionMapper extends AbstractProblemDetailMapper<NotFoundException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.NOT_FOUND;
    }

    @Override
    protected String getTitle() {
        return "Not Found";
    }

    @Override
    protected String getDetail(NotFoundException exception) {
        return "Resource not found: " + (uriInfo != null ? uriInfo.getPath() : "/api");
    }
}
