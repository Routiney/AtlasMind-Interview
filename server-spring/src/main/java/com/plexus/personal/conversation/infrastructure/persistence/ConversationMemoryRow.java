package com.plexus.personal.conversation.infrastructure.persistence;

import java.time.OffsetDateTime;

public class ConversationMemoryRow {
    private Long conversationId;
    private String summary;
    private String factsJson;
    private long version;
    private long forgottenBefore;
    private long coveredUntilMessageId;
    private OffsetDateTime updatedAt;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getFactsJson() { return factsJson; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public long getForgottenBefore() { return forgottenBefore; }
    public void setForgottenBefore(long value) { this.forgottenBefore = value; }
    public long getCoveredUntilMessageId() { return coveredUntilMessageId; }
    public void setCoveredUntilMessageId(long value) { this.coveredUntilMessageId = value; }
    public void setFactsJson(String factsJson) { this.factsJson = factsJson; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
