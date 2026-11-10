package com.empresa.mantenimiento.domain.model;

import com.empresa.mantenimiento.domain.exception.InvalidDecisionActionException;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Entidad de Dominio Rico que agrupa estado y comportamiento de una resolución humana.
 * Protege invariantes mediante el método semántico aplicarAccion().
 */
public class MaintenanceDecision {

    public static final String ACTION_SHUTDOWN_NOW = "APAGADO_INMEDIATO";
    public static final String ACTION_IGNORE_10MIN = "IGNORAR_10_MINUTOS";
    public static final String ACTION_MAINTENANCE_NOW = "PROGRAMAR_MANTENIMIENTO_AHORA";
    public static final String ACTION_ACK_WAIT_24H = "RECONOCER_Y_ESPERAR_24H";

    private static final Set<String> VALID_ACTIONS = Set.of(
            ACTION_SHUTDOWN_NOW,
            ACTION_IGNORE_10MIN,
            ACTION_MAINTENANCE_NOW,
            ACTION_ACK_WAIT_24H
    );

    private final OperatorDecisionRecord decisionRecord;
    private String status;
    private String routingTarget;
    private long delayMillis;
    private Instant processedAt;

    public MaintenanceDecision(OperatorDecisionRecord decisionRecord) {
        this.decisionRecord = Objects.requireNonNull(decisionRecord, "decisionRecord no puede ser nulo");
        if (!VALID_ACTIONS.contains(decisionRecord.chosenAction())) {
            throw new InvalidDecisionActionException("Acción no válida: " + decisionRecord.chosenAction());
        }
        this.status = "PENDING";
        this.routingTarget = "UNASSIGNED";
        this.delayMillis = 0L;
    }

    /**
     * Método semántico de negocio: aplica la decisión y determina el enrutamiento y retraso.
     */
    public synchronized void aplicarAccion(long configuredDelayMillis) {
        switch (decisionRecord.chosenAction()) {
            case ACTION_SHUTDOWN_NOW -> {
                this.routingTarget = "critical_actions_queue";
                this.delayMillis = 0L;
            }
            case ACTION_MAINTENANCE_NOW -> {
                this.routingTarget = "maintenance_queue";
                this.delayMillis = 0L;
            }
            case ACTION_ACK_WAIT_24H -> {
                this.routingTarget = "delayed_actions_exchange (x-delay: " + configuredDelayMillis + " ms)";
                this.delayMillis = configuredDelayMillis;
            }
            case ACTION_IGNORE_10MIN -> {
                this.routingTarget = "NONE (Alerta ignorada temporalmente)";
                this.delayMillis = 0L;
            }
            default -> throw new InvalidDecisionActionException("Acción desconocida: " + decisionRecord.chosenAction());
        }
        this.status = "DISPATCHED";
        this.processedAt = Instant.now();
    }

    public OperatorDecisionRecord getDecisionRecord() {
        return decisionRecord;
    }

    public synchronized String getStatus() {
        return status;
    }

    public synchronized String getRoutingTarget() {
        return routingTarget;
    }

    public synchronized long getDelayMillis() {
        return delayMillis;
    }

    public synchronized Instant getProcessedAt() {
        return processedAt;
    }
}
