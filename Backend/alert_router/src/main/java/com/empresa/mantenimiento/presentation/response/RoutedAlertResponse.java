package com.empresa.mantenimiento.presentation.response;

import java.time.Instant;
import java.util.List;

public record RoutedAlertResponse(
        String alertId,
        String sensorId,
        String severity,
        List<String> availableOptions,
        Instant routedAt
) {
}
