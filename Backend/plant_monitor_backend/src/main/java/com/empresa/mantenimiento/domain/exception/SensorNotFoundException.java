package com.empresa.mantenimiento.domain.exception;

public class SensorNotFoundException extends RuntimeException {

    public SensorNotFoundException(String message) {
        super(message);
    }
}
