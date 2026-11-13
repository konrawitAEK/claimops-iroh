package com.chubb.claimops.claim.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A question a handler sends the claimant, and the claimant's answer.
 *
 * <p>It belongs to the {@link Claim} aggregate. It is created and answered only through {@code Claim}, which keeps
 * the rule "at most one open request, and only while the claim is INFO_REQUESTED" inside the aggregate. That's why
 * it has getters but no setters.
 */
@Entity
@Table(name = "info_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InfoRequest {

    @Id
    private UUID id;

    /** Back-reference for JPA only; not exposed, so callers can't navigate around the aggregate root. */
    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    @Column(nullable = false, length = 1000)
    private String question;

    @Column(name = "requested_by", nullable = false, length = 100)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(length = 2000)
    private String answer;

    @Column(name = "answered_at")
    private Instant answeredAt;

    InfoRequest(Claim claim, String question, String requestedBy, Instant requestedAt) {
        this.id = UUID.randomUUID();
        this.claim = claim;
        this.question = question;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    void answer(String answer, Instant answeredAt) {
        this.answer = answer;
        this.answeredAt = answeredAt;
    }

    public boolean isOpen() {
        return answeredAt == null;
    }
}
