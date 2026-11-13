package com.chubb.claimops.common.error;

/** The request carries no usable identity. Maps to 401. */
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
