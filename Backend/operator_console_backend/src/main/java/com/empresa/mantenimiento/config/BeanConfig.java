package com.empresa.mantenimiento.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class BeanConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.rabbitmq.exchanges.human-alerts:human_alerts}")
    private String fanoutExchangeName;

    @Value("${app.rabbitmq.queues.operator-alerts:operator_console_alerts_queue}")
    private String queueName;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-alerts")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws-alerts")
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public FanoutExchange humanAlertsExchange() {
        return new FanoutExchange(fanoutExchangeName, true, false);
    }

    @Bean
    public Queue operatorAlertsQueue() {
        return new Queue(queueName, true, false, false);
    }

    @Bean
    public Binding bindingHumanAlerts(Queue operatorAlertsQueue, FanoutExchange humanAlertsExchange) {
        return BindingBuilder.bind(operatorAlertsQueue).to(humanAlertsExchange);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
