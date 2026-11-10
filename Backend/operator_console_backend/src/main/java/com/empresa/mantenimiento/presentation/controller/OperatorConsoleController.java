package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.application.service.OperatorConsoleApplicationService;
import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/alerts")
@CrossOrigin(origins = "*")
public class OperatorConsoleController {

    private final OperatorConsoleApplicationService consoleApplicationService;

    public OperatorConsoleController(OperatorConsoleApplicationService consoleApplicationService) {
        this.consoleApplicationService = Objects.requireNonNull(consoleApplicationService, "consoleApplicationService no puede ser nulo");
    }

    @GetMapping("/pending")
    public ResponseEntity<List<HumanActionRequest>> getPendingAlerts() {
        return ResponseEntity.ok(consoleApplicationService.getPendingAlerts());
    }

    @DeleteMapping("/{alertId}")
    public ResponseEntity<Void> dismissAlert(@PathVariable String alertId) {
        consoleApplicationService.resolveAlert(alertId);
        return ResponseEntity.noContent().build();
    }
}
