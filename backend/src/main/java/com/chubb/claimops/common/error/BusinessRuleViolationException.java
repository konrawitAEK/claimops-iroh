package com.chubb.claimops.common.error;

/** The request is well-formed but breaks a business rule, such as settling above the claimed amount. Maps to 422. */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
