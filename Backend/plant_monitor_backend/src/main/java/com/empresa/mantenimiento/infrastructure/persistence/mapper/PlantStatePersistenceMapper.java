package com.empresa.mantenimiento.infrastructure.persistence.mapper;

import com.empresa.mantenimiento.application.dto.SensorPlantStatusDto;
import com.empresa.mantenimiento.infrastructure.persistence.entity.PlantSensorStateEntity;
import org.springframework.stereotype.Component;

@Component
public class PlantStatePersistenceMapper {

    public PlantSensorStateEntity toEntity(SensorPlantStatusDto dto) {
        return new PlantSensorStateEntity(
                dto.sensorId(),
                dto.severity().name(),
                dto.lastVibration(),
                dto.statusDescription(),
                dto.lastUpdated()
        );
    }
}
