package com.chubb.claimops.exposure;

import com.chubb.claimops.claim.event.ClaimEvent;
import java.math.BigDecimal;
import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation of {@link ExposureStore}.
 *
 * <p>Every write is a single atomic statement:
 * <ul>
 *   <li>The idempotency ledger uses {@code INSERT ... ON CONFLICT DO NOTHING}. The row count it returns says
 *       whether this event is new.</li>
 *   <li>Exposure uses {@code INSERT ... ON CONFLICT DO UPDATE}. Concurrent consumers on different partitions can
 *       therefore update the same market row without losing an update and without optimistic-lock retries.</li>
 * </ul>
 * The caller, {@link ExposureProjector}, wraps these statements in one transaction.
 */
@Repository
public class JdbcExposureStore implements ExposureStore {

    private final JdbcClient jdbc;

    public JdbcExposureStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean markProcessed(ClaimEvent event) {
        return jdbc.sql("""
                        INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, now())
                        ON CONFLICT (event_id) DO NOTHING
                        """)
                .param("eventId", event.eventId())
                .update() == 1;
    }

    @Override
    public void appendAudit(ClaimEvent event) {
        jdbc.sql("""
                        INSERT INTO claim_audit_log
                            (event_id, claim_id, claim_number, event_type, previous_status, new_status, actor, occurred_at)
                        VALUES (:eventId, :claimId, :claimNumber, :type, :previousStatus, :newStatus, :actor, :occurredAt)
                        """)
                .param("eventId", event.eventId())
                .param("claimId", event.claimId())
                .param("claimNumber", event.claimNumber())
                .param("type", event.type().name())
                .param("previousStatus", event.previousStatus() == null ? null : event.previousStatus().name())
                .param("newStatus", event.newStatus().name())
                .param("actor", event.actor())
                .param("occurredAt", Timestamp.from(event.occurredAt()))
                .update();
    }

    @Override
    public void adjustExposure(ClaimEvent event, int openDelta, BigDecimal outstandingDelta, BigDecimal settledDelta) {
        jdbc.sql("""
                        INSERT INTO liability_exposures
                            (market, claim_type, currency, open_claims, outstanding_amount, settled_amount, updated_at)
                        VALUES (:market, :claimType, :currency, :openDelta, :outstandingDelta, :settledDelta, now())
                        ON CONFLICT (market, claim_type, currency) DO UPDATE SET
                            open_claims        = liability_exposures.open_claims + EXCLUDED.open_claims,
                            outstanding_amount = liability_exposures.outstanding_amount + EXCLUDED.outstanding_amount,
                            settled_amount     = liability_exposures.settled_amount + EXCLUDED.settled_amount,
                            updated_at         = EXCLUDED.updated_at
                        """)
                .param("market", event.market().name())
                .param("claimType", event.claimType().name())
                .param("currency", event.currency().name())
                .param("openDelta", openDelta)
                .param("outstandingDelta", outstandingDelta)
                .param("settledDelta", settledDelta)
                .update();
    }
}
