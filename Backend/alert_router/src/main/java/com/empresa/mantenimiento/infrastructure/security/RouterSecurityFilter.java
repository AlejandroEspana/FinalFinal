package com.empresa.mantenimiento.infrastructure.security;

import org.springframework.stereotype.Component;

@Component
public class RouterSecurityFilter {
    public String getServiceRole() {
        return "ROLE_BRIDGE_ROUTER";
    }
}
