package com.empresa.mantenimiento.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record ManualShutdownRequest(
        @NotBlank(message = "El sensorId es obligatorio")
        String sensorId,
        @NotBlank(message = "El motivo es obligatorio")
        String reason,
        @NotBlank(message = "El operatorId es obligatorio")
        String operatorId
) {
}
