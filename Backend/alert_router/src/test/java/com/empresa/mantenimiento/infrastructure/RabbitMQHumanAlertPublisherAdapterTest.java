package com.empresa.mantenimiento.infrastructure;

import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.infrastructure.adapter.RabbitMQHumanAlertPublisherAdapter;
import com.fasterxml.jackson.databind.JsonNode;
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
@DisplayName("Pruebas Unitarias Aisladas con Mockito y Contrato JSON: RabbitMQHumanAlertPublisherAdapter")
class RabbitMQHumanAlertPublisherAdapterTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private ObjectMapper mapper;
    private RabbitMQHumanAlertPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        adapter = new RabbitMQHumanAlertPublisherAdapter(rabbitTemplate, mapper, "human_alerts");
    }

    @Test
    @DisplayName("Publica en Fanout Exchange human_alerts serializando alertId y sensorId como texto plano JSON")
    void shouldPublishToFanoutExchangeWithFlatJsonStrings() throws Exception {
        HumanActionRequest req = new HumanActionRequest(
                AlertId.of("ALT-99"), SensorId.of("sensor_A"), AlertSeverity.CRITICAL,
                95.0, "Peligro", List.of("APAGADO_INMEDIATO", "IGNORAR_10_MINUTOS"), Instant.now()
        );
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        adapter.publish(req);

        verify(rabbitTemplate).convertAndSend(eq("human_alerts"), eq(""), payloadCaptor.capture());
        String json = payloadCaptor.getValue();
        JsonNode root = mapper.readTree(json);

        // Verifica contrato JSON exacto para el Frontend (texto plano, nunca {"value": ...})
        assertThat(root.get("alertId").isTextual()).isTrue();
        assertThat(root.get("alertId").asText()).isEqualTo("ALT-99");
        assertThat(root.get("sensorId").isTextual()).isTrue();
        assertThat(root.get("sensorId").asText()).isEqualTo("sensor_A");
        assertThat(root.get("availableOptions").isArray()).isTrue();
        assertThat(root.get("availableOptions").get(0).asText()).isEqualTo("APAGADO_INMEDIATO");
    }
}
