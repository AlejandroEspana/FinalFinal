package com.empresa.mantenimiento.domain.model;

import com.empresa.mantenimiento.domain.exception.DomainInvariantViolationException;
import com.empresa.mantenimiento.domain.model.state.NormalSensorState;
import com.empresa.mantenimiento.domain.model.state.SensorRiskState;
import com.empresa.mantenimiento.domain.model.strategy.AnomalyDetectionStrategy;
import com.empresa.mantenimiento.domain.model.strategy.DetectionResult;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class SensorVibrationWindow implements Serializable {

    public static final int DEFAULT_WINDOW_CAPACITY = 10;

    private final SensorId sensorId;
    private final int capacity;
    private final List<VibrationReadingRecord> readings;
    private SensorRiskState currentState;
    private Instant lastEvaluationAt;

    public SensorVibrationWindow(SensorId sensorId) {
        this(sensorId, DEFAULT_WINDOW_CAPACITY);
    }

    public SensorVibrationWindow(SensorId sensorId, int capacity) {
        Objects.requireNonNull(sensorId, "El sensorId no puede ser nulo.");
        if (capacity <= 0) {
            throw new DomainInvariantViolationException("La capacidad de la ventana debe ser un entero positivo.");
        }
        this.sensorId = sensorId;
        this.capacity = capacity;
        this.readings = new ArrayList<>(capacity);
        this.currentState = NormalSensorState.getInstance();
        this.lastEvaluationAt = Instant.now();
    }

    public synchronized void registrarLectura(VibrationReadingRecord reading) {
        Objects.requireNonNull(reading, "La lectura no puede ser nula.");
        if (!this.sensorId.equals(reading.sensorId())) {
            throw new DomainInvariantViolationException(
                    String.format("Invariante violada: La lectura pertenece a %s pero la ventana es de %s.",
                            reading.sensorId(), this.sensorId));
        }
        if (this.readings.size() >= this.capacity) {
            this.readings.remove(0);
        }
        this.readings.add(reading);
    }

    public synchronized DetectionResult evaluarCriticidad(AnomalyDetectionStrategy strategy) {
        Objects.requireNonNull(strategy, "La estrategia no puede ser nula.");
        this.lastEvaluationAt = Instant.now();
        return strategy.evaluate(Collections.unmodifiableList(this.readings));
    }

    public synchronized Optional<IndustrialAlert> procesarTelemetria(
            VibrationReadingRecord reading,
            AnomalyDetectionStrategy strategy
    ) {
        registrarLectura(reading);
        DetectionResult result = evaluarCriticidad(strategy);
        boolean emitAlert = this.currentState.shouldEmitAlert(result);
        this.currentState = this.currentState.transition(result);

        if (emitAlert && result.requiresAlert()) {
            return Optional.of(IndustrialAlert.create(
                    this.sensorId,
                    result.severity(),
                    result.triggerValue(),
                    result.explanation()
            ));
        }
        return Optional.empty();
    }

    public synchronized void resetear() {
        this.readings.clear();
        this.currentState = NormalSensorState.getInstance();
        this.lastEvaluationAt = Instant.now();
    }

    public SensorId getSensorId() {
        return sensorId;
    }

    public synchronized SensorRiskState getEstadoActual() {
        return currentState;
    }

    public synchronized AlertSeverity getSeveridadActual() {
        return currentState.getSeverity();
    }

    public synchronized List<VibrationReadingRecord> getVentanaHistorica() {
        return Collections.unmodifiableList(new ArrayList<>(readings));
    }

    public synchronized int getCantidadLecturas() {
        return readings.size();
    }

    public synchronized double getPromedioVibracion() {
        if (readings.isEmpty()) return 0.0;
        return readings.stream().mapToDouble(VibrationReadingRecord::vibrationValue).average().orElse(0.0);
    }

    public Instant getLastEvaluationAt() {
        return lastEvaluationAt;
    }
}
