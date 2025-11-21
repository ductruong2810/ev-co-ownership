package com.group8.evcoownership.exception;

public class DuplicateDocumentException extends RuntimeException {
    public DuplicateDocumentException(String message) {
        super(message);
    }
}
