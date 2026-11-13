package com.chubb.claimops.claim.web.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Body of {@code POST /api/staff/claims/{id}/settlement}. */
public record SettleClaimRequest(
        @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal settledAmount,
        @Size(max = 1000) String note) {
}
