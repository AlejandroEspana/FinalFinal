package com.empresa.mantenimiento.application.usecase;

import com.empresa.mantenimiento.application.dto.TelemetryEventDto;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.model.SensorSimulationProfile;
import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import com.empresa.mantenimiento.domain.repository.TelemetryEventPublisherPort;
import com.empresa.mantenimiento.domain.service.TelemetryGeneratorDomainService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Caso de Uso: Genera y publica una ráfaga de telemetría para los 10 sensores industriales.
 * Aplica Inyección Estricta por Constructor (DIP).
 */
@Component
public class GenerateTelemetryBatchUseCase {

    private final TelemetryEventPublisherPort publisherPort;
    private final TelemetryGeneratorDomainService generatorService;
    private final Map<String, SensorSimulationProfile> sensorProfiles;

    public GenerateTelemetryBatchUseCase(
            TelemetryEventPublisherPort publisherPort,
            TelemetryGeneratorDomainService generatorService
    ) {
        this.publisherPort = Objects.requireNonNull(publisherPort, "publisherPort no puede ser nulo");
        this.generatorService = Objects.requireNonNull(generatorService, "generatorService no puede ser nulo");
        this.sensorProfiles = new LinkedHashMap<>();

        List<String> ids = List.of(
                "sensor_A", "sensor_B", "sensor_C", "sensor_D", "sensor_E",
                "sensor_F", "sensor_G", "sensor_H", "sensor_I", "sensor_J"
        );
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            sensorProfiles.put(id, new SensorSimulationProfile(SensorId.of(id), "Sector Industrial #" + (i + 1)));
        }
    }

    public List<TelemetryEventDto> executeBatch() {
        List<TelemetryEventDto> results = new ArrayList<>(sensorProfiles.size());
        for (SensorSimulationProfile profile : sensorProfiles.values()) {
            double value = generatorService.calculateNextRandomVibration();
            VibrationReadingRecord record = profile.registrarEmision(value);
            publisherPort.publish(record);

            results.add(new TelemetryEventDto(
                    profile.getSensorId().value(),
                    record.vibrationValue(),
                    profile.getMachineSector(),
                    profile.getTotalReadingsEmitted(),
                    record.timestamp()
            ));
        }
        return results;
    }

    public SensorSimulationProfile getProfile(String sensorId) {
        return sensorProfiles.get(sensorId);
    }

    public Collection<SensorSimulationProfile> getAllProfiles() {
        return sensorProfiles.values();
    }
}
