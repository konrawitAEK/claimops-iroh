package com.chubb.claimops.claim.event;

import com.chubb.claimops.claim.domain.Claim;
import com.chubb.claimops.claim.domain.ClaimStatus;
import com.chubb.claimops.claim.domain.ClaimType;
import com.chubb.claimops.claim.domain.Currency;
import com.chubb.claimops.claim.domain.Market;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The public contract between the claim module and its event consumers.
 *
 * <p>Each event carries a full snapshot of the fields consumers need (market, type, amounts), not just the claim
 * id. Consumers can then build their read models without calling back into the claim module. This is what keeps
 * the {@code exposure} module independent of the claim tables.
 *
 * <p>{@code eventId} is unique per event and is the idempotency key: a consumer that sees the same id twice, for
 * example after a redelivery, must apply it only once.
 */
public record ClaimEvent(
        UUID eventId,
        ClaimEventType type,
        UUID claimId,
        String claimNumber,
        Market market,
        ClaimType claimType,
        Currency currency,
        ClaimStatus previousStatus,
        ClaimStatus newStatus,
        BigDecimal claimedAmount,
        BigDecimal settledAmount,
        String actor,
        Instant occurredAt) {

    /** Builds the event for a change that moved {@code claim} from {@code previousStatus} to its current status. */
    public static ClaimEvent of(ClaimEventType type, Claim claim, ClaimStatus previousStatus, String actor, Instant now) {
        return new ClaimEvent(
                UUID.randomUUID(),
                type,
                claim.getId(),
                claim.getClaimNumber(),
                claim.getMarket(),
                claim.getClaimType(),
                claim.getCurrency(),
                previousStatus,
                claim.getStatus(),
                claim.getClaimedAmount(),
                claim.getSettledAmount(),
                actor,
                now);
    }
}
