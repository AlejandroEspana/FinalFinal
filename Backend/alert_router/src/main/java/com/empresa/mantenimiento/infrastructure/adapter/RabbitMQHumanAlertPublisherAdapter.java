package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.domain.repository.HumanAlertPublisherPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Adaptador RabbitMQ que publica en el Fanout Exchange 'human_alerts'.
 */
@Component
public class RabbitMQHumanAlertPublisherAdapter implements HumanAlertPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQHumanAlertPublisherAdapter.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String fanoutExchangeName;

    public RabbitMQHumanAlertPublisherAdapter(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${app.rabbitmq.exchanges.human-alerts:human_alerts}") String fanoutExchangeName
    ) {
        this.rabbitTemplate = Objects.requireNonNull(rabbitTemplate, "rabbitTemplate no puede ser nulo");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper no puede ser nulo");
        this.fanoutExchangeName = Objects.requireNonNull(fanoutExchangeName, "fanoutExchangeName no puede ser nulo");
    }

    @Override
    public void publish(HumanActionRequest request) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        try {
            String payload = objectMapper.writeValueAsString(request);
            rabbitTemplate.convertAndSend(fanoutExchangeName, "", payload);
            log.info("Solicitud humana publicada en Fanout Exchange [{}] -> Alerta: {}, Opciones: {}",
                    fanoutExchangeName, request.alertId(), request.availableOptions());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error serializando HumanActionRequest a JSON", e);
        }
    }
}
