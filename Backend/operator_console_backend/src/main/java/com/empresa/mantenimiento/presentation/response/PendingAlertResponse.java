package com.empresa.mantenimiento.presentation.response;

import java.time.Instant;
import java.util.List;

public record PendingAlertResponse(
        String alertId,
        String sensorId,
        String severity,
        double triggerValue,
        String description,
        List<String> availableOptions,
        Instant timestamp
) {
}
