package com.empresa.mantenimiento.infrastructure.security;

import org.springframework.stereotype.Component;

@Component
public class DetectorSecurityContext {
    public String getServiceIdentity() {
        return "alert_detector_service";
    }
}
