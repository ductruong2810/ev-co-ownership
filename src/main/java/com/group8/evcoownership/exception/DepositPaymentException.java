package com.group8.evcoownership.exception;

/**
 * Exception cho các lỗi liên quan đến deposit payment
 */
public class DepositPaymentException extends RuntimeException {
    public DepositPaymentException(String message) {
        super(message);
    }

    public DepositPaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}
