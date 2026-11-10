package com.empresa.mantenimiento.presentation.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SimulateReadingRequest(
        @NotBlank(message = "El identificador del sensor (sensorId) es obligatorio.")
        String sensorId,

        @Min(value = 0, message = "El valor de vibración debe ser >= 0.0")
        @Max(value = 500, message = "El valor de vibración debe ser <= 500.0")
        double vibrationValue
) {
}
