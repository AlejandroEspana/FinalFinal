package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.application.dto.SensorPlantStatusDto;

/**
 * Puerto de Dominio (Inversión de Dependencias - DIP) para difundir el estado por WebSocket.
 */
public interface PlantStatusBroadcasterPort {

    void broadcastStatus(SensorPlantStatusDto statusDto);
}
