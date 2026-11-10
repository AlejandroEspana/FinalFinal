package com.empresa.mantenimiento.domain.model.strategy;

import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;

import java.util.List;
import java.util.Objects;

/**
 * Estrategia alternativa basada en Promedio Ponderado Exponencial (EMA).
 */
public final class ExponentialMovingAverageStrategy implements AnomalyDetectionStrategy {

    private final double alpha;
    private final double criticalEmaThreshold;
    private final double warningEmaThreshold;

    public ExponentialMovingAverageStrategy() {
        this(0.35, 88.0, 72.0);
    }

    public ExponentialMovingAverageStrategy(double alpha, double criticalEmaThreshold, double warningEmaThreshold) {
        if (alpha <= 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Alpha debe estar en (0.0, 1.0].");
        }
        this.alpha = alpha;
        this.criticalEmaThreshold = criticalEmaThreshold;
        this.warningEmaThreshold = warningEmaThreshold;
    }

    @Override
    public String getStrategyName() {
        return "ExponentialMovingAverageStrategy(alpha=" + alpha + ")";
    }

    @Override
    public DetectionResult evaluate(List<VibrationReadingRecord> readings) {
        Objects.requireNonNull(readings, "readings no puede ser nulo");
        if (readings.isEmpty()) {
            return DetectionResult.normal("Ventana vacía.");
        }

        double ema = readings.get(0).vibrationValue();
        for (int i = 1; i < readings.size(); i++) {
            ema = alpha * readings.get(i).vibrationValue() + (1.0 - alpha) * ema;
        }
        double latest = readings.get(readings.size() - 1).vibrationValue();

        if (ema >= criticalEmaThreshold) {
            return DetectionResult.critical(latest, String.format("EMA Crítico: %.2f >= %.1f", ema, criticalEmaThreshold));
        } else if (ema >= warningEmaThreshold) {
            return DetectionResult.warning(latest, String.format("EMA Advertencia: %.2f >= %.1f", ema, warningEmaThreshold));
        }
        return DetectionResult.normal(String.format("EMA Normal: %.2f", ema));
    }
}
