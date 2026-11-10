package com.empresa.mantenimiento.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record ManualRouteRequest(
        @NotBlank(message = "El alertId es obligatorio.")
        String alertId,
        @NotBlank(message = "El sensorId es obligatorio.")
        String sensorId,
        @NotBlank(message = "La severidad es obligatoria (CRITICAL o WARNING).")
        String severity,
        double triggerValue,
        String description
) {
}
