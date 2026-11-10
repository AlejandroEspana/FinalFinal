package com.empresa.mantenimiento.application.service;

import com.empresa.mantenimiento.application.dto.SensorPlantStatusDto;
import com.empresa.mantenimiento.application.usecase.UpdatePlantStatusUseCase;
import com.empresa.mantenimiento.domain.service.PlantMaterializedDomainService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class PlantMonitorApplicationService {

    private final PlantMaterializedDomainService materializedService;
    private final UpdatePlantStatusUseCase updateUseCase;

    public PlantMonitorApplicationService(
            PlantMaterializedDomainService materializedService,
            UpdatePlantStatusUseCase updateUseCase
    ) {
        this.materializedService = Objects.requireNonNull(materializedService, "materializedService no puede ser nulo");
        this.updateUseCase = Objects.requireNonNull(updateUseCase, "updateUseCase no puede ser nulo");
    }

    public List<SensorPlantStatusDto> getAllStatuses() {
        return materializedService.getAllViews().stream()
                .map(v -> new SensorPlantStatusDto(
                        v.getSensorId().value(),
                        v.getSeverity(),
                        v.getLastVibration(),
                        v.getStatusDescription(),
                        v.getLastUpdated()
                ))
                .toList();
    }

    public SensorPlantStatusDto resetSensor(String sensorId) {
        return updateUseCase.resetSensor(sensorId);
    }
}
