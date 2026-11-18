package com.empresa.mantenimiento.domain.repository;

import com.empresa.mantenimiento.application.dto.SensorPlantStatusDto;

public interface PlantStatusBroadcasterPort {

    void broadcastStatus(SensorPlantStatusDto statusDto);
}
