package com.empresa.mantenimiento.domain;

import com.empresa.mantenimiento.domain.exception.InvalidDecisionActionException;
import com.empresa.mantenimiento.domain.model.AlertId;
import com.empresa.mantenimiento.domain.model.MaintenanceDecision;
import com.empresa.mantenimiento.domain.model.OperatorDecisionRecord;
import com.empresa.mantenimiento.domain.model.SensorId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias aisladas de Dominio Rico (en memoria RAM, patrón AAA).
 */
class MaintenanceDecisionTest {

    @Test
    @DisplayName("Debe aplicar semánticamente APAGADO_INMEDIATO enrutando a critical_actions_queue")
    void shouldApplyImmediateShutdownAction() {
        // Arrange
        OperatorDecisionRecord record = new OperatorDecisionRecord(
                new AlertId("ALT-001"),
                new SensorId("sensor_A"),
                MaintenanceDecision.ACTION_SHUTDOWN_NOW,
                "operator_1",
                Instant.now(),
                "Parada inmediata por sobrevibración"
        );
        MaintenanceDecision decision = new MaintenanceDecision(record);

        // Act
        decision.aplicarAccion(10000L);

        // Assert
        assertThat(decision.getStatus()).isEqualTo("DISPATCHED");
        assertThat(decision.getRoutingTarget()).isEqualTo("critical_actions_queue");
        assertThat(decision.getDelayMillis()).isZero();
        assertThat(decision.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("Debe configurar delayMillis cuando la acción es RECONOCER_Y_ESPERAR_24H")
    void shouldConfigureDelayedMaintenanceExchange() {
        // Arrange
        OperatorDecisionRecord record = new OperatorDecisionRecord(
                new AlertId("ALT-002"),
                new SensorId("sensor_B"),
                MaintenanceDecision.ACTION_ACK_WAIT_24H,
                "operator_1",
                Instant.now(),
                "Programar diferido"
        );
        MaintenanceDecision decision = new MaintenanceDecision(record);

        // Act
        decision.aplicarAccion(10000L);

        // Assert
        assertThat(decision.getStatus()).isEqualTo("DISPATCHED");
        assertThat(decision.getDelayMillis()).isEqualTo(10000L);
        assertThat(decision.getRoutingTarget()).contains("delayed_actions_exchange");
    }

    @Test
    @DisplayName("Debe lanzar InvalidDecisionActionException ante una acción desconocida (Fail-Fast)")
    void shouldRejectInvalidAction() {
        // Arrange
        OperatorDecisionRecord invalidRecord = new OperatorDecisionRecord(
                new AlertId("ALT-003"),
                new SensorId("sensor_C"),
                "ACCION_NO_EXISTENTE",
                "operator_1",
                Instant.now(),
                "Nota"
        );

        // Act & Assert
        assertThatThrownBy(() -> new MaintenanceDecision(invalidRecord))
                .isInstanceOf(InvalidDecisionActionException.class);
    }
}
