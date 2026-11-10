package com.empresa.mantenimiento.domain.exception;

public class DomainInvariantViolationException extends RuntimeException {

    public DomainInvariantViolationException(String message) {
        super(message);
    }
}
