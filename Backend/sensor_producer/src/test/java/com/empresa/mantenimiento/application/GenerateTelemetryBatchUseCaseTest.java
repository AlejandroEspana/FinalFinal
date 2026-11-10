package com.empresa.mantenimiento.application;

import com.empresa.mantenimiento.application.dto.TelemetryEventDto;
import com.empresa.mantenimiento.application.usecase.GenerateTelemetryBatchUseCase;
import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import com.empresa.mantenimiento.domain.repository.TelemetryEventPublisherPort;
import com.empresa.mantenimiento.domain.service.TelemetryGeneratorDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: GenerateTelemetryBatchUseCase")
class GenerateTelemetryBatchUseCaseTest {

    @Mock
    private TelemetryEventPublisherPort publisherPort;

    private GenerateTelemetryBatchUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GenerateTelemetryBatchUseCase(publisherPort, new TelemetryGeneratorDomainService());
    }

    @Test
    @DisplayName("Debe generar y publicar lecturas para exactamente 10 sensores industriales sin abrir sockets")
    void shouldPublishTenReadingsInEachBatch() {
        // Arrange
        ArgumentCaptor<VibrationReadingRecord> captor = ArgumentCaptor.forClass(VibrationReadingRecord.class);

        // Act
        List<TelemetryEventDto> batch = useCase.executeBatch();

        // Assert
        assertThat(batch).hasSize(10);
        verify(publisherPort, times(10)).publish(captor.capture());

        List<String> keys = captor.getAllValues().stream().map(r -> r.sensorId().value()).toList();
        assertThat(keys).containsExactly(
                "sensor_A", "sensor_B", "sensor_C", "sensor_D", "sensor_E",
                "sensor_F", "sensor_G", "sensor_H", "sensor_I", "sensor_J"
        );
    }
}
