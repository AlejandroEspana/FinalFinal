package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.application.service.OperatorConsoleApplicationService;
import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class RabbitMQHumanAlertConsumerAdapter {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQHumanAlertConsumerAdapter.class);

    private final OperatorConsoleApplicationService consoleApplicationService;
    private final ObjectMapper objectMapper;

    public RabbitMQHumanAlertConsumerAdapter(
            OperatorConsoleApplicationService consoleApplicationService,
            ObjectMapper objectMapper
    ) {
        this.consoleApplicationService = Objects.requireNonNull(consoleApplicationService, "consoleApplicationService no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
    }

    @RabbitListener(queues = "${app.rabbitmq.queues.operator-alerts:operator_console_alerts_queue}")
    public void onAlertMessage(String messagePayload) {
        try {
            HumanActionRequest request = objectMapper.readValue(messagePayload, HumanActionRequest.class);
            consoleApplicationService.registerAndBroadcast(request);
        } catch (Exception e) {
            log.error("Error procesando alerta humana desde RabbitMQ en operator_console_backend: {}", e.getMessage(), e);
            throw new AmqpRejectAndDontRequeueException("Error procesando alerta humana desde RabbitMQ", e);
        }
    }
}
