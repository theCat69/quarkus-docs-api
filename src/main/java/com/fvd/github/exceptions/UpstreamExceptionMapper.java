package com.fvd.github.exceptions;

import com.fvd.common.exceptions.AbstractProblemDetailMapper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps UpstreamException to RFC 7807 Problem Details response with 502 Bad Gateway.
 */
@Provider
public class UpstreamExceptionMapper extends AbstractProblemDetailMapper<UpstreamException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.BAD_GATEWAY;
    }

    @Override
    protected String getTitle() {
        return "Bad Gateway";
    }

    @Override
    protected String getDetail(UpstreamException exception) {
        return exception.getMessage();
    }
}
