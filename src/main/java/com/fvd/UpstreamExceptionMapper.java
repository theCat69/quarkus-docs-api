package com.fvd;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UpstreamExceptionMapper implements ExceptionMapper<UpstreamException> {

    @Override
    public Response toResponse(UpstreamException exception) {
        return Response.status(502)
                .entity(new ErrorResponse(502, exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
