package com.empresa.mantenimiento.domain.exception;

public class AlertSessionNotFoundException extends RuntimeException {

    public AlertSessionNotFoundException(String message) {
        super(message);
    }
}
