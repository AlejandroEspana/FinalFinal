package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.model.IndustrialAlert;
import com.empresa.mantenimiento.domain.repository.AlertPublisherPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Adaptador Kafka de salida que publica en alerts_critical o alerts_warning.
 */
@Component
public class KafkaAlertPublisherAdapter implements AlertPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(KafkaAlertPublisherAdapter.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String criticalTopic;
    private final String warningTopic;

    public KafkaAlertPublisherAdapter(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.kafka.topics.alerts-critical:alerts_critical}") String criticalTopic,
            @Value("${app.kafka.topics.alerts-warning:alerts_warning}") String warningTopic
    ) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        this.criticalTopic = Objects.requireNonNull(criticalTopic, "criticalTopic no puede ser nulo");
        this.warningTopic = Objects.requireNonNull(warningTopic, "warningTopic no puede ser nulo");
    }

    @Override
    public void publishAlert(IndustrialAlert alert) {
        Objects.requireNonNull(alert, "alert no puede ser nula");
        String targetTopic = (alert.severity() == AlertSeverity.CRITICAL) ? criticalTopic : warningTopic;
        String key = alert.sensorId().value();

        try {
            String payload = objectMapper.writeValueAsString(alert);
            kafkaTemplate.send(targetTopic, key, payload)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Error al publicar alerta {} en {}: {}", alert.alertId(), targetTopic, ex.getMessage());
                        } else {
                            log.info("ALERTA DESPACHADA -> Topic: {}, Severidad: {}, Sensor: {}",
                                    targetTopic, alert.severity(), key);
                        }
                    });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Fallo serializando IndustrialAlert a JSON", e);
        }
    }
}
