package com.empresa.mantenimiento.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record ManualWorkOrderRequest(
        @NotBlank(message = "El sensorId es obligatorio")
        String sensorId,
        @NotBlank(message = "La prioridad es obligatoria")
        String priority,
        @NotBlank(message = "El operatorId es obligatorio")
        String operatorId
) {
}
