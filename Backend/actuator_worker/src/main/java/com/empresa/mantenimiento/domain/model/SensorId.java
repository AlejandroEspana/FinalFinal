package com.empresa.mantenimiento.domain.model;

import com.empresa.mantenimiento.domain.exception.InvalidActuatorCommandException;

public record SensorId(String value) {
    public SensorId {
        if (value == null || value.isBlank()) {
            throw new InvalidActuatorCommandException("El sensorId en el actuador no puede ser nulo ni vacío.");
        }
    }
}
