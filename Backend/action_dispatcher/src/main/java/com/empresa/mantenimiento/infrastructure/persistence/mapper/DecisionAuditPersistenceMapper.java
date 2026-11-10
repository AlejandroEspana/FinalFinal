package com.empresa.mantenimiento.infrastructure.persistence.mapper;

import com.empresa.mantenimiento.application.dto.AuditRecordDto;
import com.empresa.mantenimiento.domain.model.MaintenanceDecision;
import com.empresa.mantenimiento.infrastructure.persistence.entity.OperatorDecisionAuditEntity;
import org.springframework.stereotype.Component;

@Component
public class DecisionAuditPersistenceMapper {

    public OperatorDecisionAuditEntity toEntity(MaintenanceDecision decision) {
        return new OperatorDecisionAuditEntity(
                decision.getDecisionRecord().alertId().value(),
                decision.getDecisionRecord().sensorId().value(),
                decision.getDecisionRecord().chosenAction(),
                decision.getDecisionRecord().operatorId(),
                decision.getStatus(),
                decision.getRoutingTarget(),
                decision.getDelayMillis(),
                decision.getDecisionRecord().notes(),
                decision.getProcessedAt()
        );
    }

    public AuditRecordDto toDto(OperatorDecisionAuditEntity entity) {
        return new AuditRecordDto(
                entity.getId(),
                entity.getAlertId(),
                entity.getSensorId(),
                entity.getChosenAction(),
                entity.getOperatorId(),
                entity.getStatus(),
                entity.getRoutingTarget(),
                entity.getDelayMillis(),
                entity.getNotes(),
                entity.getRecordedAt()
        );
    }
}
