package com.empresa.mantenimiento.infrastructure.persistence.mapper;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.infrastructure.persistence.entity.RoutedAlertHistoryEntity;
import org.springframework.stereotype.Component;

@Component
public class RoutedAlertPersistenceMapper {

    public RoutedAlertHistoryEntity toEntity(HumanActionRequest req) {
        return new RoutedAlertHistoryEntity(
                req.alertId().value(),
                req.sensorId().value(),
                req.severity().name(),
                req.availableOptions(),
                req.timestamp()
        );
    }
}
