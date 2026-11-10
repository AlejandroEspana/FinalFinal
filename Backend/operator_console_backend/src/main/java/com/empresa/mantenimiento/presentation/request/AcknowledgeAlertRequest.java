package com.empresa.mantenimiento.presentation.request;

import jakarta.validation.constraints.NotBlank;

public record AcknowledgeAlertRequest(
        @NotBlank(message = "El alertId es obligatorio.")
        String alertId
) {
}
