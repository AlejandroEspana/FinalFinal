package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.application.service.ActuatorOrchestrationService;
import com.empresa.mantenimiento.presentation.mapper.ActuatorPresentationMapper;
import com.empresa.mantenimiento.presentation.response.ActuatorStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/actuators")
public class ActuatorStatusController {

    private final ActuatorOrchestrationService orchestrationService;
    private final ActuatorPresentationMapper mapper;

    public ActuatorStatusController(
            ActuatorOrchestrationService orchestrationService,
            ActuatorPresentationMapper mapper
    ) {
        this.orchestrationService = Objects.requireNonNull(orchestrationService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @GetMapping("/history")
    public ResponseEntity<List<ActuatorStatusResponse>> getExecutionHistory() {
        List<ActuatorStatusResponse> list = orchestrationService.getExecutionHistory()
                .stream()
                .map(mapper::toResponse)
                .toList();
        return ResponseEntity.ok(list);
    }
}
