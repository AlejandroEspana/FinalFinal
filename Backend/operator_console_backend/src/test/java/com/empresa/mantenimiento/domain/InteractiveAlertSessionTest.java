package com.empresa.mantenimiento.domain;

import com.empresa.mantenimiento.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas de Dominio Puro (RAM / AAA): InteractiveAlertSession")
class InteractiveAlertSessionTest {

    @Test
    @DisplayName("AAA: Gestiona el ciclo de vida de la alerta interactiva y su resolución")
    void shouldManageSessionLifecycle() {
        HumanActionRequest req = new HumanActionRequest(
                AlertId.of("ALT-1"), SensorId.of("sensor_A"), AlertSeverity.CRITICAL,
                96.0, "Pico", List.of("APAGADO_INMEDIATO"), Instant.now()
        );
        InteractiveAlertSession session = new InteractiveAlertSession(req);
        assertThat(session.isResolved()).isFalse();

        session.marcarComoResuelta();
        assertThat(session.isResolved()).isTrue();
        assertThat(session.getResolvedAt()).isNotNull();
    }
}
