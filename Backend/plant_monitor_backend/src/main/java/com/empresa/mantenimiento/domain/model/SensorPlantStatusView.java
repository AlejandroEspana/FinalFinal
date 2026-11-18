package com.empresa.mantenimiento.domain.model;

import java.time.Instant;
import java.util.Objects;

public class SensorPlantStatusView {

    public static final int SAFE_READINGS_FOR_AUTO_RECOVERY = 4;

    private final SensorId sensorId;
    private AlertSeverity severity;
    private String operationalStatus;
    private double lastVibration;
    private String statusDescription;
    private int consecutiveSafeReadings;
    private Instant lastUpdated;

    public SensorPlantStatusView(SensorId sensorId) {
        this.sensorId = Objects.requireNonNull(sensorId, "sensorId no puede ser nulo");
        this.severity = AlertSeverity.NORMAL;
        this.operationalStatus = "ACTIVE";
        this.lastVibration = 28.0;
        this.statusDescription = "Operación normal dentro de parámetros seguros.";
        this.consecutiveSafeReadings = 0;
        this.lastUpdated = Instant.now();
    }

    public synchronized void aplicarTelemetria(double vibrationValue) {
        if ("EMERGENCY_HALTED".equals(this.operationalStatus)) {
            this.lastVibration = 0.0;
            this.lastUpdated = Instant.now();
            return;
        }
        this.lastVibration = vibrationValue;
        this.lastUpdated = Instant.now();

        if (vibrationValue <= 75.0) {
            this.consecutiveSafeReadings++;
            if (this.severity != AlertSeverity.NORMAL && this.consecutiveSafeReadings >= SAFE_READINGS_FOR_AUTO_RECOVERY) {
                this.severity = AlertSeverity.NORMAL;
                this.operationalStatus = "ACTIVE";
                this.statusDescription = "Operación normal restablecida tras lecturas estables.";
            }
        } else {
            this.consecutiveSafeReadings = 0;
        }
    }

    public synchronized void aplicarAlerta(AlertSeverity newSeverity, double triggerValue, String description) {
        Objects.requireNonNull(newSeverity, "newSeverity no puede ser nula");
        if ("EMERGENCY_HALTED".equals(this.operationalStatus)) {
            return;
        }
        this.severity = newSeverity;
        this.lastVibration = triggerValue;
        this.consecutiveSafeReadings = 0;
        this.operationalStatus = (newSeverity == AlertSeverity.CRITICAL) ? "CRITICAL_ALERT" : "WARNING_ALERT";
        this.statusDescription = (description != null && !description.isBlank())
                ? description : "Condición anómala detectada.";
        this.lastUpdated = Instant.now();
    }

    public synchronized void detenerPorEmergencia(String reason) {
        this.operationalStatus = "EMERGENCY_HALTED";
        this.severity = AlertSeverity.CRITICAL;
        this.lastVibration = 0.0;
        this.consecutiveSafeReadings = 0;
        this.statusDescription = (reason != null && !reason.isBlank())
                ? reason : "Máquina detenida por corte de energía de emergencia.";
        this.lastUpdated = Instant.now();
    }

    public synchronized void normalizarEstado() {
        this.severity = AlertSeverity.NORMAL;
        this.operationalStatus = "ACTIVE";
        this.consecutiveSafeReadings = 0;
        this.statusDescription = "Restablecido a operación normal tras resolución o reactivación humana.";
        this.lastUpdated = Instant.now();
    }

    public SensorId getSensorId() {
        return sensorId;
    }

    public synchronized AlertSeverity getSeverity() {
        return severity;
    }

    public synchronized String getOperationalStatus() {
        return operationalStatus;
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
