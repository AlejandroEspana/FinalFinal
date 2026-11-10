package com.empresa.mantenimiento.infrastructure;

import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.infrastructure.adapter.WebSocketOperatorAlertBroadcasterAdapter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: WebSocketOperatorAlertBroadcasterAdapter")
class WebSocketOperatorAlertBroadcasterAdapterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    @DisplayName("Difunde la solicitud interactiva al destino /topic/operator-alerts")
    void shouldBroadcastToOperatorAlertsTopic() {
        WebSocketOperatorAlertBroadcasterAdapter adapter = new WebSocketOperatorAlertBroadcasterAdapter(messagingTemplate);
        HumanActionRequest req = new HumanActionRequest(
                AlertId.of("ALT-3"), SensorId.of("sensor_A"), AlertSeverity.CRITICAL,
                97.0, "Falla", List.of("APAGADO_INMEDIATO"), Instant.now()
        );

        adapter.broadcastAlert(req);

        verify(messagingTemplate).convertAndSend("/topic/operator-alerts", req);
    }
}
