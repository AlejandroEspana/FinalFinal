package com.empresa.mantenimiento.domain.model;

import com.empresa.mantenimiento.domain.exception.InvalidWorkOrderException;

import java.time.Instant;
import java.util.Objects;

/**
 * Value Object inmutable (Java Record con validación Fail-Fast) para orden de mantenimiento.
 */
public record MaintenanceWorkOrder(
        String workOrderId,
        SensorId sensorId,
        String priority,
        boolean delayed,
        long delayMillis,
        String operatorId,
        Instant scheduledAt
) {
    public MaintenanceWorkOrder {
        if (workOrderId == null || workOrderId.isBlank()) {
            throw new InvalidWorkOrderException("El workOrderId no puede ser vacío.");
        }
        Objects.requireNonNull(sensorId, "El sensorId no puede ser nulo.");
        if (priority == null || priority.isBlank()) {
            throw new InvalidWorkOrderException("La prioridad no puede ser vacía.");
        }
        if (delayMillis < 0) {
            throw new InvalidWorkOrderException("El delayMillis no puede ser negativo.");
        }
        if (operatorId == null || operatorId.isBlank()) {
            throw new InvalidWorkOrderException("El operatorId es obligatorio.");
        }
        if (scheduledAt == null) {
            scheduledAt = Instant.now();
        }
    }
}
