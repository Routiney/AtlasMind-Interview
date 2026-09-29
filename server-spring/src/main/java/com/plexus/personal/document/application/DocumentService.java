package com.plexus.personal.document.application;

import com.plexus.personal.document.domain.Document;
import com.plexus.personal.document.infrastructure.DocumentMapper;
import com.plexus.personal.document.infrastructure.DocumentRow;
import com.plexus.personal.document.infrastructure.messaging.DocumentEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {
    private static final long DEFAULT_MAX_BYTES = 10L * 1024 * 1024;
    private static final Set<String> EXTENSIONS = Set.of("pdf", "docx", "md", "markdown", "txt");
    private static final Map<String, Set<String>> MEDIA_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            "md", Set.of("text/markdown", "text/plain"),
            "markdown", Set.of("text/markdown", "text/plain"),
            "txt", Set.of("text/plain")
    );

    private final DocumentMapper mapper;
    private final DocumentEventPublisher events;
    private final Path root;
    private final long maxBytes;

    public DocumentService(DocumentMapper mapper, DocumentEventPublisher events,
                           @Value("${atlas.documents.root:./data/documents}") String root,
                           @Value("${atlas.documents.max-bytes:" + DEFAULT_MAX_BYTES + "}") long maxBytes) {
        this.mapper = mapper;
        this.events = events;
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public java.util.List<Document> list(Long userId) {
        return mapper.findByUserId(userId).stream().map(DocumentRow::toDomain).toList();
    }

    public Document find(Long id, Long userId) {
        DocumentRow row = mapper.findById(id, userId);
        if (row == null) throw new java.util.NoSuchElementException("Document not found");
        return row.toDomain();
    }

    public Mono<Document> upload(Long userId, FilePart part) {
        String filename = Optional.ofNullable(part.filename()).orElse("").trim();
        String extension = validateFilename(filename);
        String mediaType = Optional.ofNullable(part.headers().getContentType()).map(MediaType::toString)
                .orElse("").toLowerCase(Locale.ROOT);
        if (!MEDIA_TYPES.get(extension).contains(mediaType)) {
            return Mono.error(new IllegalArgumentException("文件类型与扩展名不匹配"));
        }

        String storageName = UUID.randomUUID() + "." + extension;
        Path userDirectory = root.resolve(String.valueOf(userId)).normalize();
        Path temporary = userDirectory.resolve("." + storageName + ".upload").normalize();
        Path target = userDirectory.resolve(storageName).normalize();
        if (!target.startsWith(userDirectory)) return Mono.error(new IllegalArgumentException("非法存储路径"));

        return Mono.fromCallable(() -> {
            Files.createDirectories(userDirectory);
            return temporary;
        }).flatMap(path -> part.transferTo(path)
                .then(Mono.fromCallable(() -> finishUpload(userId, filename, extension, mediaType, path, target)))
                .doOnError(error -> deleteQuietly(path)));
    }

    private Document finishUpload(Long userId, String filename, String extension, String mediaType,
                                  Path temporary, Path target) throws Exception {
        long size = Files.size(temporary);
        if (size <= 0) throw new IllegalArgumentException("文件不能为空");
        if (size > maxBytes) throw new IllegalArgumentException("文件大小不能超过 " + maxBytes + " 字节");
        String sha256 = sha256(temporary);
        boolean moved = false;
        try {
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
            DocumentRow row = new DocumentRow();
            row.userId = userId;
            row.originalFilename = filename;
            row.storageKey = userId + "/" + target.getFileName();
            row.mediaType = mediaType;
            row.fileExtension = extension;
            row.fileSize = size;
            row.sha256 = sha256;
            row.status = "PENDING";
            mapper.insert(row);
            Document document = row.toDomain();
            try {
                events.publishUploaded(document);
                events.publishParseRequested(document);
            } catch (RuntimeException publishFailure) {
                mapper.updateStatus(document.id(), userId, "FAILED", "文档任务发布失败: " + publishFailure.getMessage());
                throw publishFailure;
            }
            return find(document.id(), userId);
        } catch (Exception error) {
            if (moved) deleteQuietly(target);
            throw error;
        }
    }

    private String validateFilename(String filename) {
        if (filename.isBlank() || filename.length() > 255 || filename.contains("..")
                || filename.contains("/") || filename.contains("\\")
                || filename.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("文件名无效");
        }
        int dot = filename.lastIndexOf('.');
        String extension = dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!EXTENSIONS.contains(extension)) throw new IllegalArgumentException("仅支持 PDF、DOCX、Markdown 和 TXT");
        return extension;
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) if (read > 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public void reindex(Long id, Long userId) {
        mapper.updateStatus(id, userId, "PENDING", null);
        Document pending = find(id, userId);
        try {
            events.publishParseRequested(pending);
        } catch (RuntimeException publishFailure) {
            mapper.updateStatus(id, userId, "FAILED", "文档任务发布失败: " + publishFailure.getMessage());
            throw publishFailure;
        }
    }

    public Path storagePath(Document document) {
        Path path = root.resolve(document.storageKey()).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("非法文档路径");
        return path;
    }

    public void delete(Long id, Long userId) {
        Document document = find(id, userId);
        if (mapper.delete(id, userId) == 1) deleteQuietly(root.resolve(document.storageKey()).normalize());
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (Exception ignored) { }
    }
}
