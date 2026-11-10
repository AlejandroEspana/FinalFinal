package com.empresa.mantenimiento.application.dto;

import java.time.Instant;
import java.util.List;

public record RoutedAlertDto(
        String alertId,
        String sensorId,
        String severity,
        double triggerValue,
        String description,
        List<String> availableOptions,
        Instant timestamp
) {
}
