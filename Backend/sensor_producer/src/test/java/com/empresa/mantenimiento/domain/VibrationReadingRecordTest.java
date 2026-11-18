package com.empresa.mantenimiento.domain;

import com.empresa.mantenimiento.domain.exception.InvalidTelemetryException;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.model.SensorSimulationProfile;
import com.empresa.mantenimiento.domain.model.VibrationReadingRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas de Dominio Puro (RAM / AAA): VibrationReadingRecord y SensorSimulationProfile")
class VibrationReadingRecordTest {

    @Test
    @DisplayName("AAA: Debe instanciar una lectura inmutable válida y proteger invariantes del perfil")
    void shouldCreateValidReadingAndRegisterInProfile() {
        SensorSimulationProfile profile = new SensorSimulationProfile(SensorId.of("sensor_A"), "Sector #1");

        VibrationReadingRecord reading = profile.registrarEmision(48.5);

        assertThat(reading.sensorId().value()).isEqualTo("sensor_A");
        assertThat(reading.vibrationValue()).isEqualTo(48.5);
        assertThat(profile.getTotalReadingsEmitted()).isEqualTo(1L);
        assertThat(profile.getLastGeneratedVibration()).isEqualTo(48.5);
    }

    @Test
    @DisplayName("Fail-Fast: Debe rechazar valores negativos o fuera de escala física (>500)")
    void shouldFailWhenVibrationOutOfBounds() {
        assertThatThrownBy(() -> VibrationReadingRecord.of("sensor_A", -5.0))
                .isInstanceOf(InvalidTelemetryException.class)
                .hasMessageContaining("fuera de los rangos físicos");

        assertThatThrownBy(() -> VibrationReadingRecord.of("sensor_A", 550.0))
                .isInstanceOf(InvalidTelemetryException.class)
                .hasMessageContaining("fuera de los rangos físicos");

        assertThatThrownBy(() -> new VibrationReadingRecord(null, 30.0, Instant.now()))
                .isInstanceOf(NullPointerException.class);
    }
}
