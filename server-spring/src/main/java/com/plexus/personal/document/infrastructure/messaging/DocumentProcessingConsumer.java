package com.plexus.personal.document.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.plexus.personal.document.application.DocumentProcessingService;
import com.plexus.personal.document.application.DocumentService;
import com.plexus.personal.document.domain.Document;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentProcessingConsumer {
    private final ObjectMapper objectMapper;
    private final DocumentService documents;
    private final DocumentProcessingService processing;
    private final DocumentEventPublisher events;
    private final RedisDocumentTaskStateService taskState;

    public DocumentProcessingConsumer(
            ObjectMapper objectMapper,
            DocumentService documents,
            DocumentProcessingService processing,
            DocumentEventPublisher events,
            RedisDocumentTaskStateService taskState
    ) {
        this.objectMapper = objectMapper;
        this.documents = documents;
        this.processing = processing;
        this.events = events;
        this.taskState = taskState;
    }

    @KafkaListener(
            topics = "${atlas.kafka.document-topic:atlas.document-events.v1}",
            groupId = "${atlas.kafka.document-consumer-group:atlas-document-processor}"
    )
    public void consume(String rawEvent) {
        DocumentEvent event = read(rawEvent);
        if (!DocumentEvent.DOCUMENT_PARSE_REQUESTED.equals(event.eventType())) return;
        if (taskState.isDone(event.userId(), event.eventId())) return;

        Long documentId = Long.valueOf(event.aggregateId());
        Document document = documents.find(documentId, event.userId());
        taskState.update(event.userId(), documentId, "RUNNING", "PARSING", 10, "正在提取文档文本");
        try {
            processing.process(
                    document,
                    documents.storagePath(document),
                    (stage, percent, message) -> taskState.update(
                            event.userId(), documentId, "RUNNING", stage, percent, message)
            );
            taskState.update(event.userId(), documentId, "SUCCEEDED", "INDEXED", 100, "文档已建立索引");
            events.publishIndexed(document);
            taskState.markDone(event.userId(), event.eventId());
        } catch (RuntimeException exception) {
            taskState.update(event.userId(), documentId, "FAILED", "FAILED", 100,
                    exception.getMessage() == null ? "文档处理失败" : exception.getMessage());
            try {
                events.publishIndexFailed(document, exception.getMessage());
            } catch (RuntimeException ignored) {
                // The Kafka retry will attempt the failure event again.
            }
            throw exception;
        }
    }

    private DocumentEvent read(String rawEvent) {
        try {
            return objectMapper.readValue(rawEvent, DocumentEvent.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid document event", exception);
        }
    }
}
