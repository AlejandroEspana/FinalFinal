package com.empresa.mantenimiento.domain.service;

import com.empresa.mantenimiento.domain.model.ActuatorExecutionRecord;

/**
 * Servicio de Dominio Puro (sin dependencias de Spring) que formatea el registro de seguridad física.
 */
public class EmergencySafetyDomainService {

    public String formatPhysicalConsoleBanner(ActuatorExecutionRecord record) {
        return String.format(
                "🚨 [ACTUADOR FÍSICO DE EMERGENCIA] TRANSICIÓN DE ESTADO -> Máquina/Sensor: %s | Estado: %s -> %s | Operador: %s | Motivo: %s",
                record.sensorId(),
                record.previousState(),
                record.currentState(),
                record.operatorId(),
                record.reason()
        );
    }
}
