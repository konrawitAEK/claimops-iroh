package com.chubb.claimops.exposure;

import com.chubb.claimops.claim.event.ClaimEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point for the exposure module.
 *
 * <p>The listener only delegates. Idempotency and the transaction live in {@link ExposureProjector}, so the
 * same logic can be called directly in tests or by a replay tool. The offset is committed per record after
 * this method returns ({@code ack-mode: record}). A failure before then means redelivery, which the projector's
 * idempotency check absorbs.
 */
@Component
public class ExposureEventConsumer {

    private final ExposureProjector projector;

    public ExposureEventConsumer(ExposureProjector projector) {
        this.projector = projector;
    }

    @KafkaListener(topics = "${claimops.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void onClaimEvent(ClaimEvent event) {
        projector.apply(event);
    }
}
