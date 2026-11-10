package com.empresa.mantenimiento.domain.model.strategy;

import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;

import java.util.List;

/**
 * Patrón Strategy: Algoritmo intercambiable para la evaluación de ventanas móviles de telemetría.
 */
public interface AnomalyDetectionStrategy {

    String getStrategyName();

    DetectionResult evaluate(List<VibrationReadingRecord> readings);
}
