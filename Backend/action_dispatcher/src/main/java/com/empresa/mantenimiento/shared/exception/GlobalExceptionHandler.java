package com.empresa.mantenimiento.shared.exception;

import com.empresa.mantenimiento.domain.exception.InvalidDecisionActionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Error de validación de DTO (Bean Validation)");
        body.put("details", fieldErrors);
        body.put("timestamp", Instant.now());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({InvalidDecisionActionException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleDomainError(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", ex.getMessage(),
                "timestamp", Instant.now()
        ));
    }
}
