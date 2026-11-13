package com.chubb.claimops.common.error;

/** The operation is not allowed from the claim's current status. Maps to 409. */
public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}
