package com.fvd.docs.exceptions;

import com.fvd.common.exceptions.AbstractProblemDetailMapper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps DocNotFoundException to RFC 7807 Problem Details response with 404 Not Found.
 */
@Provider
public class DocNotFoundExceptionMapper extends AbstractProblemDetailMapper<DocNotFoundException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.NOT_FOUND;
    }

    @Override
    protected String getTitle() {
        return "Not Found";
    }

    @Override
    protected String getDetail(DocNotFoundException exception) {
        return exception.getMessage();
    }
}
