package com.group8.evcoownership.exception;

/**
 * Exception cho các conflict liên quan đến payment
 */
public class PaymentConflictException extends RuntimeException {
    public PaymentConflictException(String message) {
        super(message);
    }

    public PaymentConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
