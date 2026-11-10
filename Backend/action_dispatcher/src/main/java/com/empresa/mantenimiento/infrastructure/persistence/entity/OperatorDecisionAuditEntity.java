package com.empresa.mantenimiento.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entidad JPA para la tabla relacional de auditoría transaccional en PostgreSQL (operator_decision_audit).
 */
@Entity
@Table(name = "operator_decision_audit")
public class OperatorDecisionAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alert_id", nullable = false, length = 100)
    private String alertId;

    @Column(name = "sensor_id", nullable = false, length = 64)
    private String sensorId;

    @Column(name = "chosen_action", nullable = false, length = 80)
    private String chosenAction;

    @Column(name = "operator_id", nullable = false, length = 80)
    private String operatorId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "routing_target", nullable = false, length = 120)
    private String routingTarget;

    @Column(name = "delay_millis", nullable = false)
    private Long delayMillis;

    @Column(name = "notes", length = 512)
    private String notes;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected OperatorDecisionAuditEntity() {
        // Constructor requerido por JPA
    }

    public OperatorDecisionAuditEntity(
            String alertId,
            String sensorId,
            String chosenAction,
            String operatorId,
            String status,
            String routingTarget,
            Long delayMillis,
            String notes,
            Instant recordedAt
    ) {
        this.alertId = alertId;
        this.sensorId = sensorId;
        this.chosenAction = chosenAction;
        this.operatorId = operatorId;
        this.status = status;
        this.routingTarget = routingTarget;
        this.delayMillis = delayMillis;
        this.notes = notes;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public String getAlertId() {
        return alertId;
    }

    public String getSensorId() {
        return sensorId;
    }

    public String getChosenAction() {
        return chosenAction;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public String getStatus() {
        return status;
    }

    public String getRoutingTarget() {
        return routingTarget;
    }

    public Long getDelayMillis() {
        return delayMillis;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
