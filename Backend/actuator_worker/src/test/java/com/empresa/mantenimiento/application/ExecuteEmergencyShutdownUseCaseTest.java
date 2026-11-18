package com.empresa.mantenimiento.application;

import com.empresa.mantenimiento.application.dto.ActuatorExecutionDto;
import com.empresa.mantenimiento.application.usecase.ExecuteEmergencyShutdownUseCase;
import com.empresa.mantenimiento.domain.model.ActuatorExecutionRecord;
import com.empresa.mantenimiento.domain.model.PhysicalActuator;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.model.ShutdownCommand;
import com.empresa.mantenimiento.domain.repository.ActuatorExecutionRepositoryPort;
import com.empresa.mantenimiento.domain.service.EmergencySafetyDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExecuteEmergencyShutdownUseCaseTest {

    @Mock
    private ActuatorExecutionRepositoryPort repositoryPort;

    private ExecuteEmergencyShutdownUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExecuteEmergencyShutdownUseCase(repositoryPort, new EmergencySafetyDomainService());
    }

    @Test
    @DisplayName("Debe ejecutar corte de energía y guardar registro de ejecución")
    void shouldExecuteEmergencyShutdownAndSaveRecord() {
        SensorId sensorId = new SensorId("sensor_A");
        when(repositoryPort.getOrCreateActuator(sensorId)).thenReturn(new PhysicalActuator(sensorId));
        ShutdownCommand command = new ShutdownCommand(
                "CMD-77",
                sensorId,
                "Parada inmediata",
                "operator",
                Instant.now()
        );

        ActuatorExecutionDto dto = useCase.execute(command);

        verify(repositoryPort, times(1)).saveExecutionRecord(any(ActuatorExecutionRecord.class));
        assertThat(dto.currentState()).isEqualTo("EMERGENCY_HALTED");
        assertThat(dto.sensorId()).isEqualTo("sensor_A");
    }
}
