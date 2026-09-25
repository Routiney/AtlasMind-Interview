package com.plexus.personal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.plexus.personal.auth.application.InvalidCredentialsException;
import com.plexus.personal.conversation.application.ConversationNotFoundException;
import com.plexus.personal.user.application.UserAlreadyExistsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ApiError> handleValidation(WebExchangeBindException exception) {
        String message = exception.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数无效");
        return ResponseEntity.badRequest()
                .body(new ApiError("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleUserAlreadyExists(UserAlreadyExistsException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("USERNAME_ALREADY_EXISTS", "用户名已存在"));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiError("INVALID_CREDENTIALS", "用户名或密码错误"));
    }

    @ExceptionHandler(ConversationNotFoundException.class)
    public ResponseEntity<ApiError> handleConversationNotFound(ConversationNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("CONVERSATION_NOT_FOUND", "会话不存在"));
    }

    @ExceptionHandler(ResumeRequiredException.class)
    public ResponseEntity<ApiError> handleResumeRequired(ResumeRequiredException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("RESUME_REQUIRED", exception.getMessage()));
    }

    @ExceptionHandler(CareerPlanNotFoundException.class)
    public ResponseEntity<ApiError> handleCareerPlanNotFound(CareerPlanNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("PLAN_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ApiError> handleAgentUnavailable(WebClientRequestException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("AGENT_UNAVAILABLE", "Agent 服务暂时不可用"));
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiError> handleAgentError(WebClientResponseException exception) {
        ApiError coreError = readCoreError(exception);
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null || status.is2xxSuccessful()) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return ResponseEntity.status(status).body(coreError);
    }

    @ExceptionHandler(java.util.concurrent.TimeoutException.class)
    public ResponseEntity<ApiError> handleAgentTimeout(java.util.concurrent.TimeoutException exception) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(new ApiError("PLAN_TIMEOUT", "职业规划生成超时，请稍后重试。"));
    }

    private ApiError readCoreError(WebClientResponseException exception) {
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsString());
            String code = body.path("code").asText("AGENT_ERROR");
            String message = body.path("message").asText("Agent 服务返回错误");
            return new ApiError(code, message);
        } catch (Exception ignored) {
            return new ApiError("AGENT_ERROR", "Agent 服务返回错误");
        }
    }
}
