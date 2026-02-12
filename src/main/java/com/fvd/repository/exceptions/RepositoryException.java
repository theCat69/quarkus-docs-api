package com.fvd.repository.exceptions;

/**
 * Exception thrown when a repository operation fails.
 * Used to wrap database-level exceptions with meaningful context.
 */
public class RepositoryException extends RuntimeException {

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
