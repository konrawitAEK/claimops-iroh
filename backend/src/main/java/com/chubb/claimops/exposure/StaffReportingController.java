package com.chubb.claimops.exposure;

import com.chubb.claimops.claim.ClaimService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff reporting endpoints backed by the read models.
 *
 * <p>The dependency points from {@code exposure} to {@code claim}, never the other way round: this module already
 * consumes the claim module's events and also reads its workload query. The claim module knows nothing about
 * exposure, so the write path has no dependency on the reporting side.
 */
@RestController
public class StaffReportingController {

    private final ExposureQueryService exposureQueries;
    private final ClaimService claimService;

    public StaffReportingController(ExposureQueryService exposureQueries, ClaimService claimService) {
        this.exposureQueries = exposureQueries;
        this.claimService = claimService;
    }

    @GetMapping("/api/staff/dashboard")
    public DashboardView dashboard() {
        return new DashboardView(exposureQueries.exposures(), claimService.workload());
    }

    @GetMapping("/api/staff/claims/{id}/audit")
    public List<AuditEntryView> auditTrail(@PathVariable UUID id) {
        return exposureQueries.auditTrail(id);
    }
}
