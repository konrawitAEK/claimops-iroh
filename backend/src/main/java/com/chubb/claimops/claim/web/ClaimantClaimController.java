package com.chubb.claimops.claim.web;

import com.chubb.claimops.claim.ClaimService;
import com.chubb.claimops.claim.web.dto.ClaimDetail;
import com.chubb.claimops.claim.web.dto.ClaimSummary;
import com.chubb.claimops.claim.web.dto.ProvideInfoRequest;
import com.chubb.claimops.claim.web.dto.SubmitClaimRequest;
import com.chubb.claimops.common.auth.CurrentUser;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Claimant-facing API. Every call is scoped to the caller's own claims. */
@RestController
@RequestMapping("/api/claimant/claims")
public class ClaimantClaimController {

    private final ClaimService claimService;

    public ClaimantClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    public ResponseEntity<ClaimDetail> submit(CurrentUser user, @Valid @RequestBody SubmitClaimRequest request) {
        ClaimDetail claim = claimService.submit(user, request);
        return ResponseEntity.created(URI.create("/api/claimant/claims/" + claim.id())).body(claim);
    }

    @GetMapping
    public List<ClaimSummary> list(CurrentUser user) {
        return claimService.listOwn(user);
    }

    /** Track a claim by the number the claimant was given on submission. Other claimants' numbers return 404. */
    @GetMapping("/by-number/{claimNumber}")
    public ClaimDetail getByNumber(CurrentUser user, @PathVariable String claimNumber) {
        return claimService.getOwnByNumber(user, claimNumber);
    }

    @GetMapping("/{id}")
    public ClaimDetail get(CurrentUser user, @PathVariable UUID id) {
        return claimService.getOwn(user, id);
    }

    @PostMapping("/{id}/info-responses")
    public ClaimDetail provideInfo(CurrentUser user, @PathVariable UUID id,
                                   @Valid @RequestBody ProvideInfoRequest request) {
        return claimService.provideInfo(user, id, request.answer());
    }
}
