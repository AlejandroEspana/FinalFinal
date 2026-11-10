package com.empresa.mantenimiento.infrastructure;

import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.model.IndustrialAlert;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.infrastructure.adapter.KafkaAlertPublisherAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: KafkaAlertPublisherAdapter")
class KafkaAlertPublisherAdapterTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaAlertPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        adapter = new KafkaAlertPublisherAdapter(kafkaTemplate, mapper, "alerts_critical", "alerts_warning");
    }

    @Test
    @DisplayName("Enruta alertas CRITICAL hacia alerts_critical y WARNING hacia alerts_warning")
    void shouldRouteAlertsToCorrespondingTopics() {
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        IndustrialAlert critical = IndustrialAlert.create(SensorId.of("sensor_A"), AlertSeverity.CRITICAL, 95.0, "Pico");
        adapter.publishAlert(critical);
        verify(kafkaTemplate).send(eq("alerts_critical"), eq("sensor_A"), any());

        IndustrialAlert warning = IndustrialAlert.create(SensorId.of("sensor_B"), AlertSeverity.WARNING, 79.0, "Acumulado");
        adapter.publishAlert(warning);
        verify(kafkaTemplate).send(eq("alerts_warning"), eq("sensor_B"), any());
    }
}
