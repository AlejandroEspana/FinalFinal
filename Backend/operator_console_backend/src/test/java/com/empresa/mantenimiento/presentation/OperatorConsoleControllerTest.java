package com.empresa.mantenimiento.presentation;

import com.empresa.mantenimiento.application.service.OperatorConsoleApplicationService;
import com.empresa.mantenimiento.application.usecase.RegisterHumanAlertUseCase;
import com.empresa.mantenimiento.application.usecase.ResolveHumanAlertUseCase;
import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.domain.repository.OperatorAlertBroadcasterPort;
import com.empresa.mantenimiento.domain.service.PendingAlertsRegistryDomainService;
import com.empresa.mantenimiento.presentation.controller.OperatorConsoleController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: OperatorConsoleController")
class OperatorConsoleControllerTest {

    @Mock
    private OperatorAlertBroadcasterPort broadcasterPort;

    @Test
    @DisplayName("Lista alertas pendientes y permite descartar una alerta resuelta")
    void shouldListAndDismissAlert() {
        PendingAlertsRegistryDomainService registry = new PendingAlertsRegistryDomainService();
        OperatorConsoleApplicationService service = new OperatorConsoleApplicationService(
                new RegisterHumanAlertUseCase(registry, broadcasterPort),
                new ResolveHumanAlertUseCase(registry),
                registry
        );
        OperatorConsoleController controller = new OperatorConsoleController(service);

        HumanActionRequest req = new HumanActionRequest(
                AlertId.of("ALT-5"), SensorId.of("sensor_A"), AlertSeverity.CRITICAL,
                95.0, "Pico", List.of("APAGADO_INMEDIATO"), Instant.now()
        );
        service.registerAndBroadcast(req);

        assertThat(controller.getPendingAlerts().getBody()).hasSize(1);
        controller.dismissAlert("ALT-5");
        assertThat(controller.getPendingAlerts().getBody()).isEmpty();
    }
}
