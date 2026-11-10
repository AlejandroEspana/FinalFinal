package com.empresa.mantenimiento.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateSensorStateRequest(
        @NotBlank(message = "El sensorId es obligatorio.")
        String sensorId
) {
}
