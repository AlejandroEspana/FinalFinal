package com.empresa.mantenimiento.shared.exception;

import com.empresa.mantenimiento.domain.exception.InvalidActuatorCommandException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidActuatorCommandException.class)
    public ResponseEntity<Map<String, Object>> handleDomainException(InvalidActuatorCommandException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", ex.getMessage(),
                "timestamp", Instant.now()
        ));
    }
}
