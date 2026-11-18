package com.empresa.mantenimiento.presentation;

import com.empresa.mantenimiento.application.dto.TelemetryEventDto;
import com.empresa.mantenimiento.application.usecase.GenerateTelemetryBatchUseCase;
import com.empresa.mantenimiento.application.usecase.InjectCustomReadingUseCase;
import com.empresa.mantenimiento.config.BeanConfig;
import com.empresa.mantenimiento.presentation.controller.SimulationController;
import com.empresa.mantenimiento.presentation.mapper.SimulationPresentationMapper;
import com.empresa.mantenimiento.presentation.request.SimulateReadingRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito y Contrato JSON: SimulationController")
class SimulationControllerTest {

    @Mock
    private GenerateTelemetryBatchUseCase generateBatchUseCase;

    @Mock
    private InjectCustomReadingUseCase injectCustomReadingUseCase;

    @Test
    @DisplayName("Debe inyectar lectura manual y retornar ApiResponse exitoso")
    void shouldInjectReadingSuccessfully() {
        SimulationController controller = new SimulationController(
                generateBatchUseCase, injectCustomReadingUseCase, new SimulationPresentationMapper()
        );
        SimulateReadingRequest req = new SimulateReadingRequest("sensor_A", 96.0);
        TelemetryEventDto dto = new TelemetryEventDto("sensor_A", 96.0, "Sector #1", 5L, Instant.now());
        when(injectCustomReadingUseCase.execute("sensor_A", 96.0)).thenReturn(dto);

        var response = controller.injectReading(req);

        verify(injectCustomReadingUseCase).execute("sensor_A", 96.0);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().vibrationValue()).isEqualTo(96.0);
    }

    @Test
    @DisplayName("Contrato JSON: Deserializa tanto vibrationValue como alias vibration, burstCount y campos extra sin error 400")
    void shouldDeserializeFlexibleJsonPayloadAndExecuteBurst() throws Exception {
        ObjectMapper mapper = new BeanConfig().objectMapper();
        String frontendJson = """
                {
                  "sensorId": "sensor_B",
                  "vibration": 82.5,
                  "burstCount": 3,
                  "temperature": 45.0
                }
                """;
        SimulateReadingRequest parsed = mapper.readValue(frontendJson, SimulateReadingRequest.class);
        assertThat(parsed.sensorId()).isEqualTo("sensor_B");
        assertThat(parsed.vibrationValue()).isEqualTo(82.5);
        assertThat(parsed.resolvedBurstCount()).isEqualTo(3);

        SimulationController controller = new SimulationController(
                generateBatchUseCase, injectCustomReadingUseCase, new SimulationPresentationMapper()
        );
        TelemetryEventDto dto = new TelemetryEventDto("sensor_B", 82.5, "Sector #2", 3L, Instant.now());
        when(injectCustomReadingUseCase.execute("sensor_B", 82.5)).thenReturn(dto);

        controller.injectReading(parsed);

        verify(injectCustomReadingUseCase, times(3)).execute("sensor_B", 82.5);
    }
}
