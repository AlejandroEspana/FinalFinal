package com.empresa.mantenimiento.presentation.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulateReadingRequest(
        @NotBlank(message = "El identificador del sensor (sensorId) es obligatorio.")
        String sensorId,

        @Min(value = 0, message = "El valor de vibración debe ser >= 0.0")
        @Max(value = 500, message = "El valor de vibración debe ser <= 500.0")
        @JsonAlias({"vibration", "value"})
        double vibrationValue,

        Integer burstCount
) {
    public SimulateReadingRequest(String sensorId, double vibrationValue) {
        this(sensorId, vibrationValue, 1);
    }

    public int resolvedBurstCount() {
        if (burstCount == null || burstCount < 1) {
            return 1;
        }
        return Math.min(burstCount, 10);
    }
}
