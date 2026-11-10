package com.empresa.mantenimiento.infrastructure.security;

import org.springframework.stereotype.Component;

@Component
public class OperatorConsoleAuthContext {
    public String getActiveOperator() {
        return "operator";
    }
}
