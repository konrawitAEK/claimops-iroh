package com.chubb.claimops.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka topology and consumer error policy.
 *
 * <p>The topic is declared here so the application creates it on startup; the broker has auto-creation disabled,
 * which stops a typo from silently creating a second topic. Events are keyed by claim id, so all events for one
 * claim land on the same partition and are consumed in order. That ordering is what lets the exposure projection
 * apply a claim's SUBMITTED and DECIDED events one after the other.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic claimEventsTopic(@Value("${claimops.kafka.topic}") String topic,
                                     @Value("${claimops.kafka.partitions}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    /**
     * Retries a failing record three times, one second apart, then logs it and moves on.
     *
     * <p>Skipping keeps one poison message from blocking every later claim on that partition. The cost is that
     * a skipped event is missing from the read model until it is replayed. The production version of this
     * handler routes such records to a dead-letter topic for replay. Records that fail deserialization are
     * never retried, because {@code ErrorHandlingDeserializer} marks them as not retryable.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1_000L, 3));
    }
}
