package com.empresa.mantenimiento.application;

import com.empresa.mantenimiento.application.usecase.RegisterHumanAlertUseCase;
import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.domain.repository.OperatorAlertBroadcasterPort;
import com.empresa.mantenimiento.domain.service.PendingAlertsRegistryDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: RegisterHumanAlertUseCase")
class RegisterHumanAlertUseCaseTest {

    @Mock
    private OperatorAlertBroadcasterPort broadcasterPort;

    @Test
    @DisplayName("Registra la alerta en memoria y la difunde por WebSocket")
    void shouldRegisterAndBroadcastAlert() {
        PendingAlertsRegistryDomainService registry = new PendingAlertsRegistryDomainService();
        RegisterHumanAlertUseCase useCase = new RegisterHumanAlertUseCase(registry, broadcasterPort);

        HumanActionRequest req = new HumanActionRequest(
                AlertId.of("ALT-2"), SensorId.of("sensor_B"), AlertSeverity.WARNING,
                78.5, "Advertencia", List.of("PROGRAMAR_MANTENIMIENTO_AHORA"), Instant.now()
        );

        useCase.execute(req);

        assertThat(registry.getPendingRequests()).hasSize(1);
        verify(broadcasterPort).broadcastAlert(req);
    }
}
