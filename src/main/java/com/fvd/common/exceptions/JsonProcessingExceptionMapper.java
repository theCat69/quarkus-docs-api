package com.fvd.common.exceptions;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Maps Jackson JsonProcessingException to RFC 7807 Problem Details response with 400 Bad Request.
 */
@Provider
@Slf4j
public class JsonProcessingExceptionMapper extends AbstractProblemDetailMapper<JsonProcessingException> {

    @Override
    protected Response.Status getStatus() {
        return Response.Status.BAD_REQUEST;
    }

    @Override
    protected String getTitle() {
        return "Bad Request";
    }

    @Override
    protected String getDetail(JsonProcessingException exception) {
        log.debug("Invalid JSON request body: {}", exception.getOriginalMessage());
        return "Invalid JSON request body";
    }
}
