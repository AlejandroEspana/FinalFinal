package com.empresa.mantenimiento.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Value Object (Record) que modela el contrato inmutable de una decisión de operador.
 */
public record OperatorDecisionRecord(
        AlertId alertId,
        SensorId sensorId,
        String chosenAction,
        String operatorId,
        Instant timestamp,
        String notes
) implements Serializable {

    public OperatorDecisionRecord {
        Objects.requireNonNull(alertId, "El alertId no puede ser nulo.");
        Objects.requireNonNull(sensorId, "El sensorId no puede ser nulo.");
        Objects.requireNonNull(chosenAction, "La acción elegida no puede ser nula.");
        Objects.requireNonNull(operatorId, "El identificador del operador no puede ser nulo.");
        Objects.requireNonNull(timestamp, "El timestamp no puede ser nulo.");
        if (chosenAction.trim().isEmpty()) {
            throw new IllegalArgumentException("La acción elegida no puede estar vacía.");
        }
        chosenAction = chosenAction.trim().toUpperCase();
        notes = (notes == null) ? "" : notes;
    }
}
