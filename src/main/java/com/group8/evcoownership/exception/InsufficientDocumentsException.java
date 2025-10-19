package com.group8.evcoownership.exception;

public class InsufficientDocumentsException extends RuntimeException {

    public InsufficientDocumentsException(String message) {
        super(message);
    }

    public InsufficientDocumentsException(String message, Throwable cause) {
        super(message, cause);
    }
}
