package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.application.service.MaintenanceOrchestrationService;
import com.empresa.mantenimiento.domain.model.MaintenanceWorkOrder;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
public class RabbitMQMaintenanceConsumerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQMaintenanceConsumerAdapter.class);

    private final MaintenanceOrchestrationService orchestrationService;
    private final ObjectMapper objectMapper;

    public RabbitMQMaintenanceConsumerAdapter(
            MaintenanceOrchestrationService orchestrationService,
            ObjectMapper objectMapper
    ) {
        this.orchestrationService = Objects.requireNonNull(orchestrationService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @RabbitListener(queues = "${app.rabbitmq.queues.maintenance:maintenance_queue}")
    public void onMaintenanceWorkOrderMessage(String jsonPayload) {
        try {
            JsonNode root = objectMapper.readTree(jsonPayload);
            String workOrderId = root.path("workOrderId").asText("WO-UNKNOWN");
            JsonNode sensorNode = root.path("sensorId");
            String sensorId = sensorNode.isObject() ? sensorNode.path("value").asText() : sensorNode.asText();
            String priority = root.path("priority").asText("Mantenimiento Programado");
            boolean delayed = root.path("delayed").asBoolean(false);
            long delayMillis = root.path("delayMillis").asLong(0L);
            String operatorId = root.path("operatorId").asText("operator");
            String scheduledAtRaw = root.path("scheduledAt").asText(null);
            Instant scheduledAt = scheduledAtRaw != null ? Instant.parse(scheduledAtRaw) : Instant.now();

            MaintenanceWorkOrder workOrder = new MaintenanceWorkOrder(
                    workOrderId,
                    new SensorId(sensorId),
                    priority,
                    delayed,
                    delayMillis,
                    operatorId,
                    scheduledAt
            );

            orchestrationService.processWorkOrder(workOrder);
        } catch (Exception e) {
            log.error("Error procesando orden de mantenimiento en maintenance_worker: {}", e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException("Error procesando orden de mantenimiento en maintenance_worker", e);
        }
    }
}
