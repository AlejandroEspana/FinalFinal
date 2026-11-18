package com.empresa.mantenimiento.presentation.controller;

import com.empresa.mantenimiento.application.dto.AuditRecordDto;
import com.empresa.mantenimiento.application.dto.DecisionResultDto;
import com.empresa.mantenimiento.application.service.DecisionOrchestrationService;
import com.empresa.mantenimiento.domain.model.OperatorDecisionRecord;
import com.empresa.mantenimiento.infrastructure.adapter.MultichannelNotificationService;
import com.empresa.mantenimiento.presentation.mapper.DecisionPresentationMapper;
import com.empresa.mantenimiento.presentation.request.OperatorDecisionRequest;
import com.empresa.mantenimiento.presentation.response.DecisionDispatchedResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@RestController
public class ActionDecisionController {

    private final DecisionOrchestrationService orchestrationService;
    private final DecisionPresentationMapper mapper;
    private final MultichannelNotificationService notificationService;

    @Autowired
    public ActionDecisionController(
            DecisionOrchestrationService orchestrationService,
            DecisionPresentationMapper mapper,
            MultichannelNotificationService notificationService
    ) {
        this.orchestrationService = Objects.requireNonNull(orchestrationService);
        this.mapper = Objects.requireNonNull(mapper);
        this.notificationService = notificationService;
    }

    public ActionDecisionController(
            DecisionOrchestrationService orchestrationService,
            DecisionPresentationMapper mapper
    ) {
        this(orchestrationService, mapper, null);
    }

    public ResponseEntity<DecisionDispatchedResponse> submitDecision(OperatorDecisionRequest request) {
        return submitDecision(request, null);
    }

    @PostMapping({"/decide", "/api/v1/decide"})
    public ResponseEntity<DecisionDispatchedResponse> submitDecision(
            @Valid @RequestBody OperatorDecisionRequest request,
            Authentication authentication
    ) {
        String authenticatedUser = (authentication != null) ? authentication.getName() : null;
        OperatorDecisionRecord record = mapper.toDomainRecord(request, authenticatedUser);
        DecisionResultDto resultDto = orchestrationService.processDecision(record);

        if (notificationService != null) {
            CompletableFuture.runAsync(() ->
                    notificationService.notifyOperatorDecision(resultDto, record.operatorId(), record.notes())
            );
        }

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
