package com.plexus.personal.document.domain;
import java.time.OffsetDateTime;
public record Document(Long id, Long userId, String originalFilename, String storageKey, String mediaType, String fileExtension, long fileSize, String sha256, DocumentStatus status, String failureReason, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
