package com.plexus.personal.document.infrastructure.messaging;

import java.time.OffsetDateTime;
import java.util.Map;

public record DocumentEvent(
        String eventId,
        String eventType,
        int version,
        String aggregateId,
        Long userId,
        OffsetDateTime createdAt,
        Map<String, Object> payload
) {
    public static final String DOCUMENT_UPLOADED = "DocumentUploaded";
    public static final String DOCUMENT_PARSE_REQUESTED = "DocumentParseRequested";
    public static final String DOCUMENT_INDEXED = "DocumentIndexed";
    public static final String DOCUMENT_INDEX_FAILED = "DocumentIndexFailed";
}
