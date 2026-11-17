package com.empresa.mantenimiento.application;

import com.empresa.mantenimiento.application.dto.DecisionResultDto;
import com.empresa.mantenimiento.application.usecase.ProcessOperatorDecisionUseCase;
import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.domain.repository.ActionCommandPublisherPort;
import com.empresa.mantenimiento.domain.repository.DecisionAuditRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias aisladas con Mockito para la capa de Aplicación (Use Case).
 */
@ExtendWith(MockitoExtension.class)
class ProcessOperatorDecisionUseCaseTest {

    @Mock
    private DecisionAuditRepositoryPort auditRepositoryPort;

    @Mock
    private ActionCommandPublisherPort commandPublisherPort;

    private ProcessOperatorDecisionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProcessOperatorDecisionUseCase(auditRepositoryPort, commandPublisherPort, 10000L);
    }

    @Test
    @DisplayName("Debe despachar ShutdownCommand y registrar auditoría cuando el operador elige APAGADO_INMEDIATO")
    void shouldDispatchShutdownAndAudit() {
        // Arrange
        when(auditRepositoryPort.recordAudit(any(MaintenanceDecision.class))).thenReturn(101L);
        OperatorDecisionRecord record = new OperatorDecisionRecord(
                new AlertId("ALT-100"),
                new SensorId("sensor_A"),
                MaintenanceDecision.ACTION_SHUTDOWN_NOW,
                "operator",
                Instant.now(),
                "Vibración crítica > 90"
        );

        // Act
        DecisionResultDto result = useCase.execute(record);

        // Assert
        ArgumentCaptor<ShutdownCommand> cmdCaptor = ArgumentCaptor.forClass(ShutdownCommand.class);
        verify(auditRepositoryPort, times(1)).recordAudit(any(MaintenanceDecision.class));
        verify(commandPublisherPort, times(1)).dispatchShutdown(cmdCaptor.capture());

        assertThat(cmdCaptor.getValue().sensorId().value()).isEqualTo("sensor_A");
        assertThat(result.auditId()).isEqualTo(101L);
        assertThat(result.routingTarget()).isEqualTo("critical_actions_queue");
    }

    @Test
    @DisplayName("Debe despachar MaintenanceWorkOrder diferido con x-delay cuando elige RECONOCER_Y_ESPERAR_24H")
    void shouldDispatchDelayedMaintenanceWorkOrder() {
        // Arrange
        when(auditRepositoryPort.recordAudit(any(MaintenanceDecision.class))).thenReturn(202L);
        OperatorDecisionRecord record = new OperatorDecisionRecord(
                new AlertId("ALT-200"),
                new SensorId("sensor_D"),
                MaintenanceDecision.ACTION_ACK_WAIT_24H,
                "operator",
                Instant.now(),
                "Posponer mantenimiento 24h (simulado 10s)"
        );

        // Act
        DecisionResultDto result = useCase.execute(record);

        // Assert
        verify(auditRepositoryPort, times(1)).recordAudit(any(MaintenanceDecision.class));
        verify(commandPublisherPort, times(1))
                .dispatchDelayedMaintenance(any(MaintenanceWorkOrder.class), eq(10000L));
        assertThat(result.auditId()).isEqualTo(202L);
    }

    @Test
    @DisplayName("Fiabilidad Transaccional: Si falla el guardado en PostgreSQL, NUNCA publica el comando en RabbitMQ")
    void shouldNotPublishToRabbitMqWhenDatabaseAuditFails() {
        // Arrange
        when(auditRepositoryPort.recordAudit(any(MaintenanceDecision.class)))
                .thenThrow(new IllegalStateException("PostgreSQL connection error"));
        OperatorDecisionRecord record = new OperatorDecisionRecord(
                new AlertId("ALT-300"),
                new SensorId("sensor_A"),
                MaintenanceDecision.ACTION_SHUTDOWN_NOW,
                "operator",
                Instant.now(),
                "Falla simulada de base de datos"
        );

        // Act & Assert
        assertThatThrownBy(() -> useCase.execute(record))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(commandPublisherPort);
    }
}
