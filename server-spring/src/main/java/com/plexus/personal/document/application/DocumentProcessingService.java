package com.plexus.personal.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.plexus.personal.document.domain.Document;
import com.plexus.personal.document.infrastructure.DocumentChunkMapper;
import com.plexus.personal.document.infrastructure.DocumentChunkRow;
import com.plexus.personal.document.infrastructure.DocumentMapper;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentProcessingService {
    private final DocumentChunkMapper chunks;
    private final DocumentMapper documents;
    private final EmbeddingService embeddings;
    private final DocumentReviewClient reviewer;
    private final Tika tika = new Tika();

    public DocumentProcessingService(DocumentChunkMapper chunks, DocumentMapper documents,
                                     EmbeddingService embeddings, DocumentReviewClient reviewer) {
        this.chunks = chunks;
        this.documents = documents;
        this.embeddings = embeddings;
        this.reviewer = reviewer;
    }

    public void process(Document document, Path file) {
        process(document, file, (stage, percent, message) -> { });
    }

    public void process(Document document, Path file, ProgressListener progress) {
        try {
            progress.update("PARSING", 10, "正在提取文档文本");
            String raw = tika.parseToString(file.toFile()).trim();
            if (raw.isBlank()) throw new IllegalArgumentException("文档没有可提取文本");

            Quality quality = quality(raw);
            String normalized = raw;
            String review = null;
            String status = "READY";
            String reason = null;
            if (quality.needsReview) {
                progress.update("QUALITY_REVIEW", 25, "正在检查文档文本质量");
                JsonNode result = reviewer.review(raw);
                if (result != null && result.path("normalized_text").isTextual()) {
                    normalized = result.path("normalized_text").asText().trim();
                    review = result.toString();
                } else {
                    status = "NEEDS_REVIEW";
                    reason = "文本包含疑似乱码，Agent 审核未完成";
                }
            }

            documents.updateQuality(document.id(), document.userId(), raw, normalized,
                    quality.status, quality.score, review, status, reason);
            progress.update("CHUNKING", 45, "正在切分文档");
            List<String> parts = split(normalized, 1200, 180);
            chunks.deleteByDocument(document.id());
            for (int index = 0; index < parts.size(); index++) {
                int percent = 50 + (int) Math.round((index + 1) * 45.0 / Math.max(1, parts.size()));
                progress.update("EMBEDDING", percent, "正在建立向量索引");
                DocumentChunkRow row = new DocumentChunkRow();
                row.documentId = document.id();
                row.userId = document.userId();
                row.chunkIndex = index;
                row.content = parts.get(index);
                row.embedding = embeddings.embed(parts.get(index));
                chunks.insert(row);
            }
            progress.update("INDEXED", 100, "文档已建立索引");
        } catch (Exception exception) {
            documents.updateStatus(document.id(), document.userId(), "FAILED", exception.getMessage());
            throw new IllegalArgumentException("文档解析失败: " + exception.getMessage(), exception);
        }
    }

    @FunctionalInterface
    public interface ProgressListener {
        void update(String stage, int percent, String message);
    }

    private Quality quality(String text) {
        long bad = text.chars().filter(c -> c == 0xfffd || c == '�').count();
        long controls = text.chars().filter(c -> Character.isISOControl(c)
                && c != '\n' && c != '\r' && c != '\t').count();
        double score = Math.max(0, 1.0 - (bad * 4.0 + controls * 2.0) / Math.max(1, text.length()));
        boolean needsReview = score < .985 || bad > 0 || controls > 0;
        return new Quality(needsReview ? "SUSPECT" : "PASS", score, needsReview);
    }

    private record Quality(String status, double score, boolean needsReview) { }

    private List<String> split(String text, int size, int overlap) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + size);
            if (end < text.length()) {
                int cut = text.lastIndexOf('\n', end);
                if (cut > start + size / 2) end = cut;
            }
            String part = text.substring(start, end).trim();
            if (!part.isBlank()) result.add(part);
            if (end >= text.length()) break;
            start = Math.max(start + 1, end - overlap);
        }
        return result;
    }
}
