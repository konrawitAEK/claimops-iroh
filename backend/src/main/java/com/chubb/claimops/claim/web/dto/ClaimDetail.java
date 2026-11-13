package com.chubb.claimops.claim.web.dto;

import com.chubb.claimops.claim.domain.Claim;
import com.chubb.claimops.claim.domain.ClaimStatus;
import com.chubb.claimops.claim.domain.ClaimType;
import com.chubb.claimops.claim.domain.Currency;
import com.chubb.claimops.claim.domain.Market;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A full claim with its information requests. {@code version} is the optimistic-lock version, which helps a
 * client tell whether the claim it's showing is stale. It is {@code null} only before the claim is first saved.
 */
public record ClaimDetail(UUID id, String claimNumber, String claimantName, String policyNumber, ClaimType claimType,
                          Market market, Currency currency, LocalDate incidentDate, String incidentDescription,
                          Map<String, String> incidentDetails,
                          BigDecimal claimedAmount, BigDecimal settledAmount, String decisionNote, ClaimStatus status,
                          String assignedHandler, Instant submittedAt, Instant updatedAt, Instant decidedAt,
                          Long version, List<InfoRequestView> infoRequests) {

    public static ClaimDetail from(Claim claim) {
        return new ClaimDetail(claim.getId(), claim.getClaimNumber(), claim.getClaimantName(),
                claim.getPolicyNumber(), claim.getClaimType(), claim.getMarket(), claim.getCurrency(),
                claim.getIncidentDate(), claim.getIncidentDescription(), claim.getIncidentDetails(), claim.getClaimedAmount(),
                claim.getSettledAmount(), claim.getDecisionNote(), claim.getStatus(), claim.getAssignedHandler(),
                claim.getSubmittedAt(), claim.getUpdatedAt(), claim.getDecidedAt(), claim.getVersion(),
                claim.getInfoRequests().stream().map(InfoRequestView::from).toList());
    }
}
