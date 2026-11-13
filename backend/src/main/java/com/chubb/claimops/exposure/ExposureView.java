package com.chubb.claimops.exposure;

import java.math.BigDecimal;
import java.time.Instant;

/** Outstanding liability for one market, claim type and currency. */
public record ExposureView(String market, String claimType, String currency, int openClaims,
                           BigDecimal outstandingAmount, BigDecimal settledAmount, Instant updatedAt) {
}
