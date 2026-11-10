package com.empresa.mantenimiento.domain.model.strategy;

import com.empresa.mantenimiento.domain.model.AlertSeverity;

import java.util.Objects;

public record DetectionResult(
        AlertSeverity severity,
        double triggerValue,
        String explanation,
        boolean requiresAlert
) {
    public DetectionResult {
        Objects.requireNonNull(severity, "La severidad no puede ser nula.");
        Objects.requireNonNull(explanation, "La explicación no puede ser nula.");
    }

    public static DetectionResult normal(String explanation) {
        return new DetectionResult(AlertSeverity.NORMAL, 0.0, explanation, false);
    }

    public static DetectionResult warning(double triggerValue, String explanation) {
        return new DetectionResult(AlertSeverity.WARNING, triggerValue, explanation, true);
    }

    public static DetectionResult critical(double triggerValue, String explanation) {
        return new DetectionResult(AlertSeverity.CRITICAL, triggerValue, explanation, true);
    }
}
