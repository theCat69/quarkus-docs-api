package com.fvd.common.exceptions;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps Jakarta NotFoundException to RFC 7807 Problem Details response.
 * Returns 400 Bad Request when the cause is a parameter coercion failure
 * (e.g., non-numeric value for an Integer query parameter),
 * otherwise returns 404 Not Found.
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
        if (isParamCoercionFailure(exception)) {
            return "Invalid value for query parameter";
        }
        return "Resource not found: " + (uriInfo != null ? uriInfo.getPath() : "/api");
    }

    @Override
    protected Response.Status resolveStatus(NotFoundException exception) {
        if (isParamCoercionFailure(exception)) {
            return Response.Status.BAD_REQUEST;
        }
        return getStatus();
    }

    @Override
    protected String resolveTitle(NotFoundException exception) {
        if (isParamCoercionFailure(exception)) {
            return "Bad Request";
        }
        return getTitle();
    }

    private boolean isParamCoercionFailure(NotFoundException exception) {
        Throwable cause = exception.getCause();
        int depth = 0;
        while (cause != null && depth < 5) {
            if (cause instanceof NumberFormatException
                    || cause instanceof IllegalArgumentException) {
                return true;
            }
            cause = cause.getCause();
            depth++;
        }
        return false;
    }
}
