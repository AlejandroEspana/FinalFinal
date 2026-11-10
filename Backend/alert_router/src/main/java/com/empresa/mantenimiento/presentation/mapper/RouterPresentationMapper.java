package com.empresa.mantenimiento.presentation.mapper;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.presentation.response.RoutedAlertResponse;
import org.springframework.stereotype.Component;

@Component
public class RouterPresentationMapper {

    public RoutedAlertResponse toResponse(HumanActionRequest req) {
        return new RoutedAlertResponse(
                req.alertId().value(),
                req.sensorId().value(),
                req.severity().name(),
                req.availableOptions(),
                req.timestamp()
        );
    }
}
