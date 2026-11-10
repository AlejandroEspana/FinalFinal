package com.empresa.mantenimiento.infrastructure.external;

import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class IndustrialClockProvider {
    public Instant now() {
        return Instant.now();
    }
}
