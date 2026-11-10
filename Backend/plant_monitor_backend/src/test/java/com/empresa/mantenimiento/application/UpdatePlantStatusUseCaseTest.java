package com.empresa.mantenimiento.application;

import com.empresa.mantenimiento.application.dto.SensorPlantStatusDto;
import com.empresa.mantenimiento.application.usecase.UpdatePlantStatusUseCase;
import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.domain.repository.PlantStatusBroadcasterPort;
import com.empresa.mantenimiento.domain.service.PlantMaterializedDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: UpdatePlantStatusUseCase")
class UpdatePlantStatusUseCaseTest {

    @Mock
    private PlantStatusBroadcasterPort broadcasterPort;

    private UpdatePlantStatusUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdatePlantStatusUseCase(new PlantMaterializedDomainService(), broadcasterPort);
    }

    @Test
    @DisplayName("Actualiza vista materializada y difunde por WebSocket ante alerta CRITICAL")
    void shouldUpdateAndBroadcastOnAlert() {
        IndustrialAlert alert = new IndustrialAlert(
                AlertId.of("ALT-1"), SensorId.of("sensor_A"), AlertSeverity.CRITICAL, 98.0, "Falla", Instant.now()
        );
        ArgumentCaptor<SensorPlantStatusDto> captor = ArgumentCaptor.forClass(SensorPlantStatusDto.class);

        SensorPlantStatusDto result = useCase.applyAlert(alert);

        verify(broadcasterPort).broadcastStatus(captor.capture());
        assertThat(result.severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(captor.getValue().sensorId()).isEqualTo("sensor_A");
    }
}
