package com.empresa.mantenimiento.application;

import com.empresa.mantenimiento.application.usecase.EvaluateSensorTelemetryUseCase;
import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.model.IndustrialAlert;
import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import com.empresa.mantenimiento.domain.model.strategy.RuleBasedThresholdStrategy;
import com.empresa.mantenimiento.domain.repository.AlertPublisherPort;
import com.empresa.mantenimiento.domain.service.WindowRegistryDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: EvaluateSensorTelemetryUseCase")
class EvaluateSensorTelemetryUseCaseTest {

    @Mock
    private AlertPublisherPort alertPublisher;

    private EvaluateSensorTelemetryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new EvaluateSensorTelemetryUseCase(
                new WindowRegistryDomainService(),
                new RuleBasedThresholdStrategy(),
                alertPublisher
        );
    }

    @Test
    @DisplayName("Debe despachar exactamente 1 alerta CRITICAL ante una lectura > 90")
    void shouldPublishCriticalAlertOnSpike() {
        // Arrange
        VibrationReadingRecord reading = VibrationReadingRecord.of("sensor_A", 97.2);
        ArgumentCaptor<IndustrialAlert> captor = ArgumentCaptor.forClass(IndustrialAlert.class);

        // Act
        Optional<IndustrialAlert> result = useCase.execute(reading);

        // Assert
        assertThat(result).isPresent();
        verify(alertPublisher, times(1)).publishAlert(captor.capture());
        assertThat(captor.getValue().severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(captor.getValue().triggerValue()).isEqualTo(97.2);
    }
}
