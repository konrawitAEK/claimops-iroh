package com.chubb.claimops.claim.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Claim lifecycle and its allowed transitions.
 *
 * <pre>
 * SUBMITTED ──▶ IN_REVIEW ──▶ SETTLED
 *                 │  ▲    └──▶ REJECTED
 *                 ▼  │
 *            INFO_REQUESTED
 * </pre>
 *
 * <p>The transition table lives on the enum so the rules can be read and tested in one place. {@link Claim}
 * checks it before every change, so no code path can move a claim along an edge that isn't listed here.
 */
public enum ClaimStatus {
    SUBMITTED,
    IN_REVIEW,
    INFO_REQUESTED,
    SETTLED,
    REJECTED;

    /** Statuses whose claimed amount still counts towards outstanding liability exposure. */
    public static final Set<ClaimStatus> OPEN = EnumSet.of(SUBMITTED, IN_REVIEW, INFO_REQUESTED);

    public Set<ClaimStatus> allowedNext() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(IN_REVIEW);
            case IN_REVIEW -> EnumSet.of(INFO_REQUESTED, SETTLED, REJECTED);
            case INFO_REQUESTED -> EnumSet.of(IN_REVIEW);
            case SETTLED, REJECTED -> EnumSet.noneOf(ClaimStatus.class);
        };
    }

    public boolean canTransitionTo(ClaimStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
