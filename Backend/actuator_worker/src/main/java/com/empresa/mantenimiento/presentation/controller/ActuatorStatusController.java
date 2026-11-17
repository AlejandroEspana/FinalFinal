package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.application.dto.ActuatorExecutionDto;
import com.empresa.mantenimiento.application.service.ActuatorOrchestrationService;
import com.empresa.mantenimiento.presentation.mapper.ActuatorPresentationMapper;
import com.empresa.mantenimiento.presentation.response.ActuatorStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
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

    @PostMapping("/{sensorId}/reset")
    public ResponseEntity<ActuatorStatusResponse> resetMachine(
            @PathVariable String sensorId,
            @RequestBody(required = false) Map<String, String> body
    ) {
        String operatorId = (body != null && body.get("operatorId") != null) ? body.get("operatorId") : "operator";
        String reason = (body != null && body.get("reason") != null) ? body.get("reason") : "Reactivación manual desde SCADA";
        ActuatorExecutionDto dto = orchestrationService.resetMachine(sensorId, operatorId, reason);
        return ResponseEntity.ok(mapper.toResponse(dto));
    }
}
