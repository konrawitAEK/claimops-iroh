package com.chubb.claimops.claim.web.dto;

import com.chubb.claimops.claim.domain.ClaimStatus;
import java.util.List;
import java.util.Map;

/**
 * Team workload and performance: claim counts per status (every status present, zero included), open claims per
 * handler, and each officer's decisions with their average time to decision.
 *
 * <p>The counts are read straight from the write model rather than from a projection. They are simple aggregates
 * over an indexed column, and staff expect them to be exact right after they act.
 */
public record WorkloadView(Map<ClaimStatus, Long> byStatus, List<HandlerLoad> openByHandler,
                           List<HandlerPerformance> performance) {

    public record HandlerLoad(String handler, long openClaims) {
    }

    /**
     * One officer's track record. {@code avgHoursToDecision} runs from submission, not pickup, because that's the wait
     * the claimant experiences; it's {@code null} for an officer with open claims but no decisions yet.
     */
    public record HandlerPerformance(String handler, long openClaims, long settled, long rejected,
                                     Double avgHoursToDecision) {
    }
}
