package com.fvd;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DocNotFoundExceptionMapper implements ExceptionMapper<DocNotFoundException> {

    @Override
    public Response toResponse(DocNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(404, exception.getMessage()))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
