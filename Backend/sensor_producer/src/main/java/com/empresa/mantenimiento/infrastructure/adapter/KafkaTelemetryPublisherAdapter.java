package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import com.empresa.mantenimiento.domain.repository.TelemetryEventPublisherPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Adaptador de infraestructura Kafka que implementa TelemetryEventPublisherPort.
 * Aplica particionamiento por clave (key = sensorId) para garantizar el orden por sensor.
 */
@Component
public class KafkaTelemetryPublisherAdapter implements TelemetryEventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaTelemetryPublisherAdapter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topicName;

    public KafkaTelemetryPublisherAdapter(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.sensor-data:sensor_data}") String topicName
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        this.topicName = Objects.requireNonNull(topicName, "topicName no puede ser nulo");
    }

    @Override
    public void publish(VibrationReadingRecord reading) {
        Objects.requireNonNull(reading, "La lectura no puede ser nula");
        String partitionKey = reading.sensorId().value();

        try {
            String jsonPayload = objectMapper.writeValueAsString(reading);
            kafkaTemplate.send(topicName, partitionKey, jsonPayload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Fallo al publicar lectura de {} en Kafka topic {}: {}",
                                    partitionKey, topicName, ex.getMessage());
                        } else {
                            log.debug("Lectura publicada en Kafka [Topic: {}, Key: {}, Partition: {}]",
                                    topicName, partitionKey, result.getRecordMetadata().partition());
                        }
                    });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error serializando lectura de vibración a JSON", e);
        }
    }
}
