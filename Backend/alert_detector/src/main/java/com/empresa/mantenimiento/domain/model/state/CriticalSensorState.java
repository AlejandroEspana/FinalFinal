package com.empresa.mantenimiento.domain.model.state;

import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.model.strategy.DetectionResult;

public final class CriticalSensorState implements SensorRiskState {

    private static final CriticalSensorState INSTANCE = new CriticalSensorState();

    public static CriticalSensorState getInstance() {
        return INSTANCE;
    }

    private CriticalSensorState() {}

    @Override
    public AlertSeverity getSeverity() {
        return AlertSeverity.CRITICAL;
    }

    @Override
    public SensorRiskState transition(DetectionResult result) {
        if (result.severity() == AlertSeverity.NORMAL) {
            return WarningSensorState.getInstance();
        }
        return this;
    }

    @Override
    public boolean isOperationalSafe() {
        return false;
    }
}
