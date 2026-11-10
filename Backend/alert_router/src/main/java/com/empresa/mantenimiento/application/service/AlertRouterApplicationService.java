package com.empresa.mantenimiento.application.service;

import com.empresa.mantenimiento.application.usecase.RouteAlertToHumanUseCase;
import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.domain.model.IndustrialAlert;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class AlertRouterApplicationService {

    private final RouteAlertToHumanUseCase routeUseCase;

    public AlertRouterApplicationService(RouteAlertToHumanUseCase routeUseCase) {
        this.routeUseCase = Objects.requireNonNull(routeUseCase, "routeUseCase no puede ser nulo");
    }

    public HumanActionRequest route(IndustrialAlert alert) {
        return routeUseCase.execute(alert);
    }
}
