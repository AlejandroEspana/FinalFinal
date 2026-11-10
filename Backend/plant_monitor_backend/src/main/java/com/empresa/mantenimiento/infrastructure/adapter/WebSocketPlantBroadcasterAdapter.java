package com.empresa.mantenimiento.infrastructure.adapter;

import com.empresa.mantenimiento.application.dto.SensorPlantStatusDto;
import com.empresa.mantenimiento.domain.repository.PlantStatusBroadcasterPort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class WebSocketPlantBroadcasterAdapter implements PlantStatusBroadcasterPort {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketPlantBroadcasterAdapter(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = Objects.requireNonNull(messagingTemplate, "messagingTemplate no puede ser nulo");
    }

    @Override
    public void broadcastStatus(SensorPlantStatusDto statusDto) {
        Objects.requireNonNull(statusDto, "statusDto no puede ser nulo");
        messagingTemplate.convertAndSend("/topic/plant-status", statusDto);
    }
}
