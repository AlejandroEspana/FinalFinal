package com.empresa.mantenimiento.domain;

import com.empresa.mantenimiento.domain.model.AlertSeverity;
import com.empresa.mantenimiento.domain.service.DecisionOptionsPolicyDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Pruebas de Dominio Puro (RAM / AAA): DecisionOptionsPolicyDomainService")
class DecisionOptionsPolicyDomainServiceTest {

    private final DecisionOptionsPolicyDomainService policy = new DecisionOptionsPolicyDomainService();

    @Test
    @DisplayName("AAA: Alerta CRITICAL genera opciones [APAGADO_INMEDIATO, IGNORAR_10_MINUTOS]")
    void shouldGenerateCriticalOptions() {
        List<String> options = policy.resolveOptionsFor(AlertSeverity.CRITICAL);
        assertThat(options).containsExactly("APAGADO_INMEDIATO", "IGNORAR_10_MINUTOS");
    }

    @Test
    @DisplayName("AAA: Alerta WARNING genera opciones [PROGRAMAR_MANTENIMIENTO_AHORA, RECONOCER_Y_ESPERAR_24H]")
    void shouldGenerateWarningOptions() {
        List<String> options = policy.resolveOptionsFor(AlertSeverity.WARNING);
        assertThat(options).containsExactly("PROGRAMAR_MANTENIMIENTO_AHORA", "RECONOCER_Y_ESPERAR_24H");
    }
}
