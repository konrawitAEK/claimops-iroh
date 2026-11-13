package com.chubb.claimops.exposure;

import com.chubb.claimops.claim.web.dto.WorkloadView;
import java.util.List;

/** Staff dashboard: outstanding liability per market, type and currency, plus team workload. */
public record DashboardView(List<ExposureView> exposures, WorkloadView workload) {
}
