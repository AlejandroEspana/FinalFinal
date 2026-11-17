package com.empresa.mantenimiento.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class BeanConfig {

    @Value("${app.rabbitmq.queues.critical-actions:critical_actions_queue}")
    private String criticalQueueName;

    @Value("${app.rabbitmq.queues.maintenance:maintenance_queue}")
    private String maintenanceQueueName;

    @Value("${app.rabbitmq.exchanges.delayed-actions:delayed_actions_exchange}")
    private String delayedExchangeName;

    @Value("${app.rabbitmq.routing-keys.delayed-maintenance:maintenance_route}")
    private String delayedRoutingKey;

    @Bean
    public Queue criticalActionsQueue() {
        return new Queue(criticalQueueName, true, false, false);
    }

    @Bean
    public Queue maintenanceQueue() {
        return new Queue(maintenanceQueueName, true, false, false);
    }

    @Bean
    public CustomExchange delayedActionsExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(delayedExchangeName, "x-delayed-message", true, false, args);
    }

    @Bean
    public Binding bindingDelayedMaintenance(Queue maintenanceQueue, CustomExchange delayedActionsExchange) {
        return BindingBuilder.bind(maintenanceQueue)
                .to(delayedActionsExchange)
                .with(delayedRoutingKey)
                .noargs();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return mapper;
    }
}
