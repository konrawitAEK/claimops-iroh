package com.chubb.claimops.exposure;

import com.chubb.claimops.claim.event.ClaimEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Test double for {@link ExposureStore}, with the same semantics as the SQL version. */
class InMemoryExposureStore implements ExposureStore {

    record Row(int openClaims, BigDecimal outstanding, BigDecimal settled) {
    }

    private final Set<UUID> processed = new HashSet<>();
    private final Map<String, Row> exposures = new HashMap<>();
    final List<ClaimEvent> audit = new ArrayList<>();

    @Override
    public boolean markProcessed(ClaimEvent event) {
        return processed.add(event.eventId());
    }

    @Override
    public void appendAudit(ClaimEvent event) {
        audit.add(event);
    }

    @Override
    public void adjustExposure(ClaimEvent event, int openDelta, BigDecimal outstandingDelta, BigDecimal settledDelta) {
        exposures.merge(key(event.market().name(), event.claimType().name(), event.currency().name()),
                new Row(openDelta, outstandingDelta, settledDelta),
                (a, b) -> new Row(a.openClaims() + b.openClaims(), a.outstanding().add(b.outstanding()),
                        a.settled().add(b.settled())));
    }

    Row row(String market, String claimType, String currency) {
        return exposures.get(key(market, claimType, currency));
    }

    private static String key(String market, String claimType, String currency) {
        return market + "/" + claimType + "/" + currency;
    }
}
