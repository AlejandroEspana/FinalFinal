package com.empresa.mantenimiento.infrastructure.external;

import org.springframework.stereotype.Component;

@Component
public class PlcHardwareSimulatorClient {

    public boolean sendModBusTripSignal(String sensorId) {
        return sensorId != null && !sensorId.isBlank();
    }
}
