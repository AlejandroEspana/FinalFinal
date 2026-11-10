package com.empresa.mantenimiento.presentation.mapper;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.presentation.response.PendingAlertResponse;
import org.springframework.stereotype.Component;

@Component
public class OperatorAlertPresentationMapper {

    public PendingAlertResponse toResponse(HumanActionRequest req) {
        return new PendingAlertResponse(
                req.alertId().value(),
                req.sensorId().value(),
                req.severity().name(),
                req.triggerValue(),
                req.description(),
                req.availableOptions(),
                req.timestamp()
        );
    }
}
