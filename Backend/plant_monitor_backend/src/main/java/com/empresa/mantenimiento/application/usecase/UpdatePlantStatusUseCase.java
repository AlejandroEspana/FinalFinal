package com.empresa.mantenimiento.application.usecase;

import com.empresa.mantenimiento.application.dto.SensorPlantStatusDto;
import com.empresa.mantenimiento.domain.model.IndustrialAlert;
import com.empresa.mantenimiento.domain.model.SensorPlantStatusView;
import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import com.empresa.mantenimiento.domain.repository.PlantStatusBroadcasterPort;
import com.empresa.mantenimiento.domain.service.PlantMaterializedDomainService;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class UpdatePlantStatusUseCase {

    private final PlantMaterializedDomainService materializedService;
    private final PlantStatusBroadcasterPort broadcasterPort;

    public UpdatePlantStatusUseCase(
            PlantMaterializedDomainService materializedService,
            PlantStatusBroadcasterPort broadcasterPort
    ) {
        this.materializedService = Objects.requireNonNull(materializedService, "materializedService no puede ser nulo");
        this.broadcasterPort = Objects.requireNonNull(broadcasterPort, "broadcasterPort no puede ser nulo");
    }

    public SensorPlantStatusDto applyTelemetry(VibrationReadingRecord reading) {
        Objects.requireNonNull(reading, "reading no puede ser nula");
        SensorPlantStatusView view = materializedService.getOrCreate(reading.sensorId().value());
        view.aplicarTelemetria(reading.vibrationValue());
        SensorPlantStatusDto dto = toDto(view);
        broadcasterPort.broadcastStatus(dto);
        return dto;
    }

    public SensorPlantStatusDto applyAlert(IndustrialAlert alert) {
        Objects.requireNonNull(alert, "alert no puede ser nula");
        SensorPlantStatusView view = materializedService.getOrCreate(alert.sensorId().value());
        view.aplicarAlerta(alert.severity(), alert.triggerValue(), alert.description());
        SensorPlantStatusDto dto = toDto(view);
        broadcasterPort.broadcastStatus(dto);
        return dto;
    }

    public SensorPlantStatusDto haltSensor(String sensorId, String reason) {
        SensorPlantStatusView view = materializedService.getOrCreate(sensorId);
        view.detenerPorEmergencia(reason);
        SensorPlantStatusDto dto = toDto(view);
        broadcasterPort.broadcastStatus(dto);
        return dto;
    }

    public SensorPlantStatusDto resetSensor(String sensorId) {
        SensorPlantStatusView view = materializedService.getOrCreate(sensorId);
        view.normalizarEstado();
        SensorPlantStatusDto dto = toDto(view);
        broadcasterPort.broadcastStatus(dto);
        return dto;
    }

    private SensorPlantStatusDto toDto(SensorPlantStatusView view) {
        return new SensorPlantStatusDto(
                view.getSensorId().value(),
                view.getSeverity(),
                view.getLastVibration(),
                view.getOperationalStatus(),
                view.getStatusDescription(),
                view.getLastUpdated()
        );
    }
}
