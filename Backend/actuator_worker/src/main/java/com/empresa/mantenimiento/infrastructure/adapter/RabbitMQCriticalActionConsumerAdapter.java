package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.application.service.ActuatorOrchestrationService;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.model.ShutdownCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

/**
 * Adaptador de Entrada RabbitMQ que consume comandos de parada crítica desde critical_actions_queue.
 */
@Component
public class RabbitMQCriticalActionConsumerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQCriticalActionConsumerAdapter.class);

    private final ActuatorOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;

    public RabbitMQCriticalActionConsumerAdapter(
            ActuatorOrchestrationService orchestrationService,
            ObjectMapper objectMapper
    ) {
        this.orchestrationService = Objects.requireNonNull(orchestrationService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @RabbitListener(queues = "${app.rabbitmq.queues.critical-actions:critical_actions_queue}")
    public void onCriticalShutdownMessage(String jsonPayload) {
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            String commandId = root.path("commandId").asText("CMD-UNKNOWN");
            JsonNode sensorNode = root.path("sensorId");
            String sensorId = sensorNode.isObject() ? sensorNode.path("value").asText() : sensorNode.asText();
            String reason = root.path("reason").asText("Apagado crítico de emergencia");
            String operatorId = root.path("operatorId").asText("operator");
            String issuedAtRaw = root.path("issuedAt").asText(null);
            Instant issuedAt = issuedAtRaw != null ? Instant.parse(issuedAtRaw) : Instant.now();

            ShutdownCommand command = new ShutdownCommand(
                    commandId,
                    new SensorId(sensorId),
                    reason,
                    operatorId,
                    issuedAt
            );

            orchestrationService.executeShutdown(command);
        } catch (Exception e) {
            log.error("Error procesando comando crítico en actuator_worker: {}", e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException("Error procesando comando crítico en actuator_worker", e);
        }
    }
}
