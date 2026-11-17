package com.empresa.mantenimiento.application.service;

import com.empresa.mantenimiento.application.usecase.GenerateTelemetryBatchUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servicio de Aplicación que orquesta el ciclo periódico de emisión de telemetría hacia Kafka.
 */
@Service
public class SensorSimulationOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(SensorSimulationOrchestratorService.class);

    private final GenerateTelemetryBatchUseCase generateBatchUseCase;
    private final AtomicBoolean simulationEnabled;
    private final long rateMs;

    @Autowired
    public SensorSimulationOrchestratorService(
            GenerateTelemetryBatchUseCase generateBatchUseCase,
            @Value("${app.simulation.enabled:true}") boolean initialEnabled,
            @Value("${app.simulation.rate-ms:12000}") long rateMs
    ) {
        this.generateBatchUseCase = Objects.requireNonNull(generateBatchUseCase, "generateBatchUseCase no puede ser nulo");
        this.simulationEnabled = new AtomicBoolean(initialEnabled);
        this.rateMs = rateMs;
    }

    public SensorSimulationOrchestratorService(GenerateTelemetryBatchUseCase generateBatchUseCase) {
        this(generateBatchUseCase, true, 12000L);
    }

    @Scheduled(fixedRateString = "${app.simulation.rate-ms:12000}")
    public void runScheduledCycle() {
        if (!simulationEnabled.get()) {
            return;
        }
        var emitted = generateBatchUseCase.executeBatch();
        log.debug("Ciclo de simulación completado: {} lecturas enviadas a Kafka", emitted.size());
    }

    public boolean isSimulationEnabled() {
        return simulationEnabled.get();
    }

    public boolean toggleSimulation(Boolean explicitState) {
        if (explicitState != null) {
            simulationEnabled.set(explicitState);
        } else {
            simulationEnabled.set(!simulationEnabled.get());
        }
        log.info("Simulación automática de telemetría cambiada a enabled={}", simulationEnabled.get());
        return simulationEnabled.get();
    }

    public long getRateMs() {
        return rateMs;
    }
}
