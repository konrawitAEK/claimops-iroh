package com.chubb.claimops.claim.domain;

import java.util.List;

/**
 * Lines of business the platform handles, each with the incident details it requires.
 *
 * <p>The required keys live here rather than as columns, so adding a line of business (or a field) is a change to this
 * enum and the frontend form, not a schema migration.
 */
public enum ClaimType {
    MOTOR(List.of("licensePlate", "vehicleModel")),
    PROPERTY(List.of("propertyAddress", "damageCategory"));

    private final List<String> requiredDetails;

    ClaimType(List<String> requiredDetails) {
        this.requiredDetails = requiredDetails;
    }

    public List<String> requiredDetails() {
        return requiredDetails;
    }
}
