package com.plexus.personal;

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

    @ExceptionHandler(WebClientRequestException.class)
    public ResponseEntity<ApiError> handleAgentUnavailable(WebClientRequestException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("AGENT_UNAVAILABLE", "Agent 服务暂时不可用"));
    }

    @ExceptionHandler(WebClientResponseException.class)
    public ResponseEntity<ApiError> handleAgentError(WebClientResponseException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("AGENT_ERROR", "Agent 服务返回错误"));
    }
}
