package com.empresa.mantenimiento.presentation.mapper;

import com.empresa.mantenimiento.application.dto.SensorPlantStatusDto;
import com.empresa.mantenimiento.presentation.response.SensorStatusResponse;
import org.springframework.stereotype.Component;

@Component
public class PlantStatusPresentationMapper {

    public SensorStatusResponse toResponse(SensorPlantStatusDto dto) {
        return new SensorStatusResponse(
                dto.sensorId(),
                dto.severity().name(),
                dto.lastVibration(),
                dto.statusDescription(),
                dto.lastUpdated()
        );
    }
}
