package com.empresa.mantenimiento.infrastructure.persistence.mapper;

import com.empresa.mantenimiento.domain.model.ActuatorExecutionRecord;
import com.empresa.mantenimiento.infrastructure.persistence.entity.ActuatorExecutionLogEntity;
import org.springframework.stereotype.Component;

@Component
public class ActuatorExecutionPersistenceMapper {

    public ActuatorExecutionLogEntity toEntity(ActuatorExecutionRecord record) {
        return new ActuatorExecutionLogEntity(
                record.commandId(),
                record.sensorId(),
                record.previousState(),
                record.currentState(),
                record.operatorId(),
                record.reason(),
                record.executedAt()
        );
    }
}
