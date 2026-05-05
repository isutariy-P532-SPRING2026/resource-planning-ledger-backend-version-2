package edu.indiana.p532.rpl.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalTransition(IllegalStateTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadArg(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody(ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleIntegrityViolation(DataIntegrityViolationException ex) {
        String rootCause = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody("Data integrity error: " + rootCause));
    }

    private Map<String, Object> errorBody(String message) {
        return Map.of("message", message, "timestamp", Instant.now().toString());
    }
}
