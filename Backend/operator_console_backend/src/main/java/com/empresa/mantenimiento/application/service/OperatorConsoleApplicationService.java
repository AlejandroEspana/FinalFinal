package com.empresa.mantenimiento.application.service;

import com.empresa.mantenimiento.application.usecase.RegisterHumanAlertUseCase;
import com.empresa.mantenimiento.application.usecase.ResolveHumanAlertUseCase;
import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.domain.service.PendingAlertsRegistryDomainService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class OperatorConsoleApplicationService {

    private final RegisterHumanAlertUseCase registerUseCase;
    private final ResolveHumanAlertUseCase resolveUseCase;
    private final PendingAlertsRegistryDomainService registryService;

    public OperatorConsoleApplicationService(
            RegisterHumanAlertUseCase registerUseCase,
            ResolveHumanAlertUseCase resolveUseCase,
            PendingAlertsRegistryDomainService registryService
    ) {
        this.registerUseCase = Objects.requireNonNull(registerUseCase, "registerUseCase no puede ser nulo");
        this.resolveUseCase = Objects.requireNonNull(resolveUseCase, "resolveUseCase no puede ser nulo");
        this.registryService = Objects.requireNonNull(registryService, "registryService no puede ser nulo");
    }

    public void registerAndBroadcast(HumanActionRequest request) {
        registerUseCase.execute(request);
    }

    public void resolveAlert(String alertId) {
        resolveUseCase.execute(alertId);
    }

    public List<HumanActionRequest> getPendingAlerts() {
        return registryService.getPendingRequests();
    }
}
