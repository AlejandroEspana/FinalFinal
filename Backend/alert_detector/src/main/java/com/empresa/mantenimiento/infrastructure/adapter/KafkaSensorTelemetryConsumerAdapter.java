package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.application.service.AnomalyDetectionApplicationService;
import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class KafkaSensorTelemetryConsumerAdapter {

    private static final Logger log = LoggerFactory.getLogger(KafkaSensorTelemetryConsumerAdapter.class);

    private final AnomalyDetectionApplicationService detectionApplicationService;
    private final ObjectMapper objectMapper;

    public KafkaSensorTelemetryConsumerAdapter(
            AnomalyDetectionApplicationService detectionApplicationService,
            ObjectMapper objectMapper
    ) {
        this.detectionApplicationService = Objects.requireNonNull(detectionApplicationService, "detectionApplicationService no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    @KafkaListener(
            topics = "${app.kafka.topics.sensor-data:sensor_data}",
            groupId = "${spring.kafka.consumer.group-id:alert-detector-group}"
    )
    public void onTelemetryMessage(ConsumerRecord<String, String> record) {
        try {
            VibrationReadingRecord reading = objectMapper.readValue(record.value(), VibrationReadingRecord.class);
            detectionApplicationService.handleIncomingReading(reading);
        } catch (Exception e) {
            log.error("Fallo al consumir lectura en alert_detector [Key={}]: {}", record.key(), e.getMessage(), e);
            throw new IllegalStateException("Error procesando mensaje de telemetría en alert_detector", e);
        }
    }
}
