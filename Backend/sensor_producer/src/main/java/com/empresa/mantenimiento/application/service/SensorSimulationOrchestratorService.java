package com.empresa.mantenimiento.application.service;

import com.empresa.mantenimiento.application.usecase.GenerateTelemetryBatchUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Servicio de Aplicación que orquesta el ciclo periódico de emisión de telemetría hacia Kafka.
 */
@Service
public class SensorSimulationOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(SensorSimulationOrchestratorService.class);

    private final GenerateTelemetryBatchUseCase generateBatchUseCase;

    public SensorSimulationOrchestratorService(GenerateTelemetryBatchUseCase generateBatchUseCase) {
        this.generateBatchUseCase = Objects.requireNonNull(generateBatchUseCase, "generateBatchUseCase no puede ser nulo");
    }

    @Scheduled(fixedRateString = "${app.simulation.rate-ms:2000}")
    public void runScheduledCycle() {
        var emitted = generateBatchUseCase.executeBatch();
        log.debug("Ciclo de simulación completado: {} lecturas enviadas a Kafka", emitted.size());
    }
}
