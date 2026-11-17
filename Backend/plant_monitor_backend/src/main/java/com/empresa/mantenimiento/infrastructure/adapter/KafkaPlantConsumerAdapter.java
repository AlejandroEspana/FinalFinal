package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.application.usecase.UpdatePlantStatusUseCase;
import com.empresa.mantenimiento.domain.model.IndustrialAlert;
import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class KafkaPlantConsumerAdapter {

    private static final Logger log = LoggerFactory.getLogger(KafkaPlantConsumerAdapter.class);

    private final UpdatePlantStatusUseCase updatePlantStatusUseCase;
    private final ObjectMapper objectMapper;

    public KafkaPlantConsumerAdapter(
            UpdatePlantStatusUseCase updatePlantStatusUseCase,
            ObjectMapper objectMapper
    ) {
        this.updatePlantStatusUseCase = Objects.requireNonNull(updatePlantStatusUseCase, "updatePlantStatusUseCase no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    @KafkaListener(
            topics = {
                    "${app.kafka.topics.alerts-critical:alerts_critical}",
                    "${app.kafka.topics.alerts-warning:alerts_warning}"
            },
            groupId = "plant-monitor-alerts-group"
    )
    public void onAlertMessage(ConsumerRecord<String, String> record) {
        try {
            IndustrialAlert alert = objectMapper.readValue(record.value(), IndustrialAlert.class);
            updatePlantStatusUseCase.applyAlert(alert);
        } catch (Exception e) {
            log.error("Error procesando alerta en plant_monitor_backend: {}", e.getMessage(), e);
            throw new IllegalStateException("Error procesando alerta en plant_monitor_backend", e);
        }
    }

    @KafkaListener(
            topics = "${app.kafka.topics.sensor-data:sensor_data}",
            groupId = "plant-monitor-telemetry-group"
    )
    public void onTelemetryMessage(ConsumerRecord<String, String> record) {
        try {
            VibrationReadingRecord reading = objectMapper.readValue(record.value(), VibrationReadingRecord.class);
            updatePlantStatusUseCase.applyTelemetry(reading);
        } catch (Exception e) {
            log.error("Error procesando telemetría en plant_monitor_backend: {}", e.getMessage(), e);
            throw new IllegalStateException("Error procesando telemetría en plant_monitor_backend", e);
        }
    }
}
