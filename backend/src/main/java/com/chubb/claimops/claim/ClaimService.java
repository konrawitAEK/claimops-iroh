package com.chubb.claimops.claim;

import com.chubb.claimops.claim.domain.Claim;
import com.chubb.claimops.claim.domain.ClaimStatus;
import com.chubb.claimops.claim.domain.ClaimType;
import com.chubb.claimops.claim.domain.Currency;
import com.chubb.claimops.claim.domain.Market;
import com.chubb.claimops.claim.event.ClaimEvent;
import com.chubb.claimops.claim.event.ClaimEventType;
import com.chubb.claimops.claim.web.dto.ClaimDetail;
import com.chubb.claimops.claim.web.dto.ClaimSummary;
import com.chubb.claimops.claim.web.dto.SubmitClaimRequest;
import com.chubb.claimops.claim.web.dto.WorkloadView;
import com.chubb.claimops.common.auth.CurrentUser;
import com.chubb.claimops.common.error.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for the claim lifecycle. Each command runs in one transaction:
 * <ol>
 *   <li>load the claim,</li>
 *   <li>apply the domain method, which enforces the state machine and the actor's rights,</li>
 *   <li>flush, so an optimistic-lock conflict surfaces here and becomes a 409,</li>
 *   <li>raise a {@link ClaimEvent}. {@code ClaimEventPublisher} sends it to Kafka only after the commit.</li>
 * </ol>
 *
 * <p>Methods return DTOs built inside the transaction, so lazy collections are never touched after it has closed.
 */
@Service
@Transactional
public class ClaimService {

    private final ClaimRepository claims;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ClaimService(ClaimRepository claims, ApplicationEventPublisher events, Clock clock) {
        this.claims = claims;
        this.events = events;
        this.clock = clock;
    }

    // ---- Claimant -------------------------------------------------------------------------------------------

    public ClaimDetail submit(CurrentUser claimant, SubmitClaimRequest request) {
        Instant now = clock.instant();
        Currency currency = request.currency() != null ? request.currency() : request.market().getLocalCurrency();
        Claim claim = Claim.submit(nextClaimNumber(request.market(), now), claimant.name(), request.policyNumber(),
                request.claimType(), request.market(), currency, request.incidentDate(),
                request.incidentDescription().trim(), request.incidentDetails(), request.claimedAmount(), now);
        claims.saveAndFlush(claim);
        events.publishEvent(ClaimEvent.of(ClaimEventType.CLAIM_SUBMITTED, claim, null, claimant.name(), now));
        return ClaimDetail.from(claim);
    }

