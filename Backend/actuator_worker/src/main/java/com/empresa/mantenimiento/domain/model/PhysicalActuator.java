package com.empresa.mantenimiento.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class PhysicalActuator {

    private final SensorId sensorId;
    private MachineOperationalState currentState;
    private Instant lastShutdownAt;

    public PhysicalActuator(SensorId sensorId) {
        this.sensorId = Objects.requireNonNull(sensorId, "sensorId no puede ser nulo");
        this.currentState = new RunningMachineState();
    }

    public synchronized ActuatorExecutionRecord ejecutarCorteEnergiaInmediato(ShutdownCommand command) {
        Objects.requireNonNull(command, "El comando de apagado no puede ser nulo");
        String prev = currentState.stateName();
        this.currentState = currentState.onEmergencyStop();
        this.lastShutdownAt = Instant.now();

        return new ActuatorExecutionRecord(
                command.commandId(),
                sensorId.value(),
                prev,
                this.currentState.stateName(),
                command.operatorId(),
                command.reason(),
                this.lastShutdownAt
        );
    }

    public synchronized ActuatorExecutionRecord reactivarMaquina(String operatorId, String reason) {
        String prev = currentState.stateName();
        this.currentState = currentState.onReset();
        Instant now = Instant.now();

        return new ActuatorExecutionRecord(
                "CMD-RESET-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                sensorId.value(),
                prev,
                this.currentState.stateName(),
                (operatorId != null && !operatorId.isBlank()) ? operatorId : "operator",
                (reason != null && !reason.isBlank()) ? reason : "Reactivación segura autorizada por operador",
                now
        );
    }

    public SensorId getSensorId() {
        return sensorId;
    }

    public synchronized String getCurrentStateName() {
        return currentState.stateName();
    }

    public synchronized Instant getLastShutdownAt() {
        return lastShutdownAt;
    }
}
