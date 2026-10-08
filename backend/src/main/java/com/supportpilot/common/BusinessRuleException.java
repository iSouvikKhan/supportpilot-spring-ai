package com.supportpilot.common;

/** A request that is well-formed but violates a business rule (HTTP 409 / tool error). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
