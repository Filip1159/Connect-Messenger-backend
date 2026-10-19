package com.example.connectmessenger.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * One JSON error shape ({status, error, message}) for the whole API; unexpected exceptions are logged and
 * reported as a generic 500 so internals never reach the client. Framework exceptions that already map to a
 * proper status (malformed body -> 400, wrong method -> 405, ...) keep Spring's own handling.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        return body(e.getStatusCode(), e.getReason());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.CONTENT_TOO_LARGE, "File is too large");
    }

    @ExceptionHandler(AccessDeniedException.class)
    void rethrowAccessDenied(AccessDeniedException e) {
        throw e;  // let the security chain answer 401/403
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) throws Exception {
        if (e instanceof org.springframework.web.ErrorResponse errorResponse) {
            // framework exceptions that carry their own status (400/404/405/415 ...)
            return body(errorResponse.getStatusCode(), e.getMessage());
        }
        log.error("Unhandled exception", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    private static ResponseEntity<Map<String, Object>> body(HttpStatusCode status, String message) {
        String error = status instanceof HttpStatus http ? http.getReasonPhrase() : String.valueOf(status.value());
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", error,
                "message", message == null ? "" : message));
    }
}
