package com.chubb.claimops.common.error;

/** The resource doesn't exist, or the caller isn't allowed to know that it exists. Maps to 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
