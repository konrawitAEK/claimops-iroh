package com.chubb.claimops.claim.web.dto;

import com.chubb.claimops.claim.domain.InfoRequest;
import java.time.Instant;
import java.util.UUID;

public record InfoRequestView(UUID id, String question, String requestedBy, Instant requestedAt,
                              String answer, Instant answeredAt) {

    public static InfoRequestView from(InfoRequest request) {
        return new InfoRequestView(request.getId(), request.getQuestion(), request.getRequestedBy(),
                request.getRequestedAt(), request.getAnswer(), request.getAnsweredAt());
    }
}
