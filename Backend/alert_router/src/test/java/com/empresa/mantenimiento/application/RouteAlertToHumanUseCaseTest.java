package com.empresa.mantenimiento.application;

import com.empresa.mantenimiento.application.usecase.RouteAlertToHumanUseCase;
import com.empresa.mantenimiento.domain.model.*;
import com.empresa.mantenimiento.domain.repository.HumanAlertPublisherPort;
import com.empresa.mantenimiento.domain.service.DecisionOptionsPolicyDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias Aisladas con Mockito: RouteAlertToHumanUseCase")
class RouteAlertToHumanUseCaseTest {

    @Mock
    private HumanAlertPublisherPort publisherPort;

    private RouteAlertToHumanUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RouteAlertToHumanUseCase(new DecisionOptionsPolicyDomainService(), publisherPort);
    }

    @Test
    @DisplayName("Enruta alerta CRITICAL al Fanout Exchange con opciones polimórficas exactas")
    void shouldRouteCriticalAlertWithOptions() {
        IndustrialAlert alert = new IndustrialAlert(
                AlertId.of("ALT-01"), SensorId.of("sensor_A"), AlertSeverity.CRITICAL, 96.0, "Pico", Instant.now()
        );
        ArgumentCaptor<HumanActionRequest> captor = ArgumentCaptor.forClass(HumanActionRequest.class);

        HumanActionRequest result = useCase.execute(alert);

        verify(publisherPort, times(1)).publish(captor.capture());
        assertThat(result.availableOptions()).containsExactly("APAGADO_INMEDIATO", "IGNORAR_10_MINUTOS");
        assertThat(captor.getValue().alertId().value()).isEqualTo("ALT-01");
    }
}
