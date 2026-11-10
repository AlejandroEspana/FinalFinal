package com.empresa.mantenimiento.domain.exception;

public class InvalidActuatorCommandException extends RuntimeException {
    public InvalidActuatorCommandException(String message) {
        super(message);
    }
}
