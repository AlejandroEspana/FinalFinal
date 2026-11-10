package com.empresa.mantenimiento.application.usecase;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.domain.repository.OperatorAlertBroadcasterPort;
import com.empresa.mantenimiento.domain.service.PendingAlertsRegistryDomainService;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class RegisterHumanAlertUseCase {

    private final PendingAlertsRegistryDomainService registryService;
    private final OperatorAlertBroadcasterPort broadcasterPort;

    public RegisterHumanAlertUseCase(
            PendingAlertsRegistryDomainService registryService,
            OperatorAlertBroadcasterPort broadcasterPort
    ) {
        this.registryService = Objects.requireNonNull(registryService, "registryService no puede ser nulo");
        this.broadcasterPort = Objects.requireNonNull(broadcasterPort, "broadcasterPort no puede ser nulo");
    }

    public void execute(HumanActionRequest request) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        registryService.register(request);
        broadcasterPort.broadcastAlert(request);
    }
}
