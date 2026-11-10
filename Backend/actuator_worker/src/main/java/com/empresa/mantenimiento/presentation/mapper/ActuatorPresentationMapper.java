package com.empresa.mantenimiento.presentation.mapper;

import com.empresa.mantenimiento.application.dto.ActuatorExecutionDto;
import com.empresa.mantenimiento.presentation.response.ActuatorStatusResponse;
import org.springframework.stereotype.Component;

@Component
public class ActuatorPresentationMapper {

    public ActuatorStatusResponse toResponse(ActuatorExecutionDto dto) {
        return new ActuatorStatusResponse(
                dto.commandId(),
                dto.sensorId(),
                dto.previousState(),
                dto.currentState(),
                dto.operatorId(),
                dto.reason(),
                dto.executedAt()
        );
    }
}
