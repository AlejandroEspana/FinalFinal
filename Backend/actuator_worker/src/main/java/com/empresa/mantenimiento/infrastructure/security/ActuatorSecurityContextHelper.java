package com.empresa.mantenimiento.infrastructure.security;

import org.springframework.stereotype.Component;

@Component
public class ActuatorSecurityContextHelper {

    public String getWorkerRole() {
        return "ACTUATOR_WORKER";
    }
}
