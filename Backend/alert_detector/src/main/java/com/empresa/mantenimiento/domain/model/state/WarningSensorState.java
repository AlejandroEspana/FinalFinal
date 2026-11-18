package com.empresa.mantenimiento.domain.model.state;

import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.model.strategy.DetectionResult;

public final class WarningSensorState implements SensorRiskState {

    private static final WarningSensorState INSTANCE = new WarningSensorState();

    public static WarningSensorState getInstance() {
        return INSTANCE;
    }

    private WarningSensorState() {}

    @Override
    public AlertSeverity getSeverity() {
        return AlertSeverity.WARNING;
    }

    @Override
    public SensorRiskState transition(DetectionResult result) {
        return switch (result.severity()) {
            case CRITICAL -> CriticalSensorState.getInstance();
            case WARNING -> this;
            case NORMAL -> NormalSensorState.getInstance();
        };
    }

    @Override
    public boolean shouldEmitAlert(DetectionResult result) {
        return result.severity() == AlertSeverity.CRITICAL;
    }

    @Override
    public boolean isOperationalSafe() {
        return false;
    }
}
