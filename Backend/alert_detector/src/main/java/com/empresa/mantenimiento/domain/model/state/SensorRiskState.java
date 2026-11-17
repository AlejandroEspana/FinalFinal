package com.empresa.mantenimiento.domain.model.state;

import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.model.strategy.DetectionResult;

/**
 * Patrón State: Modela de forma polimórfica los estados operativos del sensor
 * y gobierna cuándo una transición amerita emitir una nueva alerta (evitando tormentas de alertas).
 */
public interface SensorRiskState {

    AlertSeverity getSeverity();

    SensorRiskState transition(DetectionResult result);

    /**
     * Determina polimórficamente si el resultado evaluado produce una transición o escalamiento
     * que deba emitir una alerta al bus de eventos.
     */
    boolean shouldEmitAlert(DetectionResult result);

    boolean isOperationalSafe();

    default String getStateName() {
        return getSeverity().getDisplayName();
    }
}
