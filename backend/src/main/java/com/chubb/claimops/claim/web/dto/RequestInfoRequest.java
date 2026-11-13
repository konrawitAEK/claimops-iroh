package com.chubb.claimops.claim.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/staff/claims/{id}/info-requests}. */
public record RequestInfoRequest(@NotBlank @Size(max = 1000) String question) {
}
