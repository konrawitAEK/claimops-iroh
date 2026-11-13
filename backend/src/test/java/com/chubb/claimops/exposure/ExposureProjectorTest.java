package com.chubb.claimops.exposure;

import static org.assertj.core.api.Assertions.assertThat;

import com.chubb.claimops.claim.domain.ClaimStatus;
import com.chubb.claimops.claim.domain.ClaimType;
import com.chubb.claimops.claim.domain.Currency;
import com.chubb.claimops.claim.domain.Market;
import com.chubb.claimops.claim.event.ClaimEvent;
import com.chubb.claimops.claim.event.ClaimEventType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExposureProjectorTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");

    private InMemoryExposureStore store;
    private ExposureProjector projector;

    @BeforeEach
    void setUp() {
        store = new InMemoryExposureStore();
        projector = new ExposureProjector(store);
    }

    @Test
    void claimSubmittedIncreasesOpenClaimsAndOutstandingExposure() {
        projector.apply(submitted(UUID.randomUUID(), "5000.00", Currency.SGD));
        projector.apply(submitted(UUID.randomUUID(), "1500.50", Currency.SGD));

        InMemoryExposureStore.Row row = store.row("SG", "MOTOR", "SGD");
        assertThat(row.openClaims()).isEqualTo(2);
        assertThat(row.outstanding()).isEqualByComparingTo("6500.50");
        assertThat(row.settled()).isEqualByComparingTo("0");
    }

    @Test
    void settlingRemovesTheClaimedAmountAndRecordsThePayout() {
        UUID claimId = UUID.randomUUID();
        projector.apply(submitted(claimId, "5000.00", Currency.SGD));

        projector.apply(decided(claimId, ClaimStatus.SETTLED, "5000.00", "4200.00"));

        InMemoryExposureStore.Row row = store.row("SG", "MOTOR", "SGD");
        assertThat(row.openClaims()).isZero();
        assertThat(row.outstanding()).isEqualByComparingTo("0");
        assertThat(row.settled()).isEqualByComparingTo("4200.00");
    }

    @Test
    void rejectingRemovesTheClaimedAmountWithNoPayout() {
        UUID claimId = UUID.randomUUID();
        projector.apply(submitted(claimId, "5000.00", Currency.SGD));

        projector.apply(decided(claimId, ClaimStatus.REJECTED, "5000.00", null));

        InMemoryExposureStore.Row row = store.row("SG", "MOTOR", "SGD");
        assertThat(row.openClaims()).isZero();
        assertThat(row.outstanding()).isEqualByComparingTo("0");
        assertThat(row.settled()).isEqualByComparingTo("0");
    }


    @Test
    void aRedeliveredEventIsAppliedOnlyOnce() {
        ClaimEvent event = submitted(UUID.randomUUID(), "5000.00", Currency.SGD);

        boolean first = projector.apply(event);
        boolean redelivery = projector.apply(event);

        assertThat(first).isTrue();
        assertThat(redelivery).isFalse();
        assertThat(store.row("SG", "MOTOR", "SGD").openClaims()).isEqualTo(1);
        assertThat(store.row("SG", "MOTOR", "SGD").outstanding()).isEqualByComparingTo("5000.00");
        assertThat(store.audit).hasSize(1);
    }




    private static ClaimEvent submitted(UUID claimId, String claimed, Currency currency) {
        return event(ClaimEventType.CLAIM_SUBMITTED, claimId, null, ClaimStatus.SUBMITTED, claimed, null, currency);
    }

    private static ClaimEvent decided(UUID claimId, ClaimStatus outcome, String claimed, String settled) {
        return event(ClaimEventType.CLAIM_DECIDED, claimId, ClaimStatus.IN_REVIEW, outcome, claimed, settled,
                Currency.SGD);
    }

    private static ClaimEvent event(ClaimEventType type, UUID claimId, ClaimStatus previous, ClaimStatus next,
                                    String claimed, String settled, Currency currency) {
        return new ClaimEvent(UUID.randomUUID(), type, claimId, "CLM-SG-2026-000001", Market.SG, ClaimType.MOTOR,
                currency, previous, next, new BigDecimal(claimed), settled == null ? null : new BigDecimal(settled),
                "bob", NOW);
    }
}
