package com.empresa.mantenimiento.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Entidad de Dominio Rico que modela el perfil y estado operativo de simulación
 * de un sensor físico en la planta. Protege sus invariantes sin setters públicos.
 */
public class SensorSimulationProfile {

    private final SensorId sensorId;
    private final String machineSector;
    private double lastGeneratedVibration;
    private long totalReadingsEmitted;
    private Instant lastEmittedAt;

    public SensorSimulationProfile(SensorId sensorId, String machineSector) {
        this.sensorId = Objects.requireNonNull(sensorId, "sensorId no puede ser nulo");
        this.machineSector = Objects.requireNonNull(machineSector, "machineSector no puede ser nulo");
        this.lastGeneratedVibration = 25.0;
        this.totalReadingsEmitted = 0L;
        this.lastEmittedAt = Instant.now();
    }

    /**
     * Método semántico de negocio: registra una lectura emitida por este sensor.
     */
    public synchronized VibrationReadingRecord registrarEmision(double vibrationValue) {
        VibrationReadingRecord record = new VibrationReadingRecord(this.sensorId, vibrationValue, Instant.now());
        this.lastGeneratedVibration = vibrationValue;
        this.totalReadingsEmitted++;
        this.lastEmittedAt = record.timestamp();
        return record;
    }

    public SensorId getSensorId() {
        return sensorId;
    }

    public String getMachineSector() {
        return machineSector;
    }

    public synchronized double getLastGeneratedVibration() {
        return lastGeneratedVibration;
    }

    public synchronized long getTotalReadingsEmitted() {
        return totalReadingsEmitted;
    }

    public synchronized Instant getLastEmittedAt() {
        return lastEmittedAt;
    }
}
