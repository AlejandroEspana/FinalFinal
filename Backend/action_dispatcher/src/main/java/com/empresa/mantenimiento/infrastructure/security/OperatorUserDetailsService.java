package com.empresa.mantenimiento.infrastructure.security;

import org.springframework.stereotype.Component;

@Component
public class OperatorUserDetailsService {

    public boolean isValidRole(String role) {
        return "OPERATOR".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role);
    }
}
