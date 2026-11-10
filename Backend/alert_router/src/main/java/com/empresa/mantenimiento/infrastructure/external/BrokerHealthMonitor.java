package com.empresa.mantenimiento.infrastructure.external;

import org.springframework.stereotype.Component;

@Component
public class BrokerHealthMonitor {
    public boolean isBridgeOperational() {
        return true;
    }
}
