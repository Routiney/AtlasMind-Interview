package com.plexus.personal.document.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaTopicConfig {
    @Bean
    NewTopic documentEventsTopic(
            @Value("${atlas.kafka.document-topic:atlas.document-events.v1}") String topic,
            @Value("${ATLAS_KAFKA_DOCUMENT_PARTITIONS:1}") int partitions
    ) {
        return new NewTopic(topic, Math.max(1, partitions), (short) 1);
    }

    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 2L));
    }
}
