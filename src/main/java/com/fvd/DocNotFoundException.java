package com.fvd;

public class DocNotFoundException extends RuntimeException {

    public DocNotFoundException(String message) {
        super(message);
    }
}
