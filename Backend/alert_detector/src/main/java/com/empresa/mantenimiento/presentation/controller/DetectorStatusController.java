package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.service.WindowRegistryDomainService;
import com.empresa.mantenimiento.presentation.mapper.WindowPresentationMapper;
import com.empresa.mantenimiento.presentation.request.ResetWindowRequest;
import com.empresa.mantenimiento.presentation.response.WindowStatusResponse;
import com.empresa.mantenimiento.shared.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/detector")
@CrossOrigin(origins = "*")
public class DetectorStatusController {

    private final WindowRegistryDomainService windowRegistry;
    private final WindowPresentationMapper mapper;

    public DetectorStatusController(
            WindowRegistryDomainService windowRegistry,
            WindowPresentationMapper mapper
    ) {
        this.windowRegistry = Objects.requireNonNull(windowRegistry, "windowRegistry no puede ser nulo");
        this.mapper = Objects.requireNonNull(mapper, "mapper no puede ser nulo");
    }

    @GetMapping("/windows")
    public ResponseEntity<ApiResponse<List<WindowStatusResponse>>> getAllWindows() {
        List<WindowStatusResponse> list = windowRegistry.getAllWindows().stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Estado actual de las ventanas móviles stateful", list));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<String>> resetWindow(@Valid @RequestBody ResetWindowRequest request) {
        windowRegistry.resetWindow(SensorId.of(request.sensorId()));
        return ResponseEntity.ok(ApiResponse.success("Ventana móvil reiniciada para " + request.sensorId(), request.sensorId()));
    }
}
