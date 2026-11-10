package com.empresa.mantenimiento.domain.model.strategy;

import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;

import java.util.List;
import java.util.Objects;

/**
 * Estrategia de detección basada en reglas de umbral:
 * - Regla 1 (Crítica): Lectura actual > 90.0
 * - Regla 2 (Advertencia): Al menos 3 lecturas acumuladas en la ventana > 75.0
 */
public final class RuleBasedThresholdStrategy implements AnomalyDetectionStrategy {

    public static final double CRITICAL_THRESHOLD = 90.0;
    public static final double WARNING_THRESHOLD = 75.0;
    public static final int REQUIRED_WARNING_COUNT = 3;

    @Override
    public String getStrategyName() {
        return "RuleBasedThresholdStrategy(Crítico>90, Advertencia>75 x3)";
    }

    @Override
    public DetectionResult evaluate(List<VibrationReadingRecord> readings) {
        Objects.requireNonNull(readings, "La lista de lecturas no puede ser nula.");
        if (readings.isEmpty()) {
            return DetectionResult.normal("Ventana vacía.");
        }

        VibrationReadingRecord latest = readings.get(readings.size() - 1);
        double latestValue = latest.vibrationValue();

        if (latestValue > CRITICAL_THRESHOLD) {
            return DetectionResult.critical(
                    latestValue,
                    String.format("Falla Crítica Inmediata: Vibración actual de %.2f supera el umbral crítico de %.1f.",
                            latestValue, CRITICAL_THRESHOLD)
            );
        }

        long countAboveWarning = readings.stream()
                .filter(r -> r.vibrationValue() > WARNING_THRESHOLD)
                .count();

        if (countAboveWarning >= REQUIRED_WARNING_COUNT) {
            return DetectionResult.warning(
                    latestValue,
                    String.format("Patrón de Advertencia Detectado: %d lecturas acumuladas superan %.1f (última: %.2f).",
                            countAboveWarning, WARNING_THRESHOLD, latestValue)
            );
        }

        return DetectionResult.normal(
                String.format("Operación Normal: Última lectura %.2f dentro de parámetros seguros.", latestValue)
        );
    }
}
