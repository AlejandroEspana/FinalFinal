package com.empresa.mantenimiento.infrastructure.external;

import org.springframework.stereotype.Component;

@Component
public class PlantTelemetryHealthIndicator {
    public String checkStatus() {
        return "UP";
    }
}
