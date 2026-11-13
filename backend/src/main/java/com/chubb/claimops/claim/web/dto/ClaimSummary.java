package com.chubb.claimops.claim.web.dto;

import com.chubb.claimops.claim.domain.Claim;
import com.chubb.claimops.claim.domain.ClaimStatus;
import com.chubb.claimops.claim.domain.ClaimType;
import com.chubb.claimops.claim.domain.Currency;
import com.chubb.claimops.claim.domain.Market;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One row in a claim list. */
public record ClaimSummary(UUID id, String claimNumber, String claimantName, ClaimType claimType, Market market,
                           Currency currency, BigDecimal claimedAmount, ClaimStatus status, String assignedHandler,
                           Instant submittedAt, Instant updatedAt) {

    public static ClaimSummary from(Claim claim) {
        return new ClaimSummary(claim.getId(), claim.getClaimNumber(), claim.getClaimantName(),
                claim.getClaimType(), claim.getMarket(), claim.getCurrency(), claim.getClaimedAmount(),
                claim.getStatus(), claim.getAssignedHandler(), claim.getSubmittedAt(), claim.getUpdatedAt());
    }
}
