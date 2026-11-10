package com.empresa.mantenimiento.domain.model.state;

import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.model.strategy.DetectionResult;

/**
 * Patrón State: Modela de forma polimórfica los estados operativos del sensor.
 */
public interface SensorRiskState {

    AlertSeverity getSeverity();

    SensorRiskState transition(DetectionResult result);

    boolean isOperationalSafe();

    default String getStateName() {
        return getSeverity().getDisplayName();
    }
}
