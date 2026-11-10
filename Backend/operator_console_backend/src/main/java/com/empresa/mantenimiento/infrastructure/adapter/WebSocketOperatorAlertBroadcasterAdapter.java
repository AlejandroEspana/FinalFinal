package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.domain.model.HumanActionRequest;
import com.empresa.mantenimiento.domain.repository.OperatorAlertBroadcasterPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class WebSocketOperatorAlertBroadcasterAdapter implements OperatorAlertBroadcasterPort {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketOperatorAlertBroadcasterAdapter(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate, "messagingTemplate no puede ser nulo");
    }

    @Override
    public void broadcastAlert(HumanActionRequest request) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        messagingTemplate.convertAndSend("/topic/operator-alerts", request);
    }
}
