package com.empresa.mantenimiento.domain;

import com.empresa.mantenimiento.domain.exception.InvalidActuatorCommandException;
import com.empresa.mantenimiento.domain.model.ActuatorExecutionRecord;
import com.empresa.mantenimiento.domain.model.PhysicalActuator;
import com.empresa.mantenimiento.domain.model.SensorId;
import com.empresa.mantenimiento.domain.model.ShutdownCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhysicalActuatorTest {

    @Test
    @DisplayName("Debe transicionar de RUNNING a EMERGENCY_HALTED al ejecutar corte de energía y permitir reactivación a RUNNING")
    void shouldTransitionFromRunningToEmergencyHaltedAndRestart() {
        SensorId sensorId = new SensorId("sensor_A");
        PhysicalActuator actuator = new PhysicalActuator(sensorId);
        ShutdownCommand cmd = new ShutdownCommand(
                "CMD-001",
                sensorId,
                "Vibración > 90",
                "operator",
                Instant.now()
        );

        ActuatorExecutionRecord haltRecord = actuator.ejecutarCorteEnergiaInmediato(cmd);

        assertThat(haltRecord.previousState()).isEqualTo("RUNNING");
        assertThat(haltRecord.currentState()).isEqualTo("EMERGENCY_HALTED");
        assertThat(actuator.getCurrentStateName()).isEqualTo("EMERGENCY_HALTED");

        ActuatorExecutionRecord resetRecord = actuator.reactivarMaquina("admin", "Mantenimiento completado");

        assertThat(resetRecord.previousState()).isEqualTo("EMERGENCY_HALTED");
        assertThat(resetRecord.currentState()).isEqualTo("RUNNING");
        assertThat(actuator.getCurrentStateName()).isEqualTo("RUNNING");
    }

    @Test
    @DisplayName("Debe rechazar un ShutdownCommand con sensorId vacío (Fail-Fast)")
    void shouldRejectBlankSensorId() {
        assertThatThrownBy(() -> new SensorId(" "))
                .isInstanceOf(InvalidActuatorCommandException.class);
    }
}
