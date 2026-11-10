package com.empresa.mantenimiento.infrastructure.persistence.mapper;

import com.empresa.mantenimiento.domain.model.InteractiveAlertSession;
import com.empresa.mantenimiento.infrastructure.persistence.entity.OperatorSessionEntity;
import org.springframework.stereotype.Component;

@Component
public class OperatorSessionMapper {

    public OperatorSessionEntity toEntity(InteractiveAlertSession session) {
        return new OperatorSessionEntity(
                session.getRequest().alertId().value(),
                session.getRequest().sensorId().value(),
                session.isResolved(),
                session.getRequest().timestamp()
        );
    }
}
