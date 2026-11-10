package com.empresa.mantenimiento.application.usecase;

import com.empresa.mantenimiento.domain.model.IndustrialAlert;
import com.empresa.mantenimiento.domain.model.SensorVibrationWindow;
import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import com.empresa.mantenimiento.domain.model.strategy.AnomalyDetectionStrategy;
import com.empresa.mantenimiento.domain.repository.AlertPublisherPort;
import com.empresa.mantenimiento.domain.service.WindowRegistryDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
public class EvaluateSensorTelemetryUseCase {

    private static final Logger log = LoggerFactory.getLogger(EvaluateSensorTelemetryUseCase.class);

    private final WindowRegistryDomainService windowRegistry;
    private final AnomalyDetectionStrategy detectionStrategy;
    private final AlertPublisherPort alertPublisher;

    public EvaluateSensorTelemetryUseCase(
            WindowRegistryDomainService windowRegistry,
            AnomalyDetectionStrategy detectionStrategy,
            AlertPublisherPort alertPublisher
    ) {
        this.windowRegistry = Objects.requireNonNull(windowRegistry, "windowRegistry no puede ser nulo");
        this.detectionStrategy = Objects.requireNonNull(detectionStrategy, "detectionStrategy no puede ser nulo");
        this.alertPublisher = Objects.requireNonNull(alertPublisher, "alertPublisher no puede ser nulo");
    }

    public Optional<IndustrialAlert> execute(VibrationReadingRecord reading) {
        Objects.requireNonNull(reading, "reading no puede ser nula");
        SensorVibrationWindow window = windowRegistry.getOrCreateWindow(reading.sensorId());

        Optional<IndustrialAlert> alertOpt = window.procesarTelemetria(reading, detectionStrategy);
        alertOpt.ifPresent(alert -> {
            log.warn("Alerta detectada [{}] en sensor {}: {}", alert.severity(), alert.sensorId(), alert.description());
            alertPublisher.publishAlert(alert);
        });

        return alertOpt;
    }
}
