package com.empresa.mantenimiento.presentation.mapper;

import com.empresa.mantenimiento.application.dto.TelemetryEventDto;
import com.empresa.mantenimiento.domain.model.SensorSimulationProfile;
import com.empresa.mantenimiento.presentation.response.SimulationStatusResponse;
import org.springframework.stereotype.Component;

@Component
public class SimulationPresentationMapper {

    public SimulationStatusResponse toResponse(TelemetryEventDto dto) {
        return new SimulationStatusResponse(
                dto.sensorId(),
                dto.vibrationValue(),
                dto.machineSector(),
                dto.totalReadingsEmitted(),
                dto.timestamp()
        );
    }

    public SimulationStatusResponse toResponse(SensorSimulationProfile profile) {
        return new SimulationStatusResponse(
                profile.getSensorId().value(),
                profile.getLastGeneratedVibration(),
                profile.getMachineSector(),
                profile.getTotalReadingsEmitted(),
                profile.getLastEmittedAt()
        );
    }
}
