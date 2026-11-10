package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.application.dto.AuditRecordDto;
import com.empresa.mantenimiento.application.dto.DecisionResultDto;
import com.empresa.mantenimiento.application.service.DecisionOrchestrationService;
import com.empresa.mantenimiento.domain.model.OperatorDecisionRecord;
import com.empresa.mantenimiento.presentation.mapper.DecisionPresentationMapper;
import com.empresa.mantenimiento.presentation.request.OperatorDecisionRequest;
import com.empresa.mantenimiento.presentation.response.DecisionDispatchedResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * Controlador REST protegido por Spring Security (ROLE_OPERATOR) para procesar
 * decisiones humanas y consultar la auditoría en PostgreSQL.
 */
@RestController
public class ActionDecisionController {

    private final DecisionOrchestrationService orchestrationService;
    private final DecisionPresentationMapper mapper;

    public ActionDecisionController(
            DecisionOrchestrationService orchestrationService,
            DecisionPresentationMapper mapper
    ) {
        this.orchestrationService = Objects.requireNonNull(orchestrationService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @PostMapping({"/decide", "/api/v1/decide"})
    public ResponseEntity<DecisionDispatchedResponse> submitDecision(
            @Valid @RequestBody OperatorDecisionRequest request
    ) {
        OperatorDecisionRecord record = mapper.toDomainRecord(request);
        DecisionResultDto resultDto = orchestrationService.processDecision(record);
        return ResponseEntity.ok(mapper.toResponse(resultDto));
    }

    @GetMapping("/api/v1/audit")
    public ResponseEntity<List<AuditRecordDto>> getAllAudits() {
        return ResponseEntity.ok(orchestrationService.getAllAuditHistory());
    }

    @GetMapping("/api/v1/audit/sensor/{sensorId}")
    public ResponseEntity<List<AuditRecordDto>> getAuditsBySensor(@PathVariable String sensorId) {
        return ResponseEntity.ok(orchestrationService.getAuditHistoryBySensor(sensorId));
    }
}
