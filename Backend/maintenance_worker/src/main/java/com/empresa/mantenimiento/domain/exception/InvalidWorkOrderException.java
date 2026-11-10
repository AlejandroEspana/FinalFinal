package com.empresa.mantenimiento.domain.exception;

public class InvalidWorkOrderException extends RuntimeException {
    public InvalidWorkOrderException(String message) {
        super(message);
    }
}
