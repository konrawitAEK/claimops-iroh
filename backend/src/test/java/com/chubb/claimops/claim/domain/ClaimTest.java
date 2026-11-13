package com.chubb.claimops.claim.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chubb.claimops.common.error.BusinessRuleViolationException;
import com.chubb.claimops.common.error.ForbiddenOperationException;
import com.chubb.claimops.common.error.InvalidStateTransitionException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** State machine and business rules of the {@link Claim} aggregate. */
class ClaimTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");
    private static final Map<String, String> MOTOR_DETAILS =
            Map.of("licensePlate", "SGX1234A", "vehicleModel", "Toyota Corolla");

    private static Claim newClaim() {
        return Claim.submit("CLM-SG-2026-000001", "alice", "POL-123", ClaimType.MOTOR, Market.SG, Currency.SGD,
                LocalDate.parse("2026-11-10"), "Rear-ended at a junction", MOTOR_DETAILS, new BigDecimal("5000.00"), NOW);
    }

    @Test
    @DisplayName("SUBMITTED → IN_REVIEW → INFO_REQUESTED → IN_REVIEW → SETTLED")
    void fullValidLifecycle() {
        Claim claim = newClaim();

        claim.pickUp("bob", NOW);
        claim.requestInfo("bob", "Police report?", NOW);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.INFO_REQUESTED);
        claim.provideInfo("alice", "Report 123", NOW);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.IN_REVIEW);
        claim.settle("bob", new BigDecimal("4200.00"), "Repair quote accepted", NOW);

        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SETTLED);
        assertThat(claim.getSettledAmount()).isEqualByComparingTo("4200.00");
    }

    @Test
    void settlingASubmittedClaimWithoutReviewIsAnInvalidTransition() {
        Claim claim = newClaim();

        assertThatThrownBy(() -> claim.settle("bob", new BigDecimal("100.00"), null, NOW))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("SUBMITTED to SETTLED");
    }

    @Test
    void aRejectedClaimCannotBeModified() {
        Claim claim = newClaim();
        claim.pickUp("bob", NOW);
        claim.reject("bob", "Policy lapsed", NOW);

        assertThatThrownBy(() -> claim.requestInfo("bob", "More?", NOW))
                .isInstanceOf(InvalidStateTransitionException.class);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.REJECTED);
    }

    /** Regression: a second officer could once "pick up" a claim waiting for the claimant and take it over. */
    @Test
    void anotherOfficerCannotTakeOverAClaimWaitingForTheClaimant() {
        Claim claim = newClaim();
        claim.pickUp("bob", NOW);
        claim.requestInfo("bob", "Photos?", NOW);

        assertThatThrownBy(() -> claim.pickUp("carol", NOW)).isInstanceOf(InvalidStateTransitionException.class);
        assertThat(claim.getAssignedHandler()).isEqualTo("bob");
    }

    @Test
    void onlyTheAssignedHandlerCanDecide() {
        Claim claim = newClaim();
        claim.pickUp("bob", NOW);

        assertThatThrownBy(() -> claim.reject("carol", "Not covered", NOW))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void settledAmountCannotExceedTheClaimedAmount() {
        Claim claim = newClaim();
        claim.pickUp("bob", NOW);

        assertThatThrownBy(() -> claim.settle("bob", new BigDecimal("5000.01"), null, NOW))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThat(claim.getStatus()).isEqualTo(ClaimStatus.IN_REVIEW);
    }

    @Test
    void aClaimMustBeInTheMarketsCurrencyOrUsd() {
        assertThatThrownBy(() -> Claim.submit("CLM-SG-2026-000002", "alice", "POL-123", ClaimType.MOTOR, Market.SG,
                Currency.THB, LocalDate.parse("2026-11-10"), "Wrong currency", MOTOR_DETAILS, new BigDecimal("1.00"), NOW))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
