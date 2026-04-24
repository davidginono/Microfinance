package com.sacco.mvp.integration.memberportal;

/**
 * Raised when the memberportal loan-payment-transactions endpoint cannot be
 * reached or returns a non-2xx response. The monthly sync scheduler catches
 * this per loan so one bad loan does not stop the batch.
 */
public class LoanPaymentLookupException extends RuntimeException {
    public LoanPaymentLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
