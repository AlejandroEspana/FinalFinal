package com.empresa.mantenimiento.infrastructure.security;

import org.springframework.stereotype.Component;

@Component
public class MonitorSecurityEvaluator {
    public boolean canAccessMonitor() {
        return true;
    }
}
