package com.empresa.mantenimiento.infrastructure;

import com.empresa.mantenimiento.application.service.MaintenanceOrchestrationService;
import com.empresa.mantenimiento.domain.model.MaintenanceWorkOrder;
import com.empresa.mantenimiento.infrastructure.adapter.RabbitMQMaintenanceConsumerAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMQMaintenanceConsumerAdapterTest {

    @Mock
    private MaintenanceOrchestrationService orchestrationService;

    private RabbitMQMaintenanceConsumerAdapter consumerAdapter;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumerAdapter = new RabbitMQMaintenanceConsumerAdapter(orchestrationService, mapper);
    }

    @Test
    @DisplayName("Debe deserializar mensaje diferido de maintenance_queue e invocar processWorkOrder")
    void shouldDeserializeDelayedMaintenanceMessage() {
        // Arrange
        String json = """
                {
                  "workOrderId": "WO-DELAYED-88",
                  "sensorId": "sensor_E",
                  "priority": "Mantenimiento Preventivo Programado (Diferido)",
                  "delayed": true,
                  "delayMillis": 10000,
                  "operatorId": "operator",
                  "scheduledAt": "2026-05-10T12:00:00Z"
                }
                """;

        // Act
        consumerAdapter.onMaintenanceWorkOrderMessage(json);

        // Assert
        ArgumentCaptor<MaintenanceWorkOrder> captor = ArgumentCaptor.forClass(MaintenanceWorkOrder.class);
        verify(orchestrationService, times(1)).processWorkOrder(captor.capture());
        assertThat(captor.getValue().workOrderId()).isEqualTo("WO-DELAYED-88");
        assertThat(captor.getValue().delayed()).isTrue();
        assertThat(captor.getValue().delayMillis()).isEqualTo(10000L);
    }
}
