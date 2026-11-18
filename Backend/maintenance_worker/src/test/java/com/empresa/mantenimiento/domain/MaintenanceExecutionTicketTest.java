package com.empresa.mantenimiento.domain;

import com.empresa.mantenimiento.domain.exception.InvalidWorkOrderException;
import com.empresa.mantenimiento.domain.model.MaintenanceExecutionTicket;
import com.empresa.mantenimiento.domain.model.MaintenanceWorkOrder;
import com.empresa.mantenimiento.domain.model.SensorId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaintenanceExecutionTicketTest {

    @Test
    @DisplayName("Debe completar orden de mantenimiento diferida reflejando el x-delay en el resumen")
    void shouldCompleteDelayedWorkOrder() {
        MaintenanceWorkOrder wo = new MaintenanceWorkOrder(
                "WO-DELAYED-1",
                new SensorId("sensor_B"),
                "Mantenimiento Preventivo",
                true,
                10000L,
                "operator",
                Instant.now()
        );
        MaintenanceExecutionTicket ticket = new MaintenanceExecutionTicket(wo);

        ticket.completarOrdenTecnica();

        assertThat(ticket.getTicketStatus()).isEqualTo("COMPLETED");
        assertThat(ticket.getExecutionSummary()).contains("DIFERIDO").contains("10000 ms");
        assertThat(ticket.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("Debe rechazar un delayMillis negativo (Fail-Fast)")
    void shouldRejectNegativeDelayMillis() {
        assertThatThrownBy(() -> new MaintenanceWorkOrder(
                "WO-1",
                new SensorId("sensor_A"),
                "Urgente",
                true,
                -500L,
                "operator",
                Instant.now()
        )).isInstanceOf(InvalidWorkOrderException.class);
    }
}
