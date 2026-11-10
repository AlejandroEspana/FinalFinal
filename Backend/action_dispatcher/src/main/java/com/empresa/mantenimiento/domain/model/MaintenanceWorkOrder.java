package com.empresa.mantenimiento.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

public record MaintenanceWorkOrder(
        String orderId,
        SensorId sensorId,
        String actionType,
        boolean isDelayed,
        long delayMillis,
        String requestedBy,
        Instant scheduledAt
) implements Serializable {

    public MaintenanceWorkOrder {
        Objects.requireNonNull(orderId, "El orderId no puede ser nulo.");
        Objects.requireNonNull(sensorId, "El sensorId no puede ser nulo.");
        Objects.requireNonNull(actionType, "El actionType no puede ser nulo.");
        Objects.requireNonNull(requestedBy, "El requestedBy no puede ser nulo.");
        Objects.requireNonNull(scheduledAt, "El scheduledAt no puede ser nulo.");
        if (delayMillis < 0) {
            throw new IllegalArgumentException("delayMillis no puede ser negativo.");
        }
    }
}
