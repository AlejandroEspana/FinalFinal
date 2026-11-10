package com.empresa.mantenimiento.application.dto;

import java.time.Instant;

public record AlertDetectedDto(
        String alertId,
        String sensorId,
        String severity,
        double triggerValue,
        String description,
        Instant timestamp
) {
}
