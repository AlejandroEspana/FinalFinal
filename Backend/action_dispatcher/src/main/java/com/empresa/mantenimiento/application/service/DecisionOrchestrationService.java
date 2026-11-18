package com.empresa.mantenimiento.application.service;

import com.empresa.mantenimiento.application.dto.AuditRecordDto;
import com.empresa.mantenimiento.application.dto.DecisionResultDto;
import com.empresa.mantenimiento.application.usecase.GetDecisionAuditHistoryUseCase;
import com.empresa.mantenimiento.application.usecase.ProcessOperatorDecisionUseCase;
import com.empresa.mantenimiento.domain.model.OperatorDecisionRecord;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class DecisionOrchestrationService {

    private final ProcessOperatorDecisionUseCase processOperatorDecisionUseCase;
    private final GetDecisionAuditHistoryUseCase getDecisionAuditHistoryUseCase;

    public DecisionOrchestrationService(
            ProcessOperatorDecisionUseCase processOperatorDecisionUseCase,
            GetDecisionAuditHistoryUseCase getDecisionAuditHistoryUseCase
    ) {
        this.processOperatorDecisionUseCase = Objects.requireNonNull(processOperatorDecisionUseCase);
        this.getDecisionAuditHistoryUseCase = Objects.requireNonNull(getDecisionAuditHistoryUseCase);
    }

    public DecisionResultDto processDecision(OperatorDecisionRecord record) {
        return processOperatorDecisionUseCase.execute(record);
    }

    public List<AuditRecordDto> getAllAuditHistory() {
        return getDecisionAuditHistoryUseCase.getAllOrdered();
    }

    public List<AuditRecordDto> getAuditHistoryBySensor(String sensorId) {
        return getDecisionAuditHistoryUseCase.getBySensorId(sensorId);
    }
}
