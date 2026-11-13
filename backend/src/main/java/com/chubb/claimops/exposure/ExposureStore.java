package com.chubb.claimops.exposure;

import com.chubb.claimops.claim.event.ClaimEvent;
import java.math.BigDecimal;

/**
 * Persistence port for the exposure read models.
 *
 * <p>{@link ExposureProjector} holds the projection rules: what each event type does to exposure, and how
 * duplicates are detected. This interface holds the storage. Keeping them apart lets the rules be unit-tested
 * against an in-memory store, with no database.
 */
public interface ExposureStore {

    /**
     * Records that {@code event} is being applied.
     *
     * @return {@code true} if this is the first time, {@code false} if the event id was already recorded
     */
    boolean markProcessed(ClaimEvent event);

    void appendAudit(ClaimEvent event);

    /** Adds the deltas to the exposure row for the event's market, claim type and currency, creating it if needed. */
    void adjustExposure(ClaimEvent event, int openDelta, BigDecimal outstandingDelta, BigDecimal settledDelta);
}
