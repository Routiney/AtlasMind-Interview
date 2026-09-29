package com.plexus.personal.document.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plexus.personal.document.domain.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class DocumentEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final String topic;

    public DocumentEventPublisher(
            KafkaTemplate<String, String> kafka,
            ObjectMapper objectMapper,
            @Value("${atlas.kafka.document-topic:atlas.document-events.v1}") String topic
    ) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void publishUploaded(Document document) {
        publish(event(document, DocumentEvent.DOCUMENT_UPLOADED,
                Map.of("storageKey", document.storageKey(), "sha256", document.sha256())));
    }

    public void publishParseRequested(Document document) {
        publish(event(document, DocumentEvent.DOCUMENT_PARSE_REQUESTED,
                Map.of("storageKey", document.storageKey(), "sha256", document.sha256())));
    }

    public void publishIndexed(Document document) {
        publish(event(document, DocumentEvent.DOCUMENT_INDEXED,
                Map.of("status", "READY")));
    }

    public void publishIndexFailed(Document document, String reason) {
        publish(event(document, DocumentEvent.DOCUMENT_INDEX_FAILED,
                Map.of("status", "FAILED", "reason", reason == null ? "unknown" : reason)));
    }

    private DocumentEvent event(Document document, String type, Map<String, Object> payload) {
        return new DocumentEvent(
                UUID.randomUUID().toString(),
                type,
                1,
                Long.toString(document.id()),
                document.userId(),
                OffsetDateTime.now(),
                payload
        );
    }

    private void publish(DocumentEvent event) {
        try {
            String body = objectMapper.writeValueAsString(event);
            kafka.send(topic, event.aggregateId(), body).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka event publishing interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka event publishing failed", exception);
        }
    }
}
