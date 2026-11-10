package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.domain.model.ActuatorExecutionRecord;
import org.springframework.stereotype.Service;

@Service
public class EmergencySafetyDomainService {

    public String formatPhysicalConsoleBanner(ActuatorExecutionRecord record) {
        return String.format(
                "🚨 [ACTUADOR FÍSICO DE EMERGENCIA] CORTE DE ENERGÍA EJECUTADO -> Máquina/Sensor: %s | Estado: %s -> %s | Operador: %s | Motivo: %s",
                record.sensorId(),
                record.previousState(),
                record.currentState(),
                record.operatorId(),
                record.reason()
        );
    }
}
