package com.empresa.mantenimiento.application.usecase;

import com.empresa.mantenimiento.application.dto.AuditRecordDto;
import com.empresa.mantenimiento.domain.repository.DecisionAuditRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class GetDecisionAuditHistoryUseCase {

    private final DecisionAuditRepositoryPort auditRepositoryPort;

    public GetDecisionAuditHistoryUseCase(DecisionAuditRepositoryPort auditRepositoryPort) {
        this.auditRepositoryPort = Objects.requireNonNull(auditRepositoryPort, "auditRepositoryPort no puede ser nulo");
    }

    public List<AuditRecordDto> getAllOrdered() {
        return auditRepositoryPort.findAllOrdered();
    }

    public List<AuditRecordDto> getBySensorId(String sensorId) {
        return auditRepositoryPort.findBySensorId(sensorId);
    }
}
