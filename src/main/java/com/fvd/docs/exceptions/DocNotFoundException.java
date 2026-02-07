package com.fvd.docs.exceptions;

public class DocNotFoundException extends RuntimeException {

    public DocNotFoundException(String message) {
        super(message);
    }
}
