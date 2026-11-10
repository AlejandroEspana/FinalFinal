package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.application.dto.SensorPlantStatusDto;
import com.empresa.mantenimiento.application.service.PlantMonitorApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/sensors")
@CrossOrigin(origins = "*")
public class PlantStatusController {

    private final PlantMonitorApplicationService monitorApplicationService;

    public PlantStatusController(PlantMonitorApplicationService monitorApplicationService) {
        this.monitorApplicationService = Objects.requireNonNull(monitorApplicationService, "monitorApplicationService no puede ser nulo");
    }

    @GetMapping("/status")
    public ResponseEntity<List<SensorPlantStatusDto>> getAllStatuses() {
        return ResponseEntity.ok(monitorApplicationService.getAllStatuses());
    }

    @PostMapping("/{sensorId}/reset")
    public ResponseEntity<SensorPlantStatusDto> resetSensor(@PathVariable String sensorId) {
        return ResponseEntity.ok(monitorApplicationService.resetSensor(sensorId));
    }
}
