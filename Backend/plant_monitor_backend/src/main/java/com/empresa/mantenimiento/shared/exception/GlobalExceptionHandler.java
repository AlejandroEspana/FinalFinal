package com.empresa.mantenimiento.shared.exception;

import com.empresa.mantenimiento.domain.exception.SensorNotFoundException;
import com.empresa.mantenimiento.shared.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SensorNotFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleSensorNotFound(SensorNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage(), null));
    }
}
