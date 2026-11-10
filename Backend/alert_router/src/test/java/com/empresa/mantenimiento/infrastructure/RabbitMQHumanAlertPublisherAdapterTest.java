package com.empresa.mantenimiento.infrastructure;

import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.infrastructure.adapter.RabbitMQHumanAlertPublisherAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: RabbitMQHumanAlertPublisherAdapter")
class RabbitMQHumanAlertPublisherAdapterTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitMQHumanAlertPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        adapter = new RabbitMQHumanAlertPublisherAdapter(rabbitTemplate, mapper, "human_alerts");
    }

    @Test
    @DisplayName("Publica en Fanout Exchange human_alerts con routingKey vacío")
    void shouldPublishToFanoutExchange() {
        HumanActionRequest req = new HumanActionRequest(
                AlertId.of("ALT-99"), SensorId.of("sensor_A"), AlertSeverity.CRITICAL,
                95.0, "Peligro", List.of("APAGADO_INMEDIATO"), Instant.now()
        );
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        adapter.publish(req);

        verify(rabbitTemplate).convertAndSend(eq("human_alerts"), eq(""), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("ALT-99").contains("APAGADO_INMEDIATO");
    }
}
