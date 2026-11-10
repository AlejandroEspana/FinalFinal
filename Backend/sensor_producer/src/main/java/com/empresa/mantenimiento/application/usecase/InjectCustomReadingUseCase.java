package com.empresa.mantenimiento.application.usecase;

import com.empresa.mantenimiento.application.dto.TelemetryEventDto;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.model.SensorSimulationProfile;
import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import com.empresa.mantenimiento.domain.repository.TelemetryEventPublisherPort;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Caso de Uso: Permite inyectar una lectura manual específica (ej. pico crítico o advertencia)
 * para validar escenarios de extremo a extremo desde el Frontend.
 */
@Component
public class InjectCustomReadingUseCase {

    private final TelemetryEventPublisherPort publisherPort;
    private final GenerateTelemetryBatchUseCase batchUseCase;

    public InjectCustomReadingUseCase(
            TelemetryEventPublisherPort publisherPort,
            GenerateTelemetryBatchUseCase batchUseCase
    ) {
        this.publisherPort = Objects.requireNonNull(publisherPort, "publisherPort no puede ser nulo");
        this.batchUseCase = Objects.requireNonNull(batchUseCase, "batchUseCase no puede ser nulo");
    }

    public TelemetryEventDto execute(String sensorIdStr, double vibrationValue) {
        SensorId sensorId = SensorId.of(sensorIdStr);
        SensorSimulationProfile profile = batchUseCase.getProfile(sensorId.value());

        VibrationReadingRecord record;
        String sector = "Sector Manual";
        long count = 1L;

        if (profile != null) {
            record = profile.registrarEmision(vibrationValue);
            sector = profile.getMachineSector();
            count = profile.getTotalReadingsEmitted();
        } else {
            record = VibrationReadingRecord.of(sensorId.value(), vibrationValue);
        }

        publisherPort.publish(record);

        return new TelemetryEventDto(
                sensorId.value(),
                record.vibrationValue(),
                sector,
                count,
                record.timestamp()
        );
    }
}
