package com.chubb.claimops.claim.web;

import com.chubb.claimops.claim.ClaimService;
import com.chubb.claimops.claim.domain.ClaimStatus;
import com.chubb.claimops.claim.domain.ClaimType;
import com.chubb.claimops.claim.domain.Market;
import com.chubb.claimops.claim.web.dto.ClaimDetail;
import com.chubb.claimops.claim.web.dto.ClaimSummary;
import com.chubb.claimops.claim.web.dto.RejectClaimRequest;
import com.chubb.claimops.claim.web.dto.RequestInfoRequest;
import com.chubb.claimops.claim.web.dto.SettleClaimRequest;
import com.chubb.claimops.common.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff-facing API.
 *
 * <p>State changes are modelled as POSTs to named sub-resources ({@code /pickup}, {@code /settlement}, ...) rather
 * than a PATCH of {@code status}. Each action has its own payload and rules, and the URL says what the
 * officer did, which is what the audit trail records.
 */
@RestController
@RequestMapping("/api/staff/claims")
public class StaffClaimController {

    private final ClaimService claimService;

    public StaffClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    /** Work queue. {@code assignedTo=me} is shorthand for the caller's own claims. */
    @GetMapping
    public List<ClaimSummary> search(CurrentUser user,
                                     @RequestParam(required = false) ClaimStatus status,
                                     @RequestParam(required = false) Market market,
                                     @RequestParam(required = false) ClaimType claimType,
                                     @RequestParam(required = false) String assignedTo) {
        String handler = "me".equalsIgnoreCase(assignedTo) ? user.name() : assignedTo;
        return claimService.search(status, market, claimType, handler);
    }

    @GetMapping("/{id}")
    public ClaimDetail get(@PathVariable UUID id) {
        return claimService.get(id);
    }

    @PostMapping("/{id}/pickup")
    public ClaimDetail pickUp(CurrentUser user, @PathVariable UUID id) {
        return claimService.pickUp(user, id);
    }

    @PostMapping("/{id}/info-requests")
    public ClaimDetail requestInfo(CurrentUser user, @PathVariable UUID id,
                                   @Valid @RequestBody RequestInfoRequest request) {
        return claimService.requestInfo(user, id, request.question());
    }

    @PostMapping("/{id}/settlement")
    public ClaimDetail settle(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody SettleClaimRequest request) {
        return claimService.settle(user, id, request.settledAmount(), request.note());
    }

    @PostMapping("/{id}/rejection")
    public ClaimDetail reject(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody RejectClaimRequest request) {
        return claimService.reject(user, id, request.reason());
    }
}
