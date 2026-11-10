package com.empresa.mantenimiento.infrastructure;

import com.empresa.mantenimiento.domain.model.MaintenanceWorkOrder;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.model.ShutdownCommand;
import com.empresa.mantenimiento.infrastructure.adapter.RabbitMQActionCommandPublisherAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RabbitMQActionCommandPublisherAdapterTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitMQActionCommandPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        adapter = new RabbitMQActionCommandPublisherAdapter(
                rabbitTemplate,
                mapper,
                "critical_actions_queue",
                "maintenance_queue",
                "delayed_actions_exchange",
                "maintenance_route"
        );
    }

    @Test
    @DisplayName("Debe enviar mensaje con header x-delay al exchange delayed_actions_exchange")
    void shouldSendMessageWithXDelayHeader() {
        // Arrange
        MaintenanceWorkOrder wo = new MaintenanceWorkOrder(
                "WO-DEL-1",
                new SensorId("sensor_F"),
                "Diferido",
                true,
                10000L,
                "operator",
                Instant.now()
        );

        // Act
        adapter.dispatchDelayedMaintenance(wo, 10000L);

        // Assert
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate, times(1)).send(
                eq("delayed_actions_exchange"),
                eq("maintenance_route"),
                messageCaptor.capture()
        );

        Message sentMessage = messageCaptor.getValue();
        Object xDelayHeader = sentMessage.getMessageProperties().getHeaders().get("x-delay");
        assertThat(xDelayHeader).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Debe publicar comando de apagado crítico en critical_actions_queue")
    void shouldSendCriticalShutdownToQueue() {
        // Arrange
        ShutdownCommand cmd = new ShutdownCommand(
                "CMD-1",
                new SensorId("sensor_A"),
                "Emergencia",
                "operator",
                Instant.now()
        );

        // Act
        adapter.dispatchShutdown(cmd);

        // Assert
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(""),
                eq("critical_actions_queue"),
                payloadCaptor.capture()
        );
        assertThat(payloadCaptor.getValue()).contains("sensor_A").contains("CMD-1");
    }
}
