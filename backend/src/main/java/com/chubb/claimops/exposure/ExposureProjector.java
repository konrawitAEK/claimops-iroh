package com.chubb.claimops.exposure;

import com.chubb.claimops.claim.event.ClaimEvent;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies claim events to the read models: liability exposure and the audit trail.
 *
 * <p><b>Idempotency:</b> Kafka delivers at least once, so the same event can arrive twice, for example when the
 * consumer crashes after writing but before committing its offset. Every apply first records the event id with
 * {@link ExposureStore#markProcessed}. If the id was already there, the event is skipped. The ledger write and the
 * read-model updates share one transaction, so they are written together or not at all. No window exists in which
 * an event is recorded as processed but its effect is missing.
 *
 * <p><b>Exposure rules:</b>
 * <ul>
 *   <li>{@code CLAIM_SUBMITTED}: the claim opens. Add 1 open claim and the claimed amount to outstanding.</li>
 *   <li>{@code CLAIM_DECIDED} (SETTLED or REJECTED): the claim closes. Subtract 1 and the claimed amount, and add
 *       the settled amount, if any, to paid-out value.</li>
 *   <li>{@code CLAIM_STATUS_CHANGED}: moves between open statuses. Audit only, exposure unchanged.</li>
 * </ul>
 * The claimed amount stands in for a handler-set reserve, which would be more accurate and is the next
 * refinement.
 */
@Component
public class ExposureProjector {

    private static final Logger log = LoggerFactory.getLogger(ExposureProjector.class);

    private final ExposureStore store;

    public ExposureProjector(ExposureStore store) {
        this.store = store;
    }

    /**
     * Applies one event once.
     *
     * @return {@code true} if the event was applied, {@code false} if it was a duplicate and was skipped
     */
    @Transactional
    public boolean apply(ClaimEvent event) {
        if (!store.markProcessed(event)) {
            log.info("Skipping duplicate event {} ({}) for claim {}", event.eventId(), event.type(), event.claimNumber());
            return false;
        }

        store.appendAudit(event);
        switch (event.type()) {
            case CLAIM_SUBMITTED -> store.adjustExposure(event, 1, event.claimedAmount(), BigDecimal.ZERO);
            case CLAIM_DECIDED -> store.adjustExposure(event, -1, event.claimedAmount().negate(),
                    event.settledAmount() == null ? BigDecimal.ZERO : event.settledAmount());
            case CLAIM_STATUS_CHANGED -> {
                // Moves between open statuses don't change exposure; the audit entry above records them.
            }
        }
        return true;
    }
}
