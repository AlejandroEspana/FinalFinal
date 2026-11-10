package com.empresa.mantenimiento.presentation.mapper;

import com.empresa.mantenimiento.application.dto.DecisionResultDto;
import com.empresa.mantenimiento.domain.model.AlertId;
import com.empresa.mantenimiento.domain.model.OperatorDecisionRecord;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.presentation.request.OperatorDecisionRequest;
import com.empresa.mantenimiento.presentation.response.DecisionDispatchedResponse;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class DecisionPresentationMapper {

    public OperatorDecisionRecord toDomainRecord(OperatorDecisionRequest request) {
        return new OperatorDecisionRecord(
                new AlertId(request.alertId()),
                new SensorId(request.sensorId()),
                request.chosenAction(),
                request.operatorId(),
                Instant.now(),
                request.notes()
        );
    }

    public DecisionDispatchedResponse toResponse(DecisionResultDto dto) {
        return new DecisionDispatchedResponse(
                dto.status(),
                dto.message(),
                dto.alertId(),
                dto.sensorId(),
                dto.actionDispatched(),
                dto.auditId(),
                dto.routingTarget(),
                dto.processedAt()
        );
    }
}
