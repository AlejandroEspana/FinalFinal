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
        return switch (result.severity()) {
            case CRITICAL -> this;
            case WARNING -> WarningSensorState.getInstance();
            case NORMAL -> NormalSensorState.getInstance();
        };
    }

    @Override
    public boolean shouldEmitAlert(DetectionResult result) {
        // Mientras permanezca en estado CRITICAL no vuelve a emitir duplicados hasta bajar de estado
        return false;
    }

    @Override
    public boolean isOperationalSafe() {
        return false;
    }
}
