package com.chubb.claimops.claim.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Forwards {@link ClaimEvent}s to Kafka once the database transaction that produced them has committed.
 *
 * <p>The service raises events as in-process Spring application events. Listening in {@code AFTER_COMMIT} means
 * consumers never see an event for a change that was rolled back, for example the losing side of an
 * optimistic-lock conflict. Events are keyed by claim id, which keeps each claim's events in order on one partition.
 *
 * <p><b>Trade-off:</b> the commit and the send are not atomic. If the process dies between them, the change is
 * stored but its event is lost, and the read model drifts until it is rebuilt. The fix is a transactional outbox:
 * write the event to an outbox table in the same transaction, and have a relay publish it. This version doesn't
 * build the outbox, to stay inside the time box. Send failures are logged with the event id so they can be replayed.
 */
@Component
public class ClaimEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClaimEventPublisher.class);

    private final KafkaTemplate<String, ClaimEvent> kafkaTemplate;
    private final String topic;

    public ClaimEventPublisher(KafkaTemplate<String, ClaimEvent> kafkaTemplate,
                               @Value("${claimops.kafka.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ClaimEvent event) {
        kafkaTemplate.send(topic, event.claimId().toString(), event).whenComplete((result, error) -> {
            if (error != null) {
                log.error("Failed to publish {} {} for claim {}", event.type(), event.eventId(), event.claimNumber(), error);
            } else {
                log.debug("Published {} {} for claim {} at offset {}", event.type(), event.eventId(),
                        event.claimNumber(), result.getRecordMetadata().offset());
            }
        });
    }
}
