package com.empresa.mantenimiento.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Entidad de Dominio Rico que representa el estado materializado de un sensor en la planta.
 * Protege invariantes mediante métodos semánticos (aplicarTelemetria, aplicarAlerta, normalizarEstado).
 */
public class SensorPlantStatusView {

    private final SensorId sensorId;
    private AlertSeverity severity;
    private double lastVibration;
    private String statusDescription;
    private Instant lastUpdated;

    public SensorPlantStatusView(SensorId sensorId) {
        this.sensorId = Objects.requireNonNull(sensorId, "sensorId no puede ser nulo");
        this.severity = AlertSeverity.NORMAL;
        this.lastVibration = 28.0;
        this.statusDescription = "Operación normal dentro de parámetros seguros.";
        this.lastUpdated = Instant.now();
    }

    public synchronized void aplicarTelemetria(double vibrationValue) {
        this.lastVibration = vibrationValue;
        this.lastUpdated = Instant.now();
    }

    public synchronized void aplicarAlerta(AlertSeverity newSeverity, double triggerValue, String description) {
        Objects.requireNonNull(newSeverity, "newSeverity no puede ser nula");
        this.severity = newSeverity;
        this.lastVibration = triggerValue;
        this.statusDescription = (description != null && !description.isBlank())
                ? description : "Condición anómala detectada.";
        this.lastUpdated = Instant.now();
    }

    public synchronized void normalizarEstado() {
        this.severity = AlertSeverity.NORMAL;
        this.statusDescription = "Restablecido a operación normal tras resolución humana.";
        this.lastUpdated = Instant.now();
    }

    public SensorId getSensorId() {
        return sensorId;
    }

    public synchronized AlertSeverity getSeverity() {
        return severity;
    }

    public synchronized double getLastVibration() {
        return lastVibration;
    }

    public synchronized String getStatusDescription() {
        return statusDescription;
    }

    public synchronized Instant getLastUpdated() {
        return lastUpdated;
    }
}
