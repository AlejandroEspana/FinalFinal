package com.empresa.mantenimiento.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Value("${app.rabbitmq.queues.maintenance:maintenance_queue}")
    private String maintenanceQueueName;

    @Bean
    public Queue maintenanceQueue() {
        return new Queue(maintenanceQueueName, true, false, false);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
