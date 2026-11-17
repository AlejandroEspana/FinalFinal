package com.empresa.mantenimiento.domain.exception;

public class InvalidActuatorCommandException extends IllegalArgumentException {
    public InvalidActuatorCommandException(String message) {
        super(message);
    }
}
