package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.application.dto.TelemetryEventDto;
import com.empresa.mantenimiento.application.service.SensorSimulationOrchestratorService;
import com.empresa.mantenimiento.application.usecase.GenerateTelemetryBatchUseCase;
import com.empresa.mantenimiento.application.usecase.InjectCustomReadingUseCase;
import com.empresa.mantenimiento.presentation.mapper.SimulationPresentationMapper;
import com.empresa.mantenimiento.presentation.request.SimulateReadingRequest;
import com.empresa.mantenimiento.presentation.response.SimulationStatusResponse;
import com.empresa.mantenimiento.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/simulation")
public class SimulationController {

    private final GenerateTelemetryBatchUseCase generateBatchUseCase;
    private final InjectCustomReadingUseCase injectCustomReadingUseCase;
    private final SimulationPresentationMapper mapper;
    private final SensorSimulationOrchestratorService orchestratorService;

    @Autowired
    public SimulationController(
            GenerateTelemetryBatchUseCase generateBatchUseCase,
            InjectCustomReadingUseCase injectCustomReadingUseCase,
            SimulationPresentationMapper mapper,
            SensorSimulationOrchestratorService orchestratorService
    ) {
        this.generateBatchUseCase = Objects.requireNonNull(generateBatchUseCase, "generateBatchUseCase no puede ser nulo");
        this.injectCustomReadingUseCase = Objects.requireNonNull(injectCustomReadingUseCase, "injectCustomReadingUseCase no puede ser nulo");
        this.mapper = Objects.requireNonNull(mapper, "mapper no puede ser nulo");
        this.orchestratorService = orchestratorService;
    }

    public SimulationController(
            GenerateTelemetryBatchUseCase generateBatchUseCase,
            InjectCustomReadingUseCase injectCustomReadingUseCase,
            SimulationPresentationMapper mapper
    ) {
        this(generateBatchUseCase, injectCustomReadingUseCase, mapper, null);
    }

    @PostMapping("/inject")
    public ResponseEntity<ApiResponse<SimulationStatusResponse>> injectReading(
            @Valid @RequestBody SimulateReadingRequest request
    ) {
        int count = request.resolvedBurstCount();
        TelemetryEventDto lastDto = null;
        for (int i = 0; i < count; i++) {
            lastDto = injectCustomReadingUseCase.execute(request.sensorId(), request.vibrationValue());
        }
        return ResponseEntity.ok(ApiResponse.success(
                String.format("Lectura(s) inyectada(s) hacia Kafka exitosamente (ráfaga=%d).", count),
                mapper.toResponse(lastDto)
        ));
    }

    @GetMapping("/sensors")
    public ResponseEntity<ApiResponse<List<SimulationStatusResponse>>> getSensors() {
        List<SimulationStatusResponse> list = generateBatchUseCase.getAllProfiles().stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Lista de sensores simulados.", list));
    }

    @GetMapping("/state")
    public ResponseEntity<Map<String, Object>> getSimulationState() {
        boolean enabled = orchestratorService == null || orchestratorService.isSimulationEnabled();
        long rateMs = orchestratorService != null ? orchestratorService.getRateMs() : 12000L;
        return ResponseEntity.ok(Map.of(
                "enabled", enabled,
                "rateMs", rateMs
        ));
    }

    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleSimulation(
            @RequestBody(required = false) Map<String, Boolean> body
    ) {
        Boolean desired = (body != null) ? body.get("enabled") : null;
        boolean enabled = orchestratorService == null || orchestratorService.toggleSimulation(desired);
        long rateMs = orchestratorService != null ? orchestratorService.getRateMs() : 12000L;
        return ResponseEntity.ok(Map.of(
                "enabled", enabled,
                "rateMs", rateMs
        ));
    }
}
