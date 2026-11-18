package com.empresa.mantenimiento.domain.model.state;

import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.model.strategy.DetectionResult;

public interface SensorRiskState {

    AlertSeverity getSeverity();

    SensorRiskState transition(DetectionResult result);

    boolean shouldEmitAlert(DetectionResult result);

    boolean isOperationalSafe();

    default String getStateName() {
        return getSeverity().getDisplayName();
    }
}
