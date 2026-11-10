package com.empresa.mantenimiento.infrastructure.security;

import org.springframework.stereotype.Component;

@Component
public class MaintenanceWorkerSecurityHelper {

    public String getRoleName() {
        return "MAINTENANCE_WORKER";
    }
}
