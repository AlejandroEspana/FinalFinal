package com.empresa.mantenimiento.application.usecase;

import com.empresa.mantenimiento.domain.service.PendingAlertsRegistryDomainService;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class ResolveHumanAlertUseCase {

    private final PendingAlertsRegistryDomainService registryService;

    public ResolveHumanAlertUseCase(PendingAlertsRegistryDomainService registryService) {
        this.registryService = Objects.requireNonNull(registryService, "registryService no puede ser nulo");
    }

    public void execute(String alertId) {
        registryService.resolve(alertId);
    }
}
