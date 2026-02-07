package com.fvd.common.resources;

public class ErrorResponse {

    public int status;
    public String message;

    public ErrorResponse() {
    }

    public ErrorResponse(int status, String message) {
        this.status = status;
        this.message = message;
    }
}
