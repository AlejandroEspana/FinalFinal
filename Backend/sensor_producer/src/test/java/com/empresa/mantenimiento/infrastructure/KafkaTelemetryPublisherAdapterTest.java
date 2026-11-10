package com.empresa.mantenimiento.infrastructure;

import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import com.empresa.mantenimiento.infrastructure.adapter.KafkaTelemetryPublisherAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: KafkaTelemetryPublisherAdapter")
class KafkaTelemetryPublisherAdapterTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private KafkaTelemetryPublisherAdapter adapter;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        adapter = new KafkaTelemetryPublisherAdapter(kafkaTemplate, mapper, "sensor_data");
    }

    @Test
    @DisplayName("Debe publicar al topic sensor_data utilizando sensorId como clave de partición")
    void shouldPublishWithSensorIdAsPartitionKey() {
        // Arrange
        VibrationReadingRecord reading = VibrationReadingRecord.of("sensor_A", 95.5);
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        when(kafkaTemplate.send(eq("sensor_data"), eq("sensor_A"), any())).thenReturn(future);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        // Act
        adapter.publish(reading);

        // Assert
        verify(kafkaTemplate).send(eq("sensor_data"), eq("sensor_A"), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue()).contains("sensor_A").contains("95.5");
    }
}
