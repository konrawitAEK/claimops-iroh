package com.chubb.claimops.exposure;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Reads the exposure and audit read models.
 *
 * <p>These reads are eventually consistent: a claim decided a moment ago may still count as outstanding until its
 * event has been consumed, which normally takes milliseconds. That is acceptable for a management view of
 * exposure. Workload counts that officers act on come from the write model instead (see {@code WorkloadView}).
 */
@Service
public class ExposureQueryService {

    private final JdbcClient jdbc;

    public ExposureQueryService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ExposureView> exposures() {
        return jdbc.sql("""
                        SELECT market, claim_type, currency, open_claims, outstanding_amount, settled_amount, updated_at
                        FROM liability_exposures
                        ORDER BY market, claim_type, currency
                        """)
                .query((rs, row) -> new ExposureView(
                        rs.getString("market"),
                        rs.getString("claim_type"),
                        rs.getString("currency"),
                        rs.getInt("open_claims"),
                        rs.getBigDecimal("outstanding_amount"),
                        rs.getBigDecimal("settled_amount"),
                        rs.getTimestamp("updated_at").toInstant()))
                .list();
    }

    public List<AuditEntryView> auditTrail(UUID claimId) {
        return jdbc.sql("""
                        SELECT event_id, event_type, previous_status, new_status, actor, occurred_at
                        FROM claim_audit_log
                        WHERE claim_id = :claimId
                        ORDER BY occurred_at, event_type
                        """)
                .param("claimId", claimId)
                .query((rs, row) -> {
                    Timestamp occurredAt = rs.getTimestamp("occurred_at");
                    return new AuditEntryView(
                            rs.getObject("event_id", UUID.class),
                            rs.getString("event_type"),
                            rs.getString("previous_status"),
                            rs.getString("new_status"),
                            rs.getString("actor"),
                            occurredAt.toInstant());
                })
                .list();
    }
}
