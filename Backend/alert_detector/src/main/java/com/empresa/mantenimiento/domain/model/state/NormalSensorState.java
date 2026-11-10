package com.empresa.mantenimiento.domain.model.state;

import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.model.strategy.DetectionResult;

public final class NormalSensorState implements SensorRiskState {

    private static final NormalSensorState INSTANCE = new NormalSensorState();

    public static NormalSensorState getInstance() {
        return INSTANCE;
    }

    private NormalSensorState() {}

    @Override
    public AlertSeverity getSeverity() {
        return AlertSeverity.NORMAL;
    }

    @Override
    public SensorRiskState transition(DetectionResult result) {
        return switch (result.severity()) {
            case CRITICAL -> CriticalSensorState.getInstance();
            case WARNING -> WarningSensorState.getInstance();
            case NORMAL -> this;
        };
    }

    @Override
    public boolean isOperationalSafe() {
        return true;
    }
}
