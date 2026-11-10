package com.empresa.mantenimiento.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Entidad de Dominio Rico que representa un ticket técnico de mantenimiento y
 * gestiona su ciclo de vida sin setters anémicos.
 */
public class MaintenanceExecutionTicket {

    private final MaintenanceWorkOrder workOrder;
    private String ticketStatus;
    private String executionSummary;
    private Instant completedAt;

    public MaintenanceExecutionTicket(MaintenanceWorkOrder workOrder) {
        this.workOrder = Objects.requireNonNull(workOrder, "workOrder no puede ser nulo");
        this.ticketStatus = "RECEIVED";
    }

    public synchronized void completarOrdenTecnica() {
        this.ticketStatus = "COMPLETED";
        this.completedAt = Instant.now();
        if (workOrder.delayed()) {
            this.executionSummary = String.format(
                    "Mantenimiento DIFERIDO ejecutado tras expiración de temporizador (%d ms) en sensor %s",
                    workOrder.delayMillis(),
                    workOrder.sensorId().value()
            );
        } else {
            this.executionSummary = String.format(
                    "Mantenimiento INMEDIATO ejecutado por cuadrilla técnica en sensor %s",
                    workOrder.sensorId().value()
            );
        }
    }

    public MaintenanceWorkOrder getWorkOrder() {
        return workOrder;
    }

    public synchronized String getTicketStatus() {
        return ticketStatus;
    }

    public synchronized String getExecutionSummary() {
        return executionSummary;
    }

    public synchronized Instant getCompletedAt() {
        return completedAt;
    }
}
