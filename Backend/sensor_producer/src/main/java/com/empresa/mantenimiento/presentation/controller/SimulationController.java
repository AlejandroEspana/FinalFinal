package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.application.dto.TelemetryEventDto;
import com.empresa.mantenimiento.application.usecase.GenerateTelemetryBatchUseCase;
import com.empresa.mantenimiento.application.usecase.InjectCustomReadingUseCase;
import com.empresa.mantenimiento.presentation.mapper.SimulationPresentationMapper;
import com.empresa.mantenimiento.presentation.request.SimulateReadingRequest;
import com.empresa.mantenimiento.presentation.response.SimulationStatusResponse;
import com.empresa.mantenimiento.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/simulation")
public class SimulationController {

    private final GenerateTelemetryBatchUseCase generateBatchUseCase;
    private final InjectCustomReadingUseCase injectCustomReadingUseCase;
    private final SimulationPresentationMapper mapper;

    public SimulationController(
            GenerateTelemetryBatchUseCase generateBatchUseCase,
            InjectCustomReadingUseCase injectCustomReadingUseCase,
            SimulationPresentationMapper mapper
    ) {
        this.generateBatchUseCase = Objects.requireNonNull(generateBatchUseCase, "generateBatchUseCase no puede ser nulo");
        this.injectCustomReadingUseCase = Objects.requireNonNull(injectCustomReadingUseCase, "injectCustomReadingUseCase no puede ser nulo");
        this.mapper = Objects.requireNonNull(mapper, "mapper no puede ser nulo");
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
}
