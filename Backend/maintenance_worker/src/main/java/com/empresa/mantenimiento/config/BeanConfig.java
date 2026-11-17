package com.empresa.mantenimiento.config;

import com.empresa.mantenimiento.domain.service.MaintenanceSchedulingDomainService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
    public MaintenanceSchedulingDomainService maintenanceSchedulingDomainService() {
        return new MaintenanceSchedulingDomainService();
    }

    @Bean
    public Queue maintenanceQueue() {
        return new Queue(maintenanceQueueName, true, false, false);
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
