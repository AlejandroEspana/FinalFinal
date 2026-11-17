package com.empresa.mantenimiento.presentation.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO de Entrada (Bean Validation) para la decisión del operador humano.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OperatorDecisionRequest(
        @NotBlank(message = "El alertId es obligatorio")
        String alertId,

        @NotBlank(message = "El sensorId es obligatorio")
        String sensorId,

        @NotBlank(message = "La acción elegida (chosenAction) es obligatoria")
        @Pattern(
                regexp = "^(APAGADO_INMEDIATO|IGNORAR_10_MINUTOS|PROGRAMAR_MANTENIMIENTO_AHORA|RECONOCER_Y_ESPERAR_24H)$",
                message = "La acción elegida no es una opción de negocio válida"
        )
        String chosenAction,

        String operatorId,

        @Size(max = 500, message = "Las notas no pueden exceder 500 caracteres")
        String notes
) {
}
