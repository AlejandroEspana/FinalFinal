package com.empresa.mantenimiento.domain;

import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.model.SensorPlantStatusView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas de Dominio Puro (RAM / AAA): SensorPlantStatusView")
class SensorPlantStatusViewTest {

    @Test
    @DisplayName("AAA: Actualiza estado semánticamente al recibir alerta y al normalizar")
    void shouldUpdateAndNormalizeStateSemantically() {
        SensorPlantStatusView view = new SensorPlantStatusView(SensorId.of("sensor_A"));
        assertThat(view.getSeverity()).isEqualTo(AlertSeverity.NORMAL);

        view.aplicarAlerta(AlertSeverity.CRITICAL, 96.5, "Vibración extrema");
        assertThat(view.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(view.getLastVibration()).isEqualTo(96.5);

        view.normalizarEstado();
        assertThat(view.getSeverity()).isEqualTo(AlertSeverity.NORMAL);
    }
}
