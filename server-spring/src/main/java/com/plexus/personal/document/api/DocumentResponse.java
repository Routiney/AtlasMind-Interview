package com.plexus.personal.document.api;
import com.plexus.personal.document.domain.*; import java.time.OffsetDateTime;
public record DocumentResponse(Long id,String originalFilename,String mediaType,String fileExtension,long fileSize,String sha256,String status,String failureReason,OffsetDateTime createdAt,OffsetDateTime updatedAt){public static DocumentResponse from(Document d){return new DocumentResponse(d.id(),d.originalFilename(),d.mediaType(),d.fileExtension(),d.fileSize(),d.sha256(),d.status().name(),d.failureReason(),d.createdAt(),d.updatedAt());}}
