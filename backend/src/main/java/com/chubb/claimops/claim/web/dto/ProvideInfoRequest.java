package com.chubb.claimops.claim.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /api/claimant/claims/{id}/info-responses}. */
public record ProvideInfoRequest(@NotBlank @Size(max = 2000) String answer) {
}
