package com.chubb.claimops.claim.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/staff/claims/{id}/rejection}. */
public record RejectClaimRequest(@NotBlank @Size(max = 1000) String reason) {
}
