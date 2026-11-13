package com.chubb.claimops.claim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chubb.claimops.claim.domain.Claim;
import com.chubb.claimops.claim.domain.ClaimStatus;
import com.chubb.claimops.claim.domain.ClaimType;
import com.chubb.claimops.claim.domain.Currency;
import com.chubb.claimops.claim.domain.Market;
import com.chubb.claimops.claim.event.ClaimEvent;
import com.chubb.claimops.claim.event.ClaimEventType;
import com.chubb.claimops.claim.web.dto.ClaimDetail;
import com.chubb.claimops.claim.web.dto.SubmitClaimRequest;
import com.chubb.claimops.common.auth.CurrentUser;
import com.chubb.claimops.common.auth.UserRole;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class ClaimServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-12T05:00:00Z");
    private static final Map<String, String> MOTOR_DETAILS =
            Map.of("licensePlate", "SGX1234A", "vehicleModel", "Toyota Corolla");
    private static final CurrentUser ALICE = new CurrentUser("alice", UserRole.CLAIMANT);
    private static final CurrentUser BOB = new CurrentUser("bob", UserRole.STAFF);
    private static final CurrentUser CAROL = new CurrentUser("carol", UserRole.STAFF);

    @Mock
    private ClaimRepository claims;
    @Mock
    private ApplicationEventPublisher events;

    private ClaimService service;

    @BeforeEach
    void setUp() {
        service = new ClaimService(claims, events, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void submitDefaultsToTheLocalCurrencyAndRaisesClaimSubmitted() {
        when(claims.nextClaimSequence()).thenReturn(42L);

        ClaimDetail detail = service.submit(ALICE, new SubmitClaimRequest("POL-1001", ClaimType.MOTOR, Market.TH, null,
                LocalDate.parse("2026-11-10"), "  Side swipe  ", MOTOR_DETAILS, new BigDecimal("80000.00")));

        assertThat(detail.claimNumber()).isEqualTo("CLM-TH-2026-000042");
        assertThat(detail.currency()).isEqualTo(Currency.THB);
        assertThat(detail.incidentDescription()).isEqualTo("Side swipe");
        ClaimEvent event = capturedEvent();
        assertThat(event.type()).isEqualTo(ClaimEventType.CLAIM_SUBMITTED);
        assertThat(event.previousStatus()).isNull();
        assertThat(event.newStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(event.claimedAmount()).isEqualByComparingTo("80000.00");
    }

    /**
     * Two officers load the same SUBMITTED claim at the same time. Each holds its own copy at version N. The first
     * flush succeeds; the second hits the version check and the repository throws. The loser must see the exception,
     * which {@code GlobalExceptionHandler} turns into 409, and must not publish an event for a change that was rolled
     * back.
     */
    @Test
    void concurrentPickupLosesWithAnOptimisticLockFailureAndPublishesNothing() {
        UUID id = UUID.randomUUID();
        Claim bobsCopy = submittedClaim();
        Claim carolsCopy = submittedClaim();
        when(claims.findById(id)).thenReturn(Optional.of(bobsCopy), Optional.of(carolsCopy));
        when(claims.saveAndFlush(bobsCopy)).thenReturn(bobsCopy);
        when(claims.saveAndFlush(carolsCopy)).thenThrow(new ObjectOptimisticLockingFailureException(Claim.class, id));

        ClaimDetail winner = service.pickUp(BOB, id);

        assertThatThrownBy(() -> service.pickUp(CAROL, id))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        assertThat(winner.assignedHandler()).isEqualTo("bob");
        assertThat(winner.status()).isEqualTo(ClaimStatus.IN_REVIEW);
        verify(events, times(1)).publishEvent(any(ClaimEvent.class));
        assertThat(capturedEvent().actor()).isEqualTo("bob");
    }





    private static Claim submittedClaim() {
        return Claim.submit("CLM-SG-2026-000001", "alice", "POL-1001", ClaimType.MOTOR, Market.SG, Currency.SGD,
                LocalDate.parse("2026-11-10"), "Rear-ended", MOTOR_DETAILS, new BigDecimal("5000.00"), NOW);
    }

    private ClaimEvent capturedEvent() {
        ArgumentCaptor<ClaimEvent> captor = ArgumentCaptor.forClass(ClaimEvent.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }
}
