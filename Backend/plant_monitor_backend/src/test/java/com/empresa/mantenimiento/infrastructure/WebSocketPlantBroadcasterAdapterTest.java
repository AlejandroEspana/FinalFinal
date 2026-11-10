package com.empresa.mantenimiento.infrastructure;

import com.empresa.mantenimiento.application.dto.SensorPlantStatusDto;
import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.infrastructure.adapter.WebSocketPlantBroadcasterAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: WebSocketPlantBroadcasterAdapter")
class WebSocketPlantBroadcasterAdapterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("Difunde el DTO de estado al destino /topic/plant-status")
    void shouldBroadcastToStompTopic() {
        WebSocketPlantBroadcasterAdapter adapter = new WebSocketPlantBroadcasterAdapter(messagingTemplate);
        SensorPlantStatusDto dto = new SensorPlantStatusDto("sensor_A", AlertSeverity.WARNING, 79.0, "Advertencia", Instant.now());

        adapter.broadcastStatus(dto);

        verify(messagingTemplate).convertAndSend("/topic/plant-status", dto);
    }
}
