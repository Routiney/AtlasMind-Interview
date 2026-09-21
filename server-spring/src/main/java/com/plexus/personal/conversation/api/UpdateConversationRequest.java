package com.plexus.personal.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateConversationRequest(
        @NotBlank(message = "会话标题不能为空")
        @Size(max = 200, message = "会话标题不能超过 200 个字符")
        String title
) {
}
