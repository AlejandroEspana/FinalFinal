package com.empresa.mantenimiento.presentation;

import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.service.WindowRegistryDomainService;
import com.empresa.mantenimiento.presentation.controller.DetectorStatusController;
import com.empresa.mantenimiento.presentation.mapper.WindowPresentationMapper;
import com.empresa.mantenimiento.presentation.request.ResetWindowRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas Unitarias Aisladas: DetectorStatusController")
class DetectorStatusControllerTest {

    @Test
    @DisplayName("Permite consultar y reiniciar una ventana móvil de un sensor")
    void shouldListAndResetSensorWindow() {
        WindowRegistryDomainService registry = new WindowRegistryDomainService();
        registry.getOrCreateWindow(SensorId.of("sensor_A"));

        DetectorStatusController controller = new DetectorStatusController(registry, new WindowPresentationMapper());
        var listResp = controller.getAllWindows();
        assertThat(listResp.getBody()).isNotNull();
        assertThat(listResp.getBody().data()).hasSize(1);

        var resetResp = controller.resetWindow(new ResetWindowRequest("sensor_A"));
        assertThat(resetResp.getBody().success()).isTrue();
    }
}
