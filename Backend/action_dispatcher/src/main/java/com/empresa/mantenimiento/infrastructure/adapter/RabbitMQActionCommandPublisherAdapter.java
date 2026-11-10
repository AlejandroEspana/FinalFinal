package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.domain.model.MaintenanceWorkOrder;
import com.empresa.mantenimiento.domain.model.ShutdownCommand;
import com.empresa.mantenimiento.domain.repository.ActionCommandPublisherPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Adaptador de salida RabbitMQ que implementa el despacho de comandos críticos,
 * mantenimiento inmediato y mantenimiento diferido con plugin x-delayed-message.
 */
@Component
public class RabbitMQActionCommandPublisherAdapter implements ActionCommandPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQActionCommandPublisherAdapter.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String criticalQueueName;
    private final String maintenanceQueueName;
    private final String delayedExchangeName;
    private final String delayedRoutingKey;

    public RabbitMQActionCommandPublisherAdapter(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${app.rabbitmq.queues.critical-actions:critical_actions_queue}") String criticalQueueName,
            @Value("${app.rabbitmq.queues.maintenance:maintenance_queue}") String maintenanceQueueName,
            @Value("${app.rabbitmq.exchanges.delayed-actions:delayed_actions_exchange}") String delayedExchangeName,
            @Value("${app.rabbitmq.routing-keys.delayed-maintenance:maintenance_route}") String delayedRoutingKey
    ) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        this.criticalQueueName = criticalQueueName;
        this.maintenanceQueueName = maintenanceQueueName;
        this.delayedExchangeName = delayedExchangeName;
        this.delayedRoutingKey = delayedRoutingKey;
    }

    @Override
    public void dispatchShutdown(ShutdownCommand command) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("commandId", command.commandId());
            payload.put("sensorId", command.sensorId().value());
            payload.put("reason", command.reason());
            payload.put("operatorId", command.initiatedBy());
            payload.put("issuedAt", command.issuedAt().toString());

            String json = objectMapper.writeValueAsString(payload);
            rabbitTemplate.convertAndSend("", criticalQueueName, json);
            log.warn("Comando de PARADA DE EMERGENCIA despachado a cola '{}': sensor={}",
                    criticalQueueName, command.sensorId().value());
        } catch (Exception e) {
            throw new IllegalStateException("Error despachando comando de parada crítica a RabbitMQ", e);
        }
    }

    @Override
    public void dispatchImmediateMaintenance(MaintenanceWorkOrder workOrder) {
        try {
            String json = serializeWorkOrder(workOrder);
            rabbitTemplate.convertAndSend("", maintenanceQueueName, json);
            log.info("Orden de mantenimiento INMEDIATO despachada a cola '{}': sensor={}",
                    maintenanceQueueName, workOrder.sensorId().value());
        } catch (Exception e) {
            throw new IllegalStateException("Error despachando mantenimiento inmediato a RabbitMQ", e);
        }
    }

    @Override
    public void dispatchDelayedMaintenance(MaintenanceWorkOrder workOrder, long delayMillis) {
        try {
            String json = serializeWorkOrder(workOrder);
            MessageProperties props = new MessageProperties();
            props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            props.setHeader("x-delay", delayMillis);
            Message message = new Message(json.getBytes(StandardCharsets.UTF_8), props);

            rabbitTemplate.send(delayedExchangeName, delayedRoutingKey, message);
            log.info("Orden de mantenimiento DIFERIDO despachada a exchange '{}' (routingKey='{}', x-delay={}ms): sensor={}",
                    delayedExchangeName, delayedRoutingKey, delayMillis, workOrder.sensorId().value());
        } catch (Exception e) {
            throw new IllegalStateException("Error despachando mantenimiento diferido a RabbitMQ", e);
        }
    }

    private String serializeWorkOrder(MaintenanceWorkOrder workOrder) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workOrderId", workOrder.orderId());
        payload.put("sensorId", workOrder.sensorId().value());
        payload.put("priority", workOrder.actionType());
        payload.put("delayed", workOrder.isDelayed());
        payload.put("delayMillis", workOrder.delayMillis());
        payload.put("operatorId", workOrder.requestedBy());
        payload.put("scheduledAt", workOrder.scheduledAt().toString());
        return objectMapper.writeValueAsString(payload);
    }
}
