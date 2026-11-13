package com.chubb.claimops.common.error;

/** The caller is identified but may not perform this operation. Maps to 403. */
public class ForbiddenOperationException extends RuntimeException {

    public ForbiddenOperationException(String message) {
        super(message);
    }
}
