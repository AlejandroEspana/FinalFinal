package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;

/**
 * Puerto de Dominio (Inversión de Dependencias - DIP) para publicar lecturas de telemetría.
 */
public interface TelemetryEventPublisherPort {

    /**
     * Publica una lectura inmutable de vibración hacia el bróker usando sensorId como clave de partición.
     */
    void publish(VibrationReadingRecord reading);
}
