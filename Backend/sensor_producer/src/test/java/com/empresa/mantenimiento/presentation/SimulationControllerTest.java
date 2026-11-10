package com.empresa.mantenimiento.presentation;

import com.empresa.mantenimiento.application.dto.TelemetryEventDto;
import com.empresa.mantenimiento.application.usecase.GenerateTelemetryBatchUseCase;
import com.empresa.mantenimiento.application.usecase.InjectCustomReadingUseCase;
import com.empresa.mantenimiento.presentation.controller.SimulationController;
import com.empresa.mantenimiento.presentation.mapper.SimulationPresentationMapper;
import com.empresa.mantenimiento.presentation.request.SimulateReadingRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: SimulationController")
class SimulationControllerTest {

    @Mock
    private GenerateTelemetryBatchUseCase generateBatchUseCase;

    @Mock
    private InjectCustomReadingUseCase injectCustomReadingUseCase;

    @Test
    @DisplayName("Debe inyectar lectura manual y retornar ApiResponse exitoso")
    void shouldInjectReadingSuccessfully() {
        // Arrange
        SimulationController controller = new SimulationController(
                generateBatchUseCase, injectCustomReadingUseCase, new SimulationPresentationMapper()
        );
        SimulateReadingRequest req = new SimulateReadingRequest("sensor_A", 96.0);
        TelemetryEventDto dto = new TelemetryEventDto("sensor_A", 96.0, "Sector #1", 5L, Instant.now());
        when(injectCustomReadingUseCase.execute("sensor_A", 96.0)).thenReturn(dto);

        // Act
        var response = controller.injectReading(req);

        // Assert
        verify(injectCustomReadingUseCase).execute("sensor_A", 96.0);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isTrue();
        assertThat(response.getBody().data().vibrationValue()).isEqualTo(96.0);
    }
}