    @Transactional(readOnly = true)
    public List<ClaimSummary> listOwn(CurrentUser claimant) {
        return claims.findByClaimantNameOrderBySubmittedAtDesc(claimant.name()).stream()
                .map(ClaimSummary::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClaimDetail getOwn(CurrentUser claimant, UUID claimId) {
        return ClaimDetail.from(loadOwned(claimant, claimId));
    }

    @Transactional(readOnly = true)
    public ClaimDetail getOwnByNumber(CurrentUser claimant, String claimNumber) {
        Claim claim = claims.findByClaimNumber(claimNumber.trim().toUpperCase())
                .filter(c -> c.isOwnedBy(claimant.name()))
                .orElseThrow(() -> new ResourceNotFoundException("Claim " + claimNumber + " not found"));
        return ClaimDetail.from(claim);
    }

    public ClaimDetail provideInfo(CurrentUser claimant, UUID claimId, String answer) {
        Claim claim = loadOwned(claimant, claimId);
        return apply(claim, claimant, ClaimEventType.CLAIM_STATUS_CHANGED,
                c -> c.provideInfo(claimant.name(), answer.trim(), clock.instant()));
    }

    // ---- Staff ----------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ClaimSummary> search(ClaimStatus status, Market market, ClaimType claimType, String handler) {
        return claims.search(status, market, claimType, handler).stream().map(ClaimSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public ClaimDetail get(UUID claimId) {
        return ClaimDetail.from(load(claimId));
    }

    public ClaimDetail pickUp(CurrentUser staff, UUID claimId) {
        return apply(load(claimId), staff, ClaimEventType.CLAIM_STATUS_CHANGED,
                c -> c.pickUp(staff.name(), clock.instant()));
    }

    public ClaimDetail requestInfo(CurrentUser staff, UUID claimId, String question) {
        return apply(load(claimId), staff, ClaimEventType.CLAIM_STATUS_CHANGED,
                c -> c.requestInfo(staff.name(), question.trim(), clock.instant()));
    }

    public ClaimDetail settle(CurrentUser staff, UUID claimId, BigDecimal amount, String note) {
        return apply(load(claimId), staff, ClaimEventType.CLAIM_DECIDED,
                c -> c.settle(staff.name(), amount, note == null ? null : note.trim(), clock.instant()));
    }

    public ClaimDetail reject(CurrentUser staff, UUID claimId, String reason) {
        return apply(load(claimId), staff, ClaimEventType.CLAIM_DECIDED,
                c -> c.reject(staff.name(), reason.trim(), clock.instant()));
    }

    @Transactional(readOnly = true)
    public WorkloadView workload() {
        Map<ClaimStatus, Long> byStatus = new EnumMap<>(ClaimStatus.class);
        for (ClaimStatus status : ClaimStatus.values()) {
            byStatus.put(status, 0L);
        }
        claims.countByStatus().forEach(row -> byStatus.put(row.getStatus(), row.getCount()));
        List<WorkloadView.HandlerLoad> byHandler = claims.countOpenByHandler(ClaimStatus.OPEN).stream()
                .map(row -> new WorkloadView.HandlerLoad(row.getHandler(), row.getCount()))
                .toList();
        return new WorkloadView(byStatus, byHandler, performance(byHandler));
    }

    /** Joins open workload with decisions, so an officer with only open claims, or only decided ones, still appears. */
    private List<WorkloadView.HandlerPerformance> performance(List<WorkloadView.HandlerLoad> open) {
        Map<String, Long> openByHandler = new HashMap<>();
        open.forEach(load -> openByHandler.put(load.handler(), load.openClaims()));
        Map<String, WorkloadView.HandlerPerformance> rows = new TreeMap<>();
        for (ClaimRepository.HandlerDecisions d : claims.decisionsByHandler()) {
            rows.put(d.getHandler(), new WorkloadView.HandlerPerformance(d.getHandler(),
                    openByHandler.getOrDefault(d.getHandler(), 0L), d.getSettled(), d.getRejected(),
                    d.getAvgHoursToDecision()));
        }
        openByHandler.forEach((handler, count) ->
                rows.putIfAbsent(handler, new WorkloadView.HandlerPerformance(handler, count, 0, 0, null)));
        return List.copyOf(rows.values());
    }

    // ---- Internals ------------------------------------------------------------------------------------------

    private ClaimDetail apply(Claim claim, CurrentUser actor, ClaimEventType type, Consumer<Claim> change) {
        ClaimStatus previous = claim.getStatus();
        change.accept(claim);
        claims.saveAndFlush(claim);
        events.publishEvent(ClaimEvent.of(type, claim, previous, actor.name(), claim.getUpdatedAt()));
        return ClaimDetail.from(claim);
    }

    private Claim load(UUID claimId) {
        return claims.findById(claimId)
                .orElseThrow(() -> new ResourceNotFoundException("Claim " + claimId + " not found"));
    }

    /**
     * Loads a claim for its claimant. Someone else's claim returns 404 rather than 403, so claimants can't
     * probe which claim ids exist.
     */
    private Claim loadOwned(CurrentUser claimant, UUID claimId) {
        Claim claim = load(claimId);
        if (!claim.isOwnedBy(claimant.name())) {
            throw new ResourceNotFoundException("Claim " + claimId + " not found");
        }
        return claim;
    }

    /** Human-readable claim number, e.g. {@code CLM-SG-2026-000042}, unique via a database sequence. */
    private String nextClaimNumber(Market market, Instant now) {
        return "CLM-%s-%d-%06d".formatted(market, Year.from(now.atZone(ZoneOffset.UTC)).getValue(),
                claims.nextClaimSequence());
    }
}
