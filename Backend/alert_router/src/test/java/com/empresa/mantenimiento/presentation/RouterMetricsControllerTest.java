package com.empresa.mantenimiento.presentation;

import com.empresa.mantenimiento.application.service.AlertRouterApplicationService;
import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.presentation.controller.RouterMetricsController;
import com.empresa.mantenimiento.presentation.mapper.RouterPresentationMapper;
import com.empresa.mantenimiento.presentation.request.ManualRouteRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: RouterMetricsController")
class RouterMetricsControllerTest {

    @Mock
    private AlertRouterApplicationService routerService;

    @Test
    @DisplayName("Enruta manualmente una alerta y retorna ApiResponse exitoso")
    void shouldRouteAlertViaController() {
        RouterMetricsController controller = new RouterMetricsController(routerService, new RouterPresentationMapper());
        HumanActionRequest routed = new HumanActionRequest(
                AlertId.of("ALT-1"), SensorId.of("sensor_A"), AlertSeverity.CRITICAL,
                95.0, "Pico", List.of("APAGADO_INMEDIATO"), Instant.now()
        );
        when(routerService.route(any())).thenReturn(routed);

        var resp = controller.manualRoute(new ManualRouteRequest("ALT-1", "sensor_A", "CRITICAL", 95.0, "Pico"));
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().success()).isTrue();
        assertThat(resp.getBody().data().availableOptions()).containsExactly("APAGADO_INMEDIATO");
    }
}
