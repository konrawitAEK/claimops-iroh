package com.chubb.claimops.claim.event;

/** Domain events published to the {@code chubb.claims.events} topic. */
public enum ClaimEventType {
    /** A claimant submitted a new claim. Adds to outstanding exposure. */
    CLAIM_SUBMITTED,
    /** The claim moved between non-terminal statuses (picked up, info requested, info provided). */
    CLAIM_STATUS_CHANGED,
    /** The claim reached a terminal status (SETTLED or REJECTED). Removes it from outstanding exposure. */
    CLAIM_DECIDED
}
