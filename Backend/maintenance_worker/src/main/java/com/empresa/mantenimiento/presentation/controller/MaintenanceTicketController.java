package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.application.service.MaintenanceOrchestrationService;
import com.empresa.mantenimiento.presentation.mapper.MaintenancePresentationMapper;
import com.empresa.mantenimiento.presentation.response.MaintenanceTicketResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/maintenance")
public class MaintenanceTicketController {

    private final MaintenanceOrchestrationService orchestrationService;
    private final MaintenancePresentationMapper mapper;

    public MaintenanceTicketController(
            MaintenanceOrchestrationService orchestrationService,
            MaintenancePresentationMapper mapper
    ) {
        this.orchestrationService = Objects.requireNonNull(orchestrationService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @GetMapping("/tickets")
    public ResponseEntity<List<MaintenanceTicketResponse>> getAllTickets() {
        List<MaintenanceTicketResponse> list = orchestrationService.getAllTickets()
                .stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(list);
    }
}
