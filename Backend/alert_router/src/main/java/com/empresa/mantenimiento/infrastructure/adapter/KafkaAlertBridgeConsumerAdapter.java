package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.application.service.AlertRouterApplicationService;
import com.empresa.mantenimiento.domain.model.IndustrialAlert;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class KafkaAlertBridgeConsumerAdapter {

    private static final Logger log = LoggerFactory.getLogger(KafkaAlertBridgeConsumerAdapter.class);

    private final AlertRouterApplicationService routerApplicationService;
    private final ObjectMapper objectMapper;

    public KafkaAlertBridgeConsumerAdapter(
            AlertRouterApplicationService routerApplicationService,
            ObjectMapper objectMapper
    ) {
        this.routerApplicationService = Objects.requireNonNull(routerApplicationService, "routerApplicationService no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    @KafkaListener(
            topics = {
                    "${app.kafka.topics.alerts-critical:alerts_critical}",
                    "${app.kafka.topics.alerts-warning:alerts_warning}"
            },
            groupId = "${spring.kafka.consumer.group-id:alert-router-group}"
    )
    public void onAlertMessage(ConsumerRecord<String, String> record) {
        try {
            IndustrialAlert alert = objectMapper.readValue(record.value(), IndustrialAlert.class);
            routerApplicationService.route(alert);
        } catch (Exception e) {
            log.error("Error procesando alerta desde Kafka topic {}: {}", record.topic(), e.getMessage(), e);
            throw new IllegalStateException("Error procesando alerta en alert_router", e);
        }
    }
}
