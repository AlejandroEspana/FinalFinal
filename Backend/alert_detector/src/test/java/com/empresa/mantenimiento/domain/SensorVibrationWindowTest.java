package com.empresa.mantenimiento.domain;

import com.empresa.mantenimiento.domain.exception.DomainInvariantViolationException;
import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.domain.model.state.CriticalSensorState;
import com.empresa.mantenimiento.domain.model.state.NormalSensorState;
import com.empresa.mantenimiento.domain.model.state.WarningSensorState;
import com.empresa.mantenimiento.domain.model.strategy.ExponentialMovingAverageStrategy;
import com.empresa.mantenimiento.domain.model.strategy.RuleBasedThresholdStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Pruebas de Dominio Puro (RAM / AAA): SensorVibrationWindow, Strategy y State")
class SensorVibrationWindowTest {

    private SensorVibrationWindow window;
    private RuleBasedThresholdStrategy ruleStrategy;

    @BeforeEach
    void setUp() {
        window = new SensorVibrationWindow(SensorId.of("sensor_A"), 4);
        ruleStrategy = new RuleBasedThresholdStrategy();
    }

    @Test
    @DisplayName("AAA: Ventana móvil mantiene capacidad máxima FIFO y descarta lecturas antiguas")
    void shouldMaintainSlidingWindowCapacity() {
        window.registrarLectura(VibrationReadingRecord.of("sensor_A", 10.0));
        window.registrarLectura(VibrationReadingRecord.of("sensor_A", 20.0));
        window.registrarLectura(VibrationReadingRecord.of("sensor_A", 30.0));
        window.registrarLectura(VibrationReadingRecord.of("sensor_A", 40.0));
        window.registrarLectura(VibrationReadingRecord.of("sensor_A", 50.0));

        assertThat(window.getCantidadLecturas()).isEqualTo(4);
        assertThat(window.getVentanaHistorica().get(0).vibrationValue()).isEqualTo(20.0);
        assertThat(window.getVentanaHistorica().get(3).vibrationValue()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("Fail-Fast: Rechaza lectura perteneciente a otro sensor")
    void shouldRejectForeignSensorReading() {
        assertThatThrownBy(() -> window.registrarLectura(VibrationReadingRecord.of("sensor_B", 35.0)))
                .isInstanceOf(DomainInvariantViolationException.class)
                .hasMessageContaining("Invariante violada");
    }

    @Test
    @DisplayName("Regla 1 y Patrón State: Lectura > 90 genera IndustrialAlert CRITICAL y transiciona a CriticalSensorState")
    void shouldTriggerCriticalAlertAndTransitionState() {
        Optional<IndustrialAlert> alertOpt = window.procesarTelemetria(
                VibrationReadingRecord.of("sensor_A", 95.4), ruleStrategy
        );

        assertThat(alertOpt).isPresent();
        assertThat(alertOpt.get().severity()).isEqualTo(AlertSeverity.CRITICAL);
        assertThat(window.getEstadoActual()).isInstanceOf(CriticalSensorState.class);
    }

    @Test
    @DisplayName("Regla 2 y Patrón State: 3 lecturas > 75 generan IndustrialAlert WARNING y transicionan a WarningSensorState")
    void shouldTriggerWarningAlertAfterThreeAccumulatedReadings() {
        assertThat(window.procesarTelemetria(VibrationReadingRecord.of("sensor_A", 77.0), ruleStrategy)).isEmpty();
        assertThat(window.procesarTelemetria(VibrationReadingRecord.of("sensor_A", 45.0), ruleStrategy)).isEmpty();
        assertThat(window.procesarTelemetria(VibrationReadingRecord.of("sensor_A", 78.5), ruleStrategy)).isEmpty();

        Optional<IndustrialAlert> alertOpt = window.procesarTelemetria(
                VibrationReadingRecord.of("sensor_A", 81.0), ruleStrategy
        );

        assertThat(alertOpt).isPresent();
        assertThat(alertOpt.get().severity()).isEqualTo(AlertSeverity.WARNING);
        assertThat(window.getEstadoActual()).isInstanceOf(WarningSensorState.class);
    }

    @Test
    @DisplayName("Patrón State Anti-Tormenta: Suprime alertas repetidas en el mismo estado pero emite si escala de WARNING a CRITICAL")
    void shouldSuppressDuplicateAlertsAndEmitOnEscalation() {
        window.procesarTelemetria(VibrationReadingRecord.of("sensor_A", 78.0), ruleStrategy);
        window.procesarTelemetria(VibrationReadingRecord.of("sensor_A", 79.0), ruleStrategy);
        Optional<IndustrialAlert> firstWarning = window.procesarTelemetria(
                VibrationReadingRecord.of("sensor_A", 80.0), ruleStrategy
        );
        assertThat(firstWarning).isPresent();

        // Lectura adicional en WARNING no debe causar tormenta de alertas duplicadas
        Optional<IndustrialAlert> duplicateWarning = window.procesarTelemetria(
                VibrationReadingRecord.of("sensor_A", 82.0), ruleStrategy
        );
        assertThat(duplicateWarning).isEmpty();

        // Escalamiento de WARNING -> CRITICAL sí debe emitir alerta CRITICAL
        Optional<IndustrialAlert> escalatedCritical = window.procesarTelemetria(
                VibrationReadingRecord.of("sensor_A", 96.0), ruleStrategy
        );
        assertThat(escalatedCritical).isPresent();
        assertThat(escalatedCritical.get().severity()).isEqualTo(AlertSeverity.CRITICAL);

        // Retorno a NORMAL cuando la ventana se llena de lecturas seguras
        window.procesarTelemetria(VibrationReadingRecord.of("sensor_A", 30.0), ruleStrategy);
        window.procesarTelemetria(VibrationReadingRecord.of("sensor_A", 31.0), ruleStrategy);
        window.procesarTelemetria(VibrationReadingRecord.of("sensor_A", 32.0), ruleStrategy);
        assertThat(window.getEstadoActual()).isInstanceOf(NormalSensorState.class);
    }

    @Test
    @DisplayName("Patrón Strategy: Permite intercambiar algoritmo a ExponentialMovingAverageStrategy")
    void shouldAllowInterchangeableEmaStrategy() {
        ExponentialMovingAverageStrategy emaStrategy = new ExponentialMovingAverageStrategy(0.5, 85.0, 70.0);
        window.procesarTelemetria(VibrationReadingRecord.of("sensor_A", 92.0), emaStrategy);
        assertThat(window.getSeveridadActual()).isEqualTo(AlertSeverity.CRITICAL);
    }
}
